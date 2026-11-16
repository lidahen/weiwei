package com.twelvet.product.api.domain;

import org.apache.ibatis.type.Alias;
import java.util.Date;

/**
 * 动态评论实体
 */
@Alias("productFeedComment")
public class FeedComment {

	private Long commentId;

	private Long feedId;

	private Long userId;

	private Long replyToId;

	private Long replyToUserId;

	private String content;

	private String status;

	private Date createTime;

	private Long tenantId;

	// Derived fields
	private String userNickName;

	private String userAvatar;

	private String replyToUserName;

	public Long getCommentId() {
		return commentId;
	}

	public void setCommentId(Long commentId) {
		this.commentId = commentId;
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

	public Long getReplyToId() {
		return replyToId;
	}

	public void setReplyToId(Long replyToId) {
		this.replyToId = replyToId;
	}

	public Long getReplyToUserId() {
		return replyToUserId;
	}

	public void setReplyToUserId(Long replyToUserId) {
		this.replyToUserId = replyToUserId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
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

	public String getReplyToUserName() {
		return replyToUserName;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public void setReplyToUserName(String replyToUserName) {
		this.replyToUserName = replyToUserName;
	}

}
