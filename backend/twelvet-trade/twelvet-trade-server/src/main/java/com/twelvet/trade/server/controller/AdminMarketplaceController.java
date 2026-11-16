package com.twelvet.trade.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.trade.server.service.IAdminMarketplaceService;
import com.twelvet.trade.server.service.ICsDisputeService;
import com.twelvet.trade.server.service.ITradeReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 运营管理后台控制器 职责: HTTP参数绑定、调用Service、包装响应 业务逻辑全部委托给AdminMarketplaceService
 *
 * @author twelvet
 */
@Tag(description = "运营后台", name = "AdminMarketplaceController")
@RestController
@RequestMapping("/admin/marketplace")
public class AdminMarketplaceController extends TWTController {

	private final IAdminMarketplaceService adminService;

	private final ICsDisputeService disputeService;

	private final ITradeReviewService reviewService;

	public AdminMarketplaceController(IAdminMarketplaceService adminService, ICsDisputeService disputeService,
			ITradeReviewService reviewService) {
		this.adminService = adminService;
		this.disputeService = disputeService;
		this.reviewService = reviewService;
	}

	/**
	 * 仪表盘概览
	 */
	@Operation(summary = "仪表盘概览")
	@SaCheckPermission("marketplace:admin:dashboard")
	@GetMapping("/dashboard/overview")
	public JsonResult<Map<String, Object>> overview() {
		return JsonResult.success(adminService.selectDashboardOverview());
	}

	/**
	 * 自营运营核心指标（发布量/成交量/履约率/复购率/纠纷率，含窗口趋势）
	 * @param days 统计窗口天数，仅支持 7 / 30 / 90，其它值回落 30
	 */
	@Operation(summary = "自营运营核心指标")
	@SaCheckPermission("marketplace:admin:dashboard")
	@GetMapping("/dashboard/operation-metrics")
	public JsonResult<Map<String, Object>> operationMetrics(@RequestParam(defaultValue = "30") String days) {
		return JsonResult.success(adminService.selectOperationMetrics(parseDays(days)));
	}

	/**
	 * 窗口天数解析：非法值一律回落 30，避免 @RequestParam int 转换失败把 500 暴露给调用方
	 */
	private int parseDays(String raw) {
		try {
			return Integer.parseInt(String.valueOf(raw).trim());
		}
		catch (NumberFormatException ex) {
			return 30;
		}
	}

	/**
	 * 商品审核列表
	 */
	@Operation(summary = "商品审核列表")
	@SaCheckPermission("marketplace:admin:productAudit")
	@GetMapping("/product-audit/page")
	public JsonResult<TableDataInfo<Map<String, Object>>> productAuditPage(
			@RequestParam(required = false) String status, @RequestParam(required = false) String aiRisk) {
		return JsonResult.success(adminService.selectProductAuditList(status, aiRisk));
	}

	/**
	 * 商品审核通过
	 */
	@Operation(summary = "商品审核通过")
	@SaCheckPermission("marketplace:admin:productAudit")
	@PostMapping("/product-audit/pass")
	public JsonResult<String> productPass(@RequestParam Long productId) {
		return JsonResult.success(adminService.approveProduct(productId));
	}

	/**
	 * 商品审核驳回
	 */
	@Operation(summary = "商品审核驳回")
	@SaCheckPermission("marketplace:admin:productAudit")
	@PostMapping("/product-audit/reject")
	public JsonResult<String> productReject(@RequestBody Map<String, Object> body) {
		Long productId = Long.valueOf(String.valueOf(body.get("productId")));
		String reason = String.valueOf(body.getOrDefault("reason", "信息不全"));
		return JsonResult.success(adminService.rejectProduct(productId, reason));
	}

	/**
	 * 举报列表
	 */
	@Operation(summary = "举报列表")
	@SaCheckPermission("marketplace:admin:report")
	@GetMapping("/report/page")
	public JsonResult<TableDataInfo<Map<String, Object>>> reportPage(
			@RequestParam(defaultValue = "pending") String status) {
		return JsonResult.success(adminService.selectReportList(status));
	}

	/**
	 * 处理举报
	 */
	@Operation(summary = "处理举报")
	@Log(service = "举报管理", businessType = BusinessType.UPDATE)
	@SaCheckPermission("marketplace:admin:report")
	@PostMapping("/report/handle")
	public JsonResult<String> handleReport(@RequestBody Map<String, Object> body) {
		Long reportId = Long.valueOf(String.valueOf(body.get("reportId")));
		String action = str(body.get("action"), "dismiss");
		String note = str(body.get("note"), "");
		Long handlerId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(adminService.handleReport(reportId, action, note, handlerId));
	}

	/**
	 * 取字符串参数并归一 null
	 * <p>
	 * 不能用 {@code String.valueOf(body.getOrDefault(key, def))}：键存在但值为 null 时 getOrDefault
	 * 返回 null（不是缺省值），String.valueOf(null) 得到字面量 "null" —— action 变成 "null"
	 * 会被动作白名单拒成「处理动作不合法」，note 变成 "null" 会落库并 拼进站内通知正文。C 端 ReportController 的 str()
	 * 是同一条规则。
	 * </p>
	 */
	private static String str(Object v, String defaultValue) {
		if (v == null) {
			return defaultValue;
		}
		String s = String.valueOf(v);
		return s.isEmpty() || "null".equals(s) ? defaultValue : s;
	}

	/**
	 * 用户列表
	 */
	@Operation(summary = "用户列表")
	@SaCheckPermission("marketplace:admin:user")
	@GetMapping("/user/page")
	public JsonResult<TableDataInfo<Map<String, Object>>> userPage(@RequestParam(required = false) String keyword) {
		return JsonResult.success(adminService.selectUserList(keyword));
	}

	/**
	 * 封禁/解封用户
	 */
	@Operation(summary = "封禁/解封用户")
	@SaCheckPermission("marketplace:admin:user")
	@PostMapping("/user/ban")
	public JsonResult<String> banUser(@RequestBody Map<String, Object> body) {
		Long userId = Long.valueOf(String.valueOf(body.get("userId")));
		Integer status = Integer.valueOf(String.valueOf(body.getOrDefault("status", 1)));
		return JsonResult.success(adminService.updateUserStatus(userId, status));
	}

	/**
	 * 财务概览
	 */
	@Operation(summary = "财务概览")
	@SaCheckPermission("marketplace:admin:finance")
	@GetMapping("/finance/overview")
	public JsonResult<Map<String, Object>> finance() {
		return JsonResult.success(adminService.selectFinanceOverview());
	}

	/**
	 * 评价管理列表(平台视角)
	 */
	@Operation(summary = "评价管理列表")
	@SaCheckPermission("marketplace:admin:review")
	@GetMapping("/review/page")
	public JsonResult<TableDataInfo<Map<String, Object>>> reviewPage(@RequestParam(required = false) String status,
			@RequestParam(required = false) String role, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
		return JsonResult.success(reviewService.adminPage(status, role, keyword, pageNum, pageSize));
	}

	/**
	 * 隐藏评价
	 */
	@Operation(summary = "隐藏评价")
	@SaCheckPermission("marketplace:admin:review")
	@PostMapping("/review/hide")
	public JsonResult<String> reviewHide(@RequestBody Map<String, Object> body) {
		Long reviewId = Long.valueOf(String.valueOf(body.get("reviewId")));
		return JsonResult.success(reviewService.updateStatus(reviewId, "hidden"));
	}

	/**
	 * 恢复评价
	 */
	@Operation(summary = "恢复评价")
	@SaCheckPermission("marketplace:admin:review")
	@PostMapping("/review/show")
	public JsonResult<String> reviewShow(@RequestBody Map<String, Object> body) {
		Long reviewId = Long.valueOf(String.valueOf(body.get("reviewId")));
		return JsonResult.success(reviewService.updateStatus(reviewId, "normal"));
	}

	/**
	 * 纠纷工单列表
	 */
	@Operation(summary = "纠纷工单列表")
	@SaCheckPermission("marketplace:cs:disputes")
	@GetMapping("/dispute/page")
	public JsonResult<TableDataInfo<Map<String, Object>>> disputePage(
			@RequestParam(defaultValue = "pending") String status) {
		return JsonResult.success(disputeService.selectDisputeList(status));
	}

	/**
	 * 纠纷统计
	 */
	@Operation(summary = "纠纷统计")
	@SaCheckPermission("marketplace:cs:disputes")
	@GetMapping("/dispute/stats")
	public JsonResult<Map<String, Object>> disputeStats() {
		return JsonResult.success(disputeService.selectDisputeStats());
	}

	/**
	 * 纠纷详情
	 */
	@Operation(summary = "纠纷详情")
	@SaCheckPermission("marketplace:cs:disputes")
	@GetMapping("/dispute/{disputeId}")
	public JsonResult<Map<String, Object>> disputeDetail(@PathVariable Long disputeId) {
		return JsonResult.success(disputeService.selectDisputeDetail(disputeId));
	}

	/**
	 * 客服领取工单
	 */
	@Operation(summary = "客服领取工单")
	@SaCheckPermission("marketplace:cs:disputes")
	@PostMapping("/dispute/claim/{disputeId}")
	public JsonResult<String> disputeClaim(@PathVariable Long disputeId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(disputeService.claimDispute(disputeId, userId));
	}

	/**
	 * 提交裁决
	 */
	@Operation(summary = "提交裁决")
	@SaCheckPermission("marketplace:cs:disputes")
	@PostMapping("/dispute/{disputeId}/judge")
	public JsonResult<String> disputeJudge(@PathVariable Long disputeId, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String decision = String.valueOf(body.get("decision"));
		String reason = String.valueOf(body.get("reason"));
		Object amountObj = body.get("amount");
		java.math.BigDecimal amount = amountObj == null ? null : new java.math.BigDecimal(String.valueOf(amountObj));
		return JsonResult.success(disputeService.judgeDispute(disputeId, decision, amount, reason, userId));
	}

}
