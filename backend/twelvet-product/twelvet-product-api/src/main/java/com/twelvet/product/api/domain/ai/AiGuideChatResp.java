package com.twelvet.product.api.domain.ai;

import java.util.List;

/**
 * AI 导购对话响应:推荐文案 + 结构化商品卡片列表
 */
public class AiGuideChatResp {

	private String reply;

	private List<ProductCardDTO> products;

	public AiGuideChatResp() {
	}

	public AiGuideChatResp(String reply, List<ProductCardDTO> products) {
		this.reply = reply;
		this.products = products;
	}

	public String getReply() {
		return reply;
	}

	public void setReply(String reply) {
		this.reply = reply;
	}

	public List<ProductCardDTO> getProducts() {
		return products;
	}

	public void setProducts(List<ProductCardDTO> products) {
		this.products = products;
	}

}
