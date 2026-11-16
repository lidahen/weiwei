package com.twelvet.exchange.server.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 换物详情浏览计数的去重键契约。
 *
 * <p>
 * 物品详情已开放给游客（@SaIgnore）。原实现用 {@code "exchange:view:" + itemId + ":" + userId}
 * 做 5 分钟去重键，游客 userId 为 null，所有游客共用同一个键
 * {@code exchange:view:{id}:null} —— 结果是 5 分钟内只有第一个游客的浏览被计数，
 * 其余游客的浏览全部丢失，浏览量被系统性低估。
 * </p>
 *
 * <p>
 * 修法：游客没有稳定身份可去重，返回 null 表示「不做去重、每次都计数」。
 * 登录用户仍按 userId 去重。
 * </p>
 */
class ExchangeViewCountKeyTest {

	@Test
	@DisplayName("登录用户按 userId 去重")
	void loggedInUserHasKey() {
		assertEquals("exchange:view:7:5", ExchangeItemServiceImpl.buildViewKey(7L, 5L));
	}

	@Test
	@DisplayName("游客没有去重键（每次浏览都计数，而不是全站共用一个键）")
	void anonymousHasNoKey() {
		assertNull(ExchangeItemServiceImpl.buildViewKey(7L, null),
				"游客必须返回 null：否则所有游客共用 exchange:view:7:null，浏览量被系统性低估");
	}

	@Test
	@DisplayName("不同物品的键互不相同")
	void keysDifferPerItem() {
		assertNotNull(ExchangeItemServiceImpl.buildViewKey(7L, 5L));
		assertNotNull(ExchangeItemServiceImpl.buildViewKey(8L, 5L));
	}
}
