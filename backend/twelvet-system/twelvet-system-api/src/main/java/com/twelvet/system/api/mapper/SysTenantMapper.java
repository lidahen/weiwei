package com.twelvet.system.api.mapper;

import com.twelvet.system.api.domain.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户 Mapper（商业化 P0：放在 system-api 供 product/trade/feed 等模块共用，避免跨 server 依赖）
 */
@Mapper
public interface SysTenantMapper {

	/**
	 * 根据 tenantCode 查询租户
	 * @param tenantCode 租户编码
	 * @return 租户
	 */
	SysTenant selectByCode(@Param("tenantCode") String tenantCode);

	/**
	 * 根据主键查询租户
	 * @param id 租户ID
	 * @return 租户
	 */
	SysTenant selectById(@Param("id") Long id);

	/**
	 * 更新套餐（续费/升级）
	 * @param tenant 租户（带 id + planCode + expireAt + quotaProductMax）
	 * @return 影响行数
	 */
	int updatePlan(SysTenant tenant);

	/**
	 * 统计某租户当前商品数（商业化 P0：商品配额检查）
	 * @param tenantId 租户ID
	 * @return 商品数
	 */
	int countProducts(@Param("tenantId") Long tenantId);

}
