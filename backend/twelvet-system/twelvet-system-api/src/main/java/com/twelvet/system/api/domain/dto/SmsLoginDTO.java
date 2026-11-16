package com.twelvet.system.api.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author twelvet
 * @Description: 手机号验证码登录DTO
 */
@Schema(description = "手机号验证码登录DTO")
public class SmsLoginDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 手机号
	 */
	@Schema(description = "手机号")
	@NotBlank(message = "手机号不能为空")
	@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
	private String mobile;

	/**
	 * 验证码
	 */
	@Schema(description = "验证码")
	@NotBlank(message = "验证码不能为空")
	private String code;

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	@Override
	public String toString() {
		return "SmsLoginDTO{" + "mobile='" + mobile + '\'' + ", code='" + code + '\'' + '}';
	}

}
