package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 运营管理Service接口
 *
 * @author twelvet
 */
public interface IAdminMarketplaceService {

	/**
	 * 仪表盘概览
	 * @return 概览数据
	 */
	Map<String, Object> selectDashboardOverview();

	/**
	 * 自营运营核心指标（阶段一验证口径）
	 * <p>
	 * 发布量 / 成交量 / GMV / 履约率 / 复购率 / 纠纷率 / 履约风险，窗口由 days 决定。
	 * </p>
	 * @param days 统计窗口天数，仅接受 7 / 30 / 90，其它值回落为 30
	 * @return 指标与其口径说明
	 */
	Map<String, Object> selectOperationMetrics(int days);

	/**
	 * 商品审核列表(分页)
	 * @param status 可选过滤
	 * @param aiRisk AI 初审筛选：flagged=仅看 AI 标疑，auto=仅看 AI 已自动处理，null=全部
	 * @return 商品分页列表
	 */
	TableDataInfo<Map<String, Object>> selectProductAuditList(String status, String aiRisk);

	/**
	 * 商品审核通过
	 * @param productId 商品ID
	 * @return 结果
	 */
	String approveProduct(Long productId);

	/**
	 * 商品审核驳回
	 * @param productId 商品ID
	 * @param reason 驳回原因
	 * @return 结果
	 */
	String rejectProduct(Long productId, String reason);

	/**
	 * 举报列表(分页) —— 委托 product 的 IReportService，report 表单一读写点
	 * @param status pending/done/all
	 * @return 举报分页列表
	 */
	TableDataInfo<Map<String, Object>> selectReportList(String status);

	/**
	 * 处理举报 —— 委托 product 的 IReportService（处置真实生效 + 站内通知）
	 * @param reportId 举报ID
	 * @param action 处理动作
	 * @param note 处理备注
	 * @param handlerId 处理人ID
	 * @return 结果
	 */
	String handleReport(Long reportId, String action, String note, Long handlerId);

	/**
	 * 用户列表(分页)
	 * @param keyword 搜索关键词
	 * @return 用户分页列表
	 */
	TableDataInfo<Map<String, Object>> selectUserList(String keyword);

	/**
	 * 封禁/解封用户
	 * @param userId 用户ID
	 * @param status 用户状态
	 * @return 结果
	 */
	String updateUserStatus(Long userId, Integer status);

	/**
	 * 财务概览
	 * @return 财务数据
	 */
	Map<String, Object> selectFinanceOverview();

}
