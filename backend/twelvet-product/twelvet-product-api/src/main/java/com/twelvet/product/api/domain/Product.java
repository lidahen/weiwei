package com.twelvet.product.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体
 */
public class Product extends BaseEntity {

	private Long productId;

	private Long sellerId;

	private String title;

	private String cover;

	private String images; // JSON

	private BigDecimal price;

	private BigDecimal originalPrice;

	private String condition;

	private String catL1;

	private String catL2;

	private String shippingMethod;

	private BigDecimal shippingFee;

	private Integer stock;

	private String status;

	private String description;

	private String location;

	private String city;

	private Integer viewCount;

	private Integer wantCount;

	private Integer chatCount;

	private LocalDateTime publishTime;

	private Integer isPinned;

	private String purchaseDate;

	private String auditRejectReason;

	// 列表/详情时附带
	private String sellerNickName;

	private String sellerAvatar;

	private Integer sellerSoldCount;

	private Boolean favorited;

	// 推荐流附带
	private String reason;

	private BigDecimal hotScore;

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCover() {
		return cover;
	}

	public void setCover(String cover) {
		this.cover = cover;
	}

	public String getImages() {
		return images;
	}

	public void setImages(String images) {
		this.images = images;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public BigDecimal getOriginalPrice() {
		return originalPrice;
	}

	public void setOriginalPrice(BigDecimal originalPrice) {
		this.originalPrice = originalPrice;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public String getCatL1() {
		return catL1;
	}

	public void setCatL1(String catL1) {
		this.catL1 = catL1;
	}

	public String getCatL2() {
		return catL2;
	}

	public void setCatL2(String catL2) {
		this.catL2 = catL2;
	}

	public String getShippingMethod() {
		return shippingMethod;
	}

	public void setShippingMethod(String shippingMethod) {
		this.shippingMethod = shippingMethod;
	}

	public BigDecimal getShippingFee() {
		return shippingFee;
	}

	public void setShippingFee(BigDecimal shippingFee) {
		this.shippingFee = shippingFee;
	}

	public Integer getStock() {
		return stock;
	}

	public void setStock(Integer stock) {
		this.stock = stock;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public Integer getViewCount() {
		return viewCount;
	}

	public void setViewCount(Integer viewCount) {
		this.viewCount = viewCount;
	}

	public Integer getWantCount() {
		return wantCount;
	}

	public void setWantCount(Integer wantCount) {
		this.wantCount = wantCount;
	}

	public Integer getChatCount() {
		return chatCount;
	}

	public void setChatCount(Integer chatCount) {
		this.chatCount = chatCount;
	}

	public LocalDateTime getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(LocalDateTime publishTime) {
		this.publishTime = publishTime;
	}

	public Integer getIsPinned() {
		return isPinned;
	}

	public void setIsPinned(Integer isPinned) {
		this.isPinned = isPinned;
	}

	public String getPurchaseDate() {
		return purchaseDate;
	}

	public void setPurchaseDate(String purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public String getAuditRejectReason() {
		return auditRejectReason;
	}

	public void setAuditRejectReason(String auditRejectReason) {
		this.auditRejectReason = auditRejectReason;
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

	public Integer getSellerSoldCount() {
		return sellerSoldCount;
	}

	public void setSellerSoldCount(Integer sellerSoldCount) {
		this.sellerSoldCount = sellerSoldCount;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public BigDecimal getHotScore() {
		return hotScore;
	}

	public void setHotScore(BigDecimal hotScore) {
		this.hotScore = hotScore;
	}

	public Boolean getFavorited() {
		return favorited;
	}

	public void setFavorited(Boolean favorited) {
		this.favorited = favorited;
	}

}