package com.twelvet.runner.server.service.impl;

import com.twelvet.payment.api.service.IPayOrderService;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时未接单自动退款测试（2026-11-03 批次1）
 * <p>
 * 锁定不变量：付费任务超时取消 → 调 IPayOrderService.refund 并标记任务已退款；
 * refund 失败不阻断批次（任务保持 paid 待人工/重试）；免费任务不碰支付服务。
 * </p>
 */
class RunnerTaskScanTimeoutRefundTest {

	private final RunnerTaskMapper taskMapper = mock(RunnerTaskMapper.class);

	private final RunnerTaskStateMachine stateMachine = mock(RunnerTaskStateMachine.class);

	private final IPayOrderService payOrderService = mock(IPayOrderService.class);

	private final RunnerTaskServiceImpl service = new RunnerTaskServiceImpl(taskMapper, stateMachine, payOrderService);

	private RunnerTask paidTask(Long id) {
		RunnerTask task = new RunnerTask();
		task.setId(id);
		task.setStatus(RunnerTask.STATUS_PENDING);
		task.setIsPaid(RunnerTask.IS_PAID_YES);
		task.setPrice(new BigDecimal("5.00"));
		task.setPayOutTradeNo("PAY1");
		task.setPublisherId(10L);
		task.setTenantId(1L);
		return task;
	}

	@Test
	@DisplayName("付费任务超时取消成功：调 refund 并标记任务已退款")
	void paidTaskCancelled_refundAndMark() {
		RunnerTask task = paidTask(1L);
		when(taskMapper.selectExpiredPending(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task));
		when(stateMachine.doCancel(eq(1L), eq(RunnerTask.STATUS_CANCELLED), contains("超时"), eq(10L), eq(1L)))
			.thenReturn(true);
		when(payOrderService.refund(eq("PAY1"), eq(new BigDecimal("5.00")), contains("超时"))).thenReturn(true);

		int count = service.scanTimeoutPending();

		assertEquals(1, count);
		verify(payOrderService).refund(eq("PAY1"), eq(new BigDecimal("5.00")), contains("超时"));
		verify(taskMapper).markPayRefundedByTaskId(1L);
	}

	@Test
	@DisplayName("refund 失败：不抛异常、任务保持 paid、取消计数不受影响")
	void refundFails_taskStaysPaid_noThrow() {
		RunnerTask task = paidTask(2L);
		when(taskMapper.selectExpiredPending(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task));
		when(stateMachine.doCancel(eq(2L), eq(RunnerTask.STATUS_CANCELLED), contains("超时"), eq(10L), eq(1L)))
			.thenReturn(true);
		when(payOrderService.refund(eq("PAY1"), eq(new BigDecimal("5.00")), contains("超时"))).thenReturn(false);

		assertDoesNotThrow(() -> {
			int count = service.scanTimeoutPending();
			assertEquals(1, count);
		});
		verify(taskMapper, never()).markPayRefundedByTaskId(2L);
	}

	@Test
	@DisplayName("免费任务超时取消：不碰支付服务")
	void unpaidTask_noRefund() {
		RunnerTask task = paidTask(3L);
		task.setIsPaid(RunnerTask.IS_PAID_NO);
		task.setPayOutTradeNo(null);
		when(taskMapper.selectExpiredPending(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task));
		when(stateMachine.doCancel(eq(3L), eq(RunnerTask.STATUS_CANCELLED), contains("超时"), eq(10L), eq(1L)))
			.thenReturn(true);

		int count = service.scanTimeoutPending();

		assertEquals(1, count);
		verify(payOrderService, never()).refund(any(), any(), any());
		verify(taskMapper, never()).markPayRefundedByTaskId(3L);
	}


	// ===== 批次2：cancelled+paid 滞留对账（退款失败兜底重试） =====

	@Test
	@DisplayName("对账扫描：cancelled 且 pay_status=paid 的任务重试退款并标记")
	void reconcile_cancelledPaid_retriesRefund() {
		RunnerTask task = paidTask(11L);
		task.setStatus(RunnerTask.STATUS_CANCELLED);
		when(taskMapper.selectCancelledPaid(200)).thenReturn(List.of(task));
		when(payOrderService.refund(eq("PAY1"), eq(new BigDecimal("5.00")), contains("对账"))).thenReturn(true);

		int count = service.reconcileStalePaidRefunds();

		assertEquals(1, count);
		verify(payOrderService).refund(eq("PAY1"), eq(new BigDecimal("5.00")), contains("对账"));
		verify(taskMapper).markPayRefundedByTaskId(11L);
	}

	@Test
	@DisplayName("对账扫描：单条失败不影响整批、不抛出")
	void reconcile_singleFailure_doesNotBreakBatch() {
		RunnerTask a = paidTask(12L);
		a.setStatus(RunnerTask.STATUS_CANCELLED);
		RunnerTask b = paidTask(13L);
		b.setStatus(RunnerTask.STATUS_CANCELLED);
		when(taskMapper.selectCancelledPaid(200)).thenReturn(List.of(a, b));
		when(payOrderService.refund(eq("PAY1"), any(), any()))
			.thenReturn(true) // task 12 成功
			.thenThrow(new RuntimeException("gateway down")); // task 13 抛异常

		assertDoesNotThrow(() -> service.reconcileStalePaidRefunds());

		verify(taskMapper).markPayRefundedByTaskId(12L);
	}
}
