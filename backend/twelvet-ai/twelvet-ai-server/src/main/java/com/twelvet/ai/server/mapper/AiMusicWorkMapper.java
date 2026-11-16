package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiMusicWork;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 音乐作品 Mapper
 */
@Mapper
public interface AiMusicWorkMapper {

	AiMusicWork selectAiMusicWorkById(Long id);

	AiMusicWork selectAiMusicWorkByVendorTaskId(String vendorTaskId);

	List<AiMusicWork> selectAiMusicWorkList(AiMusicWork work);

	int insertAiMusicWork(AiMusicWork work);

	int updateAiMusicWork(AiMusicWork work);

	int updateAiMusicWorkStatus(AiMusicWork work);

	int deleteAiMusicWorkById(Long id);

}
