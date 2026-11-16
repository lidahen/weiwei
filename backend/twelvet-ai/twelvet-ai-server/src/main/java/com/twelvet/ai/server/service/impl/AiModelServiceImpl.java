package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.mapper.AiModelMapper;
import com.twelvet.ai.server.service.IAiModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI模型配置Service实现
 */
@Service
public class AiModelServiceImpl implements IAiModelService {

	private final AiModelMapper aiModelMapper;

	public AiModelServiceImpl(AiModelMapper aiModelMapper) {
		this.aiModelMapper = aiModelMapper;
	}

	@Override
	public AiModel selectAiModelById(Long modelId) {
		return aiModelMapper.selectAiModelById(modelId);
	}

	@Override
	public List<AiModel> selectAiModelList(AiModel aiModel) {
		return aiModelMapper.selectAiModelList(aiModel);
	}

	@Override
	public AiModel selectDefaultLLMModel() {
		return aiModelMapper.selectDefaultLLMModel();
	}

	@Override
	public AiModel selectDefaultEmbeddingModel() {
		return aiModelMapper.selectDefaultEmbeddingModel();
	}

	@Override
	public AiModel selectDefaultImageModel() {
		AiModel model = aiModelMapper.selectDefaultImageModel();
		// 如果没有默认模型，返回第一个可用的IMAGE模型
		if (model == null) {
			AiModel query = new AiModel();
			query.setModelType("IMAGE");
			query.setStatus(1);
			List<AiModel> models = aiModelMapper.selectAiModelList(query);
			if (!models.isEmpty()) {
				return models.get(0);
			}
		}
		return model;
	}

	@Override
	public AiModel selectDefaultVideoModel() {
		AiModel model = aiModelMapper.selectDefaultVideoModel();
		if (model == null) {
			AiModel query = new AiModel();
			query.setModelType("VIDEO");
			query.setStatus(1);
			List<AiModel> models = aiModelMapper.selectAiModelList(query);
			if (!models.isEmpty()) {
				return models.get(0);
			}
		}
		return model;
	}

	@Override
	public int insertAiModel(AiModel aiModel) {
		return aiModelMapper.insertAiModel(aiModel);
	}

	@Override
	public int updateAiModel(AiModel aiModel) {
		return aiModelMapper.updateAiModel(aiModel);
	}

	@Override
	public int deleteAiModelById(Long modelId) {
		return aiModelMapper.deleteAiModelById(modelId);
	}

	@Override
	public int deleteAiModelByIds(Long[] modelIds) {
		return aiModelMapper.deleteAiModelByIds(modelIds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int changeDefaultFlag(Long modelId, Integer defaultFlag) {
		AiModel model = aiModelMapper.selectAiModelById(modelId);
		if (model == null) {
			return 0;
		}

		if (defaultFlag == 1) {
			// 清除同类型的其他默认标记
			aiModelMapper.clearDefaultFlagByType(model.getModelType());
		}

		// 更新当前模型的默认标记
		AiModel updateModel = new AiModel();
		updateModel.setModelId(modelId);
		updateModel.setDefaultFlag(defaultFlag);
		return aiModelMapper.updateAiModel(updateModel);
	}

}