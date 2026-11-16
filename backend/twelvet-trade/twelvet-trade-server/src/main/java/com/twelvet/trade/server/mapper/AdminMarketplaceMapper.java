package com.twelvet.trade.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 运营管理Mapper
 * <p>
 * 所有方法均接受 tenantId：非 null 时按租户过滤，null 时不过滤（平台超管／无线程上下文场景）。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface AdminMarketplaceMapper {

	/**
	 * 仪表盘统计(含类目分布)
	 * @param tenantId 租户ID，可为 null
	 * @return 统计结果
	 */
	Map<String, Object> selectDashboardStats(@Param("tenantId") Long tenantId);

	/**
	 * 查询类目分布
	 * @param tenantId 租户ID，可为 null
	 * @return 类目分布列表
	 */
	List<Map<String, Object>> selectCategoryDistribution(@Param("tenantId") Long tenantId);

	/**
	 * 自营运营核心指标（发布量/成交量/GMV/履约/复购/纠纷，窗口内原始计数）
	 * @param tenantId 租户ID，可为 null
	 * @param days 统计窗口天数
	 * @return 指标结果
	 */
	Map<String, Object> selectOperationMetrics(@Param("tenantId") Long tenantId, @Param("days") int days);

	/**
	 * 按日订单/成交趋势
	 * @param tenantId 租户ID，可为 null
	 * @param days 统计窗口天数
	 * @return 每日订单数与 GMV
	 */
	List<Map<String, Object>> selectDailyOrderTrend(@Param("tenantId") Long tenantId, @Param("days") int days);

	/**
	 * 按日发布量趋势
	 * @param tenantId 租户ID，可为 null
	 * @param days 统计窗口天数
	 * @return 每日发布数
	 */
	List<Map<String, Object>> selectDailyPublishTrend(@Param("tenantId") Long tenantId, @Param("days") int days);

	/**
	 * 记录商品审核通过日志
	 * @param productId 商品ID
	 * @param auditorId 审核员ID
	 * @return 结果
	 */
	int insertAuditPassLog(@Param("productId") Long productId, @Param("auditorId") Long auditorId);

	/**
	 * 记录商品审核驳回日志
	 * @param productId 商品ID
	 * @param auditorId 审核员ID
	 * @param reason 原因
	 * @return 结果
	 */
	int insertAuditRejectLog(@Param("productId") Long productId, @Param("auditorId") Long auditorId,
			@Param("reason") String reason);

	/**
	 * 商品审核列表
	 * @param status 可选过滤
	 * @param aiRisk AI 初审筛选：flagged=仅看 AI 标疑(medium/high)，auto=仅看 AI 已自动处理
	 * @param tenantId 租户ID，可为 null
	 * @return 商品列表
	 */
	List<Map<String, Object>> selectProductAuditList(@Param("status") String status, @Param("aiRisk") String aiRisk,
			@Param("tenantId") Long tenantId);

	/**
	 * 审核通过
	 * @param productId 商品ID
	 * @param tenantId 租户ID，可为 null
	 * @return 结果
	 */
	int approveProduct(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

	/**
	 * 审核驳回
	 * @param productId 商品ID
	 * @param reason 驳回原因
	 * @param tenantId 租户ID，可为 null
	 * @return 结果
	 */
	int rejectProduct(@Param("productId") Long productId, @Param("reason") String reason,
			@Param("tenantId") Long tenantId);

	/**
	 * 用户列表
	 * @param keyword 搜索关键词
	 * @param tenantId 租户ID，可为 null
	 * @return 用户列表
	 */
	List<Map<String, Object>> selectUserList(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

	/**
	 * 封禁/解封用户
	 * @param userId 用户ID
	 * @param status 用户状态
	 * @param tenantId 租户ID，可为 null
	 * @return 结果
	 */
	int updateUserStatus(@Param("userId") Long userId, @Param("status") Integer status,
			@Param("tenantId") Long tenantId);

	/**
	 * 财务统计
	 * @param tenantId 租户ID，可为 null
	 * @return 财务统计结果
	 */
	Map<String, Object> selectFinanceStats(@Param("tenantId") Long tenantId);

	/**
	 * 最近支付流水
	 * @param limit 限制条数
	 * @param tenantId 租户ID，可为 null
	 * @return 流水列表
	 */
	List<Map<String, Object>> selectRecentPayFlows(@Param("limit") int limit, @Param("tenantId") Long tenantId);

}
