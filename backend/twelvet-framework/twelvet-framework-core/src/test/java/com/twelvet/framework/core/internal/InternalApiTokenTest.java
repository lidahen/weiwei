package com.twelvet.framework.core.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内部接口令牌校验器测试
 * <p>
 * 重点验证"失败关闭"：未配置令牌时任何输入都必须被拒绝——这是该端点唯一鉴权手段， 一旦出现"未配置即放行"，等于把代任意用户下单的能力暴露到公网。
 * </p>
 */
class InternalApiTokenTest {

	private static final String TOKEN = "s3cr3t-internal-token";

	@Test
	void notConfigured_alwaysRejects() {
		InternalApiToken notConfigured = new InternalApiToken("");
		assertFalse(notConfigured.configured());
		assertFalse(notConfigured.matches(null), "未配置令牌时 null 必须被拒绝");
		assertFalse(notConfigured.matches(""), "未配置令牌时空串必须被拒绝");
		assertFalse(notConfigured.matches("anything"), "未配置令牌时任意值必须被拒绝（失败关闭）");
		assertFalse(notConfigured.matches(TOKEN), "未配置令牌时即便传入看起来正确的值也必须被拒绝");
	}

	@Test
	void blankConfiguration_treatedAsNotConfigured() {
		InternalApiToken blank = new InternalApiToken("   ");
		assertFalse(blank.configured());
		assertFalse(blank.matches("   "));
	}

	@Test
	void configured_acceptsOnlyExactToken() {
		InternalApiToken token = new InternalApiToken(TOKEN);
		assertTrue(token.configured());
		assertTrue(token.matches(TOKEN), "正确令牌应通过");
		assertFalse(token.matches(null), "缺失令牌应被拒绝");
		assertFalse(token.matches(""), "空令牌应被拒绝");
		assertFalse(token.matches("s3cr3t-internal-toke"), "少一个字符应被拒绝");
		assertFalse(token.matches("s3cr3t-internal-tokenx"), "多一个字符应被拒绝");
		assertFalse(token.matches("S3CR3T-INTERNAL-TOKEN"), "大小写敏感");
		// matches() 会先 trim 调用方传入的值，因此尾随空白不影响比对结果
		assertTrue(token.matches(TOKEN + " "), "调用方值尾部空白会被 trim，等价于正确令牌");
	}

	@Test
	void configured_trimsSurroundingWhitespace() {
		InternalApiToken token = new InternalApiToken("  " + TOKEN + "  ");
		assertTrue(token.matches(TOKEN), "配置值两端空白应被去除");
		assertTrue(token.matches("  " + TOKEN + "  "), "调用方值的两端空白应被去除");
	}

}
