package com.twelvet.system.server.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 短信发送器：不真正下发短信，仅打印日志。
 * <p>
 * 当 {@code sms.provider} 缺省或为 {@code mock} 时生效；真实服务商接入后新增实现并切换配置即可。
 * </p>
 *
 * @author twelvet
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

	/**
	 * 发送验证码短信（仅打日志）
	 * @param mobile 手机号
	 * @param code 验证码
	 */
	@Override
	public void send(String mobile, String code) {
		log.info("【Mock 短信】{} 验证码：{}", mobile, code);
	}

}
