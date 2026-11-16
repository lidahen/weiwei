package com.twelvet.trade.server.settlement;

import com.twelvet.trade.server.domain.SettlementOrder;

/**
 * 打款渠道抽象（Phase 1 只有 mock；真实渠道接入时按 settlementId 幂等）
 *
 * @author twelvet
 */
public interface PayoutGateway {

	/**
	 * 提交打款
	 * @param settlement 结算单
	 * @return 提交结果
	 */
	PayoutResult submit(SettlementOrder settlement);

	/**
	 * 查询渠道流水状态
	 * @param channelTransNo 渠道流水号
	 * @return 渠道状态
	 */
	String query(String channelTransNo);

}