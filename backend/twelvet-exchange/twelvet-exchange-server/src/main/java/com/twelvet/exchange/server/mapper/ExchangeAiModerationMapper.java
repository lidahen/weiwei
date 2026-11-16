package com.twelvet.exchange.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * AI 初审 Mapper（docs/交换广场模块设计方案.md §4.1）
 * <p>
 * ai_moderation_log 一次初审一行（bizType='exchange_item'）；物品审核流转复用本 Mapper 的专用语句。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeAiModerationMapper {

	/** 待初审物品（平台视角查全租户，Quartz 任务逐行按 tenant_id 处理） */
	List<Map<String, Object>> selectAuditingItems(@Param("limit") int limit);

	/** 指定业务对象最近一次初审记录（含 error），无记录返回 null */
	Map<String, Object> selectLatestModeration(@Param("bizType") String bizType, @Param("bizId") Long bizId);

	/** 指定业务对象累计初审失败次数（超过上限转人工，不再重试） */
	int countModerationErrors(@Param("bizType") String bizType, @Param("bizId") Long bizId);

	int insertModerationLog(@Param("tenantId") Long tenantId, @Param("bizType") String bizType,
			@Param("bizId") Long bizId, @Param("riskLevel") String riskLevel, @Param("action") String action,
			@Param("violations") String violations, @Param("summary") String summary,
			@Param("modelName") String modelName, @Param("errorMsg") String errorMsg);

	/** AI 自动通过：onswap + publish_time（与人工 passItem 同款语义） */
	int approveItemAuto(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId);

	/** AI 自动驳回：rejected + reject_reason 供主人在前端看到原因 */
	int rejectItemAuto(@Param("itemId") Long itemId, @Param("reason") String reason, @Param("tenantId") Long tenantId);

	/** 租户系统发送人（IM system 通知用，与商品初审 FavoriteMapper.selectSenderUserId 同款） */
	Long selectSenderUserId(@Param("tenantId") Long tenantId);

}
