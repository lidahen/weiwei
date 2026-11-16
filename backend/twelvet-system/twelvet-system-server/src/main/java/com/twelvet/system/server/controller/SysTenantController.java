package com.twelvet.system.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.system.api.domain.dto.RenewReq;
import com.twelvet.system.api.domain.vo.SysTenantPlanVO;
import com.twelvet.system.server.service.ISysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户管理（超管）— 商业化 P0：套餐查询 + 续费/升级
 */
@Tag(description = "租户管理（超管）", name = "SysTenantController")
@RestController
@RequestMapping("/system/tenant")
public class SysTenantController extends TWTController {

	private final ISysTenantService sysTenantService;

	public SysTenantController(ISysTenantService sysTenantService) {
		this.sysTenantService = sysTenantService;
	}

	@Operation(summary = "查询租户套餐详情")
	@SaCheckPermission("system:tenant:query")
	@GetMapping("/{tenantCode}")
	public JsonResult<SysTenantPlanVO> getTenantPlan(@PathVariable String tenantCode) {
		return JsonResult.success(sysTenantService.getPlanInfo(tenantCode));
	}

	@Operation(summary = "续费/升级套餐")
	@SaCheckPermission("system:tenant:edit")
	@Log(service = "租户管理", businessType = BusinessType.UPDATE)
	@PostMapping("/{tenantCode}/renew")
	public JsonResult<String> renew(@PathVariable String tenantCode, @Validated @RequestBody RenewReq req) {
		sysTenantService.renew(tenantCode, req.getPlanCode(), req.getMonths());
		return JsonResult.success("OK");
	}

}
