package com.twelvet.system.server.config.tenant;

import com.twelvet.framework.core.application.domain.BaseEntity;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 租户字段拦截器（仅处理 INSERT）
 * <ul>
 * <li>INSERT BaseEntity 时，自动从 {@link TenantContext} 取值写入 tenantId 字段</li>
 * <li>租户开关关闭时直接放行（零侵入）</li>
 * <li>非 BaseEntity 参数（如 update 只传 id）不受影响</li>
 * <li>SELECT / UPDATE / DELETE 的 tenant_id 过滤由 XML 显式声明（MyBatis 非 Plus 模式限制）</li>
 * </ul>
 */
@Component
@Intercepts(@Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }))
public class TenantFieldInterceptor implements Interceptor {

	@Value("${tenant.enabled:false}")
	private boolean enabled;

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		if (!enabled) {
			return invocation.proceed();
		}
		Object[] args = invocation.getArgs();
		MappedStatement ms = (MappedStatement) args[0];
		if (ms.getSqlCommandType() != SqlCommandType.INSERT) {
			return invocation.proceed();
		}
		Object param = args[1];
		if (param instanceof BaseEntity baseEntity) {
			if (baseEntity.getTenantId() == null) {
				Long tid = TenantContext.get();
				if (tid == null) {
					// 仅当 SQL 真正写入 tenant_id 列时才要求租户上下文：
					// 平台级审计表（sys_login_info / sys_operation_log）无 tenant_id 列，
					// 其 INSERT 走 @Async 线程本就无上下文，不应因此中断审计写入
					String sql = ms.getBoundSql(param).getSql();
					if (sql != null && sql.contains("tenant_id")) {
						throw new TWTException("缺少租户上下文，无法写入");
					}
					return invocation.proceed();
				}
				baseEntity.setTenantId(tid);
			}
		}
		return invocation.proceed();
	}

	@Override
	public Object plugin(Object target) {
		return org.apache.ibatis.plugin.Plugin.wrap(target, this);
	}

}
