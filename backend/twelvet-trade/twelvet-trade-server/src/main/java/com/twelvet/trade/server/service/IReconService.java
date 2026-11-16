package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.SettlementReconDiff;

import java.time.LocalDate;

/**
 * 结算对账服务
 *
 * @author twelvet
 */
public interface IReconService {

	/**
	 * 对账并写差异、冻结该业务日有未闭环差异的卖家
	 * @param bizDate 业务日
	 * @return 新增差异数（uk_diff 命中不计）
	 */
	int reconcile(LocalDate bizDate);

	/**
	 * 运营差异分页
	 * @param bizDate 业务日 yyyy-MM-dd，null 不过滤
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 分页结果
	 */
	TableDataInfo<SettlementReconDiff> adminPage(String bizDate, String status, Long tenantId);

	/**
	 * 处理差异（仅 open 可处理；不自动解冻账户）
	 * @param diffId 差异ID
	 * @param note 处理说明
	 * @param operatorId 处理人
	 * @param tenantId 租户ID
	 */
	void resolve(Long diffId, String note, Long operatorId, Long tenantId);

}