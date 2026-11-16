package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeWithdraw;

import java.math.BigDecimal;

/**
 * 卖家提现服务（申请即扣余额，人工审核 + 线下打款凭证）
 *
 * @author twelvet
 */
public interface IWithdrawService {

	/**
	 * 申请提现
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @param amount 提现金额
	 * @return 扣减后余额
	 */
	BigDecimal apply(Long sellerId, Long tenantId, BigDecimal amount);

	/**
	 * 卖家提现记录
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 分页结果
	 */
	TableDataInfo<TradeWithdraw> myPage(Long sellerId, Long tenantId);

	/**
	 * 运营提现分页
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 分页结果
	 */
	TableDataInfo<TradeWithdraw> adminPage(String status, Long tenantId);

	/**
	 * 审核提现
	 * @param withdrawId 提现单ID
	 * @param action approve/reject
	 * @param reason 驳回原因
	 * @param operatorId 审核人
	 * @param tenantId 租户ID
	 */
	void review(Long withdrawId, String action, String reason, Long operatorId, Long tenantId);

	/**
	 * 线下打款完成（回填凭证）
	 * @param withdrawId 提现单ID
	 * @param voucherUrl 凭证地址
	 * @param operatorId 打款人
	 * @param tenantId 租户ID
	 */
	void markPaid(Long withdrawId, String voucherUrl, Long operatorId, Long tenantId);

}