package com.twelvet.system.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.framework.security.utils.SecurityUtils;
import com.twelvet.system.api.domain.SysClientDetails;
import com.twelvet.system.server.service.ISysOauthClientDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author twelvet
 * @Description: OAuth2终端管理
 */
@Tag(description = "SysClientController", name = "OAuth2客户端")
@RestController
@RequestMapping("/system/client")
public class SysClientController extends TWTController {

	private final ISysOauthClientDetailsService sysOauthClientDetailsService;

	public SysClientController(ISysOauthClientDetailsService sysOauthClientDetailsService) {
		this.sysOauthClientDetailsService = sysOauthClientDetailsService;
	}

	/**
	 * 获取终端列表
	 * @param sysClientDetails SysClientDetails
	 * @return JsonResult<TableDataInfo>
	 */
	@Operation(summary = "获取终端列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("system:client:list")
	public JsonResult<TableDataInfo<SysClientDetails>> pageQuery(SysClientDetails sysClientDetails) {
		PageUtils.startPage();
		List<SysClientDetails> list = sysOauthClientDetailsService.selectSysClientDetailsList(sysClientDetails);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	/**
	 * 根据终端编号获取详细信息
	 * @param clientId 终端编号
	 * @return JsonResult<SysClientDetails>
	 */
	@Operation(summary = "根据终端编号获取详细信息")
	@GetMapping("/{clientId}")
	@SaCheckPermission("system:client:query")
	public JsonResult<SysClientDetails> getByClientId(@PathVariable String clientId) {
		return JsonResult.success(sysOauthClientDetailsService.selectSysClientDetailsById(clientId));
	}

	/**
	 * 新增终端
	 * @param sysClientDetails SysClientDetails
	 * @return JsonResult<String>
	 */
	@Operation(summary = "新增终端")
	@Log(service = "OAuth2终端管理", businessType = BusinessType.INSERT)
	@PostMapping
	@SaCheckPermission("system:client:insert")
	public JsonResult<String> insert(@Validated @RequestBody SysClientDetails sysClientDetails) {
		sysClientDetails.setCreateBy(SecurityUtils.getUsername());
		return json(sysOauthClientDetailsService.insertSysClientDetails(sysClientDetails));
	}

	/**
	 * 修改终端
	 * @param sysClientDetails SysClientDetails
	 * @return JsonResult<String>
	 */
	@Operation(summary = "修改终端")
	@Log(service = "OAuth2终端管理", businessType = BusinessType.UPDATE)
	@PutMapping
	@SaCheckPermission("system:client:update")
	public JsonResult<String> update(@Validated @RequestBody SysClientDetails sysClientDetails) {
		sysClientDetails.setUpdateBy(SecurityUtils.getUsername());
		return json(sysOauthClientDetailsService.updateSysClientDetails(sysClientDetails));
	}

	/**
	 * 删除终端
	 * @param clientIds 终端编号数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除终端")
	@Log(service = "OAuth2终端管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{clientIds}")
	@SaCheckPermission("system:client:remove")
	public JsonResult<String> remove(@PathVariable String[] clientIds) {
		return json(sysOauthClientDetailsService.deleteSysClientDetailsByIds(clientIds));
	}

}
