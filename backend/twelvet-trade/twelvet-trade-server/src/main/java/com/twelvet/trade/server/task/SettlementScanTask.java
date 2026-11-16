package com.twelvet.trade.server.task;

import com.twelvet.trade.server.service.ISettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结算扫描定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见 backend/sql/settlement_schema.sql 之后的结算菜单脚本。 quartz
 * 后台线程无租户上下文：扫描按全租户处理，属声明设计。
 * </p>
 *
 * @author twelvet
 */
@Component("settlementScan")
public class SettlementScanTask {

	private static final Logger log = LoggerFactory.getLogger(SettlementScanTask.class);

	private final ISettlementService settlementService;

	public SettlementScanTask(ISettlementService settlementService) {
		this.settlementService = settlementService;
	}

	/**
	 * 每 30 分钟：售后期届满的 holding 单推进 pending_settle
	 */
	public void scan() {
		int processed = settlementService.scanDueHoldings(200);
		log.info("[Quartz] Settlement scan={}", processed);
	}

}