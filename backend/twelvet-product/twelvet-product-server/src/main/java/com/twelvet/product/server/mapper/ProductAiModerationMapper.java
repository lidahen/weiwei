package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * AI 初审 Mapper（docs/AI初审与发布助手设计.md）
 * <p>
 * ai_moderation_log 一次初审一行；商品/动态审核流转复用本 Mapper 的专用语句 （带 publish_time / audit_reason
 * 语义），审核日志写入 product_audit_log（auditor_id=0 表示 AI）。
 */
@Mapper
public interface ProductAiModerationMapper {

	/** 待初审商品（平台视角查全租户，含类目名供 LLM 上下文） */
	List<Map<String, Object>> selectAuditingProducts(@Param("limit") int limit);

	/** 待初审动态（平台视角） */
	List<Map<String, Object>> selectPendingFeedsForModeration(@Param("limit") int limit);

	/** 指定业务对象最近一次初审记录（含 error），无记录返回 null */
	Map<String, Object> selectLatestModeration(@Param("bizType") String bizType, @Param("bizId") Long bizId);

	/** 指定业务对象累计初审失败次数（超过上限转人工，不再重试） */
	int countModerationErrors(@Param("bizType") String bizType, @Param("bizId") Long bizId);

	int insertModerationLog(@Param("tenantId") Long tenantId, @Param("bizType") String bizType,
			@Param("bizId") Long bizId, @Param("riskLevel") String riskLevel, @Param("action") String action,
			@Param("violations") String violations, @Param("summary") String summary,
			@Param("modelName") String modelName, @Param("errorMsg") String errorMsg);

	/** AI 审核动作落 product_audit_log（auditor_id=0 = AI，与人工 103 区分） */
	int insertAiAuditLog(@Param("productId") Long productId, @Param("action") String action,
			@Param("reasonCode") String reasonCode, @Param("customReason") String customReason);

	/** AI 自动通过：与人工 approveProduct 一致需要回填 publish_time */
	int approveProductAuto(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	/** AI 自动驳回：写 audit_reject_reason 供卖家在前端看到原因 */
	int rejectProductAuto(@Param("productId") Long productId, @Param("reason") String reason,
			@Param("tenantId") Long tenantId);

}
