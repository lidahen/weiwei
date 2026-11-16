# 举报处理闭环设计（C 端举报 → 落库 → 运营处理 → 处置生效）

> 状态：**待评审，未实施**
> 版本：2026-10-29 v2（v1 经构建者校验，修正 bean 名冲突、模块循环依赖、硬删/软删语义、
> 下架状态守卫、通知事务语义、跨租户目标、前端测试现状共 7 处）
> 相关代码：`ui/src/pages/marketplace/admin/Report.tsx`（占位）、`ui/src/services/marketplace/api.ts`、
> `ui/src/pages/marketplace/{ProductDetail,FeedDetail,UserProfile}.tsx`、
> `backend/twelvet-trade/.../AdminMarketplace{Controller,Service,Mapper}.java|.xml`、
> `backend/twelvet-product/.../FeedServiceImpl.java:233`、
> `backend/twelvet-product/.../mapper/UserMapper.java`、
> `backend/twelvet-im/.../ImNoticeMapper.java`
> 相关文档：`docs/全局健壮性与架构审查报告.md`（P0-5 五张表无 tenant_id；「重复举报无约束」）、
> `docs/superpowers/specs/2026-06-11-feed-square-design.md`（feed_report 原始设计）

## 1. 现状：四个断点

调查结论（2026-10-29 实测）：

| 环节 | 现状 | 证据 |
|---|---|---|
| 用户能举报 | **不能**。`feedReport()` 封装了但全仓 0 处调用；商品/用户无举报接口 | `ui/src/services/marketplace/api.ts:224` 无引用 |
| 举报落库 | **不落库**。`FeedServiceImpl.report()` 只 `log.info` 然后返回 `"reported"` | `FeedServiceImpl.java:233-236` |
| 运营看得懂 | **看不懂**。列表只吐 7 个字段，运营看到 `target_type=product, target_id=42` 不知道是哪个商品 | `AdminMarketplaceMapper.xml:185-193` |
| 处置生效 | **不生效**。`handleReport` 只改 `status='done'` 记 `action`，选「下架」不会真下架 | `AdminMarketplaceMapper.xml:195-201` |

外加：
- `Report.tsx` 是 `<Empty description="举报处理页面 - 占位中,业务功能开发中" />`
- `report` 表全库 0 行；`feed_report` 是重复造的死表（0 行、0 代码引用）
- `handleReport` 无状态守卫，已处理的举报可重复处理
- `report` 表无 `(reporter_id, target_type, target_id)` 唯一键，同一用户可无限重复举报

## 2. 目标与非目标

### 目标（本次全部实现）

1. C 端三类对象可举报：**商品 / 动态 / 用户**
2. 举报真实落 `report` 表，带证据图、防重复、防自举报、防跨租户
3. 运营端完整列表：看得懂被举报对象是什么、能筛选待处理/已处理、能看到证据图
4. 处置动作**真实生效**：警告 / 下架商品 / 删除动态 / 停用账号 / 驳回
5. 处理结果通过站内通知触达**举报人**与**被举报人**

### 非目标（明确不做）

- **不给 `report` 表加 `tenant_id` 列**。沿用全仓既有约定（`AdminMarketplaceMapper.xml` 头注释）：
  无租户列的表一律 JOIN `sys_user` 取租户。改列属于租户隔离专项，不在本次范围。
  （但**跨租户目标**这一漏洞本次要堵，见 §5.1）
- 不做 IM 消息单条举报（`im_msg`）——没有单条消息详情页，无落脚点。
- 不做举报频次风控（限流/黑名单），仅做同人同对象去重。
- 不做申诉流程（被处置人申诉）。
- 不做 AI 自动预判举报真伪。

## 3. 数据模型

### 3.1 `report` 表（沿用，仅加索引与注释）

现有结构（`docs/db/twelvet-fast_2026-10-21.sql:1971-1988`）：

| 列 | 类型 | 说明 |
|---|---|---|
| report_id | bigint PK | |
| reporter_id | bigint | 举报人 |
| target_type | varchar(20) | `product` / `feed` / `user`（原注释含 `im_msg`，本次不用） |
| target_id | bigint | 被举报对象 ID |
| reason | varchar(500) | 举报原因（存「类型标签 + 补充说明」合成文本） |
| evidence_images | text | 证据图 JSON 数组（OSS 相对路径） |
| status | varchar(20) | `pending` / `done` |
| action | varchar(20) | `dismiss`/`warn`/`remove_product`/`remove_feed`/`ban_user` |
| handle_note | varchar(500) | 处理备注 |
| handler_id | bigint | 处理人 |
| create_time | datetime | |
| handle_time | datetime | |

**变更（迁移脚本 `backend/sql/report_feature.sql`）**：

```sql
-- 同人同对象只留一条举报（重复举报走 UPDATE 复活，见 §5.1）
ALTER TABLE `report` ADD UNIQUE KEY `uk_reporter_target` (`reporter_id`,`target_type`,`target_id`);
ALTER TABLE `report` ADD KEY `idx_target` (`target_type`,`target_id`);
ALTER TABLE `report` ADD KEY `idx_status_time` (`status`,`create_time`);
ALTER TABLE `report` MODIFY COLUMN `action` varchar(20) DEFAULT NULL
    COMMENT 'dismiss/warn/remove_product/remove_feed/ban_user';
ALTER TABLE `report` MODIFY COLUMN `target_type` varchar(20) NOT NULL
    COMMENT 'product/feed/user';
DROP TABLE IF EXISTS `feed_report`;
```

`report` 表当前 0 行，加唯一键无存量冲突。`feed_report` 已核实 0 行、全仓 0 代码引用
（仅 `docs/db` 基线里存在），属重复建模的死表，删除避免后来者写错表。

**不加 `tenant_id`**：租户过滤继续走 `JOIN sys_user u ON u.user_id = r.reporter_id`，
读写两侧同口径（与 `AdminMarketplaceMapper.xml` 既有 `selectReportList` / `handleReport` 一致）。

### 3.2 被举报对象归属与「存在性」语义

| target_type | 对象表 | 归属人（owner） | 摘要字段 | 删除方式 | `targetExists` 判定 |
|---|---|---|---|---|---|
| product | `product` | `seller_id` | `title` / `cover` / `price` / `status` | **硬删**（`DELETE FROM product`，`ProductMapper.xml:343`） | 行存在即 true（任意 status） |
| feed | `feed` | `user_id` | `content` 前 80 字 / `images[0]` / `audit_status` | **软删**（`status='deleted'`，`FeedMapper.xml:177`） | 行存在 **且** `status != 'deleted'` |
| user | `sys_user` | `target_id` 本身 | `nick_name` / `avatar` / `status` | 软删（`del_flag='2'`） | 行存在 **且** `del_flag='0'` |

**这条区分必须写进 Service**：`feed` 软删后行还在，若按「行存在即存在」判定，
运营会看到一条已删除动态仍显示「正常」，且 `remove_feed` 会在已删除的动态上再做一次无意义更新。
`product` 硬删后行没了，`selectByIds` 自然查不到。

归属人用于「封禁」动作与「处置通知」。

## 4. 架构与模块归属

### 4.1 决策：举报业务逻辑整体放 `twelvet-product-server`，运营 HTTP 入口留在 `twelvet-trade`

**v1 的错误**：v1 说「product 拥有 product/feed/sys_user 的数据访问」并让 product 的
`ReportServiceImpl` 调 trade 的 `AdminMarketplaceMapper.updateUserStatus`。这是错的，且会成环：

- product 的 `UserMapper` **只有 SELECT**（`UserMapper.xml` 全文件 0 个 `<update>`），
  唯一 `UPDATE sys_user SET status` 在 trade（`AdminMarketplaceMapper.xml:218`）；
- 让 product 依赖 trade，而 trade 已依赖 product（`twelvet-trade-server/pom.xml:43-47`）→ **Maven 循环依赖**。

**v2 方案**：给 product 的 `UserMapper` 补一条 `updateStatus`（见 §4.3），
举报服务**只在 product 模块**，不反向依赖 trade。

```
C 端（已登录）
  POST /report/submit ──────────┐
  POST /feed/report ────────────┤
                                ▼
                    twelvet-product-server
                    IReportService / ReportServiceImpl
                      （@Service("productReportServiceImpl")）
                    ├─ ReportMapper        report 表唯一读写点
                    ├─ ProductMapper       校验/摘要/下架
                    ├─ FeedMapper          校验/摘要/删除
                    ├─ UserMapper          校验/摘要/停用（新增 updateStatus）
                    ├─ IImService          站内通知
                    └─ StpUtil             封号后踢下线
                                ▲
                                │ 注入（trade 已依赖 product-server）
运营端（marketplace:admin:report）
  GET  /admin/marketplace/report/page ──┐
  POST /admin/marketplace/report/handle ┴── twelvet-trade-server
                                             AdminMarketplaceController
                                             → AdminMarketplaceServiceImpl
                                             → IReportService（全部委托）
```

**依赖方向**：product → im-server（已有，`pom.xml:49-53`）；trade → product-server（已有）。
**无新增模块依赖**，无环。

### 4.2 ⚠️ Bean 名冲突（会让应用启动失败）

`twelvet-ai-server` 已存在 `com.twelvet.ai.server.service.impl.ReportServiceImpl`
（社区日报，`AiReportController` 注入），注解是裸 `@Service`（`ReportServiceImpl.java:37`）。

`FastApplication` 的 `@ComponentScan(basePackages = "com.twelvet")` 扫描全仓，
且全仓未设 `spring.main.allow-bean-definition-overriding`（Boot 3.5 默认 false）。
两个 `@Service` 类同名 → 默认 bean 名都是 `reportServiceImpl` → 启动即
`ConflictingBeanDefinitionException`。

**解决**：显式 bean 名 `@Service("productReportServiceImpl")`，
与仓库既有先例一致（`@Service("productFeedServiceImpl")`，`FeedServiceImpl.java:27`）。
接口 `IReportService` 与 AI 的同名接口在不同包，不影响 Spring；但**必须**有显式 bean 名。

### 4.3 新增/修改文件

**新增（`twelvet-product-server`）**

| 文件 | 职责 |
|---|---|
| `.../domain/Report.java` | report 表实体（普通 POJO，非 BaseEntity——表无审计字段） |
| `.../mapper/ReportMapper.java` + `resources/mapper/product/ReportMapper.xml` | report 表唯一读写点 |
| `.../service/IReportService.java` / `impl/ReportServiceImpl.java` | 举报业务（submit / adminList / adminHandle） |
| `.../controller/ReportController.java` | C 端 `POST /report/submit` |
| `src/test/.../service/impl/ReportServiceImplTest.java` | 单测 |
| `src/test/.../controller/ReportControllerAuthTest.java` | 契约测试 |
| `ui/src/components/marketplace/ReportDialog.tsx` + `.test.tsx` | 三处复用的举报弹窗 |
| `backend/sql/report_feature.sql` | 迁移 |

**修改**

| 文件 | 改动 |
|---|---|
| `FeedServiceImpl.report()` | 委托 `IReportService.submit("feed", ...)`，不再只打日志 |
| `FeedMapper.java` + XML | 新增 `selectByIds(List<Long>)`（不过滤 status，已删除也要能显示） |
| `ProductMapper.java` + XML | 新增 `selectByIds(List<Long>)`（不过滤 status） |
| `UserMapper.java` + XML | 新增 `selectByIds(List<Long>)`（含 `status`/`del_flag`）、**新增 `updateStatus`** |
| `IImService` / `ImServiceImpl` | 新增 `sendNotice(Long userId, String type, String title, String content)` |
| `AdminMarketplaceController` | 举报端点支持 `status=all`，处理端点透传 `handlerId` |
| `AdminMarketplaceServiceImpl` | 两个举报方法改为委托 `IReportService` |
| `AdminMarketplaceMapper.java/.xml` | 删除 `selectReportList` / `handleReport`（单一读写点） |
| `ui/src/services/marketplace/api.ts` | 新增 `reportSubmit` |
| `ui/src/pages/marketplace/admin/Report.tsx` | 完整页面 |
| `ProductDetail.tsx` / `FeedDetail.tsx` / `UserProfile.tsx` | 举报入口 |

> `AdminMarketplaceMapper.updateUserStatus` **保留不动**——运营「用户管理」页仍用它。
> 举报封号走 product 的 `UserMapper.updateStatus`。两条 SQL 写同一列，属可接受的轻微重复，
> 换来了无环的模块依赖。

## 5. 接口设计

### 5.1 C 端提交举报

```
POST /report/submit          （需登录，无 @SaIgnore）
Content-Type: application/json

{
  "targetType": "product",     // product | feed | user
  "targetId": 42,
  "reasonType": "fraud",       // 见 §5.3
  "reasonDetail": "卖家收钱不发货",   // 可选，≤200 字
  "evidenceImages": ["/upload/a.jpg"] // 可选，≤3 张，OSS 相对路径
}
→ JsonResult<String> data = "举报已提交，我们会尽快处理"
```

校验（`ReportServiceImpl.submit`，任一不满足抛 `TWTException`）：

| 校验 | 失败文案 |
|---|---|
| `targetType` ∈ {product, feed, user} | 举报对象类型不合法 |
| 目标存在（按 §3.2 语义） | 举报对象不存在或已删除 |
| **目标属于当前租户** | 举报对象不存在或已删除（不泄露存在性） |
| 举报人 ≠ 归属人 | 不能举报自己发布的内容 |
| `reasonType` ∈ §5.3 列表 | 举报原因不合法 |
| 已存在 `pending` 举报（同人同对象） | 你已举报过该内容，正在处理中 |
| 已存在 `done` 举报（同人同对象） | 复用该行：重置为 pending、覆盖原因与证据、清空处理字段 |

**跨租户校验**（v1 遗漏）：
- product → `productMapper.selectById(targetId, currentTid())` 已带租户过滤，天然拦住
- feed → `feedMapper.selectById(targetId, currentTid())` 同上
- user → `UserMapper.selectUserByIds` **无租户过滤**，须显式比对
  `UserMapper.selectUserTenantId(targetId)` 与 `TenantContext.get()`；
  租户开关关闭（`tid == null`）时跳过比对，保持兼容模式。

`reason` 列落库内容 = `原因标签中文 + "：" + reasonDetail`（超 500 字截断）。

### 5.2 运营列表

```
GET /admin/marketplace/report/page?status=pending|done|all&current=1&pageSize=20
→ JsonResult<TableDataInfo<Map>>
```

`status=all` 不过滤状态。返回每行：

| 字段 | 来源 |
|---|---|
| reportId / targetType / targetId | report |
| reasonType / reasonDetail | report.reason 拆分（见下） |
| evidenceImages | report.evidence_images（JSON 数组） |
| status / action / handleNote / handleTime | report |
| reporterId / reporterNickName | report + sys_user |
| handlerId / handlerNickName | report + sys_user |
| targetTitle | product.title / feed.content 前 80 字 / user.nick_name |
| targetCover | product.cover / feed.images[0] / user.avatar |
| targetExtra | product: `¥price · status`；feed: `audit_status`；user: `status` |
| ownerId / ownerNickName | 归属人（§3.2） |
| targetExists | 按 §3.2 判定 |

**N+1 处理**：按 `targetType` 分组，对每组 ID 批量 `selectByIds`（product/feed/user 三条批量 SQL），
在 Service 里组装成 Map，**不做逐条查询**。

`reason` 列存的是合成文本，列表需要拆开展示：约定落库格式 `标签：详情`，
Service 按**第一个全角冒号**切分，无冒号则整串作为 detail、reasonType 留空。
（不新增 `reason_type` 列，避免再动表结构。）

### 5.3 举报原因枚举（前后端共用，Service 侧白名单校验）

| 值 | 中文 |
|---|---|
| `spam` | 垃圾广告 / 引流 |
| `fraud` | 欺诈 / 骗钱 |
| `fake` | 假冒伪劣 / 描述不符 |
| `prohibited` | 违禁品 / 违规内容 |
| `abuse` | 辱骂 / 骚扰 |
| `other` | 其他 |

### 5.4 运营处理

```
POST /admin/marketplace/report/handle
{ "reportId": 1, "action": "remove_product", "note": "商品图片与实际不符" }
→ JsonResult<String> data = "已处理"
```

处理人取 `StpUtil.getLoginIdAsLong()`（Controller 透传给 Service）。

**状态守卫**：`UPDATE ... WHERE report_id = ? AND status = 'pending'`，`rows = 0` 抛
`TWTException("该举报已被处理")`。并发双击只会有一次成功。

**动作、前置条件与生效范围**：

| action | 适用 targetType | 前置条件 | 生效 | 通知对象 |
|---|---|---|---|---|
| `dismiss` | 任意 | 无 | 无 | 仅举报人（「经核实未违规」） |
| `warn` | 任意 | 无 | 无 | 归属人（警告）+ 举报人（已处理） |
| `remove_product` | product | **`status ∈ {onsale, auditing}`**（见下） | `product.status → offshelf` | 归属人 + 举报人 |
| `remove_feed` | feed | 目标存在（§3.2） | `feed.status → deleted`，`audit_status → rejected` | 归属人 + 举报人 |
| `ban_user` | 任意 | 归属人存在且非运营账号 | `sys_user.status → 1` + **`StpUtil.kickout(ownerId)`** | 归属人 + 举报人 |

`action` 与 `targetType` 不匹配时抛 `TWTException("该处理动作不适用于此举报对象")`。

**`remove_product` 状态守卫（v1 遗漏，会静默破坏交易）**：
`ProductMapper.updateStatus` 无状态条件（`ProductMapper.xml:299-306`）。若举报触发的下架
覆盖 `locked`（已付款待发货）或 `sold`，会让 `markSold` 的
`WHERE stock = 0 AND status = 'locked'`（`ProductMapper.xml:337-341`）永不命中，
已付款订单的商品状态错乱。

**决策（已确认）**：只允许 `onsale` / `auditing` 状态的商品被举报处置下架；
其余状态（`locked` / `sold` / `offshelf` / `draft` / `rejected`）抛
`TWTException("该商品存在进行中订单或已售，请走纠纷处理流程")`，
前端在弹窗里就提示运营走纠纷工单。

**封号即时性（已确认）**：除改 `status='1'` 外，同时 `StpUtil.kickout(ownerId)` 踢掉该用户全部在线会话。
依据：登录接口（`LoginServiceImpl:104,293`）只校验登录时刻的 status，
已发放 token 有效期 86400s 且无逐请求状态校验——不踢的话封号最长 24 小时不生效。
`StpUtil.kickout(Object loginId)` 在 sa-token-core 1.44.0 中确认存在
（`kickout` 另有 `(Object, String)` 与 `(Object, SaLogoutParameter)` 重载）。
踢会话放在**事务提交后**执行（见 §5.5），避免回滚后用户被误踢。

**处置失败不阻断**：目标在提交后被删除等情况下，记录日志、照常置 `done`，
在 `handle_note` 尾部追加 `[对象已不存在，处置未执行]`，前端可见。

### 5.5 事务边界与通知

`adminHandle` 结构：

```
@Transactional
public void adminHandle(...) {
    // 1) 状态守卫 UPDATE，rows=0 → 抛异常（回滚）
    // 2) 处置生效（product/feed/sys_user 更新，失败且目标不存在 → 记 note 不抛）
    // 3) 返回，事务提交
}
// 事务提交后（由 Controller/调用方或 TransactionSynchronization afterCommit 触发）
→ sendNotice(...) × 2，逐个 try/catch 只记 warn 日志
```

**为什么通知必须在提交后**（v1 遗漏）：v1 写「通知在事务内、失败 try/catch 不回滚」。
但 `IImService.sendNotice` 若带 `@Transactional`（默认 `REQUIRED`），
其抛异常会把**共享事务**标为 rollback-only，外层提交时抛
`UnexpectedRollbackException` —— 与意图完全相反。

**实现约束**：
- `IImService.sendNotice` **不加 `@Transactional`**（单条 INSERT，随外层事务提交即可）
- 调用侧 `try/catch` 包住，异常只记 `log.warn`
- 会话踢下线（`StpUtil.kickout`）放 `TransactionSynchronizationManager.registerSynchronization`
  的 `afterCommit` 里，或由 Controller 在 Service 返回后执行

## 6. 通知

新增 `IImService.sendNotice(userId, type, title, content)` → `ImNoticeMapper.insertNotice`。

| 场景 | 收件人 | type | title | content |
|---|---|---|---|---|
| 处理完成 | 举报人 | `platform` | 举报已处理 | 你对「{对象摘要}」的举报已处理：{动作说明}{备注} |
| 警告 | 归属人 | `platform` | 收到警告 | 你发布的「{对象摘要}」因{原因}被警告，请自查 |
| 下架商品 | 归属人 | `platform` | 商品被下架 | 你的商品「{标题}」因{原因}被下架{备注} |
| 删除动态 | 归属人 | `platform` | 动态被删除 | 你的动态因{原因}被删除{备注} |
| 停用账号 | 归属人 | `platform` | 账号已被停用 | 你的账号因违规被停用，如有疑问请联系客服 |

`im_notice` 无租户列（`docs/db` 定义），写入不做租户过滤，读取按 `user_id`。
前端「通知」Tab（`ConversationList.tsx:122-140`）已渲染 `type=platform` 为「平台」标签，无需改动。

## 7. 前端

### 7.1 复用举报弹窗 `ReportDialog`

```tsx
<ReportDialog
  open={open}
  targetType="product" | "feed" | "user"
  targetId={id}
  targetLabel="商品「九成新机械键盘」"
  onClose={() => setOpen(false)}
/>
```

内容：原因类型 `Radio.Group`（§5.3 六项）→ 补充说明 `TextArea`（≤200 字，选「其他」时必填）
→ 证据图 `UploadImages max={3}`。提交前 `requireLogin()` 守卫。

### 7.2 三个入口

| 页面 | 位置 | 隐藏条件 |
|---|---|---|
| `ProductDetail.tsx` | 卖家卡片「查看主页」旁加「举报」文字链 | 自己是卖家 |
| `FeedDetail.tsx` | 点赞/评论操作行末尾加「举报」 | 自己是作者 |
| `UserProfile.tsx` | 关注/私聊按钮组加「举报」 | 是自己主页 |

### 7.3 运营端 `Report.tsx`

`PageContainer` + `ProTable`，顶部 `Tabs`：待处理 / 已处理 / 全部（默认待处理）。

| 列 | 渲染 |
|---|---|
| 举报ID | 纯文本，宽 80 |
| 对象 | 缩略图 + `targetTitle`（超长省略）；`targetType` 用 `Tag` 区分（商品/动态/用户）；`targetExists=false` 显示「已删除」 |
| 补充 | `targetExtra` 灰字 |
| 举报人 | `reporterNickName` |
| 原因 | `reasonType` 中文 `Tag` + `reasonDetail` |
| 证据 | `evidenceImages` 走 `Image.PreviewGroup`，无图显示「—」 |
| 状态 | `pending` 橙「待处理」/ `done` 绿「已处理」 |
| 举报时间 | `valueType: 'dateTime'` |
| 处理信息 | `handleTime` + `handlerNickName` + `action` 中文 + `handleNote` |
| 操作 | 仅 `pending` 显示「处理」；`done` 显示「查看对象」链接 |

处理弹窗：动作 `Select`（选项随 `targetType` 过滤，见 §5.4）→ 处理备注 `TextArea`（≤200 字，
选 `dismiss` 时必填，理由要回传给举报人）→ 二次确认文案写明「该操作将立即生效」。

## 8. 错误处理

| 场景 | 表现 |
|---|---|
| 未登录点举报 | `requireLogin()` 跳登录页 |
| 举报对象已删除 / 跨租户 | 后端 `TWTException` → 前端 `message.error` 显示后端文案（统一「不存在或已删除」，不泄露存在性） |
| 重复举报 | 同上，文案「你已举报过该内容，正在处理中」 |
| 并发处理同一举报 | 后到者 `TWTException("该举报已被处理")`，前端刷新列表 |
| 下架 locked/sold 商品 | `TWTException("该商品存在进行中订单或已售…")`，前端提示走纠纷 |
| 处置对象已被删除 | 不报错，`handle_note` 标注，处理照常完成 |
| 通知失败 | 只记 warn 日志，处理结果不受影响（事务已提交） |

## 9. 测试策略

### 9.1 后端单测（Mockito，`ReportServiceImplTest`）

| 用例 | 断言 |
|---|---|
| 举报自己的商品 | 抛 `TWTException`，`ReportMapper.insert` 未调用 |
| 目标不存在（product 行缺失 / feed 软删 / user del_flag='2'） | 抛 `TWTException`，不落库 |
| 跨租户举报 user | 抛 `TWTException`，不落库 |
| 非法 targetType / reasonType | 抛 `TWTException` |
| 已有 pending 举报 | 抛「正在处理中」，不落库 |
| 已有 done 举报 | 走 update 复活路径，清空 handle 字段 |
| 正常提交 | insert 一次，`reason` 为「标签：详情」格式 |
| `reason` 超 500 字 | 截断 |
| 处理非 pending 举报 | 抛「已被处理」，不执行任何处置 |
| `remove_product` + targetType=feed | 抛「不适用于此举报对象」 |
| `remove_product` + status=locked | 抛「存在进行中订单」，`updateStatus` 未调用 |
| `remove_product` + status=onsale | `ProductMapper.updateStatus(offshelf)` 调用一次 |
| `remove_feed` + feed | `FeedMapper.updateStatus(deleted, rejected)` 调用一次 |
| `ban_user` | `UserMapper.updateStatus(ownerId, 1)` 调用一次 + `StpUtil.kickout(ownerId)` 被调用 |
| 通知抛异常 | 处理仍返回成功，`status='done'` 已落 |

> `StpUtil` 是静态类，测试用 `mockStatic`。已核实仓库内已有先例
> （`LoginServiceImplSmsTest:78`、`SmsCodeServiceImplTest:59` 对 `RedisUtils` 的 `mockStatic`），
> inline mock maker 可用，无需额外引入依赖。

### 9.2 控制器契约测试

`ReportControllerAuthTest`：`/report/submit` 不得带 `@SaIgnore`（防止游客写举报）。

### 9.3 前端

**v1 的错误**：v1 称「现有前端测试仅覆盖 utils」，为不测组件找理由。实际已有 5 个组件测试
（`Seo/Heading.test.tsx`、`Seo/index.test.tsx`、`AI/ShoppingGuide/index.test.tsx`、
`Exchange/Detail.test.tsx`、`marketplace/Home.test.tsx`），其中 `ShoppingGuide` 用
`@testing-library/react` 的 render/fireEvent。

**v2**：
- `ui/src/utils/report.ts` 纯函数 + 单测：`parseReportReason`（合成文本拆分）、
  `reportActionOptions(targetType)`（动作选项过滤）
- `ui/src/components/marketplace/ReportDialog.test.tsx` 组件测试：
  未登录点击 → 触发跳转守卫；选「其他」未填说明 → 阻止提交；正常提交 → 调用 `reportSubmit`

### 9.4 手工验收（本地）

1. 用普通账号举报他人商品（含证据图）→ 运营端待处理出现该条，摘要/图片正确
2. 选「下架商品」→ 商品详情状态变下架、买家侧不可购买；双方通知 Tab 各收到一条
3. 对**已付款待发货**的商品举报并选下架 → 被拒绝并提示走纠纷流程
4. 同一账号再举报同一商品 → 提示「正在处理中」
5. 举报自己商品 → 提示不能举报自己
6. 动态举报选「删除动态」→ 动态从广场消失
7. 用户举报选「停用账号」→ 该账号**当前会话立即失效**（再刷新页面被踢到登录页），
   且无法重新登录
8. 跨租户：thu 租户账号举报 default 租户用户 → 提示「不存在或已删除」

## 10. 风险与取舍

| 风险 | 处理 |
|---|---|
| 删除 `feed_report` 属破坏性操作 | 已核实 0 行、0 代码引用；迁移脚本单列注释说明；新环境先跑基线再跑迁移，结果一致 |
| 唯一键挡住「同一人多次举报」 | 业务上本就该去重；已处理后可再次举报（update 复活） |
| 处置动作误操作不可逆 | 弹窗二次确认 + `handle_note` 必填（dismiss 时）+ 操作日志（`@Log` 注解） |
| 通知走 `im_notice` 而非聊天消息 | 语义更准（平台通知），且前端「通知」Tab 已能渲染；不新增会话噪音 |
| `reason` 单列存合成文本 | 避免改表加 `reason_type`；拆分规则写在 Service 并有单测钉住 |
| product 首次写 `sys_user` | 仅一条 `updateStatus`，与 trade 的 `updateUserStatus` 并存；换来无环依赖 |
| bean 名冲突 | 显式 `@Service("productReportServiceImpl")`；计划阶段加一条启动冒烟验证 |

## 11. 构建者校验的处置清单（v1 → v2 变更对照）

| # | v1 问题 | 严重度 | v2 处置 |
|---|---|---|---|
| 1 | 新建 `ReportServiceImpl` 与 AI 模块同名 → 启动即 `ConflictingBeanDefinitionException` | 阻断 | §4.2 显式 bean 名 `productReportServiceImpl` |
| 2 | `ban_user` 让 product 调 trade 的 Mapper → 与 trade→product 成环 | 阻断 | §4.1 给 product `UserMapper` 补 `updateStatus`，依赖方向不变 |
| 3 | 「前端测试仅覆盖 utils」失实 | 中 | §9.3 改为如实描述，并补组件测试 |
| 4 | `product` 硬删 / `feed` 软删未区分，`targetExists` 语义含糊 | 中 | §3.2 明确三类的存在性判定 |
| 5 | `remove_product` 无状态守卫 → 覆盖 locked 破坏 `markSold` | **高** | §5.4 只允许 onsale/auditing；其余走纠纷 |
| 6 | `sendNotice` 若带 `@Transactional` → 通知异常反而回滚处理 | 中 | §5.5 明确不加事务注解 + 踢会话放 afterCommit |
| 7 | C 端可举报跨租户 user | 中 | §5.1 补租户比对 |

## 12. 迁移执行顺序

在既有初始化序列末尾追加（`CLAUDE.md`「数据库初始化」第 5 步之后）：

```bash
mysql ... < backend/sql/report_feature.sql
```
