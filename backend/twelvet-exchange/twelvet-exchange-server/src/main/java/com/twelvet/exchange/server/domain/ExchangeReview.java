package com.twelvet.exchange.server.domain;

import java.time.LocalDateTime;

/**
 * 交换双向评价（twelve_exchange_review）
 * <p>
 * completed 后双向评价（1-5 星 + 文字），uk_order_reviewer 防重复。
 * </p>
 *
 * @author twelvet
 */
public class ExchangeReview {

	private Long id;

	private Long orderId;

	private Long reviewerId;

	private Long revieweeId;

	private Integer score;

	private String content;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
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

	public Integer getScore() {
		return score;
	}

	public void setScore(Integer score) {
		this.score = score;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
