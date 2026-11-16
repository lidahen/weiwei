package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 结算控制器参数解析与身份/租户取值的统一入口。
 * <p>
 * 卖家端身份取登录态、租户取上下文；上下文缺失直接失败，绝不静默落 DEFAULT 1。
 * </p>
 *
 * @author twelvet
 */
public final class SettlementRequestParams {

	private SettlementRequestParams() {
	}

	/**
	 * 必填 Long
	 * @param body JSON body
	 * @param key 参数名
	 * @return 参数值
	 */
	public static Long requiredLong(Map<String, Object> body, String key) {
		return RefundRequestParams.requiredLong(body, key);
	}

	/**
	 * 字符串参数：null/空串/"null" 统一为 null
	 * @param value 原始值
	 * @return 归一后的字符串
	 */
	public static String str(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() || "null".equals(text) ? null : text;
	}

	/**
	 * 宽松解析金额（非法值按 null 处理，由 Service 校验）
	 * @param value 原始值
	 * @return 金额
	 */
	public static BigDecimal decimal(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return new BigDecimal(String.valueOf(value).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 当前租户（缺失即拒绝，不落默认租户）
	 * @return 租户ID
	 */
	public static Long tenantId() {
		Long tenantId = TenantContext.get();
		if (tenantId == null) {
			throw new TWTException("缺少租户上下文");
		}
		return tenantId;
	}

}