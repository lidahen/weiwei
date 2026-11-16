package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AI知识库管理控制器
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务
 */
@Tag(description = "AI知识库管理接口", name = "AI知识库管理")
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiKnowledgeController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 分页查询知识库列表
	 * @param query 查询条件（knowledgeName/pageNum/pageSize/current 等）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "分页查询知识库列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:knowledge:list")
	public JsonResult<Object> pageQuery(@RequestParam MultiValueMap<String, String> query) {
		String url = UriComponentsBuilder.fromPath("/ai/knowledge/pageQuery").queryParams(query).toUriString();
		return JsonResult.success(aiServiceClient.get(url, Object.class));
	}

	/**
	 * 查询知识库下拉列表（文档/聊天页面使用）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "查询知识库下拉列表")
	@GetMapping("/list")
	@SaCheckPermission("ai:knowledge:list")
	public JsonResult<Object> list() {
		return JsonResult.success(aiServiceClient.get("/ai/knowledge/list", Object.class));
	}

	/**
	 * 根据知识库ID获取详情
	 * @param knowledgeId 知识库ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "根据知识库ID获取详情")
	@GetMapping("/{knowledgeId}")
	@SaCheckPermission("ai:knowledge:query")
	public JsonResult<Object> getInfo(@PathVariable Long knowledgeId) {
		return JsonResult.success(aiServiceClient.get("/ai/knowledge/" + knowledgeId, Object.class));
	}

	/**
	 * 新增知识库
	 * @param body 知识库信息（camelCase，透传 Python）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "新增知识库")
	@Log(service = "AI知识库管理", businessType = BusinessType.INSERT)
	@PostMapping
	@SaCheckPermission("ai:knowledge:add")
	public JsonResult<Object> add(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.post("/ai/knowledge", body, Object.class));
	}

	/**
	 * 修改知识库
	 * @param body 知识库信息（camelCase，透传 Python）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "修改知识库")
	@Log(service = "AI知识库管理", businessType = BusinessType.UPDATE)
	@PutMapping
	@SaCheckPermission("ai:knowledge:update")
	public JsonResult<Object> edit(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.put("/ai/knowledge", body, Object.class));
	}

	/**
	 * 删除知识库（级联删除文档、切片与向量索引）
	 * @param knowledgeIds 知识库ID数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除知识库")
	@Log(service = "AI知识库管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{knowledgeIds}")
	@SaCheckPermission("ai:knowledge:remove")
	public JsonResult<String> remove(@PathVariable Long[] knowledgeIds) {
		aiServiceClient.delete("/ai/knowledge/" + StringUtils.arrayToCommaDelimitedString(knowledgeIds));
		return JsonResult.success();
	}

	/**
	 * 导出知识库列表（CSV 透传）
	 * @param body 导出筛选条件（knowledgeName 等）
	 * @param response HTTP 响应
	 */
	@Operation(summary = "导出知识库列表(CSV)")
	@PostMapping("/export")
	@SaCheckPermission("ai:knowledge:list")
	public void export(@RequestBody(required = false) Map<String, Object> body, HttpServletResponse response)
			throws IOException {
		String csv = aiServiceClient.post("/ai/knowledge/export", body, String.class);
		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=ai_knowledge.csv");
		response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
	}

}
