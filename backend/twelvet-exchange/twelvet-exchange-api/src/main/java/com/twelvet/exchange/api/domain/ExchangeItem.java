package com.twelvet.exchange.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 换物物品（twelve_exchange_item）
 * <p>
 * 状态机：auditing → onswap（初审 low 自动过 / 人工 pass）；auditing → rejected（初审 high 自动驳 / 人工
 * reject）；onswap → locked（意向被接受）；locked → onswap（订单取消/裁决取消）；locked → swapped（订单
 * completed）；onswap ↔ offshelf（主人下架/重新上架）
 * </p>
 *
 * @author twelvet
 */
public class ExchangeItem extends BaseEntity {

	/** 物品状态：草稿 */
	public static final String STATUS_DRAFT = "draft";

	/** 物品状态：AI 初审/人工审核中 */
	public static final String STATUS_AUDITING = "auditing";

	/** 物品状态：换物中（上架可收意向） */
	public static final String STATUS_ONSWAP = "onswap";

	/** 物品状态：已锁定（意向被接受，进入订单） */
	public static final String STATUS_LOCKED = "locked";

	/** 物品状态：已换出下架 */
	public static final String STATUS_SWAPPED = "swapped";

	/** 物品状态：已下架 */
	public static final String STATUS_OFFSHELF = "offshelf";

	/** 物品状态：审核驳回 */
	public static final String STATUS_REJECTED = "rejected";

	/** 交付方式：面交（社区场景默认） */
	public static final String DELIVERY_FACE = "face";

	/** 交付方式：快递 */
	public static final String DELIVERY_EXPRESS = "express";

	/** 交付方式：面交/快递均可 */
	public static final String DELIVERY_BOTH = "both";

	/** 成色：全新 */
	public static final String CONDITION_BRAND_NEW = "brand_new";

	/** 成色：99 新 */
	public static final String CONDITION_99 = "99";

	/** 成色：95 新 */
	public static final String CONDITION_95 = "95";

	/** 成色：90 新 */
	public static final String CONDITION_90 = "90";

	/** 成色：8 成新 */
	public static final String CONDITION_80 = "80";

	private Long id;

	private String itemNo;

	private Long ownerId;

	private String title;

	private String cover;

	private String images;

	private String catL1;

	private String catL2;

	private String conditionLevel;

	private BigDecimal estValue;

	private String wantCats;

	private String wantDesc;

	private Integer acceptAny;

	private String deliveryMode;

	private String city;

	private String location;

	private String description;

	private String status;

	private String rejectReason;

	private Integer viewCount;

	private Integer offerCount;

	private Integer swapCount;

	private BigDecimal hotScore;

	private LocalDateTime publishTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getItemNo() {
		return itemNo;
	}

	public void setItemNo(String itemNo) {
		this.itemNo = itemNo;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
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

	public String getConditionLevel() {
		return conditionLevel;
	}

	public void setConditionLevel(String conditionLevel) {
		this.conditionLevel = conditionLevel;
	}

	public BigDecimal getEstValue() {
		return estValue;
	}

	public void setEstValue(BigDecimal estValue) {
		this.estValue = estValue;
	}

	public String getWantCats() {
		return wantCats;
	}

	public void setWantCats(String wantCats) {
		this.wantCats = wantCats;
	}

	public String getWantDesc() {
		return wantDesc;
	}

	public void setWantDesc(String wantDesc) {
		this.wantDesc = wantDesc;
	}

	public Integer getAcceptAny() {
		return acceptAny;
	}

	public void setAcceptAny(Integer acceptAny) {
		this.acceptAny = acceptAny;
	}

	public String getDeliveryMode() {
		return deliveryMode;
	}

	public void setDeliveryMode(String deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public Integer getViewCount() {
		return viewCount;
	}

	public void setViewCount(Integer viewCount) {
		this.viewCount = viewCount;
	}

	public Integer getOfferCount() {
		return offerCount;
	}

	public void setOfferCount(Integer offerCount) {
		this.offerCount = offerCount;
	}

	public Integer getSwapCount() {
		return swapCount;
	}

	public void setSwapCount(Integer swapCount) {
		this.swapCount = swapCount;
	}

	public BigDecimal getHotScore() {
		return hotScore;
	}

	public void setHotScore(BigDecimal hotScore) {
		this.hotScore = hotScore;
	}

	public LocalDateTime getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(LocalDateTime publishTime) {
		this.publishTime = publishTime;
	}

	/** 物品主人昵称（后台联表冗余字段，非表列） */
	private String ownerName;

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ExchangeItem that = (ExchangeItem) o;
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : 0;
	}

}
