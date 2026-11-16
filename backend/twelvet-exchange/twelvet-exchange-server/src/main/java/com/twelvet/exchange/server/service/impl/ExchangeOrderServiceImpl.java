package com.twelvet.exchange.server.service.impl;

import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.domain.ExchangePickupCode;
import com.twelvet.exchange.server.domain.ExchangeReview;
import com.twelvet.exchange.server.mapper.ExchangeOrderLogMapper;
import com.twelvet.exchange.server.mapper.ExchangeOrderMapper;
import com.twelvet.exchange.server.mapper.ExchangePickupCodeMapper;
import com.twelvet.exchange.server.mapper.ExchangeReviewMapper;
import com.twelvet.exchange.server.service.IExchangeOrderService;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.im.server.service.IImService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 交换订单服务实现（双边履约：交付/确认/取消/纠纷/超时/裁决）
 * <p>
 * 所有流转走条件 UPDATE 乐观锁（时间戳列为空才允许写），影响行数=0 即业务失败； 每次流转落 twelve_exchange_order_log + 发
 * ExchangeOrderNotifyEvent（docs/交换广场模块设计方案.md §4.4/§4.5）。
 * </p>
 *
 * @author twelvet
 */
@Service
public class ExchangeOrderServiceImpl implements IExchangeOrderService {

	private final ExchangeOrderMapper orderMapper;

	private final ExchangeOrderLogMapper logMapper;

	private final ExchangePickupCodeMapper pickupCodeMapper;

	private final ExchangeReviewMapper reviewMapper;

	private final IImService imService;

	private final ExchangeOrderStateMachine stateMachine;

	public ExchangeOrderServiceImpl(ExchangeOrderMapper orderMapper, ExchangeOrderLogMapper logMapper,
			ExchangePickupCodeMapper pickupCodeMapper, ExchangeReviewMapper reviewMapper, IImService imService,
			ExchangeOrderStateMachine stateMachine) {
		this.orderMapper = orderMapper;
		this.logMapper = logMapper;
		this.pickupCodeMapper = pickupCodeMapper;
		this.reviewMapper = reviewMapper;
		this.imService = imService;
		this.stateMachine = stateMachine;
	}

	@Override
	public TableDataInfo myOrdersPage(Long userId, List<String> statusList, Long pageNum, Long pageSize) {
		long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
		long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
		long offset = (pn - 1) * ps;
		Long tid = stateMachine.currentTid();
		List<ExchangeOrder> list = orderMapper.selectMyOrdersPage(userId, tid, statusList, offset, ps);
		long total = orderMapper.countMyOrders(userId, tid, statusList);
		return TableDataInfo.page(list, total);
	}

	@Override
	public Map<String, Object> detail(Long orderId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			return null;
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可查看");
		}
		// A/B 双视角：我方侧别 + 对方用户信息
		boolean isA = stateMachine.isOwnerA(order, userId);
		Map<String, Object> resp = new HashMap<>();
		resp.put("order", order);
		resp.put("mySide", isA ? ExchangeOrder.SIDE_A : ExchangeOrder.SIDE_B);
		resp.put("peerId", isA ? order.getOwnerBId() : order.getOwnerAId());
		try {
			resp.put("peerInfo", imService.selectUserInfo(isA ? order.getOwnerBId() : order.getOwnerAId()));
		}
		catch (Exception e) {
			resp.put("peerInfo", null);
		}
		// 面交码只返回查看者自己一侧（对方码须线下出示核对，不能经接口泄露）
		String mySide = isA ? ExchangeOrder.SIDE_A : ExchangeOrder.SIDE_B;
		resp.put("pickupCodes",
				pickupCodeMapper.selectByOrderId(orderId, tid)
					.stream()
					.filter(c -> mySide.equals(c.getSide()))
					.collect(Collectors.toList()));
		// 双边进度时间线（流转日志）
		resp.put("logs", logMapper.selectByOrderId(orderId, tid));
		// 双向评价（已评的展示）
		List<ExchangeReview> reviews = reviewMapper.selectByOrderId(orderId, tid);
		resp.put("reviews", reviews);
		return resp;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deliver(Long orderId, Long userId, String trackNo) {
		Long tid = stateMachine.currentTid();
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可操作");
		}
		// 快递交换必须填运单号（面交现场点按钮即可）
		boolean express = ExchangeOrder.DELIVERY_EXPRESS.equals(order.getDeliveryMode());
		if (express && (trackNo == null || trackNo.isBlank())) {
			throw new TWTException("快递交换必须填写运单号");
		}
		String trimmedTrackNo = express ? trackNo.trim() : null;
		// 条件 UPDATE 写己方 delivered_time（已写则拒绝，防双击）
		int rows = stateMachine.isOwnerA(order, userId) ? orderMapper.deliverAsA(orderId, userId, trimmedTrackNo, tid)
				: orderMapper.deliverAsB(orderId, userId, trimmedTrackNo, tid);
		if (rows == 0) {
			throw new TWTException("您已交付或当前状态不可交付");
		}
		// 状态推进：双方均交付 → pending_confirm，否则 → delivering
		orderMapper.advanceDeliveryStatus(orderId, tid);

		String remark = express ? "快递交付，运单号：" + trimmedTrackNo : "标记已交付";
		stateMachine.insertLog(orderId, userId, ExchangeOrderLog.ACTION_DELIVER, remark);
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_DELIVER, userId, remark, tid);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void confirm(Long orderId, Long userId) {
		Long tid = stateMachine.currentTid();
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可操作");
		}
		// 条件 UPDATE 写己方 confirmed_time（确认动作与本人交付解耦，pending_confirm 后双方依次点击）
		int rows = stateMachine.isOwnerA(order, userId) ? orderMapper.confirmAsA(orderId, userId, tid)
				: orderMapper.confirmAsB(orderId, userId, tid);
		if (rows == 0) {
			throw new TWTException("确认失败（尚未到确认环节或已确认）");
		}
		stateMachine.insertLog(orderId, userId, ExchangeOrderLog.ACTION_CONFIRM, "确认收到对方物品");
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_CONFIRM, userId, "已确认收到", tid);
		// 双确认 → completed（双方物品 swapped + swap_count+1），未齐时订单继续等待
		stateMachine.doComplete(orderId, userId, "双方确认，交换完成");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void cancel(Long orderId, Long userId, String reason) {
		Long tid = stateMachine.currentTid();
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可操作");
		}
		// 仅双方均未交付可取消；单方已交付只能走纠纷（防"交付后反悔白嫖"，docs §8.4）
		int rows = orderMapper.cancelByUser(orderId, userId, reason, tid);
		if (rows == 0) {
			throw new TWTException("仅双方均未交付时可取消（单方已交付请发起纠纷）");
		}
		// 事务内两物品解锁回 onswap
		stateMachine.unlockItems(order);
		stateMachine.insertLog(orderId, userId, ExchangeOrderLog.ACTION_CANCEL, reason);
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_CANCEL, userId, reason, tid);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void dispute(Long orderId, Long userId, String reason) {
		Long tid = stateMachine.currentTid();
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		if (order == null) {
			throw new TWTException("订单不存在");
		}
		if (!stateMachine.isParticipant(order, userId)) {
			throw new TWTException("仅订单参与方可操作");
		}
		if (reason == null || reason.isBlank()) {
			throw new TWTException("请填写纠纷原因");
		}
		// delivering/pending_confirm 可发起，物品保持 locked 等后台裁决
		if (orderMapper.dispute(orderId, userId, reason, tid) == 0) {
			throw new TWTException("当前状态不可发起纠纷");
		}
		stateMachine.insertLog(orderId, userId, ExchangeOrderLog.ACTION_DISPUTE, reason);
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_DISPUTE, userId, reason, tid);
	}

	@Override
	public int scanTimeoutOrder() {
		int count = 0;
		// 建单超 7 天未双交付 → cancelled + 解锁 + 通知
		LocalDateTime createdThreshold = LocalDateTime.now().minusDays(ExchangeOrder.CREATED_TIMEOUT_DAYS);
		for (ExchangeOrder order : orderMapper.selectTimeoutCreated(createdThreshold, 200)) {
			Long tid = order.getTenantId();
			if (orderMapper.timeoutCancel(order.getId(), "建单超 7 天未完成双方交付，自动取消", tid) == 0) {
				continue;
			}
			stateMachine.unlockItems(order, tid);
			stateMachine.insertLog(order.getId(), 0L, ExchangeOrderLog.ACTION_TIMEOUT, "建单超时自动取消", tid);
			stateMachine.publishNotify(order.getId(), ExchangeOrderLog.ACTION_TIMEOUT, 0L, "建单超时自动取消", tid);
			count++;
		}
		// pending_confirm 超 3 天未双确认 → 自动 completed（默认收货，对齐互助自动确认惯例）
		LocalDateTime confirmThreshold = LocalDateTime.now().minusDays(ExchangeOrder.CONFIRM_TIMEOUT_DAYS);
		for (ExchangeOrder order : orderMapper.selectTimeoutPendingConfirm(confirmThreshold, 200)) {
			Long tid = order.getTenantId();
			if (orderMapper.autoComplete(order.getId(), tid) == 0) {
				continue;
			}
			stateMachine.completeSwapEffects(order, tid);
			stateMachine.insertLog(order.getId(), 0L, ExchangeOrderLog.ACTION_TIMEOUT, "超 3 天未双确认，自动完成（默认收货）", tid);
			stateMachine.publishNotify(order.getId(), ExchangeOrderLog.ACTION_TIMEOUT, 0L, "超时自动完成", tid);
			count++;
		}
		return count;
	}

	@Override
	public TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword) {
		// 平台管理：全状态分页查询（tenantId 取当前上下文，为 null 时不限定租户，与 runner 现有租户处理一致）
		long pn = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		long ps = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 200);
		long offset = (pn - 1) * ps;
		String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		String st = (status == null || status.isBlank()) ? null : status.trim();
		Long tid = stateMachine.currentTid();
		List<ExchangeOrder> list = orderMapper.selectAdminPage(tid, st, kw, offset, ps);
		long total = orderMapper.countAdminPage(tid, st, kw);
		return TableDataInfo.page(list, total);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminForceComplete(Long orderId, Long operatorId, String remark) {
		Long tid = stateMachine.currentTid();
		// 裁决：按现状完成（disputed → completed，确认时间未填回填 NOW()）
		if (orderMapper.forceComplete(orderId, remark, tid) == 0) {
			throw new TWTException("仅纠纷中订单可裁决完成");
		}
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		stateMachine.completeSwapEffects(order);
		stateMachine.insertLog(orderId, operatorId, ExchangeOrderLog.ACTION_ADMIN_FORCE_COMPLETE, remark);
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_ADMIN_FORCE_COMPLETE, operatorId, remark, tid);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminForceCancel(Long orderId, Long operatorId, String remark) {
		Long tid = stateMachine.currentTid();
		// 裁决：取消并解锁（disputed → cancelled + 物品回 onswap）
		if (orderMapper.forceCancel(orderId, remark, tid) == 0) {
			throw new TWTException("仅纠纷中订单可裁决取消");
		}
		ExchangeOrder order = stateMachine.requireOrder(orderId);
		stateMachine.unlockItems(order);
		stateMachine.insertLog(orderId, operatorId, ExchangeOrderLog.ACTION_ADMIN_FORCE_CANCEL, remark);
		stateMachine.publishNotify(orderId, ExchangeOrderLog.ACTION_ADMIN_FORCE_CANCEL, operatorId, remark, tid);
	}

}
