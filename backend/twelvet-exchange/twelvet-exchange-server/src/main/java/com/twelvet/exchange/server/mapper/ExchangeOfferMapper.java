package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.api.domain.ExchangeOffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 换物意向 Mapper（twelve_exchange_offer）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeOfferMapper {

	int insert(ExchangeOffer offer);

	/**
	 * 原子防重插入：同租户同物品同人已有 pending 时插入 0 行（并发竞态根治）
	 */
	int insertIfNoPending(ExchangeOffer offer);

	/**
	 * 物品下架时批量关闭未决意向（closed）
	 */
	int closePendingByItem(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	ExchangeOffer selectById(@Param("offerId") Long offerId, @Param("tenantId") Long tenantId);

	/**
	 * 同物品同人仅一个 pending 意向（应用层查重，idx_item 前缀扫描兜底）
	 */
	int countPendingByItemAndOfferor(@Param("itemId") Long itemId, @Param("offerorId") Long offerorId,
			@Param("tenantId") Long tenantId);

	/**
	 * 我收到的意向（pending 优先）
	 */
	List<ExchangeOffer> selectReceived(@Param("ownerId") Long ownerId, @Param("tenantId") Long tenantId,
			@Param("limit") Integer limit);

	/**
	 * 我发出的意向
	 */
	List<ExchangeOffer> selectSent(@Param("offerorId") Long offerorId, @Param("tenantId") Long tenantId,
			@Param("limit") Integer limit);

	/**
	 * 接受意向第一步：意向占位（pending → accepted，未过期才允许）
	 */
	int acceptAtomically(@Param("offerId") Long offerId, @Param("tenantId") Long tenantId,
			@Param("now") LocalDateTime now);

	/**
	 * 婉拒（物品主人：pending → rejected）
	 */
	int rejectAtomically(@Param("offerId") Long offerId, @Param("ownerId") Long ownerId,
			@Param("tenantId") Long tenantId);

	/**
	 * 撤回（发起人：pending → withdrawn）
	 */
	int withdrawAtomically(@Param("offerId") Long offerId, @Param("offerorId") Long offerorId,
			@Param("tenantId") Long tenantId);

	/**
	 * 物品成交后批量婉拒其余 pending 意向
	 */
	int rejectOthersPending(@Param("itemId") Long itemId, @Param("keepOfferId") Long keepOfferId,
			@Param("tenantId") Long tenantId);

	/**
	 * 物品当前全部 pending 意向（批量婉拒前取出，供逐条 IM 通知）
	 */
	List<ExchangeOffer> selectPendingByItem(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/**
	 * 扫过期未处理意向（pending 且 expire_time 已过）
	 */
	List<ExchangeOffer> selectExpiredPending(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

	/**
	 * 意向过期（pending → expired）
	 */
	int expireAtomically(@Param("offerId") Long offerId, @Param("tenantId") Long tenantId);

	/**
	 * 看板统计
	 */
	Map<String, Object> selectStatistics(@Param("tenantId") Long tenantId);

}
