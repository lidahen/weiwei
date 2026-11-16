package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI视频生成任务实体
 */
@Schema(description = "AI视频生成任务")
public class AiVideoTask extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "任务ID")
	private Long taskId;

	@Schema(description = "模型ID")
	private Long modelId;

	@Schema(description = "Agnes任务ID")
	private String agnesTaskId;

	@Schema(description = "Agnes视频ID")
	private String agnesVideoId;

	@Schema(description = "提示词")
	private String prompt;

	@Schema(description = "生成类型：text2vid, img2vid, multi2vid, keyframes")
	private String genType;

	@Schema(description = "输入图片JSON数组")
	private String inputImages;

	@Schema(description = "视频宽度")
	private Integer width;

	@Schema(description = "视频高度")
	private Integer height;

	@Schema(description = "帧数")
	private Integer numFrames;

	@Schema(description = "帧率")
	private Integer frameRate;

	@Schema(description = "负向提示词")
	private String negativePrompt;

	@Schema(description = "随机种子")
	private Long seed;

	@Schema(description = "状态：queued, in_progress, completed, failed")
	private String status;

	@Schema(description = "进度百分比")
	private Integer progress;

	@Schema(description = "生成结果URL")
	private String resultUrl;

	@Schema(description = "视频时长")
	private String videoSeconds;

	@Schema(description = "视频分辨率")
	private String videoSize;

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

	public String getAgnesTaskId() {
		return agnesTaskId;
	}

	public void setAgnesTaskId(String agnesTaskId) {
		this.agnesTaskId = agnesTaskId;
	}

	public String getAgnesVideoId() {
		return agnesVideoId;
	}

	public void setAgnesVideoId(String agnesVideoId) {
		this.agnesVideoId = agnesVideoId;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getGenType() {
		return genType;
	}

	public void setGenType(String genType) {
		this.genType = genType;
	}

	public String getInputImages() {
		return inputImages;
	}

	public void setInputImages(String inputImages) {
		this.inputImages = inputImages;
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

	public Integer getNumFrames() {
		return numFrames;
	}

	public void setNumFrames(Integer numFrames) {
		this.numFrames = numFrames;
	}

	public Integer getFrameRate() {
		return frameRate;
	}

	public void setFrameRate(Integer frameRate) {
		this.frameRate = frameRate;
	}

	public String getNegativePrompt() {
		return negativePrompt;
	}

	public void setNegativePrompt(String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	public Long getSeed() {
		return seed;
	}

	public void setSeed(Long seed) {
		this.seed = seed;
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

	public String getResultUrl() {
		return resultUrl;
	}

	public void setResultUrl(String resultUrl) {
		this.resultUrl = resultUrl;
	}

	public String getVideoSeconds() {
		return videoSeconds;
	}

	public void setVideoSeconds(String videoSeconds) {
		this.videoSeconds = videoSeconds;
	}

	public String getVideoSize() {
		return videoSize;
	}

	public void setVideoSize(String videoSize) {
		this.videoSize = videoSize;
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
