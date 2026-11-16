package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单 settlement_order（一单一结算）
 * <p>
 * 状态推进一律 SQL 带前置状态守卫 + 影响行数检查；uk_order_id 承担重复回调幂等。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "结算单")
public class SettlementOrder implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "结算单ID")
	private Long settlementId;

	@Schema(description = "商品订单ID")
	private Long orderId;

	@Schema(description = "关联退款单ID")
	private Long refundId;

	@Schema(description = "卖家用户ID")
	private Long sellerId;

	@Schema(description = "买家用户ID")
	private Long buyerId;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "订单实付金额")
	private BigDecimal grossAmount;

	@Schema(description = "平台佣金")
	private BigDecimal commissionAmount;

	@Schema(description = "应结给卖家金额")
	private BigDecimal netAmount;

	@Schema(description = "状态 holding/pending_settle/settled/withdrawn/closed/clawback")
	private String status;

	@Schema(description = "订单完成时间")
	private LocalDateTime doneAt;

	@Schema(description = "售后期届满时间")
	private LocalDateTime holdUntil;

	@Schema(description = "渠道结算成功时间")
	private LocalDateTime settledAt;

	@Schema(description = "渠道 mock/alipay/wechat")
	private String channel;

	@Schema(description = "渠道结算流水号")
	private String channelTransNo;

	@Schema(description = "打款失败次数")
	private Integer failCount;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	public Long getSettlementId() {
		return settlementId;
	}

	public void setSettlementId(Long settlementId) {
		this.settlementId = settlementId;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public Long getRefundId() {
		return refundId;
	}

	public void setRefundId(Long refundId) {
		this.refundId = refundId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public Long getBuyerId() {
		return buyerId;
	}

	public void setBuyerId(Long buyerId) {
		this.buyerId = buyerId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public BigDecimal getGrossAmount() {
		return grossAmount;
	}

	public void setGrossAmount(BigDecimal grossAmount) {
		this.grossAmount = grossAmount;
	}

	public BigDecimal getCommissionAmount() {
		return commissionAmount;
	}

	public void setCommissionAmount(BigDecimal commissionAmount) {
		this.commissionAmount = commissionAmount;
	}

	public BigDecimal getNetAmount() {
		return netAmount;
	}

	public void setNetAmount(BigDecimal netAmount) {
		this.netAmount = netAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getDoneAt() {
		return doneAt;
	}

	public void setDoneAt(LocalDateTime doneAt) {
		this.doneAt = doneAt;
	}

	public LocalDateTime getHoldUntil() {
		return holdUntil;
	}

	public void setHoldUntil(LocalDateTime holdUntil) {
		this.holdUntil = holdUntil;
	}

	public LocalDateTime getSettledAt() {
		return settledAt;
	}

	public void setSettledAt(LocalDateTime settledAt) {
		this.settledAt = settledAt;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getChannelTransNo() {
		return channelTransNo;
	}

	public void setChannelTransNo(String channelTransNo) {
		this.channelTransNo = channelTransNo;
	}

	public Integer getFailCount() {
		return failCount;
	}

	public void setFailCount(Integer failCount) {
		this.failCount = failCount;
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

}