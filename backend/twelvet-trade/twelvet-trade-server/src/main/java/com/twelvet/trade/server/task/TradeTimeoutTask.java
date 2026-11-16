package com.twelvet.trade.server.task;

import com.twelvet.trade.server.service.ITradeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 交易超时定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见 docs/db 基线（tradeTimeout.* 两个 Job）。 quartz 后台线程无租户上下文，service
 * 层按全租户语义（tenantId=null）处理——属声明的设计，勿加租户过滤。
 * </p>
 *
 * @author twelvet
 */
@Component("tradeTimeout")
public class TradeTimeoutTask {

	private static final Logger log = LoggerFactory.getLogger(TradeTimeoutTask.class);

	private final ITradeOrderService tradeOrderService;

	public TradeTimeoutTask(ITradeOrderService tradeOrderService) {
		this.tradeOrderService = tradeOrderService;
	}

	/**
	 * 每 5 分钟：关闭超时未支付订单（下单 30 分钟未支付）并回补库存
	 */
	public void cancelExpiredUnpaid() {
		int closed = tradeOrderService.cancelExpiredUnpaid();
		log.info("[Quartz] TradeTimeout closeUnpaid={}", closed);
	}

	/**
	 * 每 5 分钟：发货超过 auto-confirm-days（默认 7 天）未确认的订单自动确认收货并标记商品已售
	 */
	public void autoConfirmReceipt() {
		int confirmed = tradeOrderService.autoConfirmReceipt();
		log.info("[Quartz] TradeTimeout autoConfirm={}", confirmed);
	}

}
