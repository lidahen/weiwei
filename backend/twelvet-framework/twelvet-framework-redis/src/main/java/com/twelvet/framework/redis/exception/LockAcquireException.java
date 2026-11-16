package com.twelvet.framework.redis.exception;

/**
 * 分布式锁获取失败异常
 * <p>
 * 独立定义而非复用 TWTException：framework-core 已反向依赖本模块，
 * redis 模块不能依赖 core（循环依赖）。由全局异常处理器的兜底
 * {@code @ExceptionHandler(Exception.class)} 捕获（HTTP 500）；
 * 调用方亦可显式捕获后降级。
 * </p>
 *
 * @author twelvet
 */
public class LockAcquireException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LockAcquireException(String message) {
		super(message);
	}

	public LockAcquireException(String message, Throwable cause) {
		super(message, cause);
	}

}
