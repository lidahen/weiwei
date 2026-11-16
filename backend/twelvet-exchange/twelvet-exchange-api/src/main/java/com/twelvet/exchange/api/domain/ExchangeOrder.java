package com.twelvet.exchange.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 交换订单（twelve_exchange_order）
 * <p>
 * 双边履约状态机（与互助任务单边履约的本质差异）：created → delivering（任一方交付）→ pending_confirm（双方 均交付）→
 * completed（双方均确认）；分支：cancelled（双方均未交付）/ disputed（delivering/ pending_confirm 可发起，后台裁决
 * force-complete / force-cancel）。 A/B 各自的交付与确认是四个独立时间戳 列，status 由状态机按列填充情况推进。
 * </p>
 *
 * @author twelvet
 */
public class ExchangeOrder extends BaseEntity {

	/** 订单状态：已建立（双方物品 locked） */
	public static final String STATUS_CREATED = "created";

	/** 订单状态：交付中（部分交付） */
	public static final String STATUS_DELIVERING = "delivering";

	/** 订单状态：待双方确认收货 */
	public static final String STATUS_PENDING_CONFIRM = "pending_confirm";

	/** 订单状态：已完成 */
	public static final String STATUS_COMPLETED = "completed";

	/** 订单状态：已取消 */
	public static final String STATUS_CANCELLED = "cancelled";

	/** 订单状态：纠纷中 */
	public static final String STATUS_DISPUTED = "disputed";

	/** 订单侧：A（物品主人） */
	public static final String SIDE_A = "a";

	/** 订单侧：B（发起人） */
	public static final String SIDE_B = "b";

	/** 交付方式：面交 */
	public static final String DELIVERY_FACE = "face";

	/** 交付方式：快递 */
	public static final String DELIVERY_EXPRESS = "express";

	/** 面交码有效期（天） */
	public static final int PICKUP_CODE_EXPIRE_DAYS = 7;

	/** 建单超时（天）：超时未双交付自动取消 */
	public static final int CREATED_TIMEOUT_DAYS = 7;

	/** 待确认超时（天）：超时未双确认自动完成（默认收货） */
	public static final int CONFIRM_TIMEOUT_DAYS = 3;

	private Long id;

	private String orderNo;

	private Long offerId;

	private Long itemAId;

	private Long itemBId;

	private Long ownerAId;

	private Long ownerBId;

	private String deliveryMode;

	private String status;

	private LocalDateTime aDeliveredTime;

	private LocalDateTime bDeliveredTime;

	private LocalDateTime aConfirmedTime;

	private LocalDateTime bConfirmedTime;

	private String aTrackNo;

	private String bTrackNo;

	private String cancelReason;

	private String disputeReason;

	private String disputeResult;

	private LocalDateTime completeTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getOfferId() {
		return offerId;
	}

	public void setOfferId(Long offerId) {
		this.offerId = offerId;
	}

	public Long getItemAId() {
		return itemAId;
	}

	public void setItemAId(Long itemAId) {
		this.itemAId = itemAId;
	}

	public Long getItemBId() {
		return itemBId;
	}

	public void setItemBId(Long itemBId) {
		this.itemBId = itemBId;
	}

	public Long getOwnerAId() {
		return ownerAId;
	}

	public void setOwnerAId(Long ownerAId) {
		this.ownerAId = ownerAId;
	}

	public Long getOwnerBId() {
		return ownerBId;
	}

	public void setOwnerBId(Long ownerBId) {
		this.ownerBId = ownerBId;
	}

	public String getDeliveryMode() {
		return deliveryMode;
	}

	public void setDeliveryMode(String deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getADeliveredTime() {
		return aDeliveredTime;
	}

	public void setADeliveredTime(LocalDateTime aDeliveredTime) {
		this.aDeliveredTime = aDeliveredTime;
	}

	public LocalDateTime getBDeliveredTime() {
		return bDeliveredTime;
	}

	public void setBDeliveredTime(LocalDateTime bDeliveredTime) {
		this.bDeliveredTime = bDeliveredTime;
	}

	public LocalDateTime getAConfirmedTime() {
		return aConfirmedTime;
	}

	public void setAConfirmedTime(LocalDateTime aConfirmedTime) {
		this.aConfirmedTime = aConfirmedTime;
	}

	public LocalDateTime getBConfirmedTime() {
		return bConfirmedTime;
	}

	public void setBConfirmedTime(LocalDateTime bConfirmedTime) {
		this.bConfirmedTime = bConfirmedTime;
	}

	public String getATrackNo() {
		return aTrackNo;
	}

	public void setATrackNo(String aTrackNo) {
		this.aTrackNo = aTrackNo;
	}

	public String getBTrackNo() {
		return bTrackNo;
	}

	public void setBTrackNo(String bTrackNo) {
		this.bTrackNo = bTrackNo;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		this.cancelReason = cancelReason;
	}

	public String getDisputeReason() {
		return disputeReason;
	}

	public void setDisputeReason(String disputeReason) {
		this.disputeReason = disputeReason;
	}

	public String getDisputeResult() {
		return disputeResult;
	}

	public void setDisputeResult(String disputeResult) {
		this.disputeResult = disputeResult;
	}

	public LocalDateTime getCompleteTime() {
		return completeTime;
	}

	public void setCompleteTime(LocalDateTime completeTime) {
		this.completeTime = completeTime;
	}

	/** A 物品标题（后台联表冗余字段，非表列） */
	private String itemATitle;

	/** B 物品标题（后台联表冗余字段，非表列） */
	private String itemBTitle;

	/** A 用户昵称（后台联表冗余字段，非表列） */
	private String ownerAName;

	/** B 用户昵称（后台联表冗余字段，非表列） */
	private String ownerBName;

	public String getItemATitle() {
		return itemATitle;
	}

	public void setItemATitle(String itemATitle) {
		this.itemATitle = itemATitle;
	}

	public String getItemBTitle() {
		return itemBTitle;
	}

	public void setItemBTitle(String itemBTitle) {
		this.itemBTitle = itemBTitle;
	}

	public String getOwnerAName() {
		return ownerAName;
	}

	public void setOwnerAName(String ownerAName) {
		this.ownerAName = ownerAName;
	}

	public String getOwnerBName() {
		return ownerBName;
	}

	public void setOwnerBName(String ownerBName) {
		this.ownerBName = ownerBName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ExchangeOrder that = (ExchangeOrder) o;
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : 0;
	}

}
