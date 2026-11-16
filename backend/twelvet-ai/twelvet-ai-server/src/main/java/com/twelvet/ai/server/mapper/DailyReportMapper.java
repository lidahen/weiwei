package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.DailyReport;
import com.twelvet.system.api.domain.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 社区日报 Mapper（twelve_daily_report + 跨模块只读聚合 SQL）
 * <p>
 * 设计：docs/AI社区日报设计.md；聚合查询只读 product / feed / runner / trade / tenant 表， 不写任何跨模块表；所有 SQL
 * 显式携带 tenantId 过滤（与 runner 模块同惯例）。
 */
@Mapper
public interface DailyReportMapper {

	/**
	 * 查询启用租户（sys_tenant.status='0'），供 generateAll 逐租户生成
	 */
	List<SysTenant> selectEnabledTenants();

	/**
	 * 聚合日报指标（单行多子查询，口径见 docs/AI社区日报设计.md §2）
	 * @param tenantId 租户ID
	 * @param tenantCode 租户编码（sys_tenant_renew_order 按 tenant_code 归属）
	 * @param startTime 统计起始（含）
	 * @param endTime 统计结束（不含）
	 * @return {newProducts, doneOrders, doneAmount, newFeeds, runnerDoneToday,
	 * runnerDoneTotal, runnerNewReviews, runnerAvgRating, renewCount}
	 */
	Map<String, Object> selectAggStats(@Param("tenantId") Long tenantId, @Param("tenantCode") String tenantCode,
			@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

	/**
	 * 浏览 Top3 商品（标题 + 浏览次数）
	 */
	List<Map<String, Object>> selectTopProducts(@Param("tenantId") Long tenantId,
			@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
			@Param("limit") int limit);

	/**
	 * 点赞 Top2 帖子（摘要 + 点赞数）
	 */
	List<Map<String, Object>> selectHotFeeds(@Param("tenantId") Long tenantId,
			@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
			@Param("limit") int limit);

	/**
	 * 推送范围：本租户全部在职用户ID（sys_user.status=0）
	 */
	List<Long> selectPushUserIds(@Param("tenantId") Long tenantId);

	/**
	 * 推送发送者：本租户第一个在职用户（多为管理员；IM sendMessage 需要真实发送者）
	 */
	Long selectSenderUserId(@Param("tenantId") Long tenantId);

	/**
	 * 按租户分页查询日报（report_date 倒序，配合 PageHelper 分页）
	 */
	List<DailyReport> selectPage(@Param("tenantId") Long tenantId);

	/**
	 * 按主键 + 租户查询详情（租户校验防越权）
	 */
	DailyReport selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

	/**
	 * 按租户 + 日期查询（幂等检查：存在则走 UPDATE 覆盖）
	 */
	DailyReport selectByTenantAndDate(@Param("tenantId") Long tenantId, @Param("reportDate") LocalDate reportDate);

	/**
	 * 插入日报
	 */
	int insert(DailyReport report);

	/**
	 * 按租户 + 日期覆盖更新（重复生成场景）
	 */
	int updateByTenantAndDate(DailyReport report);

}
