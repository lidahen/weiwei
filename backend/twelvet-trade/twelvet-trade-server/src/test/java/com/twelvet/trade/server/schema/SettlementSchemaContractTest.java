package com.twelvet.trade.server.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算 DDL 与代码契约测试
 * <p>
 * 锁定的不变量：代码生成的值必须放得进 DDL 列宽。Mockito 测试看不到真实列宽，这类错配只能在 schema 层钉住。
 * </p>
 */
class SettlementSchemaContractTest {

	private static String schema() throws IOException {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve(Path.of("sql", "settlement_schema.sql"));
			if (Files.exists(candidate)) {
				return Files.readString(candidate);
			}
			dir = dir.getParent();
		}
		throw new AssertionError("从 " + Path.of("").toAbsolutePath() + " 向上找不到 backend/sql/settlement_schema.sql");
	}

	@Test
	@DisplayName("seller_account 必须有解冻审计列（原因/操作人/时间）")
	void sellerAccountHasUnfreezeAuditColumns() throws IOException {
		String ddl = schema();
		assertTrue(ddl.contains("`unfreeze_reason`"), "缺少 unfreeze_reason 列");
		assertTrue(ddl.contains("`unfreeze_by`"), "缺少 unfreeze_by 列");
		assertTrue(ddl.contains("`unfreeze_time`"), "缺少 unfreeze_time 列");
	}

	@Test
	@DisplayName("withdraw_no 列宽必须容纳 W+32位UUID（33 字符）")
	void withdrawNoColumnFitsGeneratedValue() throws IOException {
		Matcher matcher = Pattern.compile("`withdraw_no`\\s+varchar\\((\\d+)\\)").matcher(schema());
		assertTrue(matcher.find(), "DDL 中缺少 withdraw_no 列定义");
		int length = Integer.parseInt(matcher.group(1));
		assertTrue(length >= 33, "withdraw_no 列宽 " + length + " 小于 W+uuid 的 33 字符，插入会报 1406");
	}

}