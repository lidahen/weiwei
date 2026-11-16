package com.twelvet.system.api.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 租户套餐详情 VO（超管查询 + 租户自查）
 */
@Schema(description = "租户套餐详情")
public class SysTenantPlanVO {

	@Schema(description = "租户编码")
	private String tenantCode;

	@Schema(description = "租户名称")
	private String tenantName;

	@Schema(description = "套餐编码：free/basic/pro")
	private String planCode;

	@Schema(description = "套餐到期时间")
	private LocalDateTime expireAt;

	@Schema(description = "商品数量上限（0=不限）")
	private Integer quotaProductMax;

	@Schema(description = "当前已用商品数")
	private Integer productCount;

	@Schema(description = "每日AI调用上限（0=不限）")
	private Integer quotaAiCallDay;

	@Schema(description = "今日已用AI调用数")
	private Long aiCallToday;

	public String getTenantCode() {
		return tenantCode;
	}

	public void setTenantCode(String tenantCode) {
		this.tenantCode = tenantCode;
	}

	public String getTenantName() {
		return tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	public String getPlanCode() {
		return planCode;
	}

	public void setPlanCode(String planCode) {
		this.planCode = planCode;
	}

	public LocalDateTime getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(LocalDateTime expireAt) {
		this.expireAt = expireAt;
	}

	public Integer getQuotaProductMax() {
		return quotaProductMax;
	}

	public void setQuotaProductMax(Integer quotaProductMax) {
		this.quotaProductMax = quotaProductMax;
	}

	public Integer getProductCount() {
		return productCount;
	}

	public void setProductCount(Integer productCount) {
		this.productCount = productCount;
	}

	public Integer getQuotaAiCallDay() {
		return quotaAiCallDay;
	}

	public void setQuotaAiCallDay(Integer quotaAiCallDay) {
		this.quotaAiCallDay = quotaAiCallDay;
	}

	public Long getAiCallToday() {
		return aiCallToday;
	}

	public void setAiCallToday(Long aiCallToday) {
		this.aiCallToday = aiCallToday;
	}

}
