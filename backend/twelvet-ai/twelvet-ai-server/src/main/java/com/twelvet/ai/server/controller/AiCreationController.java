package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.api.domain.AiCreationProject;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.service.IAiCreationProjectService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI创作工作台控制器
 * <p>
 * 权限校验与分页查询在 Java 层完成；拆解/脚本分镜/渲染合成等业务代理到 Python FastAPI 服务。 产物文件（成片/分镜图/配音）由 ai-service 的
 * OSS 转储或 /ai/creation/media/* 提供。
 * <p>
 * 透传契约：前端请求体一律发 snake_case 字段（Map 原样透传，Jackson 的 SNAKE_CASE 策略 只作用于 bean 属性名，不改 Map
 * key）；计费身份经 billingHeaders() 以 X-Tenant-Id/X-User-Id 透传给 Python 侧配额检查与用量落账。
 */
@Tag(description = "AI创作工作台接口", name = "AI创作工作台")
@RestController
@RequestMapping("/ai/creation")
public class AiCreationController extends TWTController {

	private final AiServiceClient aiServiceClient;

	private final IAiCreationProjectService aiCreationProjectService;

	public AiCreationController(AiServiceClient aiServiceClient, IAiCreationProjectService aiCreationProjectService) {
		this.aiServiceClient = aiServiceClient;
		this.aiCreationProjectService = aiCreationProjectService;
	}

	/**
	 * 用量计费身份头：透传 X-Tenant-Id / X-User-Id 给 Python（与 AiAssistController 惯例一致）， 供
	 * ai-service 侧事前配额检查（quota_ai_call_day）与调用明细落账（ai_usage_log）。
	 */
	private Map<String, String> billingHeaders() {
		Map<String, String> headers = new HashMap<>();
		Long tenantId = TenantContext.get();
		if (tenantId != null) {
			headers.put("X-Tenant-Id", String.valueOf(tenantId));
		}
		try {
			headers.put("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		}
		catch (Exception e) {
			// 登录态理论上由 Sa-Token 保证；异常时忽略用户头，不阻断代理
		}
		return headers;
	}

	// ══════════════ 爆款拆解 ══════════════

	@Operation(summary = "提交爆款拆解（链接）")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/decompose")
	@SaCheckPermission("ai:creation:add")
	public JsonResult<Object> decompose(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.post("/ai/creation/decompose", body, Object.class, billingHeaders()));
	}

	@Operation(summary = "上传视频拆解（链接解析失败兜底）")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/decompose-upload")
	@SaCheckPermission("ai:creation:add")
	public JsonResult<Object> decomposeUpload(@RequestPart("file") MultipartFile file) throws java.io.IOException {
		return JsonResult.success(aiServiceClient.postMultipart("/ai/creation/decompose-upload", file.getBytes(),
				file.getOriginalFilename(), null, billingHeaders()));
	}

	@Operation(summary = "查询拆解模板详情")
	@GetMapping("/template/{templateId}")
	@SaCheckPermission("ai:creation:template")
	public JsonResult<Object> template(@PathVariable Long templateId) {
		return JsonResult
			.success(aiServiceClient.get("/ai/creation/template/" + templateId, Object.class, billingHeaders()));
	}

	@Operation(summary = "模板库列表")
	@GetMapping("/templates")
	@SaCheckPermission("ai:creation:template")
	public JsonResult<Object> templates(@RequestParam(value = "hook_type", required = false) String hookType) {
		// 同 /models：snake_case 查询参数需显式指定绑定名
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/ai/creation/templates");
		if (hookType != null && !hookType.isEmpty()) {
			builder.queryParam("hook_type", hookType);
		}
		return JsonResult.success(aiServiceClient.get(builder.build().toUriString(), Object.class, billingHeaders()));
	}

	@Operation(summary = "删除拆解模板")
	@Log(service = "AI创作", businessType = BusinessType.DELETE)
	@DeleteMapping("/template/{templateIds}")
	@SaCheckPermission("ai:creation:template")
	public JsonResult<Object> removeTemplates(@PathVariable Long[] templateIds) {
		StringBuilder ids = new StringBuilder();
		for (int i = 0; i < templateIds.length; i++) {
			if (i > 0) {
				ids.append(",");
			}
			ids.append(templateIds[i]);
		}
		aiServiceClient.delete("/ai/creation/template/" + ids);
		return JsonResult.success();
	}

	// ══════════════ 作品项目 ══════════════

	@Operation(summary = "创作域可用模型列表（LLM/IMAGE/VIDEO）")
	@GetMapping("/models")
	@SaCheckPermission("ai:creation:list")
	public JsonResult<Object> models(@RequestParam(value = "model_type", required = false) String modelType) {
		// 前端/Python 侧查询参数名为 snake_case（model_type），Spring 按名称精确绑定，需显式指定
		String path = "/ai/creation/models"
				+ (modelType != null && !modelType.isEmpty() ? "?model_type=" + modelType : "");
		return JsonResult.success(aiServiceClient.get(path, Object.class, billingHeaders()));
	}

	@Operation(summary = "项目列表（透传 Python 全量列表）")
	@GetMapping("/projects")
	@SaCheckPermission("ai:creation:list")
	public JsonResult<Object> projects() {
		return JsonResult.success(aiServiceClient.get("/ai/creation/projects", Object.class, billingHeaders()));
	}

	@Operation(summary = "创建项目（生成脚本+分镜）")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/projects")
	@SaCheckPermission("ai:creation:add")
	public JsonResult<Object> createProject(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.post("/ai/creation/projects", body, Object.class, billingHeaders()));
	}

	@Operation(summary = "项目详情（状态机+分镜）")
	@GetMapping("/projects/{projectId}")
	@SaCheckPermission("ai:creation:query")
	public JsonResult<Object> project(@PathVariable Long projectId) {
		return JsonResult
			.success(aiServiceClient.get("/ai/creation/projects/" + projectId, Object.class, billingHeaders()));
	}

	@Operation(summary = "保存人工编辑后的分镜")
	@Log(service = "AI创作", businessType = BusinessType.UPDATE)
	@PutMapping("/projects/{projectId}/storyboard")
	@SaCheckPermission("ai:creation:edit")
	public JsonResult<Object> saveStoryboard(@PathVariable Long projectId, @RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.put("/ai/creation/projects/" + projectId + "/storyboard", body,
				Object.class, billingHeaders()));
	}

	@Operation(summary = "启动渲染合成（可指定生图/生视频模型）")
	@Log(service = "AI创作", businessType = BusinessType.UPDATE)
	@PostMapping("/projects/{projectId}/render")
	@SaCheckPermission("ai:creation:edit")
	public JsonResult<Object> render(@PathVariable Long projectId,
			@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> payload = body != null ? body : Map.of();
		return JsonResult.success(aiServiceClient.post("/ai/creation/projects/" + projectId + "/render", payload,
				Object.class, billingHeaders()));
	}

	@Operation(summary = "镜级重试")
	@Log(service = "AI创作", businessType = BusinessType.UPDATE)
	@PostMapping("/projects/{projectId}/shots/{shotId}/retry")
	@SaCheckPermission("ai:creation:edit")
	public JsonResult<Object> retryShot(@PathVariable Long projectId, @PathVariable Long shotId,
			@RequestBody Map<String, Object> body) {
		return JsonResult
			.success(aiServiceClient.post("/ai/creation/projects/" + projectId + "/shots/" + shotId + "/retry", body,
					Object.class, billingHeaders()));
	}

	@Operation(summary = "生成发布包")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/projects/{projectId}/publish-package")
	@SaCheckPermission("ai:creation:edit")
	public JsonResult<Object> publishPackage(@PathVariable Long projectId,
			@RequestBody(required = false) Map<String, Object> body) {
		// Python 侧 body 必填，空体兜底为 {}（FastAPI 对空体返回 422）
		Map<String, Object> payload = body != null ? body : Map.of();
		return JsonResult.success(aiServiceClient.post("/ai/creation/projects/" + projectId + "/publish-package",
				payload, Object.class, billingHeaders()));
	}

	@Operation(summary = "一键多变体（克隆项目换钩子 N 版）")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/projects/{projectId}/variants")
	@SaCheckPermission("ai:creation:add")
	public JsonResult<Object> hookVariants(@PathVariable Long projectId,
			@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> payload = body != null ? body : Map.of();
		return JsonResult.success(aiServiceClient.post("/ai/creation/projects/" + projectId + "/variants", payload,
				Object.class, billingHeaders()));
	}

	@Operation(summary = "创作项目分页查询（Java 本地库）")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:creation:list")
	public JsonResult<TableDataInfo<AiCreationProject>> pageQuery(AiCreationProject aiCreationProject) {
		PageUtils.startPage();
		List<AiCreationProject> list = aiCreationProjectService.selectAiCreationProjectList(aiCreationProject);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	@Operation(summary = "批量删除创作项目（Java 本地库）")
	@Log(service = "AI创作", businessType = BusinessType.DELETE)
	@DeleteMapping("/projects/{projectIds}")
	@SaCheckPermission("ai:creation:remove")
	public JsonResult<String> removeProjects(@PathVariable Long[] projectIds) {
		return json(aiCreationProjectService.deleteAiCreationProjectByIds(projectIds));
	}

	// ══════════════ 人设 ══════════════

	@Operation(summary = "人设列表")
	@GetMapping("/personas")
	@SaCheckPermission("ai:creation:persona")
	public JsonResult<Object> personas() {
		return JsonResult.success(aiServiceClient.get("/ai/creation/personas", Object.class, billingHeaders()));
	}

	@Operation(summary = "保存人设")
	@Log(service = "AI创作", businessType = BusinessType.UPDATE)
	@PostMapping("/personas")
	@SaCheckPermission("ai:creation:persona")
	public JsonResult<Object> savePersona(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.post("/ai/creation/personas", body, Object.class, billingHeaders()));
	}

	@Operation(summary = "生成人设定妆照")
	@Log(service = "AI创作", businessType = BusinessType.INSERT)
	@PostMapping("/personas/look")
	@SaCheckPermission("ai:creation:persona")
	public JsonResult<Object> generateLook(@RequestBody Map<String, Object> body) {
		return JsonResult
			.success(aiServiceClient.post("/ai/creation/personas/look", body, Object.class, billingHeaders()));
	}

	@Operation(summary = "一键设为定妆照（提升已有图片）")
	@Log(service = "AI创作", businessType = BusinessType.UPDATE)
	@PostMapping("/personas/look/set")
	@SaCheckPermission("ai:creation:persona")
	public JsonResult<Object> setPersonaLook(@RequestBody Map<String, Object> body) {
		return JsonResult
			.success(aiServiceClient.post("/ai/creation/personas/look/set", body, Object.class, billingHeaders()));
	}

	@Operation(summary = "删除人设")
	@Log(service = "AI创作", businessType = BusinessType.DELETE)
	@DeleteMapping("/personas/{personaId}")
	@SaCheckPermission("ai:creation:persona")
	public JsonResult<Void> removePersona(@PathVariable Long personaId) {
		aiServiceClient.delete("/ai/creation/personas/" + personaId);
		return JsonResult.success();
	}

}
