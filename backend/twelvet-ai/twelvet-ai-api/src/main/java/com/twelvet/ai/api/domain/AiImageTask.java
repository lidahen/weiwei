package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI图片生成任务实体
 */
@Schema(description = "AI图片生成任务")
public class AiImageTask extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "任务ID")
	private Long taskId;

	@Schema(description = "模型ID")
	private Long modelId;

	@Schema(description = "提示词")
	private String prompt;

	@Schema(description = "反向提示词")
	private String negativePrompt;

	@Schema(description = "输出尺寸（如1024x1024）")
	private String size;

	@Schema(description = "生成类型：text2img, img2img")
	private String genType;

	@Schema(description = "输入图片URL")
	private String inputImage;

	@Schema(description = "输出格式：url, b64_json")
	private String responseFormat;

	@Schema(description = "图片宽度")
	private Integer width;

	@Schema(description = "图片高度")
	private Integer height;

	@Schema(description = "随机种子")
	private Long seed;

	@Schema(description = "风格")
	private String style;

	@Schema(description = "生成结果URL")
	private String resultUrl;

	@Schema(description = "状态：pending, queued, completed, failed, retrying")
	private String status;

	@Schema(description = "进度百分比（0-100）")
	private Integer progress;

	@Schema(description = "错误信息")
	private String errorMsg;

	@Schema(description = "删除标志")
	private Integer delFlag;

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public Long getModelId() {
		return modelId;
	}

	public void setModelId(Long modelId) {
		this.modelId = modelId;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getNegativePrompt() {
		return negativePrompt;
	}

	public void setNegativePrompt(String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getGenType() {
		return genType;
	}

	public void setGenType(String genType) {
		this.genType = genType;
	}

	public String getInputImage() {
		return inputImage;
	}

	public void setInputImage(String inputImage) {
		this.inputImage = inputImage;
	}

	public String getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Integer getWidth() {
		return width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public Integer getHeight() {
		return height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public Long getSeed() {
		return seed;
	}

	public void setSeed(Long seed) {
		this.seed = seed;
	}

	public String getStyle() {
		return style;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public String getResultUrl() {
		return resultUrl;
	}

	public void setResultUrl(String resultUrl) {
		this.resultUrl = resultUrl;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getProgress() {
		return progress;
	}

	public void setProgress(Integer progress) {
		this.progress = progress;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}
