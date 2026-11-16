package com.twelvet.exchange.server.mapper;

import com.twelvet.exchange.api.domain.ExchangeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 交换订单 Mapper（twelve_exchange_order，双边履约状态机）
 *
 * @author twelvet
 */
@Mapper
public interface ExchangeOrderMapper {

	int insert(ExchangeOrder order);

	ExchangeOrder selectById(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 我的交换订单（A/B 双视角，真分页；statusList 为空 = 全部）
	 */
	List<ExchangeOrder> selectMyOrdersPage(@Param("userId") Long userId, @Param("tenantId") Long tenantId,
			@Param("statusList") List<String> statusList, @Param("offset") long offset, @Param("size") long size);

	long countMyOrders(@Param("userId") Long userId, @Param("tenantId") Long tenantId,
			@Param("statusList") List<String> statusList);

	/**
	 * A 侧标记交付（条件 UPDATE：a_delivered_time 为空才允许写，天然幂等防双击）
	 */
	int deliverAsA(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("trackNo") String trackNo,
			@Param("tenantId") Long tenantId);

	/**
	 * B 侧标记交付（条件 UPDATE：b_delivered_time 为空才允许写）
	 */
	int deliverAsB(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("trackNo") String trackNo,
			@Param("tenantId") Long tenantId);

	/**
	 * 交付后推进状态：双方均交付 → pending_confirm，否则 → delivering
	 */
	int advanceDeliveryStatus(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * A 侧确认收货（条件 UPDATE：a_confirmed_time 为空才允许写）
	 */
	int confirmAsA(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * B 侧确认收货（条件 UPDATE：b_confirmed_time 为空才允许写）
	 */
	int confirmAsB(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

	/**
	 * 双方均确认 → completed（complete_time 回填）
	 */
	int advanceConfirmStatus(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 用户取消（仅双方均未交付；任一方可发起）
	 */
	int cancelByUser(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("reason") String reason,
			@Param("tenantId") Long tenantId);

	/**
	 * 超时取消（建单超 7 天未双交付）
	 */
	int timeoutCancel(@Param("orderId") Long orderId, @Param("reason") String reason, @Param("tenantId") Long tenantId);

	/**
	 * 发起纠纷（delivering/pending_confirm 可发起，物品保持 locked）
	 */
	int dispute(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("reason") String reason,
			@Param("tenantId") Long tenantId);

	/**
	 * 后台裁决：按现状完成（disputed → completed，确认时间未填的回填 NOW()）
	 */
	int forceComplete(@Param("orderId") Long orderId, @Param("result") String result, @Param("tenantId") Long tenantId);

	/**
	 * 后台裁决：取消并解锁（disputed → cancelled）
	 */
	int forceCancel(@Param("orderId") Long orderId, @Param("result") String result, @Param("tenantId") Long tenantId);

	/**
	 * 双交付后超 3 天未双确认 → 自动 completed（默认收货，确认时间回填 NOW()）
	 */
	int autoComplete(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 平台管理：分页查询（disputed 置顶，status 精确 + keyword 模糊 order_no）
	 */
	List<ExchangeOrder> selectAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

	/**
	 * 平台管理：分页总数
	 */
	long countAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword);

	/**
	 * 扫超时未双交付订单（created 超 7 天）
	 */
	List<ExchangeOrder> selectTimeoutCreated(@Param("threshold") LocalDateTime threshold,
			@Param("limit") Integer limit);

	/**
	 * 扫超时未双确认订单（pending_confirm 超 3 天）
	 */
	List<ExchangeOrder> selectTimeoutPendingConfirm(@Param("threshold") LocalDateTime threshold,
			@Param("limit") Integer limit);

	/**
	 * 看板统计
	 */
	Map<String, Object> selectStatistics(@Param("tenantId") Long tenantId);

}
