package com.twelvet.exchange.server.domain;

import java.time.LocalDateTime;

/**
 * 换物面交码（twelve_exchange_pickup_code）
 * <p>
 * 一单两码（side a/b），双方各持一个，线下互相出示核验；uk_order_side 防重复生成。
 * </p>
 *
 * @author twelvet
 */
public class ExchangePickupCode {

	private Long id;

	private Long orderId;

	private String side;

	private String code;

	private LocalDateTime expireAt;

	private Integer used;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public LocalDateTime getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(LocalDateTime expireAt) {
		this.expireAt = expireAt;
	}

	public Integer getUsed() {
		return used;
	}

	public void setUsed(Integer used) {
		this.used = used;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
