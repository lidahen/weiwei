package com.twelvet.runner.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.payment.api.dto.PayRequestDTO;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import com.twelvet.runner.server.service.IRunnerReviewService;
import com.twelvet.runner.server.service.IRunnerTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 互助任务 - 下单人视角
 *
 * @author twelvet
 */
@Tag(description = "互助任务（下单人）", name = "RunnerTaskController")
@RestController
@RequestMapping("/runner/task")
public class RunnerTaskController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(RunnerTaskController.class);

	private final IRunnerTaskService taskService;

	private final IRunnerReviewService reviewService;

	private final IPayOrderService payOrderService;

	private final RunnerTaskMapper taskMapper;

	@Value("${twelvet.name:邻里二手}")
	private String appName;

	public RunnerTaskController(IRunnerTaskService taskService, IRunnerReviewService reviewService,
			IPayOrderService payOrderService, RunnerTaskMapper taskMapper) {
		this.taskService = taskService;
		this.reviewService = reviewService;
		this.payOrderService = payOrderService;
		this.taskMapper = taskMapper;
	}

	@SaCheckLogin
	@Operation(summary = "发布任务（免费/付费）")
	@PostMapping("/publish")
	public JsonResult<Map<String, Object>> publish(@RequestBody RunnerTask task) {
		Long publisherId = StpUtil.getLoginIdAsLong();
		Long taskId = taskService.publish(task, publisherId);

		Map<String, Object> resp = new java.util.HashMap<>();
		resp.put("taskId", taskId);
		resp.put("taskNo", task.getTaskNo());
		resp.put("status", task.getStatus());
		resp.put("isPaid", task.getIsPaid());

		// 付费任务：尝试一站式发起支付（与任务落库解耦：失败不影响任务创建，可稍后在"我发布的"重新支付）
		if (task.getIsPaid() != null && task.getIsPaid() == RunnerTask.IS_PAID_YES) {
			resp.put("payStatus", RunnerTask.PAY_STATUS_UNPAID);
			try {
				Map<String, Object> payResp = createPayOrderForTask(task);
				// 绑定支付单到 task（供 refund / 事件监听使用）
				bindPayInfoQuietly(taskId, payResp);
				resp.put("pay", payResp);
			}
			catch (Exception e) {
				log.error("付费任务支付发起失败（任务已创建，可稍后重新支付）: taskId={}", taskId, e);
				Map<String, Object> payError = new java.util.HashMap<>();
				payError.put("error", "支付发起失败：" + String.valueOf(e.getMessage()));
				resp.put("pay", payError);
				resp.put("payMessage", "支付发起失败，请稍后在'我发布的'里重新支付");
			}
		}
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "重新支付（我发布的未支付付费任务，幂等）")
	@PostMapping("/{id}/pay")
	public JsonResult<Map<String, Object>> repay(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		RunnerTask task = taskService.detail(id, userId);
		if (task == null) {
			return JsonResult.error("任务不存在");
		}
		if (!task.getPublisherId().equals(userId)) {
			return JsonResult.error("仅任务发布者可支付");
		}
		if (task.getIsPaid() == null || task.getIsPaid() != RunnerTask.IS_PAID_YES) {
			return JsonResult.error("免费任务无需支付");
		}

		Map<String, Object> resp = new java.util.HashMap<>();
		resp.put("taskId", id);
		resp.put("taskNo", task.getTaskNo());
		// dev 模拟支付模式标记（billing.pay.mock）：前端据此走 /payment/mock-pay 闭环
		resp.put("mock", payOrderService.isMockEnabled());

		// 幂等 1：任务已标记 paid → 直接返回
		if (RunnerTask.PAY_STATUS_PAID.equals(task.getPayStatus())) {
			resp.put("payStatus", RunnerTask.PAY_STATUS_PAID);
			resp.put("alreadyPaid", true);
			return JsonResult.success(resp);
		}

		// 幂等 2：已绑定支付单 → 查询状态；已支付则同步任务状态；未支付/网关异常则复用原单，避免重复下单
		String existPayNo = task.getPayOutTradeNo();
		if (existPayNo != null && !existPayNo.isBlank()) {
			Map<String, Object> existing = null;
			boolean queryFailed = false;
			try {
				existing = payOrderService.queryPayStatus(existPayNo);
			}
			catch (Exception e) {
				queryFailed = true;
				log.warn("查询互助任务原支付单失败，保守复用原单: taskId={}, payNo={}", id, existPayNo, e);
			}
			if (existing != null && Boolean.TRUE.equals(existing.get("paid"))) {
				// 守卫：已取消任务不被标回 paid（final review 发现 #3）——响应如实反映标记结果
				int rows = taskMapper.markPayStatusByTaskId(id, RunnerTask.PAY_STATUS_PAID);
				if (rows > 0) {
					resp.put("payStatus", RunnerTask.PAY_STATUS_PAID);
					resp.put("alreadyPaid", true);
					resp.put("pay", existing);
				}
				else {
					RunnerTask fresh = taskMapper.selectById(id, null);
					resp.put("payStatus", fresh != null ? fresh.getPayStatus() : RunnerTask.PAY_STATUS_UNPAID);
					resp.put("alreadyPaid", false);
					resp.put("payStatusStale", true);
				}
				return JsonResult.success(resp);
			}
			if (existing != null || queryFailed) {
				// 原单未支付（或网关异常无法确认状态）→ 复用/返回原单，不重复下单
				Map<String, Object> payInfo = existing;
				if (payInfo == null) {
					payInfo = new java.util.HashMap<>();
					payInfo.put("payNo", existPayNo);
					payInfo.put("payOrderId", task.getPayOrderId());
					payInfo.put("channel", "alipay");
					payInfo.put("status", "unknown");
					payInfo.put("error", "支付网关暂时不可用，无法确认原支付单状态");
				}
				resp.put("payStatus", task.getPayStatus());
				resp.put("reused", true);
				resp.put("pay", payInfo);
				resp.put("payMessage", "已有待支付订单，请完成支付；如未生成支付码，请稍后重试");
				return JsonResult.success(resp);
			}
			// existing == null 且查询正常 → 原支付单不存在（已被清理）→ 走下方重新创建
		}

		// 无可用支付单 → 重新创建（失败降级返回，不抛 500）
		resp.put("payStatus", RunnerTask.PAY_STATUS_UNPAID);
		try {
			Map<String, Object> payResp = createPayOrderForTask(task);
			bindPayInfoQuietly(id, payResp);
			resp.put("pay", payResp);
		}
		catch (Exception e) {
			log.error("互助任务重新支付发起失败: taskId={}", id, e);
			Map<String, Object> payError = new java.util.HashMap<>();
			payError.put("error", "支付发起失败：" + String.valueOf(e.getMessage()));
			resp.put("pay", payError);
			resp.put("payMessage", "支付发起失败，请稍后在'我发布的'里重新支付");
		}
		return JsonResult.success(resp);
	}

	/**
	 * 创建任务支付单（publish 与 repay 共用）
	 */
	private Map<String, Object> createPayOrderForTask(RunnerTask task) {
		PayRequestDTO payReq = new PayRequestDTO();
		payReq.setOrderId(task.getId());
		payReq.setOrderNo(task.getTaskNo());
		payReq.setChannel("alipay");
		payReq.setAmount(task.getPrice());
		payReq.setSubject(appName + " - 互助任务 " + task.getTaskNo());
		payReq.setReturnUrl("/runner/task/my-list");
		payReq.setBizType("runner");
		return payOrderService.createPayOrder(payReq);
	}

	/**
	 * 绑定支付单信息到任务（payNo 缺失时跳过，不阻断主流程）
	 */
	private void bindPayInfoQuietly(Long taskId, Map<String, Object> payResp) {
		Object payNoObj = payResp.get("payNo");
		Object payOrderIdObj = payResp.get("orderId");
		if (payNoObj != null) {
			Long payOrderId = payOrderIdObj instanceof Number n ? n.longValue() : null;
			taskMapper.bindPayInfo(taskId, payOrderId, String.valueOf(payNoObj));
		}
	}

	@SaCheckLogin
	@Operation(summary = "任务详情")
	@GetMapping("/{id}")
	public JsonResult<RunnerTask> detail(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(taskService.detail(id, userId));
	}

	@SaCheckLogin
	@Operation(summary = "取消任务")
	@PostMapping("/{id}/cancel")
	public JsonResult<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String reason = body.get("reason") == null ? "用户取消" : String.valueOf(body.get("reason"));
		Map<String, Object> resp = taskService.cancel(id, userId, reason);
		// 付费任务：调支付服务退款
		if (Boolean.TRUE.equals(resp.get("needRefund"))) {
			Object outTradeNoObj = resp.get("payOutTradeNo");
			Object amountObj = resp.get("refundAmount");
			if (outTradeNoObj != null) {
				try {
					BigDecimal amount = amountObj instanceof Number n ? new BigDecimal(n.toString())
							: new BigDecimal(String.valueOf(amountObj));
					boolean ok = payOrderService.refund(String.valueOf(outTradeNoObj), amount, reason);
					resp.put("refundResult", ok ? "success" : "failed");
					if (ok) {
						taskService.markRefunded(id);
					}
				}
				catch (Exception e) {
					log.error("互助任务取消后退款失败: taskId={}, outTradeNo={}", id, outTradeNoObj, e);
					resp.put("refundResult", "error");
				}
			}
		}
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "确认完成")
	@PostMapping("/{id}/confirm")
	public JsonResult<String> confirm(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		taskService.confirmComplete(id, userId);
		return JsonResult.success("OK");
	}

	@SaCheckLogin
	@Operation(summary = "申请退款")
	@PostMapping("/{id}/refund")
	public JsonResult<Map<String, Object>> refund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		String reason = body.get("reason") == null ? "申请退款" : String.valueOf(body.get("reason"));
		Map<String, Object> resp = taskService.requestRefund(id, userId, reason);
		// 调支付服务退款（outTradeNo + amount）
		Object outTradeNoObj = resp.get("payOutTradeNo");
		Object amountObj = resp.get("refundAmount");
		if (outTradeNoObj != null) {
			try {
				BigDecimal amount = amountObj instanceof Number n ? new BigDecimal(n.toString())
						: new BigDecimal(String.valueOf(amountObj));
				boolean ok = payOrderService.refund(String.valueOf(outTradeNoObj), amount, reason);
				resp.put("refundResult", ok ? "success" : "failed");
			}
			catch (Exception e) {
				log.error("互助任务退款失败: taskId={}, outTradeNo={}", id, outTradeNoObj, e);
				resp.put("refundResult", "error");
			}
		}
		return JsonResult.success(resp);
	}

	@SaCheckLogin
	@Operation(summary = "评价任务（发布者对已确认完成任务，一任务一评）")
	@PostMapping("/{id}/review")
	public JsonResult<Map<String, Object>> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		Long userId = StpUtil.getLoginIdAsLong();
		Integer rating = body.get("rating") instanceof Number n ? n.intValue() : null;
		String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
		String tags = body.get("tags") == null ? null : String.valueOf(body.get("tags"));
		return JsonResult.success(reviewService.submitReview(id, userId, rating, content, tags));
	}

	@SaCheckLogin
	@Operation(summary = "查询任务评价（未评返回空）")
	@GetMapping("/{id}/review")
	public JsonResult<Map<String, Object>> getReview(@PathVariable Long id) {
		return JsonResult.success(reviewService.reviewByTask(id));
	}

	@SaCheckLogin
	@Operation(summary = "我发布的任务")
	@GetMapping("/my-list")
	public JsonResult<List<RunnerTask>> myList(@RequestParam(required = false) String status) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(taskService.listMyPublished(userId, status));
	}

	@SaCheckLogin
	@Operation(summary = "平台管理：分页查询")
	@GetMapping("/admin/page")
	public TableDataInfo adminPage(@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "20") Long pageSize, @RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword) {
		return taskService.adminPage(pageNum, pageSize, status, keyword);
	}

}
