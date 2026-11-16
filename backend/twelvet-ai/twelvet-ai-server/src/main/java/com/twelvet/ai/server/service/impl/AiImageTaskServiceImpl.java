package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiImageTask;
import com.twelvet.ai.server.mapper.AiImageTaskMapper;
import com.twelvet.ai.server.service.IAiImageTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI图片生成任务Service实现
 */
@Service
public class AiImageTaskServiceImpl implements IAiImageTaskService {

	private final AiImageTaskMapper aiImageTaskMapper;

	public AiImageTaskServiceImpl(AiImageTaskMapper aiImageTaskMapper) {
		this.aiImageTaskMapper = aiImageTaskMapper;
	}

	@Override
	public AiImageTask selectAiImageTaskById(Long taskId) {
		return aiImageTaskMapper.selectAiImageTaskById(taskId);
	}

	@Override
	public List<AiImageTask> selectAiImageTaskList(AiImageTask aiImageTask) {
		return aiImageTaskMapper.selectAiImageTaskList(aiImageTask);
	}

	@Override
	public int insertAiImageTask(AiImageTask aiImageTask) {
		return aiImageTaskMapper.insertAiImageTask(aiImageTask);
	}

	@Override
	public int updateAiImageTask(AiImageTask aiImageTask) {
		return aiImageTaskMapper.updateAiImageTask(aiImageTask);
	}

	@Override
	public int deleteAiImageTaskById(Long taskId) {
		return aiImageTaskMapper.deleteAiImageTaskById(taskId);
	}

	@Override
	public int deleteAiImageTaskByIds(Long[] taskIds) {
		return aiImageTaskMapper.deleteAiImageTaskByIds(taskIds);
	}

}
