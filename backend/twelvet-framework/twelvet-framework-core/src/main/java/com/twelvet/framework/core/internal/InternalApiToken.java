package com.twelvet.framework.core.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口调用令牌校验器（服务间调用，例如 Python ai-service → Java）
 * <p>
 * 设计要点：
 * <ul>
 * <li><b>失败关闭</b>：未配置令牌时一律拒绝，绝不出现"未配置即放行"</li>
 * <li><b>常量时间比较</b>：用 {@link MessageDigest#isEqual} 避免按响应耗时逐字节猜测令牌</li>
 * <li><b>不落库、不记日志</b>：令牌只能经环境变量注入（{@code INTERNAL_API_TOKEN}）， 任何日志/异常信息中都不出现令牌值</li>
 * </ul>
 * 背景：AI Agent 原先直接 INSERT trade_order（列清单无 tenant_id），会落到 DB 的 {@code NOT NULL DEFAULT 1}
 * 上；而写入租户本应由 Java 侧的 TenantContext 决定。 改为调用内部端点后，需要一个服务间鉴权手段，本类即为该手段。
 * </p>
 *
 * @author twelvet
 */
@Component
public class InternalApiToken {

	private static final Logger log = LoggerFactory.getLogger(InternalApiToken.class);

	/**
	 * 请求头名称（调用方需在此头中携带令牌）
	 */
	public static final String HEADER = "X-Internal-Token";

	private final String expected;

	public InternalApiToken(@Value("${twelvet.internal.token:}") String expected) {
		this.expected = expected == null ? "" : expected.trim();
	}

	/**
	 * 是否已配置令牌
	 */
	public boolean configured() {
		return StringUtils.hasText(expected);
	}

	/**
	 * 校验调用方提供的令牌
	 * @param presented 请求头中携带的令牌，可为 null
	 * @return 是否通过
	 */
	public boolean matches(String presented) {
		if (!configured()) {
			// 失败关闭：宁可内部接口不可用，也不能在未配置密钥的情况下对外开放
			log.error("内部接口被调用，但 twelvet.internal.token 未配置，已按失败关闭策略拒绝；" + "请通过环境变量 INTERNAL_API_TOKEN 注入");
			return false;
		}
		if (!StringUtils.hasText(presented)) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				presented.trim().getBytes(StandardCharsets.UTF_8));
	}

}
