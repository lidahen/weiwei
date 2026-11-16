package com.twelvet.system.server.sms;

/**
 * 短信发送接口。真实服务商（阿里云/腾讯云）接入时新增实现并由 sms.provider 选择。
 *
 * @author twelvet
 */
public interface SmsSender {

	/**
	 * 发送验证码短信
	 * @param mobile 手机号
	 * @param code 验证码
	 */
	void send(String mobile, String code);

}
