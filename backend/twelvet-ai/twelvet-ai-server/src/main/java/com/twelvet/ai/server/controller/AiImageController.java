package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.api.domain.AiImageTask;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.service.IAiImageTaskService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI图片生成控制器
 * <p>
 * 权限校验与分页查询在 Java 层完成；图片生成等外部能力代理到 Python FastAPI 服务
 */
@Tag(description = "AI图片生成接口", name = "AI图片生成")
@RestController
@RequestMapping("/ai/image")
public class AiImageController extends TWTController {

	private final AiServiceClient aiServiceClient;

	private final IAiImageTaskService aiImageTaskService;

	public AiImageController(AiServiceClient aiServiceClient, IAiImageTaskService aiImageTaskService) {
		this.aiServiceClient = aiServiceClient;
		this.aiImageTaskService = aiImageTaskService;
	}

	/**
	 * 生成图片（异步）
	 * @param aiImageTask 图片生成任务
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "生成图片")
	@Log(service = "AI图片生成", businessType = BusinessType.INSERT)
	@PostMapping("/generate")
	@SaCheckPermission("ai:image:generate")
	public JsonResult<Object> generate(@RequestBody AiImageTask aiImageTask) {
		return JsonResult.success(aiServiceClient.post("/ai/image/generate", aiImageTask, Object.class));
	}

	/**
	 * 查询图片生成任务状态
	 * @param taskId 任务ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "查询图片生成任务状态")
	@GetMapping("/status/{taskId}")
	@SaCheckPermission("ai:image:query")
	public JsonResult<Object> status(@PathVariable Long taskId) {
		return JsonResult.success(aiServiceClient.get("/ai/image/status/" + taskId, Object.class));
	}

	/**
	 * 获取图片生成任务列表
	 * @param aiImageTask 图片生成任务查询条件
	 * @return JsonResult<TableDataInfo<AiImageTask>>
	 */
	@Operation(summary = "获取图片生成任务列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:image:list")
	public JsonResult<TableDataInfo<AiImageTask>> pageQuery(AiImageTask aiImageTask) {
		PageUtils.startPage();
		List<AiImageTask> list = aiImageTaskService.selectAiImageTaskList(aiImageTask);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	/**
	 * 根据任务ID获取详情
	 * @param taskId 任务ID
	 * @return JsonResult<AiImageTask>
	 */
	@Operation(summary = "根据任务ID获取详情")
	@GetMapping("/{taskId}")
	@SaCheckPermission("ai:image:query")
	public JsonResult<AiImageTask> getByTaskId(@PathVariable Long taskId) {
		return JsonResult.success(aiImageTaskService.selectAiImageTaskById(taskId));
	}

	/**
	 * 删除图片生成任务
	 * @param taskIds 任务ID数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除图片生成任务")
	@Log(service = "AI图片生成", businessType = BusinessType.DELETE)
	@DeleteMapping("/{taskIds}")
	@SaCheckPermission("ai:image:remove")
	public JsonResult<String> remove(@PathVariable Long[] taskIds) {
		return json(aiImageTaskService.deleteAiImageTaskByIds(taskIds));
	}

}
