# AI 社区日报 设计文档

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 2026-09-06 · 状态：待实现
> 定位：聚合平台每日数据，用 LLM 生成社区日报，IM 推送 + 页面存档。
> 目标是低成本串起 product / runner / feed / IM / AI 五个模块，做演示效果与用户粘性。

## 1. 目标与非目标

**目标**

- 每天早上 7:00 为每个启用租户自动生成一份「社区日报」（LLM 文案），并 IM 推送给该租户全部在职用户
- 日报页面可翻阅历史（按日期倒序卡片流 + 详情）
- 管理员可手动触发「立即生成今日日报」（幂等：当天已生成则覆盖更新）
- 多租户隔离：日报内容、推送范围均按租户划分

**非目标（本期不做）**

- 不做个性化推荐（所有人看同一份日报）
- 不做邮件/短信渠道
- 不做阅读数统计（页面 PV 后续可加）

## 2. 信息源矩阵（每租户 · 每日）

| 维度 | 数据来源（只读聚合 SQL） | 采数指标 |
|---|---|---|
| 二手市场 | `product` | 新上架数、浏览 Top3（`product_stats_daily`/`product_view_log`）、成交流水 |
| 社区动态 | `feed` / `feed_like` | 新发帖数、点赞 Top2 帖子标题 |
| 社区跑腿 | `twelve_runner_task` / `twelve_runner_review` | 完成单数、新增好评均分、累计完成总数 |
| 平台事件 | `sys_tenant_renew_order`（可选） | 套餐变动（仅超管日报） |

聚合口径：昨日 00:00 ~ 23:59（`create_time` 落在区间），按 `tenant_id` 过滤。
**零活动租户**：跳过生成（省配额），仅记日志。

## 3. 触发与调度

- 复用 twelvet-quartz 反射模式（同 `runnerTask.scanTimeoutPending`）：
  - Bean：`@Component("aiDailyReport")`，方法 `generateAll()`
  - `sys_job` 注册：`aiDailyReport.generateAll`，cron `0 0 7 * * ?`（每天 07:00）
- `generateAll()`：查启用租户列表 → 逐租户 `generate(tenantId)`（try/catch 单租户失败不影响其他租户）
- 手动触发：`POST /ai/report/generate`（管理员，`@SaCheckPermission("ai:report:generate")`），仅生成当前租户当日
- **幂等**：`(tenant_id, report_date)` 唯一键，重复生成走 `UPDATE` 覆盖（人工触发常在定时任务之后）

## 4. 表设计

```sql
CREATE TABLE `twelve_daily_report` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id`   BIGINT NOT NULL COMMENT '租户ID',
  `report_date` DATE   NOT NULL COMMENT '日报日期',
  `title`       VARCHAR(200) NOT NULL COMMENT '日报标题（LLM 生成）',
  `content`     TEXT NOT NULL COMMENT '日报正文（Markdown）',
  `stats_json`  JSON NOT NULL COMMENT '原始聚合指标（供审计/重新生成）',
  `source`      VARCHAR(20) NOT NULL DEFAULT 'llm' COMMENT 'llm=AI生成, fallback=模板兜底',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report` (`tenant_id`, `report_date`)
) ENGINE=InnoDB COMMENT='AI 社区日报';
```

DDL 落 `backend/data/twelve-ai-report-init.sql`（含 sys_job 注册与菜单 SQL），并直接在库执行。

## 5. 接口设计

Java（twelvet-ai-server，菜单挂在「AI 应用」下）：

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/ai/report/list?pageNum&pageSize` | 当前租户日报分页，日期倒序 | 登录（`@SaCheckLogin`） |
| GET | `/ai/report/{id}` | 日报详情 | 登录 + 租户校验 |
| POST | `/ai/report/generate` | 立即生成/覆盖当日，返回日报 | `ai:report:generate` |

Python（ai-service，新增 `POST /ai/assist/daily-report`）：

- 入参：`{ stats: {...聚合指标...}, tenantName, date }`（驼峰别名，惯例同 assist）
- 出参：`{ title, content }`（content 为 Markdown，固定小节结构）
- 走 LLM 默认模型（`model_type=LLM, default_flag=1`），`feature='daily-report'` 计入 `ai_usage_daily` 配额（每租户每天至多 2 次：定时 + 一次手动覆盖）
- Java 经 `AiServiceClient` 调用（依赖已就绪），429 配额超限时直接走模板兜底

### 提示词要点

- 角色：社区运营小编；语气：轻松、有温度、不夸大
- 输入只给聚合 JSON，**不给任何用户手机号/地址等 PII**；输出固定结构：
  `## 一句话总览` → `## 二手好物`（挑 2-3 件，带标题）→ `## 邻里互助`（跑腿完成与好评）→ `## 社区动态`（热帖）
- 数据为零的小节输出「今天很安静」类一句话，不编造数据（提示词里明确：禁止虚构未提供的数字/标题）

## 6. 降级策略

| 故障 | 行为 |
|---|---|
| LLM 超时/异常/429 | `source='fallback'` 模板拼接（纯数字罗列），日报照常生成推送 |
| 某租户聚合 SQL 异常 | try/catch 跳过该租户，记 error 日志 |
| IM 推送部分失败 | 逐用户 try/catch，失败名单记日志；不影响日报存档 |
| ai-service 整体不可用 | 同 fallback 路径 |

## 7. IM 推送

- 复用 `IImService.sendMessage(userId, null, null, "system", 摘要文案)`（跑腿通知同路径，未读计数自动生效）
- 摘要 = 日报标题 + 前 80 字；正文引导到「AI 应用 → 社区日报」查看
- 推送范围：`sys_user` 中该租户 `status='0'` 的用户（当前全平台仅 10 人，量级无忧；量大后可改分批/订阅制）

## 8. 前端页面（ui/src/pages/AI/DailyReport）

- 列表页：卡片流（日期 + 标题 + 内容摘要），分页；顶部管理员可见「立即生成」按钮
- 详情：点击卡片进入当日全文（Markdown 渲染，复用现有 markdown 渲染组件如有）
- 菜单：`AI 应用`（2112）下新增 C 型菜单「社区日报」（menu_name 中文直存，icon 用现有 `icon-ai`，SQL 追加到 init 文件）

## 9. 涉及文件清单

| 端 | 文件 |
|---|---|
| Java 新增 | `twelvet-ai-server/.../controller/AiReportController.java`、`service/IReportService.java` + impl、`task/AiDailyReportQuartzTask.java`、`mapper/DailyReportMapper.java` + XML、`domain/DailyReport.java`、日报聚合 DTO |
| Python 新增 | `app/routers/assist.py` 增加 `daily-report` 端点、`app/services/product_copywriting.py` 增加日报 prompt 构建（纯逻辑）、`tests/test_daily_report.py` |
| SQL | `backend/data/twelve-ai-report-init.sql`（表 + sys_job + 菜单） |
| 前端 | `ui/src/pages/AI/DailyReport/index.tsx`、`ui/src/services/ai.ts`（新增接口封装）、路由 config |

模块依赖注意：twelvet-ai-server 需新增 `twelvet-im-server` 依赖（同 runner 的做法）以调用 IImService。

## 10. 验证计划

1. 手动 `POST /ai/report/generate`：生成成功，`stats_json` 指标与 DB 手工 count 一致，重复调用覆盖不重复插行
2. LLM 正常出文案（feature=daily-report 入 `ai_usage_log`）；临时改错 api_key → fallback 文案 + `source='fallback'`
3. 推送：admin/demo 各收到一条 system 消息，铃铛未读 +1；`im_message`/`im_unread` 落库正确
4. 多租户：default 与 thu 各自生成内容互不可见（列表租户隔离）
5. 单测：prompt 构建与指标序列化纯逻辑 mock 测试

## 11. 工作量拆分（供子代理任务切分）

- **后端代理**：Java 全部 + SQL + Python 端点 + 单测 + 构建自测（预计为最大块）
- **前端代理**：页面 + 路由 + 菜单 SQL（可与后端并行，先 mock 数据）

## 12. 风险与遗留

- LLM 输出稳定性：JSON 结构化输出（返回 {title, content}）失败率低，但仍保留模板兜底
- 配额：free 租户 50 次/日内日报占 2 次，占比可控；若未来日报租户数暴涨需独立配额通道
- 时区：统计区间用服务器本地时间；`sys_job` cron 同
