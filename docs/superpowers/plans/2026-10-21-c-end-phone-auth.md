# C 端手机号自助注册与登录 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 C 端用户用「手机号 + 验证码」在登录时自动建号并登录，短信先走 mock（固定 `123456`）。

**Architecture:** 在 `twelvet-system-server` 新增 `SmsCodeService`（Redis 存验证码 + 频控）与 `SmsSender` 接口（mock 实现），在 `LoginServiceImpl` 增加 `smsLogin`（查手机号→无则建号→签发 token），`LoginController` 暴露两个 `@SaIgnore` 端点。前端登录页手机号 Tab 改接真接口。全站登录门禁不变。

**Tech Stack:** Java 17 / Spring Boot 3.5.4 / MyBatis XML / Sa-Token / Redisson / MySQL 8.0 函数索引 / Umi Max 4 + React 18

**Spec:** `docs/superpowers/specs/2026-10-20-c-end-phone-auth-design.md`

## Global Constraints

- 验证码有效期 **300 秒**；同手机号发送间隔 **60 秒**；同手机号每日上限 **10 条**；验证码错误上限 **5 次**；校验成功即删除
- mock 验证码固定为 **`123456`**
- 新用户 `user_type='02'`、`role_id=2`、`status=0`、`del_flag='0'`、`username='u'+手机号+'_'+6位随机十六进制`、`nick_name='邻里用户'+手机号后4位`、密码为随机 UUID 的 BCrypt 哈希
- 手机号唯一性：**租户内唯一**，通过 MySQL 8.0 函数唯一索引实现（表达式含 `del_flag`），**禁止新增列**（`backend/data/twelvet.sql:1023,1027` 是无列名 INSERT）
- 手机号正则：`^1[3-9]\d{9}$`
- 业务异常一律用 `TWTException`（全局处理器映射为 **HTTP 400**，见 `TWTExceptionHandler:73-76`）
- 代码格式：Spring Java Format（`mvn spring-javaformat:validate` 必须通过）
- **不修改** `ui/src/app.tsx` 与 `ui/config/routes.ts`（全站登录门禁维持现状）

## Review Focus

以下五类输入/失败模式，spec 隐含要求但它们最容易在生产中咬人；每条都在对应任务的测试中钉住：

1. **并发首次登录同一手机号** — 两个请求同时判定「用户不存在」，都去建号。唯一索引兜底，但必须捕获 `DuplicateKeyException` 并降级为重新查询登录，否则用户看到 500。
2. **空手机号的既有账号**（`ops_demo`、`cs_demo` 等 `phonenumber=''`）— 唯一索引必须把它们视为 `NULL`，否则两个空手机号账号会互撞导致迁移失败。
3. **验证码暴力尝试** — 不限制错误次数则 6 位码可在 300 秒内被穷举。必须 5 次作废。
4. **验证码重放** — 同一验证码被使用两次。必须校验成功即删除。
5. **后台把用户手机号改成已存在的值** — 加索引后从「静默允许」变成抛 `DuplicateKeyException`（500）。必须在 `updateUser` 前置 `checkPhoneUnique` 并抛 `TWTException`。

---

### Task 1: 手机号唯一索引 SQL

**Files:**
- Create: `backend/data/sms-auth-init.sql`

**Interfaces:**
- Consumes: 无
- Produces: 数据库约束 `uk_sys_user_tenant_phone`，后续任务依赖它触发 `DuplicateKeyException`

- [ ] **Step 1: 编写 SQL 脚本**

参考 `backend/data/trade-timeout-index-init.sql` 的注释风格（背景/内容/幂等/回滚四段）。文件内容：

```sql
-- ============================================
-- C 端手机号登录：租户内手机号唯一索引（2026-10-21）
-- 文档：docs/superpowers/specs/2026-10-20-c-end-phone-auth-design.md
-- 背景：手机号是 C 端唯一身份锚点，登录/自动建号依赖「租户内手机号唯一」。
--   原 sys_user 只有 uk_sys_user_tenant_username 与 idx_sys_user_global，手机号无唯一约束。
-- 方案：MySQL 8.0 函数唯一索引（**不新增列**）。
--   ⚠️ 不能用生成列：backend/data/twelvet.sql:1023,1027 是无列名 INSERT，加列会报列数不匹配。
--   ⚠️ 不能直接对 phonenumber 建唯一索引：运营/客服账号手机号为空串，空串互撞。
--   ⚠️ 不能全局唯一：同一自然人可跨 N 个租户各有一行（global_user_id），见 alice(13800001101) 租户 1 与 2 两行。
--   ⚠️ 表达式必须含 del_flag：软删（del_flag='2'）行不能永久占用手机号，否则用户被无声锁死。
--   ⚠️ 必须用正向匹配 del_flag <=> '0'（NULL 安全等号），不能写排除式 del_flag <> '0'：
--      del_flag 可为 NULL，排除式下 NULL 行会算成 NULL → IF 当 false → 该行仍占用手机号，
--      而应用查询 del_flag='0' 看不见它，口径冲突复现死锁。
--   ⚠️ 口径必须与 SysUserMapper.xml 的 selectUserByPhoneAndTenant（su.del_flag = '0'）一致，改一处改另一处。
-- ⚠️ 执行顺序：本脚本依赖 sys_user，必须在 twelvet.sql 之后单独执行
--   （mysql -u root -p twelvet-fast < backend/data/sms-auth-init.sql）。
--   不要靠 docker-entrypoint-initdb.d 整目录挂载：字母序 s < t，本文件先跑 → sys_user 还不存在 → 必然报错。
--   也不许加「表不存在就跳过」的静默 guard（索引永远建不出来且无告警，比报错更糟）。
-- 幂等 / 自动收敛：可重复执行，且能把旧表达式索引自动重建为正确表达式。
--   判存 + 判表达式（information_schema.STATISTICS.EXPRESSION）后条件执行：
--     ① 索引不存在 → 建；② 存在且已是正确版 → no-op；③ 存在但是旧版 → 先 DROP 再建。
--   注意 ADD UNIQUE KEY ... IF NOT EXISTS 是 MariaDB 语法，MySQL 8.0 报 ERROR 1064，不要用。
-- 两种历史旧表达式（脚本会自动识别并重建，无需手工 DROP）：
--   a) 不含 del_flag：((IF(phonenumber = '', NULL, phonenumber)), tenant_id)
--   b) 排除式（del_flag IS NULL 的行仍占位）：
--      ((IF(phonenumber = '' OR del_flag <> '0', NULL, phonenumber)), tenant_id)
--   ⚠️ 判据必须同时看 del_flag 与 <=>：b) 里也有 del_flag，只查 del_flag 会误判为「已是新版」。
-- 回滚（如需）：
-- ALTER TABLE sys_user DROP INDEX uk_sys_user_tenant_phone;
-- ============================================
SET NAMES utf8mb4;

-- 前置检查（务必先跑，有结果则先人工合并再执行下面的 DDL）：
-- SELECT tenant_id, phonenumber, COUNT(*) c FROM sys_user
--  WHERE phonenumber IS NOT NULL AND phonenumber <> '' AND del_flag = '0'
--  GROUP BY tenant_id, phonenumber HAVING c > 1;

-- 索引名下的组成部分数：正确版应为 2（表达式 + tenant_id）
SET @parts := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone');

-- 第 2 部分必须是 tenant_id 列（缺它则是全局唯一，会破坏同一自然人跨租户共存）
SET @tenant_part := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'tenant_id');

-- 第 1 部分是否已就绪：唯一 + 表达式含 del_flag + NULL 安全等号 <=> + 空串守卫 <>
-- 注意：函数索引只有第 1 部分有 EXPRESSION（第 2 部分 tenant_id 是普通列，EXPRESSION 为 NULL）
-- 注意：'%del\_flag%' 里反斜杠转义下划线，否则 _ 会当通配符
SET @expr_ok := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_tenant_phone'
      AND SEQ_IN_INDEX = 1 AND NON_UNIQUE = 0
      AND EXPRESSION LIKE '%del\_flag%' AND EXPRESSION LIKE '%<=>%' AND EXPRESSION LIKE '%<>%');

-- ① 不存在 → ADD；② 已就绪 → no-op；③ 存在但非就绪 → 单条原子 DROP + ADD（无中间窗口）
SET @ddl := IF(@parts = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)',
    IF(@parts = 2 AND @tenant_part = 1 AND @expr_ok = 1,
        'SELECT 1',
        'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_tenant_phone`, ADD UNIQUE KEY `uk_sys_user_tenant_phone` ((IF(`del_flag` <=> ''0'' AND `phonenumber` <> '''', `phonenumber`, NULL)), `tenant_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: 校验 SQL（无 MySQL 时的静态检查）**

`ADD UNIQUE KEY IF NOT EXISTS` 是无效语法（MariaDB only），**不要**再 grep 它。静态检查只能拦住一部分回归，**不能**替代真跑：

Run: `grep -c "FROM information_schema.STATISTICS" backend/data/sms-auth-init.sql` → Expected: `3`（组成部分数 / 第2部分 / 第1部分各一处）
Run: `grep -c "PREPARE stmt FROM @ddl" backend/data/sms-auth-init.sql` → Expected: `1`（三分支合并为一条条件 ALTER）
Run: `grep -c "EXPRESSION LIKE '%<=>%'" backend/data/sms-auth-init.sql` → Expected: `1`（正确版判据在位）
Run: `grep -c "NON_UNIQUE = 0" backend/data/sms-auth-init.sql` → Expected: `2`（注释说明 1 处 + 判据代码 1 处）
Run: `grep -c "ADD UNIQUE KEY IF NOT EXISTS" backend/data/sms-auth-init.sql` → Expected: `0`

> 注意：后两条 `Expected: 0` 的检查，`grep -c` 在零匹配时**退出码为 1**。
> 若在脚本里跑（如 CI），需写成 `grep -c ... || true`，否则会被误判为步骤失败。

**判据选择说明**：「正确版」必须判定为「含 `del_flag` **且** 含 `<=>`」。只查 `del_flag` 不够 ——
`bc04bbe` 的排除式旧版 `IF(phonenumber = '' OR del_flag <> '0', ...)` 里同样有 `del_flag`，
单查它会把这个真 bug 判成「已是新版」而静默跳过（这正是 N-1 的成因）。

**不要**把「`IF(phonenumber = '' OR` 只出现在注释里」这类要求写成 grep 期望 0：注释行与 SQL 行命中
同一个字面量，而该字面量在正确版与错误版里都不存在于可执行位置，永远返回 0，抓不住任何回归。
真正能区分三种情况的只有执行（见下方验证要求）。

> 静态 grep 只是便宜的第一道关，**不能**替代真实执行。有 MySQL 8 时务必实跑一遍：
> `mysql -u root -hlocalhost -P<port> twelvet-fast < backend/data/sms-auth-init.sql`
> 并在空库上确认会报 `Table '...sys_user' doesn't exist`（证明它没被静默跳过）。

- [ ] **Step 3: 提交**

```bash
git add backend/data/sms-auth-init.sql
git commit -m "feat(db): 手机号租户内唯一索引（函数索引，不新增列）"
```

---

### Task 2: 手机号查询 Mapper

**Files:**
- Modify: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/mapper/SysUserMapper.java`
- Modify: `backend/twelvet-system/twelvet-system-server/src/main/resources/mapper/system/SysUserMapper.xml`
- Test: `backend/twelvet-system/twelvet-system-server/src/test/java/com/twelvet/system/server/service/impl/LoginServiceImplSmsTest.java`（Task 4 会扩展）

**Interfaces:**
- Consumes: 无
- Produces: `SysUser selectUserByPhoneAndTenant(@Param("phonenumber") String phonenumber, @Param("tenantId") Long tenantId)` — 返回 `null` 表示该租户下无此手机号用户

- [ ] **Step 1: 在 Mapper 接口加方法**

在 `SysUserMapper.java` 的 `selectUserByUserNameAndTenant`（第 34 行）之后插入：

```java
	/**
	 * 通过手机号 + 租户查询用户（C 端手机号登录用）
	 * @param phonenumber 手机号
	 * @param tenantId 租户ID，null 表示不限租户
	 * @return 用户信息，不存在返回 null
	 */
	SysUser selectUserByPhoneAndTenant(@Param("phonenumber") String phonenumber, @Param("tenantId") Long tenantId);
```

- [ ] **Step 2: 在 XML 加对应查询**

在 `SysUserMapper.xml` 的 `selectUserByUserNameAndTenant` 之后插入：

```xml
    <select id="selectUserByPhoneAndTenant" resultMap="SysUserResult">
        <include refid="selectUserVo"/>
        where u.phonenumber = #{phonenumber} and u.del_flag = '0'
        <if test="tenantId != null">
            and u.tenant_id = #{tenantId}
        </if>
        order by u.user_id
        limit 1
    </select>
```

- [ ] **Step 3: 编译验证**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am -DskipTests compile`
Expected: 退出码 0，无编译错误

- [ ] **Step 4: 提交**

```bash
git add backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/mapper/SysUserMapper.java backend/twelvet-system/twelvet-system-server/src/main/resources/mapper/system/SysUserMapper.xml
git commit -m "feat(system): 新增按手机号+租户查询用户"
```

---

### Task 3: 短信发送与验证码服务

**Files:**
- Create: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/sms/SmsSender.java`
- Create: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/sms/MockSmsSender.java`
- Create: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/ISmsCodeService.java`
- Create: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/impl/SmsCodeServiceImpl.java`
- Modify: `backend/twelvet-admin/src/main/resources/application.yml`（加 `sms` 配置段）
- Test: `backend/twelvet-system/twelvet-system-server/src/test/java/com/twelvet/system/server/service/impl/SmsCodeServiceImplTest.java`

**Interfaces:**
- Consumes: `RedisUtils`（`com.twelvet.framework.RedisUtils`，静态方法 `setCacheObject(k,v,timeout,unit)` / `getCacheObject(k)` / `deleteObject(k)` / `incrementAndGet(k)`）
- Produces:
  - `interface SmsSender { void send(String mobile, String code); }`
  - `interface ISmsCodeService { String sendCode(String mobile); void verify(String mobile, String code); }`
    - `sendCode` 返回**要展示给前端的调试验证码**（mock 模式下即 `123456`，真实短信返回 `null`）
    - `verify` 失败抛 `TWTException`

- [ ] **Step 1: 写失败的测试**

创建 `SmsCodeServiceImplTest.java`。测试用真实的 Redis 键操作会依赖 Redis，故**注入一个可替换的存储抽象**——为避免过度设计，测试直接 mock `RedisUtils` 的静态调用不可行，改用 Spring 的 `@SpringBootTest` 也会拖入整个上下文。**采用方案：把 Redis 操作抽成一个 protected 方法供测试覆写？** 不。

**决定**：`SmsCodeServiceImpl` 依赖 `RedisUtils` 的静态方法，测试改用 **Mockito `mockStatic`**（`spring-boot-starter-test` 已含 `mockito-inline`，Spring Boot 3.5 默认 Mockito 5 支持静态 mock）。测试内容：

```java
package com.twelvet.system.server.service.impl;

import com.twelvet.framework.RedisUtils;
import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.server.sms.MockSmsSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SmsCodeServiceImplTest {

	private static final String MOBILE = "13800001005";

	@Test
	@DisplayName("发送成功：返回固定验证码 123456，写入 Redis 并设置 300 秒 TTL")
	void sendCode_writesRedisAndReturnsMockCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			String code = service.sendCode(MOBILE);

			assertEquals("123456", code);
			redis.verify(() -> RedisUtils.setCacheObject(eq("sms:code:" + MOBILE), eq("123456"), eq(300L), eq(TimeUnit.SECONDS)));
		}
	}

	@Test
	@DisplayName("60 秒内重复发送被拒绝")
	void sendCode_rejectsWithinInterval() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey("sms:send:limit:" + MOBILE)).thenReturn(true);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.sendCode(MOBILE));
			assertTrue(ex.getMessage().contains("过于频繁"));
			redis.verify(() -> RedisUtils.setCacheObject(anyString(), any(), anyLong(), any(TimeUnit.class)), never());
		}
	}

	@Test
	@DisplayName("当日发送次数超上限被拒绝")
	void sendCode_rejectsWhenDailyLimitExceeded() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.hasKey(anyString())).thenReturn(false);
			redis.when(() -> RedisUtils.incrementAndGet(anyString())).thenReturn(11L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.sendCode(MOBILE));
			assertTrue(ex.getMessage().contains("上限"));
		}
	}

	@Test
	@DisplayName("校验成功：删除验证码（防重放）")
	void verify_success_deletesCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			service.verify(MOBILE, "123456");

			redis.verify(() -> RedisUtils.deleteObject("sms:code:" + MOBILE));
		}
	}

	@Test
	@DisplayName("验证码错误：计数 +1 并抛异常")
	void verify_wrongCode_incrementsFailCount() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			redis.when(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE)).thenReturn(1L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			assertThrows(TWTException.class, () -> service.verify(MOBILE, "000000"));
			redis.verify(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE));
		}
	}

	@Test
	@DisplayName("错误达 5 次：作废验证码")
	void verify_fiveFailures_invalidatesCode() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn("123456");
			redis.when(() -> RedisUtils.incrementAndGet("sms:fail:" + MOBILE)).thenReturn(5L);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			assertThrows(TWTException.class, () -> service.verify(MOBILE, "000000"));
			redis.verify(() -> RedisUtils.deleteObject("sms:code:" + MOBILE));
		}
	}

	@Test
	@DisplayName("验证码不存在或已过期")
	void verify_missingCode_throws() {
		MockSmsSender sender = new MockSmsSender();
		try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
			redis.when(() -> RedisUtils.getCacheObject("sms:code:" + MOBILE)).thenReturn(null);
			SmsCodeServiceImpl service = new SmsCodeServiceImpl(sender);

			TWTException ex = assertThrows(TWTException.class, () -> service.verify(MOBILE, "123456"));
			assertTrue(ex.getMessage().contains("失效"));
		}
	}
}
```

- [ ] **Step 2: 运行测试确认失败（RED）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=SmsCodeServiceImplTest`
Expected: 编译失败 — `SmsCodeServiceImpl` / `MockSmsSender` 不存在

- [ ] **Step 3: 实现 `SmsSender` 与 `MockSmsSender`**

`SmsSender.java`：

```java
package com.twelvet.system.server.sms;

/**
 * 短信发送接口。真实服务商（阿里云/腾讯云）接入时新增实现并由 sms.provider 选择。
 */
public interface SmsSender {

	/**
	 * 发送验证码短信
	 * @param mobile 手机号
	 * @param code 验证码
	 */
	void send(String mobile, String code);

}
```

`MockSmsSender.java`：实现 `SmsSender`，`send` 只打日志（`log.info("【Mock 短信】{} 验证码：{}", mobile, code)`），类加 `@Component` 与 `@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)`。

- [ ] **Step 4: 实现 `ISmsCodeService` 与 `SmsCodeServiceImpl`**

`ISmsCodeService.java`：

```java
package com.twelvet.system.server.service;

public interface ISmsCodeService {

	/**
	 * 发送验证码
	 * @param mobile 手机号
	 * @return 调试用验证码（mock 模式返回值，真实短信返回 null）
	 */
	String sendCode(String mobile);

	/**
	 * 校验验证码，失败抛 TWTException
	 * @param mobile 手机号
	 * @param code 用户输入的验证码
	 */
	void verify(String mobile, String code);

}
```

`SmsCodeServiceImpl.java` 实现要点（构造注入 `SmsSender`，`@Service`）：

- 常量：`CODE_EXPIRE_SECONDS = 300`、`SEND_INTERVAL_SECONDS = 60`、`DAILY_LIMIT = 10`、`MAX_VERIFY_ATTEMPTS = 5`、`MOCK_CODE = "123456"`
- Redis 键：`sms:code:{mobile}`、`sms:send:limit:{mobile}`、`sms:daily:{mobile}:{yyyyMMdd}`、`sms:fail:{mobile}`
- `sendCode(mobile)`：
  1. 校验手机号格式 `^1[3-9]\d{9}$`，不符抛 `TWTException("手机号格式不正确")`
  2. `RedisUtils.hasKey(limitKey)` 为真 → 抛 `TWTException("请求过于频繁，请稍后再试")`
  3. `long count = RedisUtils.incrementAndGet(dailyKey)`；`count > DAILY_LIMIT` → 抛 `TWTException("该手机号今日发送次数已达上限")`；当 `count == 1` 时调 `RedisUtils.expire(dailyKey, secondsToMidnight, TimeUnit.SECONDS)`（`RedisUtils.java:89` 有此方法）
  4. 生成验证码：配置项 `sms.provider == "mock"` → `MOCK_CODE`；否则 6 位随机数字（`String.format("%06d", new SecureRandom().nextInt(1000000))`）
  5. 写 `sms:code:{mobile}` = code，TTL 300s
  6. 写 `sms:send:limit:{mobile}` = "1"，TTL 60s
  7. 调用 `smsSender.send(mobile, code)`
  8. 返回 `sms.provider == "mock"` 时的 code，否则 `null`
- `verify(mobile, code)`：
  1. `RedisUtils.getCacheObject(codeKey)` 为 null → 抛 `TWTException("验证码已失效，请重新获取")`
  2. 与 `code` 不相等 → `long fails = RedisUtils.incrementAndGet(failKey)`；`fails == 1` 时 `RedisUtils.expire(failKey, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS)`；若 `fails >= MAX_VERIFY_ATTEMPTS` 则 `deleteObject(codeKey)` 并抛 `TWTException("验证码错误次数过多，请重新获取")`；否则抛 `TWTException("验证码不正确")`
  3. 相等 → `RedisUtils.deleteObject(codeKey)`，并 `deleteObject(failKey)`

配置读取用 `@Value("${sms.provider:mock}") private String provider;`（项目内 `TenantFilter` 等处均用此写法）。

- [ ] **Step 5: 加配置段**

在 `backend/twelvet-admin/src/main/resources/application.yml` 的 `twelvet:` 配置块之后追加：

```yaml
# 短信验证码（C 端手机号登录）
sms:
  # mock = 不真发短信，验证码固定 123456 并在接口响应中返回；
  # aliyun = 真实短信（尚未实现，接入前保持 mock）
  provider: mock
```

- [ ] **Step 6: 运行测试确认通过（GREEN）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=SmsCodeServiceImplTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 7: 提交**

```bash
git add backend/twelvet-system/twelvet-system-server/src backend/twelvet-admin/src/main/resources/application.yml
git commit -m "feat(system): 短信验证码服务（Redis 频控 + mock 发送器）"
```

---

### Task 4: 手机号登录（含自动建号）

**Files:**
- Modify: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/ILoginService.java`
- Modify: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/impl/LoginServiceImpl.java`
- Create: `backend/twelvet-system/twelvet-system-api/src/main/java/com/twelvet/system/api/domain/dto/SmsLoginDTO.java`
- Test: `backend/twelvet-system/twelvet-system-server/src/test/java/com/twelvet/system/server/service/impl/LoginServiceImplSmsTest.java`

**Interfaces:**
- Consumes: `ISmsCodeService.verify(String, String)`（Task 3）、`SysUserMapper.selectUserByPhoneAndTenant(String, Long)`（Task 2）
- Produces: `LoginVO smsLogin(String mobile, String code)` on `ILoginService`

- [ ] **Step 1: 建 DTO**

`SmsLoginDTO.java`：`mobile` 与 `code` 两个字段，均 `@NotBlank`，`mobile` 加 `@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")`，带 `@Schema` 注解与 getter/setter，仿 `LoginDTO` 风格。

- [ ] **Step 2: 写失败的测试**

`LoginServiceImplSmsTest.java`（Mockito 纯单测，不启动 Spring）：

```java
package com.twelvet.system.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.api.domain.SysUser;
import com.twelvet.system.api.domain.vo.LoginVO;
import com.twelvet.system.server.mapper.SysUserMapper;
import com.twelvet.system.server.service.ISmsCodeService;
import com.twelvet.system.server.service.ISysPermissionService;
import com.twelvet.system.server.service.ISysUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceImplSmsTest {

	private static final String MOBILE = "13800001005";

	private final SysUserMapper userMapper = mock(SysUserMapper.class);
	private final ISysPermissionService permissionService = mock(ISysPermissionService.class);
	private final ISmsCodeService smsCodeService = mock(ISmsCodeService.class);
	private final ISysUserService sysUserService = mock(ISysUserService.class);

	@Test
	@DisplayName("已有手机号：直接登录，不建号")
	void smsLogin_existingUser_logsIn() {
		SysUser existing = new SysUser();
		existing.setUserId(200L);
		existing.setUsername("u13800001005");
		existing.setStatus("0");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(existing);
		when(permissionService.getMenuPermission(200L)).thenReturn(Set.of("marketplace:home"));
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		verify(smsCodeService).verify(MOBILE, "123456");
		verify(sysUserService, never()).insertUser(any());
	}

	@Test
	@DisplayName("新手机号：自动建号，字段符合约定")
	void smsLogin_newUser_createsAccount() {
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(null);
		when(sysUserService.insertUser(any())).thenAnswer(inv -> {
			SysUser u = inv.getArgument(0);
			u.setUserId(300L);
			return 1;
		});
		when(permissionService.getMenuPermission(anyLong())).thenReturn(Set.of());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		verify(sysUserService).insertUser(any());
	}

	@Test
	@DisplayName("验证码错误：抛出，不查询用户")
	void smsLogin_badCode_throws() {
		doThrow(new TWTException("验证码不正确")).when(smsCodeService).verify(anyString(), anyString());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "000000"));
		verify(userMapper, never()).selectUserByPhoneAndTenant(anyString(), any());
	}

	@Test
	@DisplayName("账号被冻结：拒绝登录")
	void smsLogin_frozenUser_rejected() {
		SysUser frozen = new SysUser();
		frozen.setUserId(200L);
		frozen.setStatus("1");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any())).thenReturn(frozen);
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		TWTException ex = assertThrows(TWTException.class, () -> service.smsLogin(MOBILE, "123456"));
		assertTrue(ex.getMessage().contains("冻结"));
	}

	@Test
	@DisplayName("并发建号冲突：捕获 DuplicateKeyException 后重查并登录")
	void smsLogin_concurrentCreate_fallsBackToRequery() {
		SysUser created = new SysUser();
		created.setUserId(301L);
		created.setStatus("0");
		when(userMapper.selectUserByPhoneAndTenant(eq(MOBILE), any()))
			.thenReturn(null)
			.thenReturn(created);
		when(sysUserService.insertUser(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));
		when(permissionService.getMenuPermission(anyLong())).thenReturn(Set.of());
		LoginServiceImpl service = new LoginServiceImpl(userMapper, permissionService, smsCodeService, sysUserService);

		service.smsLogin(MOBILE, "123456");

		verify(userMapper, org.mockito.Mockito.times(2)).selectUserByPhoneAndTenant(eq(MOBILE), any());
	}
}
```

- [ ] **Step 3: 运行测试确认失败（RED）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=LoginServiceImplSmsTest`
Expected: 编译失败 — `smsLogin` 方法不存在

- [ ] **Step 4: 实现 `smsLogin`**

在 `ILoginService` 加：

```java
	/**
	 * 手机号验证码登录（首次登录自动建号）
	 * @param mobile 手机号
	 * @param code 验证码
	 * @return LoginVO
	 */
	LoginVO smsLogin(String mobile, String code);
```

`LoginServiceImpl` 构造函数改为四参（`SysUserMapper`、`ISysPermissionService`、`ISmsCodeService`、`ISysUserService`）。实现 `smsLogin`：

1. `smsCodeService.verify(mobile, code)`
2. `Long tenantId = TenantContext.get()`
3. `SysUser user = userMapper.selectUserByPhoneAndTenant(mobile, tenantId)`
4. 为 null → 建号：
   - 构造 `SysUser`：`username = "u" + mobile + "_" + IdUtils.simpleUUID().substring(0, 6)`（避开 `uk_sys_user_tenant_username` 的软删占位，见设计文档 §2.2）、`nickName = "邻里用户" + mobile.substring(7)`、`phonenumber = mobile`、`userType = "02"`、`avatar = ""`、`status = "0"`、`delFlag = "0"`、`password = BCrypt.hashpw(IdUtils.simpleUUID())`、`roleIds = new Long[]{2L}`、`tenantId = tenantId`（若为 null 则不设置，交给 `TenantFieldInterceptor`）
   - `try { sysUserService.insertUser(user); } catch (DuplicateKeyException e) { user = userMapper.selectUserByPhoneAndTenant(mobile, tenantId); }`
5. `user.getStatus().equals("1")` → 抛 `TWTException("账号已被冻结")`
6. `buildLoginUser(user)` → `SecurityUtils.loginByDevice(loginUser, DeviceType.PC)` → 组装 `LoginVO`（与 `login()` 第 62-75 行相同的三步：accessToken / refreshToken 写 Redis / expiresIn）。**抽一个私有方法 `issueToken(LoginUser)` 供 `login` 与 `smsLogin` 共用**，避免复制。
7. 复用 `login()` 里已有的 `SysLoginInfo` 异步事件写法，`setMsg` 用 `"手机号登录成功"`，`setStatus(SecurityConstants.LOGIN_SUCCESS)`，在**校验通过之后**发布（失败路径的日志由 `SmsCodeService` 的异常体现，不在本方法重复记录）。

**返回值结构必须与 `/auth/oauth2/token` 完全一致** —— `LoginVO` 的字段通过 `@JsonProperty` 序列化为 `access_token` / `refresh_token` / `expires_in`（见 `LoginVO.java:27-38`），前端 `setAuthority()`（`ui/src/utils/twelvet.ts:87-103`）直接消费这三个字段，不得改动。

- [ ] **Step 5: 运行测试确认通过（GREEN）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=LoginServiceImplSmsTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: 提交**

```bash
git add backend/twelvet-system
git commit -m "feat(system): 手机号验证码登录，首次登录自动建号"
```

---

### Task 5: 登录与发码端点

**Files:**
- Modify: `backend/twelvet-admin/src/main/java/com/twelvet/admin/controller/LoginController.java`
- Create: `backend/twelvet-admin/src/main/java/com/twelvet/admin/controller/dto/SmsSendDTO.java`（若 admin 无 dto 包则放 `com.twelvet.admin.controller.dto`）
- Test: `backend/twelvet-system/twelvet-system-server/src/test/java/com/twelvet/system/server/service/impl/LoginServiceImplSmsTest.java`（无需新增，Task 4 已覆盖服务层）

**Interfaces:**
- Consumes: `ILoginService.smsLogin(String, String)`（Task 4）、`ISmsCodeService.sendCode(String)`（Task 3）
- Produces: HTTP `POST /auth/sms/send`、`POST /auth/sms/login`

- [ ] **Step 1: 加两个端点**

在 `LoginController` 注入 `ISmsCodeService`（构造注入），新增：

```java
	/**
	 * 发送短信验证码
	 * @param body 手机号
	 * @return 调试验证码（仅 mock 模式返回）
	 */
	@SaIgnore
	@PostMapping("/sms/send")
	public JsonResult<Map<String, Object>> sendSmsCode(@Validated @RequestBody SmsSendDTO body) {
		String debugCode = smsCodeService.sendCode(body.getMobile());
		Map<String, Object> data = new HashMap<>(4);
		data.put("expireIn", 300);
		if (debugCode != null) {
			data.put("mockCode", debugCode);
		}
		return JsonResult.success(data);
	}

	/**
	 * 手机号验证码登录（首次登录自动建号）
	 * @param body 手机号 + 验证码
	 * @return LoginVO
	 */
	@SaIgnore
	@PostMapping("/sms/login")
	public JsonResult<LoginVO> smsLogin(@Validated @RequestBody SmsLoginDTO body) {
		return JsonResult.success(loginService.smsLogin(body.getMobile(), body.getCode()));
	}
```

`SmsSendDTO`：只有一个 `mobile` 字段，`@NotBlank` + `@Pattern(regexp = "^1[3-9]\\d{9}$")`。

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn -q -pl twelvet-admin -am -DskipTests compile`
Expected: 退出码 0

- [ ] **Step 3: 提交**

```bash
git add backend/twelvet-admin/src/main/java/com/twelvet/admin/controller
git commit -m "feat(admin): 暴露短信发码与手机号登录端点"
```

---

### Task 6: 后台改手机号的唯一性校验

**Files:**
- Modify: `backend/twelvet-system/twelvet-system-server/src/main/java/com/twelvet/system/server/service/impl/SysUserServiceImpl.java:271-282`
- Test: `backend/twelvet-system/twelvet-system-server/src/test/java/com/twelvet/system/server/service/impl/SysUserServiceImplPhoneUniqueTest.java`

**Interfaces:**
- Consumes: `SysUserMapper.checkPhoneUnique(String, Long)`（已存在）、`UserConstants.NOT_UNIQUE`（`"1"`）
- Produces: `updateUser` 在手机号冲突时抛 `TWTException("手机号已被占用")`

- [ ] **Step 1: 写失败的测试**

```java
package com.twelvet.system.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.system.api.domain.SysUser;
import com.twelvet.system.server.mapper.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceImplPhoneUniqueTest {

	private final SysUserMapper userMapper = mock(SysUserMapper.class);
	private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
	private final SysPostMapper postMapper = mock(SysPostMapper.class);
	private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
	private final SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);
	private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);

	@Test
	@DisplayName("改为已被占用的手机号：抛业务异常，不落库")
	void updateUser_duplicatePhone_throws() {
		SysUser other = new SysUser();
		other.setUserId(999L);
		when(userMapper.checkPhoneUnique(any(), any())).thenReturn(other);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper,
				userRoleMapper, userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		TWTException ex = assertThrows(TWTException.class, () -> service.updateUser(input));

		assertTrue(ex.getMessage().contains("手机号已被占用"));
		verify(userMapper, never()).updateUser(any());
	}

	@Test
	@DisplayName("手机号未被占用：正常更新")
	void updateUser_uniquePhone_updates() {
		when(userMapper.checkPhoneUnique(any(), any())).thenReturn(null);
		when(userMapper.updateUser(any())).thenReturn(1);
		SysUserServiceImpl service = new SysUserServiceImpl(userMapper, roleMapper, postMapper,
				userRoleMapper, userPostMapper, deptMapper);
		SysUser input = new SysUser();
		input.setUserId(1L);
		input.setPhonenumber("13800001005");

		service.updateUser(input);

		verify(userMapper).updateUser(any());
	}
}
```

- [ ] **Step 2: 运行测试确认失败（RED）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=SysUserServiceImplPhoneUniqueTest`
Expected: 第一个用例失败（未抛异常）

- [ ] **Step 3: 在 `updateUser` 加前置校验**

在 `SysUserServiceImpl.updateUser` 方法体最前面加：

```java
		// 手机号唯一约束（uk_sys_user_tenant_phone）兜底前先给出可读提示，避免 500
		if (StrUtils.isNotEmpty(user.getPhonenumber())) {
			SysUser existing = sysUserMapper.checkPhoneUnique(user.getPhonenumber(), TenantContext.get());
			if (Objects.nonNull(existing) && !existing.getUserId().equals(user.getUserId())) {
				throw new TWTException("手机号已被占用");
			}
		}
```

- [ ] **Step 4: 运行测试确认通过（GREEN）**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server -am test -Dtest=SysUserServiceImplPhoneUniqueTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交**

```bash
git add backend/twelvet-system
git commit -m "fix(system): 后台改手机号时前置唯一校验，避免唯一索引冲突 500"
```

---

### Task 7: 前端登录页接真接口

**Files:**
- Modify: `ui/src/pages/Login/service.ts`
- Modify: `ui/src/pages/Login/index.tsx`

**Interfaces:**
- Consumes: `POST /auth/sms/send` → `{ code:200, data:{ expireIn, mockCode? } }`；`POST /auth/sms/login` → `{ code:200, data: LoginVO }`（字段 `access_token`/`refresh_token`/`expires_in`）
- Produces: 无（终端用户功能）

- [ ] **Step 1: 在 service.ts 加两个请求函数**

```ts
/**
 * 发送短信验证码
 * @param mobile 手机号
 */
export async function sendSmsCode(mobile: string) {
    return request(`${controller}/sms/send`, {
        method: 'POST',
        data: { mobile },
    });
}

/**
 * 手机号验证码登录（首次登录自动注册）
 * @param mobile 手机号
 * @param code 验证码
 */
export async function smsLogin(mobile: string, code: string) {
    return request(`${controller}/sms/login`, {
        method: 'POST',
        data: { mobile, code },
    });
}
```

- [ ] **Step 2: 替换登录页的假验证码逻辑**

在 `ui/src/pages/Login/index.tsx`：

1. import 增加：`import { login, sendSmsCode, smsLogin } from './service';`（原为 `import { login } from './service';`）
2. `handleSubmit` 中，`type === 'mobile'` 分支改为调用 `smsLogin(fv.mobile, fv.code)`，不再走 `login()`：

```ts
            const data =
                type === 'account'
                    ? await login({ ...payload, type } as any)
                    : await smsLogin(fv.mobile as string, fv.code as string);
```

3. `ProFormCaptcha` 的 `onGetCaptcha` 改为：

```tsx
                                                    onGetCaptcha={async () => {
                                                        const res: any = await sendSmsCode(captchPhone);
                                                        if (res?.data?.mockCode) {
                                                            loginForm.setFieldValue('code', res.data.mockCode);
                                                            message.info(`当前为调试模式，验证码：${res.data.mockCode}`);
                                                        } else {
                                                            message.success('验证码已发送');
                                                        }
                                                    }}
```

4. `placeholder` 去掉 `(Test：15788888888)` 字样，改为 `手机号`
5. 删除 `console.log(\`手机号码${captchPhone}\`)` 与 `message.success('获取验证码成功！验证码为：1234')`

- [ ] **Step 3: 类型检查**

Run: `cd ui && npx tsc --noEmit -p tsconfig.json 2>&1 | head -20`
Expected: 无与 `Login` 相关的类型错误（项目存量错误可忽略，但不得新增）

- [ ] **Step 4: 构建验证**

Run: `cd ui && npm run build 2>&1 | tail -5`
Expected: 输出 `Build index.html`，退出码 0

- [ ] **Step 5: 提交**

```bash
git add ui/src/pages/Login
git commit -m "feat(ui): 登录页手机号 Tab 接入真实验证码登录"
```

---

### Task 8: 端到端回归

**Files:** 无（仅验证）

**Interfaces:**
- Consumes: Task 1-7 全部
- Produces: 验证证据

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server,twelvet-admin -am test 2>&1 | grep -E "Tests run|BUILD"`
Expected: `BUILD SUCCESS`，无 `Failures` / `Errors`

- [ ] **Step 2: 代码格式检查**

Run: `cd backend && mvn -q -pl twelvet-system/twelvet-system-server,twelvet-admin spring-javaformat:validate`
Expected: 退出码 0

- [ ] **Step 3: 手工验证（需本地启动 MySQL + Redis + backend + ui）**

逐条记录实际结果：

1. 执行 `backend/data/sms-auth-init.sql` → 无报错
2. 启动后端，`POST /auth/sms/send` body `{"mobile":"13900001111"}` → 200 且 `data.mockCode == "123456"`
3. 立即再发一次 → 400 `请求过于频繁，请稍后再试`
4. `POST /auth/sms/login` body `{"mobile":"13900001111","code":"123456"}` → 200 且含 `access_token`
5. 查库 `SELECT user_id,username,nick_name,user_type,phonenumber,status,tenant_id,global_user_id FROM sys_user WHERE phonenumber='13900001111'` → 1 行，`username` 以 `u13900001111_` 开头（后接 6 位随机十六进制，总长 19）、`nick_name='邻里用户1111'`、`user_type='02'`、`global_user_id = user_id`
6. 查 `sys_user_role` → 该 user_id 关联 `role_id=2`
7. 同一手机号再登录 → 200，且 `sys_user` 该手机号仍只有 1 行
8. 错误验证码 `000000` 连试 6 次 → 前 5 次 400 `验证码不正确`，第 5 次后 400 `验证码错误次数过多，请重新获取`，此后正确码也失效
9. 前端 `/login` 手机号 Tab 输入手机号 → 点「获取验证码」→ 验证码框自动填入 `123456` 并有 info 提示 → 点登录 → 进入 `/home`，侧边栏显示 C 端菜单
10. `admin` + 原密码登录 → 正常进入，侧边栏为管理台菜单
11. 后台「用户管理」把某用户手机号改为已存在值 → 提示「手机号已被占用」，非 500

- [ ] **Step 4: 提交验证记录**

把 Step 3 的实际结果追加到设计文档的「实施记录」小节（若设计文档无该小节则新建），然后：

```bash
git add docs/superpowers/specs/2026-10-20-c-end-phone-auth-design.md
git commit -m "docs(spec): 补记手机号登录实施后验证结果"
```
