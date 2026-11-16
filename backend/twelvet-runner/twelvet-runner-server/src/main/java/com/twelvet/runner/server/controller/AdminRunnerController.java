package com.twelvet.runner.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.runner.server.service.IRunnerTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 互助管理后台
 *
 * @author twelvet
 */
@Tag(description = "互助管理后台", name = "AdminRunnerController")
@RestController
@RequestMapping("/admin/runner")
public class AdminRunnerController extends TWTController {

	private final IRunnerTaskService taskService;

	public AdminRunnerController(IRunnerTaskService taskService) {
		this.taskService = taskService;
	}

	@SaCheckPermission("runner:task:list")
	@Operation(summary = "任务列表（管理后台）")
	@GetMapping("/task/page")
	public TableDataInfo page(@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "20") Long pageSize, @RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return taskService.adminPage(pageNum, pageSize, status, keyword);
	}

	@SaCheckPermission("runner:stat:view")
	@Operation(summary = "数据看板统计")
	@GetMapping("/statistics")
	public JsonResult<Map<String, Object>> statistics() {
		return JsonResult.success(taskService.statistics());
	}

}
