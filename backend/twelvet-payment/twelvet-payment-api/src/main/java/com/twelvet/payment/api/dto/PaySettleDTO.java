package com.twelvet.payment.api.dto;

import java.math.BigDecimal;

/**
 * 资金结算请求 DTO
 * <p>
 * 订单完成后释放资金到目标账户。 预留接口字段，当前支付服务暂未实现自动结算， 后续可接入分账/转账 API。
 *
 * @author twelvet
 */
public class PaySettleDTO {

	/** 商户支付流水号 */
	private String outTradeNo;

	/** 结算金额 */
	private BigDecimal amount;

	/** 目标账户标识（收款方） */
	private String targetAccount;

	/** 结算原因 */
	private String reason;

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public void setOutTradeNo(String outTradeNo) {
		this.outTradeNo = outTradeNo;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getTargetAccount() {
		return targetAccount;
	}

	public void setTargetAccount(String targetAccount) {
		this.targetAccount = targetAccount;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
