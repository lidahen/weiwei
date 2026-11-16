package com.twelvet.framework.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author twelvet
 * @Description: 安全配置
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

	@Autowired
	private AuthIgnoreConfig authIgnoreConfig;

	/**
	 * 注册拦截器
	 * @param registry InterceptorRegistry
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
		registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
			.addPathPatterns("/**")
			// 放行路径
			.excludePathPatterns(authIgnoreConfig.getUrls());
		// 登录租户绑定校验：请求租户上下文与登录租户不一致 → 400（必须在 SaInterceptor 之后）
		registry.addInterceptor(new TenantBindInterceptor()).addPathPatterns("/**");
	}

}
