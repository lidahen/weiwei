package com.twelvet.trade.server.task;

import com.twelvet.trade.server.service.IReconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 结算每日对账定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；对账 T-1 业务日。quartz 后台线程无租户上下文：差异按全租户入队，属声明设计。
 * </p>
 *
 * @author twelvet
 */
@Component("settlementRecon")
public class SettlementReconTask {

	private static final Logger log = LoggerFactory.getLogger(SettlementReconTask.class);

	private final IReconService reconService;

	public SettlementReconTask(IReconService reconService) {
		this.reconService = reconService;
	}

	/**
	 * 每日：对账 T-1 业务日并冻结差异卖家
	 */
	public void reconcile() {
		LocalDate bizDate = LocalDate.now().minusDays(1);
		int processed = reconService.reconcile(bizDate);
		log.info("[Quartz] Settlement recon bizDate={} diffs={}", bizDate, processed);
	}

}