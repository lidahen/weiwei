package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI文档实体
 */
@Schema(description = "AI文档")
public class AiDoc extends BaseEntity {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "文档ID")
	private Long docId;

	@Schema(description = "知识库ID")
	private Long knowledgeId;

	@Schema(description = "文档名称")
	private String docName;

	@Schema(description = "来源类型：INPUT-手动输入，UPLOAD-文件上传")
	private String sourceType;

	@Schema(description = "原始内容")
	private String content;

	@Schema(description = "文件URL")
	private String fileUrl;

	@Schema(description = "原始文件名")
	private String fileName;

	@Schema(description = "切片数量")
	private Integer sliceCount;

	@Schema(description = "字符数")
	private Integer charCount;

	@Schema(description = "状态：pending-待处理，processing-处理中，completed-已完成，failed-失败")
	private String status;

	@Schema(description = "删除标志")
	private Integer delFlag;

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

	public String getDocName() {
		return docName;
	}

	public void setDocName(String docName) {
		this.docName = docName;
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getFileUrl() {
		return fileUrl;
	}

	public void setFileUrl(String fileUrl) {
		this.fileUrl = fileUrl;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public Integer getSliceCount() {
		return sliceCount;
	}

	public void setSliceCount(Integer sliceCount) {
		this.sliceCount = sliceCount;
	}

	public Integer getCharCount() {
		return charCount;
	}

	public void setCharCount(Integer charCount) {
		this.charCount = charCount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getDelFlag() {
		return delFlag;
	}

	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}

}