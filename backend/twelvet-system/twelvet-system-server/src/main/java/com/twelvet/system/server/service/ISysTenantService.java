package com.twelvet.system.server.service;

import com.twelvet.system.api.domain.vo.SysTenantPlanVO;

/**
 * 租户服务（商业化 P0：套餐查询 + 续费/升级）
 */
public interface ISysTenantService {

	/**
	 * 查询租户套餐详情（含已用商品数）
	 * @param tenantCode 租户编码
	 * @return 套餐详情 VO
	 */
	SysTenantPlanVO getPlanInfo(String tenantCode);

	/**
	 * 续费/升级套餐
	 * @param tenantCode 租户编码
	 * @param planCode 套餐编码（free/basic/pro）
	 * @param months 续费月数（必须 &gt; 0）
	 */
	void renew(String tenantCode, String planCode, int months);

}
