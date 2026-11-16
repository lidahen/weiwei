package com.twelvet.system.api.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author twelvet
 * @Description: 发送短信验证码DTO
 */
@Schema(description = "发送短信验证码DTO")
public class SmsSendDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 手机号
	 */
	@Schema(description = "手机号")
	@NotBlank(message = "手机号不能为空")
	@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
	private String mobile;

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	@Override
	public String toString() {
		return "SmsSendDTO{" + "mobile='" + mobile + '\'' + '}';
	}

}
