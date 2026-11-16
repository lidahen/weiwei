package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiDoc;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI文档Mapper
 */
@Mapper
public interface AiDocMapper {

	/**
	 * 查询文档
	 */
	AiDoc selectAiDocById(Long docId);

	/**
	 * 查询文档列表
	 */
	List<AiDoc> selectAiDocList(AiDoc aiDoc);

	/**
	 * 新增文档
	 */
	int insertAiDoc(AiDoc aiDoc);

	/**
	 * 修改文档
	 */
	int updateAiDoc(AiDoc aiDoc);

	/**
	 * 删除文档
	 */
	int deleteAiDocById(Long docId);

	/**
	 * 批量删除文档
	 */
	int deleteAiDocByIds(Long[] docIds);

	/**
	 * 根据知识库ID删除文档
	 */
	int deleteAiDocByKnowledgeId(Long knowledgeId);

}