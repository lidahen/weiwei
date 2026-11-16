package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiCreationProject;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI创作作品项目Mapper
 */
@Mapper
public interface AiCreationProjectMapper {

	/**
	 * 查询创作项目
	 */
	AiCreationProject selectAiCreationProjectById(Long projectId);

	/**
	 * 查询创作项目列表
	 */
	List<AiCreationProject> selectAiCreationProjectList(AiCreationProject aiCreationProject);

	/**
	 * 删除创作项目
	 */
	int deleteAiCreationProjectByIds(Long[] projectIds);

}
