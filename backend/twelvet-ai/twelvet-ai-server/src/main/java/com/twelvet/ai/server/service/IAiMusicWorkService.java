package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiMusicWork;
import java.util.List;

public interface IAiMusicWorkService {

	AiMusicWork selectAiMusicWorkById(Long id);

	AiMusicWork selectAiMusicWorkByVendorTaskId(String vendorTaskId);

	List<AiMusicWork> selectAiMusicWorkList(AiMusicWork work);

	int insertAiMusicWork(AiMusicWork work);

	int updateAiMusicWork(AiMusicWork work);

	int updateAiMusicWorkStatus(AiMusicWork work);

	int deleteAiMusicWorkById(Long id);

}
