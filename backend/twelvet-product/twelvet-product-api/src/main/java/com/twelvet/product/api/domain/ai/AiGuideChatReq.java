package com.twelvet.product.api.domain.ai;

import java.util.List;

/**
 * AI 导购对话请求:携带前端保持的完整对话历史
 */
public class AiGuideChatReq {

	private List<ChatMsg> messages;

	/**
	 * 可选：关联的知识库 ID。传入后 Agent 会主动注入知识库上下文。
	 */
	private Long knowledgeId;

	/**
	 * 当前登录用户 ID(由服务端从 Sa-Token 登录上下文注入,前端无需传)。 Agent 据此执行下单/查订单/改地址等个人化操作,绝不向用户索要 ID。
	 */
	private Long userId;

	/**
	 * 会话 ID(前端生成并跨轮透传)。非空时走方案 3 多轮停留: 图内状态(user_id/确认流程/对话历史)由 LangGraph checkpointer
	 * 跨轮保留, 工具确认挂起后携带同一 sessionId + resume 再次调用即可恢复。
	 */
	private String sessionId;

	/**
	 * 用户兴趣画像上下文(Java 侧从 user_interest_stat/浏览记录构建,服务端注入,前端无需传)。 Python 侧作为 SystemMessage
	 * 注入,使导购推荐贴合用户兴趣。
	 */
	private String interestHint;

	public AiGuideChatReq() {
	}

	public List<ChatMsg> getMessages() {
		return messages;
	}

	public void setMessages(List<ChatMsg> messages) {
		this.messages = messages;
	}

	public Long getKnowledgeId() {
		return knowledgeId;
	}

	public void setKnowledgeId(Long knowledgeId) {
		this.knowledgeId = knowledgeId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getInterestHint() {
		return interestHint;
	}

	public void setInterestHint(String interestHint) {
		this.interestHint = interestHint;
	}

}
