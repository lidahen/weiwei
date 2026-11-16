package com.twelvet.framework.core.exception;

/**
 * 配额超限异常（商业化 P0：租户套餐配额检查触发）
 * <p>
 * 与 {@link TWTException} 区别：本异常映射 HTTP 429（Too Many Requests），而非 400。
 * </p>
 */
public class QuotaExceededException extends RuntimeException {

	public QuotaExceededException(String message) {
		super(message);
	}

}
