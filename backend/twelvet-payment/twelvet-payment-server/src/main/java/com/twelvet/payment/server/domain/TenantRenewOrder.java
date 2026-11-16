package com.twelvet.payment.server.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户套餐续费支付单 sys_tenant_renew_order
 * <p>
 * 履约凭据：支付回调/轮询按 out_trade_no 反查本表， 拿到租户/套餐/月数后自动续费。 status: unpaid → paid（paid =
 * 已支付且已续费，抢占式 UPDATE 保证幂等）
 * </p>
 *
 * @author twelvet
 */
public class TenantRenewOrder {

	private Long id;

	private String tenantCode;

	private String planCode;

	private Integer months;

	private BigDecimal amount;

	private String outTradeNo;

	private String channel;

	private String status;

	private LocalDateTime paidAt;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTenantCode() {
		return tenantCode;
	}

	public void setTenantCode(String tenantCode) {
		this.tenantCode = tenantCode;
	}

	public String getPlanCode() {
		return planCode;
	}

	public void setPlanCode(String planCode) {
		this.planCode = planCode;
	}

	public Integer getMonths() {
		return months;
	}

	public void setMonths(Integer months) {
		this.months = months;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public void setOutTradeNo(String outTradeNo) {
		this.outTradeNo = outTradeNo;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

}
