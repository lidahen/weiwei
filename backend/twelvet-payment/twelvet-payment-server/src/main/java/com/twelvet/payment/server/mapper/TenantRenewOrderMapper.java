package com.twelvet.payment.server.mapper;

import com.twelvet.payment.server.domain.TenantRenewOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户套餐续费支付单 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface TenantRenewOrderMapper {

	int insert(TenantRenewOrder order);

	TenantRenewOrder selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

	/**
	 * 抢占式标记支付（unpaid→paid），返回 0 表示已被处理（幂等）
	 */
	int markPaid(@Param("outTradeNo") String outTradeNo);

}
