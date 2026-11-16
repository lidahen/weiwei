package com.twelvet.product.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import org.apache.ibatis.type.Alias;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态实体
 */
@Alias("productFeed")
public class Feed extends BaseEntity {

	private Long feedId;

	private Long userId;

	private String content;

	private String images; // JSON array of image URLs, max 9

	private Long relatedProductId;

	private String visibility; // public/friends/self

	private Integer likeCount;

	private Integer commentCount;

	private Integer forwardCount;

	private BigDecimal hotScore;

	private String status; // normal/deleted

	private String auditStatus; // pending/approved/rejected

	private String auditReason;

	private Long originalFeedId;

	private LocalDateTime publishTime;

	// Derived fields for list/detail
	private String userNickName;

	private String userAvatar;

	private Boolean liked;

	private Boolean favorited;

	private String relatedProductTitle;

	private String relatedProductCover;

	private Integer relatedProductPrice;

	private java.util.List<FeedComment> comments;

	// AI 初审展示字段（仅审核列表查询联表填充，insert/update 不涉及；见 docs/AI初审与发布助手设计.md）
	private String aiRiskLevel;

	private String aiAction;

	private String aiSummary;

	private String aiViolations;

	public java.util.List<FeedComment> getComments() {
		return comments;
	}

	public void setComments(java.util.List<FeedComment> comments) {
		this.comments = comments;
	}

	public String getAiRiskLevel() {
		return aiRiskLevel;
	}

	public void setAiRiskLevel(String aiRiskLevel) {
		this.aiRiskLevel = aiRiskLevel;
	}

	public String getAiAction() {
		return aiAction;
	}

	public void setAiAction(String aiAction) {
		this.aiAction = aiAction;
	}

	public String getAiSummary() {
		return aiSummary;
	}

	public void setAiSummary(String aiSummary) {
		this.aiSummary = aiSummary;
	}

	public String getAiViolations() {
		return aiViolations;
	}

	public void setAiViolations(String aiViolations) {
		this.aiViolations = aiViolations;
	}

	public Long getFeedId() {
		return feedId;
	}

	public void setFeedId(Long feedId) {
		this.feedId = feedId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
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

	public Long getRelatedProductId() {
		return relatedProductId;
	}

	public void setRelatedProductId(Long relatedProductId) {
		this.relatedProductId = relatedProductId;
	}

	public String getVisibility() {
		return visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public Integer getLikeCount() {
		return likeCount;
	}

	public void setLikeCount(Integer likeCount) {
		this.likeCount = likeCount;
	}

	public Integer getCommentCount() {
		return commentCount;
	}

	public void setCommentCount(Integer commentCount) {
		this.commentCount = commentCount;
	}

	public Integer getForwardCount() {
		return forwardCount;
	}

	public void setForwardCount(Integer forwardCount) {
		this.forwardCount = forwardCount;
	}

	public BigDecimal getHotScore() {
		return hotScore;
	}

	public void setHotScore(BigDecimal hotScore) {
		this.hotScore = hotScore;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAuditStatus() {
		return auditStatus;
	}

	public void setAuditStatus(String auditStatus) {
		this.auditStatus = auditStatus;
	}

	public String getAuditReason() {
		return auditReason;
	}

	public void setAuditReason(String auditReason) {
		this.auditReason = auditReason;
	}

	public Long getOriginalFeedId() {
		return originalFeedId;
	}

	public void setOriginalFeedId(Long originalFeedId) {
		this.originalFeedId = originalFeedId;
	}

	public LocalDateTime getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(LocalDateTime publishTime) {
		this.publishTime = publishTime;
	}

	public String getUserNickName() {
		return userNickName;
	}

	public void setUserNickName(String userNickName) {
		this.userNickName = userNickName;
	}

	public String getUserAvatar() {
		return userAvatar;
	}

	public void setUserAvatar(String userAvatar) {
		this.userAvatar = userAvatar;
	}

	public Boolean getLiked() {
		return liked;
	}

	public void setLiked(Boolean liked) {
		this.liked = liked;
	}

	public Boolean getFavorited() {
		return favorited;
	}

	public void setFavorited(Boolean favorited) {
		this.favorited = favorited;
	}

	public String getRelatedProductTitle() {
		return relatedProductTitle;
	}

	public void setRelatedProductTitle(String relatedProductTitle) {
		this.relatedProductTitle = relatedProductTitle;
	}

	public String getRelatedProductCover() {
		return relatedProductCover;
	}

	public void setRelatedProductCover(String relatedProductCover) {
		this.relatedProductCover = relatedProductCover;
	}

	public Integer getRelatedProductPrice() {
		return relatedProductPrice;
	}

	public void setRelatedProductPrice(Integer relatedProductPrice) {
		this.relatedProductPrice = relatedProductPrice;
	}

}
