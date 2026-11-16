package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.RefundReason;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.service.ITradeRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 退款售后控制器（C 端买家 + 卖家端）
 * <p>
 * 职责：HTTP 参数绑定、身份取自登录态、调用 Service、包装响应。归属校验全部在 Service 内。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "退款售后", name = "TradeRefundController")
@RestController
@RequestMapping("/trade/refund")
public class TradeRefundController extends TWTController {

	private final ITradeRefundService refundService;

	public TradeRefundController(ITradeRefundService refundService) {
		this.refundService = refundService;
	}

	/**
	 * 申请退款 body: {orderId, type, reasonCode, amount, description, evidenceImages[]}
	 */
	@Operation(summary = "申请退款")
	@PostMapping("/apply")
	public JsonResult<Long> apply(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long orderId = RefundRequestParams.requiredLong(body, "orderId");
		String type = str(body.get("type"), null);
		String reasonCode = str(body.get("reasonCode"), null);
		BigDecimal amount = toBigDecimal(body.get("amount"));
		String description = str(body.get("description"), null);
		List<String> evidenceImages = toStringList(body.get("evidenceImages"));
		return JsonResult
			.success(refundService.apply(orderId, type, reasonCode, amount, description, evidenceImages, userId));
	}

	/**
	 * 退款详情 + 时间线（仅买卖双方可见）
	 */
	@Operation(summary = "退款详情")
	@SaCheckLogin
	@GetMapping("/detail/{refundId}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long refundId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(refundService.detail(refundId, userId));
	}

	/**
	 * 我的退款列表（role=buyer/seller）
	 */
	@Operation(summary = "我的退款列表")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<TradeRefund>> page(@RequestParam(defaultValue = "buyer") String role,
			@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(refundService.page(userId, role, status));
	}

	/**
	 * 按订单查退款状态与是否可申请（前端订单详情的退款入口）
	 */
	@Operation(summary = "按订单查退款状态")
	@GetMapping("/by-order/{orderId}")
	public JsonResult<Map<String, Object>> byOrder(@PathVariable Long orderId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(refundService.checkApply(orderId, userId));
	}

	/**
	 * 退款原因白名单（前端不再硬编码）
	 */
	@Operation(summary = "退款原因白名单")
	@GetMapping("/reasons")
	public JsonResult<List<Map<String, Object>>> reasons() {
		return JsonResult.success(RefundReason.all());
	}

	/**
	 * 买家撤销退款申请
	 */
	@Operation(summary = "买家撤销退款")
	@PostMapping("/cancel")
	public JsonResult<String> cancel(@RequestParam Long refundId) {
		Long userId = StpUtil.getLoginIdAsLong();
		refundService.buyerCancel(refundId, userId);
		return JsonResult.success("OK");
	}

	/**
	 * 买家填写退货物流 body: {refundId, company, returnNo}
	 */
	@Operation(summary = "买家填写退货物流")
	@PostMapping("/return")
	public JsonResult<String> buyerReturn(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long refundId = RefundRequestParams.requiredLong(body, "refundId");
		refundService.buyerReturn(refundId, str(body.get("company"), null), str(body.get("returnNo"), null), userId);
		return JsonResult.success("OK");
	}

	/**
	 * 买家申请平台介入
	 */
	@Operation(summary = "买家申请平台介入")
	@PostMapping("/mediate")
	public JsonResult<String> mediate(@RequestParam Long refundId) {
		Long userId = StpUtil.getLoginIdAsLong();
		refundService.buyerMediate(refundId, userId);
		return JsonResult.success("OK");
	}

	/**
	 * 待我处理（卖家视角）的退款列表
	 */
	@Operation(summary = "卖家退款列表")
	@GetMapping("/seller/page")
	public JsonResult<TableDataInfo<TradeRefund>> sellerPage(@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(refundService.sellerPage(userId, status));
	}

	/**
	 * 卖家同意退款 body: {refundId, returnAddress}（退货退款必须提供退货地址）
	 */
	@Operation(summary = "卖家同意退款")
	@PostMapping("/seller/agree")
	public JsonResult<String> sellerAgree(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long refundId = RefundRequestParams.requiredLong(body, "refundId");
		refundService.sellerAgree(refundId, str(body.get("returnAddress"), null), userId);
		return JsonResult.success("OK");
	}

	/**
	 * 卖家拒绝退款 body: {refundId, reason}
	 */
	@Operation(summary = "卖家拒绝退款")
	@PostMapping("/seller/reject")
	public JsonResult<String> sellerReject(@RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Long refundId = RefundRequestParams.requiredLong(body, "refundId");
		refundService.sellerReject(refundId, str(body.get("reason"), null), userId);
		return JsonResult.success("OK");
	}

	/**
	 * 卖家确认收到退货（触发退款）
	 */
	@Operation(summary = "卖家确认收到退货")
	@PostMapping("/seller/confirm-receipt")
	public JsonResult<String> sellerConfirmReceipt(@RequestParam Long refundId) {
		Long userId = StpUtil.getLoginIdAsLong();
		refundService.sellerConfirmReceipt(refundId, userId);
		return JsonResult.success("OK");
	}

	/**
	 * 取字符串参数并归一 null（对齐 AdminMarketplaceController.str）
	 * <p>
	 * 不能用 {@code String.valueOf(body.get(key))}：键存在但值为 null 时得到字面量 "null"， 会被落库或拼进业务文案。
	 * </p>
	 */
	private static String str(Object v, String defaultValue) {
		if (v == null) {
			return defaultValue;
		}
		String s = String.valueOf(v);
		return s.isEmpty() || "null".equals(s) ? defaultValue : s;
	}

	/**
	 * 宽松解析金额（非法值按 null 处理，由 Service 校验）
	 */
	private static BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return new BigDecimal(String.valueOf(value).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 证据图 URL 列表（非法类型按 null 处理）
	 */
	private static List<String> toStringList(Object value) {
		if (!(value instanceof List<?> list)) {
			return null;
		}
		List<String> result = new ArrayList<>(list.size());
		for (Object item : list) {
			if (item != null) {
				result.add(String.valueOf(item));
			}
		}
		return result;
	}

}