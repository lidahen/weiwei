package com.twelvet.payment.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.payment.api.dto.PayCallbackDTO;
import com.twelvet.payment.api.dto.PayRequestDTO;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.payment.api.service.IPaymentGateway;
import com.twelvet.payment.server.mapper.PayOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付单服务实现
 * <p>
 * 重构关键：handlePaySuccess 不再直接 UPDATE trade_order， 而是发布 PaySuccessEvent，由 trade 模块自行监听处理。
 * </p>
 * <p>
 * dev 模拟模式（billing.pay.mock=true）：沙箱密钥为占位符时跳过网关， 直接产出 mock 二维码，配合
 * /payment/mock-pay/{payNo} 完成履约闭环。 生产必须 false。
 * </p>
 *
 * @author twelvet
 */
@Service
public class PayOrderServiceImpl implements IPayOrderService {

	/** 模拟支付二维码前缀（仅 mock 模式产出） */
	private static final String MOCK_QR_PREFIX = "https://mock.pay/weiwei/";

	private static final Logger log = LoggerFactory.getLogger(PayOrderServiceImpl.class);

	private final PayOrderMapper payOrderMapper;

	private final Map<String, IPaymentGateway> gatewayMap;

	private final TenantSupport tenantSupport;

	private final ApplicationEventPublisher eventPublisher;

	@Value("${billing.pay.mock:false}")
	private boolean mockEnabled;

	public PayOrderServiceImpl(PayOrderMapper payOrderMapper, Map<String, IPaymentGateway> gatewayMap,
			TenantSupport tenantSupport, ApplicationEventPublisher eventPublisher) {
		this.payOrderMapper = payOrderMapper;
		this.gatewayMap = gatewayMap;
		this.tenantSupport = tenantSupport;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public String generatePayNo() {
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		int rand = (int) (Math.random() * 10000);
		return "PAY" + time + String.format("%04d", rand);
	}

	@Override
	public Map<String, Object> createPayOrder(PayRequestDTO request) {
		String outTradeNo = generatePayNo();

		// 1. 落支付单（trade_pay 表；tid=null 时 SQL 不写 tenant_id 列 → DB DEFAULT 1 兜底）。
		// payUserId 记录发起人：mock-pay / 查询归属校验的权威来源。
		// bizType 消除 order_id 多态（trade/runner；renew 走 insertRenewPayOrder），空值防御性默认 trade
		String bizType = (request.getBizType() == null || request.getBizType().isBlank()) ? "trade" : request.getBizType();
		payOrderMapper.insertPayOrder(request.getOrderId(), request.getPayUserId(), outTradeNo, request.getChannel(),
				request.getAmount(), bizType, tenantSupport.writeTid());

		// 2. 调网关（mock 模式跳过：dev 沙箱密钥为占位符）
		String qrCodeUrl;
		String payUrl = null;
		String channel = request.getChannel();
		Object qrCode = null;
		Map<String, Object> extra = new HashMap<>();
		if (mockEnabled) {
			qrCodeUrl = MOCK_QR_PREFIX + outTradeNo;
			payUrl = qrCodeUrl;
			qrCode = qrCodeUrl;
			extra.put("mock", true);
			log.info("模拟支付模式：跳过网关创建支付单 outTradeNo={}, amount={}", outTradeNo, request.getAmount());
		}
		else {
			IPaymentGateway gateway = gatewayMap.get(channel);
			if (gateway == null) {
				throw new IllegalArgumentException("Unsupported payment channel: " + channel);
			}
			IPaymentGateway.PaymentResult result = gateway.createPayRequest(outTradeNo, request.getSubject(),
					request.getAmount(), request.getReturnUrl());
			payUrl = result.payUrl();
			qrCode = result.qrCode();
			extra = result.extra() == null ? new HashMap<>() : result.extra();
			qrCodeUrl = payUrl != null ? payUrl : String.valueOf(qrCode == null ? "" : qrCode);
		}

		// 3. 回写二维码 URL / 商户流水号
		payOrderMapper.updateQrCode(request.getOrderId(), outTradeNo, qrCodeUrl, channel);

		// 4. 装配返回
		Map<String, Object> resp = new HashMap<>();
		resp.put("payNo", outTradeNo);
		resp.put("payUrl", payUrl);
		resp.put("qrCode", qrCode);
		resp.put("channel", channel);
		resp.put("amount", request.getAmount());
		resp.put("orderId", request.getOrderId());
		resp.put("orderNo", request.getOrderNo());
		resp.put("extra", extra);
		resp.put("mock", mockEnabled);
		return resp;
	}

	@Override
	public Map<String, Object> queryPayStatus(String payNo) {
		// payNo = out_trade_no；有租户上下文时限定本租户支付单
		Long tid = TenantContext.get();
		Map<String, Object> payOrder = payOrderMapper.selectByOutTradeNo(payNo, tid);
		if (payOrder == null || payOrder.isEmpty()) {
			return null;
		}

		// 已 paid：直接返回缓存
		if ("paid".equals(payOrder.get("status"))) {
			payOrder.put("paid", true);
			return payOrder;
		}

		// 否则查网关（mock 模式不查网关：状态由 /payment/mock-pay 驱动）
		String channel = (String) payOrder.get("pay_channel");
		IPaymentGateway gateway = gatewayMap.get(channel);
		if (mockEnabled) {
			payOrder.put("paid", false);
			return payOrder;
		}
		if (gateway != null) {
			String tradeStatus = gateway.queryPayStatus(payNo);
			if ("SUCCESS".equals(tradeStatus)) {
				LocalDateTime now = LocalDateTime.now();
				long rows = payOrderMapper.markPaidByOutTradeNo(payNo, now);
				if (rows > 0) {
					// 轮询抢到首处理：必须与回调路径同构发布事件，
					// 否则订单状态永远不迁移（随后被超时关单转售）
					publishPaySuccess(payNo, now, null, "polling-confirm");
				}
				payOrder.put("status", "paid");
				payOrder.put("paid", true);
			}
			else {
				payOrder.put("paid", false);
			}
		}

		return payOrder;
	}

	/**
	 * 查询支付单详情并发布 PaySuccessEvent（回调/轮询/mock 三条路径共用的履约入口）。
	 * <p>
	 * 事件消费方（trade/runner 监听器）按状态前置条件做幂等迁移，重发无副作用； 而漏发会把订单永久搁浅在
	 * unpaid——随后被超时关单回补库存转售，钱货两空。宁可重发，不可漏发。
	 * </p>
	 * @return true = 事件已发布；false = 支付单不存在（不应发生，调用方各自兜底）
	 */
	private boolean publishPaySuccess(String payNo, LocalDateTime paidAt, String tradeNo, String source) {
		Map<String, Object> row = payOrderMapper.selectOrderInfoByOutTradeNo(payNo);
		if (row == null || row.isEmpty()) {
			log.warn("支付单不存在，无法发布支付成功事件: payNo={}, source={}", payNo, source);
			return false;
		}
		Long orderId = row.get("order_id") != null ? ((Number) row.get("order_id")).longValue() : null;
		String channel = (String) row.get("pay_channel");
		// biz_type 兜底：防御 DDL 未执行环境（order_id=null 即续费，否则按 trade）
		Object bizTypeObj = row.get("biz_type");
		String bizType = bizTypeObj != null ? String.valueOf(bizTypeObj) : (orderId == null ? "renew" : "trade");
		BigDecimal amount = (BigDecimal) row.get("pay_amount");
		Long amountCents = amount != null ? amount.multiply(new BigDecimal("100")).longValue() : null;

		PaySuccessEvent event = new PaySuccessEvent(payNo, orderId, bizType, channel, amountCents, paidAt, tradeNo,
				source);
		eventPublisher.publishEvent(event);
		log.info("支付成功事件已发布: outTradeNo={}, orderId={}, bizType={}, source={}", payNo, orderId, bizType, source);
		return true;
	}

	@Override
	public void handlePaySuccess(PayCallbackDTO callback) {
		String payNo = callback.getOutTradeNo();

		// 幂等：markPaidByOutTradeNo 内部 SQL 已带 status != 'paid' 条件。
		// rows=0 有两种可能：单不存在，或首处理时事件发布/消费失败（订单卡 unpaid）。
		// 第二种必须重发事件自愈（监听器按状态前置条件幂等，重发无副作用）——
		// 历史缺陷在此直接 return，回调渠道重试也因 rows=0 永不再发事件，订单永久搁浅。
		int rows = payOrderMapper.markPaidByOutTradeNo(payNo, LocalDateTime.now());
		if (rows == 0) {
			log.info("支付回调重复或单已处理，重发事件自愈: payNo={}", payNo);
		}
		publishPaySuccess(payNo, LocalDateTime.now(), callback.getTradeNo(), callback.getNotifyData());
	}

	@Override
	public boolean refund(String outTradeNo, BigDecimal amount, String reason) {
		// 1. 取支付单渠道与状态：仅 paid 可退——refund 是迟到支付/超时关单补偿的兜底入口，
		// 非 paid（unpaid/refunded）直接拒绝，同时保证 markRefunded 幂等（重复调用无害）
		Map<String, Object> row = payOrderMapper.selectOrderInfoByOutTradeNo(outTradeNo);
		if (row == null || row.isEmpty()) {
			log.warn("退款失败：支付单不存在: {}", outTradeNo);
			return false;
		}
		if (!"paid".equals(row.get("status"))) {
			log.debug("退款拒绝：支付单非 paid 状态: outTradeNo={}, status={}", outTradeNo, row.get("status"));
			return false;
		}
		String channel = (String) row.get("pay_channel");
		if (mockEnabled) {
			// mock 模式：无真实资金流，直接置为已退款
			payOrderMapper.markRefunded(outTradeNo);
			log.info("模拟支付模式：支付单直接标记已退款 outTradeNo={}", outTradeNo);
			return true;
		}
		IPaymentGateway gateway = gatewayMap.get(channel);
		if (gateway == null) {
			log.warn("退款失败：不支持的渠道: {}", channel);
			return false;
		}

		// 2. 调网关退款
		boolean success = gateway.refund(outTradeNo, amount, reason);
		if (success) {
			payOrderMapper.markRefunded(outTradeNo);
		}
		return success;
	}

	@Override
	public boolean mockPay(String payNo, Long userId) {
		if (!mockEnabled) {
			throw new TWTException("模拟支付未开启（billing.pay.mock=false）");
		}
		Map<String, Object> row = payOrderMapper.selectOrderInfoByOutTradeNo(payNo);
		if (row == null || row.isEmpty()) {
			throw new TWTException("支付单不存在: " + payNo);
		}
		// 归属校验：仅支付单发起人可模拟支付（payNo 可枚举，无校验则任意登录用户
		// 可强付他人订单推其进入已付状态）。存量单 pay_user_id 为 NULL（列后加），放行兼容 dev 旧数据。
		Object ownerObj = row.get("pay_user_id");
		Long owner = ownerObj instanceof Number n ? n.longValue() : null;
		if (owner != null && !owner.equals(userId)) {
			log.warn("mock 支付拒绝（归属不符）: payNo={}, owner={}, caller={}", payNo, owner, userId);
			throw new TWTException("支付单不存在: " + payNo);
		}
		PayCallbackDTO callback = new PayCallbackDTO();
		callback.setOutTradeNo(payNo);
		callback.setTradeNo("MOCK" + payNo);
		callback.setNotifyData("mock-pay");
		// 幂等：markPaidByOutTradeNo 带 status != 'paid' 条件；事件按状态前置条件幂等，重发无副作用
		handlePaySuccess(callback);
		log.info("模拟支付完成: payNo={}", payNo);
		return true;
	}

	@Override
	public boolean isMockEnabled() {
		return mockEnabled;
	}

}
