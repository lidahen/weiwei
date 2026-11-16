package com.twelvet.payment.api.dto;

/**
 * 支付回调 DTO
 *
 * @author twelvet
 */
public class PayCallbackDTO {

	/** 商户支付流水号 */
	private String outTradeNo;

	/** 支付平台交易号 */
	private String tradeNo;

	/** 原始回调数据 */
	private String notifyData;

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public void setOutTradeNo(String outTradeNo) {
		this.outTradeNo = outTradeNo;
	}

	public String getTradeNo() {
		return tradeNo;
	}

	public void setTradeNo(String tradeNo) {
		this.tradeNo = tradeNo;
	}

	public String getNotifyData() {
		return notifyData;
	}

	public void setNotifyData(String notifyData) {
		this.notifyData = notifyData;
	}

}
