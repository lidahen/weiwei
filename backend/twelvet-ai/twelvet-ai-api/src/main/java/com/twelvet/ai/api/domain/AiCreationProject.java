package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI创作作品项目实体（创作工作台）
 */
@Schema(description = "AI创作作品项目")
public class AiCreationProject extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "项目ID")
	private Long projectId;

	@Schema(description = "作品标题")
	private String title;

	@Schema(description = "来源类型：viral_clone/original/image_text")
	private String sourceType;

	@Schema(description = "套用的爆款模板ID")
	private Long templateId;

	@Schema(description = "使用的人设ID")
	private Long personaId;

	@Schema(description = "选题/主题描述")
	private String topic;

	@Schema(description = "口播稿全文")
	private String scriptText;

	@Schema(description = "状态：draft/storyboard_ready/generating/composing/done/failed")
	private String status;

	@Schema(description = "成片地址")
	private String finalVideoUrl;

	@Schema(description = "封面地址")
	private String coverUrl;

	@Schema(description = "错误信息")
	private String errorMsg;

	@Schema(description = "删除标志")
	private Integer delFlag;

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}

	public Long getPersonaId() {
		return personaId;
	}

	public void setPersonaId(Long personaId) {
		this.personaId = personaId;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getScriptText() {
		return scriptText;
	}

	public void setScriptText(String scriptText) {
		this.scriptText = scriptText;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getFinalVideoUrl() {
		return finalVideoUrl;
	}

	public void setFinalVideoUrl(String finalVideoUrl) {
		this.finalVideoUrl = finalVideoUrl;
	}

	public String getCoverUrl() {
		return coverUrl;
	}

	public void setCoverUrl(String coverUrl) {
		this.coverUrl = coverUrl;
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
