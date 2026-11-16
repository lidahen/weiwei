package com.twelvet.system.api.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 续费/升级套餐请求
 */
@Schema(description = "续费/升级套餐请求")
public class RenewReq {

	@NotBlank
	@Schema(description = "套餐编码：free/basic/pro", example = "pro")
	private String planCode;

	@Min(1)
	@Schema(description = "续费月数", example = "12")
	private Integer months;

	public String getPlanCode() {
		return planCode;
	}

	public void setPlanCode(String planCode) {
		this.planCode = planCode;
	}

	public Integer getMonths() {
		return months;
	}

	public void setMonths(Integer months) {
		this.months = months;
	}

}
