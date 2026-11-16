package com.twelvet.exchange.server.service;

import com.twelvet.exchange.api.domain.ExchangeOffer;

import java.util.List;

/**
 * 换物意向服务（发起/收发列表/接受/婉拒/撤回）
 *
 * @author twelvet
 */
public interface IExchangeOfferService {

	/**
	 * 发起意向（校验：非本人物品、同物品同人无 pending、目标物品 onswap、offer_item_id 或 offer_snapshot
	 * 二选一；expire=now+72h）
	 */
	Long create(ExchangeOffer offer, Long offerorId);

	/**
	 * 我收到的意向（pending 优先）
	 */
	List<ExchangeOffer> received(Long userId);

	/**
	 * 我发出的意向
	 */
	List<ExchangeOffer> sent(Long userId);

	/**
	 * 接受意向（乐观锁事务，见 docs/交换广场模块设计方案.md §4.3）→ 生成订单
	 * @return 新建订单ID
	 */
	Long accept(Long offerId, Long userId);

	/**
	 * 婉拒（仅物品主人）
	 */
	void reject(Long offerId, Long userId);

	/**
	 * 撤回我发起的意向
	 */
	void withdraw(Long offerId, Long userId);

	/**
	 * 过期意向扫描（pending 且过期 → expired + 通知，用于 Quartz 定时任务）
	 * @return 受影响意向数
	 */
	int scanTimeoutOffer();

}
