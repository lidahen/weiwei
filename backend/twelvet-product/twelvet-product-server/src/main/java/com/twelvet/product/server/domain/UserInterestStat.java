package com.twelvet.product.server.domain;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户兴趣画像（首页推荐召回用，Quartz 定时全量重建）
 * <p>
 * 行为权重：浏览×1 / 收藏×3 / 下单×5，按 exp(-ln2/14 × 天数) 时间衰减（半衰期 14 天），窗口近 30 天
 * </p>
 */
public class UserInterestStat implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private Long id;

	private Long tenantId;

	private Long userId;

	private String catL1;

	private String catL2;

	private BigDecimal weight;

	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getCatL1() {
		return catL1;
	}

	public void setCatL1(String catL1) {
		this.catL1 = catL1;
	}

	public String getCatL2() {
		return catL2;
	}

	public void setCatL2(String catL2) {
		this.catL2 = catL2;
	}

	public BigDecimal getWeight() {
		return weight;
	}

	public void setWeight(BigDecimal weight) {
		this.weight = weight;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

}
