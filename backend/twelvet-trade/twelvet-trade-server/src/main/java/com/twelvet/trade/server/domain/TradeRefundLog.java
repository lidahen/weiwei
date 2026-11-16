package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款单处理日志 trade_refund_log
 * <p>
 * 退款流程里所有备注、拒绝理由、裁决理由、人工说明都写在这里的 {@code content}， trade_refund 表本身不设备注列。
 * </p>
 * <p>
 * 不继承 BaseEntity：该表没有 create_by / update_by / tenant_id 等审计列， 继承会让
 * {@code TenantFieldInterceptor} 尝试写入不存在的 tenant_id 列。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "退款处理日志")
public class TradeRefundLog implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "日志ID")
	private Long logId;

	@Schema(description = "退款单ID")
	private Long refundId;

	@Schema(description = "操作人ID(系统动作可为空)")
	private Long operatorId;

	@Schema(description = "动作编码")
	private String action;

	@Schema(description = "说明内容")
	private String content;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	public Long getLogId() {
		return logId;
	}

	public void setLogId(Long logId) {
		this.logId = logId;
	}

	public Long getRefundId() {
		return refundId;
	}

	public void setRefundId(Long refundId) {
		this.refundId = refundId;
	}

	public Long getOperatorId() {
		return operatorId;
	}

	public void setOperatorId(Long operatorId) {
		this.operatorId = operatorId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
