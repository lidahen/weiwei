package com.twelvet.trade.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单对象 trade_order
 *
 * @author twelvet
 */
@Schema(description = "交易订单")
public class TradeOrder {

	private static final long serialVersionUID = 1L;

	@Schema(description = "订单ID")
	private Long orderId;

	@Schema(description = "订单编号")
	private String orderNo;

	@Schema(description = "买家ID")
	private Long buyerId;

	@Schema(description = "卖家ID")
	private Long sellerId;

	@Schema(description = "商品ID")
	private Long productId;

	@Schema(description = "商品快照")
	private String productSnapshot;

	@Schema(description = "商品单价")
	private BigDecimal skuPrice;

	@Schema(description = "运费")
	private BigDecimal freight;

	@Schema(description = "订单总金额")
	private BigDecimal totalAmount;

	@Schema(description = "配送方式")
	private String deliveryType;

	@Schema(description = "订单地址快照")
	private String addressSnapshot;

	@Schema(description = "买家留言")
	private String buyerRemark;

	@Schema(description = "订单状态(unpaid/unshipped/shipped/done/closed/refunding/refunded)")
	private String status;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "支付过期时间")
	private LocalDateTime expireAt;

	@Schema(description = "支付时间")
	private LocalDateTime paidAt;

	@Schema(description = "发货时间")
	private LocalDateTime shippedAt;

	@Schema(description = "完成时间")
	private LocalDateTime doneAt;

	@Schema(description = "自动确认时间")
	private LocalDateTime autoConfirmAt;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "租户ID")
	private Long tenantId;

	// 关联信息(联表查询)
	@Schema(description = "买家昵称")
	private String buyerNickName;

	@Schema(description = "买家头像")
	private String buyerAvatar;

	@Schema(description = "卖家昵称")
	private String sellerNickName;

	@Schema(description = "卖家头像")
	private String sellerAvatar;

	// 物流信息
	@Schema(description = "物流公司")
	private String logisticsCompany;

	@Schema(description = "物流单号")
	private String logisticsNo;

	@Schema(description = "最新物流节点")
	private String latestNode;

	@Schema(description = "物流轨迹")
	private String traceJson;

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
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

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getProductSnapshot() {
		return productSnapshot;
	}

	public void setProductSnapshot(String productSnapshot) {
		this.productSnapshot = productSnapshot;
	}

	public BigDecimal getSkuPrice() {
		return skuPrice;
	}

	public void setSkuPrice(BigDecimal skuPrice) {
		this.skuPrice = skuPrice;
	}

	public BigDecimal getFreight() {
		return freight;
	}

	public void setFreight(BigDecimal freight) {
		this.freight = freight;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public String getDeliveryType() {
		return deliveryType;
	}

	public void setDeliveryType(String deliveryType) {
		this.deliveryType = deliveryType;
	}

	public String getAddressSnapshot() {
		return addressSnapshot;
	}

	public void setAddressSnapshot(String addressSnapshot) {
		this.addressSnapshot = addressSnapshot;
	}

	public String getBuyerRemark() {
		return buyerRemark;
	}

	public void setBuyerRemark(String buyerRemark) {
		this.buyerRemark = buyerRemark;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(LocalDateTime expireAt) {
		this.expireAt = expireAt;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime paidAt) {
		this.paidAt = paidAt;
	}

	public LocalDateTime getShippedAt() {
		return shippedAt;
	}

	public void setShippedAt(LocalDateTime shippedAt) {
		this.shippedAt = shippedAt;
	}

	public LocalDateTime getDoneAt() {
		return doneAt;
	}

	public void setDoneAt(LocalDateTime doneAt) {
		this.doneAt = doneAt;
	}

	public LocalDateTime getAutoConfirmAt() {
		return autoConfirmAt;
	}

	public void setAutoConfirmAt(LocalDateTime autoConfirmAt) {
		this.autoConfirmAt = autoConfirmAt;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public String getBuyerNickName() {
		return buyerNickName;
	}

	public void setBuyerNickName(String buyerNickName) {
		this.buyerNickName = buyerNickName;
	}

	public String getBuyerAvatar() {
		return buyerAvatar;
	}

	public void setBuyerAvatar(String buyerAvatar) {
		this.buyerAvatar = buyerAvatar;
	}

	public String getSellerNickName() {
		return sellerNickName;
	}

	public void setSellerNickName(String sellerNickName) {
		this.sellerNickName = sellerNickName;
	}

	public String getSellerAvatar() {
		return sellerAvatar;
	}

	public void setSellerAvatar(String sellerAvatar) {
		this.sellerAvatar = sellerAvatar;
	}

	public String getLogisticsCompany() {
		return logisticsCompany;
	}

	public void setLogisticsCompany(String logisticsCompany) {
		this.logisticsCompany = logisticsCompany;
	}

	public String getLogisticsNo() {
		return logisticsNo;
	}

	public void setLogisticsNo(String logisticsNo) {
		this.logisticsNo = logisticsNo;
	}

	public String getLatestNode() {
		return latestNode;
	}

	public void setLatestNode(String latestNode) {
		this.latestNode = latestNode;
	}

	public String getTraceJson() {
		return traceJson;
	}

	public void setTraceJson(String traceJson) {
		this.traceJson = traceJson;
	}

}
