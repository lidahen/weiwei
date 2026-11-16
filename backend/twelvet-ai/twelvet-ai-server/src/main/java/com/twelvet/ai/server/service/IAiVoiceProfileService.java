package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiVoiceProfile;
import java.util.List;

/**
 * AI 音色库 Service 接口
 */
public interface IAiVoiceProfileService {

	AiVoiceProfile selectAiVoiceProfileById(Long id);

	List<AiVoiceProfile> selectAiVoiceProfileList(AiVoiceProfile profile);

	int insertAiVoiceProfile(AiVoiceProfile profile);

	int updateAiVoiceProfile(AiVoiceProfile profile);

	int updateAiVoiceProfileStatus(AiVoiceProfile profile);

	int deleteAiVoiceProfileById(Long id);

	int countTodayTrainings(Long userId);

}
