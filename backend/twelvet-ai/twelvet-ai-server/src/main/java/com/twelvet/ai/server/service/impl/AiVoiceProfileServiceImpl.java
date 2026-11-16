package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiVoiceProfile;
import com.twelvet.ai.server.mapper.AiVoiceProfileMapper;
import com.twelvet.ai.server.service.IAiVoiceProfileService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiVoiceProfileServiceImpl implements IAiVoiceProfileService {

	private final AiVoiceProfileMapper aiVoiceProfileMapper;

	public AiVoiceProfileServiceImpl(AiVoiceProfileMapper aiVoiceProfileMapper) {
		this.aiVoiceProfileMapper = aiVoiceProfileMapper;
	}

	@Override
	public AiVoiceProfile selectAiVoiceProfileById(Long id) {
		return aiVoiceProfileMapper.selectAiVoiceProfileById(id);
	}

	@Override
	public List<AiVoiceProfile> selectAiVoiceProfileList(AiVoiceProfile profile) {
		return aiVoiceProfileMapper.selectAiVoiceProfileList(profile);
	}

	@Override
	public int insertAiVoiceProfile(AiVoiceProfile profile) {
		return aiVoiceProfileMapper.insertAiVoiceProfile(profile);
	}

	@Override
	public int updateAiVoiceProfile(AiVoiceProfile profile) {
		return aiVoiceProfileMapper.updateAiVoiceProfile(profile);
	}

	@Override
	public int updateAiVoiceProfileStatus(AiVoiceProfile profile) {
		return aiVoiceProfileMapper.updateAiVoiceProfileStatus(profile);
	}

	@Override
	public int deleteAiVoiceProfileById(Long id) {
		return aiVoiceProfileMapper.deleteAiVoiceProfileById(id);
	}

	@Override
	public int countTodayTrainings(Long userId) {
		return aiVoiceProfileMapper.countTodayTrainings(userId);
	}

}
