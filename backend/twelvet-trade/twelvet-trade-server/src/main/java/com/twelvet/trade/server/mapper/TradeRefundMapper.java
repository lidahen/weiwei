package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.TradeRefund;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单 Mapper
 * <p>
 * trade_refund 表没有 tenant_id 列，所有需要租户过滤的查询一律 {@code JOIN trade_order} 取租户。 进行中唯一性由生成列
 * active_flag + 唯一索引 uk_order_active 保证， 因此 {@link #insert} 的列清单<strong>不含
 * active_flag</strong>，也不得使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}（那会绕过唯一约束。
 * </p>
 *
 * @author twelvet
 */
public interface TradeRefundMapper {

	/**
	 * 新增退款单（列清单不含生成列 active_flag）
	 * @param refund 退款单
	 * @return 结果
	 */
	int insert(TradeRefund refund);

	/**
	 * 按ID查退款单（JOIN 订单以应用租户过滤）
	 * @param refundId 退款单ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 退款单
	 */
	TradeRefund selectById(@Param("refundId") Long refundId, @Param("tenantId") Long tenantId);

	/**
	 * 查订单当前进行中的退款单（终态不返回）
	 * @param orderId 订单ID
	 * @return 退款单，无则 null
	 */
	TradeRefund selectActiveByOrderId(@Param("orderId") Long orderId);

	/**
	 * 按用户角色分页查询退款单
	 * @param userId 用户ID
	 * @param role 角色(buyer 查买家，其他查卖家)
	 * @param status 状态过滤(为 null 不过滤)
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 退款单列表
	 */
	List<TradeRefund> selectList(@Param("userId") Long userId, @Param("role") String role,
			@Param("status") String status, @Param("tenantId") Long tenantId);

	/**
	 * 运营端全量分页查询
	 * @param status 状态过滤(为 null 不过滤)
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 退款单列表
	 */
	List<TradeRefund> selectAdminList(@Param("status") String status, @Param("overdue") Boolean overdue,
			@Param("tenantId") Long tenantId);

	/**
	 * 带前置状态守卫地推进退款单状态
	 * @param refundId 退款单ID
	 * @param fromStatus 期望的当前状态
	 * @param toStatus 目标状态
	 * @return 影响行数（0 表示状态已变更）
	 */
	int updateStatus(@Param("refundId") Long refundId, @Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus);

	/**
	 * 回填退货物流信息
	 * @param refundId 退款单ID
	 * @param company 物流公司
	 * @param returnNo 物流单号
	 * @return 结果
	 */
	int updateReturnLogistics(@Param("refundId") Long refundId, @Param("company") String company,
			@Param("returnNo") String returnNo);

	/**
	 * 快照卖家同意退货退款时给出的退货地址
	 * @param refundId 退款单ID
	 * @param returnAddress 退货地址快照
	 * @return 结果
	 */
	int updateReturnAddress(@Param("refundId") Long refundId, @Param("returnAddress") String returnAddress);

	/**
	 * 统计订单进行中的退款单数量
	 * @param orderId 订单ID
	 * @return 数量
	 */
	Long countActiveByOrderId(@Param("orderId") Long orderId);

	/**
	 * 扫描卖家超时未处理的退款单（pending_seller）
	 * @param before 时间上界
	 * @param limit 条数上限
	 * @return 退款单列表
	 */
	List<TradeRefund> selectSellerTimeout(@Param("before") LocalDateTime before, @Param("limit") int limit);

	/**
	 * 扫描卖家同意后买家超时未寄回的退款单（seller_agreed）
	 * <p>
	 * 刻意用 create_time 而非 update_time：用 update_time 会在每次流转后重置计时， 导致「拒绝→介入」的退款单永远扫不到。
	 * </p>
	 * @param before 时间上界
	 * @param limit 条数上限
	 * @return 退款单列表
	 */
	List<TradeRefund> selectReturnTimeout(@Param("before") LocalDateTime before, @Param("limit") int limit);

	/**
	 * 扫描买家已寄回但卖家超时未确认的退款单（buyer_returned）
	 * @param before 时间上界
	 * @param limit 条数上限
	 * @return 退款单列表
	 */
	List<TradeRefund> selectConfirmTimeout(@Param("before") LocalDateTime before, @Param("limit") int limit);

}
