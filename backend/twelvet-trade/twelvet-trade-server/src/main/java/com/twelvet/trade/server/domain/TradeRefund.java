package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单 trade_refund
 * <p>
 * 不继承 BaseEntity：trade_refund 表没有 create_by / update_by / tenant_id 等审计列， 继承会让
 * {@code TenantFieldInterceptor} 尝试写入不存在的 tenant_id 列（对齐 Report 的处理）。
 * </p>
 * <p>
 * {@link #activeFlag} 是数据库生成列（{@code status} 处于进行中时为 1，终态为 NULL）， <strong>只读，绝不能出现在任何
 * INSERT 列清单里</strong>——MySQL 8.0 对生成列写入报 3105。 进行中唯一性由唯一索引
 * {@code uk_order_active (order_id, active_flag)} 保证。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "退款单")
public class TradeRefund implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "退款单ID")
	private Long refundId;

	@Schema(description = "订单ID")
	private Long orderId;

	@Schema(description = "买家ID")
	private Long buyerId;

	@Schema(description = "卖家ID")
	private Long sellerId;

	@Schema(description = "退款类型(refund_only/return_refund)")
	private String type;

	@Schema(description = "退款原因编码")
	private String reasonCode;

	@Schema(description = "退款金额")
	private BigDecimal amount;

	@Schema(description = "问题描述")
	private String description;

	@Schema(description = "证据图(JSON 数组字符串)")
	private String evidenceImages;

	@Schema(description = "退款状态")
	private String status;

	@Schema(description = "退货物流公司")
	private String returnCompany;

	@Schema(description = "退货物流单号")
	private String returnNo;

	@Schema(description = "退货地址快照")
	private String returnAddressSnapshot;

	@Schema(description = "申请退款前的订单状态(unshipped/shipped/done/paid)")
	private String prevOrderStatus;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "进行中标记(DB 生成列, 只读)")
	private Integer activeFlag;

	public Long getRefundId() {
		return refundId;
	}

	public void setRefundId(Long refundId) {
		this.refundId = refundId;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public Long getBuyerId() {
		return buyerId;
	}

	public void setBuyerId(Long buyerId) {
		this.buyerId = buyerId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public void setReasonCode(String reasonCode) {
		this.reasonCode = reasonCode;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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

	public String getReturnCompany() {
		return returnCompany;
	}

	public void setReturnCompany(String returnCompany) {
		this.returnCompany = returnCompany;
	}

	public String getReturnNo() {
		return returnNo;
	}

	public void setReturnNo(String returnNo) {
		this.returnNo = returnNo;
	}

	public String getReturnAddressSnapshot() {
		return returnAddressSnapshot;
	}

	public void setReturnAddressSnapshot(String returnAddressSnapshot) {
		this.returnAddressSnapshot = returnAddressSnapshot;
	}

	public String getPrevOrderStatus() {
		return prevOrderStatus;
	}

	public void setPrevOrderStatus(String prevOrderStatus) {
		this.prevOrderStatus = prevOrderStatus;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

	public Integer getActiveFlag() {
		return activeFlag;
	}

	public void setActiveFlag(Integer activeFlag) {
		this.activeFlag = activeFlag;
	}

}
