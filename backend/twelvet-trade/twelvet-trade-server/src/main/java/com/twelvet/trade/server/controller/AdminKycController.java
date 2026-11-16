package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.service.ISellerAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营端实名审核控制器
 *
 * @author twelvet
 */
@Tag(description = "运营实名审核", name = "AdminKycController")
@RestController
@RequestMapping("/admin/marketplace/kyc")
public class AdminKycController extends TWTController {

	private final ISellerAccountService accountService;

	public AdminKycController(ISellerAccountService accountService) {
		this.accountService = accountService;
	}

	/**
	 * 实名分页（可按状态筛选，tenantId 缺省看全租户）
	 */
	@Operation(summary = "实名分页")
	@SaCheckPermission("marketplace:admin:kyc")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<SellerAccount>> page(@RequestParam(required = false) String kycStatus,
			@RequestParam(required = false) Long tenantId) {
		return JsonResult.success(accountService.adminKycPage(kycStatus, tenantId));
	}

	/**
	 * 实名审核 body: {accountId, result(verified/rejected), note}
	 */
	@Operation(summary = "实名审核")
	@SaCheckPermission("marketplace:admin:kyc")
	@PostMapping("/review")
	public JsonResult<Void> review(@RequestBody Map<String, Object> body) {
		accountService.reviewKyc(SettlementRequestParams.requiredLong(body, "accountId"),
				SettlementRequestParams.str(body.get("result")), SettlementRequestParams.str(body.get("note")),
				StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId());
		return JsonResult.success();
	}

}