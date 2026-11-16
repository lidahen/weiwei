package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiModel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI模型配置Mapper
 */
@Mapper
public interface AiModelMapper {

	/**
	 * 查询模型配置
	 */
	AiModel selectAiModelById(Long modelId);

	/**
	 * 查询模型配置列表
	 */
	List<AiModel> selectAiModelList(AiModel aiModel);

	/**
	 * 查询默认LLM模型
	 */
	AiModel selectDefaultLLMModel();

	/**
	 * 查询默认Embedding模型
	 */
	AiModel selectDefaultEmbeddingModel();

	/**
	 * 查询默认图片生成模型
	 */
	AiModel selectDefaultImageModel();

	/**
	 * 查询默认视频生成模型
	 */
	AiModel selectDefaultVideoModel();

	/**
	 * 新增模型配置
	 */
	int insertAiModel(AiModel aiModel);

	/**
	 * 修改模型配置
	 */
	int updateAiModel(AiModel aiModel);

	/**
	 * 删除模型配置
	 */
	int deleteAiModelById(Long modelId);

	/**
	 * 批量删除模型配置
	 */
	int deleteAiModelByIds(Long[] modelIds);

	/**
	 * 清除同类型默认标记
	 */
	int clearDefaultFlagByType(String modelType);

}