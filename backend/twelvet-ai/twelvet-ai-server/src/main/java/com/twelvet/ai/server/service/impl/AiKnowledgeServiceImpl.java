package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiKnowledge;
import com.twelvet.ai.server.mapper.AiDocMapper;
import com.twelvet.ai.server.mapper.AiDocSliceMapper;
import com.twelvet.ai.server.mapper.AiKnowledgeMapper;
import com.twelvet.ai.server.service.IAiKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI知识库Service实现
 */
@Service
public class AiKnowledgeServiceImpl implements IAiKnowledgeService {

	private static final Logger log = LoggerFactory.getLogger(AiKnowledgeServiceImpl.class);

	private final AiKnowledgeMapper aiKnowledgeMapper;

	private final AiDocMapper aiDocMapper;

	private final AiDocSliceMapper aiDocSliceMapper;

	public AiKnowledgeServiceImpl(AiKnowledgeMapper aiKnowledgeMapper, AiDocMapper aiDocMapper,
			AiDocSliceMapper aiDocSliceMapper) {
		this.aiKnowledgeMapper = aiKnowledgeMapper;
		this.aiDocMapper = aiDocMapper;
		this.aiDocSliceMapper = aiDocSliceMapper;
	}

	@Override
	public AiKnowledge selectAiKnowledgeById(Long knowledgeId) {
		return aiKnowledgeMapper.selectAiKnowledgeById(knowledgeId);
	}

	@Override
	public List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge) {
		return aiKnowledgeMapper.selectAiKnowledgeList(aiKnowledge);
	}

	@Override
	public List<AiKnowledge> selectAiKnowledgeAll() {
		return aiKnowledgeMapper.selectAiKnowledgeAll();
	}

	@Override
	public int insertAiKnowledge(AiKnowledge aiKnowledge) {
		return aiKnowledgeMapper.insertAiKnowledge(aiKnowledge);
	}

	@Override
	public int updateAiKnowledge(AiKnowledge aiKnowledge) {
		return aiKnowledgeMapper.updateAiKnowledge(aiKnowledge);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int deleteAiKnowledgeById(Long knowledgeId) {
		// 删除切片
		aiDocSliceMapper.deleteAiDocSliceByKnowledgeId(knowledgeId);
		// 删除文档
		aiDocMapper.deleteAiDocByKnowledgeId(knowledgeId);
		// 删除知识库
		return aiKnowledgeMapper.deleteAiKnowledgeById(knowledgeId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int deleteAiKnowledgeByIds(Long[] knowledgeIds) {
		for (Long knowledgeId : knowledgeIds) {
			deleteAiKnowledgeById(knowledgeId);
		}
		return knowledgeIds.length;
	}

}
