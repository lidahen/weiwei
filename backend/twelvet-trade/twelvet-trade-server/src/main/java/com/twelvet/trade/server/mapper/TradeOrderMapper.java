package com.twelvet.trade.server.mapper;

import com.twelvet.trade.api.domain.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交易订单Mapper
 *
 * @author twelvet
 */
@Mapper
public interface TradeOrderMapper {

	/**
	 * 查询订单列表(按买家/卖家)
	 * @param orderNo 订单编号
	 * @param userId 用户ID(买家或卖家)
	 * @param role buyer或seller
	 * @param status 订单状态
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 订单列表
	 */
	List<TradeOrder> selectOrderList(@Param("userId") Long userId, @Param("role") String role,
			@Param("status") String status, @Param("tenantId") Long tenantId);

	/**
	 * 查询订单详情(含买家卖家信息)
	 * @param orderId 订单ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 订单详情
	 */
	TradeOrder selectOrderDetail(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

	/**
	 * 根据买家ID和订单ID查询订单
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 订单信息
	 */
	TradeOrder selectOrderByIdAndBuyerId(@Param("orderId") Long orderId, @Param("buyerId") Long buyerId,
			@Param("tenantId") Long tenantId);

	/**
	 * 根据订单编号查询订单
	 * @param orderNo 订单编号
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 订单信息
	 */
	TradeOrder selectOrderByNo(@Param("orderNo") String orderNo, @Param("tenantId") Long tenantId);

	/**
	 * 带前置状态集合守卫地迁移订单状态（退款流程专用）
	 * <p>
	 * 不复用 {@link #updateOrderStatus}：那个硬编码 {@code AND status='unpaid'}，是支付回调监听器专用。
	 * </p>
	 * @param orderId 订单ID
	 * @param toStatus 目标状态
	 * @param fromStatuses 允许的当前状态集合
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数（0 表示状态已变更）
	 */
	int updateOrderStatusIfIn(@Param("orderId") Long orderId, @Param("toStatus") String toStatus,
			@Param("fromStatuses") List<String> fromStatuses, @Param("tenantId") Long tenantId);

	/**
	 * 新增订单
	 * @param order 订单信息
	 * @return 结果
	 */
	int insertOrder(TradeOrder order);

	/**
	 * 更新订单状态
	 * @param orderId 订单ID
	 * @param status 状态
	 * @param timestamp 时间字段
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果
	 */
	int updateOrderStatus(@Param("orderId") Long orderId, @Param("status") String status,
			@Param("timestamp") String timestamp, @Param("tenantId") Long tenantId);

	/**
	 * 确认收货
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果
	 */
	int confirmReceipt(@Param("orderId") Long orderId, @Param("buyerId") Long buyerId,
			@Param("tenantId") Long tenantId);

	/**
	 * 卖家发货
	 * @param orderId 订单ID
	 * @param sellerId 卖家ID
	 * @param autoConfirmDays 自动确认收货天数（发货时间 + N 天写 auto_confirm_at）
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果
	 */
	int shipOrder(@Param("orderId") Long orderId, @Param("sellerId") Long sellerId,
			@Param("autoConfirmDays") int autoConfirmDays, @Param("tenantId") Long tenantId);

	/**
	 * 买家取消未支付订单（原子操作：仅 status='unpaid' 可取消）
	 * <p>
	 * WHERE status='unpaid' 保证与超时关单任务并发时只有一方 rows>0， 只有 rows=1 的一方才允许释放库存（防双倍回补）。
	 * </p>
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 结果，0 表示订单不存在/非本人/状态已流转
	 */
	int cancelOrder(@Param("orderId") Long orderId, @Param("buyerId") Long buyerId, @Param("tenantId") Long tenantId);

	/**
	 * 扫描支付过期未付订单（超时关单任务专用）
	 * <p>
	 * quartz 后台任务<b>无租户上下文</b>，故本查询<b>故意不带租户参数</b>—— 全租户扫描（expire_at 由下单链路写入）。
	 * </p>
	 * @param limit 单次扫描上限
	 * @return 过期未支付订单列表
	 */
	List<TradeOrder> selectExpiredUnpaidOrders(@Param("limit") int limit);

	/**
	 * 扫描到达自动确认收货时间的已发货订单（自动确认任务专用）
	 * <p>
	 * quartz 后台任务<b>无租户上下文</b>，故本查询<b>故意不带租户参数</b>—— 全租户扫描（auto_confirm_at 由发货链路写入）。
	 * </p>
	 * @param limit 单次扫描上限
	 * @return 到期已发货订单列表
	 */
	List<TradeOrder> selectAutoConfirmDueOrders(@Param("limit") int limit);

	/**
	 * 超时关单（原子操作：仅 status='unpaid' 可关）
	 * <p>
	 * 与买家主动取消（{@link #cancelOrder}）并发时双方都走 WHERE status='unpaid' 原子 UPDATE， 只有一方
	 * rows=1——Java 层只有 rows=1 才释放库存（防双倍回补）。 与
	 * {@link #updateOrderStatus}（PaySuccessEventListener 专用）互不复用：语义不同，前置状态不同。
	 * </p>
	 * @param orderId 订单ID
	 * @return 结果，0 表示订单不存在/状态已流转（如已被买家取消或已支付）
	 */
	int closeOrder(@Param("orderId") Long orderId);

	/**
	 * 发货超时自动确认收货（原子操作：仅 status='shipped' 可确认）
	 * <p>
	 * 与买家主动确认收货（{@link #confirmReceipt}）并发时双方都走 WHERE status='shipped' 原子 UPDATE， 只有一方
	 * rows=1——Java 层只有 rows=1 才标记商品 sold。
	 * </p>
	 * @param orderId 订单ID
	 * @return 结果，0 表示订单不存在/状态已流转（如买家已主动确认）
	 */
	int autoConfirmOrder(@Param("orderId") Long orderId);

	/**
	 * 查询用户所属租户ID
	 * <p>
	 * 用于内部接口下单时**建立**租户上下文（引导查询）：此时上下文尚不存在，故本查询 **故意不带租户过滤**，以 {@code sys_user.tenant_id}
	 * 为唯一权威来源，绝不信任调用方传入的租户标识。
	 * </p>
	 * @param userId 用户ID
	 * @return 租户ID；用户不存在时为 null
	 */
	Long selectUserTenantId(@Param("userId") Long userId);

	/**
	 * 查询订单统计(平台运营视角，跨租户)
	 * @return 结果
	 */
	java.util.Map<String, Object> selectOrderStatistics();

}
