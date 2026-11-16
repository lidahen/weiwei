package com.twelvet.trade.server.constant;

import java.util.Set;

/**
 * 卖家余额流水的业务类型（落库字符串）
 *
 * @author twelvet
 */
public final class BalanceFlowType {

	/** 结算入账 */
	public static final String SETTLE_IN = "settle_in";

	/** 结算后扣回（负数） */
	public static final String CLAWBACK = "clawback";

	/** 提现扣减（申请时即扣，负数） */
	public static final String WITHDRAW_OUT = "withdraw_out";

	/** 人工调整（提现驳回退回等） */
	public static final String ADJUST = "adjust";

	/** 合法类型全集 */
	public static final Set<String> ALL = Set.of(SETTLE_IN, CLAWBACK, WITHDRAW_OUT, ADJUST);

	private BalanceFlowType() {
	}

}