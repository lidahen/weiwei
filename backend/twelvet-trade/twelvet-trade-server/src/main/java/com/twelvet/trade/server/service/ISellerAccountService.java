package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;

import java.util.Map;

/**
 * 卖家账户与实名审核服务
 *
 * @author twelvet
 */
public interface ISellerAccountService {

	/**
	 * 获取卖家账户，不存在则创建未实名账户
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 账户
	 */
	SellerAccount getOrCreate(Long sellerId, Long tenantId);

	/**
	 * 提交实名资料（加密落库，状态置 pending）
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @param accountName 实名姓名
	 * @param idCardNo 证件号明文
	 * @param bankCardNo 银行卡号明文
	 * @param bankName 开户行
	 */
	void submitKyc(Long sellerId, Long tenantId, String accountName, String idCardNo, String bankCardNo,
			String bankName);

	/**
	 * 运营实名审核分页（返回脱敏副本）
	 * @param kycStatus 实名状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 分页结果
	 */
	TableDataInfo<SellerAccount> adminKycPage(String kycStatus, Long tenantId);

	/**
	 * 实名审核
	 * @param accountId 账户ID
	 * @param result verified/rejected
	 * @param note 备注
	 * @param operatorId 审核人
	 * @param tenantId 租户ID
	 */
	void reviewKyc(Long accountId, String result, String note, Long operatorId, Long tenantId);

	/**
	 * 卖家本人账户信息（脱敏）
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 脱敏账户信息
	 */
	Map<String, Object> myAccount(Long sellerId, Long tenantId);

	/**
	 * 按卖家解冻账户：存在未闭环对账差异时拒绝；原因与操作人落库
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @param operatorId 操作人
	 * @param reason 解冻原因
	 * @return true=已解冻；false=账户不存在/未处于冻结态
	 */
	boolean unfreezeBySeller(Long sellerId, Long tenantId, Long operatorId, String reason);

	/**
	 * 余额记账（幂等：biz_key 已消费时返回 false 且不重复入账）
	 * @param flow 流水（sellerId/tenantId/type/amount/bizKey 由调用方填）
	 * @return true=记账成功；false=重复 biz_key 已处理
	 */
	boolean applyBalance(SellerBalanceFlow flow);

}