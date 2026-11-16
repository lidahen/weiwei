package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 资金流水 Mapper
 * <p>
 * trade_pay_flow 表没有 tenant_id 列；type 写入值为 pay / refund。
 * </p>
 *
 * @author twelvet
 */
public interface TradePayFlowMapper {

	/**
	 * 写一条资金流水
	 * @param orderId 订单ID
	 * @param type 类型(pay/refund)
	 * @param amount 金额
	 * @param channel 支付渠道
	 * @param thirdTransNo 三方交易号
	 * @return 结果
	 */
	int insert(@Param("orderId") Long orderId, @Param("type") String type, @Param("amount") BigDecimal amount,
			@Param("channel") String channel, @Param("thirdTransNo") String thirdTransNo);

}
