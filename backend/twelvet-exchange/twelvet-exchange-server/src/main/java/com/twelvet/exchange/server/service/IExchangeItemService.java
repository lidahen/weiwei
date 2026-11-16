package com.twelvet.exchange.server.service;

import com.twelvet.exchange.api.domain.ExchangeItem;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 换物物品服务（发布/广场/我的物品/审核）
 *
 * @author twelvet
 */
public interface IExchangeItemService {

	/**
	 * 发布物品（status=auditing，item_no=ESC+yyyyMMddHHmmss+6位随机，进 AI 初审链路）
	 */
	Long publish(ExchangeItem item, Long userId);

	/**
	 * 物品详情（浏览数+1 + 主人换物主页摘要，摘要含 ownerCredit 信用分）
	 */
	Map<String, Object> detail(Long itemId, Long userId);

	/**
	 * 广场列表（分页；类目/成色/关键词全文/交付方式筛选，hot_score 排序）
	 */
	TableDataInfo square(Long pageNum, Long pageSize, String catL1, String conditionLevel, String deliveryMode,
			String keyword);

	/**
	 * 我的换物物品（分状态 tab）
	 */
	List<ExchangeItem> myItems(Long userId, String status);

	/**
	 * 下架 / 重新上架（onswap ↔ offshelf）
	 * @return 流转后的状态
	 */
	String toggleOffshelf(Long itemId, Long userId);

	/**
	 * 类目树（只读复用 product_category_dict）
	 */
	List<Map<String, Object>> categoryTree();

	/**
	 * 平台管理：物品分页（auditing 置顶）
	 */
	TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword);

	/**
	 * 人工审核通过（auditing → onswap + publish_time）
	 */
	void adminPass(Long itemId, Long operatorId);

	/**
	 * 人工审核驳回（auditing → rejected + reject_reason，IM 通知主人）
	 */
	void adminReject(Long itemId, Long operatorId, String reason);

	/**
	 * 平台统计（在架物品数、累计浏览量、意向量、成交率、纠纷率、热门类目）
	 */
	Map<String, Object> stat();

}
