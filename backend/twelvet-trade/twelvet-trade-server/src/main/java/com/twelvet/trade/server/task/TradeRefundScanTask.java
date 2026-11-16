package com.twelvet.trade.server.task;

import com.twelvet.trade.server.service.ITradeRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 退款超时扫描定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见 backend/sql/refund_feature.sql（tradeRefundScan.scan，默认暂停）。 quartz
 * 后台线程无租户上下文，service 层按全租户语义（tenantId=null）处理——属声明的设计，勿加租户过滤。
 * </p>
 * <p>
 * 三类超时：pending_seller 48h → mediation；seller_agreed 7d → closed（订单恢复）； buyer_returned 7d
 * → mediation。
 * </p>
 *
 * @author twelvet
 */
@Component("tradeRefundScan")
public class TradeRefundScanTask {

	private static final Logger log = LoggerFactory.getLogger(TradeRefundScanTask.class);

	private final ITradeRefundService refundService;

	public TradeRefundScanTask(ITradeRefundService refundService) {
		this.refundService = refundService;
	}

	/**
	 * 每 30 分钟：扫描三类退款超时并推进状态
	 */
	public void scan() {
		int processed = refundService.scanTimeouts();
		log.info("[Quartz] TradeRefund scan={}", processed);
	}

}