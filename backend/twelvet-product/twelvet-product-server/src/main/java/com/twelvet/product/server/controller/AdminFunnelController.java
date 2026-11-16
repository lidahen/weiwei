package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.api.service.IFunnelEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营端社区漏斗看板。
 * <p>
 * 权限 marketplace:admin:funnel；所有数据按 tenantId 切片，tenantId 为空时只返回社区选项（spec D7/D8）。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "社区漏斗看板", name = "AdminFunnelController")
@RestController
@RequestMapping("/admin/marketplace/funnel")
public class AdminFunnelController extends TWTController {

	private final IFunnelEventService funnelEventService;

	public AdminFunnelController(IFunnelEventService funnelEventService) {
		this.funnelEventService = funnelEventService;
	}

	/**
	 * 漏斗事件聚合（按日/社区）
	 */
	@Operation(summary = "社区漏斗统计")
	@SaCheckPermission("marketplace:admin:funnel")
	@GetMapping("/stats")
	public JsonResult<Map<String, Object>> stats(@RequestParam(required = false) Integer days,
			@RequestParam(required = false) Long tenantId) {
		return JsonResult.success(funnelEventService.adminStats(days, tenantId));
	}

}