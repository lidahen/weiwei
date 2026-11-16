package com.twelvet.runner.server.task;

import com.twelvet.runner.server.service.IRunnerTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 互助定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；事务由内部 service 自管。
 * </p>
 *
 * @author twelvet
 */
@Component("runnerTask")
public class RunnerQuartzTask {

	private static final Logger log = LoggerFactory.getLogger(RunnerQuartzTask.class);

	private final IRunnerTaskService taskService;

	public RunnerQuartzTask(IRunnerTaskService taskService) {
		this.taskService = taskService;
	}

	/**
	 * 每 5 分钟：扫超时未接单任务 → 取消 + 自动退款
	 * <p>
	 * 取消成功即调 IPayOrderService.refund（2026-11-03 批次1 落地）；退款失败的滞留任务由
	 * {@link #reconcileStalePaidRefunds()} 兜底重试。
	 * </p>
	 */
	public void scanTimeoutPending() {
		int count = taskService.scanTimeoutPending();
		log.info("[Quartz] RunnerTaskTimeoutJob 扫描完成：取消 {} 个超时任务", count);
	}

	/**
	 * 每 30 分钟：退款对账——cancelled 但 pay_status=paid 的滞留任务兜底重试退款
	 */
	public void reconcileStalePaidRefunds() {
		int count = taskService.reconcileStalePaidRefunds();
		log.info("[Quartz] RunnerRefundReconcileJob 扫描完成：补偿退款 {} 个滞留任务", count);
	}

	/**
	 * 每小时：扫 pending_confirm 超 3 天 → 自动确认
	 */
	public void scanAutoConfirm() {
		int count = taskService.scanAutoConfirm();
		log.info("[Quartz] RunnerAutoConfirmJob 扫描完成：自动确认 {} 个任务", count);
	}

}
