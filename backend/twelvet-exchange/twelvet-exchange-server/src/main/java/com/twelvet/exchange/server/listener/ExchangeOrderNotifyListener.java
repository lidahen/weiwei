package com.twelvet.exchange.server.listener;

import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.exchange.api.domain.ExchangeOffer;
import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.domain.ExchangeOrderLog;
import com.twelvet.exchange.server.event.ExchangeOrderNotifyEvent;
import com.twelvet.exchange.server.mapper.ExchangeAiModerationMapper;
import com.twelvet.exchange.server.mapper.ExchangeItemMapper;
import com.twelvet.exchange.server.mapper.ExchangeOfferMapper;
import com.twelvet.exchange.server.mapper.ExchangeOrderMapper;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 交换广场状态流转 IM 通知监听器
 * <p>
 * 设计：docs/交换广场模块设计方案.md §8.3 消息通知矩阵
 * <ul>
 * <li>@Async 异步执行，失败只记日志，绝不影响主流程事务</li>
 * <li>@Async 线程取不到 TenantContext，事件携带 tenantId（Quartz 场景用订单/物品表 tenant_id 兜底）</li>
 * <li>发送通道复用 IM：IImService.sendMessage(operator, receiver, null, "system", content)</li>
 * <li>操作人自己发起的动作不给自己发消息（与 RunnerTaskNotifyListener 一致，UI 已有即时反馈）</li>
 * </ul>
 *
 * @author twelvet
 */
@Component
public class ExchangeOrderNotifyListener {

	private static final Logger log = LoggerFactory.getLogger(ExchangeOrderNotifyListener.class);

	private final ExchangeOrderMapper orderMapper;

	private final ExchangeOfferMapper offerMapper;

	private final ExchangeItemMapper itemMapper;

	private final ExchangeAiModerationMapper moderationMapper;

	private final IImService imService;

	public ExchangeOrderNotifyListener(ExchangeOrderMapper orderMapper, ExchangeOfferMapper offerMapper,
			ExchangeItemMapper itemMapper, ExchangeAiModerationMapper moderationMapper, IImService imService) {
		this.orderMapper = orderMapper;
		this.offerMapper = offerMapper;
		this.itemMapper = itemMapper;
		this.moderationMapper = moderationMapper;
		this.imService = imService;
	}

	@Async
	@EventListener
	public void onExchangeNotify(ExchangeOrderNotifyEvent event) {
		Long tid = null;
		try {
			tid = event.getTenantId();
			if (tid == null) {
				return;
			}
			TenantContext.set(tid);
			try {
				List<ReceiverMsg> targets = resolveReceiversAndContents(event, tid);
				if (targets.isEmpty()) {
					return;
				}
				Long senderId = resolveSenderId(event.getOperatorId(), tid);
				if (senderId == null) {
					log.warn("交换通知未找到系统发送人，跳过 IM 通知 tenantId={}: action={}", tid, event.getAction());
					return;
				}
				for (ReceiverMsg target : targets) {
					try {
						imService.sendMessage(senderId, target.receiverId, null, "system", target.content);
					}
					catch (Exception e) {
						log.warn("交换通知 IM 发送失败 action={}, receiver={}: {}", event.getAction(), target.receiverId,
								e.getMessage());
					}
				}
			}
			finally {
				TenantContext.clear();
				tid = null;
			}
		}
		catch (Exception e) {
			log.warn("交换通知处理异常 action={}: {}", event.getAction(), e.getMessage());
		}
		finally {
			if (tid != null) {
				TenantContext.clear();
			}
		}
	}

	/**
	 * 定位接收人与文案（矩阵 §8.3）；空列表表示无需通知（对方为空/给自己发/动作不识别）
	 */
	private List<ReceiverMsg> resolveReceiversAndContents(ExchangeOrderNotifyEvent event, Long tid) {
		if (event.getOrderId() != null) {
			return resolveOrderReceivers(event, tid);
		}
		if (event.getOfferId() != null) {
			return resolveOfferReceivers(event, tid);
		}
		if (event.getItemId() != null) {
			return resolveItemReceivers(event, tid);
		}
		return List.of();
	}

	private List<ReceiverMsg> resolveOrderReceivers(ExchangeOrderNotifyEvent event, Long tid) {
		ExchangeOrder order = orderMapper.selectById(event.getOrderId(), tid);
		if (order == null) {
			return List.of();
		}
		String orderNo = order.getOrderNo();
		String aTitle = itemTitle(order.getItemAId(), tid);
		String bTitle = itemTitle(order.getItemBId(), tid);
		String action = event.getAction();
		String remark = event.getRemark() == null ? "" : event.getRemark();

		List<ReceiverMsg> targets = new ArrayList<>();
		switch (action) {
			case ExchangeOrderLog.ACTION_CREATE:
				addBoth(targets, order, event.getOperatorId(),
						"【换物】您的交换订单 " + orderNo + " 已建立：「" + aTitle + "」⇄「" + bTitle + "」，请按约定完成交付");
				break;
			case ExchangeOrderLog.ACTION_DELIVER:
				addCounterpart(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 对方已完成交付，请尽快完成您的交付");
				break;
			case ExchangeOrderLog.ACTION_CONFIRM:
				// 与 runner 一致按订单结果状态区分：已完成 → 双方完成文案；未完 → 通知对方
				if (ExchangeOrder.STATUS_COMPLETED.equals(order.getStatus())) {
					addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 交换完成，双方物品已换出，记得互评哦");
				}
				else {
					addCounterpart(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 对方已确认收到物品");
				}
				break;
			case ExchangeOrderLog.ACTION_CANCEL:
				addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 已取消：" + remark);
				break;
			case ExchangeOrderLog.ACTION_DISPUTE:
				addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 发起纠纷：" + remark + "，平台将介入处理");
				break;
			case ExchangeOrderLog.ACTION_TIMEOUT:
				// 按订单结果状态区分：completed → 自动完成（默认收货）；cancelled → 超时取消
				if (ExchangeOrder.STATUS_COMPLETED.equals(order.getStatus())) {
					addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 双方交付后超 3 天未确认，已自动确认收货并完成交换");
				}
				else {
					addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 建单超 7 天未完成双方交付，已自动取消，物品已解锁");
				}
				break;
			case ExchangeOrderLog.ACTION_ADMIN_FORCE_COMPLETE:
				addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 平台已裁决按现状完成交换");
				break;
			case ExchangeOrderLog.ACTION_ADMIN_FORCE_CANCEL:
				addBoth(targets, order, event.getOperatorId(), "【换物】订单 " + orderNo + " 平台已裁决取消并解锁物品：" + remark);
				break;
			case ExchangeOrderLog.ACTION_REVIEW:
				addCounterpart(targets, order, event.getOperatorId(), "【换物】您收到一条来自订单 " + orderNo + " 的评价：" + remark);
				break;
			default:
				break;
		}
		return targets;
	}

	private List<ReceiverMsg> resolveOfferReceivers(ExchangeOrderNotifyEvent event, Long tid) {
		ExchangeOffer offer = offerMapper.selectById(event.getOfferId(), tid);
		if (offer == null) {
			return List.of();
		}
		String title = itemTitle(offer.getItemId(), tid);
		switch (event.getAction()) {
			case ExchangeOrderLog.ACTION_OFFER_RECEIVED:
				return single(offer.getOwnerId(), "【换物】您的物品「" + title + "」收到一条换物意向，快去看看吧");
			case ExchangeOrderLog.ACTION_OFFER_ACCEPTED:
				return single(offer.getOfferorId(), "【换物】您对「" + title + "」的换物意向已被接受，交换订单已建立");
			case ExchangeOrderLog.ACTION_OFFER_REJECTED:
				return single(offer.getOfferorId(), "【换物】您对「" + title + "」的换物意向未被接受，物品仍在换物广场");
			case ExchangeOrderLog.ACTION_OFFER_EXPIRED:
				return single(offer.getOfferorId(), "【换物】您对「" + title + "」的换物意向已过 72 小时有效期，自动关闭");
			case ExchangeOrderLog.ACTION_OFFER_WITHDRAWN:
				return single(offer.getOwnerId(), "【换物】对方撤回了对您物品「" + title + "」的换物意向");
			case ExchangeOrderLog.ACTION_OFFER_CLOSED:
				return single(offer.getOfferorId(), "【换物】物品「" + title + "」已被主人下架，您的换物意向已关闭");
			default:
				return List.of();
		}
	}

	private List<ReceiverMsg> resolveItemReceivers(ExchangeOrderNotifyEvent event, Long tid) {
		ExchangeItem item = itemMapper.selectById(event.getItemId(), tid);
		if (item == null) {
			return List.of();
		}
		if (ExchangeOrderLog.ACTION_ITEM_REJECTED.equals(event.getAction())) {
			String remark = event.getRemark() == null ? "内容涉嫌违规" : event.getRemark();
			return single(item.getOwnerId(), "🔔 您的物品「" + item.getTitle() + "」未通过审核：" + remark + "。可在「我的物品」中查看后重新发布");
		}
		return List.of();
	}

	/**
	 * 发送人：操作人本人；系统触发（operator=0/null，如超时任务/AI 初审）走租户系统发送人
	 */
	private Long resolveSenderId(Long operatorId, Long tid) {
		if (operatorId != null && operatorId > 0) {
			return operatorId;
		}
		return moderationMapper.selectSenderUserId(tid);
	}

	private void addBoth(List<ReceiverMsg> targets, ExchangeOrder order, Long operatorId, String content) {
		addIfNotOperator(targets, order.getOwnerAId(), operatorId, content);
		addIfNotOperator(targets, order.getOwnerBId(), operatorId, content);
	}

	private void addCounterpart(List<ReceiverMsg> targets, ExchangeOrder order, Long operatorId, String content) {
		Long receiver = operatorId != null && operatorId.equals(order.getOwnerAId()) ? order.getOwnerBId()
				: order.getOwnerAId();
		addIfNotOperator(targets, receiver, operatorId, content);
	}

	private void addIfNotOperator(List<ReceiverMsg> targets, Long receiverId, Long operatorId, String content) {
		if (receiverId == null || receiverId.equals(operatorId)) {
			return;
		}
		targets.add(new ReceiverMsg(receiverId, content));
	}

	private List<ReceiverMsg> single(Long receiverId, String content) {
		return receiverId == null ? List.of() : List.of(new ReceiverMsg(receiverId, content));
	}

	private String itemTitle(Long itemId, Long tid) {
		if (itemId == null) {
			return "";
		}
		ExchangeItem item = itemMapper.selectById(itemId, tid);
		return item == null ? "" : item.getTitle();
	}

	private static class ReceiverMsg {

		final Long receiverId;

		final String content;

		ReceiverMsg(Long receiverId, String content) {
			this.receiverId = receiverId;
			this.content = content;
		}

	}

}
