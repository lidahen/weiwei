package com.twelvet.trade.server.settlement;

import com.twelvet.trade.server.domain.SettlementOrder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mock 打款渠道：同步成功，按结算单幂等
 * <p>
 * 同一 settlementId 重复提交返回同一渠道流水号（MOCK + settlementId），避免渠道侧重复打款。
 * </p>
 *
 * @author twelvet
 */
@Service("mockPayoutGateway")
public class MockPayoutGateway implements PayoutGateway {

	private final Map<Long, String> transNos = new ConcurrentHashMap<>();

	@Override
	public PayoutResult submit(SettlementOrder settlement) {
		String transNo = transNos.computeIfAbsent(settlement.getSettlementId(), settlementId -> "MOCK" + settlementId);
		return PayoutResult.ok(transNo);
	}

	@Override
	public String query(String channelTransNo) {
		return "success";
	}

}