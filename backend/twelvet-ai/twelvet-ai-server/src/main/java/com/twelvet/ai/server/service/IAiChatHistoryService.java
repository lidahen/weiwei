package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiChatHistory;

import java.util.List;

/**
 * AI聊天历史Service接口
 */
public interface IAiChatHistoryService {

	/**
	 * 查询聊天历史列表
	 */
	List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory);

	/**
	 * 查询知识库最近的聊天历史
	 */
	List<AiChatHistory> selectRecentHistory(Long knowledgeId, Integer limit);

	/**
	 * 新增聊天历史
	 */
	int insertAiChatHistory(AiChatHistory aiChatHistory);

	/**
	 * 清空知识库的聊天历史
	 */
	int deleteAiChatHistoryByKnowledgeId(Long knowledgeId);

}