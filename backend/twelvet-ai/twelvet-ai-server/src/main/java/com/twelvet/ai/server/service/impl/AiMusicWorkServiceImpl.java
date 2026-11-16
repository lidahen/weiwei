package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiMusicWork;
import com.twelvet.ai.server.mapper.AiMusicWorkMapper;
import com.twelvet.ai.server.service.IAiMusicWorkService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiMusicWorkServiceImpl implements IAiMusicWorkService {

	private final AiMusicWorkMapper aiMusicWorkMapper;

	public AiMusicWorkServiceImpl(AiMusicWorkMapper aiMusicWorkMapper) {
		this.aiMusicWorkMapper = aiMusicWorkMapper;
	}

	@Override
	public AiMusicWork selectAiMusicWorkById(Long id) {
		return aiMusicWorkMapper.selectAiMusicWorkById(id);
	}

	@Override
	public AiMusicWork selectAiMusicWorkByVendorTaskId(String vendorTaskId) {
		return aiMusicWorkMapper.selectAiMusicWorkByVendorTaskId(vendorTaskId);
	}

	@Override
	public List<AiMusicWork> selectAiMusicWorkList(AiMusicWork work) {
		return aiMusicWorkMapper.selectAiMusicWorkList(work);
	}

	@Override
	public int insertAiMusicWork(AiMusicWork work) {
		return aiMusicWorkMapper.insertAiMusicWork(work);
	}

	@Override
	public int updateAiMusicWork(AiMusicWork work) {
		return aiMusicWorkMapper.updateAiMusicWork(work);
	}

	@Override
	public int updateAiMusicWorkStatus(AiMusicWork work) {
		return aiMusicWorkMapper.updateAiMusicWorkStatus(work);
	}

	@Override
	public int deleteAiMusicWorkById(Long id) {
		return aiMusicWorkMapper.deleteAiMusicWorkById(id);
	}

}
