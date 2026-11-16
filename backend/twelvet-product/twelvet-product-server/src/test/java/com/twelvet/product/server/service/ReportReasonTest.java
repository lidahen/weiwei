package com.twelvet.product.server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 举报原因枚举与 reason 列合成文本的拆分。
 * <p>
 * report.reason 是单列（varchar(500)），存「标签：详情」合成文本，不新增 reason_type 列。 拆分规则必须与 compose
 * 严格互逆，否则运营列表会把整串当详情显示。
 * </p>
 *
 * @author twelvet
 */
class ReportReasonTest {

	@Test
	@DisplayName("六种原因全部合法，未知值不合法")
	void isValid() {
		for (String code : new String[] { "spam", "fraud", "fake", "prohibited", "abuse", "other" }) {
			assertTrue(ReportReason.isValid(code));
		}
		assertFalse(ReportReason.isValid("unknown"));
		assertFalse(ReportReason.isValid(null));
		assertFalse(ReportReason.isValid(""));
	}

	@Test
	@DisplayName("标签映射")
	void labelOf() {
		assertEquals("欺诈 / 骗钱", ReportReason.labelOf("fraud"));
		assertEquals("垃圾广告 / 引流", ReportReason.labelOf("spam"));
		assertEquals("其他", ReportReason.labelOf("unknown"));
	}

	@Test
	@DisplayName("compose：有详情拼「标签：详情」，无详情只有标签")
	void compose() {
		assertEquals("欺诈 / 骗钱：卖家收钱不发货", ReportReason.compose("fraud", "卖家收钱不发货"));
		assertEquals("欺诈 / 骗钱", ReportReason.compose("fraud", null));
		assertEquals("欺诈 / 骗钱", ReportReason.compose("fraud", "   "));
	}

	@Test
	@DisplayName("compose 截断到 500 字")
	void composeTruncates() {
		String detail = "x".repeat(600);
		assertEquals(500, ReportReason.compose("fraud", detail).length());
	}

	@Test
	@DisplayName("截断不得切断代理对（emoji），否则留下孤立代理无法写入 utf8mb4")
	void composeTruncateDoesNotSplitSurrogatePair() {
		// 构造让第 500 个字符正好是代理对的高位：标签 8 字 + 冒号 + 详情
		String label = "欺诈 / 骗钱";
		int padding = 500 - label.length() - 1 - 1;
		String detail = "x".repeat(padding) + "😀" + "y".repeat(100);

		String reason = ReportReason.compose("fraud", detail);
		assertTrue(reason.length() <= 500, "截断后长度必须 <= 500");

		// 不能以孤立高位代理结尾
		char last = reason.charAt(reason.length() - 1);
		assertFalse(Character.isHighSurrogate(last), "截断点留下孤立高位代理，写入 utf8mb4 会报错");

		// 逐字符校验：任何代理都必须成对
		for (int i = 0; i < reason.length(); i++) {
			if (Character.isHighSurrogate(reason.charAt(i))) {
				assertTrue(i + 1 < reason.length() && Character.isLowSurrogate(reason.charAt(i + 1)),
						"位置 " + i + " 的高位代理没有配对低位代理");
				i++;
			}
			else {
				assertFalse(Character.isLowSurrogate(reason.charAt(i)), "位置 " + i + " 出现孤立低位代理");
			}
		}
	}

	@Test
	@DisplayName("split 与 compose 互逆")
	void splitRoundTrip() {
		String reason = ReportReason.compose("fraud", "卖家收钱不发货");
		assertEquals("欺诈 / 骗钱", ReportReason.splitType(reason));
		assertEquals("卖家收钱不发货", ReportReason.splitDetail(reason));
	}

	@Test
	@DisplayName("无冒号的旧数据：整串作详情，类型为空")
	void splitLegacy() {
		assertEquals("", ReportReason.splitType("内容违规"));
		assertEquals("内容违规", ReportReason.splitDetail("内容违规"));
	}

	@Test
	@DisplayName("null / 空串不抛异常")
	void splitNull() {
		assertEquals("", ReportReason.splitType(null));
		assertEquals("", ReportReason.splitDetail(null));
	}

}
