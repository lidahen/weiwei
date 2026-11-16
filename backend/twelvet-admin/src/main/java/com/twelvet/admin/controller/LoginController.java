package com.twelvet.admin.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.api.domain.dto.LoginDTO;
import com.twelvet.system.api.domain.dto.SmsLoginDTO;
import com.twelvet.system.api.domain.dto.SmsSendDTO;
import com.twelvet.system.api.domain.vo.LoginVO;
import com.twelvet.system.server.service.ILoginService;
import com.twelvet.system.server.service.ISmsCodeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author twelvet
 * @Description: 登录控制器
 */
@RestController
@RequestMapping("/auth")
public class LoginController extends TWTController {

	private final ILoginService loginService;

	private final ISmsCodeService smsCodeService;

	public LoginController(ILoginService loginService, ISmsCodeService smsCodeService) {
		this.loginService = loginService;
		this.smsCodeService = smsCodeService;
	}

	/**
	 * 用户登录
	 * @param loginDTO 用户登录信息
	 * @return JsonResult<String>
	 */
	@SaIgnore
	@PostMapping("/oauth2/token")
	public JsonResult<LoginVO> login(@Validated @RequestBody LoginDTO loginDTO) {
		return JsonResult.success(loginService.login(loginDTO));
	}

	/**
	 * 刷新令牌
	 * @param refreshToken 刷新令牌
	 * @return JsonResult<LoginVO>
	 */
	@SaIgnore
	@PostMapping("/oauth2/refresh")
	public JsonResult<LoginVO> refreshToken(@RequestParam("refresh_token") String refreshToken) {
		try {
			return JsonResult.success(loginService.refreshToken(refreshToken));
		}
		catch (TWTException e) {
			// 前端以 code === 400 判定续签失败
			return JsonResult.error(400, e.getMessage());
		}
	}

	/**
	 * 退出登录
	 * @return JsonResult<String>
	 */
	@SaIgnore
	@DeleteMapping("/token/logout")
	public JsonResult<String> logout() {
		loginService.logout();
		return JsonResult.success();
	}

	/**
	 * 发送短信验证码
	 * @param body 手机号
	 * @return 验证码有效期（秒），mock 模式下额外返回调试验证码
	 */
	@SaIgnore
	@PostMapping("/sms/send")
	public JsonResult<Map<String, Object>> sendSmsCode(@Validated @RequestBody SmsSendDTO body) {
		String debugCode = smsCodeService.sendCode(body.getMobile());
		Map<String, Object> data = new HashMap<>(4);
		data.put("expireIn", smsCodeService.codeExpireSeconds());
		if (debugCode != null) {
			data.put("mockCode", debugCode);
		}
		return JsonResult.success(data);
	}

	/**
	 * 手机号验证码登录（首次登录自动建号）
	 * @param body 手机号 + 验证码
	 * @return LoginVO
	 */
	@SaIgnore
	@PostMapping("/sms/login")
	public JsonResult<LoginVO> smsLogin(@Validated @RequestBody SmsLoginDTO body) {
		return JsonResult.success(loginService.smsLogin(body.getMobile(), body.getCode()));
	}

}
