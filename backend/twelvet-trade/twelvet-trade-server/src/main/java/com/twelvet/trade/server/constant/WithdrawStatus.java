package com.twelvet.trade.server.constant;

import java.util.Set;

/**
 * 提现单状态（落库字符串）
 *
 * @author twelvet
 */
public final class WithdrawStatus {

	/** 待审核 */
	public static final String PENDING = "pending";

	/** 审核通过，待打款 */
	public static final String APPROVED = "approved";

	/** 已打款（回填凭证） */
	public static final String PAID = "paid";

	/** 已驳回（余额已退回） */
	public static final String REJECTED = "rejected";

	/** 合法状态全集 */
	public static final Set<String> ALL = Set.of(PENDING, APPROVED, PAID, REJECTED);

	private WithdrawStatus() {
	}

}