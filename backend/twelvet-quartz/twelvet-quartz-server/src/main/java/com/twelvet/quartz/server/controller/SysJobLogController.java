package com.twelvet.quartz.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.quartz.api.domain.SysJobLog;
import com.twelvet.quartz.server.service.ISysJobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author twelvet
 * @Description: 调度日志操作处理
 */
@Tag(description = "定时任务日志接口", name = "定时任务日志")
@RestController
@RequestMapping("/job/log")
public class SysJobLogController extends TWTController {

	private final ISysJobLogService jobLogService;

	public SysJobLogController(ISysJobLogService jobLogService) {
		this.jobLogService = jobLogService;
	}

	/**
	 * 查询定时任务调度日志列表
	 * @param sysJobLog SysJobLog
	 * @return JsonResult
	 */
	@Operation(summary = "查询定时任务调度日志列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("monitor:job:query")
	public JsonResult<TableDataInfo<SysJobLog>> pageQuery(SysJobLog sysJobLog) {
		PageUtils.startPage();
		List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	/**
	 * 导出定时任务调度日志列表
	 * @param sysJobLog SysJobLog
	 * @return List<SysJobLog>
	 */
	@ResponseExcel(name = "任务调度日志")
	@Operation(summary = "导出定时任务调度日志列表")
	@Log(service = "任务调度日志", businessType = BusinessType.EXPORT)
	@PostMapping("/export")
	@SaCheckPermission("monitor:job:export")
	public List<SysJobLog> export(@RequestBody SysJobLog sysJobLog) {
		return jobLogService.selectJobLogList(sysJobLog);
	}

	/**
	 * 根据调度编号获取详细信息
	 * @param jobLogId id
	 * @return JsonResult
	 */
	@Operation(summary = "根据调度编号获取详细信息")
	@GetMapping("/{jobLogId}")
	@SaCheckPermission("monitor:job:query")
	public JsonResult<SysJobLog> getInfo(@PathVariable Long jobLogId) {
		return JsonResult.success(jobLogService.selectJobLogById(jobLogId));
	}

	/**
	 * 删除定时任务调度日志
	 * @param jobLogIds 数组id
	 * @return JsonResult
	 */
	@Operation(summary = "删除定时任务调度日志")
	@Log(service = "定时任务调度日志", businessType = BusinessType.DELETE)
	@DeleteMapping("/{jobLogIds}")
	@SaCheckPermission("monitor:job:remove")
	public JsonResult<String> remove(@PathVariable Long[] jobLogIds) {
		return json(jobLogService.deleteJobLogByIds(jobLogIds));
	}

	/**
	 * 清空定时任务调度日志
	 * @return JsonResult
	 */
	@Operation(summary = "清空定时任务调度日志")
	@Log(service = "调度日志", businessType = BusinessType.CLEAN)
	@DeleteMapping("/clean")
	@SaCheckPermission("monitor:job:remove")
	public JsonResult<String> clean() {
		jobLogService.cleanJobLog();
		return JsonResult.success();
	}

}
