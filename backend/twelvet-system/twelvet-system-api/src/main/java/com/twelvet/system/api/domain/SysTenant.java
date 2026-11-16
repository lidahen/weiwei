package com.twelvet.system.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author twelvet
 * @Description: 租户实体（MVP：商品表 tenant_id 隔离；商业化 P0 加 plan_code/expire_at/quota_product_max）
 */
@Schema(description = "租户")
public class SysTenant implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "租户ID")
	private Long id;

	@Schema(description = "租户编码（唯一，如 thu/pku）")
	private String tenantCode;

	@Schema(description = "租户名称")
	private String tenantName;

	@Schema(description = "状态 0=正常 1=停用")
	private String status;

	@Schema(description = "套餐编码：free/basic/pro")
	private String planCode;

	@Schema(description = "套餐到期时间，NULL=永不过期")
	private LocalDateTime expireAt;

	@Schema(description = "商品数量上限，0=不限")
	private Integer quotaProductMax;

	@Schema(description = "每日AI调用上限，0=不限")
	private Integer quotaAiCallDay;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

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

	public String getTenantName() {
		return tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public Integer getQuotaAiCallDay() {
		return quotaAiCallDay;
	}

	public void setQuotaAiCallDay(Integer quotaAiCallDay) {
		this.quotaAiCallDay = quotaAiCallDay;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
