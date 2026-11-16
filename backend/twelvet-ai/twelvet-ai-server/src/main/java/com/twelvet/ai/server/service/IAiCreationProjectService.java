package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiCreationProject;

import java.util.List;

/**
 * AI创作作品项目Service接口
 */
public interface IAiCreationProjectService {

	/**
	 * 查询创作项目列表
	 */
	List<AiCreationProject> selectAiCreationProjectList(AiCreationProject aiCreationProject);

	/**
	 * 批量删除创作项目
	 */
	int deleteAiCreationProjectByIds(Long[] projectIds);

}
