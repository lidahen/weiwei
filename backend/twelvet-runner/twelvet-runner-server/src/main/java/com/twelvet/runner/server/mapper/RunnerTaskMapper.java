package com.twelvet.runner.server.mapper;

import com.twelvet.runner.api.domain.RunnerTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 互助任务 Mapper（twelve_runner_task）
 *
 * @author twelvet
 */
@Mapper
public interface RunnerTaskMapper {

	int insert(RunnerTask task);

	/**
	 * 乐观锁接单（核心并发控制）
	 * <p>
	 * 影响行数=0 表示已被接单 / 任务不存在 / 已过 deadline
	 * </p>
	 */
	int assignAtomically(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId,
			@Param("runnerId") Long runnerId, @Param("now") LocalDateTime now);

	/**
	 * 通用状态机迁移：UPDATE ... WHERE id=? AND status=?
	 * @return 影响行数；0 表示状态不允许
	 */
	int transitionStatus(@Param("taskId") Long taskId, @Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus, @Param("tenantId") Long tenantId);

	/**
	 * 状态机迁移 + 写时间戳（如 start_time, finish_time, confirm_time）
	 */
	int transitionWithTimestamp(@Param("taskId") Long taskId, @Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus, @Param("timeColumn") String timeColumn,
			@Param("tenantId") Long tenantId);

	/**
	 * 取消任务（带原因）
	 */
	int cancelTask(@Param("taskId") Long taskId, @Param("status") String toStatus,
			@Param("cancelReason") String cancelReason, @Param("tenantId") Long tenantId);

	/**
	 * 互助大厅查询
	 * <p>
	 * 通用条件：status='pending' AND deadline &gt; NOW() AND tenant_id=#{tenantId}
	 * 可选筛选：taskType / 价格区间
	 * </p>
	 */
	List<RunnerTask> selectHall(@Param("tenantId") Long tenantId, @Param("taskType") Integer taskType,
			@Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice,
			@Param("limit") Integer limit);

	/**
	 * 我发布的任务
	 */
	List<RunnerTask> selectMyPublished(@Param("publisherId") Long publisherId, @Param("tenantId") Long tenantId,
			@Param("status") String status, @Param("limit") Integer limit);

	/**
	 * 我接的任务
	 */
	List<RunnerTask> selectMyTaken(@Param("runnerId") Long runnerId, @Param("tenantId") Long tenantId,
			@Param("status") String status, @Param("limit") Integer limit);

	RunnerTask selectById(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

	/**
	 * 平台管理：分页查询（status 精确匹配 + keyword 模糊匹配 title/task_no/description）
	 * @param tenantId 租户过滤（null 时不限定租户，与现有 mapper 租户处理方式一致）
	 * @param offset 分页偏移量（(pageNum-1)*pageSize）
	 * @param size 每页条数
	 */
	List<RunnerTask> selectAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

	/**
	 * 平台管理：分页总数（条件与 selectAdminPage 一致）
	 */
	long countAdminPage(@Param("tenantId") Long tenantId, @Param("status") String status,
			@Param("keyword") String keyword);

	/**
	 * 平台看板统计
	 */
	Map<String, Object> selectStatistics(@Param("tenantId") Long tenantId);

	/**
	 * 扫超时未接单任务（用于定时任务）
	 */
	List<RunnerTask> selectExpiredPending(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

	/**
	 * 退款对账扫描：已取消但仍 paid 的滞留任务（退款失败兜底重试）
	 */
	List<RunnerTask> selectCancelledPaid(@Param("limit") Integer limit);

	/**
	 * 扫超时未确认任务（pending_confirm 超 3 天 → 自动完成）
	 */
	List<RunnerTask> selectExpiredPendingConfirm(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

	/**
	 * 完成任务时更新 pay_order_id 关联（可选）
	 */
	int updatePayOrderId(@Param("taskId") Long taskId, @Param("payOrderId") Long payOrderId);

	/**
	 * 更新完成凭证图
	 */
	int updateByIdForFinishImages(@Param("taskId") Long taskId, @Param("finishImages") String finishImages);

	/**
	 * 绑定支付单（publish 后由 controller 写入 pay_order_id + pay_out_trade_no）
	 */
	int bindPayInfo(@Param("taskId") Long taskId, @Param("payOrderId") Long payOrderId,
			@Param("payOutTradeNo") String payOutTradeNo);

	/**
	 * 按 outTradeNo 更新支付状态（事件监听器用）
	 */
	int markPayStatusByOutTradeNo(@Param("payOutTradeNo") String payOutTradeNo, @Param("payStatus") String payStatus);

	/**
	 * 按 outTradeNo 反查 taskId（事件监听器用）
	 */
	Long selectTaskIdByOutTradeNo(@Param("payOutTradeNo") String payOutTradeNo);

	/**
	 * 按 taskId 标记已付（仅 unpaid → paid；已取消任务被守卫拦截，防迟到支付复活）
	 */
	int markPayStatusByTaskId(@Param("taskId") Long taskId, @Param("payStatus") String payStatus);

	/**
	 * 按 taskId 标记已退款（仅 paid → refunded；不限任务状态——已取消任务的退款标记必须成功）
	 */
	int markPayRefundedByTaskId(@Param("taskId") Long taskId);

}
