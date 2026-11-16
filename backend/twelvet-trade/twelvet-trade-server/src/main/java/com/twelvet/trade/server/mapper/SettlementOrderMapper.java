package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.SettlementOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 结算单 Mapper
 * <p>
 * 所有状态推进语句都带前置状态守卫，调用方必须检查返回行数：0 行 = 并发变更，跳过而不是重试。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface SettlementOrderMapper {

	/**
	 * 幂等创建结算单（uk_order_id 命中时返回 0）
	 * @param settlement 结算单
	 * @return 影响行数
	 */
	int insertIgnore(SettlementOrder settlement);

	/**
	 * 按订单查结算单
	 * @param orderId 订单ID
	 * @return 结算单，不存在为 null
	 */
	SettlementOrder selectByOrderId(@Param("orderId") Long orderId);

	/**
	 * 按结算单ID查（带租户过滤）
	 * @param settlementId 结算单ID
	 * @param tenantId 租户ID，null 不过滤
	 * @return 结算单
	 */
	SettlementOrder selectById(@Param("settlementId") Long settlementId, @Param("tenantId") Long tenantId);

	/**
	 * 扫描售后期届满的 holding 单（订单 done、无进行中退款、卖家已实名）
	 * @param limit 批量上限
	 * @param holdDays 售后期天数
	 * @return 候选结算单
	 */
	List<SettlementOrder> selectDueHoldings(@Param("limit") int limit, @Param("holdDays") int holdDays);

	/**
	 * 推进 holding → pending_settle（守卫：holding、订单 done、无进行中退款）
	 * @param settlementId 结算单ID
	 * @param holdDays 售后期天数
	 * @return 影响行数
	 */
	int markPending(@Param("settlementId") Long settlementId, @Param("holdDays") int holdDays);

	/**
	 * 扫描可打款结算单（卖家已实名、未冻结、失败次数未超限）
	 * @param limit 批量上限
	 * @param maxFailCount 失败次数上限
	 * @return 候选结算单
	 */
	List<SettlementOrder> selectPendingPayout(@Param("limit") int limit, @Param("maxFailCount") int maxFailCount);

	/**
	 * 推进 pending_settle → settled
	 * @param settlementId 结算单ID
	 * @param channel 渠道
	 * @param channelTransNo 渠道流水号
	 * @return 影响行数
	 */
	int markSettled(@Param("settlementId") Long settlementId, @Param("channel") String channel,
			@Param("channelTransNo") String channelTransNo);

	/**
	 * 窗口内退款关闭结算单（holding/pending_settle → closed）
	 * @param orderId 订单ID
	 * @param refundId 退款单ID
	 * @return 影响行数
	 */
	int markClosed(@Param("orderId") Long orderId, @Param("refundId") Long refundId);

	/**
	 * 结算后退款标记扣回（settled/withdrawn → clawback，保留 settled 事实）
	 * @param settlementId 结算单ID
	 * @param refundId 退款单ID
	 * @return 影响行数
	 */
	int markClawback(@Param("settlementId") Long settlementId, @Param("refundId") Long refundId);

	/**
	 * 打款失败次数 +1
	 * @param settlementId 结算单ID
	 * @return 影响行数
	 */
	int incrementFailCount(@Param("settlementId") Long settlementId);

	/**
	 * 运营分页（PageHelper 在 service 层启动）
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 结算单列表
	 */
	List<SettlementOrder> selectAdminPage(@Param("status") String status, @Param("tenantId") Long tenantId);

	/**
	 * 对账：已支付订单无结算单
	 * @param bizDate 业务日
	 * @return 差异行（orderId/sellerId/tenantId/channelAmount）
	 */
	List<Map<String, Object>> selectMissingSettlement(@Param("bizDate") LocalDate bizDate);

	/**
	 * 对账：有结算单但无已支付流水
	 * @param bizDate 业务日
	 * @return 差异行
	 */
	List<Map<String, Object>> selectPhantomSettlement(@Param("bizDate") LocalDate bizDate);

	/**
	 * 对账：结算金额与实付金额不一致
	 * @param bizDate 业务日
	 * @return 差异行
	 */
	List<Map<String, Object>> selectAmountMismatch(@Param("bizDate") LocalDate bizDate);

	/**
	 * 对账：退款成功但结算单未 closed/clawback
	 * @param bizDate 业务日
	 * @return 差异行
	 */
	List<Map<String, Object>> selectRefundStatusMismatch(@Param("bizDate") LocalDate bizDate);

	/**
	 * 对账：同一订单多张结算单（uk_order_id 下应为 0 行，保留作代码缺陷哨兵）
	 * @param bizDate 业务日
	 * @return 差异行
	 */
	List<Map<String, Object>> selectDuplicateSettlement(@Param("bizDate") LocalDate bizDate);

}