# 密钥管理与轮换 SOP

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 配套文档：[可交付落地改造设计文档](superpowers/specs/2026-10-13-delivery-readiness-design.md)（决策 D2）
> 状态：**待执行**。清单本身已可用（不含任何密钥值）。
> 本文档**严禁写入任何真实密钥值** —— 提交前必须过一遍本文附录 A 的扫描命令。

---

## 1. 为什么必须轮换（而不是删文件）

- `ai-service/.env` 被 git 跟踪，且在 **5 个提交**里被修改过（首个提交 `07d6bb4` 的说明就是 "track .env and FAISS indexes (private repo)"，最近一次 2026-09-30）。
- 同一组阿里云 OSS AK/SK 在**至少 3 处**重复出现。
- ~~`deploy/部署交接手册.md`~~ 曾以表格形式**再列一遍**生产 MySQL/Redis 口令与 OSS AK/SK（该文件已于 2026-10-21 随 `deploy/` 删除）。

**结论：`git rm --cached` 只能让工作区变干净，历史里的值仍然存在。泄漏已经发生，所有凭据必须视为已失陷 —— 唯一有效的止血是轮换。**

> 是否额外清洗 git 历史是**独立可选项**（见设计文档 D2）。轮换之后历史里的旧值已失效，残留风险趋近于零，因此轮换的优先级远高于清历史。

---

## 2. 凭据清单（不含值）

> 编号用于轮换时逐项打勾。**"位置"只写字段名与行号，不写值。**

| # | 凭据类型 | 存放位置 | 用途 | 轮换平台 | 已入 git 历史 |
|---|---|---|---|---|---|
| K1 | 阿里云 OSS AccessKeyId + SecretKey | ~~`deploy/部署交接手册.md`~~（已删除）、~~`deploy/application-pro.yml:108-109`~~（已删除）、`backend/twelvet-admin/src/main/resources/application.yml:198-199`、`ai-service/.env:32-33` | 图片/视频对象存储读写 | 阿里云 RAM 控制台 | ✅ 多处 |
| K2 | AI 对话模型 API Key | `ai-service/.env:6` | Agent / 导购 / 文案生成 | Agnes 平台 | ✅ |
| K3 | Embedding API Key | `ai-service/.env:11` | RAG 向量化 | SiliconFlow | ✅ |
| K4 | 商汤（SenseAudio）API Key | `ai-service/.env:24` | AI 音乐 | 商汤 | ✅ |
| K5 | SiliconFlow 音色克隆 Key | `ai-service/.env.example`（示例中为空，实际值可能在部署机 `.env`） | 音色克隆 | SiliconFlow | 待确认 |
| K6 | 生产 MySQL 口令（root 与从库） | ~~`deploy/部署交接手册.md:70`~~、~~`deploy/application-pro.yml:7,21`~~（均已删除）、`ai-service/.env:1` | 业务库 | MySQL | ✅ |
| K7 | 生产 Redis 口令 | ~~`deploy/部署交接手册.md:70`~~、~~`deploy/application-pro.yml:27`~~（均已删除）、`ai-service/.env:20` | 会话/缓存/Agent checkpointer | Redis | ✅ |
| K8 | 开发库口令（弱口令） | `backend/.../application-dev.yml:10,20,25`、`backend/docker/docker-compose.yml:28,69`（存根）、~~根 `docker-compose.yml`~~（已删除） | 本地开发 | MySQL/Redis | ✅ |
| K9 | OAuth 客户端 secret | `docs/db/twelvet-fast_2026-10-21.sql`（`sys_oauth_client_details`） | 登录 Basic 认证 | 自建 | ✅ |
| K10 | 预置账号口令哈希 | `docs/db/twelvet-fast_2026-10-21.sql`（`sys_user` 预置行） | 初始管理员与 demo 账号 | 自建 | ✅ |
| K11 | 前端硬编码 Basic 认证串 + 登录页预填口令 | `ui/src/pages/Login/service.ts:8`、`ui/src/pages/Login/index.tsx:198,253-257`（**已打进 `ui/dist`**） | 登录页 | 自建 | ✅ |
| K12 | 支付宝 / 微信支付密钥 | `backend/.../application-dev.yml:54-56,64`、~~`deploy/application-pro.yml`~~（已删除，当时为 `PLACEHOLDER_*`） | 支付（**当前未接通**） | 支付宝/微信商户平台 | ✅（占位值） |
| K13 | Jasypt 加密口令 | `backend/.../application.yml:215`（**当前为空**） | 配置加密 | — | ✅ |

---

## 3. 轮换顺序（关键：先建新、后吊销旧）

**不要"先删后建"** —— 那会造成一段时间的全线不可用。按以下顺序，每一步都可回退：

```
① 在各平台新建凭据（保留旧凭据有效，此时两套并存）
        ↓
② 只改「部署机上的运行时配置」（不碰代码、不碰 git）
        ↓
③ 重启服务 + 跑端到端冒烟（登录 / 上传图片 / AI 对话 / 下单 / 支付回调）
        ↓
④ 确认全部通过后，回到 ① 的平台吊销旧凭据
        ↓
⑤ 最后才做代码侧"出库"改造（设计文档 0.2），并提交
```

理由：②③ 阶段任何一步出问题，只要把配置改回旧值即可立即恢复；④ 之后旧凭据才真正失效。

---

## 4. 逐项操作与验证

| # | 操作 | 验证（必须看到预期输出） |
|---|---|---|
| K1 | 阿里云 RAM 新建 AK（**最小权限**：仅该 bucket 的读写，不要给账号级权限） | 后端上传接口返回 200；`ui/dist` 中图片地址可访问；旧 AK 在控制台已禁用 |
| K2 | Agnes 平台新建 Key | 调 `/ai/chat` 返回正常回答（非 401） |
| K3 | SiliconFlow 新建 Key | 知识库检索有命中；日志无 401 |
| K4 | 商汤新建 Key | AI 音乐生成任务能提交并轮询到结果 |
| K6/K7 | MySQL/Redis 改强口令 | 后端登录 200；Agent 会话可跨轮保持（checkpointer 生效） |
| K8 | 开发库改回**本机默认**（见设计文档 D3） | 新人 `mvn spring-boot:run` 直接能起（不依赖隧道） |
| K9/K10 | 移除写死 secret；初始口令改为**首登强制修改** | 新库初始化后，用初始口令登录被要求改密 |
| K11 | 删除登录页预填与硬编码 Basic；重新构建 dist | `ui/dist` 中搜不到 demo 账号与认证串；登录页输入框为空 |
| K12 | 拿到真密钥后替换 `PLACEHOLDER_*` | 支付回调能收到且验签通过（**当前是"下单必失败"，属已知未接通项**） |
| K13 | 删除 Jasypt 依赖或用外部传入口令真正启用（**当前是"看起来加密了"的空口令中间态**） | 以 `pro` profile 启动不再依赖字面量 `ENC(PASSWORD)` |

---

## 5. 代码侧"出库"改造（第 ⑤ 步，对应设计文档 0.2）

统一约定：**单一来源 = 环境变量**，三端各自读取。

| 端 | 改造 |
|---|---|
| backend | `application*.yml` 中敏感项改为 `${ENV_VAR}`（**无默认值**）；`application-local.yml` 入 `.gitignore` |
| ai-service | `.env` 取消跟踪并删除；`app/config.py` 关键字段缺失时**启动即失败**（当前 82 个字段全有默认值，配错不报错） |
| ui | 删除硬编码 Basic 与预填口令；需要构建期变量的走环境注入 |
| 仓库 | 删除 `.gitignore` 中"intentionally tracked"的注释；`.env.example` 与代码读取字段**逐项对齐** |

**注意**：`ai-service` 的 `.env.example` 目前缺 60 个字段，且示例里的模型供应商已过期（照抄会静默切到另一个供应商）—— 对齐工作必须做，否则运维会漏配。

---

## 6. 轮换后的验收（A1 标准）

```powershell
# 1) 工作区与历史里都不应再出现真实密钥特征
git -C F:\code\weiwei log --all -p -- ai-service/.env | Select-String 'sk-[A-Za-z0-9]{20,}|LTAI[A-Za-z0-9]{10,}'
#    期望：无输出（历史里若有，说明尚未清历史 —— 但此时旧值已因轮换而失效）

# 2) 工作区不得有任何被跟踪的凭据文件
git -C F:\code\weiwei ls-files | Select-String '(^|/)\.env$'
#    期望：无输出

# 3) 全仓扫描（含文档），确保没有把密钥写进任何提交物
Select-String -Path F:\code\weiwei\*.md,F:\code\weiwei\docs\*.md,F:\code\weiwei\deploy\*.md `
  -Pattern 'sk-[A-Za-z0-9]{20,}|LTAI[A-Za-z0-9]{10,}' -ErrorAction SilentlyContinue
#    期望：无输出

# 4) 端到端冒烟
#    登录 200 → 上传图片成功 → AI 对话有回答 → AI 导购下单成功（且租户归属正确）→ 支付回调可达
```

---

## 7. 事故预案

| 症状 | 原因 | 处置 |
|---|---|---|
| 某功能突然 401/403 | 该处配置漏改（第 ② 步覆盖不全） | 把该处配置改回旧值 → 恢复 → 重做该步 |
| 服务启动失败 | 敏感项改为 `${ENV}` 后无默认值，而环境变量未注入 | 检查 systemd `Environment=` / compose `environment:` 是否补齐 |
| Agent 会话丢失 | Redis 口令改后 checkpointer 连不上，回退内存版 | 检查 `REDIS_URL`；确认 redis-stack 可连 |
| 上传图片 403 | OSS 新 AK 权限不足（未给 bucket 读写） | RAM 策略补 bucket 级权限 |

---

## 附录 A · 提交前扫描命令（建议进 `verify.sh`）

```powershell
# 对即将提交的内容做密钥扫描；命中即拒绝提交
$patterns = 'sk-[A-Za-z0-9]{20,}', 'LTAI[A-Za-z0-9]{10,}', 'AKID[A-Za-z0-9]{10,}',
            'password\s*[:=]\s*["'']?[^\s"'']{6,}', '-----BEGIN [A-Z ]*PRIVATE KEY-----'
$targets = git -C F:\code\weiwei diff --cached --name-only
foreach ($f in $targets) {
  if (Test-Path $f) {
    $hit = Select-String -Path $f -Pattern $patterns -ErrorAction SilentlyContinue
    if ($hit) { "❌ $f 命中密钥特征（行 $($hit.LineNumber -join ','))" }
  }
}
```

**本文档起草时曾把两个生产口令原文抄入，被上述扫描拦下。这条纪律对文档同样适用。**
