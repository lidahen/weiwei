package com.twelvet.payment.api.event;

import java.time.LocalDateTime;

/**
 * 支付成功事件
 * <p>
 * 由 twelvet-payment-server 在支付回调处理成功后发布。 业务模块（trade / runner 等）监听此事件，自行更新各自业务状态。
 * </p>
 * <p>
 * 这样支付服务不依赖任何业务模块，实现真正的单向依赖： trade → payment-api ← payment-server（trade 依赖 api，不依赖
 * server）
 * </p>
 *
 * @author twelvet
 */
public class PaySuccessEvent {

	/** 商户支付流水号 */
	private final String outTradeNo;

	/** 业务订单ID（商品订单/互助任务等；租户续费时为 null） */
	private final Long orderId;

	/** 业务类型：trade/runner/renew（监听器按此门卫，取代旧 orderId==null 判别） */
	private final String bizType;

	/** 支付渠道 */
	private final String channel;

	/** 支付金额（分） */
	private final Long amountCents;

	/** 支付时间 */
	private final LocalDateTime paidAt;

	/** 支付平台交易号 */
	private final String tradeNo;

	/** 原始回调数据 */
	private final String notifyData;

	public PaySuccessEvent(String outTradeNo, Long orderId, String bizType, String channel, Long amountCents,
			LocalDateTime paidAt, String tradeNo, String notifyData) {
		this.outTradeNo = outTradeNo;
		this.orderId = orderId;
		this.bizType = bizType;
		this.channel = channel;
		this.amountCents = amountCents;
		this.paidAt = paidAt;
		this.tradeNo = tradeNo;
		this.notifyData = notifyData;
	}

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public Long getOrderId() {
		return orderId;
	}

	public String getBizType() {
		return bizType;
	}

	public String getChannel() {
		return channel;
	}

	public Long getAmountCents() {
		return amountCents;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public String getTradeNo() {
		return tradeNo;
	}

	public String getNotifyData() {
		return notifyData;
	}

}
