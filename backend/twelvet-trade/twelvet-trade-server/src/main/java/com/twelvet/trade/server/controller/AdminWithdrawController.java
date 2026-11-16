package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeWithdraw;
import com.twelvet.trade.server.service.IWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营端提现管理控制器
 *
 * @author twelvet
 */
@Tag(description = "运营提现管理", name = "AdminWithdrawController")
@RestController
@RequestMapping("/admin/marketplace/withdraw")
public class AdminWithdrawController extends TWTController {

	private final IWithdrawService withdrawService;

	public AdminWithdrawController(IWithdrawService withdrawService) {
		this.withdrawService = withdrawService;
	}

	/**
	 * 提现分页（可按状态筛选，tenantId 缺省看全租户）
	 */
	@Operation(summary = "提现分页")
	@SaCheckPermission("marketplace:admin:withdraw")
	@GetMapping("/page")
	public JsonResult<TableDataInfo<TradeWithdraw>> page(@RequestParam(required = false) String status,
			@RequestParam(required = false) Long tenantId) {
		return JsonResult.success(withdrawService.adminPage(status, tenantId));
	}

	/**
	 * 提现审核 body: {withdrawId, action(approve/reject), reason}
	 */
	@Operation(summary = "提现审核")
	@SaCheckPermission("marketplace:admin:withdraw")
	@PostMapping("/review")
	public JsonResult<Void> review(@RequestBody Map<String, Object> body) {
		withdrawService.review(SettlementRequestParams.requiredLong(body, "withdrawId"),
				SettlementRequestParams.str(body.get("action")), SettlementRequestParams.str(body.get("reason")),
				StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId());
		return JsonResult.success();
	}

	/**
	 * 线下打款完成 body: {withdrawId, voucherUrl}
	 */
	@Operation(summary = "提现打款")
	@SaCheckPermission("marketplace:admin:withdraw")
	@PostMapping("/pay")
	public JsonResult<Void> pay(@RequestBody Map<String, Object> body) {
		withdrawService.markPaid(SettlementRequestParams.requiredLong(body, "withdrawId"),
				SettlementRequestParams.str(body.get("voucherUrl")), StpUtil.getLoginIdAsLong(),
				SettlementRequestParams.tenantId());
		return JsonResult.success();
	}

}