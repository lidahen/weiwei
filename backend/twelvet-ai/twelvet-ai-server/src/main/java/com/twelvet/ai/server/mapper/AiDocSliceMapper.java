package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiDocSlice;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI文档切片Mapper
 */
@Mapper
public interface AiDocSliceMapper {

	/**
	 * 查询切片
	 */
	AiDocSlice selectAiDocSliceById(Long sliceId);

	/**
	 * 查询文档的切片列表
	 */
	List<AiDocSlice> selectAiDocSliceByDocId(Long docId);

	/**
	 * 查询知识库的所有切片
	 */
	List<AiDocSlice> selectAiDocSliceByKnowledgeId(Long knowledgeId);

	/**
	 * 批量插入切片
	 */
	int batchInsertAiDocSlice(List<AiDocSlice> list);

	/**
	 * 删除文档的所有切片
	 */
	int deleteAiDocSliceByDocId(Long docId);

	/**
	 * 删除知识库的所有切片
	 */
	int deleteAiDocSliceByKnowledgeId(Long knowledgeId);

}