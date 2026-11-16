package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.product.server.service.IReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 举报 C 端接口
 * <p>
 * 商品 / 动态 / 用户三类对象统一入口。动态举报的旧路径 {@code POST /feed/report} 保留（前端已发版）， 内部委托同一个
 * {@link IReportService#submit}。
 * </p>
 *
 * @author twelvet
 */
@Tag(description = "举报 C 端接口", name = "ReportController")
@RestController
@RequestMapping("/report")
@Component("productReportController")
public class ReportController extends TWTController {

	private final IReportService reportService;

	public ReportController(IReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * 提交举报（商品 / 动态 / 用户）
	 * <p>
	 * 举报人从会话取，不接受前端传入 —— 否则可伪造成他人举报。
	 * </p>
	 * @param body {targetType, targetId, reasonType, reasonDetail?, evidenceImages?}
	 * @return 提示文案
	 */
	@Operation(summary = "提交举报")
	@PostMapping("/submit")
	public JsonResult<String> submit(@RequestBody Map<String, Object> body) {
		Long reporterId = StpUtil.getLoginIdAsLong();
		String targetType = str(body.get("targetType"));
		Long targetId = toLong(body.get("targetId"));
		String reasonType = str(body.get("reasonType"));
		String reasonDetail = str(body.get("reasonDetail"));
		@SuppressWarnings("unchecked")
		List<String> evidenceImages = (List<String>) body.get("evidenceImages");

		reportService.submit(targetType, targetId, reporterId, reasonType, reasonDetail, evidenceImages);
		return JsonResult.success("举报已提交，我们会尽快处理");
	}

	/** null 与字面量 "null"/"" 一律归一为 null，避免 String.valueOf(null) 产出 "null" 字符串 */
	private static String str(Object v) {
		if (v == null) {
			return null;
		}
		String s = String.valueOf(v);
		return s.isEmpty() || "null".equals(s) ? null : s;
	}

	private static Long toLong(Object v) {
		if (v == null) {
			return null;
		}
		try {
			return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v));
		}
		catch (NumberFormatException e) {
			throw new TWTException("举报对象ID不合法");
		}
	}

}
