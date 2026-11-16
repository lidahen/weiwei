package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiCreationProject;
import com.twelvet.ai.server.mapper.AiCreationProjectMapper;
import com.twelvet.ai.server.service.IAiCreationProjectService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI创作作品项目Service实现
 */
@Service
public class AiCreationProjectServiceImpl implements IAiCreationProjectService {

	private final AiCreationProjectMapper aiCreationProjectMapper;

	public AiCreationProjectServiceImpl(AiCreationProjectMapper aiCreationProjectMapper) {
		this.aiCreationProjectMapper = aiCreationProjectMapper;
	}

	@Override
	public List<AiCreationProject> selectAiCreationProjectList(AiCreationProject aiCreationProject) {
		return aiCreationProjectMapper.selectAiCreationProjectList(aiCreationProject);
	}

	@Override
	public int deleteAiCreationProjectByIds(Long[] projectIds) {
		return aiCreationProjectMapper.deleteAiCreationProjectByIds(projectIds);
	}

}
