package com.twelvet.ai.server.controller;

import com.twelvet.ai.api.domain.AiUsageDaily;
import com.twelvet.ai.server.mapper.AiUsageMapper;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.security.utils.SecurityUtils;
import com.twelvet.system.api.domain.SysTenant;
import com.twelvet.system.api.mapper.SysTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量查询（月账单数据源）
 * <p>
 * 数据由 Python 侧聊天完成时落账（ai_usage_daily）；本端点按当前租户上下文只读， 登录即可查本租户用量（数据按 tenant_id 圈定，无跨租户暴露）。
 */
@Tag(description = "AiUsageController", name = "AI用量")
@RestController
@RequestMapping("/ai/usage")
public class AiUsageController extends TWTController {

	private final AiUsageMapper aiUsageMapper;

	private final SysTenantMapper sysTenantMapper;

	public AiUsageController(AiUsageMapper aiUsageMapper, SysTenantMapper sysTenantMapper) {
		this.aiUsageMapper = aiUsageMapper;
		this.sysTenantMapper = sysTenantMapper;
	}

	/**
	 * AI 用量（日明细 + 合计）。 平台超管可传 tenantId/tenantCode 查任意租户（上下文为 default/无 时生效）；
	 * 其他用户强制当前租户上下文，防越权枚举（与用户列表 bindTenantScope 同语义）。
	 * @param days 统计天数（默认 30，上限 365）
	 * @param tenantId 目标租户ID（仅平台超管可指定）
	 * @param tenantCode 目标租户编码（仅平台超管可指定）
	 * @return JsonResult<Map> {days, rows, total:{calls,promptTokens,completionTokens}}
	 */
	@Operation(summary = "AI用量（日明细+合计）")
	@GetMapping("/daily")
	public JsonResult<Map<String, Object>> daily(@RequestParam(defaultValue = "30") int days,
			@RequestParam(required = false) Long tenantId, @RequestParam(required = false) String tenantCode) {
		int window = Math.min(Math.max(days, 1), 365);
		Long ctx = TenantContext.get();
		Long requested = tenantId;
		if (requested == null && tenantCode != null && !tenantCode.isBlank()) {
			SysTenant tenant = sysTenantMapper.selectByCode(tenantCode);
			requested = tenant == null ? -1L : tenant.getId();
		}
		Long effectiveTenantId;
		if (requested != null && (ctx == null || ctx == 1L)
				&& SecurityUtils.isAdmin(SecurityUtils.getLoginUser().getUserId())) {
			effectiveTenantId = requested;
		}
		else {
			effectiveTenantId = ctx;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("days", window);
		if (effectiveTenantId == null) {
			// 平台/兼容视角且未指定租户：不返回聚合数据
			data.put("rows", List.of());
			data.put("total", Map.of("calls", 0, "promptTokens", 0, "completionTokens", 0));
			return JsonResult.success(data);
		}
		List<AiUsageDaily> rows = aiUsageMapper.selectDaily(effectiveTenantId, LocalDate.now().minusDays(window - 1L));
		long calls = 0;
		long promptTokens = 0;
		long completionTokens = 0;
		for (AiUsageDaily row : rows) {
			calls += row.getCalls() == null ? 0 : row.getCalls();
			promptTokens += row.getPromptTokens() == null ? 0 : row.getPromptTokens();
			completionTokens += row.getCompletionTokens() == null ? 0 : row.getCompletionTokens();
		}
		data.put("rows", rows);
		data.put("total", Map.of("calls", calls, "promptTokens", promptTokens, "completionTokens", completionTokens));
		return JsonResult.success(data);
	}

}
