package com.twelvet.exchange.server.event;

/**
 * 交换广场状态流转通知事件
 * <p>
 * 状态机/意向/审核流转成功后发布，由 {@code ExchangeOrderNotifyListener} 异步消费： 调 IImService 发 IM system
 * 消息（文案矩阵见 docs/交换广场模块设计方案.md §8.3）。 事件必须携带 tenantId（@Async 线程取不到 TenantContext
 * ThreadLocal）。 orderId / offerId / itemId 按动作语境三选一。
 * </p>
 *
 * @author twelvet
 */
public class ExchangeOrderNotifyEvent {

	private final Long orderId;

	private final Long offerId;

	private final Long itemId;

	private final String action;

	private final Long operatorId;

	private final String remark;

	private final Long tenantId;

	public ExchangeOrderNotifyEvent(Long orderId, String action, Long operatorId, String remark, Long tenantId) {
		this(orderId, null, null, action, operatorId, remark, tenantId);
	}

	public ExchangeOrderNotifyEvent(Long orderId, Long offerId, Long itemId, String action, Long operatorId,
			String remark, Long tenantId) {
		this.orderId = orderId;
		this.offerId = offerId;
		this.itemId = itemId;
		this.action = action;
		this.operatorId = operatorId;
		this.remark = remark;
		this.tenantId = tenantId;
	}

	public Long getOrderId() {
		return orderId;
	}

	public Long getOfferId() {
		return offerId;
	}

	public Long getItemId() {
		return itemId;
	}

	public String getAction() {
		return action;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public String getRemark() {
		return remark;
	}

	public Long getTenantId() {
		return tenantId;
	}

}
