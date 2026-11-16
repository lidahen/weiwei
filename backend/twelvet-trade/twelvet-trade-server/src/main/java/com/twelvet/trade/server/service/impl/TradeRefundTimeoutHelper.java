package com.twelvet.trade.server.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 退款超时扫描的单条事务包装。
 * <p>
 * 为什么是独立 bean：scanTimeouts 在同一类内循环，直接内联多步写库时，中间失败会留下「退款单已改状态但订单未恢复」的 半截数据；同类自调用不经过 Spring
 * 代理，方法上的 @Transactional 不生效，因此把事务边界放到独立 bean。
 * </p>
 *
 * @author twelvet
 */
@Component
public class TradeRefundTimeoutHelper {

	/**
	 * 在独立事务中执行单条退款单的超时处理
	 * @param action 单条处理动作
	 * @param <T> 返回值类型
	 * @return 动作返回值
	 */
	@Transactional(rollbackFor = Exception.class)
	public <T> T inTransaction(Supplier<T> action) {
		return action.get();
	}

	/**
	 * 在独立事务（REQUIRES_NEW）中执行：用于自动同意的「认领」。
	 * <p>
	 * 认领（pending_seller → refunding）必须先于退款网关提交。若网关调用之后的写库失败， 外层事务回滚但认领已落库，退款单停留在
	 * refunding，超时扫描只捞 pending_seller， 因此不会再次调用网关造成重复退款；滞留的 refunding 由运营「退款中」页签人工收口。
	 * </p>
	 * @param action 认领动作
	 * @param <T> 返回值类型
	 * @return 动作返回值
	 */
	@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
	public <T> T inNewTransaction(Supplier<T> action) {
		return action.get();
	}

}