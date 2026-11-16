package com.twelvet.ai.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI聊天历史实体
 */
@Schema(description = "AI聊天历史")
public class AiChatHistory implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "消息ID")
	private Long msgId;

	@Schema(description = "知识库ID")
	private Long knowledgeId;

	@Schema(description = "会话ID")
	private String sessionId;

	@Schema(description = "角色：USER-用户，ASSISTANT-AI")
	private String role;

	@Schema(description = "消息内容")
	private String content;

	@Schema(description = "创建者")
	private String createBy;

	@Schema(description = "创建时间")
	private Date createTime;

	public Long getMsgId() {
		return msgId;
	}

	public void setMsgId(Long msgId) {
		this.msgId = msgId;
	}

	public Long getKnowledgeId() {
		return knowledgeId;
	}

	public void setKnowledgeId(Long knowledgeId) {
		this.knowledgeId = knowledgeId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getCreateBy() {
		return createBy;
	}

	public void setCreateBy(String createBy) {
		this.createBy = createBy;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

}