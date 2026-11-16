package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖家余额账本流水 seller_balance_flow（只追加）
 * <p>
 * uk_biz_key 承担重复消费幂等：同一业务键第二次写入直接命中重复键、不重复记账。 balanceAfter 必须与 seller_account.balance
 * 保持一致，由对账任务校验。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "卖家余额流水")
public class SellerBalanceFlow implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "流水ID")
	private Long flowId;

	@Schema(description = "账户ID")
	private Long accountId;

	@Schema(description = "卖家用户ID")
	private Long sellerId;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "结算单ID")
	private Long settlementId;

	@Schema(description = "退款单ID")
	private Long refundId;

	@Schema(description = "提现单ID")
	private Long withdrawId;

	@Schema(description = "类型 settle_in/clawback/withdraw_out/adjust")
	private String type;

	@Schema(description = "金额：正数入账，负数扣减")
	private BigDecimal amount;

	@Schema(description = "变动后余额")
	private BigDecimal balanceAfter;

	@Schema(description = "幂等键")
	private String bizKey;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	public Long getFlowId() {
		return flowId;
	}

	public void setFlowId(Long flowId) {
		this.flowId = flowId;
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

	public Long getSettlementId() {
		return settlementId;
	}

	public void setSettlementId(Long settlementId) {
		this.settlementId = settlementId;
	}

	public Long getRefundId() {
		return refundId;
	}

	public void setRefundId(Long refundId) {
		this.refundId = refundId;
	}

	public Long getWithdrawId() {
		return withdrawId;
	}

	public void setWithdrawId(Long withdrawId) {
		this.withdrawId = withdrawId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public void setBalanceAfter(BigDecimal balanceAfter) {
		this.balanceAfter = balanceAfter;
	}

	public String getBizKey() {
		return bizKey;
	}

	public void setBizKey(String bizKey) {
		this.bizKey = bizKey;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}