package com.twelvet.im.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 消息对象 im_message
 *
 * @author twelvet
 */
@Schema(description = "消息")
public class ImMessage {

	private static final long serialVersionUID = 1L;

	@Schema(description = "消息ID")
	private Long msgId;

	@Schema(description = "会话ID")
	private Long convId;

	@Schema(description = "发送者ID")
	private Long senderId;

	@Schema(description = "接收者ID")
	private Long receiverId;

	@Schema(description = "消息类型(text/image/bargain/system/product_card)")
	private String type;

	@Schema(description = "消息内容")
	private String content;

	@Schema(description = "图片URL")
	private String imageUrl;

	@Schema(description = "状态(sending/sent/delivered/read/failed)")
	private String status;

	@Schema(description = "是否撤回")
	private Integer recalled;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private Long tenantId;

	public Long getMsgId() {
		return msgId;
	}

	public void setMsgId(Long msgId) {
		this.msgId = msgId;
	}

	public Long getConvId() {
		return convId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public void setConvId(Long convId) {
		this.convId = convId;
	}

	public Long getSenderId() {
		return senderId;
	}

	public void setSenderId(Long senderId) {
		this.senderId = senderId;
	}

	public Long getReceiverId() {
		return receiverId;
	}

	public void setReceiverId(Long receiverId) {
		this.receiverId = receiverId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getRecalled() {
		return recalled;
	}

	public void setRecalled(Integer recalled) {
		this.recalled = recalled;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
