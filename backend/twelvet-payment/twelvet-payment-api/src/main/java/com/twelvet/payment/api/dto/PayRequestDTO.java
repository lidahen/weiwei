package com.twelvet.payment.api.dto;

import java.math.BigDecimal;

/**
 * 支付请求 DTO
 *
 * @author twelvet
 */
public class PayRequestDTO {

	/** 业务订单ID（商品订单/互助任务等；租户续费时为 null） */
	private Long orderId;

	/** 支付单发起人（收银台创建者；mock-pay/归属校验的权威来源。租户续费等非个人路径为 null） */
	private Long payUserId;

	/** 业务订单号 */
	private String orderNo;

	/** 业务类型：trade(商品订单) / runner(互助任务)；租户续费走独立 SQL 写死 renew。空时服务端默认 trade */
	private String bizType;

	/** 支付渠道：alipay / wechat */
	private String channel;

	/** 金额 */
	private BigDecimal amount;

	/** 商品描述 */
	private String subject;

	/** 同步跳转地址 */
	private String returnUrl;

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public Long getPayUserId() {
		return payUserId;
	}

	public void setPayUserId(Long payUserId) {
		this.payUserId = payUserId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getBizType() {
		return bizType;
	}

	public void setBizType(String bizType) {
		this.bizType = bizType;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getReturnUrl() {
		return returnUrl;
	}

	public void setReturnUrl(String returnUrl) {
		this.returnUrl = returnUrl;
	}

}
