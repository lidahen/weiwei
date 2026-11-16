package com.twelvet.payment.api.enums;

/**
 * 支付状态枚举
 * <p>
 * 对应 trade_pay.status 字段
 *
 * @author twelvet
 */
public enum PayStatus {

	/** 待支付 */
	UNPAID("unpaid"),

	/** 已支付 */
	PAID("paid"),

	/** 已关闭 */
	CLOSED("closed"),

	/** 已退款 */
	REFUNDED("refunded");

	private final String code;

	PayStatus(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}
