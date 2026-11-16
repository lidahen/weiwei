package com.twelvet.exchange.server.task;

import com.twelvet.exchange.server.service.IExchangeMatchService;
import com.twelvet.exchange.server.service.IExchangeOfferService;
import com.twelvet.exchange.server.service.IExchangeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 交换广场定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；事务由内部 service 自管。
 * </p>
 *
 * @author twelvet
 */
@Component("exchangeTask")
public class ExchangeQuartzTask {

	private static final Logger log = LoggerFactory.getLogger(ExchangeQuartzTask.class);

	private final IExchangeOfferService offerService;

	private final IExchangeOrderService orderService;

	private final IExchangeMatchService matchService;

	public ExchangeQuartzTask(IExchangeOfferService offerService, IExchangeOrderService orderService,
			IExchangeMatchService matchService) {
		this.offerService = offerService;
		this.orderService = orderService;
		this.matchService = matchService;
	}

	/**
	 * 每 5 分钟：扫过期未处理意向 → expired + 通知发起人
	 */
	public void scanTimeoutOffer() {
		int count = offerService.scanTimeoutOffer();
		log.info("[Quartz] ExchangeOfferTimeoutJob 扫描完成：过期 {} 个意向", count);
	}

	/**
	 * 每小时：建单 7 天未双交付 → 取消+解锁；双交付后 3 天未双确认 → 自动完成
	 */
	public void scanTimeoutOrder() {
		int count = orderService.scanTimeoutOrder();
		log.info("[Quartz] ExchangeOrderTimeoutJob 扫描完成：处理 {} 个超时订单", count);
	}

	/**
	 * 每日 10 点：反向匹配扫描——wanters 召回 + 快照 diff 去重 + IM 推送「有人想换你的物品」（幂等）
	 */
	public void scanMatchPush() {
		int pushed = matchService.scanMatchPush();
		log.info("[Quartz] ExchangeMatchPushJob 扫描完成：推送 {} 人", pushed);
	}

}
