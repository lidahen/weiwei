package com.twelvet.system.server.service;

import com.twelvet.system.api.domain.dto.LoginDTO;
import com.twelvet.system.api.domain.vo.LoginVO;

/**
 * @author twelvet
 * @Description: 登录服务
 */
public interface ILoginService {

	/**
	 * 登录
	 * @param loginDTO LoginDTO
	 */
	LoginVO login(LoginDTO loginDTO);

	/**
	 * 手机号验证码登录（首次登录自动建号）
	 * @param mobile 手机号
	 * @param code 验证码
	 * @return LoginVO
	 */
	LoginVO smsLogin(String mobile, String code);

	/**
	 * 退出登录
	 */
	void logout();

	/**
	 * 刷新令牌
	 * @param refreshToken 刷新令牌
	 * @return LoginVO
	 */
	LoginVO refreshToken(String refreshToken);

}
