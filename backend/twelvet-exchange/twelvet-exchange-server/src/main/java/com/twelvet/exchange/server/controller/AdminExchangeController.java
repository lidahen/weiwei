package com.twelvet.exchange.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.exchange.server.service.IExchangeItemService;
import com.twelvet.exchange.server.service.IExchangeOrderService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 交换广场管理后台
 *
 * @author twelvet
 */
@Tag(description = "交换广场管理后台", name = "AdminExchangeController")
@RestController
@RequestMapping("/admin/exchange")
public class AdminExchangeController extends TWTController {

	private final IExchangeItemService itemService;

	private final IExchangeOrderService orderService;

	public AdminExchangeController(IExchangeItemService itemService, IExchangeOrderService orderService) {
		this.itemService = itemService;
		this.orderService = orderService;
	}

	@SaCheckPermission("exchange:admin:item:list")
	@Operation(summary = "物品管理/待审列表（auditing 置顶）")
	@GetMapping("/item")
	public TableDataInfo items(@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "20") Long pageSize, @RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return itemService.adminPage(pageNum, pageSize, status, keyword);
	}

	@SaCheckPermission("exchange:admin:item:audit")
	@Operation(summary = "人工审核通过（auditing → onswap）")
	@PostMapping("/item/{id}/pass")
	public JsonResult<String> pass(@PathVariable Long id) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		itemService.adminPass(id, operatorId);
		return JsonResult.success("OK");
	}

	@SaCheckPermission("exchange:admin:item:audit")
	@Operation(summary = "人工审核驳回（auditing → rejected，IM 通知主人）")
	@PostMapping("/item/{id}/reject")
	public JsonResult<String> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		String reason = body.get("reason") == null ? "内容涉嫌违规" : String.valueOf(body.get("reason"));
		itemService.adminReject(id, operatorId, reason);
		return JsonResult.success("OK");
	}

	@SaCheckPermission("exchange:admin:order:list")
	@Operation(summary = "订单列表（disputed 置顶）")
	@GetMapping("/order")
	public TableDataInfo orders(@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "20") Long pageSize, @RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return orderService.adminPage(pageNum, pageSize, status, keyword);
	}

	@SaCheckPermission("exchange:admin:order:force")
	@Operation(summary = "裁决：按现状完成（disputed → completed）")
	@PostMapping("/order/{id}/force-complete")
	public JsonResult<String> forceComplete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		String remark = body.get("remark") == null ? "平台裁决按现状完成" : String.valueOf(body.get("remark"));
		orderService.adminForceComplete(id, operatorId, remark);
		return JsonResult.success("OK");
	}

	@SaCheckPermission("exchange:admin:order:force")
	@Operation(summary = "裁决：取消并解锁物品（disputed → cancelled）")
	@PostMapping("/order/{id}/force-cancel")
	public JsonResult<String> forceCancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long operatorId = StpUtil.getLoginIdAsLong();
		String remark = body.get("remark") == null ? "平台裁决取消" : String.valueOf(body.get("remark"));
		orderService.adminForceCancel(id, operatorId, remark);
		return JsonResult.success("OK");
	}

	@SaCheckPermission("exchange:admin:stat:view")
	@Operation(summary = "数据看板（在架物品数、意向量、成交率、纠纷率、热门类目）")
	@GetMapping("/stat")
	public JsonResult<Map<String, Object>> stat() {
		return JsonResult.success(itemService.stat());
	}

}
