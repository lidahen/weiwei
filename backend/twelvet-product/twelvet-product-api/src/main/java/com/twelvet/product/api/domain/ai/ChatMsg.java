package com.twelvet.product.api.domain.ai;

/**
 * AI 导购对话历史中的一条消息(由前端保持并上送)
 */
public class ChatMsg {

	/**
	 * 角色:user / assistant
	 */
	private String role;

	private String content;

	public ChatMsg() {
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

}
