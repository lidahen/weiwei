package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.trade.server.crypto.SensitiveDataCipher;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.mapper.SellerAccountMapper;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖家账户与实名审核测试
 * <p>
 * 锁定的不变量：证件号/卡号只以密文落库；重复提交/非法审核被拒；对外返回值必须脱敏。
 * </p>
 */
class SellerAccountServiceKycTest {

	private final SellerAccountMapper mapper = mock(SellerAccountMapper.class);

	private final SellerBalanceFlowMapper flowMapper = mock(SellerBalanceFlowMapper.class);

	private final SettlementReconDiffMapper diffMapper = mock(SettlementReconDiffMapper.class);

	private final SensitiveDataCipher cipher = new SensitiveDataCipher(
			Base64.getEncoder().encodeToString(new byte[32]));

	private final SellerAccountServiceImpl service = new SellerAccountServiceImpl(mapper, cipher, flowMapper,
			diffMapper);

	private SellerAccount newAccount(String status) {
		SellerAccount account = new SellerAccount();
		account.setAccountId(11L);
		account.setSellerId(7L);
		account.setTenantId(42L);
		account.setKycStatus(status);
		account.setBalance(new BigDecimal("0.00"));
		account.setFrozen(0);
		account.setVersion(0);
		return account;
	}

	@Test
	@DisplayName("未实名账户提交资料：落库密文且置 pending")
	void submit_unverified_encryptsAndPends() {
		when(mapper.selectBySeller(7L, 42L)).thenReturn(newAccount("unverified"));
		when(mapper.submitKyc(eq(11L), eq("张三"), any(), any(), eq("工商银行"), eq(42L))).thenReturn(1);

		service.submitKyc(7L, 42L, "张三", "110101199001011234", "6222020200112233445", "工商银行");

		ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> bankCaptor = ArgumentCaptor.forClass(String.class);
		verify(mapper).submitKyc(eq(11L), eq("张三"), idCaptor.capture(), bankCaptor.capture(), eq("工商银行"),
				eq(42L));
		assertNotEquals("110101199001011234", idCaptor.getValue());
		assertEquals("110101199001011234", cipher.decrypt(idCaptor.getValue()));
		assertNotEquals("6222020200112233445", bankCaptor.getValue());
		assertEquals("6222020200112233445", cipher.decrypt(bankCaptor.getValue()));
	}

	@Test
	@DisplayName("已通过账户不能重复提交")
	void submit_afterVerified_rejected() {
		when(mapper.selectBySeller(7L, 42L)).thenReturn(newAccount("verified"));

		assertThrows(TWTException.class,
				() -> service.submitKyc(7L, 42L, "张三", "110101199001011234", "6222020200112233445", "工商银行"));
		verify(mapper, never()).submitKyc(anyLong(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("审核通过：状态推进为 verified")
	void review_verified_updatesStatus() {
		when(mapper.reviewKyc(11L, "verified", 42L)).thenReturn(1);

		service.reviewKyc(11L, "verified", "资料清晰", 99L, 42L);

		verify(mapper).reviewKyc(11L, "verified", 42L);
	}

	@Test
	@DisplayName("审核状态已被并发变更：抛异常")
	void review_rowsZero_throws() {
		when(mapper.reviewKyc(11L, "verified", 42L)).thenReturn(0);

		assertThrows(TWTException.class, () -> service.reviewKyc(11L, "verified", null, 99L, 42L));
	}

	@Test
	@DisplayName("审核结果非法值被拒绝")
	void review_invalidResult_rejected() {
		assertThrows(TWTException.class, () -> service.reviewKyc(11L, "ok", null, 99L, 42L));
		verify(mapper, never()).reviewKyc(anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("账户查询返回脱敏值，不回传明文/密文")
	void myAccount_masksSensitiveFields() {
		SellerAccount account = newAccount("verified");
		account.setAccountName("李四");
		account.setIdCardNo(cipher.encrypt("110101199001011234"));
		account.setBankCardNo(cipher.encrypt("6222020200112233445"));
		when(mapper.selectBySeller(7L, 42L)).thenReturn(account);

		Map<String, Object> result = service.myAccount(7L, 42L);

		// DesensitizationUtil.chineseName 的实际输出是「首字 + 1 个星号/剩余字」，Javadoc 的「李**」与实现不符
		assertTrue(((String) result.get("accountName")).matches("李\\*+"));
		assertTrue(((String) result.get("idCardNo")).matches("\\*+1234"));
		assertTrue(((String) result.get("bankCardNo")).matches("\\*+3445"));
		assertEquals(new BigDecimal("0.00"), result.get("balance"));
	}

	@Test
	@DisplayName("实名必填项缺失或格式非法：拒绝且不落库（姓名缺失不得抛 NPE 500）")
	void submit_blankOrInvalid_rejected() {
		assertThrows(TWTException.class,
				() -> service.submitKyc(7L, 42L, null, "110101199001011234", "6222020200112233445", "工商银行"));
		assertThrows(TWTException.class,
				() -> service.submitKyc(7L, 42L, "  ", "110101199001011234", "6222020200112233445", "工商银行"));
		assertThrows(TWTException.class, () -> service.submitKyc(7L, 42L, "张三", "", "6222020200112233445", "工商银行"));
		assertThrows(TWTException.class, () -> service.submitKyc(7L, 42L, "张三", "123", "6222020200112233445", "工商银行"));
		assertThrows(TWTException.class, () -> service.submitKyc(7L, 42L, "张三", "110101199001011234", "abc", "工商银行"));

		verify(mapper, never()).submitKyc(anyLong(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("解冻：原因必填")
	void unfreeze_blankReason_rejected() {
		assertThrows(TWTException.class, () -> service.unfreezeBySeller(7L, 42L, 99L, " "));
		verify(mapper, never()).unfreeze(anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("解冻：仍有未闭环差异时拒绝（防止绕过冻结）")
	void unfreeze_openDiffs_rejected() {
		when(mapper.selectBySeller(7L, 42L)).thenReturn(newAccount("verified"));
		when(diffMapper.countOpenBySeller(7L, 42L)).thenReturn(2);

		assertThrows(TWTException.class, () -> service.unfreezeBySeller(7L, 42L, 99L, "差异已处理"));
		verify(mapper, never()).unfreeze(anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("解冻：无未闭环差异时落库原因与操作人")
	void unfreeze_successPersistsReason() {
		when(mapper.selectBySeller(7L, 42L)).thenReturn(newAccount("verified"));
		when(diffMapper.countOpenBySeller(7L, 42L)).thenReturn(0);
		when(mapper.unfreeze(11L, 42L, 99L, "差异已处理")).thenReturn(1);

		assertTrue(service.unfreezeBySeller(7L, 42L, 99L, "差异已处理"));

		verify(mapper).unfreeze(11L, 42L, 99L, "差异已处理");
	}

	@Test
	@DisplayName("账户不存在时创建未实名账户")
	void getOrCreate_createsWhenMissing() {
		when(mapper.selectBySeller(7L, 42L)).thenReturn(null,
				accountWithId());
		when(mapper.insert(any(SellerAccount.class))).thenAnswer(invocation -> {
			SellerAccount inserted = invocation.getArgument(0);
			inserted.setAccountId(11L);
			return 1;
		});

		SellerAccount created = service.getOrCreate(7L, 42L);

		assertNotNull(created);
		assertEquals(11L, created.getAccountId());
		ArgumentCaptor<SellerAccount> captor = ArgumentCaptor.forClass(SellerAccount.class);
		verify(mapper).insert(captor.capture());
		assertEquals("unverified", captor.getValue().getKycStatus());
		assertEquals(new BigDecimal("0.00"), captor.getValue().getBalance());
	}

	private SellerAccount accountWithId() {
		return newAccount("unverified");
	}

}