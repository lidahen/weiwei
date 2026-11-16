package com.twelvet.framework.log.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 请求体日志脱敏测试
 * <p>
 * 锁定的不变量：实名提交接口的请求体包含证件号/银行卡号/姓名，必须整段脱敏后再写日志； 其他接口的请求体保持原样。
 * </p>
 */
class WebLogFilterSanitizeTest {

	private static final String KYC_BODY = "{\"accountName\":\"张三\",\"idCardNo\":\"110101199001011234\","
			+ "\"bankCardNo\":\"6222020200112233445\",\"bankName\":\"工商银行\"}";

	@Test
	@DisplayName("实名提交：请求体不落明文")
	void kycBodyHidden() {
		String logged = WebLogFilter.sanitizeBody("/trade/seller/account/apply", KYC_BODY);

		assertFalse(logged.contains("张三"));
		assertFalse(logged.contains("110101199001011234"));
		assertFalse(logged.contains("6222020200112233445"));
	}

	@Test
	@DisplayName("普通接口：请求体保持原样")
	void otherBodyUnchanged() {
		assertEquals(KYC_BODY, WebLogFilter.sanitizeBody("/trade/order/create", KYC_BODY));
	}

	@Test
	@DisplayName("空请求体：保持 null/空串")
	void blankBodyUnchanged() {
		assertEquals(null, WebLogFilter.sanitizeBody("/trade/seller/account/apply", null));
		assertEquals("", WebLogFilter.sanitizeBody("/trade/seller/account/apply", ""));
	}

}