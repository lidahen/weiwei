package com.twelvet.exchange.server.domain;

import java.time.LocalDateTime;

/**
 * 交换订单流转日志（twelve_exchange_order_log）
 * <p>
 * 流水记录：create / deliver / confirm / cancel / dispute / timeout / admin_force_complete /
 * admin_force_cancel / review。 另有 ACTION_OFFER_* / ACTION_ITEM_REJECTED 系列动作仅用于 IM 通知事件
 * （无订单上下文，不落 order_log）。
 * </p>
 *
 * @author twelvet
 */
public class ExchangeOrderLog {

	/** 动作：订单建立（接受意向） */
	public static final String ACTION_CREATE = "create";

	/** 动作：标记交付 */
	public static final String ACTION_DELIVER = "deliver";

	/** 动作：确认收货 */
	public static final String ACTION_CONFIRM = "confirm";

	/** 动作：取消 */
	public static final String ACTION_CANCEL = "cancel";

	/** 动作：发起纠纷 */
	public static final String ACTION_DISPUTE = "dispute";

	/** 动作：超时（建单超时取消 / 双交付后超时自动完成，按订单结果状态区分文案） */
	public static final String ACTION_TIMEOUT = "timeout";

	/** 动作：后台裁决按现状完成 */
	public static final String ACTION_ADMIN_FORCE_COMPLETE = "admin_force_complete";

	/** 动作：后台裁决取消并解锁 */
	public static final String ACTION_ADMIN_FORCE_CANCEL = "admin_force_cancel";

	/** 动作：评价 */
	public static final String ACTION_REVIEW = "review";

	/** 通知动作：收到换物意向（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_RECEIVED = "offer_received";

	/** 通知动作：意向被接受（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_ACCEPTED = "offer_accepted";

	/** 通知动作：意向被婉拒（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_REJECTED = "offer_rejected";

	/** 通知动作：意向过期（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_EXPIRED = "offer_expired";

	/** 通知动作：意向撤回（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_WITHDRAWN = "offer_withdrawn";

	/** 通知动作：物品下架导致意向关闭（仅通知，不落 order_log） */
	public static final String ACTION_OFFER_CLOSED = "offer_closed";

	/** 通知动作：物品审核驳回（AI 初审/人工驳回共用，仅通知，不落 order_log） */
	public static final String ACTION_ITEM_REJECTED = "item_rejected";

	private Long id;

	private Long orderId;

	private Long operatorId;

	private String action;

	private String remark;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public void setOperatorId(Long operatorId) {
		this.operatorId = operatorId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
