package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 支付单反查 Mapper（供退款定位原始支付记录）
 * <p>
 * <strong>多态外键撞号警告</strong>：trade_pay.order_id 被商品订单 / 跑腿任务 / 租户续费共用， 本机实测 17–21
 * 号撞号（trade_pay 17–21 是 1.50/2.00/4.00 的跑腿已付单， trade_order 17–21 是 2499/507/507/499/1511
 * 的商品单）。 调用方<strong>禁止</strong>照抄单条件 order_id 反查，必须用 pay_user_id + 金额双重校验， 候选不唯一时转人工。
 * </p>
 *
 * @author twelvet
 */
public interface TradePayLookupMapper {

	/**
	 * 按订单ID取全部已支付候选（调用方必须再做身份与金额校验）
	 * @param orderId 订单ID
	 * @return 每行含 pay_id / out_trade_no / pay_amount / pay_user_id / pay_channel
	 */
	List<Map<String, Object>> selectPaidByOrderId(@Param("orderId") Long orderId);

}
