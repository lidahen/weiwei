package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.service.ITradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单控制器 职责: HTTP参数绑定、权限校验、调用Service、包装响应 业务逻辑全部委托给TradeOrderService
 *
 * @author twelvet
 */
@Tag(description = "订单管理", name = "TradeOrderController")
@RestController
@RequestMapping("/trade/order")
public class TradeOrderController extends TWTController {

	private final ITradeOrderService tradeOrderService;

	public TradeOrderController(ITradeOrderService tradeOrderService) {
		this.tradeOrderService = tradeOrderService;
	}

	/**
	 * 我的订单列表 (role=buyer/seller, status可选)
	 */
	@Operation(summary = "我的订单列表")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<TradeOrder>> page(@RequestParam(defaultValue = "buyer") String role,
			@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(tradeOrderService.selectOrderList(userId, role, status));
	}

	/**
	 * 订单详情（仅订单双方可见）
	 */
	@Operation(summary = "订单详情")
	@SaCheckLogin
	@GetMapping("/detail/{orderId}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long orderId) {
		Long viewerId = StpUtil.getLoginIdAsLong();
		Map<String, Object> detail = tradeOrderService.selectOrderDetail(orderId, viewerId);
		if (detail.isEmpty()) {
			return JsonResult.success(null);
		}
		return JsonResult.success(detail);
	}

	/**
	 * 创建订单（真实下单） body: {productId, addressId?, buyerRemark?, deliveryType?}
	 */
	@Operation(summary = "创建订单")
	@PostMapping("/create")
	public JsonResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long productId = Long.valueOf(String.valueOf(body.get("productId")));
		Long addressId = toLong(body.get("addressId"));
		String buyerRemark = body.get("buyerRemark") != null ? String.valueOf(body.get("buyerRemark")) : null;
		String deliveryType = body.get("deliveryType") != null ? String.valueOf(body.get("deliveryType")) : null;
		Map<String, Object> result = tradeOrderService.createOrder(userId, productId, addressId, buyerRemark,
				deliveryType);
		return JsonResult.success(result);
	}

	/**
	 * 宽松解析 Long（addressId 等可空参数；非法值按 null 处理）
	 */
	private Long toLong(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(value).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 确认收货
	 */
	@Operation(summary = "确认收货")
	@PostMapping("/confirm")
	public JsonResult<String> confirm(@RequestParam Long orderId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(tradeOrderService.confirmReceipt(orderId, userId));
	}

	/**
	 * 买家取消未支付订单（释放库存）
	 */
	@Operation(summary = "买家取消订单")
	@PostMapping("/cancel")
	public JsonResult<String> cancel(@RequestParam Long orderId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(tradeOrderService.cancelOrder(orderId, userId));
	}

	/**
	 * 卖家发货
	 */
	@Operation(summary = "卖家发货")
	@PostMapping("/ship")
	public JsonResult<String> ship(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
		String company = String.valueOf(body.get("company"));
		String trackingNo = String.valueOf(body.get("no"));
		return JsonResult.success(tradeOrderService.shipOrder(orderId, userId, company, trackingNo));
	}

	/**
	 * Ping健康检查
	 */
	@Operation(summary = "Ping")
	@GetMapping("/ping")
	public JsonResult<String> ping() {
		return JsonResult.success("pong from trade/order");
	}

}
