package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.SellerBalanceFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 卖家余额账本 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface SellerBalanceFlowMapper {

	/**
	 * 追加流水（uk_biz_key 命中时返回 0，不重复记账）
	 * @param flow 流水
	 * @return 影响行数
	 */
	int insertIgnore(SellerBalanceFlow flow);

	/**
	 * 账户流水金额合计（对账用）
	 * @param accountId 账户ID
	 * @return 合计，无流水为 0
	 */
	BigDecimal sumAmount(@Param("accountId") Long accountId);

	/**
	 * 账户最后一条流水（对账用）
	 * @param accountId 账户ID
	 * @return 最后一条流水，无流水为 null
	 */
	SellerBalanceFlow selectLast(@Param("accountId") Long accountId);

	/**
	 * 对账：账本金额合计与账户余额不一致的账户
	 * @return 差异行（accountId/sellerId/tenantId/balance/flowSum）
	 */
	List<Map<String, Object>> selectAccountsWithLedgerDrift();

	/**
	 * 对账：最后一条流水的 balance_after 与账户余额不一致的账户（与金额合计独立检查）
	 * @return 差异行（accountId/sellerId/tenantId/balance/lastBalance）
	 */
	List<Map<String, Object>> selectAccountsWithLastBalanceDrift();

}