package com.twelvet.payment.api.service;

import java.util.Map;

/**
 * 租户套餐续费支付服务接口（跨模块共享）
 *
 * @author twelvet
 */
public interface ITenantRenewPayService {

	/**
	 * 创建续费支付单并返回二维码
	 * @param tenantCode 租户编码
	 * @param planCode 套餐编码
	 * @param months 续费月数
	 * @param channel 支付渠道
	 * @return 支付参数
	 */
	Map<String, Object> createOrder(String tenantCode, String planCode, int months, String channel);

	/**
	 * 查询支付/续费状态
	 * @param payNo 支付单号
	 * @return 状态信息
	 */
	Map<String, Object> status(String payNo);

	/**
	 * 模拟支付（dev 演示闭环；生产 billing.pay.mock=false 时不可用）
	 * @param payNo 支付单号
	 */
	void mockPay(String payNo);

	/**
	 * 履约：标 trade_pay 已付 + 抢占式标续费单已付（幂等闸门）+ 套餐落库
	 * @param outTradeNo 商户支付流水号
	 * @return true=本次执行了续费；false=已被处理过
	 */
	boolean fulfill(String outTradeNo);

}
