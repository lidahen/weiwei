package com.twelvet.framework.core.tenant;

/**
 * 租户上下文（基于 ThreadLocal）
 * <p>
 * MVP 范围：仅在 Servlet 请求线程内可用，跨线程（@Async / @Scheduled / WebSocket）需显式传递。
 * </p>
 */
public final class TenantContext {

	private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(Long tenantId) {
		CURRENT.set(tenantId);
	}

	public static Long get() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}

}
