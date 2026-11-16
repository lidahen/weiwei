package com.twelvet.payment.api.enums;

/**
 * 支付渠道枚举
 *
 * @author twelvet
 */
public enum PayChannel {

	/** 支付宝 */
	ALIPAY("alipay"),

	/** 微信支付 */
	WECHAT("wechat");

	private final String code;

	PayChannel(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static PayChannel of(String code) {
		for (PayChannel channel : values()) {
			if (channel.code.equals(code)) {
				return channel;
			}
		}
		throw new IllegalArgumentException("不支持的支付渠道: " + code);
	}

}
