package com.twelvet.trade.server.constant;

import java.util.Set;

/**
 * 结算单状态（落库字符串，禁止 enum ordinal）
 *
 * @author twelvet
 */
public final class SettlementStatus {

	/** 担保中：订单已支付，等待售后期届满 */
	public static final String HOLDING = "holding";

	/** 待打款：售后期届满且无进行中退款，等待批次提交渠道 */
	public static final String PENDING_SETTLE = "pending_settle";

	/** 已结算：渠道打款成功且账本已入账 */
	public static final String SETTLED = "settled";

	/** 已提现：保留状态，Phase 1 提现是账户级，不使用 */
	public static final String WITHDRAWN = "withdrawn";

	/** 已关闭：窗口内退款成功，结算单作废 */
	public static final String CLOSED = "closed";

	/** 已扣回：结算后退款，余额账本记 clawback */
	public static final String CLAWBACK = "clawback";

	/** 合法状态全集（对账/后台筛选校验用） */
	public static final Set<String> ALL = Set.of(HOLDING, PENDING_SETTLE, SETTLED, WITHDRAWN, CLOSED, CLAWBACK);

	private SettlementStatus() {
	}

}