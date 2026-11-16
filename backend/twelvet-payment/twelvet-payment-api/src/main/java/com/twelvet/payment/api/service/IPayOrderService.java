package com.twelvet.payment.api.service;

import com.twelvet.payment.api.dto.PayCallbackDTO;
import com.twelvet.payment.api.dto.PayRequestDTO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付单服务对外接口（跨模块共享）
 * <p>
 * trade / runner / tenant-renew 均通过此接口调用支付能力， 不感知支付网关细节。
 *
 * @author twelvet
 */
public interface IPayOrderService {

	/** 生成商户支付流水号: PAY + yyyyMMddHHmmss + 4位随机数 */
	String generatePayNo();

	/**
	 * 创建支付单并调用网关发起支付
	 * @param request 支付请求参数
	 * @return 支付参数（payNo, payUrl, qrCode, channel, amount, orderId, orderNo, extra）
	 */
	Map<String, Object> createPayOrder(PayRequestDTO request);

	/**
	 * 查询支付单状态（通过 out_trade_no）
	 * @param payNo 支付单号（= out_trade_no）
	 * @return 支付单信息，含 status / paid 字段；不存在时返回 null
	 */
	Map<String, Object> queryPayStatus(String payNo);

	/**
	 * 处理支付回调（幂等）
	 * <p>
	 * 1. 更新 trade_pay 状态为 paid 2. 发布 PaySuccessEvent，由业务模块监听自行处理后续逻辑
	 * </p>
	 * @param callback 回调数据
	 */
	void handlePaySuccess(PayCallbackDTO callback);

	/**
	 * 发起退款
	 * @param outTradeNo 商户支付流水号
	 * @param amount 退款金额
	 * @param reason 退款原因
	 * @return 退款是否成功
	 */
	boolean refund(String outTradeNo, BigDecimal amount, String reason);

	/**
	 * 模拟支付成功（仅 dev 模拟模式可用，billing.pay.mock=true）
	 * <p>
	 * 跳过网关，直接走 handlePaySuccess 履约链路（标记支付单已付 + 发布 PaySuccessEvent）。 生产环境 mock 关闭时抛异常。
	 * </p>
	 * @param payNo 支付单号（= out_trade_no）
	 * @param userId 当前登录用户（归属校验：仅支付单发起人可模拟支付；传 null 或与发起人不符即拒绝， 存量单 pay_user_id 为 NULL
	 * 时放行以兼容 dev 旧数据）
	 * @return 是否完成履约（false = 该支付单已支付过，幂等跳过）
	 */
	boolean mockPay(String payNo, Long userId);

	/** 当前是否开启模拟支付（billing.pay.mock） */
	boolean isMockEnabled();

}
