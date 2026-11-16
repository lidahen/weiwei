package com.twelvet.trade.server.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款原因白名单契约测试。
 *
 * <p>
 * C 端申请接口与后台详情页共用同一份白名单，编码、中文标签与顺序都必须稳定， 否则两端展示与校验会漂移。
 * </p>
 */
class RefundReasonTest {

	@Test
	@DisplayName("六种原因全部合法，未知值不合法")
	void isValid() {
		for (String code : new String[] { "not_as_described", "damaged", "wrong_item", "not_received",
				"no_longer_needed", "other" }) {
			assertTrue(RefundReason.isValid(code), code);
		}
		assertFalse(RefundReason.isValid("bogus"));
		assertFalse(RefundReason.isValid(null));
		assertFalse(RefundReason.isValid(""));
	}

	@Test
	@DisplayName("not_received / no_longer_needed 只能用于仅退款")
	void refundOnlyReasons() {
		assertTrue(RefundReason.supportsType("not_received", "refund_only"));
		assertFalse(RefundReason.supportsType("not_received", "return_refund"));
		assertTrue(RefundReason.supportsType("no_longer_needed", "refund_only"));
		assertFalse(RefundReason.supportsType("no_longer_needed", "return_refund"));
	}

	@Test
	@DisplayName("not_as_described / damaged / wrong_item / other 两种类型都支持")
	void bothTypesReasons() {
		for (String code : new String[] { "not_as_described", "damaged", "wrong_item", "other" }) {
			assertTrue(RefundReason.supportsType(code, "refund_only"), code);
			assertTrue(RefundReason.supportsType(code, "return_refund"), code);
		}
	}

	@Test
	@DisplayName("标签映射，未知编码回落「其他」")
	void labelOf() {
		assertEquals("与描述不符", RefundReason.labelOf("not_as_described"));
		assertEquals("其他", RefundReason.labelOf("unknown"));
	}

	@Test
	@DisplayName("all() 保序返回六项，每项含 code/label/types")
	void allShape() {
		List<Map<String, Object>> all = RefundReason.all();
		assertEquals(6, all.size());
		assertEquals("not_as_described", all.get(0).get("code"));
		assertEquals(List.of("refund_only"), all.get(3).get("types"));
	}

}
