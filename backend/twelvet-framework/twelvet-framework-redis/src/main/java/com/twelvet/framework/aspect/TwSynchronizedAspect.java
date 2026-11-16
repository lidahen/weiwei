package com.twelvet.framework.aspect;

import com.twelvet.framework.annotation.TwSynchronized;
import com.twelvet.framework.redis.exception.LockAcquireException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面（Redisson）
 * <p>
 * 拦截 {@link TwSynchronized} 注解方法：以注解 value 为锁名取 Redisson 可重入锁
 * （语义上按"不支持重入"使用），等待 10s、持锁 30s 自动释放（防死锁——被注解方法
 * 执行超 30s 后并发线程可进入，这是设计选择）。获取失败抛业务异常。
 * </p>
 * <p>
 * 注意：类内自调用（this.method()）不走代理，注解不生效——与 @Transactional 同一限制。
 * </p>
 *
 * @author twelvet
 */
@Aspect
@Component
public class TwSynchronizedAspect {

	private static final Logger log = LoggerFactory.getLogger(TwSynchronizedAspect.class);

	/** 等锁超时（秒） */
	private static final long WAIT_TIME_SECONDS = 10;

	/** 持锁自动释放时间（秒），防持有者崩溃后死锁 */
	private static final long LEASE_TIME_SECONDS = 30;

	private final RedissonClient redissonClient;

	public TwSynchronizedAspect(RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
	}

	@Around("@annotation(twSynchronized)")
	public Object around(ProceedingJoinPoint joinPoint, TwSynchronized twSynchronized) throws Throwable {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		String lockName = twSynchronized.value();
		RLock lock = redissonClient.getLock(lockName);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("[分布式锁] 等待锁被中断: lock={}, method={}", lockName, method.getName());
			throw new LockAcquireException("操作繁忙，请稍后重试");
		}
		if (!acquired) {
			log.warn("[分布式锁] 获取锁超时: lock={}, method={}", lockName, method.getName());
			throw new LockAcquireException("操作繁忙，请稍后重试");
		}
		try {
			return joinPoint.proceed();
		}
		finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

}
