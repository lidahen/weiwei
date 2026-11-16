package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.service.ITradeRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 运营端退款管理控制器
 * <p>
 * 权限统一为 marketplace:admin:refund；归属校验不适用于运营视角（可看全量）， 但租户过滤仍由 Service 的 JOIN 保证。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "运营退款管理", name = "AdminRefundController")
@RestController
@RequestMapping("/admin/marketplace/refund")
public class AdminRefundController extends TWTController {

	private final ITradeRefundService refundService;

	public AdminRefundController(ITradeRefundService refundService) {
		this.refundService = refundService;
	}

	/**
	 * 退款单分页（可按状态筛选）
	 */
	@Operation(summary = "退款单分页")
	@SaCheckPermission("marketplace:admin:refund")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<TradeRefund>> page(@RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean overdue) {
		return JsonResult.success(refundService.adminPage(status, overdue));
	}

	/**
	 * 退款单详情（refund + logs + order 摘要）
	 */
	@Operation(summary = "退款单详情")
	@SaCheckPermission("marketplace:admin:refund")
	@GetMapping("/detail/{refundId}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long refundId) {
		return JsonResult.success(refundService.adminDetail(refundId));
	}

	/**
	 * 运营裁决退款单 body: {refundId, decision(buyer/seller/partial), amount, reason}
	 */
	@Operation(summary = "裁决退款单")
	@SaCheckPermission("marketplace:admin:refund")
	@PostMapping("/judge")
	public JsonResult<String> judge(@RequestBody Map<String, Object> body) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		Long refundId = RefundRequestParams.requiredLong(body, "refundId");
		refundService.judge(refundId, str(body.get("decision"), null), toBigDecimal(body.get("amount")),
				str(body.get("reason"), null), operatorId);
		return JsonResult.success("OK");
	}

	/**
	 * 运营手工收口 refunding 退款单 body: {refundId, result(success/fail), note}
	 */
	@Operation(summary = "手工收口退款单")
	@SaCheckPermission("marketplace:admin:refund")
	@PostMapping("/resolve")
	public JsonResult<String> resolve(@RequestBody Map<String, Object> body) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		Long refundId = RefundRequestParams.requiredLong(body, "refundId");
		refundService.resolve(refundId, str(body.get("result"), null), str(body.get("note"), null), operatorId);
		return JsonResult.success("OK");
	}

	/**
	 * 取字符串参数并归一 null（对齐 TradeRefundController.str）
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

}