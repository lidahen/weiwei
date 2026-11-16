package com.twelvet.exchange.server.service.impl;

import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.api.domain.ExchangeOffer;
import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.domain.ExchangePickupCode;
import com.twelvet.exchange.server.event.ExchangeOrderNotifyEvent;
import com.twelvet.exchange.server.mapper.ExchangeItemMapper;
import com.twelvet.exchange.server.mapper.ExchangeOfferMapper;
import com.twelvet.exchange.server.mapper.ExchangeOrderLogMapper;
import com.twelvet.exchange.server.mapper.ExchangeOrderMapper;
import com.twelvet.exchange.server.mapper.ExchangePickupCodeMapper;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 交换订单状态机 & 日志共享逻辑（双边履约）
 * <p>
 * 抽出来供 IExchangeOfferService（接受意向建单）与 IExchangeOrderService（履约/取消/纠纷/裁决）共用，
 * 避免两边重复实现日志插入、解锁/换出副作用与通知发布。 所有流转走条件 UPDATE 乐观锁， 影响行数=0 即业务失败（docs/交换广场模块设计方案.md §8.2）。
 * </p>
 *
 * @author twelvet
 */
@Component
public class ExchangeOrderStateMachine {

	private final ExchangeItemMapper itemMapper;

	private final ExchangeOfferMapper offerMapper;

	private final ExchangeOrderMapper orderMapper;

	private final ExchangeOrderLogMapper logMapper;

	private final ExchangePickupCodeMapper pickupCodeMapper;

	private final TenantSupport tenantSupport;

	private final ApplicationEventPublisher eventPublisher;

	public ExchangeOrderStateMachine(ExchangeItemMapper itemMapper, ExchangeOfferMapper offerMapper,
			ExchangeOrderMapper orderMapper, ExchangeOrderLogMapper logMapper,
			ExchangePickupCodeMapper pickupCodeMapper, TenantSupport tenantSupport,
			ApplicationEventPublisher eventPublisher) {
		this.itemMapper = itemMapper;
		this.offerMapper = offerMapper;
		this.orderMapper = orderMapper;
		this.logMapper = logMapper;
		this.pickupCodeMapper = pickupCodeMapper;
		this.tenantSupport = tenantSupport;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 当前租户上下文（开关关闭时 null）
	 */
	public Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 取写路径租户（开关开启但无上下文时抛异常）
	 */
	public Long writeTid() {
		return tenantSupport.writeTid();
	}

	/**
	 * 接受意向（核心并发点）：单事务三步条件 UPDATE（offer pending→accepted / item_a onswap→locked / item_b
	 * onswap→locked），任一步影响行数=0 抛业务异常回滚； 随后建订单（created）+ 两条面交码 + 该物品其余 pending 意向批量
	 * rejected + 落日志 + 发事件。
	 * <p>
	 * 事务边界由调用方（IExchangeOfferService.accept）持有。
	 * </p>
	 * @param offer 待接受的意向（须为 pending）
	 * @param itemA A 的物品（被申请，须为 onswap）
	 * @param itemB B 用来交换的物品（须为 onswap；快照意向已在 service 层前置拦截）
	 * @param operatorId 物品主人 A
	 * @return 新建订单
	 */
	public ExchangeOrder doAccept(ExchangeOffer offer, ExchangeItem itemA, ExchangeItem itemB, Long operatorId) {
		Long tid = currentTid();
		LocalDateTime now = LocalDateTime.now();
		// ① 意向占位
		if (offerMapper.acceptAtomically(offer.getId(), tid, now) == 0) {
			throw new TWTException("该意向已被处理或已过期");
		}
		// ② A 物品锁定
		if (itemMapper.lockItem(itemA.getId(), offer.getOwnerId(), tid) == 0) {
			throw new TWTException("物品已锁定或下架，无法接受该意向");
		}
		// ③ B 物品锁定（快照意向时跳过，service 层已拦截快照意向不可直接接受）
		if (itemB != null && itemMapper.lockItem(itemB.getId(), offer.getOfferorId(), tid) == 0) {
			throw new TWTException("对方物品已锁定或下架，无法接受该意向");
		}

		ExchangeOrder order = new ExchangeOrder();
		order.setOrderNo(generateOrderNo());
		order.setOfferId(offer.getId());
		order.setItemAId(itemA.getId());
		order.setItemBId(itemB.getId());
		order.setOwnerAId(offer.getOwnerId());
		order.setOwnerBId(offer.getOfferorId());
		order.setDeliveryMode(resolveDeliveryMode(itemA.getDeliveryMode(), itemB.getDeliveryMode()));
		order.setStatus(ExchangeOrder.STATUS_CREATED);
		order.setTenantId(tid);
		orderMapper.insert(order);

		// 两条面交码（side a/b 各 6 位随机数字，有效期 7 天；uk_order_side 防重复生成）
		insertPickupCode(order.getId(), ExchangeOrder.SIDE_A, tid, now);
		insertPickupCode(order.getId(), ExchangeOrder.SIDE_B, tid, now);

		// 该物品其余 pending 意向批量 rejected（先取出清单供逐条 IM 通知，再批量关闭）
		List<ExchangeOffer> others = offerMapper.selectPendingByItem(itemA.getId(), tid);
		offerMapper.rejectOthersPending(itemA.getId(), offer.getId(), tid);
		for (ExchangeOffer other : others) {
			publishOfferNotify(other.getId(), ExchangeOrderLog.ACTION_OFFER_REJECTED, operatorId, tid);
		}

		insertLog(order.getId(), operatorId, ExchangeOrderLog.ACTION_CREATE, "接受意向，交换订单建立");
		publishNotify(order.getId(), ExchangeOrderLog.ACTION_CREATE, operatorId, null, tid);
		publishOfferNotify(offer.getId(), ExchangeOrderLog.ACTION_OFFER_ACCEPTED, operatorId, tid);
		return order;
	}

	/**
	 * 双方均确认 → completed：条件 UPDATE 推进 + 双方物品 swapped（swap_count+1） + 落日志 + 发事件
	 * @return false 表示尚有对方未确认（本次仅确认自己），订单继续等待
	 */
	public boolean doComplete(Long orderId, Long operatorId, String remark) {
		Long tid = currentTid();
		if (orderMapper.advanceConfirmStatus(orderId, tid) == 0) {
			return false;
		}
		ExchangeOrder order = requireOrder(orderId);
		itemMapper.markSwapped(order.getItemAId(), tid);
		itemMapper.markSwapped(order.getItemBId(), tid);
		insertLog(orderId, operatorId, ExchangeOrderLog.ACTION_CONFIRM, remark);
		publishNotify(orderId, ExchangeOrderLog.ACTION_CONFIRM, operatorId, remark, tid);
		return true;
	}

	/**
	 * 裁决/自动完成后共用的换出副作用（物品 swapped + swap_count+1），订单状态由各语句自行推进
	 */
	public void completeSwapEffects(ExchangeOrder order) {
		completeSwapEffects(order, currentTid());
	}

	/**
	 * 带显式租户的换出副作用（Quartz 线程无租户上下文，须传订单自身租户）
	 */
	public void completeSwapEffects(ExchangeOrder order, Long tid) {
		itemMapper.markSwapped(order.getItemAId(), tid);
		itemMapper.markSwapped(order.getItemBId(), tid);
	}

	/**
	 * 取消/裁决取消后解锁双方物品（locked → onswap）
	 */
	public void unlockItems(ExchangeOrder order) {
		unlockItems(order, currentTid());
	}

	/**
	 * 带显式租户的解锁（Quartz 线程无租户上下文，须传订单自身租户）
	 */
	public void unlockItems(ExchangeOrder order, Long tid) {
		itemMapper.unlockItem(order.getItemAId(), tid);
		itemMapper.unlockItem(order.getItemBId(), tid);
	}

	/**
	 * 状态流转通知事件（@Async 监听器异步处理，失败不影响主流程）
	 */
	public void publishNotify(Long orderId, String action, Long operatorId, String remark, Long tenantId) {
		eventPublisher.publishEvent(new ExchangeOrderNotifyEvent(orderId, action, operatorId, remark, tenantId));
	}

	/**
	 * 意向/物品侧流转通知事件（无订单上下文）
	 */
	public void publishOfferNotify(Long offerId, String action, Long operatorId, Long tenantId) {
		eventPublisher
			.publishEvent(new ExchangeOrderNotifyEvent(null, offerId, null, action, operatorId, null, tenantId));
	}

	/**
	 * 物品侧流转通知事件（如人工审核驳回 IM 通知主人）
	 */
	public void publishItemNotify(Long itemId, String action, Long operatorId, String remark, Long tenantId) {
		eventPublisher
			.publishEvent(new ExchangeOrderNotifyEvent(null, null, itemId, action, operatorId, remark, tenantId));
	}

	/**
	 * 落流转日志
	 */
	public void insertLog(Long orderId, Long operatorId, String action, String remark) {
		insertLog(orderId, operatorId, action, remark, currentTid());
	}

	/**
	 * 带显式租户的落日志（Quartz 线程无租户上下文，tenant_id 列 NOT NULL，null 会 SQL 异常）
	 */
	public void insertLog(Long orderId, Long operatorId, String action, String remark, Long tid) {
		ExchangeOrderLog log = new ExchangeOrderLog();
		log.setOrderId(orderId);
		log.setOperatorId(operatorId);
		log.setAction(action);
		log.setRemark(remark);
		logMapper.insert(log, tid);
	}

	/**
	 * 校验订单存在
	 */
	public ExchangeOrder requireOrder(Long orderId) {
		return orderMapper.selectById(orderId, currentTid());
	}

	/**
	 * 校验当前用户是 ownerA
	 */
	public boolean isOwnerA(ExchangeOrder order, Long userId) {
		return order != null && order.getOwnerAId() != null && order.getOwnerAId().equals(userId);
	}

	/**
	 * 校验当前用户是 ownerB
	 */
	public boolean isOwnerB(ExchangeOrder order, Long userId) {
		return order != null && order.getOwnerBId() != null && order.getOwnerBId().equals(userId);
	}

	/**
	 * 校验当前用户是订单参与方（A/B 双视角）
	 */
	public boolean isParticipant(ExchangeOrder order, Long userId) {
		return isOwnerA(order, userId) || isOwnerB(order, userId);
	}

	/**
	 * 交付方式取双方物品支持方式的交集：相同取同值；一方 both 取另一方；face+express 组合回落面交（社区主形态）
	 */
	private String resolveDeliveryMode(String aMode, String bMode) {
		if (aMode == null) {
			aMode = ExchangeOrder.DELIVERY_FACE;
		}
		if (bMode == null) {
			bMode = ExchangeOrder.DELIVERY_FACE;
		}
		if (aMode.equals(bMode)) {
			return aMode;
		}
		if (ExchangeItem.DELIVERY_BOTH.equals(aMode)) {
			return bMode;
		}
		if (ExchangeItem.DELIVERY_BOTH.equals(bMode)) {
			return aMode;
		}
		return ExchangeOrder.DELIVERY_FACE;
	}

	private void insertPickupCode(Long orderId, String side, Long tid, LocalDateTime now) {
		ExchangePickupCode pickupCode = new ExchangePickupCode();
		pickupCode.setOrderId(orderId);
		pickupCode.setSide(side);
		pickupCode.setCode(String.format("%06d", (int) (Math.random() * 1000000)));
		pickupCode.setExpireAt(now.plusDays(ExchangeOrder.PICKUP_CODE_EXPIRE_DAYS));
		pickupCodeMapper.insert(pickupCode, tid);
	}

	private String generateOrderNo() {
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		int rand = (int) (Math.random() * 1000000);
		return "EXC" + time + String.format("%06d", rand);
	}

}
