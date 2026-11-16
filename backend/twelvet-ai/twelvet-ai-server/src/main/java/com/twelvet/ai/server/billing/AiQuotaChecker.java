package com.twelvet.ai.server.billing;

/**
 * （已停用）AI 聊天每日配额检查（商业化第三轮 P1 原实现，见 git 历史）。
 *
 * <p>
 * 配额统一改造（2026-09）：聊天每日配额的检查与计数已统一收口到 Python ai-service 侧 单一账本——所有 AI 入口（聊天
 * {@code /ai/chat}、{@code /ai/chat/stream}，商品辅助 {@code /ai/assist/*}）统一由
 * {@code app/services/quota_service.py} 的 {@code check_and_count} 在 MySQL
 * {@code ai_usage_daily}（租户 × 自然日）上事前检查并计数， 与 {@code ai_usage_log} 明细账共用同一口径。
 * </p>
 *
 * <p>
 * 停用原因：本类原先在 Redis（db1，{@code BillingKeys.aiDayKey}）按日 INCR 独立计数， 与 Python 侧
 * {@code ai_usage_daily} 形成两套互不相通的每日额度池， {@code sys_tenant.quota_ai_call_day}
 * 实际被翻倍（聊天一次、内容生成一次各算各的）。 本类只承担每日次数计数，无每秒级限流职责，故整体停用而非部分保留。
 * </p>
 *
 * <p>
 * 口径不变：{@code quota_ai_call_day}=0/NULL 表示不限（Pro，仍计数供用量展示）、 套餐过期拒绝、超限返回 429 友好错误（Python
 * 返回 {@code code=429} 的 ApiResponse， Java 侧 {@code AiServiceClient.unwrapBody} 将其转为
 * {@code QuotaExceededException} → HTTP 429 透传前端）。
 * </p>
 *
 * <p>
 * 本类保留仅为说明口径迁移原因（无调用方）；Redis 中的历史计数 key 随 TTL 自然过期。
 * </p>
 */
public class AiQuotaChecker {

	/**
	 * @deprecated 配额检查与计数已统一到 Python ai-service（ai_usage_daily 单一账本，
	 * quota_service.check_and_count），此方法为空操作。
	 */
	@Deprecated
	public void checkAndIncr() {
		// no-op：原 Redis db1 按日 INCR 计数已废弃，配额统一由 Python quota_service 计入 ai_usage_daily
	}

}
