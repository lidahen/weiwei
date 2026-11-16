package com.twelvet.trade.server.settlement;

import com.twelvet.trade.server.constant.BalanceFlowType;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.SettlementOrder;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单笔结算落账（markSettled 与余额入账同事务）
 * <p>
 * 任一步失败整体回滚：不会出现「渠道已打款但账本没记」或「账本记了但状态没推进」。 调用方（批次）负责把异常转成 fail_count。
 * </p>
 *
 * @author twelvet
 */
@Component
public class SettlementPayoutHelper {

	private static final Logger log = LoggerFactory.getLogger(SettlementPayoutHelper.class);

	private final SettlementOrderMapper settlementMapper;

	private final ISellerAccountService sellerAccountService;

	public SettlementPayoutHelper(SettlementOrderMapper settlementMapper, ISellerAccountService sellerAccountService) {
		this.settlementMapper = settlementMapper;
		this.sellerAccountService = sellerAccountService;
	}

	/**
	 * 单笔打款落账
	 * @param settlement 结算单
	 * @param channelTransNo 渠道流水号
	 * @return true=推进成功；false=并发已结算（markSettled 0 行）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean payoutOne(SettlementOrder settlement, String channelTransNo) {
		int rows = settlementMapper.markSettled(settlement.getSettlementId(), settlement.getChannel(), channelTransNo);
		if (rows == 0) {
			// 并发已被其它批次结算，跳过
			return false;
		}
		SellerBalanceFlow flow = new SellerBalanceFlow();
		flow.setSellerId(settlement.getSellerId());
		flow.setTenantId(settlement.getTenantId());
		flow.setSettlementId(settlement.getSettlementId());
		flow.setType(BalanceFlowType.SETTLE_IN);
		flow.setAmount(settlement.getNetAmount());
		flow.setBizKey("settle:" + settlement.getSettlementId());
		if (!sellerAccountService.applyBalance(flow)) {
			// 幂等键已存在：此前已完成入账（数据修复场景），不再重复记账
			log.warn("[结算] 入账流水已存在 settlementId={}", settlement.getSettlementId());
		}
		return true;
	}

}