package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiVoiceProfile;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 音色库 Mapper
 */
@Mapper
public interface AiVoiceProfileMapper {

	AiVoiceProfile selectAiVoiceProfileById(Long id);

	List<AiVoiceProfile> selectAiVoiceProfileList(AiVoiceProfile profile);

	int insertAiVoiceProfile(AiVoiceProfile profile);

	int updateAiVoiceProfile(AiVoiceProfile profile);

	int updateAiVoiceProfileStatus(AiVoiceProfile profile);

	int deleteAiVoiceProfileById(Long id);

	int countTodayTrainings(Long userId);

}
