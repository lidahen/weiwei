package com.twelvet.trade.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.payment.api.dto.PayRequestDTO;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.trade.server.service.ITradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付控制器
 * <p>
 * 重构后注入 IPayOrderService（来自 twelvet-payment-api）， 不再直接依赖 trade 内的 PayOrderService。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "支付管理", name = "TradePayController")
@RestController
@RequestMapping("/trade/pay")
public class TradePayController extends TWTController {

	private final IPayOrderService payOrderService;

	private final ITradeOrderService tradeOrderService;

	@Value("${twelvet.name:邻里二手}")
	private String appName;

	@Value("${payment.return-url:/trade/payment/result}")
	private String returnUrl;

	public TradePayController(IPayOrderService payOrderService, ITradeOrderService tradeOrderService) {
		this.payOrderService = payOrderService;
		this.tradeOrderService = tradeOrderService;
	}

	@Operation(summary = "创建支付单")
	@PostMapping("/create")
	public JsonResult<Map<String, Object>> createPayOrder(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
		String channel = String.valueOf(body.getOrDefault("channel", "alipay"));

		// 收银台入口守卫：归属本人 + 仅 unpaid + 未过期（validateOrderPayable）。
		// 只校验归属不够——已关闭订单若仍可创建支付单，支付成功后订单不会迁移（unpaid 前置），
		// 结果是钱已收、单已关、库存已回补转售。
		Map<String, Object> order = tradeOrderService.validateOrderPayable(orderId, userId);
		if (order == null) {
			return JsonResult.error("订单不存在、无权操作或当前状态不可支付");
		}

		PayRequestDTO request = new PayRequestDTO();
		request.setOrderId(orderId);
		request.setOrderNo((String) order.get("order_no"));
		request.setChannel(channel);
		request.setAmount(new BigDecimal(String.valueOf(order.get("total_amount"))));
		request.setSubject(appName + " - 订单 " + order.get("order_no"));
		request.setReturnUrl(returnUrl);
		request.setPayUserId(userId);
		request.setBizType("trade");

		Map<String, Object> result = payOrderService.createPayOrder(request);
		return JsonResult.success(result);
	}

	@Operation(summary = "查询支付状态")
	@GetMapping("/status/{payNo}")
	public JsonResult<Map<String, Object>> payStatus(@PathVariable String payNo) {
		Map<String, Object> result = payOrderService.queryPayStatus(payNo);
		if (result == null) {
			return JsonResult.error("支付单不存在");
		}

		// 归属校验：仅订单买家本人可查（防越权枚举支付单金额/渠道/状态）；
		// 校验失败与「支付单不存在」返回完全相同的响应，防探测区分两种失败
		Object orderIdObj = result.get("order_id");
		Long orderId = orderIdObj instanceof Number number ? number.longValue() : null;
		if (orderId == null || tradeOrderService.validateOrderOwnership(orderId, StpUtil.getLoginIdAsLong()) == null) {
			return JsonResult.error("支付单不存在");
		}

		return JsonResult.success(result);
	}

}
