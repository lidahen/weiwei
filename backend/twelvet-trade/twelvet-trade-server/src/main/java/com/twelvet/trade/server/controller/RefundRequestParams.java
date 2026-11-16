package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.exception.TWTException;

import java.util.Map;

/**
 * 退款控制器 JSON body 参数解析。
 * <p>
 * 缺失 / 非法值一律翻译为 TWTException（HTTP 400），避免 NumberFormatException 把用户输入问题伪装成服务端 500。
 * </p>
 *
 * @author twelvet
 */
public final class RefundRequestParams {

	private RefundRequestParams() {
	}

	/**
	 * 读取必填 Long 参数
	 * @param body JSON body
	 * @param key 参数名
	 * @return 参数值
	 */
	public static Long requiredLong(Map<String, Object> body, String key) {
		Object value = (body == null) ? null : body.get(key);
		String text = (value == null) ? null : String.valueOf(value).trim();
		if (text == null || text.isEmpty() || "null".equals(text)) {
			throw new TWTException("缺少参数: " + key);
		}
		try {
			return Long.valueOf(text);
		}
		catch (NumberFormatException e) {
			throw new TWTException("参数格式错误: " + key);
		}
	}

}