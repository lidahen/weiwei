package com.twelvet.product.server.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推荐曝光流水（CTR 统计数据源；点击=曝光后 30 分钟内同用户浏览该商品）
 */
public class RecommendImpression implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private Long id;

	private Long tenantId;

	private Long userId;

	private Long productId;

	private String scene;

	private Integer position;

	/** A/B 分组：0=A 默认 / 1=B 实验组（未开启 A/B 恒为 0） */
	private Integer bucket;

	private java.time.LocalDateTime createTime;

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

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getScene() {
		return scene;
	}

	public void setScene(String scene) {
		this.scene = scene;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}

	public Integer getBucket() {
		return bucket;
	}

	public void setBucket(Integer bucket) {
		this.bucket = bucket;
	}

	public java.time.LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(java.time.LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
