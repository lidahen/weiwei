package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiChatHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI聊天历史Mapper
 */
@Mapper
public interface AiChatHistoryMapper {

	/**
	 * 查询聊天历史
	 */
	AiChatHistory selectAiChatHistoryById(Long msgId);

	/**
	 * 查询聊天历史列表
	 */
	List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory);

	/**
	 * 查询知识库最近的聊天历史
	 */
	List<AiChatHistory> selectRecentHistoryByKnowledgeId(Long knowledgeId, Integer limit);

	/**
	 * 新增聊天历史
	 */
	int insertAiChatHistory(AiChatHistory aiChatHistory);

	/**
	 * 删除知识库的聊天历史
	 */
	int deleteAiChatHistoryByKnowledgeId(Long knowledgeId);

}