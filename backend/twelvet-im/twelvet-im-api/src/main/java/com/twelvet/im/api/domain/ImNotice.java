package com.twelvet.im.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通知对象 im_notice
 *
 * @author twelvet
 */
@Schema(description = "通知")
public class ImNotice {

	private static final long serialVersionUID = 1L;

	@Schema(description = "通知ID")
	private Long noticeId;

	@Schema(description = "用户ID")
	private Long userId;

	@Schema(description = "类型(order/platform/interact)")
	private String type;

	@Schema(description = "标题")
	private String title;

	@Schema(description = "内容")
	private String content;

	@Schema(description = "是否已读")
	private Integer isRead;

	@Schema(description = "创建时间")
	private String createTime;

	public Long getNoticeId() {
		return noticeId;
	}

	public void setNoticeId(Long noticeId) {
		this.noticeId = noticeId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getIsRead() {
		return isRead;
	}

	public void setIsRead(Integer isRead) {
		this.isRead = isRead;
	}

	public String getCreateTime() {
		return createTime;
	}

	public void setCreateTime(String createTime) {
		this.createTime = createTime;
	}

}
