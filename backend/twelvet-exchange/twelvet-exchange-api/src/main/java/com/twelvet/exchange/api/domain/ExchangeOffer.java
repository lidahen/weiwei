package com.twelvet.exchange.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 换物意向（twelve_exchange_offer）
 * <p>
 * B 对 A 的物品发起的交换请求。状态机：pending → accepted（A 接受，进订单）/ rejected（A 婉拒或物品成交 批量关闭）/
 * expired（72h 未处理）/ withdrawn（B 撤回）/ closed（物品下架批量关闭）
 * </p>
 *
 * @author twelvet
 */
public class ExchangeOffer extends BaseEntity {

	/** 意向状态：待处理 */
	public static final String STATUS_PENDING = "pending";

	/** 意向状态：已接受 */
	public static final String STATUS_ACCEPTED = "accepted";

	/** 意向状态：已婉拒（含物品成交后的批量关闭） */
	public static final String STATUS_REJECTED = "rejected";

	/** 意向状态：已过期（72h 未处理） */
	public static final String STATUS_EXPIRED = "expired";

	/** 意向状态：已撤回 */
	public static final String STATUS_WITHDRAWN = "withdrawn";

	/** 意向状态：已关闭（物品下架等场景批量关闭） */
	public static final String STATUS_CLOSED = "closed";

	/** 意向有效期（小时），docs/交换广场模块设计方案.md §4.2 */
	public static final int EXPIRE_HOURS = 72;

	private Long id;

	private Long itemId;

	private Long ownerId;

	private Long offerorId;

	private Long offerItemId;

	private String offerSnapshot;

	private String message;

	private BigDecimal diffAmount;

	private String status;

	private LocalDateTime expireTime;

	private LocalDateTime handleTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getItemId() {
		return itemId;
	}

	public void setItemId(Long itemId) {
		this.itemId = itemId;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}

	public Long getOfferorId() {
		return offerorId;
	}

	public void setOfferorId(Long offerorId) {
		this.offerorId = offerorId;
	}

	public Long getOfferItemId() {
		return offerItemId;
	}

	public void setOfferItemId(Long offerItemId) {
		this.offerItemId = offerItemId;
	}

	public String getOfferSnapshot() {
		return offerSnapshot;
	}

	public void setOfferSnapshot(String offerSnapshot) {
		this.offerSnapshot = offerSnapshot;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public BigDecimal getDiffAmount() {
		return diffAmount;
	}

	public void setDiffAmount(BigDecimal diffAmount) {
		this.diffAmount = diffAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(LocalDateTime expireTime) {
		this.expireTime = expireTime;
	}

	public LocalDateTime getHandleTime() {
		return handleTime;
	}

	public void setHandleTime(LocalDateTime handleTime) {
		this.handleTime = handleTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ExchangeOffer that = (ExchangeOffer) o;
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : 0;
	}

}
