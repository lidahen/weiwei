package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.api.domain.DailyReport;
import com.twelvet.ai.server.service.IReportService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 社区日报控制器
 * <p>
 * 设计：docs/AI社区日报设计.md §5 列表/详情登录即可（全员触达），手动生成需 ai:report:generate 权限； 数据按租户上下文圈定，无跨租户暴露。
 */
@Tag(description = "AiReportController", name = "AI社区日报")
@RestController
@RequestMapping("/ai/report")
public class AiReportController extends TWTController {

	private final IReportService reportService;

	public AiReportController(IReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * 当前租户日报分页（日期倒序）
	 * @return JsonResult<TableDataInfo<DailyReport>>
	 */
	@Operation(summary = "日报分页列表")
	@GetMapping("/list")
	@SaCheckLogin
	public JsonResult<TableDataInfo<DailyReport>> list() {
		Long tenantId = TenantContext.get();
		if (tenantId == null) {
			// 平台/兼容视角：无租户上下文不返回数据
			return JsonResult.success(TableDataInfo.page(List.of(), 0));
		}
		PageUtils.startPage();
		List<DailyReport> list = reportService.selectPage(tenantId);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	/**
	 * 日报详情（登录 + 租户校验）
	 * @param id 日报ID
	 * @return JsonResult<DailyReport>
	 */
	@Operation(summary = "日报详情")
	@GetMapping("/{id}")
	@SaCheckLogin
	public JsonResult<DailyReport> detail(@PathVariable Long id) {
		Long tenantId = TenantContext.get();
		DailyReport report = tenantId == null ? null : reportService.selectByIdAndTenant(id, tenantId);
		if (report == null) {
			throw new TWTException("日报不存在或无权查看");
		}
		return JsonResult.success(report);
	}

	/**
	 * 日报语音播报（晨间广播）：按需合成 mp3 并本地缓存，租户校验同详情。
	 * @param id 日报ID
	 * @return audio/mpeg 字节流（TTS 未配置时全局异常处理器转 JSON，前端按响应类型区分）
	 */
	@Operation(summary = "日报语音播报")
	@GetMapping("/{id}/audio")
	@SaCheckLogin
	public ResponseEntity<byte[]> audio(@PathVariable Long id) {
		Long tenantId = TenantContext.get();
		byte[] audio = reportService.loadReportAudio(id, tenantId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio);
	}

	/**
	 * 立即生成/覆盖当前租户当日日报（幂等：数据日期默认昨天，重复调用覆盖更新），并 IM 推送
	 * @return JsonResult<DailyReport>
	 */
	@Operation(summary = "立即生成今日日报")
	@Log(service = "AI社区日报", businessType = BusinessType.OTHER)
	@PostMapping("/generate")
	@SaCheckPermission("ai:report:generate")
	public JsonResult<DailyReport> generate() {
		Long tenantId = TenantContext.get();
		if (tenantId == null) {
			throw new TWTException("当前无租户上下文，无法生成日报");
		}
		return JsonResult.success(reportService.generate(tenantId, true));
	}

}
