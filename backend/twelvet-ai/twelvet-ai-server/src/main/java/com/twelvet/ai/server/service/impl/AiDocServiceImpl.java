package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;
import com.twelvet.ai.server.mapper.AiDocMapper;
import com.twelvet.ai.server.mapper.AiDocSliceMapper;
import com.twelvet.ai.server.service.IAiDocService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI文档Service实现
 */
@Service
public class AiDocServiceImpl implements IAiDocService {

	private static final Logger log = LoggerFactory.getLogger(AiDocServiceImpl.class);

	private final AiDocMapper aiDocMapper;

	private final AiDocSliceMapper aiDocSliceMapper;

	public AiDocServiceImpl(AiDocMapper aiDocMapper, AiDocSliceMapper aiDocSliceMapper) {
		this.aiDocMapper = aiDocMapper;
		this.aiDocSliceMapper = aiDocSliceMapper;
	}

	@Override
	public AiDoc selectAiDocById(Long docId) {
		return aiDocMapper.selectAiDocById(docId);
	}

	@Override
	public List<AiDoc> selectAiDocList(AiDoc aiDoc) {
		return aiDocMapper.selectAiDocList(aiDoc);
	}

	@Override
	public int insertAiDoc(AiDoc aiDoc) {
		aiDoc.setStatus("pending");
		int rows = aiDocMapper.insertAiDoc(aiDoc);
		// 异步处理文档
		if (rows > 0 && aiDoc.getDocId() != null) {
			processDocAsync(aiDoc.getDocId());
		}
		return rows;
	}

	@Override
	public int updateAiDoc(AiDoc aiDoc) {
		return aiDocMapper.updateAiDoc(aiDoc);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int deleteAiDocById(Long docId) {
		// 删除切片
		aiDocSliceMapper.deleteAiDocSliceByDocId(docId);
		return aiDocMapper.deleteAiDocById(docId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int deleteAiDocByIds(Long[] docIds) {
		for (Long docId : docIds) {
			deleteAiDocById(docId);
		}
		return docIds.length;
	}

	@Override
	public List<AiDocSlice> selectDocSlices(Long docId) {
		return aiDocSliceMapper.selectAiDocSliceByDocId(docId);
	}

	@Override
	public void processDoc(Long docId) {
		processDocAsync(docId);
	}

	@Async
	public void processDocAsync(Long docId) {
		try {
			// TODO: 文档处理功能暂时禁用，等待Spring AI版本升级后恢复
			log.warn("文档处理功能暂时禁用: docId={}", docId);
		}
		catch (Exception e) {
			log.error("文档处理失败: docId={}", docId, e);
			// 更新状态为失败
			AiDoc doc = new AiDoc();
			doc.setDocId(docId);
			doc.setStatus("failed");
			aiDocMapper.updateAiDoc(doc);
		}
	}

}
