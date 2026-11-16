package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.domain.TradeRefund;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 结算服务
 *
 * @author twelvet
 */
public interface ISettlementService {

	/**
	 * 支付成功后创建 holding 结算单（uk_order_id 幂等）
	 * @param order 已支付订单（租户取订单，回调线程无租户上下文）
	 * @param channel 支付渠道，null 时按 mock 记
	 */
	void createOnPaySuccess(TradeOrder order, String channel);

	/**
	 * 扫描售后期届满的 holding 单并推进 pending_settle
	 * @param limit 批量上限
	 * @return 成功推进数
	 */
	int scanDueHoldings(int limit);

	/**
	 * 扫描可打款结算单并提交渠道，成功入账
	 * @param limit 批量上限
	 * @return 成功 settled 数
	 */
	int payoutBatch(int limit);

	/**
	 * 退款成功后的结算互斥：窗口内关闭结算单；已结算则按账本扣回并冻结负余额
	 * @param refund 退款单
	 * @param order 订单（租户/卖家来源）
	 * @param refundedAmount 实际退款金额（部分退款时小于 refund.amount，扣回以它为准）
	 */
	void onRefundSuccess(TradeRefund refund, TradeOrder order, BigDecimal refundedAmount);

	/**
	 * 运营结算单分页
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 分页结果
	 */
	TableDataInfo<SettlementOrder> adminPage(String status, Long tenantId);

	/**
	 * 运营结算单详情
	 * @param settlementId 结算单ID
	 * @param tenantId 租户ID，null 不过滤
	 * @return 结算单字段
	 */
	Map<String, Object> adminDetail(Long settlementId, Long tenantId);

}