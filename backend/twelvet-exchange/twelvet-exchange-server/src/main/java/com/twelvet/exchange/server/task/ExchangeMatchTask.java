package com.twelvet.exchange.server.task;

import com.twelvet.exchange.server.client.ExchangeMatchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 换物匹配定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用）
 * <p>
 * 方法必须无参；sys_job 注册见 backend/sql/exchange_match.sql（job_id=124）。
 * </p>
 *
 * @author twelvet
 */
@Component("exchangeMatchTask")
public class ExchangeMatchTask {

	private static final Logger log = LoggerFactory.getLogger(ExchangeMatchTask.class);

	private final ExchangeMatchClient matchClient;

	public ExchangeMatchTask(ExchangeMatchClient matchClient) {
		this.matchClient = matchClient;
	}

	/**
	 * 每 30 分钟：调 ai-service /exchange-match/reindex 增量同步换物双索引 （embedding 未配置/上游不可用时客户端降级返回 0 并记
	 * warn，下轮重试）
	 */
	public void syncVectors() {
		int count = matchClient.reindex();
		log.info("[Quartz] ExchangeVectorSyncJob 同步完成：{} 个换物物品在索引内", count);
	}

}
