package com.twelvet.trade.server.task;

import com.twelvet.trade.server.service.ISettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结算打款定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见结算菜单/任务脚本。quartz 后台线程无租户上下文：按全租户处理，属声明设计。
 * </p>
 *
 * @author twelvet
 */
@Component("settlementPayout")
public class SettlementPayoutTask {

	private static final Logger log = LoggerFactory.getLogger(SettlementPayoutTask.class);

	private final ISettlementService settlementService;

	public SettlementPayoutTask(ISettlementService settlementService) {
		this.settlementService = settlementService;
	}

	/**
	 * 每 30 分钟：pending_settle 单提交 mock 渠道并落账
	 */
	public void payout() {
		int processed = settlementService.payoutBatch(200);
		log.info("[Quartz] Settlement payout={}", processed);
	}

}