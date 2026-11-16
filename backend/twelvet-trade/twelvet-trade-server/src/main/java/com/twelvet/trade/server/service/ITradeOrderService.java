package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.api.domain.TradeOrder;

import java.util.List;
import java.util.Map;

/**
 * 交易订单Service接口
 *
 * @author twelvet
 */
public interface ITradeOrderService {

	/**
	 * 查询订单列表(按买家/卖家)
	 * @param userId 用户ID
	 * @param role buyer或seller
	 * @param status 订单状态
	 * @return 订单分页列表
	 */
	TableDataInfo<TradeOrder> selectOrderList(Long userId, String role, String status);

	/**
	 * 查询订单详情(含物流)
	 * <p>
	 * 仅订单双方（买家/卖家）可查；非参与方与"订单不存在"返回同样的空结果， 避免通过响应差异探测订单是否存在。
	 * </p>
	 * @param orderId 订单ID
	 * @param viewerId 查看者ID（当前登录用户）
	 * @return 订单详情，无权或不存在时为空 Map
	 */
	Map<String, Object> selectOrderDetail(Long orderId, Long viewerId);

	/**
	 * 创建订单（二手交易下单主链路）
	 * <p>
	 * 校验顺序：商品存在且 onsale → 自购拦截 → 原子扣库存（rows=0 即拒绝，防超卖） → express 时校验收货地址归属。商品与地址快照用
	 * Jackson 序列化落库。
	 * </p>
	 * @param buyerId 买家ID
	 * @param productId 商品ID
	 * @param addressId 收货地址ID（express 必填；self_pickup 可空）
	 * @param buyerRemark 买家留言（可空）
	 * @param deliveryType 配送方式（可空：入参为空时按商品 shippingMethod 推导）
	 * @return 订单信息
	 */
	Map<String, Object> createOrder(Long buyerId, Long productId, Long addressId, String buyerRemark,
			String deliveryType);

	/**
	 * 内部接口下单（服务间调用，如 Python ai-service 的 AI 导购 Agent）
	 * <p>
	 * 与 {@link #createOrder} 的区别只有一个：调用方<b>没有用户登录态</b>，因此租户上下文需要由 服务端自行建立。租户来源为
	 * {@code sys_user.tenant_id}（买家所属租户）， <b>绝不信任调用方传入的租户标识</b>；随后复用
	 * {@link #createOrder}， 商品查询因此天然带租户过滤——跨租户商品会查不到并抛"商品不存在"，与前端公开路径行为一致。
	 * </p>
	 * @param buyerId 买家用户ID
	 * @param productId 商品ID
	 * @return 订单信息
	 */
	Map<String, Object> createOrderForUser(Long buyerId, Long productId);

	/**
	 * 内部接口下单（带收货地址）：服务端自行建立租户上下文后复用 {@link #createOrder}
	 * <p>
	 * {@code addressId} 由调用方（AI Agent）从用户默认地址解析后传入；express 商品缺地址会被 {@link #createOrder}
	 * 的校验链拒绝（"请选择收货地址"）。self_pickup 商品传 null 即可。
	 * </p>
	 * @param buyerId 买家用户ID
	 * @param productId 商品ID
	 * @param addressId 收货地址ID（express 必填；归属校验由 createOrder 完成）
	 * @return 订单信息
	 */
	Map<String, Object> createOrderForUser(Long buyerId, Long productId, Long addressId);

	/**
	 * 确认收货
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @return 结果
	 */
	String confirmReceipt(Long orderId, Long buyerId);

	/**
	 * 买家取消未支付订单并释放库存
	 * <p>
	 * 原子 UPDATE（仅 status='unpaid' 可取消）成功（rows=1）才回补库存—— 与超时关单任务并发时只有一方 rows=1，防止库存双倍回补。
	 * </p>
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @return 结果
	 */
	String cancelOrder(Long orderId, Long buyerId);

	/**
	 * 卖家发货
	 * @param orderId 订单ID
	 * @param sellerId 卖家ID
	 * @param company 物流公司
	 * @param trackingNo 物流单号
	 * @return 结果
	 */
	String shipOrder(Long orderId, Long sellerId, String company, String trackingNo);

	/**
	 * 关闭超时未支付订单并释放库存（quartz 定时任务入口）
	 * <p>
	 * 逐单原子关单（仅 status='unpaid' 命中），rows=1 才回补库存—— 与买家主动取消并发时只有一方 rows=1，防止库存双倍回补。 quartz
	 * 无租户上下文，<b>故意无租户参数</b>（全租户扫描）；逐单独立处理， 单单失败只记日志继续下一单，不抛异常。
	 * </p>
	 * @return 本次成功关单数
	 */
	int cancelExpiredUnpaid();

	/**
	 * 发货超时自动确认收货并标记商品已售（quartz 定时任务入口）
	 * <p>
	 * 逐单原子确认（仅 status='shipped' 命中），rows=1 才标记商品 sold—— 与买家主动确认并发时只有一方 rows=1。 quartz
	 * 无租户上下文，<b>故意无租户参数</b>（全租户扫描）；逐单独立处理， 单单失败只记日志继续下一单，不抛异常。
	 * </p>
	 * @return 本次自动确认单数
	 */
	int autoConfirmReceipt();

	/**
	 * 查询订单是否存在(买家校验)
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @return 订单信息
	 */
	Map<String, Object> validateOrderOwnership(Long orderId, Long buyerId);

	/**
	 * 校验订单可支付（收银台入口守卫）：归属本人 + status='unpaid' + 未过 expire_at + 存量单（expire_at IS
	 * NULL）不可支付。
	 * <p>
	 * 防资损：已关/已发货订单若仍可创建支付单，支付成功后订单不会随之迁移（迁移 SQL 有 unpaid 前置），
	 * 结果是钱已收、单已关、库存已回补转售。不可支付的订单与"订单不存在"返回同样形态（null），防探测。
	 * </p>
	 * @param orderId 订单ID
	 * @param buyerId 买家ID（必须为订单买家本人）
	 * @return 订单号与金额（order_no / total_amount / buyer_id）；不可支付或非本人返回 null
	 */
	Map<String, Object> validateOrderPayable(Long orderId, Long buyerId);

}
