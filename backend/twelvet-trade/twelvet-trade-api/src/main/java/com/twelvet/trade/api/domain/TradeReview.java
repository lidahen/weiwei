package com.twelvet.trade.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易评价对象 trade_review
 * <p>
 * 评分挂在订单上，买卖双方各评一条（role=buyer/seller），订单确认收货（done）后可评。 设计文档：docs/交易评价设计.md
 *
 * @author twelvet
 */
@Schema(description = "交易评价")
public class TradeReview implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "评价ID")
	private Long reviewId;

	@Schema(description = "订单ID")
	private Long orderId;

	@Schema(description = "评价方向 buyer=买家评卖家 seller=卖家评买家")
	private String role;

	@Schema(description = "评价人ID")
	private Long reviewerId;

	@Schema(description = "被评价人ID")
	private Long revieweeId;

	@Schema(description = "商品ID(冗余自订单)")
	private Long productId;

	@Schema(description = "评分 1-5")
	private Integer overallScore;

	@Schema(description = "评语")
	private String content;

	@Schema(description = "图片 JSON 数组(相对路径)")
	private String images;

	@Schema(description = "标签 JSON 数组")
	private String tags;

	@Schema(description = "是否匿名")
	private Boolean anonymous;

	@Schema(description = "追评(预留)")
	private String supplement;

	@Schema(description = "追评时间(预留)")
	private LocalDateTime supplementTime;

	@Schema(description = "状态 normal/hidden")
	private String status;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "评价时间")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	// ---- join 展示字段 ----

	@Schema(description = "评价人昵称(匿名时为匿名用户)")
	private String reviewerNickName;

	@Schema(description = "评价人头像(匿名时为空)")
	private String reviewerAvatar;

	@Schema(description = "订单号(管理端列表用)")
	private String orderNo;

	@Schema(description = "商品标题(管理端列表用)")
	private String productTitle;

	public Long getReviewId() {
		return reviewId;
	}

	public void setReviewId(Long reviewId) {
		this.reviewId = reviewId;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Long getReviewerId() {
		return reviewerId;
	}

	public void setReviewerId(Long reviewerId) {
		this.reviewerId = reviewerId;
	}

	public Long getRevieweeId() {
		return revieweeId;
	}

	public void setRevieweeId(Long revieweeId) {
		this.revieweeId = revieweeId;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public Integer getOverallScore() {
		return overallScore;
	}

	public void setOverallScore(Integer overallScore) {
		this.overallScore = overallScore;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getImages() {
		return images;
	}

	public void setImages(String images) {
		this.images = images;
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags;
	}

	public Boolean getAnonymous() {
		return anonymous;
	}

	public void setAnonymous(Boolean anonymous) {
		this.anonymous = anonymous;
	}

	public String getSupplement() {
		return supplement;
	}

	public void setSupplement(String supplement) {
		this.supplement = supplement;
	}

	public LocalDateTime getSupplementTime() {
		return supplementTime;
	}

	public void setSupplementTime(LocalDateTime supplementTime) {
		this.supplementTime = supplementTime;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public String getReviewerNickName() {
		return reviewerNickName;
	}

	public void setReviewerNickName(String reviewerNickName) {
		this.reviewerNickName = reviewerNickName;
	}

	public String getReviewerAvatar() {
		return reviewerAvatar;
	}

	public void setReviewerAvatar(String reviewerAvatar) {
		this.reviewerAvatar = reviewerAvatar;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getProductTitle() {
		return productTitle;
	}

	public void setProductTitle(String productTitle) {
		this.productTitle = productTitle;
	}

}
