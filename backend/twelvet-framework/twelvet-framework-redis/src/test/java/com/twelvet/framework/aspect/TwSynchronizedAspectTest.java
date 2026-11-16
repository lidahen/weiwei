package com.twelvet.framework.aspect;

import com.twelvet.framework.annotation.TwSynchronized;
import com.twelvet.framework.redis.exception.LockAcquireException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分布式锁切面测试（mock Redisson，无真实 Redis 依赖）
 * <p>
 * 锁定不变量：获取成功 → 方法执行且 finally unlock；获取超时 → TWTException 且方法不执行；
 * 等待被中断 → 恢复中断标志并抛 LockAcquireException。
 * </p>
 */
class TwSynchronizedAspectTest {

	private RedissonClient redissonClient;

	private RLock lock;

	private ProceedingJoinPoint joinPoint;

	private TwSynchronizedAspect aspect;

	@BeforeEach
	void setUp() throws Exception {
		redissonClient = mock(RedissonClient.class);
		lock = mock(RLock.class);
		joinPoint = mock(ProceedingJoinPoint.class);
		when(joinPoint.getSignature()).thenReturn(mock(MethodSignature.class));
		// MethodSignature.getMethod() 是 final 委托，mock 返回一个带注解的真实方法
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		when(signature.getMethod()).thenReturn(TwSynchronizedAspectTest.class.getDeclaredMethod("annotated"));
		when(redissonClient.getLock("order:lock")).thenReturn(lock);
		aspect = new TwSynchronizedAspect(redissonClient);
	}

	@TwSynchronized("order:lock")
	public static void annotated() {
	}

	@Test
	@DisplayName("tryLock 成功：方法执行、finally unlock")
	void lockAcquired_proceedsAndUnlocks() throws Throwable {
		when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);
		when(joinPoint.proceed()).thenReturn("ok");
		when(lock.isHeldByCurrentThread()).thenReturn(true);

		Object result = aspect.around(joinPoint, TwSynchronizedAspectTest.class.getDeclaredMethod("annotated")
			.getAnnotation(TwSynchronized.class));

		assertEquals("ok", result);
		verify(joinPoint).proceed();
		verify(lock).unlock();
	}

	@Test
	@DisplayName("tryLock 超时：抛 TWTException、方法不执行、不 unlock")
	void lockTimeout_throwsAndSkips() throws Throwable {
		when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(false);

		assertThrows(LockAcquireException.class, () -> aspect.around(joinPoint,
				TwSynchronizedAspectTest.class.getDeclaredMethod("annotated").getAnnotation(TwSynchronized.class)));

		verify(joinPoint, never()).proceed();
		verify(lock, never()).unlock();
	}

	@Test
	@DisplayName("等待锁被中断：恢复中断标志并抛 LockAcquireException")
	void interrupted_restoresFlagAndThrows() throws Throwable {
		when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new InterruptedException());

		assertThrows(LockAcquireException.class, () -> aspect.around(joinPoint,
				TwSynchronizedAspectTest.class.getDeclaredMethod("annotated").getAnnotation(TwSynchronized.class)));

		assertTrue(Thread.currentThread().isInterrupted(), "中断标志必须恢复");
		// 清理，避免影响同线程后续测试
		Thread.interrupted();
		verify(joinPoint, never()).proceed();
	}

}
