package com.twelvet.runner.server.listener;

import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件监听器（runner 模块）
 * <p>
 * 监听 payment 发布的 PaySuccessEvent：
 * <ol>
 * <li>按 outTradeNo 反查 taskId（twelve_runner_task.pay_out_trade_no）</li>
 * <li>标记 pay_status='paid'，允许接单人接单</li>
 * </ol>
 * </p>
 *
 * @author twelvet
 */
@Component
public class RunnerPaySuccessEventListener {

	private static final Logger log = LoggerFactory.getLogger(RunnerPaySuccessEventListener.class);

	private final RunnerTaskMapper taskMapper;

	public RunnerPaySuccessEventListener(RunnerTaskMapper taskMapper) {
		this.taskMapper = taskMapper;
	}

	@EventListener
	public void onPaySuccess(PaySuccessEvent event) {
		Long orderId = event.getOrderId();
		// bizType 由 publishPaySuccess 从 trade_pay.biz_type 读出（消除多态后的权威判别），
		// 取代旧 orderId==null 判别——trade 商品订单事件不再进本监听器
		if (orderId == null || !"runner".equals(event.getBizType())) {
			return;
		}
		String outTradeNo = event.getOutTradeNo();
		// 按 outTradeNo 反查（保留为防御）；biz_type=runner 时 orderId 即任务 ID，反查兜底
		Long taskId = taskMapper.selectTaskIdByOutTradeNo(outTradeNo);
		if (taskId == null) {
			taskId = orderId;
		}
		int rows = taskMapper.markPayStatusByTaskId(taskId, RunnerTask.PAY_STATUS_PAID);
		if (rows > 0) {
			log.info("互助任务支付状态已更新: taskId={}, outTradeNo={}", taskId, outTradeNo);
		}
		else {
			log.info("互助任务支付状态未变更（已 paid 或任务已取消）: taskId={}, outTradeNo={}", taskId, outTradeNo);
		}
	}

}
