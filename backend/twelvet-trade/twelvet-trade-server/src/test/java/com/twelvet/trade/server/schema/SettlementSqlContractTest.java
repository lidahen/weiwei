package com.twelvet.trade.server.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算相关 SQL 契约测试
 * <p>
 * 锁定的不变量：对账查询必须按业务日限定并排除 trade_pay.order_id 多态撞号；打款查询必须带出结算渠道； 财务概览不得再引用旧
 * trade_withdraw.user_id。
 * </p>
 */
class SettlementSqlContractTest {

	private static String read(String fileName) throws IOException {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve(Path.of("twelvet-trade", "twelvet-trade-server", "src", "main", "resources",
					"mapper", "trade", fileName));
			if (Files.exists(candidate)) {
				return Files.readString(candidate);
			}
			dir = dir.getParent();
		}
		throw new AssertionError("找不到 mapper: " + fileName);
	}

	private static String block(String xml, String id) {
		Matcher matcher = Pattern
			.compile("<(?:select|update|insert) id=\"" + id + "\"[^>]*>(.*?)</(?:select|update|insert)>",
					Pattern.DOTALL)
			.matcher(xml);
		assertTrue(matcher.find(), "找不到 SQL 块: " + id);
		return matcher.group(1);
	}

	@Test
	@DisplayName("打款候选必须带出 settlement.channel，markSettled 不得写 NULL 渠道")
	void pendingPayoutSelectsChannel() throws IOException {
		assertTrue(block(read("SettlementOrderMapper.xml"), "selectPendingPayout").contains("s.channel"),
				"selectPendingPayout 未选出 s.channel，markSettled 会把渠道写成 NULL");
	}

	@Test
	@DisplayName("对账查询按 bizDate 限定，避免历史差异每天重复入队")
	void reconQueriesUseBizDate() throws IOException {
		String xml = read("SettlementOrderMapper.xml");
		assertTrue(block(xml, "selectAmountMismatch").contains("#{bizDate}"), "金额不一致查询未使用 bizDate");
		assertTrue(block(xml, "selectRefundStatusMismatch").contains("#{bizDate}"), "退款状态不一致查询未使用 bizDate");
		assertTrue(block(xml, "selectDuplicateSettlement").contains("#{bizDate}"), "重复结算查询未使用 bizDate");
	}

	@Test
	@DisplayName("对账关联 trade_pay 必须用 buyer + tenant 消除 order_id 多态撞号")
	void reconPayJoinDisambiguates() throws IOException {
		String xml = read("SettlementOrderMapper.xml");
		assertTrue(block(xml, "selectMissingSettlement").contains("pay_user_id = o.buyer_id"),
				"缺结算查询未按 pay_user_id 排除跑腿/续费撞号");
		assertTrue(block(xml, "selectAmountMismatch").contains("pay_user_id = s.buyer_id"),
				"金额不一致查询未按 pay_user_id 排除跑腿/续费撞号");
		assertTrue(block(xml, "selectPhantomSettlement").contains("pay_user_id = s.buyer_id"),
				"幽灵结算查询未按 pay_user_id 排除跑腿/续费撞号");
	}

	@Test
	@DisplayName("财务概览 pendingWithdraws 改用新 trade_withdraw.tenant_id，不得再 JOIN sys_user.user_id")
	void financeStatsUsesNewWithdrawSchema() throws IOException {
		String block = block(read("AdminMarketplaceMapper.xml"), "selectFinanceStats");
		assertFalse(block.contains("w.user_id"), "selectFinanceStats 仍在引用已改名旧表的 w.user_id");
		assertTrue(block.contains("w.tenant_id"), "selectFinanceStats 未改用新 trade_withdraw.tenant_id");
	}

}