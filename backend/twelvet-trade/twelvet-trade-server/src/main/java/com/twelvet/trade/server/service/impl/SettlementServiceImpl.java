package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.trade.api.domain.TradeOrder;
import com.twelvet.trade.server.constant.BalanceFlowType;
import com.twelvet.trade.server.constant.SettlementStatus;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.domain.TradeRefund;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.ISettlementService;
import com.twelvet.trade.server.settlement.PayoutGateway;
import com.twelvet.trade.server.settlement.PayoutResult;
import com.twelvet.trade.server.settlement.SettlementPayoutHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算服务实现
 *
 * @author twelvet
 */
@Service
public class SettlementServiceImpl implements ISettlementService {

	private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);

	private final SettlementOrderMapper settlementMapper;

	private final PayoutGateway payoutGateway;

	private final SettlementPayoutHelper payoutHelper;

	private final SellerAccountMapper sellerAccountMapper;

	private final ISellerAccountService sellerAccountService;

	/** 平台佣金率（0 = 本期不抽佣）；配置漏填走默认 0，见 spec §3 */
	@Value("${trade.settlement.commission-rate:0}")
	private BigDecimal commissionRate = BigDecimal.ZERO;

	/** 售后期天数（订单完成后 N 天） */
	@Value("${trade.settlement.hold-days:7}")
	private int holdDays = 7;

	/** 打款失败次数上限，达到后不再自动重试（转人工） */
	@Value("${trade.settlement.max-fail-count:3}")
	private int maxFailCount = 3;

	public SettlementServiceImpl(SettlementOrderMapper settlementMapper, PayoutGateway payoutGateway,
			SettlementPayoutHelper payoutHelper, SellerAccountMapper sellerAccountMapper,
			ISellerAccountService sellerAccountService) {
		this.settlementMapper = settlementMapper;
		this.payoutGateway = payoutGateway;
		this.payoutHelper = payoutHelper;
		this.sellerAccountMapper = sellerAccountMapper;
		this.sellerAccountService = sellerAccountService;
	}

	@Override
	public void createOnPaySuccess(TradeOrder order, String channel) {
		BigDecimal gross = order.getTotalAmount();
		BigDecimal commission = gross.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
		SettlementOrder settlement = new SettlementOrder();
		settlement.setOrderId(order.getOrderId());
		settlement.setSellerId(order.getSellerId());
		settlement.setBuyerId(order.getBuyerId());
		settlement.setTenantId(order.getTenantId());
		settlement.setGrossAmount(gross);
		settlement.setCommissionAmount(commission);
		settlement.setNetAmount(gross.subtract(commission));
		settlement.setChannel(channel == null || channel.isBlank() ? "mock" : channel);
		settlement.setStatus(SettlementStatus.HOLDING);
		int rows = settlementMapper.insertIgnore(settlement);
		if (rows == 0) {
			log.info("[结算] 结算单已存在，跳过 orderId={}", order.getOrderId());
		}
	}

	@Override
	public int scanDueHoldings(int limit) {
		List<SettlementOrder> candidates = settlementMapper.selectDueHoldings(limit, holdDays);
		int promoted = 0;
		for (SettlementOrder settlement : candidates) {
			try {
				// 守卫失败（并发退款已关闭结算单）= 0 行，跳过不重试
				int rows = settlementMapper.markPending(settlement.getSettlementId(), holdDays);
				if (rows > 0) {
					promoted++;
				}
			}
			catch (Exception e) {
				// 单条失败不影响其余候选
				log.warn("[结算] 推进待结算失败 settlementId={}: {}", settlement.getSettlementId(), e.getMessage());
			}
		}
		return promoted;
	}

	@Override
	public int payoutBatch(int limit) {
		List<SettlementOrder> candidates = settlementMapper.selectPendingPayout(limit, maxFailCount);
		int settled = 0;
		for (SettlementOrder settlement : candidates) {
			PayoutResult result = payoutGateway.submit(settlement);
			if (!result.isSuccess()) {
				settlementMapper.incrementFailCount(settlement.getSettlementId());
				log.warn("[结算] 渠道打款失败 settlementId={} retryable={} message={}", settlement.getSettlementId(),
						result.isRetryable(), result.getMessage());
				continue;
			}
			try {
				// markSettled + 入账同事务：任一步失败整体回滚，fail_count 留在事务外，下一批次重试
				if (payoutHelper.payoutOne(settlement, result.getChannelTransNo())) {
					settled++;
				}
			}
			catch (Exception e) {
				settlementMapper.incrementFailCount(settlement.getSettlementId());
				log.warn("[结算] 落账失败 settlementId={}: {}", settlement.getSettlementId(), e.getMessage());
			}
		}
		return settled;
	}

	/**
	 * 退款成功互斥结算。
	 * <p>
	 * REQUIRES_NEW：退款主事务已进入收尾，结算异常（余额冲突等）必须只回滚自身的扣回，不能把退款事务标记
	 * rollback-only——否则「结算失败不阻断退款」在外层捕获后仍会在提交时炸掉。
	 * </p>
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void onRefundSuccess(TradeRefund refund, TradeOrder order, BigDecimal refundedAmount) {
		Long orderId = order != null ? order.getOrderId() : refund.getOrderId();
		int closed = settlementMapper.markClosed(orderId, refund.getRefundId());
		if (closed > 0) {
			// 售后期内退款：结算单作废，无需扣款
			return;
		}
		SettlementOrder settlement = settlementMapper.selectByOrderId(orderId);
		if (settlement == null || SettlementStatus.CLOSED.equals(settlement.getStatus())) {
			return;
		}
		if (!SettlementStatus.SETTLED.equals(settlement.getStatus())
				&& !SettlementStatus.WITHDRAWN.equals(settlement.getStatus())) {
			// holding/pending_settle 但 markClosed 0 行：并发已处理，跳过
			log.warn("[结算] 退款成功时结算单状态未处理 settlementId={} status={}", settlement.getSettlementId(),
					settlement.getStatus());
			return;
		}
		SellerAccount account = sellerAccountMapper.selectBySeller(settlement.getSellerId(), settlement.getTenantId());
		if (account == null) {
			log.warn("[结算] 扣回找不到卖家账户 settlementId={} sellerId={}", settlement.getSettlementId(),
					settlement.getSellerId());
			return;
		}
		SellerBalanceFlow flow = new SellerBalanceFlow();
		flow.setSellerId(settlement.getSellerId());
		flow.setTenantId(settlement.getTenantId());
		flow.setSettlementId(settlement.getSettlementId());
		flow.setRefundId(refund.getRefundId());
		flow.setType(BalanceFlowType.CLAWBACK);
		// 部分退款以实际执行金额扣回；refund.amount 仍是原始申请金额，直接取它会多扣卖家
		BigDecimal clawbackAmount = refundedAmount != null ? refundedAmount : refund.getAmount();
		flow.setAmount(clawbackAmount.negate());
		flow.setBizKey("clawback:" + refund.getRefundId());
		boolean applied = sellerAccountService.applyBalance(flow);
		if (!applied) {
			// 幂等键已消费：此退款已扣回过，不重复标记、不冻结
			log.info("[结算] 扣回流水已存在 refundId={}", refund.getRefundId());
			return;
		}
		settlementMapper.markClawback(settlement.getSettlementId(), refund.getRefundId());
		if (flow.getBalanceAfter() != null && flow.getBalanceAfter().signum() < 0) {
			sellerAccountMapper.setFrozen(account.getAccountId(), 1, settlement.getTenantId());
			log.warn("[结算] 卖家余额为负已冻结 sellerId={} settlementId={}", settlement.getSellerId(),
					settlement.getSettlementId());
		}
	}

	@Override
	public TableDataInfo<SettlementOrder> adminPage(String status, Long tenantId) {
		PageUtils.startPage();
		List<SettlementOrder> rows = settlementMapper.selectAdminPage(status, tenantId);
		return PageUtils.getDataTable(rows);
	}

	@Override
	public Map<String, Object> adminDetail(Long settlementId, Long tenantId) {
		SettlementOrder settlement = settlementMapper.selectById(settlementId, tenantId);
		if (settlement == null) {
			throw new TWTException("结算单不存在");
		}
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("settlementId", settlement.getSettlementId());
		detail.put("orderId", settlement.getOrderId());
		detail.put("refundId", settlement.getRefundId());
		detail.put("sellerId", settlement.getSellerId());
		detail.put("buyerId", settlement.getBuyerId());
		detail.put("tenantId", settlement.getTenantId());
		detail.put("grossAmount", settlement.getGrossAmount());
		detail.put("commissionAmount", settlement.getCommissionAmount());
		detail.put("netAmount", settlement.getNetAmount());
		detail.put("status", settlement.getStatus());
		detail.put("doneAt", settlement.getDoneAt());
		detail.put("holdUntil", settlement.getHoldUntil());
		detail.put("settledAt", settlement.getSettledAt());
		detail.put("channel", settlement.getChannel());
		detail.put("channelTransNo", settlement.getChannelTransNo());
		detail.put("failCount", settlement.getFailCount());
		detail.put("createTime", settlement.getCreateTime());
		detail.put("updateTime", settlement.getUpdateTime());
		return detail;
	}

}