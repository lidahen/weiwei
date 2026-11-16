# AI 初审 与 拍图发布助手 设计

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 状态：已实现。两个功能共享 ai-service 的 VISION 模型配置与 `ai_usage_daily` 配额账本。
> 原则沿用全站 AI 设计：**Java 只做身份/状态机，Python 做模型调用，任何 AI 故障静默降级为人工流程**。

---

## 1. AI 初审（商品 / 动态）

### 1.1 定位

管理后台的商品审核（`draft → auditing → onsale/rejected`）与动态审核（`audit_status: pending/approved/rejected`）目前全靠人工。AI 初审在**人工之前**插入一道机器审查：

| AI 结论 | 处理（`ai.moderation.auto-action=true` 时） | 说明 |
|---|---|---|
| low（低风险） | **自动通过**：商品置 `onsale`，动态置 `approved` | 写 `product_audit_log`（auditor_id=0 = AI） |
| high（高风险） | **自动驳回**：商品置 `rejected` + 驳回理由，动态置 `rejected` + 理由 | **IM 通知卖家/发布者**（system 通道），理由可申诉 |
| medium（存疑） | **仅标注**，留在待审队列 | 管理员在审核页看到 AI 标签与理由，人工裁决 |

`ai.moderation.auto-action=false` 时 AI 只标注不动作（灰度期默认建议）。
`ai.moderation.enabled=false`（或任务未注册）时完全不跑，等效现状。

### 1.2 审查内容（规则 + LLM 双层）

- **确定性规则（先跑，命中即定级，不依赖模型）**：
  - 硬违禁关键词（毒品/枪支/管制刀具/色情/代孕等）→ 直接 high；
  - 引流与站外交易（微信号/QQ/二维码占位/电话号码/"私聊转账"）→ 至少 medium；
  - 假冒品牌话术（"高仿/复刻/1:1"）→ 至少 medium。
- **LLM 结构化审查**（文本）：输入标题/描述/类目/价格/成色，输出 JSON
  `{risk_level, violations:[{type,detail}], summary}`，判定口径：违禁品与明显欺诈=high，
  疑似引流/图文存疑/夸大宣传=medium，正常二手=low。
- **视觉审查（可选）**：配置了 VISION 模型且商品带图时，取前 3 张图问多模态模型：
  是否含二维码/联系方式/水印/盗图/图文不符。二维码或联系方式 → high，其余问题 → medium。
  VISION 未配置或调用失败 → 跳过图片审查（纯文本结论照常）。

最终 `risk_level` 取 规则 vs LLM vs 视觉 的最高档。

### 1.3 ai-service 接口

`POST /ai/moderation/content`（Java 代理层传 `X-Tenant-Id` / `X-User-Id`）：

```json
// 请求（camelCase）
{ "bizType": "product",            // product | post
  "title": "iPhone 14 Pro 95新", "description": "…", "categoryName": "数码/手机",
  "price": 4999, "condition": "95",
  "images": ["/upload/xxx.jpg", "https://…"],   // 相对路径自动拼 static_base_url
  "userId": "1024" }
// 响应 data
{ "riskLevel": "high", "action": "auto_reject",   // auto_pass / manual / auto_reject
  "violations": [{"type":"banned_item","detail":"描述出现管制刀具相关词"}],
  "summary": "…", "modelName": "deepseek-chat" }
```

- 配额：`check_and_count` 事前检查 + `log_usage(feature='moderation')` 事后计量；
- LLM/异常：模型未配置或上游失败返回 `code=500`，Java 侧**跳过该条留待人工**，绝不误伤。

### 1.4 Java 侧（twelvet-product-server，商品与动态同属该模块）

```
Quartz: productAiModeration.auditProducts (每10分钟)   productAiModeration.auditFeeds (每10分钟, 错峰5分)
  │  ProductAiModerationMapper.selectAuditingProducts(limit)   -- 平台视角查全租户
  │  逐条: TenantContext.set(tenantId) → AiModerationClient.moderate(…) → 写 ai_moderation_log
  │        ├ low  + autoAction → ProductMapper.updateStatus(onsale) + product_audit_log(pass,'ai_auto')
  │        ├ high + autoAction → updateStatus(rejected, AI理由) + audit_log(reject) + IImService 通知卖家
  │        └ medium / AI失败   → 只留标注（失败记 action=error）
  │  幂等：已有非 error 初审记录的商品跳过；error 后 30 分钟内重试，累计 3 次放弃转人工
  └  finally TenantContext.clear()
```

- 新表 `ai_moderation_log`（见 init SQL）：一次初审一行，含 risk_level/action/violations(JSON)/summary/model_name/error_msg；
- 管理端列表：`selectProductAuditList` / `selectPendingFeeds` LEFT JOIN 最新一条初审结果
  （`aiRiskLevel/aiAction/aiSummary/aiViolations`），并支持 `aiRisk=flagged`（仅看 medium/high）与 `auto`（仅看已自动处理）筛选；
- VISION 模型：`ai_model` 表新增 `model_type='VISION'` 用法（model_config 页面可直接配置，无枚举校验）。

---

## 2. 拍图发布助手 + AI 估价

### 2.1 AI 识图填单 `POST /ai/assist/product-recognition`

- 入参 `{images: [url|相对路径]（1-5张）, hint?: "用户补充说明"}`；
- Python 将 `product_category_dict` 全量类目以 `code:name(parent)` 文本注入提示词，
  要求多模态模型**从字典中选 code**，并产出：`categoryL1/categoryL2`（code+name）、`title`（≤60字）、
  `description`（100-300字，只依据图片可见信息，不编造）、`condition`（brand_new/99/95/90/80/70_lower）；
- VISION 模型未配置 → `code=500`（前端提示"管理员未配置视觉模型"）；模型输出不合法字段自动剔除；
- 计量 feature=`product-recognition`。

### 2.2 AI 估价 `POST /ai/assist/product-valuation`

**纯统计、不靠 LLM 编价格**（价格数字必须来自真实成交，LLM 只在类目缺失时帮忙分类）：

```
样本：trade_order(status='done', 近180天) JOIN product 取类目与成交价 sku_price
      → 同 cat_l2 样本 < 5 时回退同 cat_l1
在售：product(status='onsale') 同类目价格分布（当前市场挂价参考）
输出：p25 / p50 / p75 / 样本数 + 成色系数调整后的建议区间 [low, high]
```

- 成色系数（纯逻辑，可单测）：brand_new ×1.15，99 ×1.08，95 ×1.0，90 ×0.92，80 ×0.8，70_lower ×0.6；
- `categoryL1` 未传时：LLM 按标题分类到字典（失败/未配置则返回 400"请先选择品类"）；
- 样本为 0：返回 `sampleCount=0` + 在售区间（若有），说明文案如实标注"近 180 天无成交样本"；
- 计量 feature=`product-valuation`。

### 2.3 前端（ui）

- **发布页 `ProductPublish.tsx`**：
  - AI 按钮组新增「AI 识图填单」（无图时禁用）：取已上传图片调识别接口，**只回填空字段**
    （类目/成色/标题/描述），已填写的字段不覆盖，message 提示回填数量；
  - 售价字段下方「AI 估价」入口：按当前 标题/类目/成色 调估价接口，展示
    「AI 估价：¥x ~ ¥y · 中位 ¥z · 近180天成交 N 件」与一句说明；
- **管理端 `ProductAudit.tsx` / `FeedAudit.tsx`**：新增「AI 初审」列（风险 Tag + Tooltip 展示
  违规点与 summary，AI 自动通过/驳回也有标识），搜索栏支持「仅看 AI 标疑 / AI 已自动处理」筛选。

---

## 3. 配置与上线

| 配置 | 默认 | 说明 |
|---|---|---|
| `ai.moderation.enabled` | true | 总开关（Quartz 任务内检查） |
| `ai.moderation.auto-action` | true | low 自动过 / high 自动驳；false 只标注 |
| `ai.moderation.batch-size` | 50 | 单轮最多处理条数（防首轮积压拖垮任务） |
| `ai_model.model_type='VISION'` | 无 | 多模态理解模型（OpenAI 兼容 chat 接口），未配则图片审查与识图填单不可用 |

上线步骤：执行 `backend/data/ai-moderation-init.sql`（建表 + 2 个 sys_job + VISION 模型示例行）→
重启 backend →（建议先 `ai.moderation.auto-action=false` 跑几天看标注准确率）→ 打开自动动作。

回滚：`DELETE FROM sys_job WHERE job_name IN ('AI初审-商品','AI初审-动态'); DROP TABLE ai_moderation_log;`
（功能为旁路，回滚后审核流退回纯人工，无残留状态。）

## 4. 边界与不做的事

- 不做全自动"申诉复核"、"自动封号"——自动驳回仅针对单条商品/动态，且理由可追溯（表里有模型与违规明细）；
- 初审不改写 `product_draft`，驳回后卖家仍可编辑重新提交（现有编辑链路原样）；
- 估价不使用任何 LLM 生成价格数字；样本不足时如实说"无成交样本"，不编造区间；
- 所有 AI 调用走 `ai_usage_daily` 租户账本；初审调用身份为租户（X-Tenant-Id），计入租户配额。
