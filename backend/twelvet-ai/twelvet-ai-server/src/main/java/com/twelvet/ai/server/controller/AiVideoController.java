package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.api.domain.AiVideoTask;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.service.IAiVideoTaskService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI视频生成控制器
 * <p>
 * 权限校验与分页查询在 Java 层完成；视频生成/状态查询等外部能力代理到 Python FastAPI 服务
 */
@Tag(description = "AI视频生成接口", name = "AI视频生成")
@RestController
@RequestMapping("/ai/video")
public class AiVideoController extends TWTController {

	private final AiServiceClient aiServiceClient;

	private final IAiVideoTaskService aiVideoTaskService;

	public AiVideoController(AiServiceClient aiServiceClient, IAiVideoTaskService aiVideoTaskService) {
		this.aiServiceClient = aiServiceClient;
		this.aiVideoTaskService = aiVideoTaskService;
	}

	/**
	 * 生成视频
	 * @param aiVideoTask 视频生成任务
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "生成视频")
	@Log(service = "AI视频生成", businessType = BusinessType.INSERT)
	@PostMapping("/generate")
	@SaCheckPermission("ai:video:generate")
	public JsonResult<Object> generate(@RequestBody AiVideoTask aiVideoTask) {
		return JsonResult.success(aiServiceClient.post("/ai/video/generate", aiVideoTask, Object.class));
	}

	/**
	 * 查询视频生成任务状态
	 * @param taskId 任务ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "查询视频生成任务状态")
	@GetMapping("/status/{taskId}")
	@SaCheckPermission("ai:video:query")
	public JsonResult<Object> status(@PathVariable Long taskId) {
		return JsonResult.success(aiServiceClient.get("/ai/video/status/" + taskId, Object.class));
	}

	/**
	 * 获取视频生成任务列表
	 * @param aiVideoTask 视频生成任务查询条件
	 * @return JsonResult<TableDataInfo<AiVideoTask>>
	 */
	@Operation(summary = "获取视频生成任务列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:video:list")
	public JsonResult<TableDataInfo<AiVideoTask>> pageQuery(AiVideoTask aiVideoTask) {
		PageUtils.startPage();
		List<AiVideoTask> list = aiVideoTaskService.selectAiVideoTaskList(aiVideoTask);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	/**
	 * 根据任务ID获取详情
	 * @param taskId 任务ID
	 * @return JsonResult<AiVideoTask>
	 */
	@Operation(summary = "根据任务ID获取详情")
	@GetMapping("/{taskId}")
	@SaCheckPermission("ai:video:query")
	public JsonResult<AiVideoTask> getByTaskId(@PathVariable Long taskId) {
		return JsonResult.success(aiVideoTaskService.selectAiVideoTaskById(taskId));
	}

	/**
	 * 删除视频生成任务
	 * @param taskIds 任务ID数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除视频生成任务")
	@Log(service = "AI视频生成", businessType = BusinessType.DELETE)
	@DeleteMapping("/{taskIds}")
	@SaCheckPermission("ai:video:remove")
	public JsonResult<String> remove(@PathVariable Long[] taskIds) {
		return json(aiVideoTaskService.deleteAiVideoTaskByIds(taskIds));
	}

}
