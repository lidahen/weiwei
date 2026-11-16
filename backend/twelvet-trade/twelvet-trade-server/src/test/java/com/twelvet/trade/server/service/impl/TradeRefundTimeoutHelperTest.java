package com.twelvet.trade.server.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 退款超时单条事务包装测试（{@link TradeRefundTimeoutHelper}）
 * <p>
 * 单测只能断言事务注解而非真实回滚（无 Spring 上下文），因此锁定两条结构性不变量：
 * <ol>
 * <li>{@code inTransaction} 必须 {@code rollbackFor = Exception.class}：单条退款状态 / 订单恢复 /
 * 日志同事务回滚</li>
 * <li>{@code inNewTransaction} 必须 {@code REQUIRES_NEW}：自动同意的「认领」独立提交后才调网关，
 * 后续失败只回滚外层，退款单保持 {@code refunding}，超时扫描不会再次调用网关（防重复退款）</li>
 * </ol>
 * </p>
 */
class TradeRefundTimeoutHelperTest {

	private final TradeRefundTimeoutHelper helper = new TradeRefundTimeoutHelper();

	private static Transactional annotationOf(String methodName) throws NoSuchMethodException {
		Method method = TradeRefundTimeoutHelper.class.getMethod(methodName, java.util.function.Supplier.class);
		Transactional transactional = method.getAnnotation(Transactional.class);
		assertNotNull(transactional, methodName + " 必须标注 @Transactional（独立 bean 经代理生效）");
		return transactional;
	}

	@Test
	@DisplayName("inTransaction：Exception 全回滚")
	void inTransaction_rollsBackOnException() throws NoSuchMethodException {
		Transactional transactional = annotationOf("inTransaction");

		assertArrayEquals(new Class<?>[] { Exception.class }, transactional.rollbackFor());
	}

	@Test
	@DisplayName("inNewTransaction：REQUIRES_NEW 独立事务 + Exception 全回滚（认领先提交）")
	void inNewTransaction_requiresNewAndRollsBack() throws NoSuchMethodException {
		Transactional transactional = annotationOf("inNewTransaction");

		assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
		assertEquals(true, Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
	}

	@Test
	@DisplayName("动作返回值原样透传，异常向调用方传播（由 runOne 统一捕获）")
	void passesThroughResultAndException() {
		assertEquals("ok", helper.inTransaction(() -> "ok"));
		assertEquals("new", helper.inNewTransaction(() -> "new"));
		assertThrows(IllegalStateException.class, () -> helper.inTransaction(() -> {
			throw new IllegalStateException("boom");
		}));
	}

}