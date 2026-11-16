package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算对账差异 settlement_recon_diff
 * <p>
 * uk_diff(biz_date, type, biz_key) 保证同一业务日同一类型同一业务键只入队一次。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "结算对账差异")
public class SettlementReconDiff implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "差异ID")
	private Long diffId;

	@Schema(description = "对账业务日")
	private LocalDate bizDate;

	@Schema(description = "差异类型")
	private String type;

	@Schema(description = "本类型内幂等键")
	private String bizKey;

	@Schema(description = "订单ID")
	private Long orderId;

	@Schema(description = "结算单ID")
	private Long settlementId;

	@Schema(description = "卖家用户ID")
	private Long sellerId;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "渠道金额")
	private BigDecimal channelAmount;

	@Schema(description = "平台金额")
	private BigDecimal platformAmount;

	@Schema(description = "状态 open/resolved")
	private String status;

	@Schema(description = "处理人")
	private Long resolvedBy;

	@Schema(description = "处理说明")
	private String resolvedNote;

	@Schema(description = "处理时间")
	private LocalDateTime resolvedAt;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	public Long getDiffId() {
		return diffId;
	}

	public void setDiffId(Long diffId) {
		this.diffId = diffId;
	}

	public LocalDate getBizDate() {
		return bizDate;
	}

	public void setBizDate(LocalDate bizDate) {
		this.bizDate = bizDate;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getBizKey() {
		return bizKey;
	}

	public void setBizKey(String bizKey) {
		this.bizKey = bizKey;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public Long getSettlementId() {
		return settlementId;
	}

	public void setSettlementId(Long settlementId) {
		this.settlementId = settlementId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public BigDecimal getChannelAmount() {
		return channelAmount;
	}

	public void setChannelAmount(BigDecimal channelAmount) {
		this.channelAmount = channelAmount;
	}

	public BigDecimal getPlatformAmount() {
		return platformAmount;
	}

	public void setPlatformAmount(BigDecimal platformAmount) {
		this.platformAmount = platformAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getResolvedBy() {
		return resolvedBy;
	}

	public void setResolvedBy(Long resolvedBy) {
		this.resolvedBy = resolvedBy;
	}

	public String getResolvedNote() {
		return resolvedNote;
	}

	public void setResolvedNote(String resolvedNote) {
		this.resolvedNote = resolvedNote;
	}

	public LocalDateTime getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(LocalDateTime resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}