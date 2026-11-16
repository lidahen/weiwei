package com.twelvet.payment.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.payment.api.service.ITenantRenewPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租户套餐扫码续费支付（商业化 P2，超管操作）
 *
 * @author twelvet
 */
@Tag(description = "租户续费支付", name = "TenantRenewPayController")
@RestController
@RequestMapping("/payment/tenant-renew")
public class TenantRenewPayController extends TWTController {

	private final ITenantRenewPayService renewPayService;

	public TenantRenewPayController(ITenantRenewPayService renewPayService) {
		this.renewPayService = renewPayService;
	}

	@Operation(summary = "创建续费支付单")
	@SaCheckPermission("system:tenant:edit")
	@Log(service = "租户续费支付", businessType = BusinessType.INSERT)
	@PostMapping("/{tenantCode}/order")
	public JsonResult<Map<String, Object>> createOrder(@PathVariable String tenantCode,
			@RequestBody Map<String, Object> body) {
		String planCode = (String) body.get("planCode");
		Object monthsObj = body.get("months");
		Object channelObj = body.get("channel");
		int months = monthsObj instanceof Number n ? n.intValue() : 0;
		String channel = channelObj == null || String.valueOf(channelObj).isBlank() ? "alipay"
				: String.valueOf(channelObj);
		return JsonResult.success(renewPayService.createOrder(tenantCode, planCode, months, channel));
	}

	@Operation(summary = "查询续费支付状态")
	@SaCheckPermission("system:tenant:query")
	@GetMapping("/status/{payNo}")
	public JsonResult<Map<String, Object>> status(@PathVariable String payNo) {
		return JsonResult.success(renewPayService.status(payNo));
	}

	@Operation(summary = "模拟支付成功（仅 mock 模式）")
	@SaCheckPermission("system:tenant:edit")
	@Log(service = "租户续费支付", businessType = BusinessType.UPDATE)
	@PostMapping("/mock-pay/{payNo}")
	public JsonResult<String> mockPay(@PathVariable String payNo) {
		renewPayService.mockPay(payNo);
		return JsonResult.success("OK");
	}

}
