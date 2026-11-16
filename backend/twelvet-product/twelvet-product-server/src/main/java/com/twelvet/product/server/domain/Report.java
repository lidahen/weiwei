package com.twelvet.product.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报对象 report
 * <p>
 * 不继承 BaseEntity：report 表没有 create_by/update_by/tenant_id 等审计列， 继承会让
 * TenantFieldInterceptor 尝试写入不存在的 tenant_id 列。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "举报")
public class Report implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "举报ID")
	private Long reportId;

	@Schema(description = "举报人ID")
	private Long reporterId;

	@Schema(description = "被举报对象类型(product/feed/user)")
	private String targetType;

	@Schema(description = "被举报对象ID")
	private Long targetId;

	@Schema(description = "举报原因(标签：详情 合成文本)")
	private String reason;

	@Schema(description = "证据图 JSON 数组(OSS 相对路径)")
	private String evidenceImages;

	@Schema(description = "状态(pending/done)")
	private String status;

	@Schema(description = "处理动作(dismiss/warn/remove_product/remove_feed/ban_user)")
	private String action;

	@Schema(description = "处理备注")
	private String handleNote;

	@Schema(description = "处理人ID")
	private Long handlerId;

	@Schema(description = "举报时间")
	private LocalDateTime createTime;

	@Schema(description = "处理时间")
	private LocalDateTime handleTime;

	public Long getReportId() {
		return reportId;
	}

	public void setReportId(Long reportId) {
		this.reportId = reportId;
	}

	public Long getReporterId() {
		return reporterId;
	}

	public void setReporterId(Long reporterId) {
		this.reporterId = reporterId;
	}

	public String getTargetType() {
		return targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public void setTargetId(Long targetId) {
		this.targetId = targetId;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getEvidenceImages() {
		return evidenceImages;
	}

	public void setEvidenceImages(String evidenceImages) {
		this.evidenceImages = evidenceImages;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getHandleNote() {
		return handleNote;
	}

	public void setHandleNote(String handleNote) {
		this.handleNote = handleNote;
	}

	public Long getHandlerId() {
		return handlerId;
	}

	public void setHandlerId(Long handlerId) {
		this.handlerId = handlerId;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getHandleTime() {
		return handleTime;
	}

	public void setHandleTime(LocalDateTime handleTime) {
		this.handleTime = handleTime;
	}

}
