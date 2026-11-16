package com.twelvet.system.server.service;

/**
 * 短信验证码服务：发送与校验，Redis 存储 + 频控 + 失败计数。
 *
 * @author twelvet
 */
public interface ISmsCodeService {

	/**
	 * 发送验证码
	 * @param mobile 手机号
	 * @return 调试用验证码（mock 模式返回值，真实短信返回 null）
	 */
	String sendCode(String mobile);

	/**
	 * 校验验证码，失败抛 TWTException
	 * @param mobile 手机号
	 * @param code 用户输入的验证码
	 */
	void verify(String mobile, String code);

	/**
	 * 验证码有效期（秒）
	 * @return 验证码有效期，单位为秒
	 */
	int codeExpireSeconds();

}
