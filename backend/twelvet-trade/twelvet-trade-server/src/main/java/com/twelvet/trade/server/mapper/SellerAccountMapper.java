package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.SellerAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 卖家收款账户 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface SellerAccountMapper {

	/**
	 * 按卖家+租户查账户
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 账户，不存在为 null
	 */
	SellerAccount selectBySeller(@Param("sellerId") Long sellerId, @Param("tenantId") Long tenantId);

	/**
	 * 按ID查账户
	 * @param accountId 账户ID
	 * @return 账户
	 */
	SellerAccount selectById(@Param("accountId") Long accountId);

	/**
	 * 新建账户（balance=0, frozen=0, version=0, unverified）
	 * @param account 账户
	 * @return 影响行数
	 */
	int insert(SellerAccount account);

	/**
	 * 提交实名资料（仅 unverified/rejected 可提交，置 pending）
	 * @param accountId 账户ID
	 * @param accountName 实名姓名
	 * @param idCardNo 证件号密文
	 * @param bankCardNo 银行卡号密文
	 * @param bankName 开户行
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int submitKyc(@Param("accountId") Long accountId, @Param("accountName") String accountName,
			@Param("idCardNo") String idCardNo, @Param("bankCardNo") String bankCardNo,
			@Param("bankName") String bankName, @Param("tenantId") Long tenantId);

	/**
	 * 实名审核（仅 pending 可审核）
	 * @param accountId 账户ID
	 * @param kycStatus verified/rejected
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int reviewKyc(@Param("accountId") Long accountId, @Param("kycStatus") String kycStatus,
			@Param("tenantId") Long tenantId);

	/**
	 * 余额变动（乐观锁，版本必须匹配）
	 * @param accountId 账户ID
	 * @param amount 变动金额（正数入账，负数扣减）
	 * @param version 期望版本
	 * @return 影响行数（0=版本冲突）
	 */
	int addBalance(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount,
			@Param("version") Integer version);

	/**
	 * 设置冻结标记
	 * @param accountId 账户ID
	 * @param frozen 0/1
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int setFrozen(@Param("accountId") Long accountId, @Param("frozen") Integer frozen,
			@Param("tenantId") Long tenantId);

	/**
	 * 人工解冻（仅冻结态可解冻，记录原因/操作人/时间）
	 * @param accountId 账户ID
	 * @param tenantId 租户ID
	 * @param operatorId 操作人
	 * @param reason 解冻原因
	 * @return 影响行数
	 */
	int unfreeze(@Param("accountId") Long accountId, @Param("tenantId") Long tenantId,
			@Param("operatorId") Long operatorId, @Param("reason") String reason);

	/**
	 * 实名审核分页（PageHelper 在 service 层启动）
	 * @param kycStatus 实名状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 账户列表
	 */
	List<SellerAccount> selectKycPage(@Param("kycStatus") String kycStatus, @Param("tenantId") Long tenantId);

}