package com.twelvet.trade.server.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖家收款账户 seller_account
 * <p>
 * 不继承 BaseEntity：本表没有 create_by / update_by / remark 审计列，继承会让 TenantFieldInterceptor
 * 尝试写入不存在的列。余额是账本结果，不是资金池。
 * </p>
 *
 * @author twelvet
 */
@Schema(description = "卖家收款账户")
public class SellerAccount implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "账户ID")
	private Long accountId;

	@Schema(description = "卖家用户ID")
	private Long sellerId;

	@Schema(description = "租户ID")
	private Long tenantId;

	@Schema(description = "实名姓名")
	private String accountName;

	/** 证件号密文（AES/GCM），禁止明文落库 */
	@Schema(description = "证件号密文")
	private String idCardNo;

	/** 银行卡号密文（AES/GCM），禁止明文落库 */
	@Schema(description = "银行卡号密文")
	private String bankCardNo;

	@Schema(description = "开户行")
	private String bankName;

	@Schema(description = "实名状态 unverified/pending/verified/rejected")
	private String kycStatus;

	@Schema(description = "可用余额")
	private BigDecimal balance;

	@Schema(description = "1=冻结（负余额/对账差异）")
	private Integer frozen;

	@Schema(description = "乐观锁版本")
	private Integer version;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public void setTenantId(Long tenantId) {
		this.tenantId = tenantId;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getIdCardNo() {
		return idCardNo;
	}

	public void setIdCardNo(String idCardNo) {
		this.idCardNo = idCardNo;
	}

	public String getBankCardNo() {
		return bankCardNo;
	}

	public void setBankCardNo(String bankCardNo) {
		this.bankCardNo = bankCardNo;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getKycStatus() {
		return kycStatus;
	}

	public void setKycStatus(String kycStatus) {
		this.kycStatus = kycStatus;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public Integer getFrozen() {
		return frozen;
	}

	public void setFrozen(Integer frozen) {
		this.frozen = frozen;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

}