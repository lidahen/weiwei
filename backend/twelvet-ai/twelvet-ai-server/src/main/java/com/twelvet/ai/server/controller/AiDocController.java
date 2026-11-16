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
 * AI知识库文档管理控制器
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务
 */
@Tag(description = "AI知识库文档管理接口", name = "AI知识库文档管理")
@RestController
@RequestMapping("/ai/doc")
public class AiDocController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiDocController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 分页查询文档列表
	 * @param query 查询条件（knowledgeId/docName/pageNum/pageSize/current 等）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "分页查询文档列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:doc:list")
	public JsonResult<Object> pageQuery(@RequestParam MultiValueMap<String, String> query) {
		String url = UriComponentsBuilder.fromPath("/ai/doc/pageQuery").queryParams(query).toUriString();
		return JsonResult.success(aiServiceClient.get(url, Object.class));
	}

	/**
	 * 根据文档ID获取详情
	 * @param docId 文档ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "根据文档ID获取详情")
	@GetMapping("/{docId}")
	@SaCheckPermission("ai:doc:query")
	public JsonResult<Object> getInfo(@PathVariable Long docId) {
		return JsonResult.success(aiServiceClient.get("/ai/doc/" + docId, Object.class));
	}

	/**
	 * 新增文档（UPLOAD 时 Python 从 fileUrl 下载解析并自动切片）
	 * @param body 文档信息（camelCase，透传 Python）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "新增文档")
	@Log(service = "AI知识库文档管理", businessType = BusinessType.INSERT)
	@PostMapping
	@SaCheckPermission("ai:doc:add")
	public JsonResult<Object> add(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.post("/ai/doc", body, Object.class));
	}

	/**
	 * 修改文档
	 * @param body 文档信息（camelCase，透传 Python）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "修改文档")
	@Log(service = "AI知识库文档管理", businessType = BusinessType.UPDATE)
	@PutMapping
	@SaCheckPermission("ai:doc:update")
	public JsonResult<Object> edit(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.put("/ai/doc", body, Object.class));
	}

	/**
	 * 删除文档（级联删除切片并重建知识库索引）
	 * @param docIds 文档ID数组
	 * @return JsonResult<String>
	 */
	@Operation(summary = "删除文档")
	@Log(service = "AI知识库文档管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{docIds}")
	@SaCheckPermission("ai:doc:remove")
	public JsonResult<String> remove(@PathVariable Long[] docIds) {
		aiServiceClient.delete("/ai/doc/" + StringUtils.arrayToCommaDelimitedString(docIds));
		return JsonResult.success();
	}

	/**
	 * 导出文档列表（CSV 透传）
	 * @param body 导出筛选条件（knowledgeId 等）
	 * @param response HTTP 响应
	 */
	@Operation(summary = "导出文档列表(CSV)")
	@PostMapping("/export")
	@SaCheckPermission("ai:doc:list")
	public void export(@RequestBody(required = false) Map<String, Object> body, HttpServletResponse response)
			throws IOException {
		String csv = aiServiceClient.post("/ai/doc/export", body, String.class);
		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=ai_doc.csv");
		response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
	}

}
