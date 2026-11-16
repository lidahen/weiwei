package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.api.domain.ExchangeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 换物物品 Mapper（twelve_exchange_item）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeItemMapper {

	int insert(ExchangeItem item);

	/**
	 * 广场列表（onswap，hot_score 排序；keyword 走 FULLTEXT ngram 全文检索）
	 */
	List<ExchangeItem> selectSquarePage(@Param("tenantId") Long tenantId, @Param("catL1") String catL1,
			@Param("conditionLevel") String conditionLevel, @Param("deliveryMode") String deliveryMode,
			@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

	/**
	 * 广场列表总数（条件与 selectSquarePage 一致）
	 */
	long countSquarePage(@Param("tenantId") Long tenantId, @Param("catL1") String catL1,
			@Param("conditionLevel") String conditionLevel, @Param("deliveryMode") String deliveryMode,
			@Param("keyword") String keyword);

	ExchangeItem selectById(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 浏览数 +1（热度加权：浏览 +1，对齐动态 hot_score=浏览/互动加权的做法）
	 */
	int incrementViewCount(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 收到的意向数 +1（热度加权：意向 +3）
	 */
	int incrementOfferCount(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 我的换物物品（分状态 tab）
	 */
	List<ExchangeItem> selectMyItems(@Param("ownerId") Long ownerId, @Param("tenantId") Long tenantId,
			@Param("status") String status, @Param("limit") Integer limit);

	/**
	 * 主人视角物品流转（onswap ↔ offshelf 下架/重新上架）：条件 UPDATE，影响行数=0 表示状态不允许
	 */
	int transitionStatus(@Param("itemId") Long itemId, @Param("ownerId") Long ownerId,
			@Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
			@Param("tenantId") Long tenantId);

	/**
	 * 接受意向第二步：锁定 A 物品（onswap → locked）
	 */
	int lockItem(@Param("itemId") Long itemId, @Param("ownerId") Long ownerId, @Param("tenantId") Long tenantId);

	/**
	 * 物品解锁（订单取消/裁决取消：locked → onswap）
	 */
	int unlockItem(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 交换完成：物品换出下架（locked → swapped，同时 swap_count+1）
	 */
	int markSwapped(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 主人累计换出次数（换物主页摘要）
	 */
	long selectOwnerSwapTotal(@Param("ownerId") Long ownerId, @Param("tenantId") Long tenantId);

	/**
	 * 平台管理：分页查询（auditing 置顶，status 精确 + keyword 模糊 title/item_no）
	 */
	List<ExchangeItem> selectAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

	/**
	 * 平台管理：分页总数
	 */
	long countAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword);

	/**
	 * 人工审核通过（auditing → onswap + publish_time，与 AI 自动通过同款语义）
	 */
	int passItem(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 人工审核驳回（auditing → rejected + reject_reason）
	 */
	int rejectItem(@Param("itemId") Long itemId, @Param("reason") String reason, @Param("tenantId") Long tenantId);

	/**
	 * 匹配召回命中的在架物品卡片（onswap 有效性过滤 + 主人昵称，recommend/wanters 共用）
	 */
	List<ExchangeItem> selectOnswapByIds(@Param("itemIds") List<Long> itemIds, @Param("tenantId") Long tenantId);

	/**
	 * 定时任务全局扫描在架物品（跨租户分页，行内携带 tenant_id 供下游逐行处理）
	 */
	List<Map<String, Object>> selectOnswapAll(@Param("offset") long offset, @Param("limit") int limit);

	/**
	 * 看板统计
	 */
	Map<String, Object> selectStatistics(@Param("tenantId") Long tenantId);

	/**
	 * 热门类目（在架/锁定/已换出物品分布）
	 */
	List<Map<String, Object>> selectHotCategories(@Param("tenantId") Long tenantId, @Param("limit") Integer limit);

}
