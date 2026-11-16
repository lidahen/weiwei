package com.twelvet.trade.server.service.impl;

import com.twelvet.product.api.domain.FunnelEvent;
import com.twelvet.product.api.service.IFunnelEventService;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.mapper.TradeOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超时任务单笔处理器（quartz 循环体内逐单调用的原子单元）
 * <p>
 * 为什么是独立 bean：{@link TradeOrderServiceImpl#cancelExpiredUnpaid()} 的循环若直接内联
 * 「关单+回补」，两步不在同一事务且异常被吞——回补失败后订单已 closed（扫描谓词 status='unpaid' 永不再命中），商品永久 locked，仅手工 SQL
 * 可修复。把单笔处理提为独立 Spring bean 的 @Transactional 方法（同类自调用不经过代理、事务不生效，这是已知陷阱）， 回补失败时关单一并回滚，下轮
 * 5 分钟重扫自动重试。
 * </p>
 * <p>
 * 租户设计：quartz 无租户上下文，mapper 一律传 null（全租户语义）。
 * </p>
 *
 * @author twelvet
 */
@Component
public class TradeOrderTimeoutHelper {

	private static final Logger log = LoggerFactory.getLogger(TradeOrderTimeoutHelper.class);

	private final TradeOrderMapper orderMapper;

	private final ProductMapper productMapper;

	private final IFunnelEventService funnelEventService;

	public TradeOrderTimeoutHelper(TradeOrderMapper orderMapper, ProductMapper productMapper,
			IFunnelEventService funnelEventService) {
		this.orderMapper = orderMapper;
		this.productMapper = productMapper;
		this.funnelEventService = funnelEventService;
	}

	/**
	 * 关闭单个超时未支付订单并回补库存（同一事务）
	 * @return true = 关单成功（rows=1）；false = 状态已流转被跳过（rows=0，正常并发，非失败）
	 * @throws RuntimeException 回补失败时向外传播（事务回滚关单，调用方 catch 后继续下一单， 本单下轮重扫重试）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean closeSingleExpiredOrder(Long orderId, Long productId) {
		// 原子关单：仅 status='unpaid' 命中。rows=0 = 已被买家取消/已支付/状态已流转——
		// 绝不回补库存（与买家取消并发时只有一方 rows=1，防双倍回补）
		int rows = orderMapper.closeOrder(orderId);
		if (rows == 0) {
			return false;
		}
		// 同事务回补：失败即整体回滚（订单回到 unpaid，下轮重扫重试），商品不会滞留 locked
		productMapper.restoreStock(productId, null);
		return true;
	}

	/**
	 * 自动确认单个到期订单并标记商品 sold（同一事务）
	 * @return true = 确认成功（rows=1）；false = 状态已流转被跳过（rows=0）
	 * @throws RuntimeException markSold 失败时向外传播（事务回滚确认，下轮重扫重试）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean confirmSingleOrder(Long orderId, Long productId) {
		// 原子确认：仅 status='shipped' 命中。rows=0 = 买家已主动确认/并发抢先——不标记商品
		int rows = orderMapper.autoConfirmOrder(orderId);
		if (rows == 0) {
			return false;
		}
		// 同事务标记：markSold 带 stock=0 AND status='locked' 守卫，多库存商品 rows=0 属正常
		productMapper.markSold(productId, null);
		// 核心漏斗（spec §6.3）：quartz 无租户上下文，事件租户/买家必须从订单取。
		// 埋点整体兜底：读订单/写事件失败都不得让已成功的自动确认回滚
		try {
			TradeOrder order = orderMapper.selectOrderDetail(orderId, null);
			if (order != null) {
				FunnelEvent funnelEvent = new FunnelEvent();
				funnelEvent.setEventType(FunnelEvent.TYPE_CONFIRM_RECEIPT);
				funnelEvent.setTenantId(order.getTenantId());
				funnelEvent.setUserId(order.getBuyerId());
				funnelEvent.setRole("buyer");
				funnelEvent.setProductId(productId);
				funnelEvent.setBizId(String.valueOf(orderId));
				funnelEvent.setEventKey("confirm_receipt:" + orderId);
				funnelEventService.record(funnelEvent);
			}
		}
		catch (Exception e) {
			log.warn("[漏斗] 自动确认事件记录失败 orderId={}: {}", orderId, e.getMessage());
		}
		return true;
	}

}
