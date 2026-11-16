package com.twelvet.payment.server.service.impl;

import com.twelvet.payment.api.service.IPaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 微信支付网关实现（v3 API）
 * <p>
 * 使用 Native 扫码支付模式。
 * </p>
 *
 * @author twelvet
 */
@Service("wechat")
public class WechatGatewayImpl implements IPaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(WechatGatewayImpl.class);

	private static final String WX_V3_DOMAIN = "https://api.mch.weixin.qq.com";

	@Value("${payment.wechat.app-id}")
	private String appId;

	@Value("${payment.wechat.mch-id}")
	private String mchId;

	@Value("${payment.wechat.api-key}")
	private String apiKey;

	@Value("${payment.wechat.api-private-key:}")
	private String apiPrivateKeyPem;

	@Value("${payment.wechat.api-cert-serial-no:}")
	private String apiCertSerialNo;

	@Value("${payment.wechat.notify-url}")
	private String notifyUrl;

	private final RestTemplate restTemplate;

	private volatile PrivateKey cachedPrivateKey;

	/**
	 * 显式配置连接/读取超时：原先用裸 `new RestTemplate()`（无超时）， 微信侧网络分区或挂起时请求线程会无限阻塞，最终拖垮 Tomcat 线程池，
	 * 导致支付下单/查单整体不可用。
	 */
	public WechatGatewayImpl(RestTemplateBuilder restTemplateBuilder) {
		this.restTemplate = restTemplateBuilder.connectTimeout(Duration.ofSeconds(5))
			.readTimeout(Duration.ofSeconds(10))
			.build();
	}

	private PrivateKey parsePrivateKey(String pem) {
		if (pem == null || pem.isBlank()) {
			throw new IllegalStateException("Wechat api-private-key not configured. "
					+ "Set payment.wechat.api-private-key with the merchant private key PEM.");
		}
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s+", "");
		try {
			byte[] decoded = Base64.getDecoder().decode(base64);
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
			return KeyFactory.getInstance("RSA").generatePrivate(spec);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse Wechat private key PEM", e);
		}
	}

	@Override
	public String getChannel() {
		return "wechat";
	}

	@Override
	public PaymentResult createPayRequest(String outTradeNo, String subject, BigDecimal amount, String returnUrl) {
		Map<String, Object> body = new LinkedHashMap<>();
		// 微信 v3 下单字段名固定为 appid / mchid（单数）。
		// 原先误写为 appids / mchids，微信会直接返回 400 PARAM_ERROR，导致该渠道下单永远失败。
		body.put("appid", appId);
		body.put("mchid", mchId);
		body.put("description", subject);
		body.put("out_trade_no", outTradeNo);
		body.put("notify_url", notifyUrl);

		Map<String, Object> amountObj = new LinkedHashMap<>();
		amountObj.put("total", amount.multiply(new BigDecimal("100")).longValue());
		amountObj.put("currency", "CNY");
		body.put("amount", amountObj);

		Map<String, Object> sceneInfo = new LinkedHashMap<>();
		sceneInfo.put("payer_client_ip", "127.0.0.1");
		body.put("scene_info", sceneInfo);

		String bodyJson = com.alibaba.fastjson.JSONObject.toJSONString(body);
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String nonceStr = UUID.randomUUID().toString().replace("-", "");

		String path = "/v3/pay/transactions/native";
		String signStr = "POST\n" + path + "\n" + timestamp + "\n" + nonceStr + "\n" + bodyJson + "\n";

		String sign = rsaSha256Sign(signStr);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", buildAuthHeader(timestamp, nonceStr, sign));
		headers.set("Content-Type", "application/json");

		ResponseEntity<String> resp = restTemplate.exchange(WX_V3_DOMAIN + path, HttpMethod.POST,
				new HttpEntity<>(bodyJson, headers), String.class);

		if (resp.getStatusCode() == HttpStatus.CREATED) {
			Map<String, Object> result = com.alibaba.fastjson.JSONObject.parseObject(resp.getBody(), Map.class);
			String codeUrl = (String) result.get("code_url");
			Map<String, Object> extra = new HashMap<>();
			extra.put("outTradeNo", outTradeNo);
			extra.put("codeUrl", codeUrl);
			return new PaymentResult(codeUrl, codeUrl, null, extra);
		}
		else {
			log.error("Wechat precreate failed: {}", resp.getBody());
			throw new RuntimeException("Wechat precreate failed: " + resp.getBody());
		}
	}

	@Override
	public String queryPayStatus(String outTradeNo) {
		String path = "/v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + mchId;
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String nonceStr = UUID.randomUUID().toString().replace("-", "");
		String signStr = "GET\n" + path + "\n" + timestamp + "\n" + nonceStr + "\n\n";
		String sign = rsaSha256Sign(signStr);

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", buildAuthHeader(timestamp, nonceStr, sign));

		ResponseEntity<String> resp = restTemplate.exchange(WX_V3_DOMAIN + path, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		if (resp.getStatusCode() == HttpStatus.OK) {
			Map<String, Object> result = com.alibaba.fastjson.JSONObject.parseObject(resp.getBody(), Map.class);
			String tradeState = (String) result.get("trade_state");
			if ("SUCCESS".equals(tradeState))
				return "SUCCESS";
			if ("CLOSED".equals(tradeState))
				return "CLOSED";
			return "WAITING";
		}
		if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
			return "WAITING";
		}
		return "FAILED";
	}

	@Override
	public boolean refund(String outTradeNo, BigDecimal refundAmount, String reason) {
		log.warn("Wechat refund not implemented yet");
		return false;
	}

	@Override
	public boolean verifyNotify(Map<String, String> params) {
		log.info("Wechat notify (placeholder): {}", params);
		return true;
	}

	private String rsaSha256Sign(String data) {
		PrivateKey privateKey = cachedPrivateKey;
		if (privateKey == null) {
			synchronized (this) {
				if (cachedPrivateKey == null) {
					privateKey = parsePrivateKey(apiPrivateKeyPem);
					cachedPrivateKey = privateKey;
				}
				else {
					privateKey = cachedPrivateKey;
				}
			}
		}
		try {
			Signature sig = Signature.getInstance("SHA256withRSA");
			sig.initSign(privateKey);
			sig.update(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(sig.sign());
		}
		catch (Exception e) {
			throw new RuntimeException("RSA-SHA256 sign error: " + e.getMessage(), e);
		}
	}

	private String buildAuthHeader(String timestamp, String nonceStr, String signature) {
		if (apiCertSerialNo == null || apiCertSerialNo.isBlank()) {
			throw new IllegalStateException("Wechat api-cert-serial-no not configured. "
					+ "Set payment.wechat.api-cert-serial-no with the API certificate serial number.");
		}
		return String.format(
				"WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",timestamp=\"%s\",serial_no=\"%s\",signature=\"%s\"",
				mchId, nonceStr, timestamp, apiCertSerialNo, signature);
	}

}
