package com.twelvet.trade.server.settlement;

/**
 * 打款渠道提交结果（不可变值对象）
 *
 * @author twelvet
 */
public final class PayoutResult {

	private final boolean success;

	private final String channelTransNo;

	private final boolean retryable;

	private final String message;

	private PayoutResult(boolean success, String channelTransNo, boolean retryable, String message) {
		this.success = success;
		this.channelTransNo = channelTransNo;
		this.retryable = retryable;
		this.message = message;
	}

	public static PayoutResult ok(String channelTransNo) {
		return new PayoutResult(true, channelTransNo, false, null);
	}

	public static PayoutResult retry(String message) {
		return new PayoutResult(false, null, true, message);
	}

	public static PayoutResult fail(String message) {
		return new PayoutResult(false, null, false, message);
	}

	public boolean isSuccess() {
		return success;
	}

	public String getChannelTransNo() {
		return channelTransNo;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public String getMessage() {
		return message;
	}

}