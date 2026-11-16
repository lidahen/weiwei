package com.twelvet.ai.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 用量日账单（表：ai_usage_daily；Python 侧聊天完成时落账，Java 侧只读）
 */
@Schema(description = "AI用量日账单")
public class AiUsageDaily implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private Long id;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "统计日期")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate statDate;

	@Schema(description = "调用次数")
	private Integer calls;

	@Schema(description = "输入token累计")
	private Long promptTokens;

	@Schema(description = "输出token累计")
	private Long completionTokens;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public LocalDate getStatDate() {
		return statDate;
	}

	public void setStatDate(LocalDate statDate) {
		this.statDate = statDate;
	}

	public Integer getCalls() {
		return calls;
	}

	public void setCalls(Integer calls) {
		this.calls = calls;
	}

	public Long getPromptTokens() {
		return promptTokens;
	}

	public void setPromptTokens(Long promptTokens) {
		this.promptTokens = promptTokens;
	}

	public Long getCompletionTokens() {
		return completionTokens;
	}

	public void setCompletionTokens(Long completionTokens) {
		this.completionTokens = completionTokens;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

}
