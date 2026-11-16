package com.twelvet.trade.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeWithdraw;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.IWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 卖家结算中心控制器（C 端）
 * <p>
 * 身份取登录态，租户取上下文；不标注权限注解，登录由全局拦截器保证。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "卖家结算中心", name = "SellerSettlementController")
@RestController
@RequestMapping("/trade/seller")
public class SellerSettlementController extends TWTController {

	private final ISellerAccountService accountService;

	private final IWithdrawService withdrawService;

	public SellerSettlementController(ISellerAccountService accountService, IWithdrawService withdrawService) {
		this.accountService = accountService;
		this.withdrawService = withdrawService;
	}

	/**
	 * 卖家账户信息（脱敏）
	 */
	@Operation(summary = "卖家账户信息")
	@GetMapping("/account")
	public JsonResult<Map<String, Object>> account() {
		return JsonResult
			.success(accountService.myAccount(StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId()));
	}

	/**
	 * 提交实名资料 body: {accountName, idCardNo, bankCardNo, bankName}
	 */
	@Operation(summary = "提交实名资料")
	@PostMapping("/account/apply")
	public JsonResult<Void> applyKyc(@RequestBody Map<String, Object> body) {
		accountService.submitKyc(StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId(),
				SettlementRequestParams.str(body.get("accountName")), SettlementRequestParams.str(body.get("idCardNo")),
				SettlementRequestParams.str(body.get("bankCardNo")), SettlementRequestParams.str(body.get("bankName")));
		return JsonResult.success();
	}

	/**
	 * 申请提现 body: {amount}
	 */
	@Operation(summary = "申请提现")
	@PostMapping("/withdraw/apply")
	public JsonResult<BigDecimal> applyWithdraw(@RequestBody Map<String, Object> body) {
		return JsonResult.success(withdrawService.apply(StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId(),
				SettlementRequestParams.decimal(body.get("amount"))));
	}

	/**
	 * 卖家提现记录
	 */
	@Operation(summary = "卖家提现记录")
	@GetMapping("/withdraw/page")
	public JsonResult<TableDataInfo<TradeWithdraw>> withdrawPage() {
		return JsonResult
			.success(withdrawService.myPage(StpUtil.getLoginIdAsLong(), SettlementRequestParams.tenantId()));
	}

}