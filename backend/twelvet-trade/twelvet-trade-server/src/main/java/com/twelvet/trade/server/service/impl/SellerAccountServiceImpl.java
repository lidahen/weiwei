package com.twelvet.trade.server.service.impl;

import com.github.pagehelper.PageInfo;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.utils.DesensitizationUtil;
import com.twelvet.trade.server.crypto.SensitiveDataCipher;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 卖家账户与实名审核实现
 * <p>
 * 证件号/银行卡号只以密文落库（{@link SensitiveDataCipher}），任何对外返回都是脱敏副本；日志不打印明文。
 * </p>
 *
 * @author twelvet
 */
@Service
public class SellerAccountServiceImpl implements ISellerAccountService {

	private static final Logger log = LoggerFactory.getLogger(SellerAccountServiceImpl.class);

	private static final Set<String> REVIEW_RESULTS = Set.of("verified", "rejected");

	private static final Pattern ID_CARD_PATTERN = Pattern.compile("^(\\d{15}|\\d{17}[\\dXx])$");

	private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{12,19}$");

	private final SellerAccountMapper mapper;

	private final SensitiveDataCipher cipher;

	private final SellerBalanceFlowMapper flowMapper;

	private final SettlementReconDiffMapper diffMapper;

	public SellerAccountServiceImpl(SellerAccountMapper mapper, SensitiveDataCipher cipher,
			SellerBalanceFlowMapper flowMapper, SettlementReconDiffMapper diffMapper) {
		this.mapper = mapper;
		this.cipher = cipher;
		this.flowMapper = flowMapper;
		this.diffMapper = diffMapper;
	}

	@Override
	public SellerAccount getOrCreate(Long sellerId, Long tenantId) {
		SellerAccount account = mapper.selectBySeller(sellerId, tenantId);
		if (account != null) {
			return account;
		}
		SellerAccount created = new SellerAccount();
		created.setSellerId(sellerId);
		created.setTenantId(tenantId);
		created.setKycStatus("unverified");
		created.setBalance(new BigDecimal("0.00"));
		created.setFrozen(0);
		created.setVersion(0);
		try {
			mapper.insert(created);
			return created;
		}
		catch (DataIntegrityViolationException e) {
			// 并发首单撞 uk_seller_tenant：另一个线程已创建，重查返回
			SellerAccount existing = mapper.selectBySeller(sellerId, tenantId);
			if (existing == null) {
				throw e;
			}
			return existing;
		}
	}

	@Override
	public void submitKyc(Long sellerId, Long tenantId, String accountName, String idCardNo, String bankCardNo,
			String bankName) {
		validateKyc(accountName, idCardNo, bankCardNo);
		SellerAccount account = getOrCreate(sellerId, tenantId);
		String status = account.getKycStatus();
		if (!"unverified".equals(status) && !"rejected".equals(status)) {
			throw new TWTException("实名审核中或已通过，不能重复提交");
		}
		String trimmedName = accountName.trim();
		String trimmedIdCard = idCardNo.trim();
		String trimmedBankCard = bankCardNo.trim();
		int rows = mapper.submitKyc(account.getAccountId(), trimmedName, cipher.encrypt(trimmedIdCard),
				cipher.encrypt(trimmedBankCard), bankName == null ? null : bankName.trim(), tenantId);
		if (rows == 0) {
			throw new TWTException("实名状态已变更，请刷新后重试");
		}
		log.info("[结算] 卖家提交实名 sellerId={} accountId={}", sellerId, account.getAccountId());
	}

	@Override
	public TableDataInfo<SellerAccount> adminKycPage(String kycStatus, Long tenantId) {
		PageUtils.startPage();
		List<SellerAccount> rows = mapper.selectKycPage(kycStatus, tenantId);
		long total = new PageInfo<>(rows).getTotal();
		List<SellerAccount> masked = rows.stream().map(this::masked).toList();
		return TableDataInfo.page(masked, total);
	}

	@Override
	public void reviewKyc(Long accountId, String result, String note, Long operatorId, Long tenantId) {
		if (!REVIEW_RESULTS.contains(result)) {
			throw new TWTException("审核结果只能是 verified 或 rejected");
		}
		int rows = mapper.reviewKyc(accountId, result, tenantId);
		if (rows == 0) {
			throw new TWTException("实名状态已变更，请刷新后重试");
		}
		log.info("[结算] 实名审核 accountId={} result={} operatorId={}", accountId, result, operatorId);
	}

	@Override
	public Map<String, Object> myAccount(Long sellerId, Long tenantId) {
		SellerAccount account = getOrCreate(sellerId, tenantId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("accountId", account.getAccountId());
		result.put("sellerId", account.getSellerId());
		result.put("accountName", DesensitizationUtil.chineseName(account.getAccountName()));
		result.put("idCardNo", maskTail(account.getIdCardNo()));
		result.put("bankCardNo", maskTail(account.getBankCardNo()));
		result.put("bankName", account.getBankName());
		result.put("kycStatus", account.getKycStatus());
		result.put("balance", account.getBalance());
		result.put("frozen", account.getFrozen());
		return result;
	}

	@Override
	public boolean unfreezeBySeller(Long sellerId, Long tenantId, Long operatorId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new TWTException("请填写解冻原因");
		}
		SellerAccount account = mapper.selectBySeller(sellerId, tenantId);
		if (account == null) {
			return false;
		}
		if (diffMapper.countOpenBySeller(sellerId, tenantId) > 0) {
			throw new TWTException("存在未闭环对账差异，不能解冻");
		}
		int rows = mapper.unfreeze(account.getAccountId(), tenantId, operatorId, reason.trim());
		log.info("[结算] 解冻卖家 sellerId={} accountId={} operatorId={} reason={} rows={}", sellerId, account.getAccountId(),
				operatorId, reason, rows);
		return rows > 0;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean applyBalance(SellerBalanceFlow flow) {
		SellerAccount account = mapper.selectBySeller(flow.getSellerId(), flow.getTenantId());
		if (account == null) {
			// 账户缺失属数据异常：抛出由调用方事务整体回滚
			throw new TWTException("卖家账户不存在");
		}
		flow.setAccountId(account.getAccountId());
		// balance_after 必须在 INSERT 前算好：列 NOT NULL，先插 NULL 会被 INSERT IGNORE 静默写成 0.00
		BigDecimal newBalance = account.getBalance().add(flow.getAmount());
		flow.setBalanceAfter(newBalance);
		if (flowMapper.insertIgnore(flow) == 0) {
			// biz_key 已消费：幂等命中，不重复入账
			return false;
		}
		if (mapper.addBalance(account.getAccountId(), flow.getAmount(), account.getVersion()) == 0) {
			// 乐观锁冲突：回滚流水，下一批次重来（不重试）
			throw new TWTException("余额更新冲突");
		}
		return true;
	}

	private static void validateKyc(String accountName, String idCardNo, String bankCardNo) {
		if (accountName == null || accountName.isBlank()) {
			throw new TWTException("请填写实名姓名");
		}
		if (idCardNo == null || !ID_CARD_PATTERN.matcher(idCardNo.trim()).matches()) {
			throw new TWTException("证件号格式不正确（15 位或 18 位）");
		}
		if (bankCardNo == null || !BANK_CARD_PATTERN.matcher(bankCardNo.trim()).matches()) {
			throw new TWTException("银行卡号格式不正确（12–19 位数字）");
		}
	}

	private SellerAccount masked(SellerAccount source) {
		SellerAccount copy = new SellerAccount();
		copy.setAccountId(source.getAccountId());
		copy.setSellerId(source.getSellerId());
		copy.setTenantId(source.getTenantId());
		copy.setAccountName(DesensitizationUtil.chineseName(source.getAccountName()));
		copy.setIdCardNo(maskTail(source.getIdCardNo()));
		copy.setBankCardNo(maskTail(source.getBankCardNo()));
		copy.setBankName(source.getBankName());
		copy.setKycStatus(source.getKycStatus());
		copy.setBalance(source.getBalance());
		copy.setFrozen(source.getFrozen());
		copy.setVersion(source.getVersion());
		copy.setCreateTime(source.getCreateTime());
		copy.setUpdateTime(source.getUpdateTime());
		return copy;
	}

	private String maskTail(String encrypted) {
		if (encrypted == null || encrypted.isEmpty()) {
			return null;
		}
		String plain;
		try {
			plain = cipher.decrypt(encrypted);
		}
		catch (RuntimeException e) {
			return "****";
		}
		if (plain.length() <= 4) {
			return "****";
		}
		return "*".repeat(plain.length() - 4) + plain.substring(plain.length() - 4);
	}

}