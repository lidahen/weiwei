package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.service.ISettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营端结算管理控制器
 *
 * @author twelvet
 */
@Tag(description = "运营结算管理", name = "AdminSettlementController")
@RestController
@RequestMapping("/admin/marketplace/settlement")
public class AdminSettlementController extends TWTController {

	private final ISettlementService settlementService;

	public AdminSettlementController(ISettlementService settlementService) {
		this.settlementService = settlementService;
	}

	/**
	 * 结算单分页（可按状态筛选，tenantId 缺省看全租户）
	 */
	@Operation(summary = "结算单分页")
	@SaCheckPermission("marketplace:admin:settlement")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<SettlementOrder>> page(@RequestParam(required = false) String status,
			@RequestParam(required = false) Long tenantId) {
		return JsonResult.success(settlementService.adminPage(status, tenantId));
	}

	/**
	 * 结算单详情
	 */
	@Operation(summary = "结算单详情")
	@SaCheckPermission("marketplace:admin:settlement")
	@GetMapping("/detail/{settlementId}")
	public JsonResult<Map<String, Object>> detail(@PathVariable Long settlementId,
			@RequestParam(required = false) Long tenantId) {
		return JsonResult.success(settlementService.adminDetail(settlementId, tenantId));
	}

}