package com.twelvet.exchange.server.service;

import com.twelvet.exchange.api.domain.ExchangeItem;

import java.util.List;

/**
 * 换物向量匹配服务（向量双向召回 + 每日推送，docs/交换广场二期-智能匹配与信用设计.md §4.2/§4.3）
 *
 * @author twelvet
 */
public interface IExchangeMatchService {

	/**
	 * 为你推荐（登录用户全部 onswap 物品的 want_desc 向量召回供给物品卡片，按相似度排序）
	 * @return 命中卡片（可能为空=无需求文本/向量通道不可用/无有效物品，调用方降级空态）
	 */
	List<ExchangeItem> recommendForMe(Long userId, int limit);

	/**
	 * 可能想换 TA 的人（物品供给向量反向召回需求侧 onswap 物品卡片，按相似度排序）
	 * @return 命中卡片（可能为空，调用方降级空态）
	 */
	List<ExchangeItem> wanters(Long itemId, int limit);

	/**
	 * 反向匹配每日推送扫描（wanters 召回 + 快照 diff 去重 + IM 推送「有人想换你的物品」，幂等）
	 * @return 本轮推送人数
	 */
	int scanMatchPush();

}
