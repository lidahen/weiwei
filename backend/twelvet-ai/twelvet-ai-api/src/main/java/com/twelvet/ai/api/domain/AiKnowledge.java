package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI知识库实体
 */
@Schema(description = "AI知识库")
public class AiKnowledge extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "知识库ID")
	private Long knowledgeId;

	@Schema(description = "知识库名称")
	private String knowledgeName;

	@Schema(description = "知识库描述")
	private String knowledgeDesc;

	@Schema(description = "排序")
	private Integer knowledgeSort;

	@Schema(description = "删除标志")
	private Integer delFlag;

	public Long getKnowledgeId() {
		return knowledgeId;
	}

	public void setKnowledgeId(Long knowledgeId) {
		this.knowledgeId = knowledgeId;
	}

	public String getKnowledgeName() {
		return knowledgeName;
	}

	public void setKnowledgeName(String knowledgeName) {
		this.knowledgeName = knowledgeName;
	}

	public String getKnowledgeDesc() {
		return knowledgeDesc;
	}

	public void setKnowledgeDesc(String knowledgeDesc) {
		this.knowledgeDesc = knowledgeDesc;
	}

	public Integer getKnowledgeSort() {
		return knowledgeSort;
	}

	public void setKnowledgeSort(Integer knowledgeSort) {
		this.knowledgeSort = knowledgeSort;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}