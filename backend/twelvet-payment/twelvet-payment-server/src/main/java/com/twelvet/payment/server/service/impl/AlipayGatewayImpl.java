package com.twelvet.payment.server.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.twelvet.payment.api.service.IPaymentGateway;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付网关实现
 * <p>
 * 支持扫码支付（Precreate）和 H5 网页支付（PagePay）
 * </p>
 *
 * @author twelvet
 */
@Service("alipay")
public class AlipayGatewayImpl implements IPaymentGateway {

	@Value("${payment.alipay.app-id}")
	private String appId;

	@Value("${payment.alipay.private-key}")
	private String privateKey;

	@Value("${payment.alipay.alipay-public-key}")
	private String alipayPublicKey;

	@Value("${payment.alipay.gateway-url}")
	private String gatewayUrl;

	@Value("${payment.alipay.charset:UTF-8}")
	private String charset;

	@Value("${payment.alipay.sign-type:RSA2}")
	private String signType;

	@Value("${payment.alipay.notify-url}")
	private String notifyUrl;

	private AlipayClient alipayClient;

	@PostConstruct
	private void initClient() {
		this.alipayClient = new DefaultAlipayClient(gatewayUrl, appId, privateKey, "json", charset, alipayPublicKey,
				signType);
	}

	@Override
	public String getChannel() {
		return "alipay";
	}

	@Override
	public PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl) {
		AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
		request.setNotifyUrl(notifyUrl);
		request.setReturnUrl(returnUrl);

		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", outTradeNo);
		bizContent.put("total_amount", amount.toString());
		bizContent.put("subject", subject);
		bizContent.put("timeout_express", "30m");

		request.setBizContent(bizContent.toJSONString());

		try {
			AlipayTradePrecreateResponse response = alipayClient.execute(request);
			if (response.isSuccess()) {
				Map<String, Object> extra = new HashMap<>();
				extra.put("qrCode", response.getQrCode());
				extra.put("outTradeNo", outTradeNo);
				return new PaymentResult(null, response.getQrCode(), null, extra);
			}
			else {
				throw new RuntimeException("Alipay precreate failed: " + response.getSubMsg());
			}
		}
		catch (AlipayApiException e) {
			throw new RuntimeException("Alipay precreate API error: " + e.getMessage(), e);
		}
	}

	@Override
	public String queryPayStatus(String outTradeNo) {
		AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", outTradeNo);
		request.setBizContent(bizContent.toJSONString());

		try {
			AlipayTradeQueryResponse response = alipayClient.execute(request);
			if (response.isSuccess()) {
				return response.getTradeStatus();
			}
			return "FAILED";
		}
		catch (AlipayApiException e) {
			return "FAILED";
		}
	}

	@Override
	public boolean refund(String outTradeNo, BigDecimal refundAmount, String reason) {
		AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", outTradeNo);
		bizContent.put("refund_amount", refundAmount.toString());
		bizContent.put("refund_reason", reason);
		request.setBizContent(bizContent.toJSONString());

		try {
			AlipayTradeRefundResponse response = alipayClient.execute(request);
			return response.isSuccess();
		}
		catch (AlipayApiException e) {
			return false;
		}
	}

	@Override
	public boolean verifyNotify(Map<String, String> params) {
		try {
			return AlipaySignature.rsaCheckV1(params, alipayPublicKey, charset, signType);
		}
		catch (AlipayApiException e) {
			return false;
		}
	}

}
