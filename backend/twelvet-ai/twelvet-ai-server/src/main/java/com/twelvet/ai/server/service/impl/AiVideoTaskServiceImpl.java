package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiVideoTask;
import com.twelvet.ai.server.mapper.AiVideoTaskMapper;
import com.twelvet.ai.server.service.IAiVideoTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI视频生成任务Service实现
 */
@Service
public class AiVideoTaskServiceImpl implements IAiVideoTaskService {

	private final AiVideoTaskMapper aiVideoTaskMapper;

	public AiVideoTaskServiceImpl(AiVideoTaskMapper aiVideoTaskMapper) {
		this.aiVideoTaskMapper = aiVideoTaskMapper;
	}

	@Override
	public AiVideoTask selectAiVideoTaskById(Long taskId) {
		return aiVideoTaskMapper.selectAiVideoTaskById(taskId);
	}

	@Override
	public List<AiVideoTask> selectAiVideoTaskList(AiVideoTask aiVideoTask) {
		return aiVideoTaskMapper.selectAiVideoTaskList(aiVideoTask);
	}

	@Override
	public int insertAiVideoTask(AiVideoTask aiVideoTask) {
		return aiVideoTaskMapper.insertAiVideoTask(aiVideoTask);
	}

	@Override
	public int updateAiVideoTask(AiVideoTask aiVideoTask) {
		return aiVideoTaskMapper.updateAiVideoTask(aiVideoTask);
	}

	@Override
	public int deleteAiVideoTaskById(Long taskId) {
		return aiVideoTaskMapper.deleteAiVideoTaskById(taskId);
	}

	@Override
	public int deleteAiVideoTaskByIds(Long[] taskIds) {
		return aiVideoTaskMapper.deleteAiVideoTaskByIds(taskIds);
	}

}
