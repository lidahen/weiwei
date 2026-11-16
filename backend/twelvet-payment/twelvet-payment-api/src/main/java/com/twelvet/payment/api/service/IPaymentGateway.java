package com.twelvet.payment.api.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * 支付网关统一接口（跨模块共享）
 * <p>
 * 实现类放在 twelvet-payment-server，用 @Service("alipay") / @Service("wechat") 注册。 其他模块注入
 * Map&lt;String, IPaymentGateway&gt; 按 channel 自动分发。
 *
 * @author twelvet
 */
public interface IPaymentGateway {

	/** 返回支持的通道: "alipay" 或 "wechat" */
	String getChannel();

	/**
	 * 创建支付请求，返回前端需要的支付参数
	 * @param outTradeNo 商户支付流水号
	 * @param subject 商品描述
	 * @param amount 金额
	 * @param returnUrl 同步跳转地址（前端URL）
	 * @return 支付参数
	 */
	PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl);

	/**
	 * 查询支付状态
	 * @param outTradeNo 商户支付流水号
	 * @return "SUCCESS" / "WAITING" / "CLOSED" / "FAILED"
	 */
	String queryPayStatus(String outTradeNo);

	/**
	 * 退款
	 * @param outTradeNo 商户支付流水号
	 * @param refundAmount 退款金额
	 * @param reason 退款原因
	 * @return 退款是否成功
	 */
	boolean refund(String outTradeNo, BigDecimal refundAmount, String reason);

	/**
	 * 验证回调签名，返回 true 表示签名合法
	 * @param params 回调参数 Map
	 * @return 验签结果
	 */
	boolean verifyNotify(Map<String, String> params);

	/** 支付结果 */
	record PaymentResult(String payUrl, String qrCode, String appScheme, Map<String, Object> extra) {
		public PaymentResult {
			if (extra == null)
				extra = Collections.emptyMap();
		}
	}

}
