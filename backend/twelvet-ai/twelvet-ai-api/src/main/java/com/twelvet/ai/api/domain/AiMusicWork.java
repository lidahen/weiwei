package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI 音乐作品实体
 */
@Schema(description = "AI 音乐作品")
public class AiMusicWork extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键 ID")
	private Long id;

	@Schema(description = "用户 ID")
	private Long userId;

	/** 1=文生歌 2=平台音色翻唱 3=克隆说唱 4=纯音乐 */
	@Schema(description = "类型:1=文生歌 2=平台音色翻唱 3=克隆说唱 4=纯音乐")
	private Integer type;

	@Schema(description = "标题")
	private String title;

	@Schema(description = "风格标签,逗号分隔")
	private String styleTags;

	@Schema(description = "BPM")
	private Integer bpm;

	@Schema(description = "调式")
	private String keySignature;

	@Schema(description = "歌词(含结构标签)")
	private String lyrics;

	@Schema(description = "用户原始描述")
	private String prompt;

	@Schema(description = "关联音色 ID")
	private Long voiceProfileId;

	@Schema(description = "供应商:senseaudio / siliconflow")
	private String vendor;

	@Schema(description = "上游任务 ID")
	private String vendorTaskId;

	@Schema(description = "上游音色 ID")
	private String vendorVoiceId;

	@Schema(description = "最终 mp3 OSS 地址")
	private String audioUrl;

	@Schema(description = "音频时长(毫秒)")
	private Integer audioDurationMs;

	@Schema(description = "封面图")
	private String coverUrl;

	@Schema(description = "多格式音频地址 JSON")
	private String derivedAudio;

	/** 0=排队 1=生成中 2=已完成 3=失败 */
	@Schema(description = "状态:0=排队 1=生成中 2=已完成 3=失败")
	private Integer status;

	@Schema(description = "错误信息")
	private String errorMsg;

	@Schema(description = "消耗配额")
	private Integer quotaCost;

	@Schema(description = "派生自哪首")
	private Long parentWorkId;

	@Schema(description = "完成时间")
	private java.util.Date completedAt;

	@Schema(description = "删除标志(0=未删 1=已删)")
	private Integer delFlag;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getStyleTags() {
		return styleTags;
	}

	public void setStyleTags(String styleTags) {
		this.styleTags = styleTags;
	}

	public Integer getBpm() {
		return bpm;
	}

	public void setBpm(Integer bpm) {
		this.bpm = bpm;
	}

	public String getKeySignature() {
		return keySignature;
	}

	public void setKeySignature(String keySignature) {
		this.keySignature = keySignature;
	}

	public String getLyrics() {
		return lyrics;
	}

	public void setLyrics(String lyrics) {
		this.lyrics = lyrics;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public Long getVoiceProfileId() {
		return voiceProfileId;
	}

	public void setVoiceProfileId(Long voiceProfileId) {
		this.voiceProfileId = voiceProfileId;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getVendorTaskId() {
		return vendorTaskId;
	}

	public void setVendorTaskId(String vendorTaskId) {
		this.vendorTaskId = vendorTaskId;
	}

	public String getVendorVoiceId() {
		return vendorVoiceId;
	}

	public void setVendorVoiceId(String vendorVoiceId) {
		this.vendorVoiceId = vendorVoiceId;
	}

	public String getAudioUrl() {
		return audioUrl;
	}

	public void setAudioUrl(String audioUrl) {
		this.audioUrl = audioUrl;
	}

	public Integer getAudioDurationMs() {
		return audioDurationMs;
	}

	public void setAudioDurationMs(Integer audioDurationMs) {
		this.audioDurationMs = audioDurationMs;
	}

	public String getCoverUrl() {
		return coverUrl;
	}

	public void setCoverUrl(String coverUrl) {
		this.coverUrl = coverUrl;
	}

	public String getDerivedAudio() {
		return derivedAudio;
	}

	public void setDerivedAudio(String derivedAudio) {
		this.derivedAudio = derivedAudio;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public Integer getQuotaCost() {
		return quotaCost;
	}

	public void setQuotaCost(Integer quotaCost) {
		this.quotaCost = quotaCost;
	}

	public Long getParentWorkId() {
		return parentWorkId;
	}

	public void setParentWorkId(Long parentWorkId) {
		this.parentWorkId = parentWorkId;
	}

	public java.util.Date getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(java.util.Date completedAt) {
		this.completedAt = completedAt;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}
