package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现申请 trade_withdraw
 * <p>
 * Phase 1 人工提现：申请即扣余额（withdraw_out），驳回用 adjust 退回，paid 不再动余额。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "提现申请")
public class TradeWithdraw implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "提现单ID")
	private Long withdrawId;

	@Schema(description = "对外提现单号")
	private String withdrawNo;

	@Schema(description = "账户ID")
	private Long accountId;

	@Schema(description = "卖家用户ID")
	private Long sellerId;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "提现金额")
	private BigDecimal amount;

	@Schema(description = "状态 pending/approved/paid/rejected")
	private String status;

	@Schema(description = "审核/打款人")
	private Long operatorId;

	@Schema(description = "线下打款凭证")
	private String voucherUrl;

	@Schema(description = "驳回/失败原因")
	private String failReason;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	public Long getWithdrawId() {
		return withdrawId;
	}

	public void setWithdrawId(Long withdrawId) {
		this.withdrawId = withdrawId;
	}

	public String getWithdrawNo() {
		return withdrawNo;
	}

	public void setWithdrawNo(String withdrawNo) {
		this.withdrawNo = withdrawNo;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public void setOperatorId(Long operatorId) {
		this.operatorId = operatorId;
	}

	public String getVoucherUrl() {
		return voucherUrl;
	}

	public void setVoucherUrl(String voucherUrl) {
		this.voucherUrl = voucherUrl;
	}

	public String getFailReason() {
		return failReason;
	}

	public void setFailReason(String failReason) {
		this.failReason = failReason;
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