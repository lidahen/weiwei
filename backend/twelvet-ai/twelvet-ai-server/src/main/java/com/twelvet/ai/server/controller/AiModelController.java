package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI模型配置控制器
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务
 */
@Tag(description = "AI模型管理接口", name = "AI模型管理")
@RestController
@RequestMapping("/ai/model")
public class AiModelController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiModelController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 查询模型配置列表
	 * @param aiModel 查询条件
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "查询模型配置列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:model:list")
	public JsonResult<Object> pageQuery(AiModel aiModel) {
		return JsonResult.success(aiServiceClient.get("/ai/model/list", Object.class));
	}

	/**
	 * 按类型查询可用模型(AI 音乐/图片等页面给用户选模型用)
	 * <p>
	 * 终端用户功能, 登录即可; 转发到 Python /ai/model/by-type/{type}。 必须声明在 /{modelId} 之前之外, 否则会被 Long
	 * 主键路由吃掉。
	 * </p>
	 */
	@Operation(summary = "按类型查询模型列表")
	@GetMapping("/by-type/{modelType}")
	@SaCheckLogin
	public JsonResult<Object> byType(@PathVariable String modelType) {
		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		return JsonResult.success(aiServiceClient.get("/ai/model/by-type/" + modelType, Object.class, headers));
	}

	/**
	 * 根据模型ID获取详情
	 * @param modelId 模型ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "根据模型ID获取详情")
	@GetMapping("/{modelId}")
	@SaCheckPermission("ai:model:query")
	public JsonResult<Object> getByModelId(@PathVariable Long modelId) {
		return JsonResult.success(aiServiceClient.get("/ai/model/" + modelId, Object.class));
	}

	/**
	 * 新增模型配置
	 * @param aiModel 模型配置
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "新增模型配置")
	@Log(service = "AI模型管理", businessType = BusinessType.INSERT)
	@PostMapping
	@SaCheckPermission("ai:model:add")
	public JsonResult<Object> add(@RequestBody AiModel aiModel) {
		return JsonResult.success(aiServiceClient.post("/ai/model", aiModel, Object.class));
	}

	/**
	 * 修改模型配置
	 * @param aiModel 模型配置
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "修改模型配置")
	@Log(service = "AI模型管理", businessType = BusinessType.UPDATE)
	@PutMapping
	@SaCheckPermission("ai:model:update")
	public JsonResult<Object> edit(@RequestBody AiModel aiModel) {
		return JsonResult.success(aiServiceClient.put("/ai/model", aiModel, Object.class));
	}

	/**
	 * 删除模型配置
	 * @param modelIds 模型ID数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除模型配置")
	@Log(service = "AI模型管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{modelIds}")
	@SaCheckPermission("ai:model:remove")
	public JsonResult<String> remove(@PathVariable Long[] modelIds) {
		aiServiceClient.delete("/ai/model/" + StringUtils.arrayToCommaDelimitedString(modelIds));
		return JsonResult.success();
	}

	/**
	 * 切换默认模型状态
	 * @param aiModel 包含modelId和defaultFlag
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "切换默认模型状态")
	@Log(service = "AI模型管理", businessType = BusinessType.UPDATE)
	@PutMapping("/changeStatus")
	@SaCheckPermission("ai:model:update")
	public JsonResult<Object> changeStatus(@RequestBody AiModel aiModel) {
		return JsonResult.success(aiServiceClient.put("/ai/model/status", aiModel, Object.class));
	}

}
