package com.twelvet.framework.core.constants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 商业化 Redis key 模板（第三轮 P1：AI 计量）
 * <p>
 * ai-server（检查/计数）与 system-server（getPlanInfo 展示用量）共用， key 结构改动必须两处同步——故收口到
 * framework-core。
 * </p>
 */
public final class BillingKeys {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

	/** 每租户每日 AI 调用计数（INCR，首次写入后 26h 过期） */
	public static String aiDayKey(long tenantId) {
		return "billing:ai:day:" + tenantId + ":" + LocalDate.now().format(DAY);
	}

	private BillingKeys() {
	}

}
