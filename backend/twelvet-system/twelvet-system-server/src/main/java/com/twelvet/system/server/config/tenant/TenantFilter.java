package com.twelvet.system.server.config.tenant;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.system.api.domain.SysTenant;
import com.twelvet.system.api.mapper.SysTenantMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 租户识别 Filter（MVP + P2 子域名）
 * <ul>
 * <li>解析顺序：Host 子域名（{@code <sub>.<tenant.base-domain>}）→ HTTP Header
 * {@code X-Tenant-Code} → 不设租户（兼容模式）</li>
 * <li>子域名优先于 header：域名是更强的租户信号（租户子域名站点的管理后台同样被租户域圈定）</li>
 * <li>保留子域名（www/api/admin 等）不作为租户，跳过子域名解析走 header/兼容</li>
 * <li>识别后放入 {@link TenantContext}，请求结束清理</li>
 * <li>开关 {@code tenant.enabled=false} 时直接放行，零侵入回滚</li>
 * <li>未识别租户返回 404（不返回 403，避免泄露存在性）</li>
 * </ul>
 */
@Component
public class TenantFilter extends GenericFilterBean implements Ordered {

	private static final String HEADER = "X-Tenant-Code";

	/** 保留子域名：不作为租户编码 */
	private static final List<String> RESERVED = List.of("www", "api", "admin", "static", "docs", "console");

	private final SysTenantMapper sysTenantMapper;

	@Value("${tenant.enabled:false}")
	private boolean enabled;

	/** 子域名基准域名（如 ershou.com / localhost）；空 = 关闭子域名解析 */
	@Value("${tenant.base-domain:}")
	private String baseDomain;

	public TenantFilter(SysTenantMapper sysTenantMapper) {
		this.sysTenantMapper = sysTenantMapper;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!enabled) {
			chain.doFilter(request, response);
			return;
		}
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;
		String code = resolveBySubdomain(req);
		if (code == null) {
			code = req.getHeader(HEADER);
		}
		try {
			if (code != null && !code.isBlank()) {
				SysTenant tenant = sysTenantMapper.selectByCode(code);
				if (tenant == null || !"0".equals(tenant.getStatus())) {
					// 用 setStatus 不用 sendError：sendError 会触发 Tomcat 重走拦截器，
					// 在 Filter 中断时 SaToken ThreadLocal 上下文未初始化会抛 500
					res.setStatus(HttpServletResponse.SC_NOT_FOUND);
					res.setContentType("application/json;charset=UTF-8");
					res.getWriter().write("{\"code\":404,\"msg\":\"tenant not found\"}");
					return;
				}
				TenantContext.set(tenant.getId());
			}
			chain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
		}
	}

	/**
	 * 从 Host 头解析子域名租户：{@code <sub>.<baseDomain>[:port]} → sub
	 * <p>
	 * 非 baseDomain 的主机（如 localhost 直连、IP 访问）返回 null，走 header/兼容模式。
	 * </p>
	 */
	private String resolveBySubdomain(HttpServletRequest req) {
		if (baseDomain == null || baseDomain.isBlank()) {
			return null;
		}
		String host = req.getHeader("Host");
		if (host == null || host.isBlank()) {
			return null;
		}
		// 去端口（IPv6 字面量不含子域名场景，直接按最后一个冒号切）
		int colon = host.lastIndexOf(':');
		if (colon > 0 && !host.substring(colon + 1).contains(".")) {
			host = host.substring(0, colon);
		}
		host = host.toLowerCase(Locale.ROOT);
		String suffix = "." + baseDomain.toLowerCase(Locale.ROOT);
		if (!host.endsWith(suffix)) {
			return null;
		}
		String sub = host.substring(0, host.length() - suffix.length());
		if (sub.isBlank() || sub.contains(".") || RESERVED.contains(sub)) {
			return null;
		}
		return sub;
	}

	/**
	 * 高优先级：在 SaInterceptor 之前执行（Filter 在 Servlet 容器层先于 Spring MVC 拦截器）
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

}
