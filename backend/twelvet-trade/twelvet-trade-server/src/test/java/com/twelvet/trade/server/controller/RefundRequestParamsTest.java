package com.twelvet.trade.server.controller;

import com.twelvet.framework.core.exception.TWTException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundRequestParamsTest {

	@Test
	@DisplayName("缺失 key → 业务提示，不是 NumberFormatException")
	void requiredLong_missing_throwsBusinessMessage() {
		TWTException e = assertThrows(TWTException.class, () -> RefundRequestParams.requiredLong(Map.of(), "orderId"));
		assertEquals("缺少参数: orderId", e.getMessage());
	}

	@Test
	@DisplayName("key 存在但值为 null → 同一个业务提示")
	void requiredLong_literalNull_throws() {
		Map<String, Object> body = new HashMap<>();
		body.put("refundId", null);
		TWTException e = assertThrows(TWTException.class, () -> RefundRequestParams.requiredLong(body, "refundId"));
		assertEquals("缺少参数: refundId", e.getMessage());
	}

	@Test
	@DisplayName("非数字 → 参数格式错误")
	void requiredLong_nonNumeric_throwsFormatMessage() {
		TWTException e = assertThrows(TWTException.class,
				() -> RefundRequestParams.requiredLong(Map.of("orderId", "abc"), "orderId"));
		assertEquals("参数格式错误: orderId", e.getMessage());
	}

	@Test
	@DisplayName("数字与带空格的数字字符串都接受")
	void requiredLong_acceptsNumberAndString() {
		assertEquals(7L, RefundRequestParams.requiredLong(Map.of("orderId", (Object) 7), "orderId"));
		assertEquals(8L, RefundRequestParams.requiredLong(Map.of("orderId", " 8 "), "orderId"));
	}

}