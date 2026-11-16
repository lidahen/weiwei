package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * AI文档切片管理控制器
 * <p>
 * 权限校验保留在 Java 层，业务逻辑代理到 Python FastAPI 服务
 */
@Tag(description = "AI文档切片管理接口", name = "AI文档切片管理")
@RestController
@RequestMapping("/ai/slice")
public class AiSliceController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiSliceController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 分页查询切片列表
	 * @param query 查询条件（docId/pageNum/pageSize/current 等）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "分页查询切片列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:doc:list")
	public JsonResult<Object> pageQuery(@RequestParam MultiValueMap<String, String> query) {
		String url = UriComponentsBuilder.fromPath("/ai/slice/pageQuery").queryParams(query).toUriString();
		return JsonResult.success(aiServiceClient.get(url, Object.class));
	}

	/**
	 * 根据切片ID获取详情
	 * @param sliceId 切片ID
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "根据切片ID获取详情")
	@GetMapping("/{sliceId}")
	@SaCheckPermission("ai:doc:list")
	public JsonResult<Object> getInfo(@PathVariable Long sliceId) {
		return JsonResult.success(aiServiceClient.get("/ai/slice/" + sliceId, Object.class));
	}

	/**
	 * 修改切片（内容变更后自动重新向量化）
	 * @param body 切片信息（camelCase，透传 Python）
	 * @return JsonResult<Map>
	 */
	@Operation(summary = "修改切片")
	@Log(service = "AI文档切片管理", businessType = BusinessType.UPDATE)
	@PutMapping
	@SaCheckPermission("ai:doc:list")
	public JsonResult<Object> edit(@RequestBody Map<String, Object> body) {
		return JsonResult.success(aiServiceClient.put("/ai/slice", body, Object.class));
	}

}
