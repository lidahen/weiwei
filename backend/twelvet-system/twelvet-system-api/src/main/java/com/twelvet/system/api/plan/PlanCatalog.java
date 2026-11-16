package com.twelvet.system.api.plan;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 套餐目录（硬编码；后续要支持自定义套餐再迁移 sys_plan 表）
 * <p>
 * 商业化 P0：3 档（free / basic / pro），租户续费时按 code 选档。 位置说明：P2 起从 system-server 移到
 * system-api——trade-server 的扫码续费履约 需要读套餐配额/月价，避免 trade→system-server 依赖。
 * </p>
 */
public final class PlanCatalog {

	/** 免费版：100 商品 / 50 AI 调用/日 / 免费 */
	public static final Plan FREE = new Plan("free", "免费版", 100, 50, BigDecimal.ZERO);

	/** 基础版：1000 商品 / 500 AI 调用/日 / 99 元/月 */
	public static final Plan BASIC = new Plan("basic", "基础版", 1000, 500, new BigDecimal("99"));

	/** Pro 版：不限商品 / 不限 AI 调用 / 299 元/月 */
	public static final Plan PRO = new Plan("pro", "Pro 版", 0, 0, new BigDecimal("299"));

	private static final Map<String, Plan> PLANS = Map.of(FREE.code(), FREE, BASIC.code(), BASIC, PRO.code(), PRO);

	private PlanCatalog() {
	}

	/**
	 * 按 code 查套餐
	 * @param code 套餐编码（free/basic/pro）
	 * @return 套餐；不存在返回 null
	 */
	public static Plan get(String code) {
		return code == null ? null : PLANS.get(code);
	}

}
