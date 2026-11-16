package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiKnowledge;

import java.util.List;

/**
 * AI知识库Service接口
 */
public interface IAiKnowledgeService {

	/**
	 * 查询知识库
	 */
	AiKnowledge selectAiKnowledgeById(Long knowledgeId);

	/**
	 * 查询知识库列表
	 */
	List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge);

	/**
	 * 查询所有知识库
	 */
	List<AiKnowledge> selectAiKnowledgeAll();

	/**
	 * 新增知识库
	 */
	int insertAiKnowledge(AiKnowledge aiKnowledge);

	/**
	 * 修改知识库
	 */
	int updateAiKnowledge(AiKnowledge aiKnowledge);

	/**
	 * 删除知识库
	 */
	int deleteAiKnowledgeById(Long knowledgeId);

	/**
	 * 批量删除知识库
	 */
	int deleteAiKnowledgeByIds(Long[] knowledgeIds);

}