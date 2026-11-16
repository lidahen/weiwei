package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.utils.IdUtils;
import com.twelvet.framework.utils.JacksonUtils;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.UserAddressMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import com.twelvet.trade.server.mapper.CsDisputeMapper;
import com.twelvet.trade.server.mapper.TradeLogisticsMapper;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.service.ICsDisputeService;
import com.twelvet.trade.server.service.ITradeOrderService;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易订单Service业务层处理
 *
 * @author twelvet
 */
@Service
public class TradeOrderServiceImpl implements ITradeOrderService {

	private static final Logger log = LoggerFactory.getLogger(TradeOrderServiceImpl.class);

	private final TradeOrderMapper orderMapper;

	private final TradeLogisticsMapper logisticsMapper;

	private final AdminMarketplaceMapper adminMapper;

	private final ProductMapper productMapper;

	private final UserAddressMapper userAddressMapper;

	private final TenantSupport tenantSupport;

	/** 超时任务单笔处理器（独立 bean，关单+回补/确认+标记 同事务原子单元） */
	private final TradeOrderTimeoutHelper timeoutHelper;

	/** IM 站内通知（尽力而为：失败只记日志，绝不阻断交易主流程） */
	private final IImService imService;

	private final IFunnelEventService funnelEventService;

	/**
	 * 未支付订单的支付过期分钟数（超时后由 quartz 任务关单并回补库存，见 trade.order.* 配置）
	 */
	@Value("${trade.order.unpaid-timeout-minutes:30}")
	private int unpaidTimeoutMinutes;

	/**
	 * 发货后自动确认收货天数（发货时间 + N 天写 auto_confirm_at，由 quartz 任务扫描，见 trade.order.* 配置）
	 */
	@Value("${trade.order.auto-confirm-days:7}")
	private int autoConfirmDays;

	public TradeOrderServiceImpl(TradeOrderMapper orderMapper, TradeLogisticsMapper logisticsMapper,
			AdminMarketplaceMapper adminMapper, ProductMapper productMapper, UserAddressMapper userAddressMapper,
			TenantSupport tenantSupport, TradeOrderTimeoutHelper timeoutHelper, IImService imService,
			IFunnelEventService funnelEventService) {
		this.orderMapper = orderMapper;
		this.logisticsMapper = logisticsMapper;
		this.adminMapper = adminMapper;
		this.productMapper = productMapper;
		this.userAddressMapper = userAddressMapper;
		this.tenantSupport = tenantSupport;
		this.timeoutHelper = timeoutHelper;
		this.imService = imService;
		this.funnelEventService = funnelEventService;
	}

	/**
	 * 站内通知买家（尽力而为：IM 故障不得阻断交易主流程）
	 */
	private void notifyBuyer(Long userId, String title, String content) {
		if (userId == null) {
			return;
		}
		try {
			imService.sendNotice(userId, "order", title, content);
		}
		catch (Exception e) {
			log.warn("[订单] 通知买家失败 userId={} title={}: {}", userId, title, e.getMessage());
		}
	}

	/**
	 * 站内通知卖家（尽力而为：IM 故障不得阻断交易主流程）
	 */
	private void notifySeller(Long userId, String title, String content) {
		if (userId == null) {
			return;
		}
		try {
			imService.sendNotice(userId, "order", title, content);
		}
		catch (Exception e) {
			log.warn("[订单] 通知卖家失败 userId={} title={}: {}", userId, title, e.getMessage());
		}
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	/**
	 * 查询订单列表
	 */
	@Override
	public TableDataInfo<TradeOrder> selectOrderList(Long userId, String role, String status) {
		// 分页：PageUtils 读取请求参数 current / pageSize（前端 ProTable 与本页均按此传参）
		PageUtils.startPage();
		List<TradeOrder> list = orderMapper.selectOrderList(userId, role, status, currentTid());
		if (list == null || list.isEmpty()) {
			return PageUtils.getDataTable(list);
		}

		// 批量加载物流信息（避免 N+1 查询）
		List<Long> orderIds = list.stream().map(TradeOrder::getOrderId).collect(Collectors.toList());
		List<Map<String, Object>> logisticsList = logisticsMapper.selectLogisticsByOrderIds(orderIds, currentTid());
		Map<Long, Map<String, Object>> logisticsMap = logisticsList.stream()
			.collect(Collectors.toMap(l -> (Long) l.get("orderId"), l -> l, (existing, replacement) -> existing));

		for (TradeOrder order : list) {
			Map<String, Object> logistics = logisticsMap.get(order.getOrderId());
			if (logistics != null) {
				order.setLogisticsCompany((String) logistics.get("company"));
				order.setLogisticsNo((String) logistics.get("no"));
				order.setLatestNode((String) logistics.get("latestNode"));
				order.setTraceJson((String) logistics.get("traceJson"));
			}
		}
		return PageUtils.getDataTable(list);
	}

	/**
	 * 查询订单详情(含物流)
	 */
	@Override
	public Map<String, Object> selectOrderDetail(Long orderId, Long viewerId) {
		TradeOrder order = orderMapper.selectOrderDetail(orderId, currentTid());
		// 越权防护：订单详情含收货地址/手机号，仅订单双方（买家/卖家）可见。
		// 非参与方与"订单不存在"统一返回空结果，避免通过响应差异探测订单是否存在。
		if (order == null || viewerId == null
				|| (!viewerId.equals(order.getBuyerId()) && !viewerId.equals(order.getSellerId()))) {
			return new HashMap<>();
		}
		Map<String, Object> result = orderToMap(order);
		// 加载物流
		Map<String, Object> logistics = logisticsMapper.selectLogisticsByOrderId(orderId, currentTid());
		result.put("logistics", logistics != null ? logistics : null);
		// 标记已支付
		String status = order.getStatus();
		result.put("paid", "paid".equals(status) || "unshipped".equals(status) || "shipped".equals(status)
				|| "done".equals(status));
		return result;
	}

	/**
	 * 创建订单（二手交易下单主链路）
	 * <p>
	 * 校验链：商品存在且 onsale → 自购拦截 → 原子扣库存（rows=0 即拒绝，防超卖兜底） → express
	 * 时校验收货地址归属。任何一步失败都不落订单、不留扣减（事务回滚）。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> createOrder(Long buyerId, Long productId, Long addressId, String buyerRemark,
			String deliveryType) {
		return createOrder(buyerId, productId, addressId, buyerRemark, deliveryType, false);
	}

	/**
	 * 下单主链路。{@code aiAssisted} 仅由 AI 导购内部接口传 true，用于漏斗统计 AI 辅助成交占比。
	 */
	private Map<String, Object> createOrder(Long buyerId, Long productId, Long addressId, String buyerRemark,
			String deliveryType, boolean aiAssisted) {
		// 取商品快照(租户隔离由 TenantContext 透传，关闭时为 null；跨租户商品直接"不存在")
		Product product = productMapper.selectById(productId, currentTid());
		if (product == null) {
			throw new TWTException("商品不存在");
		}
		if (!"onsale".equals(product.getStatus())) {
			throw new TWTException("商品已下架或已被拍下");
		}
		Long sellerId = product.getSellerId();
		if (sellerId != null && sellerId.equals(buyerId)) {
			throw new TWTException("不能购买自己发布的商品");
		}

		// 解析配送方式：一律以商品 shippingMethod 为准（express/self_pickup/free 之外的值按 express 处理）。
		// 不采信客户端入参：UI 从不传 deliveryType，显式传 self_pickup 只可能是伪造——
		// 把 express 商品伪造成面交即可跳过地址校验，产出无法履约的订单。
		String effectiveDeliveryType = switch (product.getShippingMethod() == null ? "" : product.getShippingMethod()) {
			case "self_pickup" -> "self_pickup";
			case "free" -> "express";
			default -> "express";
		};

		// 地址快照：express 必须有归属本人的收货地址（归属校验与读取一步完成）
		Map<String, Object> address = null;
		if ("express".equals(effectiveDeliveryType)) {
			if (addressId == null) {
				throw new TWTException("请选择收货地址");
			}
			address = userAddressMapper.selectAddressById(addressId, buyerId);
			if (address == null) {
				throw new TWTException("收货地址不存在或不属于当前用户");
			}
		}

		// 原子扣库存（防超卖兜底）：WHERE stock > 0 AND status='onsale'，rows=0 表示
		// 商品在读取后被并发拍下/下架，直接拒绝且不落订单（事务回滚）
		int deducted = productMapper.deductStock(productId, currentTid());
		if (deducted == 0) {
			throw new TWTException("商品已下架或已被拍下");
		}

		BigDecimal price = product.getPrice();
		BigDecimal freight = product.getShippingFee();
		if (freight == null)
			freight = BigDecimal.ZERO;
		// 运费只跟商品配送方式走：free（包邮）/self_pickup（自提）不为运费扣款——发布页可能误留
		// shippingFee（antd 表单切换配送方式时 preserve 保留隐藏字段值），后端照计会产生
		// "展示金额 ≠ 扣款金额"的资损争议。不能用 effectiveDeliveryType 判断（free 已映射为 express）。
		if ("free".equals(product.getShippingMethod()) || "self_pickup".equals(product.getShippingMethod())) {
			freight = BigDecimal.ZERO;
		}
		BigDecimal total = price.add(freight);

		String orderNo = "NO" + IdUtils.fastSimpleUUID();
		// 快照必须走 Jackson：title 含引号时 String.format 拼 JSON 会产出非法 JSON（脏数据）
		Map<String, Object> productSnapshotMap = new HashMap<>();
		productSnapshotMap.put("title", product.getTitle());
		productSnapshotMap.put("price", price);
		productSnapshotMap.put("cover", product.getCover());
		productSnapshotMap.put("condition", product.getCondition());
		String snapshot = JacksonUtils.toJson(productSnapshotMap);

		// 插入订单（租户归属：与商品同租户；开关开启但请求无租户上下文时抛异常，不静默落 default）
		TradeOrder order = new TradeOrder();
		order.setOrderNo(orderNo);
		order.setBuyerId(buyerId);
		order.setSellerId(sellerId);
		order.setProductId(productId);
		order.setProductSnapshot(snapshot);
		order.setSkuPrice(price);
		order.setFreight(freight);
		order.setTotalAmount(total);
		order.setDeliveryType(effectiveDeliveryType);
		order.setAddressSnapshot(address != null ? JacksonUtils.toJson(address) : null);
		order.setBuyerRemark(buyerRemark);
		order.setExpireAt(LocalDateTime.now().plusMinutes(unpaidTimeoutMinutes));
		order.setTenantId(tenantSupport.writeTid());
		orderMapper.insertOrder(order);
		// 核心漏斗（spec §6.3）：下单
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_ORDER_CREATE);
		funnelEvent.setTenantId(order.getTenantId());
		funnelEvent.setUserId(buyerId);
		funnelEvent.setRole("buyer");
		funnelEvent.setProductId(productId);
		funnelEvent.setCategory(product.getCatL1());
		funnelEvent.setAiAssisted(aiAssisted);
		funnelEvent.setBizId(String.valueOf(order.getOrderId()));
		funnelEvent.setEventKey("order_create:" + order.getOrderId());
		funnelEventService.record(funnelEvent);

		Map<String, Object> result = new HashMap<>();
		result.put("orderId", order.getOrderId());
		result.put("orderNo", orderNo);
		result.put("totalAmount", total);
		result.put("status", "unpaid");
		return result;
	}

	/**
	 * 内部接口下单：服务端自行建立租户上下文后复用 {@link #createOrder}
	 * <p>
	 * 修复背景：AI 导购 Agent 原先直接 INSERT trade_order，列清单中<b>没有 tenant_id</b>， 于是落到表定义的
	 * {@code NOT NULL DEFAULT 1} 上。而 trade_order 的租户归属本应与商品同租户 （见 {@link #createOrder}
	 * 的注释），且 {@code tenant.enabled} 当前为 <b>true</b>， 结果是：Agent
	 * 下的单在买家自己的租户里查不到，却出现在默认租户（1）的订单列表中——既破坏功能， 也构成跨租户数据混入。改为走本方法后，订单的租户、运费、商品快照全部由 Java
	 * 侧统一负责。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> createOrderForUser(Long buyerId, Long productId) {
		// 旧签名兼容：等价于无地址下单
		return createOrderForUser(buyerId, productId, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> createOrderForUser(Long buyerId, Long productId, Long addressId) {
		// 租户隔离开关闭时保持与前端公开路径完全一致的行为：不建立上下文，
		// XML 中 <if test="tenantId != null"> 不命中，列缺省由 DB DEFAULT 兜底（零侵入回滚能力不被破坏）
		if (!tenantSupport.enabled()) {
			return createOrder(buyerId, productId, addressId, null, null, true);
		}
		// 租户来源：sys_user.tenant_id。这是引导查询（此时上下文尚不存在），
		// 所以查询本身不带租户过滤；绝不使用调用方传入的租户标识，避免"传谁就是谁"的越权。
		Long buyerTenantId = orderMapper.selectUserTenantId(buyerId);
		if (buyerTenantId == null) {
			throw new TWTException("用户不存在或未归属任何租户，无法下单");
		}
		TenantContext.set(buyerTenantId);
		try {
			return createOrder(buyerId, productId, addressId, null, null, true);
		}
		finally {
			// 必须清理：否则上下文会随线程复用泄漏到后续无关请求
			TenantContext.clear();
		}
	}

	/**
	 * 确认收货
	 * <p>
	 * 先查订单（取 productId 供 markSold 使用并校验买家身份）→ 原子 UPDATE（仅 status='shipped' 可确认）→ rows=0
	 * 抛异常（不再静默 OK）；rows=1 才标记商品 sold（stock=0 AND status='locked' 守卫由 SQL 保证多库存商品不被误标）。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String confirmReceipt(Long orderId, Long buyerId) {
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		if (order == null) {
			throw new TWTException("订单不存在或不属于当前用户");
		}
		int rows = orderMapper.confirmReceipt(orderId, buyerId, currentTid());
		if (rows == 0) {
			// 状态已流转（非 shipped）或并发被关单：明确失败，不允许静默成功误导买家
			log.warn("确认收货失败: order={}, buyer={}, status={}", orderId, buyerId, order.getStatus());
			throw new TWTException("确认收货失败，订单当前状态不可确认收货");
		}
		// 交易完成：商品标记已售（SQL 守卫 stock=0 AND status='locked'，rows 可能是 0——多库存
		// 商品继续在售属正常，无需报错）
		productMapper.markSold(order.getProductId(), currentTid());
		notifySeller(order.getSellerId(), "买家已确认收货", "订单已完成");
		// 核心漏斗（spec §6.3）：确认收货 = 北极星指标（单社区周完成交易数）
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_CONFIRM_RECEIPT);
		funnelEvent.setTenantId(order.getTenantId());
		funnelEvent.setUserId(buyerId);
		funnelEvent.setRole("buyer");
		funnelEvent.setProductId(order.getProductId());
		funnelEvent.setBizId(String.valueOf(orderId));
		funnelEvent.setEventKey("confirm_receipt:" + orderId);
		funnelEventService.record(funnelEvent);
		return "OK";
	}

	/**
	 * 卖家发货
	 * <p>
	 * 先原子 UPDATE（status IN ('unshipped','paid')，后者为存量野状态兼容口）→ rows=0 直接抛异常
	 * <b>不写物流</b>（失败不留物流脏数据）；rows=1 才插入物流并写 auto_confirm_at（发货时间 + autoConfirmDays
	 * 天，供自动确认收货任务扫描）。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String shipOrder(Long orderId, Long sellerId, String company, String trackingNo) {
		// 先更新订单状态（原子）：失败即抛异常，绝不落物流
		int rows = orderMapper.shipOrder(orderId, sellerId, autoConfirmDays, currentTid());
		if (rows == 0) {
			log.warn("发货失败: order={}, seller={}", orderId, sellerId);
			throw new TWTException("发货失败，订单不存在或当前状态不可发货");
		}
		// 更新成功才插入物流
		logisticsMapper.insertLogistics(orderId, company, trackingNo, "已揽件", "[{\"node\":\"已揽件\",\"time\":\"刚刚\"}]",
				tenantSupport.writeTid());
		TradeOrder order = orderMapper.selectOrderDetail(orderId, currentTid());
		notifyBuyer(order == null ? null : order.getBuyerId(), "卖家已发货", "物流公司：" + company + "，单号：" + trackingNo);
		if (order != null) {
			FunnelEvent funnelEvent = new FunnelEvent();
			funnelEvent.setEventType(FunnelEvent.TYPE_SHIP);
			funnelEvent.setTenantId(order.getTenantId());
			funnelEvent.setUserId(sellerId);
			funnelEvent.setRole("seller");
			funnelEvent.setProductId(order.getProductId());
			funnelEvent.setBizId(String.valueOf(orderId));
			funnelEvent.setEventKey("ship:" + orderId);
			funnelEventService.record(funnelEvent);
		}
		return "OK";
	}

	/**
	 * 买家取消未支付订单并释放库存
	 * <p>
	 * 避免先读后判的 TOCTOU：直接原子 UPDATE（仅 status='unpaid' 可取消）→ rows=0 抛异常且
	 * <b>不释放库存</b>（与超时关单任务并发时只有一方 rows=1，防止双倍回补）；rows=1 才用 之前查出的 productId 回补库存。
	 * </p>
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String cancelOrder(Long orderId, Long buyerId) {
		// 先查订单取 productId（查不到由 UPDATE rows 判定兜底，不做先验拦截）
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		// 原子取消：仅 status='unpaid' 命中；rows=0 = 订单不存在/非本人/已支付/已被超时关单
		int rows = orderMapper.cancelOrder(orderId, buyerId, currentTid());
		if (rows == 0) {
			log.warn("取消订单失败: order={}, buyer={}", orderId, buyerId);
			throw new TWTException("取消失败，订单不存在或当前状态不可取消");
		}
		// 取消成功才释放库存（productId 可能因查无订单为 null，此时不应发生——rows=1 必然有单）。
		// 存量判别：expire_at IS NULL = 旧链路订单（2026-10-19 之前下单，从未扣过库存），
		// 回补会凭空虚增库存（stock 1→2 → 一件实体卖两次）；quartz 关单扫描同样按
		// expire_at IS NOT NULL 过滤，两条路径判别口径一致。
		if (order != null && order.getExpireAt() != null) {
			productMapper.restoreStock(order.getProductId(), currentTid());
		}
		return "OK";
	}

	/**
	 * 查询订单是否存在(买家校验)
	 */
	@Override
	public Map<String, Object> validateOrderOwnership(Long orderId, Long buyerId) {
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		if (order == null) {
			return null;
		}
		Map<String, Object> result = new HashMap<>();
		result.put("order_id", order.getOrderId());
		result.put("order_no", order.getOrderNo());
		result.put("total_amount", order.getTotalAmount());
		result.put("buyer_id", order.getBuyerId());
		return result;
	}

	/**
	 * 校验订单可支付（收银台入口守卫，语义见 {@link #validateOrderPayable} 接口注释）
	 * <p>
	 * 状态与时效在 Java 层判定（先查后判）；与支付成功迁移 SQL 的 unpaid 前置、quartz 关单的 unpaid
	 * 命中形成三重防线——入口拒绝、迁移守卫、超时关单互不信任，任一竞态窗口的钱货 分离都止于"支付单已付但订单未迁移"，由事件自愈或第 2
	 * 期退款兜底，不再扩大为库存错配。
	 * </p>
	 */
	@Override
	public Map<String, Object> validateOrderPayable(Long orderId, Long buyerId) {
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		if (order == null || !"unpaid".equals(order.getStatus())) {
			return null;
		}
		// 存量单（expire_at IS NULL）不可支付：旧链路无库存扣减/关单语义，支付后状态迁移
		// 与库存联动不可靠，须先人工迁移（与 cancelOrder/quartz 的存量判别口径一致）
		if (order.getExpireAt() == null || order.getExpireAt().isBefore(LocalDateTime.now())) {
			return null;
		}
		Map<String, Object> result = new HashMap<>();
		result.put("order_id", order.getOrderId());
		result.put("order_no", order.getOrderNo());
		result.put("total_amount", order.getTotalAmount());
		result.put("buyer_id", order.getBuyerId());
		return result;
	}

	/**
	 * 关闭超时未支付订单并释放库存（quartz 定时任务入口）
	 * <p>
	 * 后台静默容错：逐单独立处理，单单失败只 log.warn 继续下一单（不抛异常中断整批——一单回滚 不应连坐其余订单）。单笔「关单+回补」委托给
	 * {@link TradeOrderTimeoutHelper}（独立 bean，
	 * @Transactional 经代理生效）：回补失败时关单一并回滚，下轮重扫自动重试——修复前两步裸调， 回补失败被吞后订单已 closed 永不重访，商品永久
	 * locked。租户设计：quartz 无租户上下文， 回补库存传 null（全租户语义）。
	 * </p>
	 */
	@Override
	public int cancelExpiredUnpaid() {
		// 扫描上限 200：单次任务处理量上限，防止积压时单次运行过久（每 5 分钟重跑，天然分批消化）
		List<TradeOrder> expiredOrders = orderMapper.selectExpiredUnpaidOrders(200);
		if (expiredOrders == null || expiredOrders.isEmpty()) {
			return 0;
		}
		int closed = 0;
		for (TradeOrder order : expiredOrders) {
			try {
				if (timeoutHelper.closeSingleExpiredOrder(order.getOrderId(), order.getProductId())) {
					closed++;
				}
				else {
					log.warn("超时关单跳过(状态已流转): order={}", order.getOrderId());
				}
			}
			catch (Exception e) {
				// 事务已回滚（订单仍 unpaid），下轮重扫自动重试；只记录，不中断整批
				log.warn("超时关单单单失败(将下轮重试)，继续下一单: order={}", order.getOrderId(), e);
			}
		}
		return closed;
	}

	/**
	 * 发货超时自动确认收货并标记商品已售（quartz 定时任务入口）
	 * <p>
	 * 后台静默容错同 {@link #cancelExpiredUnpaid}；租户设计：quartz 无租户上下文，标记商品传 null（全租户语义）。
	 * </p>
	 */
	@Override
	public int autoConfirmReceipt() {
		// 扫描上限同 cancelExpiredUnpaid（每 5 分钟重跑，天然分批消化）
		List<TradeOrder> dueOrders = orderMapper.selectAutoConfirmDueOrders(200);
		if (dueOrders == null || dueOrders.isEmpty()) {
			return 0;
		}
		int confirmed = 0;
		for (TradeOrder order : dueOrders) {
			try {
				// 单笔「确认+标记 sold」委托 helper（同事务）：markSold 失败整体回滚，下轮重试
				if (timeoutHelper.confirmSingleOrder(order.getOrderId(), order.getProductId())) {
					confirmed++;
				}
				else {
					log.warn("自动确认收货跳过(状态已流转): order={}", order.getOrderId());
				}
			}
			catch (Exception e) {
				log.warn("自动确认收货单单失败(将下轮重试)，继续下一单: order={}", order.getOrderId(), e);
			}
		}
		return confirmed;
	}

	/**
	 * TradeOrder -> Map 转换
	 */
	private Map<String, Object> orderToMap(TradeOrder order) {
		Map<String, Object> map = new HashMap<>();
		map.put("orderId", order.getOrderId());
		map.put("orderNo", order.getOrderNo());
		map.put("buyerId", order.getBuyerId());
		map.put("sellerId", order.getSellerId());
		map.put("productId", order.getProductId());
		map.put("productSnapshot", order.getProductSnapshot());
		map.put("skuPrice", order.getSkuPrice());
		map.put("freight", order.getFreight());
		map.put("totalAmount", order.getTotalAmount());
		map.put("deliveryType", order.getDeliveryType());
		map.put("addressSnapshot", order.getAddressSnapshot());
		map.put("buyerRemark", order.getBuyerRemark());
		map.put("status", order.getStatus());
		map.put("createTime", order.getCreateTime());
		map.put("paidAt", order.getPaidAt());
		map.put("shippedAt", order.getShippedAt());
		map.put("doneAt", order.getDoneAt());
		map.put("autoConfirmAt", order.getAutoConfirmAt());
		map.put("buyerNickName", order.getBuyerNickName());
		map.put("buyerAvatar", order.getBuyerAvatar());
		map.put("sellerNickName", order.getSellerNickName());
		map.put("sellerAvatar", order.getSellerAvatar());
		return map;
	}

}
