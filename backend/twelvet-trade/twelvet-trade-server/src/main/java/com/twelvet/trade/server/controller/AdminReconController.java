package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.SettlementReconDiff;
import com.twelvet.trade.server.service.IReconService;
import com.twelvet.trade.server.service.ISellerAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营端对账差异控制器
 *
 * @author twelvet
 */
@Tag(description = "运营对账差异", name = "AdminReconController")
@RestController
@RequestMapping("/admin/marketplace/recon")
public class AdminReconController extends TWTController {

	private final IReconService reconService;

	private final ISellerAccountService accountService;

	public AdminReconController(IReconService reconService, ISellerAccountService accountService) {
		this.reconService = reconService;
		this.accountService = accountService;
	}

	/**
	 * 差异分页（可按业务日/状态筛选，tenantId 缺省看全租户）
	 */
	@Operation(summary = "差异分页")
	@SaCheckPermission("marketplace:admin:recon")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<SettlementReconDiff>> page(@RequestParam(required = false) String bizDate,
			@RequestParam(required = false) String status, @RequestParam(required = false) Long tenantId) {
		return JsonResult.success(reconService.adminPage(bizDate, status, tenantId));
	}

	/**
	 * 处理差异 body: {diffId, note}
	 */
	@Operation(summary = "处理差异")
	@SaCheckPermission("marketplace:admin:recon")
	@PostMapping("/resolve")
	public JsonResult<Void> resolve(@RequestBody Map<String, Object> body) {
		reconService.resolve(SettlementRequestParams.requiredLong(body, "diffId"),
				SettlementRequestParams.str(body.get("note")), StpUtil.getLoginIdAsLong(),
				SettlementRequestParams.tenantId());
		return JsonResult.success();
	}

	/**
	 * 解冻卖家（差异行只有 sellerId，按卖家定位账户）body: {sellerId, reason}
	 */
	@Operation(summary = "解冻卖家")
	@SaCheckPermission("marketplace:admin:recon")
	@PostMapping("/unfreeze")
	public JsonResult<Boolean> unfreeze(@RequestBody Map<String, Object> body) {
		boolean unfrozen = accountService.unfreezeBySeller(SettlementRequestParams.requiredLong(body, "sellerId"),
				SettlementRequestParams.tenantId(), StpUtil.getLoginIdAsLong(),
				SettlementRequestParams.str(body.get("reason")));
		return JsonResult.success(unfrozen);
	}

}