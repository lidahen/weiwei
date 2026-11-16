package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.mapper.CsDisputeMapper;
import com.twelvet.trade.server.service.ITradeRefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纠纷裁决联动退款测试
 * <p>
 * 锁定不变量：
 * <ol>
 * <li>裁决成功（rows=1）后按 order_id 反查退款单联动退款（不依赖 trade_dispute.refund_id，该列全仓零写入点）</li>
 * <li>无退款单属正常（纠纷可能不走退款），不报错</li>
 * <li>已关闭的纠纷不可重复裁决：UPDATE 0 行必须抛，不再静默返回 OK</li>
 * </ol>
 * </p>
 */
class CsDisputeServiceImplJudgeLinkTest {

	private static final Long DISPUTE_ID = 9L;

	private static final Long ORDER_ID = 1L;

	private static final Long CS_ID = 900L;

	private final CsDisputeMapper disputeMapper = mock(CsDisputeMapper.class);

	private final ITradeRefundService refundService = mock(ITradeRefundService.class);

	private final CsDisputeServiceImpl service = new CsDisputeServiceImpl(disputeMapper, refundService);

	private Map<String, Object> detail() {
		Map<String, Object> detail = new HashMap<>();
		detail.put("orderId", ORDER_ID);
		detail.put("dispute_id", DISPUTE_ID);
		return detail;
	}

	@Test
	@DisplayName("裁决 buyer 且订单有 mediation 退款单 → 触发退款")
	void judge_buyer_triggersRefund() {
		BigDecimal amount = new BigDecimal("1107.00");
		when(disputeMapper.selectDisputeDetail(DISPUTE_ID)).thenReturn(detail());
		when(disputeMapper.judgeDispute(DISPUTE_ID, "buyer", amount, "买家举证充分", CS_ID)).thenReturn(1);
		when(refundService.judgeByOrder(ORDER_ID, "buyer", amount, "买家举证充分", CS_ID)).thenReturn(true);

		assertEquals("OK", service.judgeDispute(DISPUTE_ID, "buyer", amount, "买家举证充分", CS_ID));

		verify(refundService).judgeByOrder(ORDER_ID, "buyer", amount, "买家举证充分", CS_ID);
	}

	@Test
	@DisplayName("裁决 buyer 但无退款单 → 只裁决，不报错")
	void judge_noRefund_doesNotThrow() {
		BigDecimal amount = new BigDecimal("1107.00");
		when(disputeMapper.selectDisputeDetail(DISPUTE_ID)).thenReturn(detail());
		when(disputeMapper.judgeDispute(DISPUTE_ID, "buyer", amount, "理由", CS_ID)).thenReturn(1);
		when(refundService.judgeByOrder(ORDER_ID, "buyer", amount, "理由", CS_ID)).thenReturn(false);

		assertEquals("OK", service.judgeDispute(DISPUTE_ID, "buyer", amount, "理由", CS_ID));
	}

	@Test
	@DisplayName("已关闭的纠纷不可重复裁决（UPDATE 0 行 → 抛，不再静默 OK）")
	void judge_alreadyClosed_rejected() {
		when(disputeMapper.selectDisputeDetail(DISPUTE_ID)).thenReturn(detail());
		when(disputeMapper.judgeDispute(DISPUTE_ID, "buyer", null, "理由", CS_ID)).thenReturn(0);

		assertEquals("纠纷工单已关闭", assertThrows(TWTException.class,
				() -> service.judgeDispute(DISPUTE_ID, "buyer", null, "理由", CS_ID)).getMessage());
		verify(refundService, never()).judgeByOrder(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("裁决 seller → 退款单关闭，不调网关")
	void judge_seller_closesRefund() {
		when(disputeMapper.selectDisputeDetail(DISPUTE_ID)).thenReturn(detail());
		when(disputeMapper.judgeDispute(DISPUTE_ID, "seller", null, "买家证据不足", CS_ID)).thenReturn(1);

		assertEquals("OK", service.judgeDispute(DISPUTE_ID, "seller", null, "买家证据不足", CS_ID));

		verify(refundService).judgeByOrder(ORDER_ID, "seller", null, "买家证据不足", CS_ID);
	}

}