package com.twelvet.ai.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI文档切片实体
 */
@Schema(description = "AI文档切片")
public class AiDocSlice implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "切片ID")
	private Long sliceId;

	@Schema(description = "文档ID")
	private Long docId;

	@Schema(description = "知识库ID")
	private Long knowledgeId;

	@Schema(description = "切片序号")
	private Integer sliceIndex;

	@Schema(description = "切片内容")
	private String content;

	@Schema(description = "字符数")
	private Integer charCount;

	@Schema(description = "向量化状态")
	private String vectorStatus;

	@Schema(description = "创建时间")
	private Date createTime;

	public Long getSliceId() {
		return sliceId;
	}

	public void setSliceId(Long sliceId) {
		this.sliceId = sliceId;
	}

	public Long getDocId() {
		return docId;
	}

	public void setDocId(Long docId) {
		this.docId = docId;
	}

	public Long getKnowledgeId() {
		return knowledgeId;
	}

	public void setKnowledgeId(Long knowledgeId) {
		this.knowledgeId = knowledgeId;
	}

	public Integer getSliceIndex() {
		return sliceIndex;
	}

	public void setSliceIndex(Integer sliceIndex) {
		this.sliceIndex = sliceIndex;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getCharCount() {
		return charCount;
	}

	public void setCharCount(Integer charCount) {
		this.charCount = charCount;
	}

	public String getVectorStatus() {
		return vectorStatus;
	}

	public void setVectorStatus(String vectorStatus) {
		this.vectorStatus = vectorStatus;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

}