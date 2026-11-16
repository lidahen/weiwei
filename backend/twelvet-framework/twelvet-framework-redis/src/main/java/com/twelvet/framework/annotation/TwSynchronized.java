package com.twelvet.framework.annotation;

import java.lang.annotation.*;

/**
 * 分布式锁（不支持重入）
 * <p>
 * 2026-11-03 实现：{@link com.twelvet.framework.aspect.TwSynchronizedAspect}（Redisson）。
 * 使用须知：
 * <ul>
 * <li>value 即锁名（Redisson key 原样），跨业务共用注解时请自带业务前缀防撞名</li>
 * <li>等锁 10s，超时抛 {@link com.twelvet.framework.redis.exception.LockAcquireException}
 * （"操作繁忙，请稍后重试"；经全局兜底 handler 返回 HTTP 500）</li>
 * <li>持锁 30s 自动释放（防死锁）；方法执行超 30s 后并发线程可进入——长任务勿用本注解</li>
 * <li>不支持重入；类内自调用（this.method()）不走代理、注解不生效</li>
 * </ul>
 * </p>
 *
 * @author twelvet
 * @Description: 分布式锁（不支持重入）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface TwSynchronized {

	/**
	 * 唯一锁名称
	 */
	String value();

}
