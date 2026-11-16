package com.twelvet.trade.server.listener;

import com.twelvet.im.server.service.IImService;
import com.twelvet.payment.api.event.PaySuccessEvent;
import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import com.twelvet.trade.server.mapper.TradePayFlowMapper;
import com.twelvet.trade.server.service.ISettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 支付成功事件监听器（trade 模块）
 * <p>
 * 重构后：原 PayOrderService.handlePaySuccess 中的 "UPDATE trade_order SET status = 'paid'"
 * 逻辑迁移至此。
 * </p>
 * <p>
 * 通过 Spring 事件机制解耦：payment 发布事件 → trade 监听处理， payment 不再依赖 trade 的任何类。
 * </p>
 *
 * @author twelvet
 */
@Component
public class PaySuccessEventListener {

	private static final Logger log = LoggerFactory.getLogger(PaySuccessEventListener.class);

	private final TradeOrderMapper orderMapper;

	private final TradePayFlowMapper flowMapper;

	private final IImService imService;

	private final IFunnelEventService funnelEventService;

	private final ISettlementService settlementService;

	private final IPayOrderService payOrderService;

	public PaySuccessEventListener(TradeOrderMapper orderMapper, TradePayFlowMapper flowMapper, IImService imService,
			IFunnelEventService funnelEventService, IPayOrderService payOrderService, ISettlementService settlementService) {
		this.orderMapper = orderMapper;
		this.flowMapper = flowMapper;
		this.imService = imService;
		this.funnelEventService = funnelEventService;
		this.payOrderService = payOrderService;
		this.settlementService = settlementService;
	}

	/**
	 * 站内通知（尽力而为：IM 故障不得阻断支付回调链路）
	 */
	private void notify(Long userId, String title, String content) {
		if (userId == null) {
			return;
		}
		try {
			imService.sendNotice(userId, "order", title, content);
		}
		catch (Exception e) {
			log.warn("[支付] 通知失败 userId={} title={}: {}", userId, title, e.getMessage());
		}
	}

	@EventListener
	public void onPaySuccess(PaySuccessEvent event) {
		Long orderId = event.getOrderId();
		// bizType 由 publishPaySuccess 从 trade_pay.biz_type 读出（消除多态后的权威判别），
		// 取代旧 orderId==null 即续费的判别——runner 事件也不再进本监听器
		if (orderId == null || !"trade".equals(event.getBizType())) {
			return;
		}

		LocalDateTime now = event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now();
		// updateOrderStatus: UPDATE trade_order SET status='unshipped',
		// paid_at=sysdate(),
		// update_time=sysdate()
		// 置 unshipped（待发货）而非野状态 paid：paid 只作为存量数据被兼容读取（发货 SQL 的
		// IN 列表），不再写入。unshipped 直接命中发货链路 status='unshipped' 条件。
		// tenantId=null 跳过租户过滤（回调无租户上下文，按 orderId 全局定位）
		int rows = orderMapper.updateOrderStatus(orderId, "unshipped", "paid_at", null);
		if (rows > 0) {
			log.info("订单已标记为已支付: orderId={}, outTradeNo={}", orderId, event.getOutTradeNo());
			// 资金对账页的数据来源：支付成功写一条 pay 流水。写入失败不得回滚主流程
			try {
				BigDecimal amount = (event.getAmountCents() == null) ? null
						: new BigDecimal(event.getAmountCents()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
				flowMapper.insert(orderId, "pay", amount, event.getChannel(), event.getOutTradeNo());
			}
			catch (Exception e) {
				log.warn("[支付] 写资金流水失败 orderId={}: {}", orderId, e.getMessage());
			}
			// 支付成功通知买卖双方（回调无租户上下文，按 orderId 全局定位订单）
			TradeOrder order = orderMapper.selectOrderDetail(orderId, null);
			if (order != null) {
				notify(order.getBuyerId(), "支付成功", "订单已支付，等待卖家发货");
				notify(order.getSellerId(), "买家已付款", "请尽快发货");
				// 核心漏斗（spec §6.3）：支付成功。回调线程无租户上下文，租户必须来自订单
				FunnelEvent funnelEvent = new FunnelEvent();
				funnelEvent.setEventType(FunnelEvent.TYPE_PAY_SUCCESS);
				funnelEvent.setTenantId(order.getTenantId());
				funnelEvent.setUserId(order.getBuyerId());
				funnelEvent.setRole("buyer");
				funnelEvent.setProductId(order.getProductId());
				funnelEvent.setBizId(String.valueOf(orderId));
				funnelEvent.setEventKey("pay_success:" + orderId);
				funnelEventService.record(funnelEvent);
				// 担保交易：支付成功即创建 holding 结算单。写失败不阻断支付后处理——
				// 订单已置 unshipped，对账会以 missing_settlement 发现并补偿
				try {
					settlementService.createOnPaySuccess(order, event.getChannel());
				}
				catch (Exception e) {
					log.error("[结算] 创建结算单失败 orderId={}: {}", orderId, e.getMessage());
				}
			}
		}
		else {
			// rows=0 有两种语义：订单已关/已取消（该退款）与事件重发、订单已 unshipped（绝不能退）。
			// 不能用 rows=0 触发退款，必须显式查单状态——仅 closed/cancelled 才自动原路退款
			TradeOrder order = orderMapper.selectOrderDetail(orderId, null);
			String status = order != null ? order.getStatus() : null;
			if ("closed".equals(status) || "cancelled".equals(status)) {
				log.warn("[支付] 迟到支付：订单已 {}，自动原路退款 orderId={}, outTradeNo={}", status, orderId,
						event.getOutTradeNo());
				BigDecimal amount = event.getAmountCents() == null ? null
						: new BigDecimal(event.getAmountCents()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
				boolean ok = payOrderService.refund(event.getOutTradeNo(), amount, "订单已超时关闭，自动退款");
				if (order.getBuyerId() != null) {
					notify(order.getBuyerId(), "支付时订单已关闭",
							ok ? "您支付的款项将原路退回" : "退款处理失败，请联系客服");
				}
			}
			else {
				log.info("订单已处于已支付状态或不存在: orderId={}, outTradeNo={}", orderId, event.getOutTradeNo());
			}
		}
	}

}
