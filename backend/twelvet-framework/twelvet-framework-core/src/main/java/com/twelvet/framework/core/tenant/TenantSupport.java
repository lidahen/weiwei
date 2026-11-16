package com.twelvet.framework.core.tenant;

import com.twelvet.framework.core.exception.TWTException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 租户写操作守卫（第二轮隔离引入）
 * <ul>
 * <li>{@link #writeTid()}：写路径取租户。开关开启且无租户上下文时抛 TWTException， 与
 * {@code TenantFieldInterceptor}（BaseEntity INSERT 自动注入）行为一致—— 严禁静默把数据落到 default 租户</li>
 * <li>开关关闭时返回 null，调用方 XML 中 {@code <if test="tenantId != null">} 不命中， 列缺省由 DB
 * {@code NOT NULL DEFAULT 1} 兜底，回滚零侵入</li>
 * <li>供非 BaseEntity 实体 / @Param Map 形态的 INSERT 使用；BaseEntity 走拦截器无需本类</li>
 * </ul>
 */
@Component
public class TenantSupport {

	@Value("${tenant.enabled:false}")
	private boolean enabled;

	public boolean enabled() {
		return enabled;
	}

	/**
	 * 写路径取当前租户ID
	 * @throws TWTException 开关开启但请求未携带可识别的 X-Tenant-Code
	 */
	public Long writeTid() {
		Long tid = TenantContext.get();
		if (enabled && tid == null) {
			throw new TWTException("缺少租户上下文，无法写入");
		}
		return tid;
	}

}
