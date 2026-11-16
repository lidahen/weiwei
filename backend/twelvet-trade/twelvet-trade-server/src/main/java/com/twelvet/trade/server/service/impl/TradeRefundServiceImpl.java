package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.utils.JacksonUtils;
import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.domain.RefundReason;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.mapper.TradePayLookupMapper;
import com.twelvet.trade.server.mapper.TradeRefundLogMapper;
import com.twelvet.trade.server.mapper.TradeRefundMapper;
import com.twelvet.trade.server.service.ISettlementService;
import com.twelvet.trade.server.service.ITradeRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 退款售后 Service 实现
 * <p>
 * 状态推进一律「SQL 带前置状态守卫 + 检查影响行数」，0 行即视为并发变更并抛业务异常， 不得静默成功。退款单的进行中唯一性由唯一索引 uk_order_active
 * 兜底，并发冲突翻译为可读提示。
 * </p>
 *
 * @author twelvet
 */
@Service
public class TradeRefundServiceImpl implements ITradeRefundService {

	/**
	 * 售后期限（天）：done 订单超过该天数不可申请
	 */
	private static final int AFTER_SALE_DAYS = 7;

	/**
	 * 证据图上限（与举报一致）
	 */
	private static final int MAX_EVIDENCE_IMAGES = 3;

	/**
	 * 允许申请退款的订单状态
	 */
	private static final Set<String> APPLYABLE_STATUSES = Set.of("unshipped", "shipped", "done", "paid");

	private static final Logger log = LoggerFactory.getLogger(TradeRefundServiceImpl.class);

	private static final String TYPE_REFUND_ONLY = "refund_only";

	private static final String TYPE_RETURN_REFUND = "return_refund";

	private static final String ORDER_STATUS_DONE = "done";

	private static final String ORDER_STATUS_REFUNDING = "refunding";

	private static final String REFUND_STATUS_PENDING_SELLER = "pending_seller";

	private static final String DENY_ORDER_NOT_FOUND = "订单不存在或不属于当前用户";

	private static final String DENY_STATUS_NOT_SUPPORTED = "该订单当前状态不支持申请退款";

	private static final String DENY_AFTER_SALE_EXPIRED = "已超过 7 天售后期限";

	private static final String DENY_REASON_INVALID = "无效的退款原因";

	private static final String DENY_REASON_TYPE_MISMATCH = "该退款原因不适用于退货退款";

	private static final String DENY_AMOUNT_NOT_POSITIVE = "退款金额必须大于 0";

	private static final String DENY_ACTIVE_REFUND_EXISTS = "该订单已有处理中的退款申请";

	private static final String DENY_ORDER_CHANGED = "订单状态已变更，请刷新后重试";

	private static final String DENY_TOO_MANY_EVIDENCE = "最多上传 3 张证据图";

	private static final String DENY_REFUND_NOT_FOUND = "退款不存在";

	private static final String DENY_NO_PERMISSION = "无权操作该退款";

	private static final String DENY_REFUND_STATUS_CHANGED = "退款状态已变更，请刷新";

	private static final String DENY_RETURN_ADDRESS_REQUIRED = "请提供退货地址";

	private static final String DENY_REJECT_REASON_REQUIRED = "请填写拒绝理由";

	private static final String DENY_REFUND_ONLY_NO_LOGISTICS = "仅退款无需填写退货物流";

	private static final String DENY_ORDER_CHANGED_FOR_MEDIATE = "订单状态已变更，无法申请介入";

	private static final String DENY_PREV_ORDER_STATUS_MISSING = "退款单缺少原订单状态，请联系客服处理";

	private static final String DENY_JUDGE_DECISION_INVALID = "裁决结论不合法";

	private static final String DENY_RESOLVE_RESULT_INVALID = "收口结果不合法";

	private static final String DENY_PARTIAL_AMOUNT_INVALID = "部分退款金额需大于 0 且不超过申请金额";

	private static final List<String> CANCELABLE_STATUSES = List.of("pending_seller", "seller_agreed",
			"seller_rejected");

	/**
	 * 单次扫描每类超时的上限
	 */
	private static final int SCAN_LIMIT = 200;

	/**
	 * 卖家处理超时（小时）
	 */
	@Value("${trade.refund.seller-timeout-hours:48}")
	private int sellerTimeoutHours = 48;

	/**
	 * 买家未寄回超时（天）
	 */
	@Value("${trade.refund.return-timeout-days:7}")
	private int returnTimeoutDays = 7;

	/**
	 * 卖家未确认收货超时（天）
	 */
	@Value("${trade.refund.confirm-timeout-days:7}")
	private int confirmTimeoutDays = 7;

	private final TradeOrderMapper orderMapper;

	private final TradeRefundMapper refundMapper;

	private final TradeRefundLogMapper logMapper;

	private final TradePayLookupMapper payLookupMapper;

	private final IPayOrderService payOrderService;

	private final TradePayFlowMapper flowMapper;

	private final IImService imService;

	private final ProductMapper productMapper;

	/**
	 * 超时扫描单条事务包装（独立 bean，保证 @Transactional 走代理）
	 */
	private final TradeRefundTimeoutHelper timeoutHelper;

	private final IFunnelEventService funnelEventService;

	private final ISettlementService settlementService;

	public TradeRefundServiceImpl(TradeOrderMapper orderMapper, TradeRefundMapper refundMapper,
			TradeRefundLogMapper logMapper, TradePayLookupMapper payLookupMapper, IPayOrderService payOrderService,
			TradePayFlowMapper flowMapper, IImService imService, ProductMapper productMapper,
			TradeRefundTimeoutHelper timeoutHelper, IFunnelEventService funnelEventService,
			ISettlementService settlementService) {
		this.orderMapper = orderMapper;
		this.refundMapper = refundMapper;
		this.logMapper = logMapper;
		this.payLookupMapper = payLookupMapper;
		this.payOrderService = payOrderService;
		this.flowMapper = flowMapper;
		this.imService = imService;
		this.productMapper = productMapper;
		this.timeoutHelper = timeoutHelper;
		this.funnelEventService = funnelEventService;
		this.settlementService = settlementService;
	}

	/**
	 * 当前租户ID（未识别租户时为 null，XML 中 <if> 不命中，过滤器不生效——零侵入回滚）
	 */
	private Long currentTid() {
		return TenantContext.get();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long apply(Long orderId, String type, String reasonCode, BigDecimal amount, String description,
			List<String> evidenceImages, Long buyerId) {
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		String denyReason = validateApply(order, type, reasonCode, amount);
		if (denyReason != null) {
			throw new TWTException(denyReason);
		}
		// 证据图在落库前校验：非法请求不得先把订单冻结为 refunding
		if (evidenceImages != null && evidenceImages.size() > MAX_EVIDENCE_IMAGES) {
			throw new TWTException(DENY_TOO_MANY_EVIDENCE);
		}
		Long activeCount = refundMapper.countActiveByOrderId(orderId);
		if (activeCount != null && activeCount > 0) {
			throw new TWTException(DENY_ACTIVE_REFUND_EXISTS);
		}

		// CAS 只接受「读到的那一个状态」：宽白名单会让并发发货（SELECT 与 UPDATE 之间 unshipped→shipped）
		// 被一并冻结，而 prev_order_status 仍按陈旧读值落库，恢复/回补都会走错分支
		int rows = orderMapper.updateOrderStatusIfIn(orderId, ORDER_STATUS_REFUNDING, List.of(order.getStatus()),
				currentTid());
		if (rows == 0) {
			throw new TWTException(DENY_ORDER_CHANGED);
		}

		TradeRefund refund = new TradeRefund();
		refund.setOrderId(orderId);
		refund.setBuyerId(buyerId);
		refund.setSellerId(order.getSellerId());
		refund.setType(type);
		refund.setReasonCode(reasonCode);
		refund.setAmount(amount);
		refund.setDescription(description);
		refund.setEvidenceImages(JacksonUtils.toJson(evidenceImages));
		refund.setStatus(REFUND_STATUS_PENDING_SELLER);
		refund.setPrevOrderStatus(order.getStatus());
		try {
			refundMapper.insert(refund);
		}
		catch (DuplicateKeyException e) {
			// uk_order_active 兜底：并发重复申请时唯一索引冲突，翻译为业务提示
			throw new TWTException(DENY_ACTIVE_REFUND_EXISTS);
		}

		logMapper.insert(refund.getRefundId(), buyerId, "apply",
				"申请" + RefundReason.labelOf(reasonCode) + "，金额 ¥" + amount);
		// 核心漏斗（spec §6.3）：申请退款
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_REFUND_APPLY);
		funnelEvent.setTenantId(order.getTenantId());
		funnelEvent.setUserId(buyerId);
		funnelEvent.setRole("buyer");
		funnelEvent.setProductId(order.getProductId());
		funnelEvent.setBizId(String.valueOf(refund.getRefundId()));
		funnelEvent.setEventKey("refund_apply:" + refund.getRefundId());
		funnelEventService.record(funnelEvent);
		notifySeller(order.getSellerId(), "新的退款申请", "买家申请退款，金额 ¥" + amount + "，请及时处理");
		return refund.getRefundId();
	}

	@Override
	public Map<String, Object> checkApply(Long orderId, Long buyerId) {
		Map<String, Object> result = new LinkedHashMap<>(6);
		TradeOrder order = orderMapper.selectOrderByIdAndBuyerId(orderId, buyerId, currentTid());
		String denyReason;
		Long activeRefundId = null;
		if (order == null) {
			denyReason = DENY_ORDER_NOT_FOUND;
		}
		else {
			// 必须先查进行中的退款单：申请即冻结，订单处于 refunding 时 validateOrder 只会给出
			// 「状态不支持」，activeRefundId 永远为空，前端的「查看退款进度」入口就永远不会出现
			TradeRefund active = refundMapper.selectActiveByOrderId(orderId);
			if (active != null) {
				denyReason = DENY_ACTIVE_REFUND_EXISTS;
				activeRefundId = active.getRefundId();
			}
			else {
				denyReason = validateOrder(order);
			}
		}
		result.put("canApply", denyReason == null);
		result.put("denyReason", denyReason);
		result.put("activeRefundId", activeRefundId);
		result.put("orderStatus", order == null ? null : order.getStatus());
		result.put("allowedTypes", allowedTypes(order));
		return result;
	}

	/**
	 * 当前订单状态可用的退款类型（前端据此过滤选项，不靠前端猜）
	 * @param order 订单
	 * @return 允许的类型；订单为空或状态不支持时返回空列表
	 */
	private List<String> allowedTypes(TradeOrder order) {
		if (order == null) {
			return List.of();
		}
		String status = order.getStatus();
		if ("unshipped".equals(status) || "paid".equals(status)) {
			return List.of(TYPE_REFUND_ONLY);
		}
		if ("shipped".equals(status) || ORDER_STATUS_DONE.equals(status)) {
			return List.of(TYPE_REFUND_ONLY, TYPE_RETURN_REFUND);
		}
		return List.of();
	}

	@Override
	public Map<String, Object> detail(Long refundId, Long viewerId) {
		TradeRefund refund = refundMapper.selectById(refundId, currentTid());
		if (refund == null || viewerId == null
				|| (!viewerId.equals(refund.getBuyerId()) && !viewerId.equals(refund.getSellerId()))) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<>(4);
		result.put("refund", refund);
		result.put("logs", logMapper.selectByRefundId(refundId));
		return result;
	}

	@Override
	public Map<String, Object> adminDetail(Long refundId) {
		TradeRefund refund = refundMapper.selectById(refundId, currentTid());
		if (refund == null) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<>(4);
		result.put("refund", refund);
		result.put("logs", logMapper.selectByRefundId(refundId));
		result.put("order", orderMapper.selectOrderDetail(refund.getOrderId(), currentTid()));
		return result;
	}

	@Override
	public TableDataInfo<TradeRefund> adminPage(String status, Boolean overdue) {
		PageUtils.startPage();
		return PageUtils.getDataTable(refundMapper.selectAdminList(status, overdue, currentTid()));
	}

	@Override
	public TableDataInfo<TradeRefund> page(Long userId, String role, String status) {
		PageUtils.startPage();
		return PageUtils.getDataTable(refundMapper.selectList(userId, role, status, currentTid()));
	}

	@Override
	public TableDataInfo<TradeRefund> sellerPage(Long sellerId, String status) {
		PageUtils.startPage();
		return PageUtils.getDataTable(refundMapper.selectList(sellerId, "seller", status, currentTid()));
	}

	/**
	 * 订单级闸门（不含类型/原因/金额）：订单归属、状态、done 的 7 天窗口
	 * @param order 订单
	 * @return 拒绝原因；可申请返回 null
	 */
	private String validateOrder(TradeOrder order) {
		if (order == null) {
			return DENY_ORDER_NOT_FOUND;
		}
		String status = order.getStatus();
		if (!APPLYABLE_STATUSES.contains(status)) {
			return DENY_STATUS_NOT_SUPPORTED;
		}
		if (ORDER_STATUS_DONE.equals(status)) {
			LocalDateTime base = (order.getDoneAt() != null) ? order.getDoneAt() : order.getUpdateTime();
			// done_at 可为 NULL，回退 update_time；两者都 NULL 则拒绝，不得 NPE
			if (base == null || base.isBefore(LocalDateTime.now().minusDays(AFTER_SALE_DAYS))) {
				return DENY_AFTER_SALE_EXPIRED;
			}
		}
		return null;
	}

	/**
	 * 申请退款完整闸门：订单级闸门 + 类型适配 + 原因白名单 + 金额上限
	 * @return 拒绝原因；可申请返回 null
	 */
	private String validateApply(TradeOrder order, String type, String reasonCode, BigDecimal amount) {
		String denyReason = validateOrder(order);
		if (denyReason != null) {
			return denyReason;
		}
		String status = order.getStatus();
		// 未发货（含存量 paid）只能仅退款；shipped / done 两种类型都允许
		if (("unshipped".equals(status) || "paid".equals(status)) && !TYPE_REFUND_ONLY.equals(type)) {
			return DENY_STATUS_NOT_SUPPORTED;
		}
		if (!RefundReason.isValid(reasonCode)) {
			return DENY_REASON_INVALID;
		}
		if (!RefundReason.supportsType(reasonCode, type)) {
			return DENY_REASON_TYPE_MISMATCH;
		}
		if (amount == null || amount.signum() <= 0) {
			return DENY_AMOUNT_NOT_POSITIVE;
		}
		BigDecimal totalAmount = order.getTotalAmount();
		if (totalAmount != null && amount.compareTo(totalAmount) > 0) {
			return "退款金额不能超过实付 ¥" + totalAmount;
		}
		return null;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void sellerAgree(Long refundId, String returnAddress, Long sellerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!sellerId.equals(refund.getSellerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		boolean returnRefund = TYPE_RETURN_REFUND.equals(refund.getType());
		// 地址校验在状态推进之前：非法请求不改任何状态
		if (returnRefund && (returnAddress == null || returnAddress.isBlank())) {
			throw new TWTException(DENY_RETURN_ADDRESS_REQUIRED);
		}
		int rows = refundMapper.updateStatus(refundId, "pending_seller", "seller_agreed");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		if (returnRefund) {
			refundMapper.updateReturnAddress(refundId, returnAddress);
		}
		logMapper.insert(refundId, sellerId, "seller_agree",
				returnRefund ? "卖家同意退货退款，退货地址：" + returnAddress : "卖家同意退款申请");
		// 仅退款跳过 buyer_returned：seller_agreed 紧接推进 refunding 并执行退款
		if (!returnRefund) {
			int advanceRows = refundMapper.updateStatus(refundId, "seller_agreed", "refunding");
			if (advanceRows == 0) {
				throw new TWTException(DENY_REFUND_STATUS_CHANGED);
			}
			executeRefund(refund, refund.getAmount());
		}
		notifyBuyer(refund.getBuyerId(), "卖家已同意退款", returnRefund ? "请按退货地址寄回：" + returnAddress : "退款处理中");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void sellerReject(Long refundId, String reason, Long sellerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!sellerId.equals(refund.getSellerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		if (reason == null || reason.isBlank()) {
			throw new TWTException(DENY_REJECT_REASON_REQUIRED);
		}
		int rows = refundMapper.updateStatus(refundId, "pending_seller", "seller_rejected");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		// 卖家拒绝即退款关闭，买家仍可另开新申请或对这条申请介入
		restoreOrder(refund, currentTid());
		logMapper.insert(refundId, sellerId, "seller_reject", reason);
		notifyBuyer(refund.getBuyerId(), "卖家已拒绝退款", "如不认可，可申请平台介入");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void buyerReturn(Long refundId, String company, String returnNo, Long buyerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!buyerId.equals(refund.getBuyerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		if (!TYPE_RETURN_REFUND.equals(refund.getType())) {
			throw new TWTException(DENY_REFUND_ONLY_NO_LOGISTICS);
		}
		if (company == null || company.isBlank()) {
			throw new TWTException("请填写退货物流公司");
		}
		if (returnNo == null || returnNo.isBlank()) {
			throw new TWTException("请填写退货单号");
		}
		int rows = refundMapper.updateStatus(refundId, "seller_agreed", "buyer_returned");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		refundMapper.updateReturnLogistics(refundId, company, returnNo);
		logMapper.insert(refundId, buyerId, "buyer_return", "买家已寄回：" + company + " " + returnNo);
		notifySeller(refund.getSellerId(), "买家已寄回退货", "物流：" + company + " " + returnNo + "，请确认收货");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void buyerCancel(Long refundId, Long buyerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!buyerId.equals(refund.getBuyerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		int rows = 0;
		for (String fromStatus : CANCELABLE_STATUSES) {
			rows = refundMapper.updateStatus(refundId, fromStatus, "closed");
			if (rows > 0) {
				break;
			}
		}
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		restoreOrder(refund, currentTid());
		logMapper.insert(refundId, buyerId, "buyer_cancel", "买家撤销退款申请");
		notifySeller(refund.getSellerId(), "买家已撤销退款", "退款申请已撤销");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void buyerMediate(Long refundId, Long buyerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!buyerId.equals(refund.getBuyerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		if (refund.getPrevOrderStatus() == null) {
			throw new TWTException(DENY_PREV_ORDER_STATUS_MISSING);
		}
		int rows = refundMapper.updateStatus(refundId, "seller_rejected", "mediation");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		// 必须重新冻结订单：否则平台介入期间买家能确认收货，形成「货拿走 + 钱退回」
		int orderRows = orderMapper.updateOrderStatusIfIn(refund.getOrderId(), ORDER_STATUS_REFUNDING,
				List.of(refund.getPrevOrderStatus()), currentTid());
		if (orderRows == 0) {
			throw new TWTException(DENY_ORDER_CHANGED_FOR_MEDIATE);
		}
		logMapper.insert(refundId, buyerId, "mediate", "买家申请平台介入");
		notifySeller(refund.getSellerId(), "买家已申请平台介入", "请等待平台处理");
	}

	/**
	 * 按 ref ID 取退款单，不存在即抛业务异常
	 */
	private TradeRefund requireRefund(Long refundId) {
		TradeRefund refund = refundMapper.selectById(refundId, currentTid());
		if (refund == null) {
			throw new TWTException(DENY_REFUND_NOT_FOUND);
		}
		return refund;
	}

	/**
	 * 退款关闭时按 prev_order_status 恢复订单（0 行只告警：订单可能已被运营收口）
	 * <p>
	 * 恢复前必须确认同订单没有另一条进行中的退款单：被拒单（seller_rejected）不占 active_flag，
	 * 买家可以再申请新单并把订单重新冻结；此时撤销旧单若直接解冻，新单仍在处理中但订单已恢复为可发货/可收货， 最终形成「货拿走 + 钱退回」。
	 * </p>
	 */
	private void restoreOrder(TradeRefund refund, Long tenantId) {
		if (refund.getPrevOrderStatus() == null) {
			log.warn("退款单 {} 缺少 prev_order_status，跳过订单 {} 状态恢复", refund.getRefundId(), refund.getOrderId());
			return;
		}
		Long activeCount = refundMapper.countActiveByOrderId(refund.getOrderId());
		if (activeCount != null && activeCount > 0) {
			log.warn("退款单 {} 关闭时同订单仍有 {} 条进行中的退款单，跳过订单 {} 状态恢复", refund.getRefundId(), activeCount,
					refund.getOrderId());
			return;
		}
		int rows = orderMapper.updateOrderStatusIfIn(refund.getOrderId(), refund.getPrevOrderStatus(),
				List.of(ORDER_STATUS_REFUNDING), tenantId);
		if (rows == 0) {
			log.warn("退款单 {} 恢复订单 {} 到 {} 时 0 行（订单可能已被运营收口）", refund.getRefundId(), refund.getOrderId(),
					refund.getPrevOrderStatus());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void sellerConfirmReceipt(Long refundId, Long sellerId) {
		TradeRefund refund = requireRefund(refundId);
		if (!sellerId.equals(refund.getSellerId())) {
			throw new TWTException(DENY_NO_PERMISSION);
		}
		int rows = refundMapper.updateStatus(refundId, "buyer_returned", "refunding");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		logMapper.insert(refundId, sellerId, "seller_confirm", "卖家确认收到退货");
		notifyBuyer(refund.getBuyerId(), "卖家已确认收到退货", "退款处理中");
		executeRefund(refund, refund.getAmount());
	}

	/**
	 * 退款执行唯一入口（卖家确认收货 / 仅退款同意 / 客服裁决三条路径共用）
	 * <p>
	 * 调用方负责先把退款单推进到 refunding；本方法只做：定位支付单 → 调网关 → 收尾。 定位失败 / 金额不足 / 网关失败一律转人工（写
	 * manual_required 日志 + 通知买家），不抛异常。
	 * </p>
	 * @param refund 退款单（状态已是 refunding）
	 * @param amount 本次退款金额
	 * @return 是否退款成功
	 */
	private boolean executeRefund(TradeRefund refund, BigDecimal amount) {
		Map<String, Object> payOrder = pickPayOrder(payLookupMapper.selectPaidByOrderId(refund.getOrderId()),
				refund.getBuyerId());
		String manualReason = null;
		if (payOrder == null) {
			manualReason = "未找到与买家匹配的唯一已支付支付单，需人工处理";
		}
		else {
			BigDecimal payAmount = toBigDecimal(payOrder.get("pay_amount"));
			if (payAmount == null || payAmount.compareTo(amount) < 0) {
				// mock 模式下 PayOrderService.refund 只改状态、不做金额检查，少了这道校验超额退款会静默「成功」
				manualReason = "支付单金额 " + payAmount + " 小于申请退款金额 " + amount + "，需人工处理";
				payOrder = null;
			}
		}
		if (payOrder == null) {
			markManualRequired(refund, manualReason);
			return false;
		}
		String outTradeNo = (String) payOrder.get("out_trade_no");
		String channel = (String) payOrder.get("pay_channel");
		boolean success = payOrderService.refund(outTradeNo, amount,
				"退款：" + RefundReason.labelOf(refund.getReasonCode()));
		if (!success) {
			markManualRequired(refund, "退款网关执行失败（out_trade_no=" + outTradeNo + "），需人工处理");
			return false;
		}
		int rows = refundMapper.updateStatus(refund.getRefundId(), "refunding", "refund_success");
		if (rows == 0) {
			log.warn("退款单 {} 推进 refund_success 时 0 行（状态已变更），继续收尾", refund.getRefundId());
		}
		finishRefundSuccess(refund, amount, outTradeNo, channel);
		return true;
	}

	/**
	 * 退款成功收尾：订单 refunded、写退款流水、refund_ok 日志、通知买家
	 * <p>
	 * 运营手工收口（resolve）也复用它，只是不经过网关（outTradeNo / channel 传 null）。
	 * </p>
	 */
	private void finishRefundSuccess(TradeRefund refund, BigDecimal amount, String outTradeNo, String channel) {
		TradeOrder order = orderMapper.selectOrderDetail(refund.getOrderId(), currentTid());
		int rows = orderMapper.updateOrderStatusIfIn(refund.getOrderId(), "refunded", List.of(ORDER_STATUS_REFUNDING),
				currentTid());
		if (rows == 0) {
			log.warn("退款单 {} 收尾订单 {} 为 refunded 时 0 行（订单可能已被运营收口）", refund.getRefundId(), refund.getOrderId());
		}
		try {
			flowMapper.insert(refund.getOrderId(), "refund", amount, channel, outTradeNo);
		}
		catch (Exception e) {
			log.warn("[退款] 写资金流水失败 orderId={}: {}", refund.getOrderId(), e.getMessage());
		}
		logMapper.insert(refund.getRefundId(), null, "refund_ok", "退款成功，金额 ¥" + amount);
		// 核心漏斗（spec §6.3）：退款成功
		FunnelEvent funnelEvent = new FunnelEvent();
		funnelEvent.setEventType(FunnelEvent.TYPE_REFUND_SUCCESS);
		funnelEvent.setTenantId(order == null ? null : order.getTenantId());
		funnelEvent.setUserId(refund.getBuyerId());
		funnelEvent.setRole("buyer");
		funnelEvent.setProductId(order == null ? null : order.getProductId());
		funnelEvent.setBizId(String.valueOf(refund.getRefundId()));
		funnelEvent.setEventKey("refund_success:" + refund.getRefundId());
		funnelEventService.record(funnelEvent);
		// 结算互斥：窗口内退款作废结算单，已结算按账本扣回。结算异常不得让已成功的退款失败
		try {
			settlementService.onRefundSuccess(refund, order, amount);
		}
		catch (Exception e) {
			log.error("[结算] 退款成功互斥结算失败 refundId={}: {}", refund.getRefundId(), e.getMessage());
		}
		settleProduct(refund, order);
		notifyBuyer(refund.getBuyerId(), "退款成功", "您的退款 ¥" + amount + " 已原路退回");
	}

	/**
	 * 退款成功后的商品处置
	 * <p>
	 * 未发货退款：货还在卖家手里，restoreStock 回补库存（它自己把 locked/sold 恢复 onsale）；
	 * 已发货/已收货退款：货在买家手里，不回补库存，但必须置 offshelf —— 停在 locked 会让商品
	 * 不可见/不可编辑/不可下架，永久锁死。商品处置失败不得让已成功的退款回滚。
	 * </p>
	 */
	private void settleProduct(TradeRefund refund, TradeOrder order) {
		try {
			if (order == null || order.getProductId() == null) {
				log.warn("退款单 {} 收尾时订单 {} 或商品为空，跳过商品处置", refund.getRefundId(), refund.getOrderId());
				return;
			}
			String prevOrderStatus = refund.getPrevOrderStatus();
			if ("unshipped".equals(prevOrderStatus) || "paid".equals(prevOrderStatus)) {
				productMapper.restoreStock(order.getProductId(), currentTid());
			}
			else if ("shipped".equals(prevOrderStatus) || ORDER_STATUS_DONE.equals(prevOrderStatus)) {
				int rows = productMapper.updateStatusIfIn(order.getProductId(), "offshelf", List.of("locked", "sold"),
						currentTid());
				if (rows == 0) {
					log.warn("退款单 {} 置商品 {} 为 offshelf 时 0 行（商品状态可能已变更）", refund.getRefundId(), order.getProductId());
				}
			}
			else {
				log.warn("退款单 {} 的 prevOrderStatus={} 无商品处置规则", refund.getRefundId(), prevOrderStatus);
			}
		}
		catch (Exception e) {
			log.warn("[退款] 商品处置失败 orderId={}: {}", refund.getOrderId(), e.getMessage());
		}
	}

	/**
	 * 转人工：写 manual_required 日志并通知买家；运营通过退款管理页的 refunding 筛选发现待人工项
	 */
	private void markManualRequired(TradeRefund refund, String reason) {
		logMapper.insert(refund.getRefundId(), null, "manual_required", reason);
		notifyBuyer(refund.getBuyerId(), "退款处理中", "您的退款已转人工处理，请耐心等待");
	}

	/**
	 * 站内通知失败不得回滚主流程（对齐 ReportServiceImpl.notifyAfterCommit）
	 */
	private void notifyBuyer(Long userId, String title, String content) {
		try {
			imService.sendNotice(userId, "order", title, content);
		}
		catch (Exception e) {
			log.warn("[退款] 站内通知发送失败 userId={}: {}", userId, e.getMessage());
		}
	}

	/**
	 * 通知卖家；失败不得回滚退款主流程
	 */
	private void notifySeller(Long userId, String title, String content) {
		try {
			imService.sendNotice(userId, "order", title, content);
		}
		catch (Exception e) {
			log.warn("[退款] 卖家通知失败 userId={}: {}", userId, e.getMessage());
		}
	}

	/**
	 * 从候选中挑出本次商品订单对应的支付单
	 * <p>
	 * 候选已由 selectPaidByOrderId 按 biz_type='trade' 收窄（2026-11-03 消歧，历史撞号 17–21 号
	 * 不会再混入）。pay_user_id + 唯一性校验保留为防御：存量 pay_user_id 大量为 NULL，
	 * 此时仅候选唯一才执行，多条一律转人工（宁可不退，不可错退）。
	 * </p>
	 * @param candidates 按 order_id 查到的已支付支付单
	 * @param buyerId 退款单买家
	 * @return 选中的支付单；无法唯一确定时返回 null
	 */
	private Map<String, Object> pickPayOrder(List<Map<String, Object>> candidates, Long buyerId) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		Map<String, Object> matched = null;
		for (Map<String, Object> candidate : candidates) {
			Long payUserId = toLong(candidate.get("pay_user_id"));
			if (payUserId == null || !payUserId.equals(buyerId)) {
				continue;
			}
			if (matched == null || payIdOf(candidate) > payIdOf(matched)) {
				matched = candidate;
			}
		}
		if (matched != null) {
			return matched;
		}
		// 存量数据 pay_user_id 全为 NULL：候选唯一才允许执行
		if (candidates.size() == 1 && candidates.get(0).get("pay_user_id") == null) {
			return candidates.get(0);
		}
		return null;
	}

	private long payIdOf(Map<String, Object> payOrder) {
		Long payId = toLong(payOrder.get("pay_id"));
		return (payId != null) ? payId : -1L;
	}

	private Long toLong(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal) {
			return (BigDecimal) value;
		}
		try {
			return new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void judge(Long refundId, String decision, BigDecimal amount, String reason, Long operatorId) {
		if (!"buyer".equals(decision) && !"seller".equals(decision) && !"partial".equals(decision)) {
			throw new TWTException(DENY_JUDGE_DECISION_INVALID);
		}
		TradeRefund refund = requireRefund(refundId);
		if ("seller".equals(decision)) {
			int rows = refundMapper.updateStatus(refundId, "mediation", "closed");
			if (rows == 0) {
				throw new TWTException(DENY_REFUND_STATUS_CHANGED);
			}
			restoreOrder(refund, currentTid());
			logMapper.insert(refundId, operatorId, "judge", contentOr(reason, "裁决：驳回退款申请"));
			notifyBuyer(refund.getBuyerId(), "平台裁决结果", "平台已驳回退款申请");
			notifySeller(refund.getSellerId(), "平台裁决结果", "平台已驳回退款申请");
			return;
		}
		BigDecimal judgeAmount = refund.getAmount();
		if ("partial".equals(decision)) {
			if (amount == null || amount.signum() <= 0 || amount.compareTo(refund.getAmount()) > 0) {
				throw new TWTException(DENY_PARTIAL_AMOUNT_INVALID);
			}
			judgeAmount = amount;
		}
		int rows = refundMapper.updateStatus(refundId, "mediation", "refunding");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		logMapper.insert(refundId, operatorId, "judge",
				contentOr(reason, "partial".equals(decision) ? "裁决：部分退款 ¥" + judgeAmount : "裁决：全额退款给买家"));
		notifyBuyer(refund.getBuyerId(), "平台裁决结果", "平台裁决退款 ¥" + judgeAmount);
		notifySeller(refund.getSellerId(), "平台裁决结果", "平台裁决退款 ¥" + judgeAmount);
		executeRefund(refund, judgeAmount);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean judgeByOrder(Long orderId, String decision, BigDecimal amount, String reason, Long operatorId) {
		// 不依赖 trade_dispute.refund_id（该列全仓零写入点），按 order_id 反查进行中的退款单
		TradeRefund refund = refundMapper.selectActiveByOrderId(orderId);
		if (refund == null || !"mediation".equals(refund.getStatus())) {
			return false;
		}
		judge(refund.getRefundId(), decision, amount, reason, operatorId);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void resolve(Long refundId, String result, String note, Long operatorId) {
		if (!"success".equals(result) && !"fail".equals(result)) {
			throw new TWTException(DENY_RESOLVE_RESULT_INVALID);
		}
		TradeRefund refund = requireRefund(refundId);
		if ("success".equals(result)) {
			// 运营已在线下完成退款，不再调网关：outTradeNo / channel 传 null
			int rows = refundMapper.updateStatus(refundId, "refunding", "refund_success");
			if (rows == 0) {
				throw new TWTException(DENY_REFUND_STATUS_CHANGED);
			}
			finishRefundSuccess(refund, refund.getAmount(), null, null);
		}
		else {
			int rows = refundMapper.updateStatus(refundId, "refunding", "closed");
			if (rows == 0) {
				throw new TWTException(DENY_REFUND_STATUS_CHANGED);
			}
			restoreOrder(refund, currentTid());
		}
		String resolvedNote = contentOr(note, "运营手工收口");
		logMapper.insert(refundId, operatorId, "manual_resolved", resolvedNote);
		notifyBuyer(refund.getBuyerId(), "退款已收口", resolvedNote);
		notifySeller(refund.getSellerId(), "退款已收口", resolvedNote);
	}

	private String contentOr(String content, String defaultValue) {
		return (content != null && !content.isBlank()) ? content : defaultValue;
	}

	@Override
	public int scanTimeouts() {
		LocalDateTime now = LocalDateTime.now();
		int processed = 0;

		for (TradeRefund refund : refundMapper.selectSellerTimeout(now.minusHours(sellerTimeoutHours), SCAN_LIMIT)) {
			processed += runOne(refund, () -> processSellerTimeout(refund));
		}
		for (TradeRefund refund : refundMapper.selectReturnTimeout(now.minusDays(returnTimeoutDays), SCAN_LIMIT)) {
			processed += runOne(refund, () -> processReturnTimeout(refund));
		}
		for (TradeRefund refund : refundMapper.selectConfirmTimeout(now.minusDays(confirmTimeoutDays), SCAN_LIMIT)) {
			processed += runOne(refund, () -> processConfirmTimeout(refund));
		}
		return processed;
	}

	/**
	 * 单条执行：事务边界由 helper 提供，异常只告警，不得中断整批
	 * @param refund 退款单
	 * @param action 单条处理动作
	 * @return 成功处理返回 1，失败返回 0
	 */
	private int runOne(TradeRefund refund, java.util.function.Supplier<Integer> action) {
		try {
			Integer result = timeoutHelper.inTransaction(action);
			return result == null ? 0 : result;
		}
		catch (Exception e) {
			log.warn("[退款] 扫描退款单 {} 失败: {}", refund.getRefundId(), e.getMessage());
			return 0;
		}
	}

	/**
	 * 卖家超时未处理：推进到平台介入
	 */
	private int processSellerTimeout(TradeRefund refund) {
		// 售后矩阵：未发货（unshipped/paid）+ 仅退款，卖家 48h 未处理即自动同意；已发货不自动通过
		if (TYPE_REFUND_ONLY.equals(refund.getType())
				&& ("unshipped".equals(refund.getPrevOrderStatus()) || "paid".equals(refund.getPrevOrderStatus()))) {
			// 认领必须在独立事务中先提交：若网关调用后的写库失败，外层回滚但退款单保持 refunding，
			// 超时扫描只捞 pending_seller，绝不会再次调网关（防重复退款）；滞留单由运营手工收口
			timeoutHelper.inNewTransaction(() -> {
				int claimed = refundMapper.updateStatus(refund.getRefundId(), "pending_seller", "refunding");
				if (claimed == 0) {
					throw new TWTException(DENY_REFUND_STATUS_CHANGED);
				}
				logMapper.insert(refund.getRefundId(), null, "timeout_auto_agree", "卖家超时未处理，自动同意退款");
				return claimed;
			});
			boolean refunded = executeRefund(refund, refund.getAmount());
			if (refunded) {
				notifyBuyer(refund.getBuyerId(), "退款已自动同意", "卖家超时未处理，退款 ¥" + refund.getAmount() + " 已原路退回");
				notifySeller(refund.getSellerId(), "退款已自动同意", "订单未发货，系统已按超时规则自动退款");
			}
			else {
				// 网关失败时 executeRefund 已通知买家的「退款处理中」，这里不得再说「已原路退回」
				notifySeller(refund.getSellerId(), "退款已自动同意", "订单未发货，系统已自动同意，退款将转人工处理");
			}
			return 1;
		}
		int rows = refundMapper.updateStatus(refund.getRefundId(), "pending_seller", "mediation");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		logMapper.insert(refund.getRefundId(), null, "mediate", "卖家超时未处理，平台介入");
		notifyBuyer(refund.getBuyerId(), "退款已转平台介入", "卖家超时未处理，平台介入");
		notifySeller(refund.getSellerId(), "退款已转平台介入", "卖家超时未处理，平台介入");
		return 1;
	}

	/**
	 * 买家超时未寄回：关闭退款并恢复订单
	 */
	private int processReturnTimeout(TradeRefund refund) {
		int rows = refundMapper.updateStatus(refund.getRefundId(), "seller_agreed", "closed");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		// Quartz 无租户上下文：全租户扫描，tenantId 显式传 null
		restoreOrder(refund, null);
		logMapper.insert(refund.getRefundId(), null, "timeout_close", "买家超时未寄回，退款关闭");
		notifyBuyer(refund.getBuyerId(), "退款已关闭", "买家超时未寄回，退款关闭");
		notifySeller(refund.getSellerId(), "退款已关闭", "买家超时未寄回，退款关闭");
		return 1;
	}

	/**
	 * 卖家超时未确认收货：推进到平台介入
	 */
	private int processConfirmTimeout(TradeRefund refund) {
		int rows = refundMapper.updateStatus(refund.getRefundId(), "buyer_returned", "mediation");
		if (rows == 0) {
			throw new TWTException(DENY_REFUND_STATUS_CHANGED);
		}
		logMapper.insert(refund.getRefundId(), null, "mediate", "卖家超时未确认收货，平台介入");
		notifyBuyer(refund.getBuyerId(), "退款已转平台介入", "卖家超时未确认收货，平台介入");
		notifySeller(refund.getSellerId(), "退款已转平台介入", "卖家超时未确认收货，平台介入");
		return 1;
	}

}