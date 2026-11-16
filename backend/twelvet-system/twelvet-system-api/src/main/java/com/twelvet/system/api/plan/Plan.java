package com.twelvet.system.api.plan;

import java.math.BigDecimal;

/**
 * 套餐元数据（商业化 P0：硬编码 3 档，套餐变更请改 PlanCatalog）
 *
 * @param code 套餐编码（与 sys_tenant.plan_code 对应）
 * @param name 中文名（仅展示）
 * @param quotaProductMax 商品数量上限；0 = 不限
 * @param quotaAiCallDay 每日 AI 调用上限；0 = 不限
 * @param monthlyPrice 月价（元）；0 = 免费。P2 起扫码续费按此计价
 */
public record Plan(String code, String name, int quotaProductMax, int quotaAiCallDay, BigDecimal monthlyPrice) {

}
