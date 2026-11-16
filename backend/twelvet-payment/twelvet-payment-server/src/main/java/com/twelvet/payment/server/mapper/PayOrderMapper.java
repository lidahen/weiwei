package com.twelvet.payment.server.mapper;

import com.twelvet.payment.api.domain.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付单 Mapper（对应表 trade_pay）
 * <p>
 * 跨模块共享的支付单由 twelvet-payment-server 统一维护。 trade / runner / tenant-renew 业务侧不要直接写本表，应通过
 * IPayOrderService 调用。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface PayOrderMapper {

	/**
	 * 插入支付单
	 * <p>
	 * tenantId 传 null 时 SQL 不写该列（DB DEFAULT 1 兜底，对应 tenant.enabled=false 回滚零侵入）
	 * </p>
	 * @param orderId 业务订单ID（租户续费为 null）
	 * @param payUserId 支付单发起人（收银台创建者；租户续费/存量兼容路径传 null）
	 * @param outTradeNo 商户支付流水号
	 * @param channel 支付渠道
	 * @param amount 金额
	 * @param bizType 业务类型：trade/runner（租户续费走 insertRenewPayOrder）
	 * @param tid 租户ID（可为 null）
	 * @return 插入行数
	 */
	int insertPayOrder(@Param("orderId") Long orderId, @Param("payUserId") Long payUserId,
			@Param("outTradeNo") String outTradeNo, @Param("channel") String channel,
			@Param("amount") BigDecimal amount, @Param("bizType") String bizType, @Param("tid") Long tid);

	/**
	 * 按 out_trade_no 更新二维码 URL 与商户流水号
	 * <p>
	 * 用于 createPayOrder 第二步：网关返回 qrCode 后回写
	 * </p>
	 */
	int updateQrCode(@Param("orderId") Long orderId, @Param("outTradeNo") String outTradeNo,
			@Param("qrCodeUrl") String qrCodeUrl, @Param("channel") String channel);

	/**
	 * 支付回调置为 paid（幂等：WHERE status != 'paid'）
	 * @return 影响行数；0 表示已处理或不存在
	 */
	int markPaidByOutTradeNo(@Param("outTradeNo") String outTradeNo, @Param("paidAt") LocalDateTime paidAt);

	/**
	 * 退款：置为 refunded
	 */
	int markRefunded(@Param("outTradeNo") String outTradeNo);

	/**
	 * 按 out_trade_no 查询（租户上下文过滤）
	 * @param tid 租户ID；为 null 不过滤
	 * @return 支付单全字段（含 status, paid_at 等）
	 */
	Map<String, Object> selectByOutTradeNo(@Param("outTradeNo") String outTradeNo, @Param("tid") Long tid);

	/**
	 * 按 out_trade_no 查询（仅租户续费场景使用，写入空 order_id 行）
	 * <p>
	 * 与 {@link #selectByOutTradeNo} 的差别：不走租户过滤
	 * </p>
	 * @return 含 order_id / pay_channel / pay_amount
	 */
	Map<String, Object> selectOrderInfoByOutTradeNo(@Param("outTradeNo") String outTradeNo);

	/**
	 * 租户续费场景：插入无 order_id 的支付单（order_id=NULL）
	 */
	int insertRenewPayOrder(@Param("outTradeNo") String outTradeNo, @Param("channel") String channel,
			@Param("amount") BigDecimal amount, @Param("qrCodeUrl") String qrCodeUrl, @Param("tid") Long tid);

	/**
	 * 实体装载（保留 resultMap 用，便于 PayOrder 形态调用方使用）
	 */
	PayOrder selectEntityByOutTradeNo(@Param("outTradeNo") String outTradeNo);

	/**
	 * 通用 UPDATE：标记 trade_pay 已付（租户续费 fulfillment 路径，幂等）
	 */
	int markRenewPaid(@Param("outTradeNo") String outTradeNo);

}
