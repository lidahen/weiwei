package com.twelvet.payment.server.controller;

import com.twelvet.payment.api.dto.PayCallbackDTO;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.payment.api.service.IPaymentGateway;
import com.twelvet.payment.api.service.ITenantRenewPayService;
import com.twelvet.payment.server.domain.TenantRenewOrder;
import com.twelvet.payment.server.mapper.TenantRenewOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付网关异步回调（白名单路径，无登录态）
 * <p>
 * 验签通过后按 out_trade_no 分发：
 * <ul>
 * <li>存在 sys_tenant_renew_order → 租户续费履约</li>
 * <li>否则 → 商品订单支付成功（发布 PaySuccessEvent）</li>
 * </ul>
 * 回调无租户上下文（设计如此）：按 out_trade_no 定位，不做租户过滤。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "支付回调", name = "PaymentNotifyController")
@RestController
public class PaymentNotifyController {

	private static final Logger log = LoggerFactory.getLogger(PaymentNotifyController.class);

	private final Map<String, IPaymentGateway> gatewayMap;

	private final TenantRenewOrderMapper renewOrderMapper;

	private final ITenantRenewPayService renewPayService;

	private final IPayOrderService payOrderService;

	public PaymentNotifyController(Map<String, IPaymentGateway> gatewayMap, TenantRenewOrderMapper renewOrderMapper,
			ITenantRenewPayService renewPayService, IPayOrderService payOrderService) {
		this.gatewayMap = gatewayMap;
		this.renewOrderMapper = renewOrderMapper;
		this.renewPayService = renewPayService;
		this.payOrderService = payOrderService;
	}

	@Operation(summary = "支付网关异步回调")
	@PostMapping({ "/payment/callback/{channel}" })
	public String notifyPost(@PathVariable("channel") String channel, @RequestParam Map<String, String> params) {
		return handle(channel, params);
	}

	@Operation(summary = "支付网关异步回调(GET 调试用)")
	@GetMapping({ "/payment/callback/{channel}" })
	public String notifyGet(@PathVariable("channel") String channel, @RequestParam Map<String, String> params) {
		return handle(channel, params);
	}

	private String handle(String channel, Map<String, String> params) {
		log.info("支付回调: channel={}, keys={}", channel, params.keySet());
		IPaymentGateway gateway = gatewayMap.get(channel);
		if (gateway == null || !gateway.verifyNotify(params)) {
			log.warn("支付回调验签失败: channel={}", channel);
			return "fail";
		}
		String outTradeNo = params.get("out_trade_no");
		if (outTradeNo == null || outTradeNo.isBlank()) {
			return "fail";
		}
		try {
			TenantRenewOrder renewOrder = renewOrderMapper.selectByOutTradeNo(outTradeNo);
			if (renewOrder != null) {
				renewPayService.fulfill(outTradeNo);
			}
			else {
				PayCallbackDTO callback = new PayCallbackDTO();
				callback.setOutTradeNo(outTradeNo);
				callback.setTradeNo(params.get("trade_no"));
				callback.setNotifyData(params.toString());
				payOrderService.handlePaySuccess(callback);
			}
		}
		catch (Exception e) {
			log.error("支付回调处理失败: outTradeNo={}", outTradeNo, e);
			return "fail";
		}
		return "success";
	}

}
