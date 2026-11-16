package com.twelvet.framework.security.config;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.security.domain.LoginUser;
import com.twelvet.framework.security.utils.SecurityUtils;
import com.twelvet.system.api.domain.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录租户绑定校验（第四轮：租户级登录）
 * <ul>
 * <li>登录时 LoginUser 记住 sys_user.tenant_id；请求携带的租户上下文（子域名/X-Tenant-Code） 与之不一致 →
 * 400，关闭「default token + X-Tenant-Code: thu 换头读数据」的穿越</li>
 * <li>超管（userId=1）豁免：平台运营可跨租户操作，与数据范围/平台接口豁免同语义； 否则超管无法为其他租户创建管理员账号（死锁）</li>
 * <li>匿名请求（C 端浏览、登录本身）跳过：租户上下文仍生效，业务表照常过滤</li>
 * <li>无租户上下文跳过：平台全局视角（与商品列表兼容模式对称）</li>
 * <li>旧 token（本轮升级前签发，无 tenantId）跳过：重新登录后生效</li>
 * </ul>
 * 必须注册在 {@link SaTokenConfigure} 的 SaInterceptor 之后（依赖登录态已校验）。
 */
public class TenantBindInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Long ctx = TenantContext.get();
		if (ctx == null) {
			return true;
		}
		if (StpUtil.getLoginIdDefaultNull() == null) {
			return true;
		}
		LoginUser loginUser = SecurityUtils.getLoginUser();
		if (loginUser == null || loginUser.getTenantId() == null) {
			return true;
		}
		// 超管豁免：平台运营（userId=1）可跨租户操作，与数据范围/平台接口豁免同一语义。
		// 没有该豁免会死锁：超管无法再为其他租户创建管理员账号。
		if (SysUser.isAdmin(loginUser.getUserId())) {
			return true;
		}
		if (!ctx.equals(loginUser.getTenantId())) {
			throw new TWTException("当前登录租户与访问租户不一致，请重新登录");
		}
		return true;
	}

}
