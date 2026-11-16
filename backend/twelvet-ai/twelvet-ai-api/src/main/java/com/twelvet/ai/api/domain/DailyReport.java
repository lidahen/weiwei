package com.twelvet.ai.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * AI 社区日报（表：twelve_daily_report）
 * <p>
 * 设计：docs/AI社区日报设计.md；幂等键 uk_report(tenant_id, report_date)， 重复生成走 UPDATE 覆盖；stats_json
 * 保留原始聚合指标供审计/重新生成。
 */
@Schema(description = "AI社区日报")
public class DailyReport implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private Long id;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "日报日期（统计数据所属日期）")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate reportDate;

	@Schema(description = "日报标题（LLM 生成）")
	private String title;

	@Schema(description = "日报正文（Markdown）")
	private String content;

	@Schema(description = "原始聚合指标 JSON（供审计/重新生成）")
	private String statsJson;

	@Schema(description = "llm=AI生成, fallback=模板兜底")
	private String source;

	@Schema(description = "创建时间")
	private java.time.LocalDateTime createTime;

	@Schema(description = "更新时间")
	private java.time.LocalDateTime updateTime;

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

	public LocalDate getReportDate() {
		return reportDate;
	}

	public void setReportDate(LocalDate reportDate) {
		this.reportDate = reportDate;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getStatsJson() {
		return statsJson;
	}

	public void setStatsJson(String statsJson) {
		this.statsJson = statsJson;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public java.time.LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(java.time.LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public java.time.LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(java.time.LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

}
