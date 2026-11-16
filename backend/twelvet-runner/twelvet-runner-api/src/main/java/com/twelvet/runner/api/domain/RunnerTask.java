package com.twelvet.runner.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 互助任务（twelve_runner_task）
 * <p>
 * 状态机：pending → accepted → running → pending_confirm → completed； 分支：cancelled / disputed
 * / refund
 * </p>
 *
 * @author twelvet
 */
public class RunnerTask extends BaseEntity {

	/** 任务状态：待接单 */
	public static final String STATUS_PENDING = "pending";

	/** 任务状态：已接单 */
	public static final String STATUS_ACCEPTED = "accepted";

	/** 任务状态：服务中 */
	public static final String STATUS_RUNNING = "running";

	/** 任务状态：待下单人确认 */
	public static final String STATUS_PENDING_CONFIRM = "pending_confirm";

	/** 任务状态：已完成 */
	public static final String STATUS_COMPLETED = "completed";

	/** 任务状态：已取消 */
	public static final String STATUS_CANCELLED = "cancelled";

	/** 任务状态：纠纷中 */
	public static final String STATUS_DISPUTED = "disputed";

	/** 任务状态：退款中 */
	public static final String STATUS_REFUND = "refund";

	/** 任务类型：代取快递 */
	public static final int TYPE_PICKUP = 1;

	/** 任务类型：代买东西 */
	public static final int TYPE_BUY = 2;

	/** 任务类型：代排队 */
	public static final int TYPE_QUEUE = 3;

	/** 任务类型：其他 */
	public static final int TYPE_OTHER = 4;

	/** 付费 */
	public static final int IS_PAID_YES = 1;

	/** 免费 */
	public static final int IS_PAID_NO = 0;

	/** 支付状态：未支付 */
	public static final String PAY_STATUS_UNPAID = "unpaid";

	/** 支付状态：已支付 */
	public static final String PAY_STATUS_PAID = "paid";

	/** 支付状态：已退款 */
	public static final String PAY_STATUS_REFUNDED = "refunded";

	private Long id;

	private String taskNo;

	private Long publisherId;

	private Long assigneeId;

	private Integer taskType;

	private String title;

	private String description;

	private String pickupAddr;

	private String deliverAddr;

	private BigDecimal pickupLat;

	private BigDecimal pickupLng;

	private BigDecimal deliverLat;

	private BigDecimal deliverLng;

	private Integer isPaid;

	private BigDecimal price;

	private String status;

	private LocalDateTime expectTime;

	private LocalDateTime deadline;

	private LocalDateTime assignTime;

	private LocalDateTime finishTime;

	private LocalDateTime confirmTime;

	private String cancelReason;

	private String refundReason;

	private String refundStatus;

	private Long payOrderId;

	private String payOutTradeNo;

	private String payStatus;

	private String images;

	private String finishImages;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTaskNo() {
		return taskNo;
	}

	public void setTaskNo(String taskNo) {
		this.taskNo = taskNo;
	}

	public Long getPublisherId() {
		return publisherId;
	}

	public void setPublisherId(Long publisherId) {
		this.publisherId = publisherId;
	}

	public Long getAssigneeId() {
		return assigneeId;
	}

	public void setAssigneeId(Long assigneeId) {
		this.assigneeId = assigneeId;
	}

	public Integer getTaskType() {
		return taskType;
	}

	public void setTaskType(Integer taskType) {
		this.taskType = taskType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPickupAddr() {
		return pickupAddr;
	}

	public void setPickupAddr(String pickupAddr) {
		this.pickupAddr = pickupAddr;
	}

	public String getDeliverAddr() {
		return deliverAddr;
	}

	public void setDeliverAddr(String deliverAddr) {
		this.deliverAddr = deliverAddr;
	}

	public BigDecimal getPickupLat() {
		return pickupLat;
	}

	public void setPickupLat(BigDecimal pickupLat) {
		this.pickupLat = pickupLat;
	}

	public BigDecimal getPickupLng() {
		return pickupLng;
	}

	public void setPickupLng(BigDecimal pickupLng) {
		this.pickupLng = pickupLng;
	}

	public BigDecimal getDeliverLat() {
		return deliverLat;
	}

	public void setDeliverLat(BigDecimal deliverLat) {
		this.deliverLat = deliverLat;
	}

	public BigDecimal getDeliverLng() {
		return deliverLng;
	}

	public void setDeliverLng(BigDecimal deliverLng) {
		this.deliverLng = deliverLng;
	}

	public Integer getIsPaid() {
		return isPaid;
	}

	public void setIsPaid(Integer isPaid) {
		this.isPaid = isPaid;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getExpectTime() {
		return expectTime;
	}

	public void setExpectTime(LocalDateTime expectTime) {
		this.expectTime = expectTime;
	}

	public LocalDateTime getDeadline() {
		return deadline;
	}

	public void setDeadline(LocalDateTime deadline) {
		this.deadline = deadline;
	}

	public LocalDateTime getAssignTime() {
		return assignTime;
	}

	public void setAssignTime(LocalDateTime assignTime) {
		this.assignTime = assignTime;
	}

	public LocalDateTime getFinishTime() {
		return finishTime;
	}

	public void setFinishTime(LocalDateTime finishTime) {
		this.finishTime = finishTime;
	}

	public LocalDateTime getConfirmTime() {
		return confirmTime;
	}

	public void setConfirmTime(LocalDateTime confirmTime) {
		this.confirmTime = confirmTime;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		this.cancelReason = cancelReason;
	}

	public String getRefundReason() {
		return refundReason;
	}

	public void setRefundReason(String refundReason) {
		this.refundReason = refundReason;
	}

	public String getRefundStatus() {
		return refundStatus;
	}

	public void setRefundStatus(String refundStatus) {
		this.refundStatus = refundStatus;
	}

	public Long getPayOrderId() {
		return payOrderId;
	}

	public void setPayOrderId(Long payOrderId) {
		this.payOrderId = payOrderId;
	}

	public String getPayOutTradeNo() {
		return payOutTradeNo;
	}

	public void setPayOutTradeNo(String payOutTradeNo) {
		this.payOutTradeNo = payOutTradeNo;
	}

	public String getPayStatus() {
		return payStatus;
	}

	public void setPayStatus(String payStatus) {
		this.payStatus = payStatus;
	}

	public String getImages() {
		return images;
	}

	public void setImages(String images) {
		this.images = images;
	}

	public String getFinishImages() {
		return finishImages;
	}

	public void setFinishImages(String finishImages) {
		this.finishImages = finishImages;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		RunnerTask that = (RunnerTask) o;
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : 0;
	}

}
