# Weiwei 可交付落地 · 阶段 A（可复现）实施计划

> 设计依据：[可交付落地改造设计文档](../specs/2026-10-13-delivery-readiness-design.md)
> 范围：**阶段 A（可复现）**。
> **2026-10-13 优先级变更（用户决定）**：安全与凭据相关任务整体推迟到**阶段 D**；本文件原 T8/T9/T10 已移出本阶段，`T7` 拆为 **T7a**（可复现部分，留在本阶段）与 **T7b**（凭据出库，移入阶段 D）。
> 执行顺序：**A 可复现 → B 可信 → C 可运营 → D 安全与凭据**。
> 纪律：不写任何真实密钥值；每步都必须有"运行命令 → 读取输出"的验证证据。
>
> **⚠️ 2026-10-21 注**：本计划中提到的 `docs/verify-baseline-2026-10-13.txt` 与
> `docs/verify-after-stageA-gate-2026-10-13.txt` 两份一次性门禁输出快照已删除
> （合计 308KB，可用 `bash verify.sh` 随时重新生成）。下文相关步骤按"当时已执行"阅读。

---

## 0. 阶段目标与完成定义

**目标**：让三端质量门禁从"今天全是红的"变成"一条命令全绿"，并让新人能在干净机器上把三端跑起来。
（凭据止血按用户决定移至阶段 D，本阶段不做。）

**完成定义（全部满足才算阶段 A 完成）**

```bash
cd /f/code/weiwei && bash verify.sh      # Windows Git Bash 用法
# 期望：5 项检查全部 PASS，退出码 0
#   1) backend: mvn -B -o test             → BUILD SUCCESS
#   2) backend: spring-javaformat:validate → PASS
#   3) ui: tsc --noEmit                    → 0 error
#   4) ui: jest --ci                       → 全绿
#   5) ai-service: pytest tests -q         → 0 error / 无静默 skip
# 密钥扫描：故意未纳入（阶段 D 再接入）
```

**当前红灯基线（2026-10-13 实测，作为改进前证据留档）**

| 检查 | 现状 |
|---|---|
| `mvn -o test` | ✅ 通过，但仅 10 个用例（1 文件） |
| `spring-javaformat:validate` | ❌ 3 个文件违规 |
| `tsc --noEmit` | ❌ 18 个错误 / 9 个文件 |
| `jest --ci` | ❌ 1 failed / 1 passed |
| `pytest tests -q` | ❌ 收集即失败（9 errors） |

---

## 1. 任务总览与依赖顺序

```
T1 verify.sh 骨架（先建度量，再改东西）   ← 脚本已建，基线已留档
      │
      ├── T2 ai-service 测试可运行（依赖声明 + pytest 配置）
      │        └── T3 消除 4 个静默 skip
      ├── T4 backend 格式违规（3 文件）
      ├── T5 ui tsc 18 错（含 openDetail 真缺陷）
      │        └── T6 ui jest 红
      └── T7a dev 配置与生产库解耦（仅"可复现"部分）
              │
              └── T11 verify.sh 收口

【按用户决定移入阶段 D（安全与凭据，最后做）】
  T0   密钥轮换（人工执行，见 密钥管理与轮换SOP.md）
  T7b  dev 配置的"凭据出库"部分
  T8   凭据出库（.env 取消跟踪 + 环境变量注入 + example 对齐）
  T9   删 UI 登录页预填凭据与硬编码 Basic 串
  T10  初始化 SQL 去预置口令
```

> **阶段 A 还需补做（原属"阶段 1"，但它是"可交付"的核心，故上提）**：补 `backend/Dockerfile`、`ui/Dockerfile` 并修根 `docker-compose.yml`（一键起全套）、Flyway baseline（107 表导出）、README/ONBOARDING/CLAUDE.md 对齐真实结构、`ui` 锁定依赖、**新增 `.gitattributes` 固定行尾策略**（T4 发现：无该文件且 `core.autocrlf=true` 时，行尾取决于各机器本地配置）。详见设计文档阶段 A；本文件先覆盖"门禁变绿"这条主线，上述内容作为阶段 A 第二批任务展开。

---

## 2. 任务详情

### T1 · 建立 `verify.sh`（先有度量）

**Files:**
- Create: `verify.sh`（仓库根）

**背景**：现状没有统一入口，三端各跑各的、命令分散在 `backend/.github/workflows/test.yml`、`ui/Jenkinsfile`、`deploy/` 手册里，且都不可用（远端是 Gitee，Actions 不触发）。先建立一个**平台无关**的入口。

- [ ] **Step 1: 写脚本，先只做"报告"不改代码**

  要求（每条都要在脚本里落实）：
  - 任一检查失败 → **记录失败原因并继续跑完剩余检查**（要一次看清全部红灯，而不是修一个跑一次）
  - 最后统一汇总，**有失败则 `exit 1`**
  - 每项打印"正在跑什么 + 结果"

- [ ] **Step 2: 运行它，记录当前基线**

  ```powershell
  cd F:\code\weiwei; .\verify.sh
  # 期望（当前）：backend test PASS / javaformat FAIL / tsc FAIL / jest FAIL / pytest FAIL，退出码 1
  ```

- [ ] **Step 3: 把这次输出存为改进前证据**
  - 存 `docs/verify-baseline-2026-10-13.txt`

---

### T2 · 让 ai-service 测试能跑完

**Files:**
- Create: `ai-service/pytest.ini`
- Modify: `ai-service/requirements.txt`、`ai-service/requirements.lock.txt`

**背景（实测）**：`py -3 -m pytest tests -q` → `Interrupted: 9 errors during collection`：
- 3 × `ModuleNotFoundError: respx`（`tests/music/test_senseaudio_universal.py:5` 等）
- 6 × `Form data requires "python-multipart"`（已写在 `requirements.txt:3`，但当前解释器未装）
- 另有 4 × `No module named 'fakeredis'`（`tests/test_redis_persistence.py:32`）
- **`respx` 与 `fakeredis` 两个测试依赖根本未声明在任何 requirements 文件里**
- `requirements.lock.txt` 缺 8 个直接依赖（`python-multipart`、`redis`、`langgraph-checkpoint-redis`、`yt-dlp`、`scenedetect`、`opencv-python-headless` 等）

- [ ] **Step 1: 写 `pytest.ini`**

  ```ini
  [pytest]
  asyncio_mode = auto
  testpaths = tests
  addopts = -q
  ```

  说明：`asyncio_mode = auto` 是消除 T3 那 4 个静默 skip 的关键。

- [ ] **Step 2: 声明测试依赖**（新增 `requirements-dev.txt`，避免污染生产依赖）

  内容至少包含：`pytest`（固定版本）、`pytest-asyncio`、`respx`、`fakeredis`。

- [ ] **Step 3: 运行，确认收集阶段不再报错**

  ```powershell
  cd F:\code\weiwei\ai-service
  py -3 -m pip install -r requirements-dev.txt
  py -3 -m pytest tests -q
  # 期望：不再有 "errors during collection"；用例真正开始执行
  ```

- [ ] **Step 4: 记录结果**
  - 若仍有 error，逐条记入 `docs/verify-baseline-2026-10-13.txt`

---

### T3 · 消除 4 个静默跳过的异步测试（假绿灯）

**Files:**
- Modify: `ai-service/tests/test_community_tools.py:61,66,70,78`

**背景**：这 4 个 `async def test_*` 没有 `@pytest.mark.asyncio`，在 strict 模式下被**静默跳过**（实测 `SKIPPED [4] ... no async plugin installed`）。其中 `:61,66,70` **正是租户隔离守卫测试**——"无租户上下文必须拒绝，绝不跨租户泄露"。

**这是本阶段安全价值最高的一步**：修好之前，租户越权防线没有任何回归保护。

- [ ] **Step 1: 运行，确认它们当前被 skip（RED）**

  ```powershell
  cd F:\code\weiwei\ai-service
  py -3 -m pytest tests/test_community_tools.py -v -rs
  # 期望（修之前）：4 个用例显示 SKIPPED，reason 含 "async def function"
  ```

- [ ] **Step 2: 依赖 T2 的 `asyncio_mode = auto`，或给 4 个方法补 `@pytest.mark.asyncio`**
  - 二选一，**不要两套并存**（本项目 `test_agent.py` 等用的是显式 marker，需统一约定后统一改）

- [ ] **Step 3: 运行，确认它们真的执行（GREEN）**

  ```powershell
  py -3 -m pytest tests/test_community_tools.py -v -rs
  # 期望：4 passed（或暴露真实失败——若有失败，说明租户隔离守卫本身有问题，必须当缺陷修）
  ```

- [ ] **Step 4: 记录 skip 总数**
  - 目标：`pytest` 输出的 skip 数**逐条可解释**（缺 DB 而 skip 是可接受的；"没装插件"不可接受）

---

### T4 · 修 backend 格式违规

**Files:**
- Modify: `backend/twelvet-framework/twelvet-framework-core/src/main/java/com/twelvet/framework/core/config/MessageSourceConfig.java`
- Modify: `backend/twelvet-framework/twelvet-framework-core/src/main/java/com/twelvet/framework/core/constants/BillingKeys.java`
- Modify: `backend/twelvet-framework/twelvet-framework-core/src/main/java/com/twelvet/framework/core/tenant/TenantSupport.java`

**背景**：这 3 个文件让 `spring-javaformat:validate` BUILD FAILURE —— 而它是现有 CI 的**第一条命令**。即"CI 即使跑起来也会立刻红"。

- [ ] **Step 1: 确认 RED**

  ```powershell
  cd F:\code\weiwei\backend
  & 'F:\apache-maven-3.9.9\bin\mvn.cmd' -B -o spring-javaformat:validate
  # 期望：BUILD FAILURE，列出上述 3 个文件
  ```

- [ ] **Step 2: 应用格式化**

  ```powershell
  & 'F:\apache-maven-3.9.9\bin\mvn.cmd' -B -o spring-javaformat:apply
  ```

- [ ] **Step 3: ★ 逐一 review diff（不可盲改）**

  ```powershell
  git -C F:\code\weiwei diff -- backend
  # 期望：仅空白/换行/缩进类改动；若出现语义改动 → 停下，手动修正
  ```

  理由：`TenantSupport` 是**租户隔离的关键类**（`writeTid()` 的语义见设计文档 D5），对它的自动改动必须人眼确认。

- [ ] **Step 4: 确认 GREEN + 编译测试不受影响**

  ```powershell
  & 'F:\apache-maven-3.9.9\bin\mvn.cmd' -B -o spring-javaformat:validate
  & 'F:\apache-maven-3.9.9\bin\mvn.cmd' -B -o test
  # 期望：两条都 BUILD SUCCESS
  ```

---

### T5 · 修 18 个 `tsc` 错误（含一个已上线的真缺陷）

**Files:**
- Modify: `ui/src/pages/System/Tenant/index.tsx`（含 `openDetail` 真缺陷 + 6 个类型错误）
- Modify: `ui/src/app.tsx:264`、`ui/src/components/Footer/MarketplaceFooter.tsx:27`
- Modify: `ui/src/components/TwelveT/Dictionaries/DictionariesCheckbox/index.tsx:5`
- Modify: `ui/src/pages/Human/Dept/index.tsx:267`、`ui/src/pages/Log/Operation/index.tsx:44`
- Modify: `ui/src/pages/Tool/Code/Gen/components/EditCode/TagList.tsx:19`
- Modify: `ui/src/requestErrorConfig.ts:151,186`

**背景**：`max build` 不做类型检查，所以这 18 个错误一路进了生产。其中
`ui/src/pages/System/Tenant/index.tsx:352` 调用 `openDetail(row)`，而该函数**在本文件从未定义、也未 import**（全仓仅此一处引用）→ **租户管理页每行的「详情」按钮点击必抛 `ReferenceError`**。

- [ ] **Step 1: 确认 RED（先把 18 条抄进任务记录）**

  ```powershell
  cd F:\code\weiwei\ui
  node node_modules\typescript\bin\tsc --noEmit
  # 期望：18 个 error
  ```

- [ ] **Step 2: ★ 先修 `openDetail`——这是功能缺陷，不是类型问题（设计意图已查明，无需问人）**

  2026-10-13 核实：**租户详情弹窗早已存在，只缺这一个处理函数。**
  - 状态已定义：`index.tsx:90-93` `const [detailModal, setDetailModal] = useState<{visible: boolean; record?: SystemTenant.PageListItem}>({visible: false});`
  - 弹窗已实现：`index.tsx:510-543` `<Modal title={\`租户详情 — ${detailModal.record?.tenantCode}\`}>`，内含租户编码/名称/套餐/到期时间/商品配额/AI 配额 6 个 `Descriptions.Item`
  - 因此该按钮的语义是唯一确定的：把 `row` 塞进 `detailModal` 并显示

  修复形如（**纯 UI 状态函数，无接口调用**）：

  ```tsx
  const openDetail = (row: SystemTenant.PageListItem) => {
      setDetailModal({visible: true, record: row});
  };
  ```

  - **不允许**用 `@ts-ignore`、`any`、或删掉按钮来"消错"——那是把缺陷藏起来
  - 若实施时发现 `detailModal` 另有赋值点，先查清再改（说明还有第二处调用）

- [ ] **Step 3: 修剩余 17 个类型错误 —— ★ 修「类型标注」，不是修「调用方」**

  **关键判断依据（已核实，搞反会把正常代码改坏）**：
  - 全项目有 **154 处** `const {code, msg, data} = await xxxApi()` 的解构写法，`ui/src/services/ant-design-pro/aimusic.ts:14` 的注释也明确写着「`@umijs/max` 的 request 默认返回响应体 `{ code, msg, data }`」
  - → **运行时约定 = request 返回完整信封 `{code,msg,data}`**，调用方解构信封是**正确的**
  - → `Tenant/index.tsx:107,191,216` 报错的真正原因是 **service 的类型参数写错了**：把 **payload** 类型当成了 `request<T>` 的 `T`
    - `service.ts:95-103` `getAiUsage` 声明 `request<{days;rows;total}>`，实际运行时返回 `{code,msg,data:{days,rows,total}}`
    - `service.ts:55,39` `getRenewStatus` / `createRenewOrder` 声明的是 `RenewStatusResult` / `RenewOrderResult`（payload 类型），同样是把 payload 当成了 `T`
  - → **正确修法**：把这几处的类型参数改为信封形状（或统一用项目约定的 `request<{code:number;msg:string;data:T}>`）
  - → **AI 用量抽屉在运行时是正常工作的**（`const {data} = await getAiUsage(...)` 能取到值），修完类型后**不要**顺手"修正"调用方

  其余按各文件实际语义修：
  - `MarketplaceFooter.tsx:27` 用了不存在的 `serif` 字体键 → 补字体定义或改用已有键
  - `DictionariesCheckbox/index.tsx:5` 引入了 antd 已移除的 `CheckboxValueType` → 换等价类型
  - `app.tsx:264` `motionEaseIn` → 拼写应为 `motionEaseInOut`
  - `EditCode/TagList.tsx:19`、`Dept/index.tsx:267`、`Log/Operation/index.tsx:44`、`requestErrorConfig.ts:151,186` 按实际语义修

- [ ] **Step 4: 确认 GREEN**

  ```powershell
  node node_modules\typescript\bin\tsc --noEmit
  # 期望：0 error（退出码 0）
  ```

- [ ] **Step 5: 验证「详情」按钮真的能用**
  - 起前端（`yarn start`）+ 后端，打开租户管理页，点「详情」：
  - **期望**：弹出对应内容，控制台无 `ReferenceError`

---

### T6 · 修 ui jest 红

**Files:**
- Modify: `ui/src/pages/AI/ShoppingGuide/index.tsx:205,211`
- Modify: `ui/tests/setupTests.jsx`

**背景（实测）**：`jest --ci` → `1 failed, 1 passed`。失败原因 `ReferenceError: CSS is not defined`。
根因：`CSS.supports?.('height','100dvh')` 的 `?.` **只保护方法不存在，不保护 `CSS` 这个全局变量不存在**；jsdom 没有 `CSS` 全局。
真实浏览器都有 `CSS`，所以**生产大概率未受影响**，但防护写法本身有缺陷（意图是"检测是否支持 100dvh"）。

- [ ] **Step 1: 确认 RED**

  ```powershell
  cd F:\code\weiwei\ui
  node node_modules\jest\bin\jest.js --ci
  # 期望：Test Suites: 1 failed；报错 CSS is not defined
  ```

- [ ] **Step 2: 修防护写法（两处）**

  ```tsx
  // 改前：...(CSS.supports?.('height', '100dvh') ? {...} : {})
  // 改后：...(typeof CSS !== 'undefined' && CSS.supports?.('height', '100dvh') ? {...} : {})
  ```

- [ ] **Step 3: 补 jsdom 环境缺失的全局（`tests/setupTests.jsx`）**
  - 若第 2 步已让测试通过，此步仍建议保留（其他代码可能也会用 `CSS`）
  - **不要**用全局 mock 掩盖真实缺陷

- [ ] **Step 4: 确认 GREEN**

  ```powershell
  node node_modules\jest\bin\jest.js --ci
  # 期望：Tests: 2 passed
  ```

- [ ] **Step 5: 记录耗时**
  - 实测该套件耗时 **151.7 秒**（仅 2 个用例）→ 若纳入门禁会成为瓶颈，记录到计划后续项（可能需要 `max build` 缓存或调 jest 配置）

---

### T7 · dev profile 与生产凭据解耦（设计 D3）

**Files:**
- Modify: `backend/twelvet-admin/src/main/resources/application-dev.yml:1-3,8-10,20,24-25`
- Create: `backend/twelvet-admin/src/main/resources/application-local.yml.example`
- Modify: `F:\code\weiwei\.gitignore`

**背景**：`application-dev.yml` 是 pom 的 `activeByDefault`（`backend/pom.xml:541-548`），却**注释里要求先建到生产 IP 的 SSH 隧道、连接端口非标准、并内嵌生产口令**。而 `README.md:293-295,317` 与 `docs/ONBOARDING.md:99-105` 教的是本机 `3306` + 弱口令、无密码 `6379`。
→ **照文档做必然连不上；且新人可能误连生产库。** 这是 A4（干净机器可复现）的头号阻塞。

- [ ] **Step 1: 把 `application-dev.yml` 改为"本机默认值"**
  - DB：`localhost:3306`，用户名/口令走 `${DB_USERNAME:root}` / `${DB_PASSWORD:}`（**无生产值**）
  - Redis：`localhost:6379`，口令走 `${REDIS_PASSWORD:}`
  - 删除注释中的生产 IP、隧道端口、内网拓扑

- [ ] **Step 2: 新增 `application-local.yml.example`**
  - 给需要隧道/特殊端口的开发者用；真实 `application-local.yml` 加入 `.gitignore`

- [ ] **Step 3: 验证"dev 配置里没有生产凭据"**

  ```powershell
  Select-String -Path F:\code\weiwei\backend\twelvet-admin\src\main\resources\application-dev.yml `
    -Pattern '49\.232\.|13306|16379|password:\s*\S+'
  # 期望：无输出
  ```

- [ ] **Step 4: 验证本机可启动**
  - 起本地 MySQL/Redis（按 README），`mvn spring-boot:run -pl twelvet-admin`
  - **期望**：应用起得来，能登录；**不需要任何 SSH 隧道**

---

### T8 · 凭据出库（设计 D2，机制部分）

**Files:**
- Modify: `.gitignore`（删掉"intentionally tracked"注释）
- Delete from index: `ai-service/.env`（保留本地文件）
- Modify: `ai-service/.env.example`（补齐缺失字段）
- Modify: `backend/twelvet-admin/src/main/resources/application.yml:198-199`
- Modify: `deploy/application-pro.yml:7,21,27,108-109`

> **前提**：T0 轮换必须先完成或并行，否则出库后旧密钥仍可用 = 没止血。

- [ ] **Step 1: 取消跟踪（保留本地文件）**

  ```powershell
  git -C F:\code\weiwei rm --cached ai-service/.env
  ```

- [ ] **Step 2: `ai-service/.env` 加入 `.gitignore` 并删掉误导性注释**

- [ ] **Step 3: 三端敏感项改为环境变量注入**
  - backend：`${OSS_ACCESS_KEY:}` 等（**无默认值**）
  - ai-service：`app/config.py` 关键字段**缺失即启动失败**（当前 82 个字段全有默认值，配错不报错）
  - 部署机：systemd `Environment=` / compose `environment:` 注入

- [ ] **Step 4: `.env.example` 与代码字段逐项对齐（已量化，可机械执行）**

  2026-10-13 实测（脚本比对 `app/config.py` 字段 vs `.env.example` 键名）：

  | 指标 | 数值 |
  |---|---|
  | `Settings` 字段总数 | **82** |
  | `.env.example` 键数 | 22 |
  | `.env`（实际在用的）键数 | 20 |
  | example 缺失字段数 | **60** |
  | **代码里"没有默认值"的字段数** | **0** |

  - **60 个缺失字段中最危险的一批**（缺失 + 默认值为空 → **静默失效，不报错**）：
    `REDIS_URL`（默认 `redis://localhost:6379/0`）、`OSS_ACCESS_KEY` / `OSS_SECRET_KEY`（默认空串 → 上传必 401）、`TTS_API_KEY` / `ASR_API_KEY` / `TENCENT_LBS_KEY`（默认空串 → 功能静默不可用）、`APP_HOST` / `APP_PORT`（**全仓无人读取，是死配置**）。
  - 另有 example 取值已过期：示例里是 DeepSeek，而代码默认已切 Agnes → **照抄 example 会静默换掉模型供应商**。
  - **★ 关键结论：82 个字段全部有默认值、0 个必填**，所以"配错不报错"是**结构性**的。仅对齐 example 无法解决——必须**显式定义必填字段集合**（见阶段 2 任务 2.5）。
  - 交付物：example 覆盖全部 82 个字段的键名（值可留空或写安全默认），并标注哪些是必填。

- [ ] **Step 5: 验证（A1 标准）**

  ```powershell
  git -C F:\code\weiwei ls-files | Select-String '(^|/)\.env$'
  # 期望：无输出

  git -C F:\code\weiwei log --all -p -- ai-service/.env | Select-String 'sk-[A-Za-z0-9]{20,}|LTAI[A-Za-z0-9]{10,}'
  # 期望：无输出（若有 → 旧值仍在历史里；此时应已因 T0 轮换而失效）
  ```

---

### T9 · 删除 UI 登录页预填凭据

**Files:**
- Modify: `ui/src/pages/Login/index.tsx:198,253-257`
- Modify: `ui/src/pages/Login/service.ts:7-8`
- Rebuild: `ui/dist`（**当前产物里已包含 `user_demo`**）

**背景**：登录页表单 `initialValues` 预填 demo 账号与弱口令，并有一处硬编码 Basic 认证串；`ui/dist`（2026-10-08 构建）中已 grep 到 `user_demo`。公网站点等于把凭据直接摆在登录框里。

- [ ] **Step 1: 删除 `initialValues` 预填与快捷登录里的硬编码口令**
- [ ] **Step 2: 处理客户端 Basic 认证串**
  - 若为 OAuth 客户端凭据，改为**构建期环境注入**（不进源码）
  - 若后端可改为公开客户端，则移除
- [ ] **Step 3: 重新构建并验证产物干净**

  ```powershell
  cd F:\code\weiwei\ui; yarn build
  Select-String -Path F:\code\weiwei\ui\dist\**\*.js -Pattern 'user_demo|Basic [A-Za-z0-9+/=]{12,}' -ErrorAction SilentlyContinue
  # 期望：无输出
  ```

- [ ] **Step 4: 人工验证登录页**
  - **期望**：用户名/口令输入框为**空**

---

### T10 · 初始化 SQL 去预置口令

**Files:**
- Modify: `backend/data/twelvet.sql:738,1023-1030`
- Modify: `backend/data/neighborswap.sql:637-642`
- Modify: `backend/README.md:39`

**背景**：初始化 SQL 里写死了 `admin`/`demo` 的预置口令哈希（两账号**同一哈希**）、一条明文 OAuth client secret；`neighborswap.sql` 的 4 个 demo 账号（买/卖/运营/客服）**共用一个哈希**，注释即写明口令；README 还对外宣传默认口令。

- [ ] **Step 1: 移除 demo 账号与其预置口令**
- [ ] **Step 2: `admin` 改为"首次登录强制改密" → ★ 明确移出阶段 0**

  2026-10-13 核实：`sys_user` 表**没有**任何"口令最后修改时间"或"是否必须改密"字段（全库 grep `pwd_update_date` / `must_change_password` / `password_update` 零命中，`twelvet.sql:1007` 只有 `login_date`）。
  → 强制改密需要**新增字段 + 后端登录流程改造 + 前端改密页跳转**，属独立功能，**不在阶段 0**（阶段 0 只做"不再把口令写进初始化脚本"）。
  → 本步只登记为后续待办，不在本阶段实现。
- [ ] **Step 3: OAuth client secret 改为随机值 + 支持轮换**
- [ ] **Step 4: README 去掉默认口令宣传**
- [ ] **Step 5: 验证**

  ```powershell
  Select-String -Path F:\code\weiwei\backend\data\*.sql -Pattern 'INSERT INTO `?sys_user`?|password\s*[:=]|client_secret'
  # 期望：不再出现预置口令哈希或明文 secret
  ```

---

### T11 · `verify.sh` 收口 + 接入密钥扫描

**Files:**
- Modify: `verify.sh`

- [ ] **Step 1: 把 T2–T10 的最终命令固化进脚本**
  - 5 项检查：backend test / javaformat validate / tsc / jest / pytest
- [ ] **Step 2: 接入密钥扫描（附录 A 的命令，来自轮换 SOP）**
  - 扫 `git diff --cached` 的改动文件 + 仓库内所有 `.md`
- [ ] **Step 3: 全量运行，确认全绿**

  ```powershell
  cd F:\code\weiwei; .\verify.sh
  # 期望：5/5 PASS + 密钥扫描 PASS，退出码 0
  ```

- [ ] **Step 4: 存证据**
  - `docs/verify-after-stage0-2026-10-13.txt`

---

## 3. 阶段 0 验收清单

```
□ T1  verify.sh 可运行，能一次列出全部红灯
□ T2  pytest 不再 "errors during collection"
□ T3  4 个异步测试真实执行（含 3 个租户隔离守卫）；skip 数逐条可解释
□ T4  spring-javaformat:validate PASS（且 diff 已人工 review）
□ T5  tsc 0 error；「租户管理→详情」按钮实测可用
□ T6  jest 全绿
□ T7  application-dev.yml 无生产凭据；本机无需隧道即可启动
□ T8  .env 未被跟踪；三端敏感项走环境变量；example 与代码字段一致
□ T9  ui/dist 中无 demo 账号与认证串；登录页输入框为空
□ T10 初始化 SQL 无预置口令；README 无默认口令
□ T11 verify.sh 5/5 PASS + 密钥扫描 PASS（附真实输出）
□ T0  凭据已轮换（人工，附完成记录）
```

---

## 4. 风险与注意事项

| 风险 | 处置 |
|---|---|
| T5 的 `openDetail` 设计意图不明 | **停下来问用户**，不自行发明交互 |
| T4 自动格式化的 diff 触及 `TenantSupport` | 必须人工 review；发现语义改动则手动修正 |
| T3 修好后测试**真的失败**（守卫本身有问题） | 视为**真实缺陷**处理，不得改测试迁就实现 |
| T2 装 `requirements-dev.txt` 污染生产依赖 | 独立文件，生产仍只装 `requirements.txt`（阶段 1 再收口 lock） |
| T8 出库后部署机漏配环境变量 | 按轮换 SOP 的"先建新→改配置→验证→再吊销"顺序，保留回退窗口 |
| jest 单套件 151.7s 拖慢门禁 | 记录为后续优化项（阶段 1） |

---

## 5. 与后续阶段的边界

以下**明确不在**阶段 0（避免范围失控）：

- Flyway / baseline（阶段 1，设计 D4）
- `backend/Dockerfile`、`ui/Dockerfile`、compose 修复（阶段 1，D6）
- 文档结构重写（README/ONBOARDING/CLAUDE.md 对齐真实结构）（阶段 1）
- 锁定 `ui` 依赖（阶段 1）
- 健康探针、可观测、优雅停机、Agent 下单改造、ai-service 鉴权（阶段 2，D5/D7/D8/D9）
- 限流、验证码、Sa-Token 策略、Knife4j 可关闭、备份演练（阶段 3）

---

## 6. 执行记录

### 2026-10-13 · T1 完成 ✅

**产出**：`verify.sh`（仓库根）——三端统一门禁，失败不中断、一次跑完，工具缺失计为失败（门禁不因"跑不起来"而变绿）。
**留档**：`docs/verify-baseline-2026-10-13.txt`（209KB，已过密钥扫描，无命中）

基线实测（改动前）：

```
汇总（总耗时 73s）
  ✅ backend 单元测试 (mvn test)                    7s
  ❌ backend 格式校验 (spring-javaformat:validate)  exit 1, 3s
  ❌ ui 类型检查 (tsc --noEmit)                     exit 2, 8s
  ❌ ui 单元测试 (jest --ci)                        exit 1, 46s
  ❌ ai-service 测试 (pytest -q)                    exit 2, 9s
 结果：存在失败项 ❌
```

工程细节：Windows/Git Bash 下优先调 `mvn.cmd`（规避 `deploy/mvnw.sh` 记录的 classworlds 故障）；默认 `-o` 离线（本机 m2 仓库在 `F:\apache-maven-3.9.9\mvn_repo`），可用 `MVN_FLAGS=""` 覆盖。

### 2026-10-13 · T2 完成 ✅

**根因**：测试依赖从未声明 —— `respx`、`fakeredis` 不在任何 requirements 文件里；`python-multipart` 虽写在 `requirements.txt:3` 但环境未装。导致 9 个模块**收集期**就报错，**0 个用例执行**。

**产出**：
- 新增 `ai-service/pytest.ini`（`asyncio_mode = auto` + `asyncio_default_fixture_loop_scope = function`）
- 新增 `ai-service/requirements-dev.txt`（`respx==0.23.1`、`fakeredis==2.39.0`，按本机实际验证通过的组合锁定；`-r requirements.txt` 兜住全量）

**验证**：`py -3 -m pytest tests -q` → **393 passed, 1 skipped, 0 failed**（此前：9 errors during collection）

### 2026-10-13 · T3 完成 ✅（安全价值最高的一步）

`asyncio_mode = auto` 一次修好全部 4 个"静默跳过"，**无需改动测试代码**。逐名验证：

```
tests/test_community_tools.py::TestTenantGuard::test_runner_tasks_rejected_without_tenant  PASSED
tests/test_community_tools.py::TestTenantGuard::test_overview_rejected_without_tenant      PASSED
tests/test_community_tools.py::TestTenantGuard::test_feeds_rejected_with_zero_tenant       PASSED
tests/test_community_tools.py::TestCommunityToolsWithDb::test_search_runner_tasks_serializes PASSED
====================== 10 passed, 392 warnings in 2.04s =======================
```

→ **3 个租户隔离守卫用例从"从未执行"变成"真实执行且通过"**。
剩余 1 个 skip 是显式声明的（`tests/test_redis_persistence.py:164`：环境缺 DB 依赖），可解释。

### 2026-10-13 · 附带发现：一个 "失败" 其实是「过时测试」，不是回归

修复依赖后暴露出 `test_synthesize_tts_not_implemented` 失败。**判断过程（不可直接改测试让它变绿）**：

| 事件 | 时间 | 提交 |
|---|---|---|
| 用例引入（当时 TTS 未实现，断言抛 `NotImplementedError`） | 2026-09-10 | `6e56401` |
| 用例最后改动 | 2026-09-16 | `50f43f7` |
| **TTS 实装**（`synthesize_tts` 走 POST `/v1/t2a_v2`） | **2026-10-03** | `16e4c03` |

实现比测试晚 17 天落地，用例从未同步；又因套件当时**连收集都失败**，这个红灯长期无人看到。
**结论：过时测试。** 按当前真实契约重写为 `test_synthesize_tts_requires_voice_id`（断言缺 `voice_id` 抛 `MusicVendorError`），并在 docstring 里留了沿革说明以防再次误判。
同时修掉该文件自身的矛盾：模块头 `senseaudio_universal.py:12` 仍写 `synthesize_tts, 待实现`，而 `:526` 已写"实测可用"。

### 2026-10-13 · 门禁状态（T1–T3 后）

```
✅ PASS  backend 单元测试 (mvn test)                   8s
❌ FAIL  backend 格式校验 (spring-javaformat:validate)  exit 1, 2s
❌ FAIL  ui 类型检查 (tsc --noEmit)                     exit 2, 9s
❌ FAIL  ui 单元测试 (jest --ci)                        exit 1, 41s
✅ PASS  ai-service 测试 (pytest -q)                    65s
 汇总（总耗时 125s）→ 存在失败项 ❌
```

下一步：**T4**（backend 3 文件格式违规）→ **T5**（ui 18 个 tsc 错，含 `openDetail` 真缺陷）→ **T6**（jest 红）。

### 2026-10-13 · T4 完成 ✅ —— 但真实范围是 100 个文件，不是 3 个

**★ 范围误判的根因（重要，会影响所有"读 CI 输出判断范围"的场景）**：
`spring-javaformat:validate` 在**第一个存在违规的模块**就 `BUILD FAILURE` 退出，**根本不检查后面的模块**。因此输出里只列出 `twelvet-framework-core` 的 3 个文件 —— 审计和本计划据此误判为"3 个文件"。`apply` 一跑，真实范围是 **100 个文件 / 833 增 / 624 删**，横跨 ai / payment / product / runner / system / trade / im 等模块。

**处理：不盲改，先做字节级校验证明"零代码变更"**

- ❌ **错误方法（已记录以免重犯）**：只剔除空白后比较 → 报"67 个文件有差异"。**原因是 Javadoc 重排版会把两行合并，从而去掉续行的 `*` 前缀**，那是注释结构变化，不是代码变化。
- ✅ **正确方法**：写带状态机（normal / line / block / string / char）的 Java 注释剥离器，剥掉注释**和**全部空白后再逐字节比较：

```
检查 .java 文件数                    : 100
剥掉注释+空白后【代码完全一致】的文件 : 100
剥掉注释+空白后【代码仍有差异】的文件 : 0
```

**验证证据**：
- `mvn -B -o spring-javaformat:validate` → **BUILD SUCCESS**
- `mvn -B -o test` → **BUILD SUCCESS**（Tests run: 10, Failures: 0）
- 提交：`88726cb style(backend): 应用 spring-javaformat 修复全部格式违规`（独立提交，避免与功能改动混淆）

**顺带发现的新问题（已登记为阶段 A 后续任务）**：
仓库**没有 `.gitattributes`**，且 `core.autocrlf=true` → 行尾策略完全依赖每台机器的本地配置。本次格式化已使 **7 个文件**的 LF/CRLF 发生变化，若不以 `.gitattributes` 固定，不同机器提交出来的内容会不一致（"我本地是好的"类问题的又一个来源）。

### 2026-10-13 · T5 完成 ✅ —— 修掉 16 处 tsc 错误，其中 1 个已上线的真缺陷

**真缺陷（同 `openDetail` 一类：类型错误其实是运行时缺陷）**

| # | 位置 | 性质 | 处理 |
|---|---|---|---|
| 1 | `pages/System/Tenant/index.tsx:352` | **真缺陷**：「详情」按钮调用 `openDetail(row)`，该函数全文件从未定义也未 import → 点击必抛 `ReferenceError` | 详情弹窗（`detailModal` 状态 + `<Modal title="租户详情">`）早已存在，只缺处理函数 → 按既有意图补上（纯 UI 状态赋值）。**已核对 `detailModal` 无第二个赋值点**，接线唯一 |
| 2 | `Tool/Code/Gen/.../TagList.tsx:19` | **潜在缺陷**：直接渲染 `{value}` 而 `value` 是 `{key,label}[]`，非空时 React 抛 `Objects are not valid as a React child` | 按组件语义（名称 TagList、props 为 key/label）改为渲染 `<Tag>`。当前调用方未传 value，故尚未暴露 |

**类型标注写错（★ 关键判断：调用方是对的，不可反向修改）**
`@umijs/max` 的 `request` 返回**完整信封** `{code,msg,data}`（依据：`services/ant-design-pro/aimusic.ts:14` 的注释 + 全项目 **154 处** `const {code,msg,data} = await xxxApi()` 解构）。而 `Tenant/service.ts` 的 `getAiUsage` / `getRenewStatus` / `createRenewOrder` 把 **payload** 类型当成了 `request<T>` 的 T → 修法是**改类型参数**，不是改调用方。若搞反，会把运行时正常的 AI 用量抽屉改坏。

**其余**
- `app.tsx:264` `motionEaseIn`：antd 5.29 的 `AliasToken` 并无该 token（只有 `motionEaseInBack` / `motionEaseInQuint` / `motionEaseInOut` 等变体），**从未生效**，属死配置。其曲线是 ease-in，与已有 `motionEaseInOut`（`0.4,0,0.2,1`）语义不同 —— **按 tsc 提示改名会覆盖正确值**，故删除而非改名。（本计划 §T5 Step 3 原先写的"拼写应为 motionEaseInOut"是错的，已在执行中纠正。）
- Marketplace 设计令牌补 `serif`：**复用 Login 令牌同一字体栈**（`Login/styles/tokens.ts:34`），非自创。
- antd 5.29 不再导出 `CheckboxValueType`（`CheckboxGroupProps<T>` 已泛型化）→ 按历史定义就地声明，不用 `any`。
- `Dept/index.tsx` postData 参数标注、`Log/Operation` res 索引签名、`requestErrorConfig` 的 `unknown` 收窄与 headers 索引签名。

**验证证据**
- `tsc --noEmit` → **0 error**
- `jest --ci` → `1 failed, 1 passed`，**与改动前完全一致**（那 1 个红是 `CSS is not defined`，属 T6），证明本次未引入新失败
- `./verify.sh` → ui 类型检查 **PASS**
- 提交：`9d9d470 fix(ui): 修复 tsc 全部类型错误，含「租户详情」按钮真缺陷`

**⚠️ 未完成的验证（诚实登记）**：浏览器点击「详情」的**端到端确认未做**（需起后端 + 前端并登录）。本次以「tsc 0 error + 接线结构逐行核对」为证据；补**组件级回归测试**列入后续任务，否则同类"已定义但未接线"的缺陷仍只能靠 tsc 兜。

### 2026-10-13 · T6 完成 ✅ —— **门禁 5/5 全绿，阶段 A 主线收口**

**根因**：`ShoppingGuide/index.tsx` 原本写 `CSS.supports?.('height', '100dvh')`。
**optional chaining 只保护「方法不存在」，不保护「`CSS` 这个全局未定义」** —— jsdom 环境没有 `CSS` 全局，故抛 `ReferenceError`；任何缺少该全局的运行环境（SSR、部分 WebView）同样会崩。

**修法选择（刻意不做什么）**：

| 方案 | 决定 | 理由 |
|---|---|---|
| 修源码防护写法：`typeof CSS !== 'undefined' && typeof CSS.supports === 'function' ? CSS.supports(...) : false` | ✅ **采用** | 这是缺陷本体。抽为组件内的 `supportsDvh` 常量，两处调用点复用（原为重复表达式） |
| 给 `tests/setupTests.jsx` 塞一个假的 `CSS` 全局 | ❌ **不采用** | jsdom **没有 CSS 引擎**，假 `supports()` 只能返回假答案，属于"用 mock 掩盖真实缺陷"；且会让同类写法继续被放过 |
| 改用 `window.CSS` 判断 | ❌ 未采用 | 与 `typeof` 判断等效，但 `typeof` 对未声明标识符才是安全的写法 |

**行为影响**：真实浏览器中 `CSS` 存在且支持 `100dvh` → 仍走 `100dvh`（无行为变化）；jsdom / 无 `CSS` 环境下退化为 `100vh` 分支（本来就是 fallback 设计）。

**验证证据**：
- `jest --ci` → `2 passed, 2 total`，**退出码 0**
- `./verify.sh` → **5/5 PASS，退出码 0，总耗时 128s**（留档 `docs/verify-after-stageA-gate-2026-10-13.txt`）

### 2026-10-13 · 门禁状态（T1–T6 后）★ 全绿

```
✅ PASS  backend 单元测试 (mvn test)                    8s
✅ PASS  backend 格式校验 (spring-javaformat:validate)  3s
✅ PASS  ui 类型检查 (tsc --noEmit)                     9s
✅ PASS  ui 单元测试 (jest --ci)                        38s
✅ PASS  ai-service 测试 (pytest -q)                    70s
 汇总（总耗时 128s）→ 全部通过 ✅   （verify.sh 退出码 0）
```

演进：**1/4 → 2/3 → 3/2 → 4/1 → 5/5 全绿**

**提交**：`88726cb`（backend 格式化）→ `9d9d470`（ui tsc）→ `383023b`（ai-service 测试可运行）→ `67422da`（门禁 + 文档）→ `65f6f7b`（计划记录）→ 本轮 T6。

---

## 7. 阶段 A 剩余任务（"可复现"尚未完成）

门禁全绿只解决了"改动有保护"，**"新人能在干净机器上跑起来"还没做**。阶段 A 剩余：

### 2026-10-13 · A7 完成 ✅ —— dev 配置不再指向生产库

**问题**（可复现性头号阻塞）：`application-dev.yml` 把本地开发指向一台远程生产库（SSH 隧道转发到 `13306`/`16379`）并内嵌其 MySQL/Redis 口令；而 dev 是 pom 的 `activeByDefault`。后果有两层：按 README 做的人**必然连不上**（README 教的是本机 3306），而**隧道若开着则本地开发直接读写生产库** —— 后者是误操作风险，不只是便利问题。

**改动**：数据源与 Redis 改为本机默认值且全部支持环境变量覆盖（复用同一文件 `payment` 块既有的 `${ENV:default}` 写法）：
`DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD`、`REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`，默认 `localhost:3306`(root) 与 `localhost:6379`(无密码)，与根 `docker-compose.yml` 的 MySQL/Redis 完全对齐。移除生产 IP 与内嵌口令，保留一段「历史注意（勿回退）」说明。未新增 `application-local.yml`（既然参数已可环境变量覆盖，再加 profile 文件属冗余）。

**验证（三步递进，专门排除歧义）**
1. YAML 解析通过，默认值确认是 `${DB_HOST:localhost}:${DB_PORT:3306}` 与 `${REDIS_HOST:localhost}:${REDIS_PORT:6379}`；
2. 启动 jar（`profile=dev`）能正常进入数据源初始化，**占位符解析无 "Could not resolve placeholder"**，因本机无 MySQL 而 `Connection refused`；
3. **判别测试**：注入 `DB_HOST=no-such-db-host.invalid` 后，错误由 `Connection refused` 变为 `UnknownHostException(no-such-db-host.invalid)` —— 这一步是必要的，因为 3306 与 13306 在无监听时**都会** refused，单看第 2 步无法区分到底走哪个端口。该测试证明配置确实读取环境变量。
4. `./verify.sh` → 5/5 PASS。

**附带发现**：`backend/twelvet-admin/target/classes/application-dev.yml` 是构建产物，此前仍留有旧口令**并会被打进 jar**；已用 `process-resources` 刷新清除。凭据的轮换与彻底出库仍按用户决定留在阶段 D。

提交：`c41e389`。

### 2026-10-13 · A8 完成 ✅ —— 行尾策略固定并落地

1. **新增 `.gitattributes`**：`* text=auto eol=lf`（文本统一 LF，与 Linux 部署一致）+ `*.bat/*.cmd` 保留 CRLF + `*.sh` 强制 LF + 常见二进制禁用转换 + 锁文件 `-diff` 折叠 review 噪音。
2. **跟进一次 renormalize**（否则策略落不了地）：加入属性后索引里仍有一批文件保留 CRLF，今后每次触碰都会产生"整文件改动"的假 diff。执行 `git add --renormalize .` → **25 个文件**。
3. **安全性验证（与 T4 同一标准）**：逐个比较 HEAD 与暂存版本，去掉 `\r` 后逐字节比对 → **25/25 仅行尾差异，真实内容改动 0**。
   - 其中包含 `ai-service/.env`（仍在跟踪，属阶段 D 出库范围），本次对它同样只是行尾，无内容变化。
4. 提交：`5542d6b`（新增属性）、`c3efd03`（renormalize）。

### 2026-10-13 · A12 完成 ✅ —— 交付 npm lock，统一包管理器为 npm

**问题**：`ui/.gitignore` 把 `yarn.lock` / `package-lock.json` / `pnpm-lock.yaml` **三把锁全部忽略** —— 等于一把都不交付；而文档与 CI 却都写 `yarn install`，依赖完全无法复现。

**选 npm 而非 yarn 的依据（证据，不是偏好）**：
1. `deploy/部署交接手册.md:165-168` 已记录 **yarn 在本机是坏的**（`Cannot find module .../corepack/dist/yarn...`），并明确写「绕过方式：直接用 `npm run build`，不要用 yarn」→ 团队事实上已在用 npm；
2. 磁盘上的 `node_modules` 本就是 npm 装的，`package-lock.json`（lockfileVersion 3、3268 个包）存在且与 `package.json` 同步；
3. 若改用 yarn 需重新解析生成 `yarn.lock`，其依赖树很可能与**已验证通过的那棵树**（tsc 0 error、jest 全绿）不同 —— 等于把绿灯建立在未验证的树上。

**改动**：`ui/.gitignore` 不再忽略 `package-lock.json`（并写明依据）、继续忽略另两把锁以避免歧义；交付 `ui/package-lock.json`（1952 KB）；文档/脚本统一 npm（`docs/ONBOARDING.md` 6 处、`README.md:304`、`verify.sh` 提示语、`ui/.github/workflows/test.yml` 并修掉误传给 `setup-node` 的 `distribution: zulu`）。

**验证**：`npm ci --dry-run` 退出码 **0**（lock 与 package.json 同步可用）；`git check-ignore` 确认另两把锁仍被忽略；`./verify.sh` **5/5 PASS**（依赖树未变动，绿灯保持）。提交 `b3214fa`。

**未验证（诚实登记）**：未执行真正的 `npm ci` 全新安装 —— 它会删除并重建 1.57GB 的 `node_modules`，可能使当前已验证的依赖树发生变化。如需以"从零安装后门禁仍全绿"作为验收标准，请明确指示，我再单独执行。

### 2026-10-13 · A11 完成 ✅ —— 文档对齐真实结构（四批）

**问题**：多份文档描述的内容与仓库实际不符，会直接误导任何接手者（包括 AI 助手自己 ——
`CLAUDE.md` 是首要入口）。

| 批次 | 文件 | 修正要点 | 提交 |
|---|---|---|---|
| 1 | `CLAUDE.md` | 目录结构（原写 `twelvet-fast/` 平铺，且全篇不提三端之一 `ai-service/`）；技术栈（原写 `Spring AI 1.0.0 / 默认 DeepSeek` —— **实测全 pom 无 `spring-ai` 依赖**）；AI 集成段（架构说反：Java 只是转发层）；API 数（`39/218` → 实测 **59/346**，原表还漏 payment、runner 两模块）；前端（`twelvet-ui-master`+yarn → `ui/`+npm）；环境配置（test/staging 无配置文件）；i18n 实测缺陷；`.atomcode.md` 未交付 | `ae47650` |
| 2 | `ui/README.md`、`README_ZH.md` | 原为**上游 twelvet Spring Cloud 微服务脚手架**的 README（徽章版本 3.2.0 / 2.7.5），与本仓库无关。重写为 ui 实际内容 + 架构不变量 + 4 条已知问题 | `b6e0250` |
| 3 | 根 `README.md`、`backend/README.md` | 根 README 教的 `-v backend/data:/docker-entrypoint-initdb.d` **实测第 4 个文件即失败**；「初始化顺序」是未验证的逻辑顺序却被当执行顺序；`backend/` 标为"微服务"（实为单体）；`.archive/` 说明已失效（该目录 2026-10-13 已删）；测试命令 `python` 本机不可用、asyncio"已知问题"已不复现。backend README 整份是上游模板（指向别的仓库、`./deploy.sh nginx` 子命令不存在、"内存 > 16"而线上 2C2G）→ 重写含环境变量覆盖示例与 7 条实跑确认的已知问题 | `0963121` |
| 4 | `deploy/部署交接手册.md` | 「一句话现状」与状态表写"登录不可用/84 张旧表"，与同文档第 7 章自相矛盾；按就近实测统一为登录可用、**107 张表**、Redis 7.4.11、ai-service 502。另更正"`deploy/` 未被跟踪"——`git ls-files` 实测**有 13 个文件被跟踪** | `5f3bb11` |

### 阶段 A 剩余任务

| # | 任务 | 当前证据 |
|---|---|---|
| A9 | `backend/Dockerfile`、`ui/Dockerfile` + 修根 compose | compose 引用 `build: ./backend`、`build: ./ui`，两者均无 Dockerfile → `docker compose up` 直接失败。**另：本机未安装 Docker，无法在本机验证**（需先决定验证方式） |
| A10 | Flyway baseline（107 表导出）+ 归档 35 个手写 SQL | 已实测：生产库 **107 张表**，与 `db-export/twelvet-fast_2026-10-08.sql` 完全一致（baseline 选择有据）；`twelvet.sql` 含 `DROP DATABASE` 却按 initdb 字母序排第 24/27，第 4 个文件就 `USE` 一个还不存在的库 |
| A13 | 补组件级回归测试 | T5 的 `openDetail` 类缺陷（已定义但未接线）目前只能靠 `tsc` 兜一部分；"详情"按钮的端到端点击验证仍未做 |

---

## 9. 阶段 B ① · 跨租户下单缺陷修复（2026-10-13 完成）

### 缺陷链路（每条都有代码位置）

| 路径 | 行为 |
|---|---|
| Java 写单 | `TenantSupport.java:31-37`：开关开启且无租户上下文时**抛异常**，注释明写"严禁静默把数据落到 default 租户" |
| Python 写单 | `ai-service/app/services/agent/tools.py` 的 `confirm_order` 直接 `INSERT INTO trade_order`，**列清单里没有 `tenant_id`** → 落到表定义 `NOT NULL DEFAULT 1` |

**⚠️ 关键更正**：先前的记录写"开关默认 false、属潜在风险"是**错的** —— `application.yml:18`
实际为 `tenant.enabled: true`，所以该缺陷**当时就是活的**，不是将来时的风险。

### 后果

买家在自己租户里查不到刚下的单（订单列表按租户过滤），该单却出现在默认租户（1）的列表里 ——
既破坏功能，又构成跨租户数据混入。同时丢掉 `freight`（Python 侧硬编码 0，Java 取
`product.shippingFee`）与 `product_snapshot`（Java 会写 JSON 快照）。

### 修复

- **Java**：新增 `POST /internal/trade/order/create` —— `@SaIgnore` + `X-Internal-Token`
  常量时间校验 + **失败关闭**（未配置令牌一律 403）；租户由 `sys_user.tenant_id` 解析
  （**不信任调用方传参**）后 `TenantContext.set/clear`，复用 `createOrder`；业务性拒绝返回 **400**。
- **Python**：`confirm_order` 删除直写，改调上述端点；**失败时明确报错、绝不回退直写**；
  保留确认令牌与幂等键（HITL 行为不搬去 Java）。
- **边缘**：`deploy/Caddyfile` 对 `/api/internal/*`、`/internal/*` 返回 404 —— 必须放在
  `handle_path /api/*` **之前**，否则会被反向代理先接管（Caddy handle 按出现顺序匹配）。

### 验证（按用户要求：只写代码 + 单测）

| 检查 | 结果 |
|---|---|
| 后端单测 | **18 passed**：`InternalApiTokenTest` 4（含"未配置一律拒绝"）、`TradeOrderServiceImplInternalTest` 4（租户取自 DB / 跨租户商品被拒 / 失败路径清理上下文 / 开关关闭时与公开路径一致）、`RecommendScorerTest` 10 |
| ai-service 测试 | **401 passed, 1 skipped**（新增 `tests/test_agent_internal_order.py` 8 个：请求契约、失败关闭且不发请求、403/400/网络异常映射、**Java 失败时不标记幂等键且不回退直写**、成功时用 Java 返回的订单号与金额） |
| 三端门禁 | `bash verify.sh` **5/5 PASS**，exit 0 |

**未验证（诚实登记）**：按用户决定未做集成实跑，故以下仍只有结构性/单元级证据，**没有"真跑"的行为证据**：
真实 MySQL 上的 `tenant_id` 落库值、Caddy 拦截是否按预期生效、端到端（Agent → Java → DB）联调。

**范围外（已记录，未改）**：`pay_order`（`tools.py`）同样直写 `trade_order`，但已核实它
**不在任何 Agent 工具列表**（`AGENT_TOOLS` 各域列表均无），Agent 触达不到；`tools.py` 中
若干**读**查询仍缺租户过滤，属同类问题，建议单独立项。

---

## 8. 端到端实测（首次真实运行证据，2026-10-13）

**方式**：经 SSH 隧道（`ssh -N -L 13306:127.0.0.1:3306 -L 16379:127.0.0.1:6379 weiwei-prod`）连接服务器真实 MySQL/Redis，用**环境变量**注入（`DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD`、`REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`）启动 `twelvet-admin.jar`（dev profile）。
→ 注意：**远程连接走环境变量，而不是把生产配置写回 dev 文件** —— 这正是 A7 新增机制的实际用法，也印证了"dev 不指向生产、需要时用注入"的设计。

### 已验证（行为证据，非静态推断）

| 项 | 结果 |
|---|---|
| 应用启动 | `Started FastApplication in 18.518 seconds`；datasource master+slave 连接成功；Redisson 连上 16379；Quartz 启动；Undertow 就绪 |
| 登录 | `POST /auth/oauth2/token`（Basic `twelvet:<secret>`）→ **HTTP 200, code=200**，返回 36 位 token |
| 鉴权调用 | `GET /system/user/profile`（Bearer）→ **HTTP 200** |
| 未登录拦截 | 同接口不带 token → **HTTP 401** ✅ |
| 生产库结构 | `information_schema` 表数 = **107**，10 用户 / 3 租户 —— **与 `db-export/twelvet-fast_2026-10-08.sql` 的 107 张表完全一致**（为 A10 baseline 提供实测依据） |
| 服务端 Redis | 7.4.11，与 compose 镜像 `redis/redis-stack-server:7.4.0-v0` 同线 |

### 新发现的缺陷（行为验证，列入阶段 B/C）

1. **i18n 的 Redis 缓存部分失效（静默）**：`RedisUtils.setCacheObject` 用 `GenericJackson2JsonRedisSerializer` 序列化含 `LocalDateTime` 的实体时抛 `InvalidDefinitionException`（未注册 `jackson-datatype-jsr310`），发生在 `I18nServiceImpl.initI18n` 的**线程池**里 → 不致命、**无任何告警**。证据：服务端 db1 有 228 个 `locale::*` 键（能序列化的进去了），而报错那批（`SysDictData.createTime`）永远进不去 → 每次重启都要回查 DB，跨实例共享失效。
2. **不存在的路径返回 HTTP 500 而非 404**：`GET /system/dict/type/list`（我猜错的路径）→ `HTTP 500` + `{"code":500,"msg":"No static resource system/dict/type/list."}`。根因是 `TWTExceptionHandler` 的 `Exception` 兜底把 Spring 的 `NoResourceFoundException` 吞成 500，并把内部消息回吐客户端。后果：**监控无法区分"真故障"与"路径打错"**（与审计 7.3 / 7.4 同源）。
3. **会话键前缀与文档描述不同（澄清，非缺陷）**：先前以为前缀是 `satoken:`，实测是 **`Authorization:login:token:<token>`**（由 `sa-token.token-name: Authorization` 决定）。会话**确实持久化在 Redis**：重启实例后复用重启前的 token 仍返回 200 —— 且已核对旧进程确实退出、8080 由新 PID 持有，故该结论有效（这一步专门做了核验，否则旧进程未死会让结论完全反过来）。
4. **环境间会话无命名空间隔离**：dev 使用 `spring.data.redis.database: 1`，而服务端 db1 里另有 3 个**非本次创建**的登录会话。即开发环境连上服务器 Redis 时会与其它会话共处同一 keyspace，存在互相干扰甚至误踢的风险。建议阶段 B/C 明确：dev 用独立 db 或独立实例。

### 清理与遗留

- 本次验证创建的 4 个 admin 会话已**精确删除**（`...:login:token:<t>` 与 `...:login:token-session:<t>`）。
- 另有 **3 个非本次创建**的会话保留未动（`04fd6b0c…`、`133b337f…`、`b16b1f80…`）—— 不做猜测性删除，避免误踢真实用户；如需清理请你确认。
- 临时资源全部清理：后端进程、SSH 隧道、本机临时 Redis 均已停止，临时 MySQL datadir 已删除，8080/13306/16379/6379 端口均已释放。
