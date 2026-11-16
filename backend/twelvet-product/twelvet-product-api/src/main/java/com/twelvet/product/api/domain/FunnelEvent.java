package com.twelvet.product.api.domain;

import java.io.Serializable;

/**
 * 核心漏斗事件（产品策略 spec §6.3）。
 * <p>
 * 10 类事件全部由服务端在权威业务方法内记录：不接受客户端上报，避免伪造漏斗。 服务端事件用 {@code eventKey = "<event_type>:<业务ID>"}
 * 做幂等，配合 {@code uk_event_key} 与 {@code INSERT IGNORE}，支付回调 / 超时扫描重试不会重复计数。
 * </p>
 *
 * @author twelvet
 */
public class FunnelEvent implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String TYPE_PRODUCT_EXPOSURE = "product_exposure";

	public static final String TYPE_PRODUCT_DETAIL_VIEW = "product_detail_view";

	public static final String TYPE_IM_START = "im_start";

	public static final String TYPE_ORDER_CREATE = "order_create";

	public static final String TYPE_PAY_SUCCESS = "pay_success";

	public static final String TYPE_SHIP = "ship";

	public static final String TYPE_CONFIRM_RECEIPT = "confirm_receipt";

	public static final String TYPE_REFUND_APPLY = "refund_apply";

	public static final String TYPE_REFUND_SUCCESS = "refund_success";

	public static final String TYPE_REVIEW_CREATE = "review_create";

	/** 租户/社区ID；为空时由服务从 TenantContext 补齐 */
	private Long tenantId;

	/** 10 类事件之一，取值见 TYPE_* 常量 */
	private String eventType;

	private Long userId;

	/** buyer / seller / visitor */
	private String role;

	private Long productId;

	/** product.cat_l1 快照 */
	private String category;

	/** 1=AI 辅助产生（AI 导购下单）；空值按 false 落库 */
	private Boolean aiAssisted;

	/** home_recommend / nearby / search / detail */
	private String scene;

	/** 订单 / 退款 / 评价 / 会话ID */
	private String bizId;

	/** 幂等键：服务端事件必填，格式 "<event_type>:<业务ID>" */
	private String eventKey;

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Boolean getAiAssisted() {
		return aiAssisted;
	}

	public void setAiAssisted(Boolean aiAssisted) {
		this.aiAssisted = aiAssisted;
	}

	public String getScene() {
		return scene;
	}

	public void setScene(String scene) {
		this.scene = scene;
	}

	public String getBizId() {
		return bizId;
	}

	public void setBizId(String bizId) {
		this.bizId = bizId;
	}

	public String getEventKey() {
		return eventKey;
	}

	public void setEventKey(String eventKey) {
		this.eventKey = eventKey;
	}

}