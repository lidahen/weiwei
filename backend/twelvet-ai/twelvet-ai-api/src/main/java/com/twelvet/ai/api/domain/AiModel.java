package com.twelvet.ai.api.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI模型配置实体
 */
@Schema(description = "AI模型配置")
public class AiModel extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "模型ID")
	private Long modelId;

	@Schema(description = "供应商：openai, deepseek, zhipu, ollama等")
	private String modelProvider;

	@Schema(description = "模型类型：LLM-大语言模型，EMBEDDING-嵌入模型，IMAGE-图片生成，VIDEO-视频生成")
	private String modelType;

	@Schema(description = "模型名称（标准名称）")
	@JsonAlias("model")
	private String modelName;

	@Schema(description = "模型别名（调用时使用）")
	@JsonAlias("alias")
	private String modelAlias;

	@Schema(description = "API密钥")
	private String apiKey;

	@Schema(description = "API基础地址")
	private String baseUrl;

	@Schema(description = "温度参数")
	private BigDecimal temperature;

	@Schema(description = "TopP参数")
	private BigDecimal topP;

	@Schema(description = "扩展参数JSON")
	private String extParam;

	@Schema(description = "是否默认模型")
	private Integer defaultFlag;

	@Schema(description = "状态：0-停用，1-启用")
	private Integer status;

	@Schema(description = "删除标志")
	private Integer delFlag;

	public Long getModelId() {
		return modelId;
	}

	public void setModelId(Long modelId) {
		this.modelId = modelId;
	}

	public String getModelProvider() {
		return modelProvider;
	}

	public void setModelProvider(String modelProvider) {
		this.modelProvider = modelProvider;
	}

	public String getModelType() {
		return modelType;
	}

	public void setModelType(String modelType) {
		this.modelType = modelType;
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public String getModelAlias() {
		return modelAlias;
	}

	public void setModelAlias(String modelAlias) {
		this.modelAlias = modelAlias;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public BigDecimal getTemperature() {
		return temperature;
	}

	public void setTemperature(BigDecimal temperature) {
		this.temperature = temperature;
	}

	public BigDecimal getTopP() {
		return topP;
	}

	public void setTopP(BigDecimal topP) {
		this.topP = topP;
	}

	public String getExtParam() {
		return extParam;
	}

	public void setExtParam(String extParam) {
		this.extParam = extParam;
	}

	public Integer getDefaultFlag() {
		return defaultFlag;
	}

	public void setDefaultFlag(Integer defaultFlag) {
		this.defaultFlag = defaultFlag;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}
