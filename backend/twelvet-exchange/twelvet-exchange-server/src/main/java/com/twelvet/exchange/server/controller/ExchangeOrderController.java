package com.twelvet.exchange.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.exchange.server.service.IExchangeOrderService;
import com.twelvet.exchange.server.service.IExchangeReviewService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 交换订单 - 双边履约（交付/确认/取消/纠纷/评价）
 *
 * @author twelvet
 */
@Tag(description = "交换订单（双边履约）", name = "ExchangeOrderController")
@RestController
@RequestMapping("/exchange/order")
public class ExchangeOrderController extends TWTController {

	private final IExchangeOrderService orderService;

	private final IExchangeReviewService reviewService;

	public ExchangeOrderController(IExchangeOrderService orderService, IExchangeReviewService reviewService) {
		this.orderService = orderService;
		this.reviewService = reviewService;
	}

	@SaCheckLogin
	@Operation(summary = "我的交换订单（A/B 双视角，真分页；statuses 逗号分隔，如 created,delivering）")
	@GetMapping("/my-orders")
	public TableDataInfo myOrders(@RequestParam(required = false) String statuses,
			@RequestParam(required = false, defaultValue = "1") Long pageNum,
			@RequestParam(required = false, defaultValue = "10") Long pageSize) {
		Long userId = StpUtil.getLoginIdAsLong();
		List<String> statusList = statuses == null || statuses.isBlank() ? List.of()
				: List.of(statuses.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		return orderService.myOrdersPage(userId, statusList, pageNum, pageSize);
	}

	@SaCheckLogin
	@Operation(summary = "订单详情（双边进度时间线 + 面交码 + 对方信息 + 流转日志）")
	@GetMapping("/{id}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		Map<String, Object> detail = orderService.detail(id, userId);
		if (detail == null) {
			return JsonResult.error("订单不存在");
		}
		return JsonResult.success(detail);
	}

	@SaCheckLogin
	@Operation(summary = "标记我方已交付（face 点按钮 / express 传运单号）")
	@PostMapping("/{id}/deliver")
	public JsonResult<String> deliver(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String trackNo = body == null || body.get("trackNo") == null ? null : String.valueOf(body.get("trackNo"));
		orderService.deliver(id, userId, trackNo);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "确认已收到对方物品（双确认 → completed）")
	@PostMapping("/{id}/confirm")
	public JsonResult<String> confirm(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		orderService.confirm(id, userId);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "取消（仅双方均未交付，物品解锁）")
	@PostMapping("/{id}/cancel")
	public JsonResult<String> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String reason = body.get("reason") == null ? "用户取消" : String.valueOf(body.get("reason"));
		orderService.cancel(id, userId, reason);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "发起纠纷（附原因，后台裁决）")
	@PostMapping("/{id}/dispute")
	public JsonResult<String> dispute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String reason = body.get("reason") == null ? "用户发起纠纷" : String.valueOf(body.get("reason"));
		orderService.dispute(id, userId, reason);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "提交评价（1-5 星，仅 completed 订单参与方）")
	@PostMapping("/{id}/review")
	public JsonResult<Map<String, Object>> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Integer score = body.get("score") instanceof Number n ? n.intValue() : null;
		String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
		return JsonResult.success(reviewService.submitReview(id, userId, score, content));
	}

}
