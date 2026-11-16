package com.twelvet.payment.api.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体（跨模块共享）
 * <p>
 * 对应数据库 trade_pay 表。 重构后从 twelvet-trade 迁移到 twelvet-payment-api， trade / runner /
 * tenant-renew 均可引用。
 *
 * @author twelvet
 */
public class PayOrder {

	/** 支付宝通道 */
	public static final String CHANNEL_ALIPAY = "alipay";

	/** 微信支付通道 */
	public static final String CHANNEL_WECHAT = "wechat";

	/** 待支付 */
	public static final String STATUS_PENDING = "pending";

	/** 支付中 */
	public static final String STATUS_PAYING = "paying";

	/** 支付成功 */
	public static final String STATUS_SUCCESS = "success";

	/** 支付失败 */
	public static final String STATUS_FAILED = "failed";

	private Long id;

	private String payNo;

	private Long orderId;

	private String orderNo;

	private String channel;

	private BigDecimal amount;

	private String subject;

	private String status;

	private String payUrl;

	private String outTradeNo;

	private String tradeNo;

	private String notifyData;

	private LocalDateTime paidAt;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getPayNo() {
		return payNo;
	}

	public void setPayNo(String payNo) {
		this.payNo = payNo;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPayUrl() {
		return payUrl;
	}

	public void setPayUrl(String payUrl) {
		this.payUrl = payUrl;
	}

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public void setOutTradeNo(String outTradeNo) {
		this.outTradeNo = outTradeNo;
	}

	public String getTradeNo() {
		return tradeNo;
	}

	public void setTradeNo(String tradeNo) {
		this.tradeNo = tradeNo;
	}

	public String getNotifyData() {
		return notifyData;
	}

	public void setNotifyData(String notifyData) {
		this.notifyData = notifyData;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime paidAt) {
		this.paidAt = paidAt;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PayOrder payOrder = (PayOrder) o;
		return id != null && id.equals(payOrder.id);
	}

	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "PayOrder{" + "id=" + id + ", payNo='" + payNo + '\'' + ", orderId=" + orderId + ", orderNo='" + orderNo
				+ '\'' + ", channel='" + channel + '\'' + ", amount=" + amount + ", subject='" + subject + '\''
				+ ", status='" + status + '\'' + ", payUrl='" + payUrl + '\'' + ", outTradeNo='" + outTradeNo + '\''
				+ ", tradeNo='" + tradeNo + '\'' + ", notifyData='" + notifyData + '\'' + ", paidAt=" + paidAt
				+ ", createTime=" + createTime + ", updateTime=" + updateTime + '}';
	}

}
