package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI知识库Mapper
 */
@Mapper
public interface AiKnowledgeMapper {

	/**
	 * 查询知识库
	 */
	AiKnowledge selectAiKnowledgeById(Long knowledgeId);

	/**
	 * 查询知识库列表
	 */
	List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge);

	/**
	 * 查询所有知识库（下拉选择用）
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