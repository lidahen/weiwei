package com.twelvet.exchange.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 换物匹配推送快照 Mapper（twelve_exchange_match_push，docs/交换广场二期-智能匹配与信用设计.md §二）
 * <p>
 * 快照天然去重：同一需求方对同一物品只推一次（pushed_user_ids JSON diff）。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeMatchPushMapper {

	/** 物品已推送名单快照（无记录返回 null） */
	Map<String, Object> selectByItemId(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/** upsert 快照（uk_item 冲突时更新已推名单与推送时间） */
	int upsertPush(@Param("tenantId") Long tenantId, @Param("itemId") Long itemId,
			@Param("pushedUserIds") String pushedUserIds);

}
