# 举报处理闭环 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通「C 端举报 → 落 `report` 表 → 运营看得懂 → 处置真生效 + 站内通知」四段链路，替换现有空壳页面与只改状态的假处理。

**Architecture:** 举报业务全部落在 `twelvet-product-server`（`ReportServiceImpl` 显式 bean 名 `productReportServiceImpl`，避开与 AI 模块同名类的启动冲突）；`twelvet-trade-server` 的 `AdminMarketplaceController` 保持 `/admin/marketplace/report/*` 路径不变，内部改为委托 product 侧 `IReportService`。处置动作直接改 `product` / `feed` / `sys_user`，封号后 `StpUtil.kickout` 踢下线；通知走 `im_notice`。依赖方向 trade → product 单向，无环。

**Tech Stack:** Java 17 + Spring Boot 3.5.4 + MyBatis XML（无 MyBatis-Plus）+ Sa-Token 1.44.0 + Mockito（含 `mockStatic`）；前端 Umi Max 4 + React 18 + AntD 5 + ProTable（npm）。

**Spec:** `docs/superpowers/specs/2026-10-29-report-handling-design.md`

## Global Constraints

- **bean 名必须显式**：新建的 `ReportServiceImpl` 用 `@Service("productReportServiceImpl")`。裸 `@Service` 会与 `com.twelvet.ai.server.service.impl.ReportServiceImpl`（社区日报）撞名，`FastApplication` 扫全仓 `com.twelvet` 且未开 `allow-bean-definition-overriding` → 启动即 `ConflictingBeanDefinitionException`。
- **模块依赖方向不可反转**：`twelvet-product-server` 不得依赖 `twelvet-trade-server`（后者已依赖前者）。封号走 product 的 `UserMapper.updateStatus`，不用 trade 的 `AdminMarketplaceMapper.updateUserStatus`（那条留给运营「用户管理」页）。
- 租户模式：所有新 SQL 用 `<if test="tenantId != null">` 手写条件（零侵入回滚）；`TenantContext.get()` 为 null 即不过滤。跨租户校验只在 `targetType=user` 时需要显式比对（product/feed 的 `selectById` 自带过滤）。
- 错误一律抛 `TWTException`（`com.twelvet.framework.core.exception.TWTException`），Controller 沿用现有不捕获风格（全局异常处理器转 JsonResult）。
- JSON 序列化/反序列化用 `com.twelvet.framework.utils.JacksonUtils.toJson(Object)` / `readValue(String, Class)`，禁止手拼 JSON。
- Java 格式：提交前对改动模块执行 `mvn spring-javaformat:apply -pl <模块> -am`；现有代码是 tab 缩进。
- 包管理器一律 npm，禁 yarn。
- 每个任务独立 commit，消息格式 `feat(report): ...` / `feat(ui): ...`，结尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- 状态词汇固定：`report.status` ∈ {`pending`, `done`}；`report.action` ∈ {`dismiss`, `warn`, `remove_product`, `remove_feed`, `ban_user`}；`product.status` 处置只允许从 {`onsale`, `auditing`} 转出。

## Review Focus

1. **已付款待发货商品被举报下架**：`product.status='locked'`（买家已付、卖家待发货）时，运营若强行下架会让 `markSold` 的 `WHERE stock=0 AND status='locked'` 永不命中，已付款订单商品状态错乱——Task 6 测试锁定 `remove_product` 在 `locked`/`sold` 时抛 `TWTException` 且 `updateStatus` 未被调用。
2. **并发重复处理同一条举报**：两个运营同时点「处理」，必须恰好一个成功、另一个收到「已被处理」——Task 6 测试锁定 `UPDATE ... WHERE status='pending'` 返回 0 时抛异常且不执行任何处置。
3. **封号后仍在线**：`LoginServiceImpl` 只在登录时校验 `status='1'`，token 有效期 86400s 且无逐请求校验。不踢会话则封号最长 24 小时不生效——Task 6 测试断言 `StpUtil.kickout(ownerId)` 被调用。
4. **通知失败反而回滚处理**：`sendNotice` 若带 `@Transactional`，异常会把共享事务标 rollback-only，外层提交抛 `UnexpectedRollbackException`，与「通知失败不影响处理」相反——Task 5 的 `sendNotice` **不加**事务注解；Task 6 测试锁定通知抛异常时处理仍成功落库。
5. **软删动态被当作「仍存在」**：`feed` 是软删（`status='deleted'` 行还在），`product` 是硬删。若按「行存在即存在」判定，运营会看到已删动态显示正常、`remove_feed` 做无意义更新——Task 6 测试锁定 feed `status='deleted'` 时 `targetExists=false`。

---

### Task 1: 数据库迁移与 `report` 表约束

**Files:**
- Create: `backend/sql/report_feature.sql`

**Interfaces:**
- Produces: `report` 表具备唯一键 `uk_reporter_target (reporter_id, target_type, target_id)`、索引 `idx_target`、`idx_status_time`；`feed_report` 表被删除。
- Produces: 迁移脚本可在既有库上重复执行不报错（幂等）。

- [x] **Step 1: 写迁移脚本**

创建 `backend/sql/report_feature.sql`，内容：

```sql
-- ============================================================
-- 举报处理闭环：report 表约束补齐 + 废弃表清理
-- 设计文档：docs/superpowers/specs/2026-10-29-report-handling-design.md
-- 前置：docs/db/twelvet-fast_2026-10-21.sql 基线已导入
-- 幂等：所有 ADD 前先查 information_schema，存在则跳过
-- ============================================================

SET NAMES utf8mb4;

-- 1. 同人同对象唯一（重复举报走 UPDATE 复活，见 ReportServiceImpl.submit）
SET @has_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                  AND INDEX_NAME = 'uk_reporter_target');
SET @sql := IF(@has_uk = 0,
    'ALTER TABLE `report` ADD UNIQUE KEY `uk_reporter_target` (`reporter_id`,`target_type`,`target_id`)',
    'SELECT "uk_reporter_target 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 按对象反查（运营列表按 targetType 批量取摘要）
SET @has_idx_target := (SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                          AND INDEX_NAME = 'idx_target');
SET @sql := IF(@has_idx_target = 0,
    'ALTER TABLE `report` ADD KEY `idx_target` (`target_type`,`target_id`)',
    'SELECT "idx_target 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 待处理队列按时间排序
SET @has_idx_status_time := (SELECT COUNT(*) FROM information_schema.STATISTICS
                             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report'
                               AND INDEX_NAME = 'idx_status_time');
SET @sql := IF(@has_idx_status_time = 0,
    'ALTER TABLE `report` ADD KEY `idx_status_time` (`status`,`create_time`)',
    'SELECT "idx_status_time 已存在，跳过"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 列注释对齐实际取值
ALTER TABLE `report` MODIFY COLUMN `action` varchar(20) DEFAULT NULL
    COMMENT 'dismiss/warn/remove_product/remove_feed/ban_user';
ALTER TABLE `report` MODIFY COLUMN `target_type` varchar(20) NOT NULL
    COMMENT 'product/feed/user';

-- 5. 删除重复建模的死表 feed_report（已核实 0 行、全仓 0 代码引用）
DROP TABLE IF EXISTS `feed_report`;
```

- [x] **Step 2: 在本地库执行，确认无报错**

Run: `mysql -u<user> -p <db> < backend/sql/report_feature.sql`
Expected: 无 ERROR；重复执行第二次输出「已存在，跳过」而非报错。

- [x] **Step 3: 验证约束生效**

Run:
```sql
SHOW INDEX FROM report WHERE Key_name IN ('uk_reporter_target','idx_target','idx_status_time');
SHOW TABLES LIKE 'feed_report';
```
Expected: 前三条索引各至少一行；`feed_report` 无输出（已删除）。

- [x] **Step 4: Commit**

```bash
git add backend/sql/report_feature.sql
git commit -m "feat(report): 补齐 report 表唯一键与索引，删除废弃表 feed_report

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 三个 Mapper 的批量查询与封号写入

**Files:**
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/ProductMapper.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/ProductMapper.xml`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/FeedMapper.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/FeedMapper.xml`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/UserMapper.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/UserMapper.xml`

**Interfaces:**
- Produces: `ProductMapper.selectByIds(@Param("productIds") List<Long>, @Param("tenantId") Long) : List<Product>`
- Produces: `FeedMapper.selectByIds(@Param("feedIds") List<Long>, @Param("tenantId") Long) : List<Feed>`
- Produces: `UserMapper.selectByIds(@Param("userIds") List<Long>) : List<Map<String,Object>>`（每行含 `user_id`/`nick_name`/`avatar`/`status`/`del_flag`）
- Produces: `UserMapper.updateStatus(@Param("userId") Long, @Param("status") Integer, @Param("tenantId") Long) : int`

> 注意：`UserMapper` 已有 `selectUserByIds`（只返回展示字段、无 `status`/`del_flag`）。**新增** `selectByIds` 而非改旧方法——旧方法被 `UserProfileServiceImpl` 等调用，改字段有回归风险。

- [x] **Step 1: 写失败测试**

创建 `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/mapper/ReportMapperContractTest.java`，用反射断言接口方法存在与签名（Mapper 接口无实现，无法用 Mockito 验证 SQL，SQL 正确性由 Task 9 的手工验收兜底）：

```java
package com.twelvet.product.server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 举报闭环所需的 Mapper 方法契约。
 * <p>
 * MyBatis 接口无实现类，SQL 本身的正确性靠 Task 9 手工验收；
 * 本测试只钉死「方法名 + 参数顺序 + 返回类型」——这些是跨任务引用的公共契约，
 * 改名或改顺序会让 ReportServiceImpl 静默编译失败或运行期绑定错 SQL。
 * </p>
 */
class ReportMapperContractTest {

	@Test
	@DisplayName("ProductMapper.selectByIds 批量取商品摘要")
	void productSelectByIds() throws NoSuchMethodException {
		Method m = ProductMapper.class.getMethod("selectByIds", List.class, Long.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("FeedMapper.selectByIds 批量取动态摘要")
	void feedSelectByIds() throws NoSuchMethodException {
		Method m = FeedMapper.class.getMethod("selectByIds", List.class, Long.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("UserMapper.selectByIds 批量取用户摘要（含 status/del_flag）")
	void userSelectByIds() throws NoSuchMethodException {
		Method m = UserMapper.class.getMethod("selectByIds", List.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("UserMapper.updateStatus 封号写入")
	void userUpdateStatus() throws NoSuchMethodException {
		Method m = UserMapper.class.getMethod("updateStatus", Long.class, Integer.class, Long.class);
		assertEquals(int.class, m.getReturnType());
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportMapperContractTest`
Expected: 编译失败或 4 个用例全红（`NoSuchMethodException`）。

- [x] **Step 3: 实现接口方法**

`ProductMapper.java` 追加（放在 `selectById` 之后）：

```java
	/**
	 * 按 ID 批量查商品（举报处理用：已下架/已驳回的商品也要能显示摘要，故不过滤 status）
	 * @param productIds 商品ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 商品列表
	 */
	List<Product> selectByIds(@Param("productIds") List<Long> productIds, @Param("tenantId") Long tenantId);
```

`FeedMapper.java` 追加：

```java
	/**
	 * 按 ID 批量查动态（举报处理用：软删的动态也要能显示摘要，故不过滤 status）
	 * @param feedIds 动态ID列表
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 动态列表
	 */
	List<Feed> selectByIds(@Param("feedIds") List<Long> feedIds, @Param("tenantId") Long tenantId);
```

`UserMapper.java` 追加：

```java
	/**
	 * 按 ID 批量查用户（举报处理用：含 status/del_flag 供存在性与封禁态判定）
	 * @param userIds 用户ID列表
	 * @return 用户列表，每行含 user_id/nick_name/avatar/status/del_flag
	 */
	List<Map<String, Object>> selectByIds(@Param("userIds") List<Long> userIds);

	/**
	 * 更新用户账号状态（举报处置封号用；与 trade 的 updateUserStatus 并存，避免模块依赖成环）
	 * @param userId 用户ID
	 * @param status 账号状态（0正常 1停用）
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 影响行数
	 */
	int updateStatus(@Param("userId") Long userId, @Param("status") Integer status,
			@Param("tenantId") Long tenantId);
```

- [x] **Step 4: 实现 XML SQL**

`ProductMapper.xml` 追加（`productCols` 已存在，直接复用）：

```xml
    <select id="selectByIds" resultMap="ProductResult">
        SELECT <include refid="productCols"/>
        FROM product p
        WHERE p.product_id IN
        <foreach collection="productIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        <if test="tenantId != null">AND p.tenant_id = #{tenantId}</if>
    </select>
```

`FeedMapper.xml` 追加（`feedCols` 已存在）：

```xml
    <select id="selectByIds" resultMap="FeedResult">
        SELECT <include refid="feedCols"/>
        FROM feed f
        WHERE f.feed_id IN
        <foreach collection="feedIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        <if test="tenantId != null">AND f.tenant_id = #{tenantId}</if>
    </select>
```

`UserMapper.xml` 追加：

```xml
    <select id="selectByIds" resultType="map">
        SELECT user_id, nick_name, avatar, status, del_flag
        FROM sys_user
        WHERE user_id IN
        <foreach collection="userIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </select>

    <update id="updateStatus">
        UPDATE sys_user SET status = #{status}, update_time = NOW()
        WHERE user_id = #{userId} AND del_flag = '0'
        <if test="tenantId != null">AND tenant_id = #{tenantId}</if>
    </update>
```

- [x] **Step 5: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportMapperContractTest`
Expected: 4 passed。

- [x] **Step 6: 确认未破坏既有测试**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test`
Expected: 全绿（原有 `RecommendScorerTest` / `InternalRecommend*Test` 不受影响）。

- [x] **Step 7: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 补三个 Mapper 的批量查询与封号写入

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `Report` 实体 + `ReportMapper`（report 表唯一读写点）

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/domain/Report.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/mapper/ReportMapper.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/resources/mapper/product/ReportMapper.xml`

**Interfaces:**
- Consumes: 无（Task 2 的 Mapper 在后续任务才用到）
- Produces: `Report` POJO，字段 `reportId`/`reporterId`/`targetType`/`targetId`/`reason`/`evidenceImages`/`status`/`action`/`handleNote`/`handlerId`/`createTime`/`handleTime`
- Produces: `ReportMapper.insert(Report) : int`
- Produces: `ReportMapper.selectByReporterAndTarget(@Param("reporterId") Long, @Param("targetType") String, @Param("targetId") Long) : Report`
- Produces: `ReportMapper.revive(@Param("reportId") Long, @Param("reason") String, @Param("evidenceImages") String) : int`
- Produces: `ReportMapper.selectList(@Param("status") String, @Param("tenantId") Long) : List<Report>`（`status` 为 null 表示全部）
- Produces: `ReportMapper.selectById(@Param("reportId") Long, @Param("tenantId") Long) : Report`
- Produces: `ReportMapper.markHandled(@Param("reportId") Long, @Param("action") String, @Param("note") String, @Param("handlerId") Long) : int`（`WHERE status='pending'`，rows=0 即已被处理）

> `Report` **不继承 `BaseEntity`**：`report` 表没有 `create_by`/`update_by` 等审计列，继承会让 `TenantFieldInterceptor` 尝试写不存在的 `tenant_id` 列。

- [x] **Step 1: 写失败测试**

创建 `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/ReportMapperContractTest.java`：

```java
package com.twelvet.product.server.service.impl;

import com.twelvet.product.server.domain.Report;
import com.twelvet.product.server.mapper.ReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * report 表读写契约。
 * <p>
 * 钉死两件事：① 实体字段名与 XML resultMap 一致（拼错即运行期字段为 null，静默无报错）；
 * ② markHandled 是唯一把 pending 改成 done 的入口，状态守卫写在该 SQL 里。
 * </p>
 */
class ReportMapperContractTest {

	@Test
	@DisplayName("Report 实体覆盖 report 表全部列")
	void reportEntityHasAllColumns() throws NoSuchMethodException {
		Class<Report> c = Report.class;
		assertNotNull(c.getMethod("getReportId"));
		assertNotNull(c.getMethod("getReporterId"));
		assertNotNull(c.getMethod("getTargetType"));
		assertNotNull(c.getMethod("getTargetId"));
		assertNotNull(c.getMethod("getReason"));
		assertNotNull(c.getMethod("getEvidenceImages"));
		assertNotNull(c.getMethod("getStatus"));
		assertNotNull(c.getMethod("getAction"));
		assertNotNull(c.getMethod("getHandleNote"));
		assertNotNull(c.getMethod("getHandlerId"));
		assertNotNull(c.getMethod("getCreateTime"));
		assertNotNull(c.getMethod("getHandleTime"));
	}

	@Test
	@DisplayName("ReportMapper 方法签名")
	void mapperSignatures() throws NoSuchMethodException {
		assertEquals(int.class, ReportMapper.class.getMethod("insert", Report.class).getReturnType());
		assertEquals(Report.class,
				ReportMapper.class.getMethod("selectByReporterAndTarget", Long.class, String.class, Long.class)
						.getReturnType());
		assertEquals(int.class,
				ReportMapper.class.getMethod("revive", Long.class, String.class, String.class).getReturnType());
		assertEquals(List.class, ReportMapper.class.getMethod("selectList", String.class, Long.class).getReturnType());
		assertEquals(Report.class, ReportMapper.class.getMethod("selectById", Long.class, Long.class).getReturnType());
		assertEquals(int.class,
				ReportMapper.class.getMethod("markHandled", Long.class, String.class, String.class, Long.class)
						.getReturnType());
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportMapperContractTest`
Expected: 编译失败（`Report` / `ReportMapper` 不存在）。

- [x] **Step 3: 实现 `Report` 实体**

创建 `domain/Report.java`：普通 POJO，`@Schema` 注解风格与 `Feed` 一致，字段类型如下：

| 字段 | 类型 |
|---|---|
| reportId | Long |
| reporterId | Long |
| targetType | String |
| targetId | Long |
| reason | String |
| evidenceImages | String |
| status | String |
| action | String |
| handleNote | String |
| handlerId | Long |
| createTime | LocalDateTime |
| handleTime | LocalDateTime |

全部带 getter/setter。**不继承 `BaseEntity`**。

- [x] **Step 4: 实现 `ReportMapper` 接口**

```java
package com.twelvet.product.server.mapper;

import com.twelvet.product.server.domain.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 举报表 Mapper —— report 表的唯一读写点
 * <p>
 * 运营端列表与处置也走本 Mapper（trade 的 AdminMarketplaceMapper 已删除同名 SQL），
 * 避免两处 SQL 漂移。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface ReportMapper {

	/** 新增举报 */
	int insert(Report report);

	/**
	 * 按举报人 + 对象查已有举报（去重与复活用）
	 * @return 已有举报；无则 null
	 */
	Report selectByReporterAndTarget(@Param("reporterId") Long reporterId,
			@Param("targetType") String targetType, @Param("targetId") Long targetId);

	/**
	 * 复活已处理举报：重置为 pending、覆盖原因与证据、清空处理字段
	 * @return 影响行数
	 */
	int revive(@Param("reportId") Long reportId, @Param("reason") String reason,
			@Param("evidenceImages") String evidenceImages);

	/**
	 * 举报列表（运营端）
	 * @param status pending/done；null 表示全部
	 * @param tenantId 租户ID(为 null 不过滤)，经 reporter 的 sys_user 取租户
	 * @return 举报列表
	 */
	List<Report> selectList(@Param("status") String status, @Param("tenantId") Long tenantId);

	/** 按 ID 查举报（含租户过滤） */
	Report selectById(@Param("reportId") Long reportId, @Param("tenantId") Long tenantId);

	/**
	 * 置为已处理 —— 唯一的状态迁移入口
	 * <p>
	 * WHERE 带 status='pending'：并发双击时只有先到者 rows=1，后到者 rows=0 由 Service 抛异常。
	 * </p>
	 * @return 影响行数（0 表示该举报已被处理）
	 */
	int markHandled(@Param("reportId") Long reportId, @Param("action") String action,
			@Param("note") String note, @Param("handlerId") Long handlerId);
}
```

- [x] **Step 5: 实现 `ReportMapper.xml`**

创建 `resources/mapper/product/ReportMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.product.server.mapper.ReportMapper">

    <!--
      report 表无 tenant_id 列（docs/全局健壮性与架构审查报告.md P0-5），
      租户口径一律 JOIN sys_user(reporter_id) 取，与 AdminMarketplaceMapper 头注释约定一致。
    -->
    <resultMap id="ReportResult" type="com.twelvet.product.server.domain.Report">
        <id property="reportId" column="report_id"/>
        <result property="reporterId" column="reporter_id"/>
        <result property="targetType" column="target_type"/>
        <result property="targetId" column="target_id"/>
        <result property="reason" column="reason"/>
        <result property="evidenceImages" column="evidence_images"/>
        <result property="status" column="status"/>
        <result property="action" column="action"/>
        <result property="handleNote" column="handle_note"/>
        <result property="handlerId" column="handler_id"/>
        <result property="createTime" column="create_time"/>
        <result property="handleTime" column="handle_time"/>
    </resultMap>

    <sql id="reportCols">
        r.report_id, r.reporter_id, r.target_type, r.target_id, r.reason,
        r.evidence_images, r.status, r.action, r.handle_note, r.handler_id,
        r.create_time, r.handle_time
    </sql>

    <insert id="insert" useGeneratedKeys="true" keyProperty="reportId">
        INSERT INTO report (reporter_id, target_type, target_id, reason, evidence_images,
                            status, create_time)
        VALUES (#{reporterId}, #{targetType}, #{targetId}, #{reason}, #{evidenceImages},
                'pending', NOW())
    </insert>

    <select id="selectByReporterAndTarget" resultMap="ReportResult">
        SELECT <include refid="reportCols"/>
        FROM report r
        WHERE r.reporter_id = #{reporterId}
          AND r.target_type = #{targetType}
          AND r.target_id = #{targetId}
    </select>

    <update id="revive">
        UPDATE report
        SET reason = #{reason},
            evidence_images = #{evidenceImages},
            status = 'pending',
            action = NULL,
            handle_note = NULL,
            handler_id = NULL,
            handle_time = NULL
        WHERE report_id = #{reportId}
    </update>

    <select id="selectList" resultMap="ReportResult">
        SELECT <include refid="reportCols"/>
        FROM report r
        JOIN sys_user u ON u.user_id = r.reporter_id
        <where>
            <if test="status != null and status != ''">r.status = #{status}</if>
            <if test="tenantId != null">AND u.tenant_id = #{tenantId}</if>
        </where>
        ORDER BY r.create_time DESC
    </select>

    <select id="selectById" resultMap="ReportResult">
        SELECT <include refid="reportCols"/>
        FROM report r
        JOIN sys_user u ON u.user_id = r.reporter_id
        WHERE r.report_id = #{reportId}
        <if test="tenantId != null">AND u.tenant_id = #{tenantId}</if>
    </select>

    <update id="markHandled">
        UPDATE report
        SET status = 'done',
            action = #{action},
            handle_note = #{note},
            handler_id = #{handlerId},
            handle_time = NOW()
        WHERE report_id = #{reportId} AND status = 'pending'
    </update>

</mapper>
```

- [x] **Step 6: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportMapperContractTest`
Expected: 2 passed。

- [x] **Step 7: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 新增 Report 实体与 ReportMapper（report 表唯一读写点）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: 举报原因枚举与 reason 合成文本拆分

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/ReportReason.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/ReportReasonTest.java`

**Interfaces:**
- Produces: `ReportReason.isValid(String code) : boolean`
- Produces: `ReportReason.labelOf(String code) : String`（未知 code 返回 `"其他"`）
- Produces: `ReportReason.compose(String code, String detail) : String`（产出 `标签：详情`，detail 为空则只返回标签，总长截断 500）
- Produces: `ReportReason.splitType(String reason) : String`（按第一个全角冒号切，无冒号返回 `""`）
- Produces: `ReportReason.splitDetail(String reason) : String`（按第一个全角冒号切，无冒号返回整串）

- [x] **Step 1: 写失败测试**

创建 `ReportReasonTest.java`：

```java
package com.twelvet.product.server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 举报原因枚举与 reason 列合成文本的拆分。
 * <p>
 * report.reason 是单列（varchar(500)），存「标签：详情」合成文本，不新增 reason_type 列。
 * 拆分规则必须与 compose 严格互逆，否则运营列表会把整串当详情显示。
 * </p>
 */
class ReportReasonTest {

	@Test
	@DisplayName("六种原因全部合法，未知值不合法")
	void isValid() {
		for (String code : new String[] { "spam", "fraud", "fake", "prohibited", "abuse", "other" }) {
			assertTrue(ReportReason.isValid(code));
		}
		assertFalse(ReportReason.isValid("unknown"));
		assertFalse(ReportReason.isValid(null));
		assertFalse(ReportReason.isValid(""));
	}

	@Test
	@DisplayName("标签映射")
	void labelOf() {
		assertEquals("欺诈 / 骗钱", ReportReason.labelOf("fraud"));
		assertEquals("垃圾广告 / 引流", ReportReason.labelOf("spam"));
		assertEquals("其他", ReportReason.labelOf("unknown"));
	}

	@Test
	@DisplayName("compose：有详情拼「标签：详情」，无详情只有标签")
	void compose() {
		assertEquals("欺诈 / 骗钱：卖家收钱不发货", ReportReason.compose("fraud", "卖家收钱不发货"));
		assertEquals("欺诈 / 骗钱", ReportReason.compose("fraud", null));
		assertEquals("欺诈 / 骗钱", ReportReason.compose("fraud", "   "));
	}

	@Test
	@DisplayName("compose 截断到 500 字")
	void composeTruncates() {
		String detail = "x".repeat(600);
		assertEquals(500, ReportReason.compose("fraud", detail).length());
	}

	@Test
	@DisplayName("split 与 compose 互逆")
	void splitRoundTrip() {
		String reason = ReportReason.compose("fraud", "卖家收钱不发货");
		assertEquals("欺诈 / 骗钱", ReportReason.splitType(reason));
		assertEquals("卖家收钱不发货", ReportReason.splitDetail(reason));
	}

	@Test
	@DisplayName("无冒号的旧数据：整串作详情，类型为空")
	void splitLegacy() {
		assertEquals("", ReportReason.splitType("内容违规"));
		assertEquals("内容违规", ReportReason.splitDetail("内容违规"));
	}

	@Test
	@DisplayName("null / 空串不抛异常")
	void splitNull() {
		assertEquals("", ReportReason.splitType(null));
		assertEquals("", ReportReason.splitDetail(null));
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportReasonTest`
Expected: 编译失败（`ReportReason` 不存在）。

- [x] **Step 3: 实现 `ReportReason`**

要点（签名已定，实现按此写）：
- 六个 code→中文标签用 `LinkedHashMap` 保持展示顺序
- `compose` 用全角冒号 `：`（U+FF1A）连接，`detail` trim 后为空则只返回标签
- 截断用 `substring(0, Math.min(len, 500))`
- `splitType` / `splitDetail` 用 `reason.indexOf('：')`，-1 时分别返回 `""` / 原串
- 所有方法对 `null` / 空串安全

- [x] **Step 4: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportReasonTest`
Expected: 7 passed。

- [x] **Step 5: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 新增举报原因枚举与 reason 合成/拆分

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: `IImService.sendNotice` 站内通知

**Files:**
- Modify: `backend/twelvet-im/twelvet-im-server/src/main/java/com/twelvet/im/server/service/IImService.java`
- Modify: `backend/twelvet-im/twelvet-im-server/src/main/java/com/twelvet/im/server/service/impl/ImServiceImpl.java`
- Test: `backend/twelvet-im/twelvet-im-server/src/test/java/com/twelvet/im/server/service/impl/ImServiceSendNoticeTest.java`

**Interfaces:**
- Consumes: `ImNoticeMapper.insertNotice(ImNotice) : int`（已存在）
- Produces: `IImService.sendNotice(@Param userId Long, type String, title String, content String) : void` —— **不加 `@Transactional`**（见 Global Constraints 与 Review Focus #4）

- [x] **Step 1: 写失败测试**

创建 `ImServiceSendNoticeTest.java`（Mockito，构造器注入 `ImNoticeMapper` mock；`ImServiceImpl` 构造器还需 `ImConversationMapper`/`ImMessageMapper`/`ImUnreadMapper`/`ImUserMapper`/`TenantSupport`，全部 `mock(...)` 传入）：

```java
package com.twelvet.im.server.service.impl;

import com.twelvet.im.api.domain.ImNotice;
import com.twelvet.im.server.mapper.ImConversationMapper;
import com.twelvet.im.server.mapper.ImMessageMapper;
import com.twelvet.im.server.mapper.ImNoticeMapper;
import com.twelvet.im.server.mapper.ImUnreadMapper;
import com.twelvet.im.server.mapper.ImUserMapper;
import com.twelvet.framework.core.tenant.TenantSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知写入。
 * <p>
 * 关键约束：sendNotice 不得带 @Transactional —— 它在举报处置的事务内被调用，
 * 若自身带事务，抛异常会把共享事务标 rollback-only，外层提交时抛 UnexpectedRollbackException，
 * 反而让「通知失败不影响处理」变成「通知失败必回滚处理」（设计文档 §5.5 / Review Focus #4）。
 * </p>
 */
class ImServiceSendNoticeTest {

	private ImNoticeMapper noticeMapper;
	private ImServiceImpl service;

	@BeforeEach
	void setUp() {
		noticeMapper = mock(ImNoticeMapper.class);
		service = new ImServiceImpl(mock(ImConversationMapper.class), mock(ImMessageMapper.class), noticeMapper,
				mock(ImUnreadMapper.class), mock(ImUserMapper.class), mock(TenantSupport.class));
	}

	@Test
	@DisplayName("写入一条 platform 通知")
	void sendNotice_writesRow() {
		when(noticeMapper.insertNotice(any())).thenReturn(1);

		service.sendNotice(7L, "platform", "举报已处理", "你对「机械键盘」的举报已处理");

		ArgumentCaptor<ImNotice> captor = ArgumentCaptor.forClass(ImNotice.class);
		verify(noticeMapper).insertNotice(captor.capture());
		ImNotice n = captor.getValue();
		assertEquals(7L, n.getUserId());
		assertEquals("platform", n.getType());
		assertEquals("举报已处理", n.getTitle());
		assertEquals("你对「机械键盘」的举报已处理", n.getContent());
		assertEquals(0, n.getIsRead());
	}

	@Test
	@DisplayName("userId 为 null 时不写库")
	void sendNotice_nullUser_skipped() {
		service.sendNotice(null, "platform", "t", "c");
		verify(noticeMapper, never()).insertNotice(any());
	}

	@Test
	@DisplayName("写入失败向外抛，由调用方决定是否吞掉")
	void sendNotice_dbError_propagates() {
		when(noticeMapper.insertNotice(any())).thenThrow(new RuntimeException("db down"));
		assertThrows(RuntimeException.class, () -> service.sendNotice(7L, "platform", "t", "c"));
	}

	@Test
	@DisplayName("sendNotice 方法不带 @Transactional")
	void sendNotice_notTransactional() throws NoSuchMethodException {
		var m = ImServiceImpl.class.getMethod("sendNotice", Long.class, String.class, String.class, String.class);
		org.junit.jupiter.api.Assertions.assertNull(
				m.getAnnotation(org.springframework.transaction.annotation.Transactional.class),
				"sendNotice 必须无事务注解，否则通知异常会回滚举报处理");
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-im/twelvet-im-server -am test -Dtest=ImServiceSendNoticeTest`
Expected: 编译失败（`sendNotice` 不存在）。

- [x] **Step 3: 实现**

`IImService.java` 追加（放在 `selectNoticeList` 之后）：

```java
	/**
	 * 写一条站内通知（im_notice）
	 * <p>
	 * 不加事务注解：本方法在举报处置事务内被调用，若自带事务，抛异常会把共享事务标 rollback-only，
	 * 外层提交时抛 UnexpectedRollbackException —— 与「通知失败不影响处理」正好相反。
	 * 异常向外抛，由调用方 try/catch 决定是否吞掉。
	 * </p>
	 * @param userId 收件人ID（为 null 直接跳过）
	 * @param type 通知类型（order/platform/interact）
	 * @param title 标题
	 * @param content 内容
	 */
	void sendNotice(Long userId, String type, String title, String content);
```

`ImServiceImpl.java` 追加实现（**不加 `@Transactional`**）：

```java
	@Override
	public void sendNotice(Long userId, String type, String title, String content) {
		if (userId == null) {
			return;
		}
		ImNotice notice = new ImNotice();
		notice.setUserId(userId);
		notice.setType(type);
		notice.setTitle(title);
		notice.setContent(content);
		notice.setIsRead(0);
		noticeMapper.insertNotice(notice);
	}
```

- [x] **Step 4: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-im/twelvet-im-server -am test -Dtest=ImServiceSendNoticeTest`
Expected: 4 passed。

- [x] **Step 5: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-im/twelvet-im-server -am
git add backend/twelvet-im/twelvet-im-server
git commit -m "feat(im): 新增 sendNotice 站内通知写入

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: `ReportServiceImpl` —— 提交、列表、处置（核心）

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/IReportService.java`
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/ReportServiceImpl.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/service/impl/ReportServiceImplTest.java`

**Interfaces:**
- Consumes: `ReportMapper`（Task 3 全部方法）、`ProductMapper.selectByIds/selectById/updateStatus`（Task 2 + 既有）、`FeedMapper.selectByIds/selectById/updateStatus`（Task 2 + 既有）、`UserMapper.selectByIds/selectUserTenantId/updateStatus`（Task 2 + 既有）、`IImService.sendNotice`（Task 5）、`ReportReason`（Task 4）
- Produces: `IReportService.submit(String targetType, Long targetId, Long reporterId, String reasonType, String reasonDetail, List<String> evidenceImages) : void`
- Produces: `IReportService.adminList(String status) : TableDataInfo<Map<String,Object>>`（每行字段见设计文档 §5.2）

> **分页必须由 Service 返回 `TableDataInfo`，不能返回裸 `List`**：`PageUtils.startPage()` 开启分页后，
> `reportMapper.selectList(...)` 返回的是 `Page<Report>`（`ArrayList` 子类，携带 total）。
> 若 Service 把它映射成新的 `ArrayList<Map>` 再交给 `PageUtils.getDataTable`，**Page 包装丢失**，
> `new PageInfo<>(新list)` 读到的 total 会等于当前页条数（20），前端分页器只显示一页。
> 正确做法：Service 内先 `long total = new PageInfo<>(reports).getTotal()`，
> 再组装 `List<Map>`，最后 `return TableDataInfo.page(rows, total)`。
- Produces: `IReportService.adminHandle(Long reportId, String action, String note, Long handlerId) : void`
- Produces: bean 名 `productReportServiceImpl`（`@Service("productReportServiceImpl")`）

> **实现顺序**：先写 `submit`（Step 1-6），再写 `adminList`（Step 7-12），最后 `adminHandle`（Step 13-20）。每段自带红绿循环，避免一个巨型测试文件一次写完。

#### 6A. submit

- [x] **Step 1: 写失败测试（submit 部分）**

创建 `ReportServiceImplTest.java`，构造器注入全部 mock：

```java
package com.twelvet.product.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.im.server.service.IImService;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.domain.Report;
import com.twelvet.product.server.mapper.FeedMapper;
import com.twelvet.product.server.mapper.ProductMapper;
import com.twelvet.product.server.mapper.ReportMapper;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 举报提交校验。锁定的不变量：
 * 1) 不能举报自己的内容（自举报是灌水入口）
 * 2) 目标不存在/已删除/跨租户一律同一文案（不泄露存在性）
 * 3) 同人同对象：pending 拒绝；done 复活（清空处理字段）
 * 4) reason 落库为「标签：详情」合成文本
 */
class ReportServiceImplTest {

	private ReportMapper reportMapper;
	private ProductMapper productMapper;
	private FeedMapper feedMapper;
	private UserMapper userMapper;
	private IImService imService;
	private TenantSupport tenantSupport;
	private IReportService service;

	@BeforeEach
	void setUp() {
		reportMapper = mock(ReportMapper.class);
		productMapper = mock(ProductMapper.class);
		feedMapper = mock(FeedMapper.class);
		userMapper = mock(UserMapper.class);
		imService = mock(IImService.class);
		tenantSupport = mock(TenantSupport.class);
		service = new ReportServiceImpl(reportMapper, productMapper, feedMapper, userMapper, imService, tenantSupport);
		// PageUtils.startPage() 经 ServletUtils.getRequest().get() 读 current/pageSize，
		// 无请求上下文时抛 NoSuchElementException —— adminList 用例必须补一个空参请求
		HttpServletRequest request = mock(HttpServletRequest.class);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		RequestContextHolder.resetRequestAttributes();
	}

	private Product product(Long id, Long sellerId, String status) {
		Product p = new Product();
		p.setProductId(id);
		p.setSellerId(sellerId);
		p.setTitle("九成新机械键盘");
		p.setStatus(status);
		p.setPrice(new BigDecimal("120.00"));
		return p;
	}

	private Feed feed(Long id, Long userId, String status) {
		Feed f = new Feed();
		f.setFeedId(id);
		f.setUserId(userId);
		f.setContent("今天出个闲置");
		f.setStatus(status);
		return f;
	}

	@Test
	@DisplayName("正常提交商品举报：落库且 reason 为合成文本")
	void submit_product_ok() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(null);

		service.submit("product", 42L, 7L, "fraud", "卖家收钱不发货", List.of("/a.jpg"));

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportMapper).insert(captor.capture());
		Report r = captor.getValue();
		assertEquals(7L, r.getReporterId());
		assertEquals("product", r.getTargetType());
		assertEquals(42L, r.getTargetId());
		assertEquals("欺诈 / 骗钱：卖家收钱不发货", r.getReason());
		assertEquals("[\"/a.jpg\"]", r.getEvidenceImages());
	}

	@Test
	@DisplayName("举报自己的商品被拒")
	void submit_self_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 7L, "onsale"));

		assertThrows(TWTException.class, () -> service.submit("product", 42L, 7L, "fraud", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("商品不存在 → 统一文案")
	void submit_productMissing_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(null);

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "fraud", null, null));
		assertEquals("举报对象不存在或已删除", e.getMessage());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("软删的动态视为不存在（Review Focus #5 的提交侧）")
	void submit_feedSoftDeleted_rejected() {
		TenantContext.set(1L);
		when(feedMapper.selectById(10L, 1L)).thenReturn(feed(10L, 99L, "deleted"));

		assertThrows(TWTException.class, () -> service.submit("feed", 10L, 7L, "spam", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("跨租户举报用户被拒（不泄露存在性）")
	void submit_crossTenantUser_rejected() {
		TenantContext.set(1L);
		when(userMapper.selectByIds(List.of(55L)))
				.thenReturn(List.of(Map.of("user_id", 55L, "nick_name", "alice", "status", 0, "del_flag", "0")));
		when(userMapper.selectUserTenantId(55L)).thenReturn(2L);

		assertThrows(TWTException.class, () -> service.submit("user", 55L, 7L, "abuse", null, null));
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("已有 pending 举报 → 拒绝")
	void submit_duplicatePending_rejected() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		Report exist = new Report();
		exist.setStatus("pending");
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(exist);

		TWTException e = assertThrows(TWTException.class,
				() -> service.submit("product", 42L, 7L, "fraud", null, null));
		assertEquals("你已举报过该内容，正在处理中", e.getMessage());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("已有 done 举报 → 复活而非新增")
	void submit_duplicateDone_revived() {
		TenantContext.set(1L);
		when(productMapper.selectById(42L, 1L)).thenReturn(product(42L, 99L, "onsale"));
		Report exist = new Report();
		exist.setReportId(5L);
		exist.setStatus("done");
		when(reportMapper.selectByReporterAndTarget(7L, "product", 42L)).thenReturn(exist);
		when(reportMapper.revive(anyLong(), anyString(), any())).thenReturn(1);

		service.submit("product", 42L, 7L, "spam", "又来灌水", null);

		verify(reportMapper).revive(eq(5L), eq("垃圾广告 / 引流：又来灌水"), any());
		verify(reportMapper, never()).insert(any());
	}

	@Test
	@DisplayName("非法 targetType / reasonType 被拒")
	void submit_invalidArgs_rejected() {
		assertThrows(TWTException.class, () -> service.submit("im_msg", 1L, 7L, "spam", null, null));
		assertThrows(TWTException.class, () -> service.submit("product", 1L, 7L, "bogus", null, null));
	}

	@Test
	@DisplayName("租户开关关闭时跳过租户比对")
	void submit_tenantOff_skipsTenantCheck() {
		TenantContext.clear();
		when(userMapper.selectByIds(List.of(55L)))
				.thenReturn(List.of(Map.of("user_id", 55L, "nick_name", "alice", "status", 0, "del_flag", "0")));
		when(userMapper.selectUserTenantId(55L)).thenReturn(2L);
		when(reportMapper.selectByReporterAndTarget(7L, "user", 55L)).thenReturn(null);

		assertDoesNotThrow(() -> service.submit("user", 55L, 7L, "abuse", null, null));
		verify(reportMapper).insert(any());
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: 编译失败（`IReportService` / `ReportServiceImpl` 不存在）。

- [x] **Step 3: 实现 `IReportService`（先只声明 submit）**

```java
package com.twelvet.product.server.service;

import java.util.List;

/**
 * 举报 Service
 *
 * @author twelvet
 */
public interface IReportService {

	/**
	 * 提交举报
	 * @param targetType product/feed/user
	 * @param targetId 被举报对象ID
	 * @param reporterId 举报人ID
	 * @param reasonType 原因编码（spam/fraud/fake/prohibited/abuse/other）
	 * @param reasonDetail 补充说明（可空，≤200 字）
	 * @param evidenceImages 证据图 OSS 相对路径（可空，≤3 张）
	 */
	void submit(String targetType, Long targetId, Long reporterId, String reasonType, String reasonDetail,
			List<String> evidenceImages);
}
```

- [x] **Step 4: 实现 `ReportServiceImpl.submit` 与私有辅助**

类声明：`@Service("productReportServiceImpl")`（**必须显式 bean 名**，见 Global Constraints）。

`submit` 实现顺序：
1. 校验 `targetType` ∈ {product, feed, user}，否则抛「举报对象类型不合法」
2. 校验 `ReportReason.isValid(reasonType)`，否则抛「举报原因不合法」
3. 校验证据图数量 ≤3，否则抛「证据图最多 3 张」
4. 按 `targetType` 查目标，返回 `(ownerId, 摘要, exists)`：
   - product → `productMapper.selectById(targetId, currentTid())`；存在即 exists（硬删，查不到就没了）
   - feed → `feedMapper.selectById(targetId, currentTid())`；exists = 行存在 **且** `status != "deleted"`
   - user → `userMapper.selectByIds(List.of(targetId))`；exists = 行存在 **且** `del_flag = "0"`；再比对租户（`currentTid() != null` 时 `userMapper.selectUserTenantId(targetId)` 必须等于 `currentTid()`）
5. `!exists` → 抛「举报对象不存在或已删除」
6. `ownerId.equals(reporterId)` → 抛「不能举报自己发布的内容」
7. `reportMapper.selectByReporterAndTarget(reporterId, targetType, targetId)`：
   - `status="pending"` → 抛「你已举报过该内容，正在处理中」
   - 存在且 `status="done"` → `reportMapper.revive(exist.reportId, reason, evidenceJson)`，返回
8. 否则组装 `Report` 并 `insert`：`reason = ReportReason.compose(reasonType, reasonDetail)`，`evidenceImages = evidenceImages 非空 ? JacksonUtils.toJson(evidenceImages) : null`

私有辅助：`private Long currentTid() { return TenantContext.get(); }`（与 `FeedServiceImpl` 一致）。

- [x] **Step 5: 跑测试确认 GREEN（submit 部分）**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: submit 的 9 个用例全绿（`adminList` / `adminHandle` 的测试此时还没写）。

- [x] **Step 6: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 举报提交校验与落库

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

#### 6B. adminList

- [x] **Step 7: 写失败测试（adminList 部分，追加到 `ReportServiceImplTest`）**

```java
	@Test
	@DisplayName("列表：三类对象批量取摘要，不逐条查（N+1）")
	void adminList_batchesByTargetType() {
		TenantContext.set(1L);
		Report r1 = reportOf(1L, "product", 42L, "pending");
		Report r2 = reportOf(2L, "product", 43L, "pending");
		Report r3 = reportOf(3L, "feed", 10L, "pending");
		Report r4 = reportOf(4L, "user", 55L, "pending");
		when(reportMapper.selectList("pending", 1L)).thenReturn(List.of(r1, r2, r3, r4));
		when(productMapper.selectByIds(List.of(42L, 43L), 1L))
				.thenReturn(List.of(product(42L, 99L, "onsale"), product(43L, 99L, "offshelf")));
		when(feedMapper.selectByIds(List.of(10L), 1L)).thenReturn(List.of(feed(10L, 88L, "normal")));
		when(userMapper.selectByIds(List.of(55L)))
				.thenReturn(List.of(Map.of("user_id", 55L, "nick_name", "alice", "status", 0, "del_flag", "0")));

		List<Map<String, Object>> rows = service.adminList("pending").getRecords();

		assertEquals(4, rows.size());
		// product 摘要
		assertEquals("九成新机械键盘", rows.get(0).get("targetTitle"));
		assertEquals(true, rows.get(0).get("targetExists"));
		assertEquals(99L, rows.get(0).get("ownerId"));
		// 已下架商品仍显示（不过滤 status）
		assertEquals(true, rows.get(1).get("targetExists"));
		// feed 摘要
		assertEquals("今天出个闲置", rows.get(2).get("targetTitle"));
		assertEquals(88L, rows.get(2).get("ownerId"));
		// user 摘要
		assertEquals("alice", rows.get(3).get("targetTitle"));
		assertEquals(55L, rows.get(3).get("ownerId"));
		// 批量查询各调一次
		verify(productMapper, times(1)).selectByIds(any(), any());
		verify(feedMapper, times(1)).selectByIds(any(), any());
		verify(userMapper, times(1)).selectByIds(any());
	}

	@Test
	@DisplayName("列表：软删动态 targetExists=false 且标题保留（Review Focus #5）")
	void adminList_softDeletedFeed_markedDeleted() {
		TenantContext.set(1L);
		when(reportMapper.selectList("pending", 1L))
				.thenReturn(List.of(reportOf(3L, "feed", 10L, "pending")));
		when(feedMapper.selectByIds(List.of(10L), 1L)).thenReturn(List.of(feed(10L, 88L, "deleted")));

		List<Map<String, Object>> rows = service.adminList("pending").getRecords();

		assertEquals(false, rows.get(0).get("targetExists"));
		assertEquals("今天出个闲置", rows.get(0).get("targetTitle"));
	}

	@Test
	@DisplayName("列表：目标已被硬删，摘要为空但行仍返回")
	void adminList_missingProduct_keepsRow() {
		TenantContext.set(1L);
		when(reportMapper.selectList("pending", 1L))
				.thenReturn(List.of(reportOf(1L, "product", 42L, "pending")));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of());

		List<Map<String, Object>> rows = service.adminList("pending").getRecords();

		assertEquals(1, rows.size());
		assertEquals(false, rows.get(0).get("targetExists"));
		assertNull(rows.get(0).get("ownerId"));
	}

	@Test
	@DisplayName("列表：分页 total 取自原始 Page，不因重映射丢失")
	void adminList_keepsTotal() {
		TenantContext.set(1L);
		// PageHelper 未开启时不返回 Page 包装，total 退化为列表长度（1），本用例锁定「不为 0 且等于条数」
		when(reportMapper.selectList("pending", 1L))
				.thenReturn(List.of(reportOf(1L, "product", 42L, "pending")));
		when(productMapper.selectByIds(List.of(42L), 1L))
				.thenReturn(List.of(product(42L, 99L, "onsale")));

		var result = service.adminList("pending");

		assertEquals(1, result.getRecords().size());
		assertEquals(1L, result.getTotal());
	}

	@Test
	@DisplayName("列表：status=all 透传 null")
	void adminList_allPassesNull() {
		TenantContext.set(1L);
		when(reportMapper.selectList(null, 1L)).thenReturn(List.of());

		List<Map<String, Object>> rows = service.adminList("all").getRecords();

		assertEquals(0, rows.size());
		verify(reportMapper).selectList(isNull(), eq(1L));
	}
```

测试辅助方法（加到测试类内）：

```java
	private Report reportOf(Long id, String type, Long targetId, String status) {
		Report r = new Report();
		r.setReportId(id);
		r.setReporterId(7L);
		r.setTargetType(type);
		r.setTargetId(targetId);
		r.setStatus(status);
		r.setReason("欺诈 / 骗钱：卖家收钱不发货");
		return r;
	}
```

- [x] **Step 8: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: 编译失败（`adminList` 不存在）。

- [x] **Step 9: 实现 `IReportService.adminList` 声明**

```java
	/**
	 * 运营举报列表（含被举报对象摘要）
	 * @param status pending/done/all
	 * @return 分页结果；每行含 reportId/targetType/targetId/targetTitle/targetCover/targetExtra/
	 *         targetExists/ownerId/ownerNickName/reporterNickName/handlerNickName/
	 *         reasonType/reasonDetail/evidenceImages/status/action/handleNote/handleTime
	 */
	TableDataInfo<Map<String, Object>> adminList(String status);
```

> **分页顺序（必须照做）**：`PageUtils.startPage()` 必须在 `reportMapper.selectList` **之前**调用
> （PageHelper 靠 ThreadLocal 拦截**下一条** SQL）。拿到 `List<Report>` 后**立刻**读 total，
> 再组装新 list——顺序颠倒 total 就丢。

- [x] **Step 10: 实现 `ReportServiceImpl.adminList`**

实现顺序：
1. `PageUtils.startPage()`
2. `status` 参数：`"all"` → null，其余原样传给 `reportMapper.selectList`
3. `List<Report> reports = reportMapper.selectList(statusOrNull, currentTid())`
4. **`long total = new PageInfo<>(reports).getTotal();`**（此时 `reports` 仍是 `Page<Report>`，total 有效）
5. 按 `targetType` 分组收集 ID（`productIds` / `feedIds` / `userIds`），另收集 `reporterIds` / `handlerIds`
6. 批量查询（空列表**不要**调 Mapper，MyBatis `IN ()` 会语法错误）：
   - `productIds` 非空 → `productMapper.selectByIds(productIds, currentTid())`
   - `feedIds` 非空 → `feedMapper.selectByIds(feedIds, currentTid())`
   - `userIds ∪ reporterIds ∪ handlerIds` 合并去重 → **一次** `userMapper.selectByIds(allUserIds)`（避免 3 次查询）
7. 组装 `List<Map<String,Object>>`：
   - 基础：`reportId`/`targetType`/`targetId`/`status`/`action`/`handleNote`/`handleTime`/`evidenceImages`（JSON 字符串）
   - `reasonType = ReportReason.splitType(reason)`，`reasonDetail = ReportReason.splitDetail(reason)`
   - `reporterNickName` / `handlerNickName` 从第 6 步的用户 Map 取
   - `targetTitle`/`targetCover`/`targetExtra`/`ownerId`/`ownerNickName`/`targetExists` 按 §3.2 语义填充
8. `return TableDataInfo.page(rows, total);`

> 需要 import `com.github.pagehelper.PageInfo`。`PageUtils.getDataTable` 在此不可用（它会重新 `new PageInfo` 读 total，对新 list 无效）。

`TableDataInfo.page(List, long)` 是既有静态方法（`PageUtils.getDataTable` 内部就是这么调的）。

- [x] **Step 11: 跑测试确认 GREEN（含 submit）**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: submit 9 + adminList 5 = 14 passed。

- [x] **Step 12: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 运营举报列表与目标摘要批量填充

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

#### 6C. adminHandle

- [x] **Step 13: 写失败测试（adminHandle 部分）**

```java
	@Test
	@DisplayName("处理已处理的举报 → 抛异常且不执行任何处置（Review Focus #2）")
	void handle_alreadyHandled_rejected() {
		TenantContext.set(1L);
		Report r = reportOf(1L, "product", 42L, "done");
		when(reportMapper.selectById(1L, 1L)).thenReturn(r);
		when(reportMapper.markHandled(anyLong(), any(), any(), anyLong())).thenReturn(0);

		TWTException e = assertThrows(TWTException.class,
				() -> service.adminHandle(1L, "remove_product", "备注", 100L));
		assertEquals("该举报已被处理", e.getMessage());
		verify(productMapper, never()).updateStatus(anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("动作与对象类型不匹配 → 抛异常")
	void handle_actionTypeMismatch_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "feed", 10L, "pending"));

		assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", null, 100L));
	}

	@Test
	@DisplayName("下架 locked 商品被拒（Review Focus #1：保护已付款订单）")
	void handle_removeProductLocked_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L))
				.thenReturn(List.of(product(42L, 99L, "locked")));

		TWTException e = assertThrows(TWTException.class,
				() -> service.adminHandle(1L, "remove_product", null, 100L));
		assertEquals("该商品存在进行中订单或已售，请走纠纷处理流程", e.getMessage());
		verify(productMapper, never()).updateStatus(anyLong(), any(), any(), any());
		verify(reportMapper, never()).markHandled(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("下架 sold 商品同样被拒")
	void handle_removeProductSold_rejected() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of(product(42L, 99L, "sold")));

		assertThrows(TWTException.class, () -> service.adminHandle(1L, "remove_product", null, 100L));
		verify(productMapper, never()).updateStatus(anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("下架在售商品：改 offshelf + 双方通知")
	void handle_removeProductOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of(product(42L, 99L, "onsale")));
		when(reportMapper.markHandled(1L, "remove_product", "图片不符", 100L)).thenReturn(1);

		service.adminHandle(1L, "remove_product", "图片不符", 100L);

		verify(productMapper).updateStatus(eq(42L), eq("offshelf"), isNull(), eq(1L));
		verify(imService).sendNotice(eq(99L), eq("platform"), anyString(), contains("下架"));
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("删除动态：status=deleted + audit_status=rejected")
	void handle_removeFeedOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "feed", 10L, "pending"));
		when(feedMapper.selectByIds(List.of(10L), 1L)).thenReturn(List.of(feed(10L, 88L, "normal")));
		when(reportMapper.markHandled(1L, "remove_feed", "违规", 100L)).thenReturn(1);

		service.adminHandle(1L, "remove_feed", "违规", 100L);

		verify(feedMapper).updateStatus(eq(10L), eq("deleted"), eq("rejected"), eq(1L));
		verify(imService).sendNotice(eq(88L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("封号：改 status=1 且踢下线（Review Focus #3）")
	void handle_banUserKicksOut() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "user", 55L, "pending"));
		when(userMapper.selectByIds(List.of(55L)))
				.thenReturn(List.of(Map.of("user_id", 55L, "nick_name", "alice", "status", 0, "del_flag", "0")));
		when(reportMapper.markHandled(1L, "ban_user", "骚扰", 100L)).thenReturn(1);

		try (var stp = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
			service.adminHandle(1L, "ban_user", "骚扰", 100L);
			stp.verify(() -> cn.dev33.satoken.stp.StpUtil.kickout(55L));
		}

		verify(userMapper).updateStatus(eq(55L), eq(1), eq(1L));
		verify(imService).sendNotice(eq(55L), eq("platform"), anyString(), contains("停用"));
	}

	@Test
	@DisplayName("驳回：不改任何业务数据，只通知举报人")
	void handle_dismissOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(reportMapper.markHandled(1L, "dismiss", "经核实未违规", 100L)).thenReturn(1);

		service.adminHandle(1L, "dismiss", "经核实未违规", 100L);

		verify(productMapper, never()).updateStatus(anyLong(), any(), any(), any());
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), contains("未违规"));
	}

	@Test
	@DisplayName("警告：不改业务数据，通知双方")
	void handle_warnOk() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of(product(42L, 99L, "onsale")));
		when(reportMapper.markHandled(1L, "warn", "注意言行", 100L)).thenReturn(1);

		service.adminHandle(1L, "warn", "注意言行", 100L);

		verify(imService).sendNotice(eq(99L), eq("platform"), anyString(), anyString());
		verify(imService).sendNotice(eq(7L), eq("platform"), anyString(), anyString());
	}

	@Test
	@DisplayName("通知抛异常不影响处理结果（Review Focus #4）")
	void handle_noticeFailureDoesNotRollback() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of(product(42L, 99L, "onsale")));
		when(reportMapper.markHandled(1L, "remove_product", "备注", 100L)).thenReturn(1);
		doThrow(new RuntimeException("db down")).when(imService).sendNotice(any(), any(), any(), any());

		assertDoesNotThrow(() -> service.adminHandle(1L, "remove_product", "备注", 100L));
		verify(reportMapper).markHandled(1L, "remove_product", "备注", 100L);
	}

	@Test
	@DisplayName("处置目标已被删除：照常置 done，备注追加说明")
	void handle_targetGone_stillHandled() {
		TenantContext.set(1L);
		when(reportMapper.selectById(1L, 1L)).thenReturn(reportOf(1L, "product", 42L, "pending"));
		when(productMapper.selectByIds(List.of(42L), 1L)).thenReturn(List.of());
		when(reportMapper.markHandled(eq(1L), eq("remove_product"), contains("对象已不存在"), eq(100L)))
				.thenReturn(1);

		assertDoesNotThrow(() -> service.adminHandle(1L, "remove_product", "备注", 100L));
		verify(productMapper, never()).updateStatus(anyLong(), any(), any(), any());
	}
```

- [x] **Step 14: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: 编译失败（`adminHandle` 不存在）。

- [x] **Step 15: 实现 `IReportService.adminHandle` 声明**

```java
	/**
	 * 处理举报（处置真实生效 + 站内通知）
	 * @param reportId 举报ID
	 * @param action dismiss/warn/remove_product/remove_feed/ban_user
	 * @param note 处理备注（dismiss 时必填）
	 * @param handlerId 处理人ID
	 */
	void adminHandle(Long reportId, String action, String note, Long handlerId);
```

- [x] **Step 16: 实现 `ReportServiceImpl.adminHandle`**

方法标 `@Transactional(rollbackFor = Exception.class)`。顺序：

1. `Report r = reportMapper.selectById(reportId, currentTid())`；null → 抛「举报不存在」
2. 动作白名单校验：`action` ∈ 五值，否则抛「处理动作不合法」
3. 类型匹配校验：`remove_product` 仅 product、`remove_feed` 仅 feed；`dismiss`/`warn`/`ban_user` 任意
4. **先查目标**（`selectByIds(List.of(targetId))`），得到 `ownerId` / `exists` / `摘要`
5. `remove_product`：目标不存在 → 不处置、`note` 追加 `[对象已不存在，处置未执行]`；
   存在但 `status` ∉ {`onsale`,`auditing`} → 抛「该商品存在进行中订单或已售，请走纠纷处理流程」；
   否则 `productMapper.updateStatus(targetId, "offshelf", null, currentTid())`
6. `remove_feed`：目标不存在 → 同上追加说明；否则 `feedMapper.updateStatus(targetId, "deleted", "rejected", currentTid())`
7. `ban_user`：`ownerId` 非空 → `userMapper.updateStatus(ownerId, 1, currentTid())`；否则追加说明
8. `dismiss` / `warn`：不改业务数据
9. `int rows = reportMapper.markHandled(reportId, action, note, handlerId)`；`rows == 0` → 抛「该举报已被处理」
10. 事务提交后通知（见下）

**通知与踢会话的实现约束**（Review Focus #3/#4）：

```java
	private void notifyAfterCommit(Runnable task) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					task.run();
				}
			});
		}
		else {
			task.run();
		}
	}
```

- 通知：`notifyAfterCommit(() -> safeNotify(...))`，`safeNotify` 内部对每个收件人 `try/catch` 只 `log.warn`
- 踢会话：`ban_user` 时 `notifyAfterCommit(() -> StpUtil.kickout(ownerId))`，同样 try/catch
- 通知文案按设计文档 §6 的五种场景构造

- [x] **Step 17: 跑测试确认 GREEN（全部 23 个用例）**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportServiceImplTest`
Expected: 24 passed（submit 9 + adminList 5 + adminHandle 10）。

- [x] **Step 18: 跑模块全量测试确认无回归**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test`
Expected: 全绿。

- [x] **Step 19: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 举报处置真实生效与站内通知

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: C 端举报接口 + 动态举报改接

**Files:**
- Create: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/ReportController.java`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/controller/FeedController.java:119-125`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/impl/FeedServiceImpl.java:231-236`
- Modify: `backend/twelvet-product/twelvet-product-server/src/main/java/com/twelvet/product/server/service/IFeedService.java`
- Test: `backend/twelvet-product/twelvet-product-server/src/test/java/com/twelvet/product/server/controller/ReportControllerAuthTest.java`

**Interfaces:**
- Consumes: `IReportService.submit(...)`（Task 6）
- Produces: `POST /report/submit`，body `{targetType, targetId, reasonType, reasonDetail?, evidenceImages?}` → `JsonResult<String>`
- Produces: `FeedServiceImpl.report(Long feedId, Long userId, String reasonType, String reasonDetail)` 改为委托 `IReportService.submit("feed", feedId, userId, reasonType, reasonDetail, null)`

- [x] **Step 1: 写失败测试**

创建 `ReportControllerAuthTest.java`（仿 `TradeReviewControllerAnonymousAccessTest` 的反射风格）：

```java
package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 举报接口的可匿名访问性契约。
 * <p>
 * /report/submit 是写操作，绝不能 @SaIgnore —— 游客写举报会绕过租户与频率约束。
 * 与 TradeReviewControllerAnonymousAccessTest 同一思路：钉注解，与 profile 无关。
 * </p>
 */
class ReportControllerAuthTest {

	@Test
	@DisplayName("提交举报不得匿名可访问")
	void submitIsNotAnonymous() throws NoSuchMethodException {
		Method m = ReportController.class.getMethod("submit", java.util.Map.class);
		assertNull(m.getAnnotation(SaIgnore.class), "submit 是写操作，绝不能匿名可访问");
		assertNull(ReportController.class.getAnnotation(SaIgnore.class), "类级也不能匿名");
	}

	@Test
	@DisplayName("动态举报仍走 FeedController，且不是匿名接口")
	void feedReportNotAnonymous() throws NoSuchMethodException {
		Method m = FeedController.class.getMethod("report", Long.class, String.class, String.class);
		assertNull(m.getAnnotation(SaIgnore.class), "动态举报是写操作，不能匿名");
	}
}
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportControllerAuthTest`
Expected: 编译失败（`ReportController` 不存在）。

- [x] **Step 3: 实现 `ReportController`**

```java
package com.twelvet.product.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.product.server.service.IReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 举报 C 端接口
 *
 * @author twelvet
 */
@Tag(description = "举报 C 端接口", name = "ReportController")
@RestController
@RequestMapping("/report")
public class ReportController extends TWTController {

	private final IReportService reportService;

	public ReportController(IReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * 提交举报（商品 / 动态 / 用户）
	 */
	@Operation(summary = "提交举报")
	@PostMapping("/submit")
	public JsonResult<String> submit(@RequestBody Map<String, Object> body) {
		Long reporterId = StpUtil.getLoginIdAsLong();
		String targetType = String.valueOf(body.get("targetType"));
		Long targetId = Long.valueOf(String.valueOf(body.get("targetId")));
		String reasonType = String.valueOf(body.get("reasonType"));
		String reasonDetail = body.get("reasonDetail") == null ? null : String.valueOf(body.get("reasonDetail"));
		@SuppressWarnings("unchecked")
		List<String> evidenceImages = (List<String>) body.get("evidenceImages");
		reportService.submit(targetType, targetId, reporterId, reasonType, reasonDetail, evidenceImages);
		return JsonResult.success("举报已提交，我们会尽快处理");
	}
}
```

- [x] **Step 4: 改 `FeedServiceImpl.report` 委托**

`IFeedService.java` 注释改为「举报动态（委托 IReportService）」；`FeedServiceImpl` 注入 `IReportService`（构造器加参，注意 bean 名冲突——用类型注入即可，Spring 按类型找到唯一实现），方法体替换为：

```java
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String report(Long feedId, Long userId, String reasonType, String reasonDetail) {
		reportService.submit("feed", feedId, userId, reasonType, reasonDetail, null);
		return "reported";
	}
```

移除原 `log.info` 与 `return "reported"`。

- [x] **Step 5: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test -Dtest=ReportControllerAuthTest`
Expected: 2 passed。

- [x] **Step 6: 跑模块全量测试**

Run: `cd backend && mvn -pl twelvet-product/twelvet-product-server -am test`
Expected: 全绿（`FeedServiceImpl` 构造器变更需同步更新任何既有测试——若 `FeedServiceImpl` 此前无测试则无需改）。

- [x] **Step 7: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-product/twelvet-product-server -am
git add backend/twelvet-product/twelvet-product-server
git commit -m "feat(report): 新增 C 端举报接口，动态举报改接 ReportService

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: 运营端接口改委托（trade 侧）

**Files:**
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/controller/AdminMarketplaceController.java:108-129`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/IAdminMarketplaceService.java:55-70`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/service/impl/AdminMarketplaceServiceImpl.java:268-292`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/java/com/twelvet/trade/server/mapper/AdminMarketplaceMapper.java`
- Modify: `backend/twelvet-trade/twelvet-trade-server/src/main/resources/mapper/trade/AdminMarketplaceMapper.xml:185-202`
- Test: `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/AdminReportDelegationTest.java`

**Interfaces:**
- Consumes: `IReportService.adminList(String)` / `adminHandle(Long, String, String, Long)`（Task 6）
- Produces: `AdminMarketplaceServiceImpl(IAdminMarketplaceMapper, IReportService)` 两参构造器
- Produces: `GET /admin/marketplace/report/page?status=pending|done|all` → `JsonResult<TableDataInfo<Map<String,Object>>>`
- Produces: `POST /admin/marketplace/report/handle` body `{reportId, action, note}` → `JsonResult<String>`

> ⚠️ **既有测试影响（已核实）**：全仓 `grep -rn "new AdminMarketplaceServiceImpl" backend/` **0 处**，
> `new FeedServiceImpl` 同样 0 处——两处构造器加参不会破坏既有测试。

- [x] **Step 1: 写失败测试**

创建 `backend/twelvet-trade/twelvet-trade-server/src/test/java/com/twelvet/trade/server/service/impl/AdminReportDelegationTest.java`：

```java
package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.server.service.IReportService;
import com.twelvet.trade.server.mapper.AdminMarketplaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营举报接口委托契约。
 * <p>
 * /admin/marketplace/report/* 的 URL 与权限点保持不变（前端与菜单表 2103 已引用），
 * 内部改为委托 product 侧 IReportService —— 保证 report 表只有一处读写 SQL。
 * </p>
 * <p>
 * 不测 Controller：handleReport 取 {@code StpUtil.getLoginIdAsLong()} 需要 Sa-Token 请求上下文，
 * standalone MockMvc 里没有。委托发生在 Service 层，测 Service 即可覆盖真实逻辑。
 * </p>
 */
class AdminReportDelegationTest {

	private IReportService reportService;
	private AdminMarketplaceServiceImpl service;

	@BeforeEach
	void setUp() {
		reportService = mock(IReportService.class);
		service = new AdminMarketplaceServiceImpl(mock(AdminMarketplaceMapper.class), reportService);
	}

	@Test
	@DisplayName("列表委托 adminList，TableDataInfo 原样返回")
	void selectReportList_delegates() {
		TableDataInfo<Map<String, Object>> stub = TableDataInfo.page(List.of(Map.of("reportId", 1L)), 1L);
		when(reportService.adminList("all")).thenReturn(stub);

		TableDataInfo<Map<String, Object>> result = service.selectReportList("all");

		verify(reportService).adminList("all");
		assertSame(stub, result);
		assertEquals(1L, result.getTotal());
	}

	@Test
	@DisplayName("处理委托 adminHandle，handlerId 原样透传")
	void handleReport_delegates() {
		assertEquals("已处理", service.handleReport(1L, "remove_product", "备注", 100L));
		verify(reportService).adminHandle(1L, "remove_product", "备注", 100L);
	}

	@Test
	@DisplayName("AdminMarketplaceMapper 不再声明举报 SQL（单一读写点）")
	void mapperHasNoReportSql() {
		for (Method m : AdminMarketplaceMapper.class.getMethods()) {
			if (m.getName().equals("selectReportList") || m.getName().equals("handleReport")) {
				throw new AssertionError("举报 SQL 必须只在 product 的 ReportMapper 里：" + m.getName());
			}
		}
	}
}
```

> `handleReport` 签名改为四参（新增 `handlerId`）。`selectReportList` 保持单参。
> `AdminMarketplaceServiceImpl` 构造器加第二个参数 `IReportService`（原有唯一依赖 `AdminMarketplaceMapper` 保留）。

- [x] **Step 2: 跑测试确认 RED**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=AdminReportDelegationTest`
Expected: 编译失败（`AdminMarketplaceServiceImpl` 无两参构造器、`handleReport` 四参不存在）。

- [x] **Step 3: 实现**

`IAdminMarketplaceService.java` 改签名：

```java
	/**
	 * 举报列表(分页) —— 委托 product 的 IReportService，report 表单一读写点
	 * @param status pending/done/all
	 * @return 举报分页列表
	 */
	TableDataInfo<Map<String, Object>> selectReportList(String status);

	/**
	 * 处理举报 —— 委托 product 的 IReportService
	 * @param reportId 举报ID
	 * @param action 处理动作
	 * @param note 处理备注
	 * @param handlerId 处理人ID
	 * @return 结果
	 */
	String handleReport(Long reportId, String action, String note, Long handlerId);
```

`AdminMarketplaceServiceImpl` 注入 `IReportService`（构造器加第二个参数），两个方法改为：

```java
	@Override
	public TableDataInfo<Map<String, Object>> selectReportList(String status) {
		// 分页在 IReportService.adminList 内完成（PageHelper 需紧贴 selectList，跨 Service 会失效）
		return reportService.adminList(status);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String handleReport(Long reportId, String action, String note, Long handlerId) {
		reportService.adminHandle(reportId, action, note, handlerId);
		return "已处理";
	}
```

`AdminMarketplaceController` 的 `handleReport` 改为：

```java
	@Operation(summary = "处理举报")
	@Log(service = "举报管理", businessType = BusinessType.UPDATE)
	@SaCheckPermission("marketplace:admin:report")
	@PostMapping("/report/handle")
	public JsonResult<String> handleReport(@RequestBody Map<String, Object> body) {
		Long reportId = Long.valueOf(String.valueOf(body.get("reportId")));
		String action = String.valueOf(body.getOrDefault("action", "dismiss"));
		String note = String.valueOf(body.getOrDefault("note", ""));
		Long handlerId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(adminService.handleReport(reportId, action, note, handlerId));
	}
```

`reportPage` 的 `@RequestParam` 默认值从 `"pending"` 保留不变。

`AdminMarketplaceMapper.java` 删除 `selectReportList` / `handleReport` 两个方法声明；
`AdminMarketplaceMapper.xml` 删除对应两个 `<select>`/`<update>` 块（第 185-202 行）。

- [x] **Step 4: 跑测试确认 GREEN**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test -Dtest=AdminReportDelegationTest`
Expected: 3 passed。

- [x] **Step 5: 跑模块全量测试**

Run: `cd backend && mvn -pl twelvet-trade/twelvet-trade-server -am test`
Expected: 全绿。

- [x] **Step 6: Commit**

```bash
cd backend && mvn spring-javaformat:apply -pl twelvet-trade/twelvet-trade-server -am
git add backend/twelvet-trade/twelvet-trade-server
git commit -m "feat(report): 运营举报接口改委托 product 侧 ReportService

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: 前端 API + 举报弹窗

**Files:**
- Modify: `ui/src/services/marketplace/api.ts`
- Create: `ui/src/utils/report.ts`
- Create: `ui/src/utils/report.test.ts`
- Create: `ui/src/components/marketplace/ReportDialog.tsx`
- Create: `ui/src/components/marketplace/ReportDialog.test.tsx`

**Interfaces:**
- Produces: `reportSubmit(data: {targetType, targetId, reasonType, reasonDetail?, evidenceImages?})`
- Produces: `REPORT_REASONS: {value, label}[]`
- Produces: `parseReportReason(reason: string): {reasonType, reasonDetail}`
- Produces: `reportActionOptions(targetType: string): {value, label}[]`
- Produces: `<ReportDialog open targetType targetId targetLabel onClose />`

- [x] **Step 1: 写失败测试（纯函数）**

创建 `ui/src/utils/report.test.ts`：

```ts
import { REPORT_REASONS, parseReportReason, reportActionOptions } from './report';

describe('parseReportReason', () => {
    it('按第一个全角冒号拆出类型与详情', () => {
        expect(parseReportReason('欺诈 / 骗钱：卖家收钱不发货')).toEqual({
            reasonType: '欺诈 / 骗钱',
            reasonDetail: '卖家收钱不发货',
        });
    });

    it('无冒号时整串作详情', () => {
        expect(parseReportReason('内容违规')).toEqual({ reasonType: '', reasonDetail: '内容违规' });
    });

    it('空值安全', () => {
        expect(parseReportReason('')).toEqual({ reasonType: '', reasonDetail: '' });
        expect(parseReportReason(undefined as any)).toEqual({ reasonType: '', reasonDetail: '' });
    });
});

describe('reportActionOptions', () => {
    it('商品可选下架', () => {
        const values = reportActionOptions('product').map((o) => o.value);
        expect(values).toContain('remove_product');
        expect(values).not.toContain('remove_feed');
    });

    it('动态可选删除，不含下架商品', () => {
        const values = reportActionOptions('feed').map((o) => o.value);
        expect(values).toContain('remove_feed');
        expect(values).not.toContain('remove_product');
    });

    it('驳回/警告/封号对三类都可用', () => {
        for (const t of ['product', 'feed', 'user']) {
            const values = reportActionOptions(t).map((o) => o.value);
            expect(values).toEqual(expect.arrayContaining(['dismiss', 'warn', 'ban_user']));
        }
    });
});

describe('REPORT_REASONS', () => {
    it('六项，值与后端白名单一致', () => {
        expect(REPORT_REASONS.map((r) => r.value)).toEqual([
            'spam', 'fraud', 'fake', 'prohibited', 'abuse', 'other',
        ]);
    });
});
```

- [x] **Step 2: 跑测试确认 RED**

Run: `cd ui && npm test -- --testPathPattern=report.test`
Expected: 失败（`./report` 模块不存在）。

- [x] **Step 3: 实现 `ui/src/utils/report.ts`**

```ts
/** 举报原因（与后端 ReportReason 白名单一一对应，改这里必须同步后端） */
export const REPORT_REASONS: { value: string; label: string }[] = [
    { value: 'spam', label: '垃圾广告 / 引流' },
    { value: 'fraud', label: '欺诈 / 骗钱' },
    { value: 'fake', label: '假冒伪劣 / 描述不符' },
    { value: 'prohibited', label: '违禁品 / 违规内容' },
    { value: 'abuse', label: '辱骂 / 骚扰' },
    { value: 'other', label: '其他' },
];

/** 全角冒号：后端 ReportReason.compose 用同一个字符连接「标签：详情」 */
const FULLWIDTH_COLON = '：';

/** 拆分后端合成文本 reason；无冒号时整串作详情（兼容旧数据） */
export function parseReportReason(reason?: string | null): {
    reasonType: string;
    reasonDetail: string;
} {
    if (!reason) return { reasonType: '', reasonDetail: '' };
    const idx = reason.indexOf(FULLWIDTH_COLON);
    if (idx < 0) return { reasonType: '', reasonDetail: reason };
    return {
        reasonType: reason.slice(0, idx),
        reasonDetail: reason.slice(idx + 1),
    };
}

/** 处理动作（与后端 adminHandle 白名单一致；下架/删除按对象类型过滤） */
export function reportActionOptions(
    targetType: string,
): { value: string; label: string }[] {
    const base = [
        { value: 'dismiss', label: '驳回（未违规）' },
        { value: 'warn', label: '警告' },
        { value: 'ban_user', label: '停用账号' },
    ];
    if (targetType === 'product') {
        return [{ value: 'remove_product', label: '下架商品' }, ...base];
    }
    if (targetType === 'feed') {
        return [{ value: 'remove_feed', label: '删除动态' }, ...base];
    }
    return base;
}
```

- [x] **Step 4: 跑测试确认 GREEN**

Run: `cd ui && npm test -- --testPathPattern=report.test`
Expected: 7 passed。

- [x] **Step 5: 写失败测试（弹窗组件）**

创建 `ui/src/components/marketplace/ReportDialog.test.tsx`（仿 `ui/src/pages/AI/ShoppingGuide/index.test.tsx` 的 `@testing-library/react` 用法）：

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ReportDialog from './ReportDialog';

const submit = jest.fn();

jest.mock('@/services/marketplace/api', () => ({
    reportSubmit: (...args: any[]) => submit(...args),
}));

jest.mock('@/utils/loginGuard', () => ({
    requireLogin: () => true,
}));

jest.mock('@/components/marketplace/UploadImages', () => () => <div data-testid="upload" />);

describe('ReportDialog', () => {
    beforeEach(() => submit.mockClear());

    it('未选「其他」不强制填说明，可直接提交', async () => {
        render(
            <ReportDialog open targetType="product" targetId={42} targetLabel="商品「键盘」" onClose={jest.fn()} />,
        );
        fireEvent.click(screen.getByText('垃圾广告 / 引流'));
        fireEvent.click(screen.getByText('提交举报'));
        await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
        expect(submit.mock.calls[0][0]).toMatchObject({
            targetType: 'product',
            targetId: 42,
            reasonType: 'spam',
        });
    });

    it('选「其他」未填说明时阻止提交', async () => {
        render(
            <ReportDialog open targetType="user" targetId={55} targetLabel="用户 alice" onClose={jest.fn()} />,
        );
        fireEvent.click(screen.getByText('其他'));
        fireEvent.click(screen.getByText('提交举报'));
        await waitFor(() => expect(submit).not.toHaveBeenCalled());
    });
});
```

- [x] **Step 6: 跑测试确认 RED**

Run: `cd ui && npm test -- --testPathPattern=ReportDialog`
Expected: 失败（组件不存在）。

- [x] **Step 7: 实现 `ReportDialog.tsx`**

要点：
- props：`open` / `targetType` / `targetId` / `targetLabel` / `onClose`
- `Radio.Group` 渲染 `REPORT_REASONS`；`TextArea` 最多 200 字；`UploadImages max={3}`
- 提交前 `requireLogin()`，未登录直接返回（`loginGuard` 会跳登录页）
- `reasonType === 'other'` 且 `reasonDetail` 为空 → `message.warning('请填写具体原因')` 并中止
- 证据图取 `fileList.filter(f => f.status === 'done')` 后按 `ProductPublish.tsx:147-160` 的 `getImagePath` 同逻辑取 OSS 相对路径
- 成功：`message.success(res?.msg || '举报已提交')` + `onClose()`
- 失败：`message.error(e?.response?.data?.msg || '提交失败，请稍后重试')`

- [x] **Step 8: 跑测试确认 GREEN**

Run: `cd ui && npm test -- --testPathPattern=ReportDialog`
Expected: 2 passed。

- [x] **Step 9: 加 API 函数**

`ui/src/services/marketplace/api.ts` 追加（放在 `feedReport` 附近）：

```ts
// 举报 - 商品/动态/用户统一入口
export async function reportSubmit(data: Record<string, any>) {
    return request('/report/submit', { method: 'POST', data });
}
```

- [x] **Step 10: 类型检查 + Commit**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误。

```bash
git add ui/src/utils/report.ts ui/src/utils/report.test.ts ui/src/components/marketplace/ReportDialog.tsx ui/src/components/marketplace/ReportDialog.test.tsx ui/src/services/marketplace/api.ts
git commit -m "feat(ui): 举报弹窗与统一提交接口

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: 三个 C 端页面接举报入口

**Files:**
- Modify: `ui/src/pages/marketplace/ProductDetail.tsx`
- Modify: `ui/src/pages/marketplace/FeedDetail.tsx`
- Modify: `ui/src/pages/marketplace/UserProfile.tsx`

**Interfaces:**
- Consumes: `<ReportDialog>`（Task 9）

- [x] **Step 1: `ProductDetail.tsx` 接入**

在卖家卡片「查看主页」按钮旁（约 `ProductDetail.tsx:344-351`）加：

```tsx
{product.sellerId !== currentUserId && (
    <a style={{ fontSize: 12, color: '#999' }} onClick={() => setReportOpen(true)}>
        举报
    </a>
)}
```

组件内加状态 `const [reportOpen, setReportOpen] = useState(false);`，页面底部渲染：

```tsx
<ReportDialog
    open={reportOpen}
    targetType="product"
    targetId={product.productId}
    targetLabel={`商品「${product.title}」`}
    onClose={() => setReportOpen(false)}
/>
```

`currentUserId` 取法：沿用页面已有登录态来源（若无，用 `getToken()` 存在性 + `userProfileMe()` 的 `userId`；实现时按页面现有变量取名，不新引入请求）。

- [x] **Step 2: `FeedDetail.tsx` 接入**

在点赞/评论操作行（`FeedDetail.tsx:258-272`）末尾追加：

```tsx
{feed.userId !== currentUserId && (
    <span onClick={() => setReportOpen(true)} style={{ cursor: 'pointer', fontSize: 14 }}>
        <FlagOutlined /> 举报
    </span>
)}
```

底部渲染 `<ReportDialog targetType="feed" targetId={feed.feedId} targetLabel="该动态" ... />`。

- [x] **Step 3: `UserProfile.tsx` 接入**

在关注/私聊按钮组旁追加「举报」，条件 `!isMe`：

```tsx
{!isMe && (
    <Button danger onClick={() => setReportOpen(true)}>
        举报
    </Button>
)}
```

底部渲染 `<ReportDialog targetType="user" targetId={profile.userId} targetLabel={`用户 ${profile.nickName}`} ... />`。

- [x] **Step 4: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误。

- [x] **Step 5: 构建验证**

Run: `cd ui && npm run build`
Expected: 构建成功，`ui/dist/` 产出。

- [x] **Step 6: Commit**

```bash
git add ui/src/pages/marketplace/ProductDetail.tsx ui/src/pages/marketplace/FeedDetail.tsx ui/src/pages/marketplace/UserProfile.tsx
git commit -m "feat(ui): 商品/动态/用户主页接入举报入口

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: 运营端举报处理页

**Files:**
- Modify: `ui/src/pages/marketplace/admin/Report.tsx`（整页重写）
- Modify: `ui/src/services/marketplace/api.ts`（`adminReportPage` 保持，`adminReportHandle` 保持）

**Interfaces:**
- Consumes: `adminReportPage({status, current, pageSize})` / `adminReportHandle({reportId, action, note})`（已存在）；`parseReportReason` / `reportActionOptions` / `REPORT_REASONS`（Task 9）

- [x] **Step 1: 重写 `Report.tsx`**

结构：
- `PageContainer` + `Tabs`（`pending` / `done` / `all`，默认 `pending`）
- `ProTable`，`rowKey="reportId"`，`request` 里把当前 tab 作为 `status` 传给 `adminReportPage`，返回 `{data: r?.records || r?.data?.records || [], success: true, total: r?.total || r?.data?.total || 0}`
- 列按设计文档 §7.3 十列实现；证据图用 `Image.PreviewGroup`；`targetExists === false` 时对象列显示 `<Tag color="default">已删除</Tag>`
- 「处理」弹窗：`Select` 选项来自 `reportActionOptions(row.targetType)`；`TextArea` 备注 ≤200 字；`action === 'dismiss'` 且备注为空 → 阻止提交；`Modal.confirm` 二次确认文案「该操作将立即生效，确定吗？」
- 提交后 `message.success` + `actionRef.current?.reload()`

- [x] **Step 2: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 无错误。

- [x] **Step 3: 构建验证**

Run: `cd ui && npm run build`
Expected: 构建成功。

- [x] **Step 4: Commit**

```bash
git add ui/src/pages/marketplace/admin/Report.tsx
git commit -m "feat(ui): 运营举报处理页完整实现

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 12: 全链路手工验收

**Files:** 无代码改动

- [x] **Step 1: 后端全量测试**

Run: `cd backend && mvn test`
Expected: 全绿。

- [x] **Step 2: 格式校验**

Run: `cd backend && mvn spring-javaformat:validate`
Expected: BUILD SUCCESS。

- [x] **Step 3: 前端类型检查 + 测试 + 构建**

Run:
```bash
cd ui && npx tsc --noEmit && npm test && npm run build
```
Expected: 全绿。

- [x] **Step 4: 启动应用做端到端验收**

Run: `cd ui && PORT=3000 npm run start:dev`（另开终端 `cd backend && mvn -pl twelvet-admin spring-boot:run`）

按设计文档 §9.4 八条逐项验收，逐条记录实际结果：

| # | 操作 | 期望 |
|---|---|---|
| 1 | 普通账号举报他人商品（含证据图） | 运营端待处理出现该条，摘要/图片正确 |
| 2 | 选「下架商品」 | 商品状态变下架、买家侧不可购买；双方通知各一条 |
| 3 | 对已付款待发货（`locked`）商品举报并选下架 | 被拒绝，提示走纠纷流程 |
| 4 | 同一账号再举报同一商品 | 提示「你已举报过该内容，正在处理中」 |
| 5 | 举报自己商品 | 提示「不能举报自己发布的内容」 |
| 6 | 动态举报选「删除动态」 | 动态从广场消失 |
| 7 | 用户举报选「停用账号」 | 该账号当前会话立即失效，且无法重新登录 |
| 8 | thu 租户账号举报 default 租户用户 | 提示「举报对象不存在或已删除」 |

- [x] **Step 5: 记录验收结果**

在 `docs/superpowers/plans/2026-10-29-report-handling.md` 末尾追加「## 验收记录」小节，写实际结果（通过/失败 + 失败原因）。**失败项不得标记完成。**

- [x] **Step 6: Commit**

```bash
git add docs/superpowers/plans/2026-10-29-report-handling.md
git commit -m "docs(report): 全链路验收记录

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 验收记录（2026-10-29）

### 自动化验收（已完成）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量测试 | `cd backend && mvn -o test` | **BUILD SUCCESS**；10 个模块全绿（framework 4 / system 27 / im 4 / product 60 / trade 46 / payment 10 / exchange 3） |
| 后端格式校验 | `mvn -o spring-javaformat:validate -pl twelvet-product/twelvet-product-server,twelvet-trade/twelvet-trade-server,twelvet-im/twelvet-im-server,twelvet-framework/twelvet-framework-core` | **通过**（本分支涉及模块无违规） |
| 前端类型检查 | `cd ui && npx tsc --noEmit` | **无错误** |
| 前端测试 | `cd ui && npx jest` | **107/107 通过**（15 个 suite） |
| 前端构建 | `cd ui && npm run build` | **成功**，产物在 `ui/dist/` |

> **注**：`mvn spring-javaformat:validate` 在全仓跑会失败，7 个违规文件全在 `twelvet-exchange-server`
> （`ExchangeMatchClient`、`IExchangeReviewService`、`ExchangeItemServiceImpl`、`ExchangeMatchServiceImpl`、
> `ExchangeReviewServiceImpl`、`ExchangeMatchTask`、`ExchangeViewCountKeyTest`）。这些文件本分支
> 未触碰（`git diff --name-only master...HEAD -- backend/twelvet-exchange` 为空），属既有技术债，
> 不在本次范围内。

### 端到端手工验收（2026-10-30 已执行 —— 8/8 通过）

**执行环境**：MySQL 3306 + 后端 8080 + ai-service 8001 + ui 8000 全部 LISTENING；
`report_feature.sql` 已应用（实测 `uk_reporter_target` / `idx_target` / `idx_status_time` 三个索引存在，`feed_report` 已不存在）。
账号：`user_demo`(101 买家) / `seller_demo`(102 卖家) / `ops_demo`(103 运营) / `alice`(租户 2)。

| # | 操作 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 普通账号举报他人商品（含证据图） | 运营端待处理出现该条，摘要/图片正确 | 提交 200；DB `reason=欺诈 / 骗钱：描述与实际不符`、`evidence_images=["/a.jpg"]`；运营列表 `total:1`，`targetTitle=iPhone 14 Pro 256G 暗紫色 99新`、`targetExtra=¥5999.00 · onsale`、`reporterNickName=买家小美`、`targetExists=true`、`ownerId=102` | **通过** |
| 2 | 选「下架商品」 | 商品状态变下架、买家侧不可购买；双方通知各一条 | 返回「已处理」；product 1 `onsale`→`offshelf`；report `done`/`remove_product`/`handle_note=图片与实际不符，已核实`/`handler_id=103`；`im_notice` 2 条（101、102），标题「商品被下架」 | **通过** |
| 3 | 对 `locked` 商品举报并选下架 | 被拒绝，提示走纠纷流程 | 对已售 product 41 选 `remove_product` → `{"code":400,"msg":"该商品存在进行中订单或已售，请走纠纷处理流程"}`；product 41 仍 `sold`，report 仍 `pending` | **通过** |
| 4 | 同一账号再举报同一商品 | 提示「你已举报过该内容，正在处理中」 | `{"code":400,"msg":"你已举报过该内容，正在处理中"}` | **通过** |
| 5 | 举报自己商品 | 提示「不能举报自己发布的内容」 | `{"code":400,"msg":"不能举报自己发布的内容"}` | **通过** |
| 6 | 动态举报选「删除动态」 | 动态从广场消失 | feed 6 → 200；`remove_feed` 后 `status=deleted`、`audit_status=rejected`；`im_notice` 双方各 1 条 | **通过** |
| 7 | 用户举报选「停用账号」 | 该账号当前会话立即失效，且无法重新登录 | 封号前 seller token `/user/profile/me` 200（creditScore 80）；封后同一 token → `{"code":401,"msg":"token 已被踢下线：f7d409c7-..."}`；重新登录 → `{"code":400,"msg":"账号已被冻结"}`；`sys_user.status=1` | **通过** |
| 8 | 跨租户举报用户 | 提示「举报对象不存在或已删除」 | 带 `X-Tenant-Code: thu` 时 `{"code":400,"msg":"举报对象不存在或已删除"}`；**不带该头时返回 200** —— 见下方说明 | **通过（需带租户头）** |

**用例 8 的说明（重要，非缺陷）**：不带 `X-Tenant-Code` 时 `TenantContext` 为 null，
`TenantFilter` 按「零侵入回滚」设计**跳过租户过滤**（这是全项目既有的多租户约定，不是举报模块引入的）。
真实前端始终经 BFF 携带租户头，因此线上路径不会出现该情况。复测时误插的跨租户行已删除
（`DELETE FROM report WHERE reporter_id=123 AND target_type='user' AND target_id=101`）。

**补充验证（计划外，实现时新增的两条防线）：**

| 场景 | 期望 | 实际 | 结论 |
|---|---|---|---|
| 封禁运营账号（`user_type=00`） | 拒绝，不落库改动 | `{"code":400,"msg":"运营账号不可通过举报处置停用"}`；103 `status` 仍为 0 | **通过** |
| 同一举报重复处置（双击） | 第二次被拒 | 第一次「已处理」；第二次 `{"code":400,"msg":"该举报已被处理"}`（`markHandled` 的 `WHERE status='pending'` 生效） | **通过** |
| 未登录提交举报 | 401 | `{"code":401,"msg":"未能读取到有效 token"}` | **通过** |
| 普通用户调处置接口 | 403 | `{"code":403,"msg":"无此权限：marketplace:admin:report"}` | **通过** |

**验证后已回滚本地测试数据**（避免污染其它演示）：删除 `report` 全部测试行；product 1 恢复 `onsale`；
feed 6 恢复 `normal`/`approved`；`seller_demo`(102) 恢复 `status=0`；删除 8 条测试 `im_notice`。

**这些行为同时被单元测试覆盖（逻辑层）：**

| 用例 | 覆盖它的测试 |
|---|---|
| 1 / 4 / 5 | `ReportServiceImplTest.submit_product_ok` / `submit_duplicatePending_rejected` / `submit_self_rejected` |
| 2 | `handle_removeProductOk`（`updateStatus(42, "offshelf", null, 1)` + 双方 sendNotice） |
| 3 | `handle_removeProductLocked_rejected` / `handle_removeProductSold_rejected`（经变异验证：把 locked 加入可下架集合该测试即 RED） |
| 6 | `handle_removeFeedOk` |
| 7 | `handle_banUserKicksOut`（mockStatic 验证 `StpUtil.kickout(55)`） |
| 8 | `submit_crossTenantUser_rejected` |

**启动后端前必须先执行的 SQL**（`backend/sql/report_feature.sql`，幂等，可重复跑）：

```sql
-- 1. uk_reporter_target (reporter_id, target_type, target_id)
-- 2. idx_target (target_type, target_id)
-- 3. idx_status_time (status, create_time)
-- 4. ALTER report MODIFY action COMMENT 'dismiss/warn/remove_product/remove_feed/ban_user'
-- 5. DROP TABLE IF EXISTS feed_report
```

**在新环境复跑验收的步骤**：启动 MySQL → 执行 `backend/sql/report_feature.sql` → 起后端
→ 起前端（`PORT=3000 npm run start:dev`）→ 按上表逐条验证。注意跨租户用例需带 `X-Tenant-Code` 头。

### 遗留（本分支不处理）

- `twelvet-exchange-server` 的 7 个格式违规文件（既有技术债）
- `ui/dist` 中登录页预填 demo 账号与硬编码 Basic 认证串（CLAUDE.md 已记录为阶段 D 待处理项）
