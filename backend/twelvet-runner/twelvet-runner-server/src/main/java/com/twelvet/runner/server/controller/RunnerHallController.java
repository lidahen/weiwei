package com.twelvet.runner.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.service.IRunnerHallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 互助大厅 - 接单人视角
 *
 * @author twelvet
 */
@Tag(description = "互助大厅", name = "RunnerHallController")
@RestController
@RequestMapping("/runner/hall")
public class RunnerHallController extends TWTController {

	private final IRunnerHallService hallService;

	public RunnerHallController(IRunnerHallService hallService) {
		this.hallService = hallService;
	}

	@SaCheckLogin
	@Operation(summary = "互助大厅（按类型/价格筛选）")
	@GetMapping("")
	public JsonResult<List<RunnerTask>> hall(@RequestParam(required = false) Integer taskType,
			@RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(defaultValue = "30") Integer limit) {
		return JsonResult.success(hallService.hall(taskType, minPrice, maxPrice, limit));
	}

	@SaCheckLogin
	@Operation(summary = "接单（乐观锁原子操作）")
	@PostMapping("/{id}/assign")
	public JsonResult<Map<String, Object>> assign(@PathVariable Long id) {
		Long runnerId = StpUtil.getLoginIdAsLong();
		boolean ok = hallService.assign(id, runnerId);
		Map<String, Object> resp = new HashMap<>();
		if (ok) {
			resp.put("success", true);
			resp.put("taskId", id);
			return JsonResult.success(resp);
		}
		resp.put("success", false);
		resp.put("reason", "已被接单 / 任务不存在 / 已过期 / 自接");
		return JsonResult.error("接单失败");
	}

	@SaCheckLogin
	@Operation(summary = "开始服务")
	@PostMapping("/{id}/start")
	public JsonResult<String> start(@PathVariable Long id) {
		Long runnerId = StpUtil.getLoginIdAsLong();
		return hallService.start(id, runnerId) ? JsonResult.success("OK") : JsonResult.error("操作失败");
	}

	@SaCheckLogin
	@Operation(summary = "提交完成（附凭证图）")
	@PostMapping("/{id}/finish")
	public JsonResult<String> finish(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
		Long runnerId = StpUtil.getLoginIdAsLong();
		String finishImages = body == null ? null : (String) body.get("finishImages");
		return hallService.finish(id, runnerId, finishImages) ? JsonResult.success("OK") : JsonResult.error("操作失败");
	}

	@SaCheckLogin
	@Operation(summary = "拒单")
	@PostMapping("/{id}/reject")
	public JsonResult<String> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
		Long runnerId = StpUtil.getLoginIdAsLong();
		String reason = body == null || body.get("reason") == null ? "接单人拒单" : String.valueOf(body.get("reason"));
		return hallService.reject(id, runnerId, reason) ? JsonResult.success("OK") : JsonResult.error("操作失败");
	}

	@SaCheckLogin
	@Operation(summary = "我接的任务")
	@GetMapping("/my-taken")
	public JsonResult<List<RunnerTask>> myTaken(@RequestParam(required = false) String status) {
		Long runnerId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(hallService.listMyTaken(runnerId, status));
	}

	@SaCheckLogin
	@Operation(summary = "任务流转日志")
	@GetMapping("/{id}/logs")
	public JsonResult<List<RunnerTaskLog>> logs(@PathVariable Long id) {
		return JsonResult.success(hallService.taskLogs(id));
	}

}
