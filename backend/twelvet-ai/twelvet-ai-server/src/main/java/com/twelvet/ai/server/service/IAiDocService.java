package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;

import java.util.List;

/**
 * AI文档Service接口
 */
public interface IAiDocService {

	/**
	 * 查询文档
	 */
	AiDoc selectAiDocById(Long docId);

	/**
	 * 查询文档列表
	 */
	List<AiDoc> selectAiDocList(AiDoc aiDoc);

	/**
	 * 新增文档（手动输入）
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
	 * 查询文档切片列表
	 */
	List<AiDocSlice> selectDocSlices(Long docId);

	/**
	 * 处理文档（切片+向量化）
	 */
	void processDoc(Long docId);

}