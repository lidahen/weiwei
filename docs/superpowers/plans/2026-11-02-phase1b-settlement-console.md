# Phase 1B 结算运营与卖家端 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Umi 管理端补齐「实名审核 / 结算单 / 提现审核 / 结算对账」四个运营页，在 C 端补齐「卖家中心」（实名提交 + 余额 + 提现申请），菜单与路由对齐 Phase 1A 的接口与权限。

**Architecture:** 每个页面一个 `ProTable`（或 `PageContainer` + 表单）组件，业务判断下沉到同目录纯函数（`*.ts` + Jest），组件只做接线；API 统一放 `ui/src/services/marketplace/api.ts`；路由加在 `ui/config/routes.ts`，菜单行已由 Phase 1A 的 `backend/sql/settlement_menu.sql` 建好（`component` 路径必须与页面文件一致）。

**Tech Stack:** Umi Max 4、React 18、TypeScript、Ant Design 5 / ProComponents、Jest（纯函数测试）、npm（禁止 yarn）。

**Spec:** `docs/superpowers/specs/2026-11-02-settlement-compliance-design.md` §7；接口与字段以 `docs/superpowers/plans/2026-11-02-phase1a-settlement-backend.md` Task 11 为准（前置：Phase 1A 已合并）。

## Global Constraints

- 包管理器 `npm`；测试 `npm --prefix ui test -- --runTestsByPath <file> --watchAll=false`；类型检查 `npx tsc --noEmit`（在 `ui/` 下）。
- 页面组件与纯函数分文件：纯函数放 `ui/src/pages/marketplace/admin/<name>View.ts` 或 `ui/src/pages/marketplace/<name>View.ts`，测试同名 `.test.ts`；**不渲染 antd 组件**（仓库既有测试全部是纯函数，Phase 0B 已定此惯例）。
- 金额展示统一 `formatPrice`（`ui/src/pages/marketplace/utils.ts`）；状态用 `Tag` + 纯函数 `statusMeta(status)` 映射；日期 `YYYY-MM-DD HH:mm`。
- 权限字符串与服务端一致：`marketplace:admin:kyc` / `marketplace:admin:settlement` / `marketplace:admin:withdraw` / `marketplace:admin:recon`（页面本身不判权限，菜单可见性由后端 `sys_menu` 决定）。
- 菜单组件路径固定：`marketplace/admin/Kyc`、`marketplace/admin/Settlement`、`marketplace/admin/Withdraw`、`marketplace/admin/Recon`、`marketplace/MySettlement`；路由 `path` 与 `settlement_menu.sql` 完全一致。
- 上传凭证复用 `ui/src/components/marketplace/UploadImages.tsx` 的 `action`/token 约定；表单校验纯函数化（`validateKycForm`、`validateWithdrawAmount`），错误信息中文。
- 不做真实打款、不接真实渠道；页面文案不得出现「平台担保」承诺（spec §8 未确认）。

## Review Focus

1. **菜单/路由/组件三处路径不一致**：`settlement_menu.sql` 的 `component`、`routes.ts` 的 `component`、实际文件三者必须逐字一致，否则运营点菜单 404（Task 2–6 各自的接线步骤 + Task 7 断言）。
2. **金额与状态误显示**：`null` 金额显示 `-` 而不是 `¥0.00`；结算/提现/差异状态必须有中文映射，未知状态原样显示（Task 1 纯函数测试）。
3. **提现金额越界**：前端校验必须拦截 `<=0`、超过可用余额、超过 2 位小数（Task 2 纯函数测试）；后端仍会二次校验。
4. **KYC 敏感字段回显**：表单不回显完整证件号/卡号（接口只给脱敏值），编辑时留空即不修改——页面不得把脱敏值当明文提交（Task 2）。
5. **对账差异误操作**：resolve 必须填备注；解冻是独立动作，不与 resolve 合并（Task 6）。

---

## File Structure

**新建（UI）**
- `ui/src/pages/marketplace/MySettlement.tsx`、`ui/src/pages/marketplace/mySettlementView.ts` + `.test.ts`
- `ui/src/pages/marketplace/admin/Kyc.tsx`、`kycView.ts` + `.test.ts`
- `ui/src/pages/marketplace/admin/Settlement.tsx`、`settlementView.ts` + `.test.ts`
- `ui/src/pages/marketplace/admin/Withdraw.tsx`、`withdrawView.ts` + `.test.ts`
- `ui/src/pages/marketplace/admin/Recon.tsx`、`reconView.ts` + `.test.ts`

**修改**
- `ui/src/services/marketplace/api.ts` — 新增 14 个 API 函数（见 Task 1）。
- `ui/config/routes.ts` — `/user/settlement`（C 端）+ 4 个 `/admin/marketplace/*` 路由。

**依赖（前置）**：Phase 1A 的 `backend/sql/settlement_menu.sql` 已执行；接口 `/trade/seller/*`、`/admin/marketplace/{kyc,settlement,withdraw,recon}/*` 已存在。

---

### Task 1: API 客户端与共享纯函数

**Files:**
- Modify: `ui/src/services/marketplace/api.ts`
- Create: `ui/src/pages/marketplace/admin/settlementShared.ts`（状态映射与格式化，四个运营页共用，属纯函数）
- Test: `ui/src/pages/marketplace/admin/settlementShared.test.ts`

**Interfaces:**
- Consumes: Phase 1A 接口响应（`JsonResult`，`res.data` 为负载）
- Produces（API，名称固定）:
  - `sellerAccount()` → GET `/trade/seller/account`
  - `sellerKycApply(data: {accountName,idCardNo,bankCardNo,bankName})` → POST `/trade/seller/account/apply`
  - `sellerWithdrawApply(amount: number)` → POST `/trade/seller/withdraw/apply` `{amount}`
  - `sellerWithdrawPage(params?: {current,pageSize})` → GET `/trade/seller/withdraw/page`
  - `adminKycPage(params?: {kycStatus,current,pageSize})` → GET `/admin/marketplace/kyc/page`
  - `adminKycReview(data: {accountId,result,note})` → POST `/admin/marketplace/kyc/review`
  - `adminSettlementPage(params?: {status,current,pageSize})` → GET `/admin/marketplace/settlement/page`
  - `adminSettlementDetail(settlementId: number|string)` → GET `/admin/marketplace/settlement/detail/{id}`
  - `adminWithdrawPage(params?: {status,current,pageSize})` → GET `/admin/marketplace/withdraw/page`
  - `adminWithdrawReview(data: {withdrawId,action:'approve'|'reject',reason})` → POST `/admin/marketplace/withdraw/review`
  - `adminWithdrawPay(data: {withdrawId,voucherUrl})` → POST `/admin/marketplace/withdraw/pay`
  - `adminReconPage(params?: {bizDate,status,current,pageSize})` → GET `/admin/marketplace/recon/page`
  - `adminReconResolve(data: {diffId,note})` → POST `/admin/marketplace/recon/resolve`
  - `adminReconUnfreeze(data: {sellerId,reason})` → POST `/admin/marketplace/recon/unfreeze`
- Produces（纯函数，`settlementShared.ts`）:
  - `SETTLEMENT_STATUS: Record<string,{text:string;color:string}>`
  - `WITHDRAW_STATUS`、`KYC_STATUS`、`RECON_STATUS`、`RECON_TYPES` — 同形状
  - `statusMeta(map, status)` → `{text,color}`，未知状态返回 `{text: status, color:'default'}`
  - `moneyText(value): string` → `null/undefined → '-'`，否则 `formatPrice(value)`
  - `validateWithdrawAmount(amountText: string, balance: number): string | null` — 返回错误文案或 null
  - `validateKycForm(form): string | null` — 姓名/证件号/银行卡号/开户行非空，证件号 15/18 位，银行卡 12–19 位数字
  - `toAmount(value: string): number`

- [ ] **Step 1: 写失败测试**

`settlementShared.test.ts`：
```ts
expect(statusMeta(SETTLEMENT_STATUS, 'holding')).toEqual({ text: '担保中', color: 'blue' });
expect(statusMeta(SETTLEMENT_STATUS, 'unknown')).toEqual({ text: 'unknown', color: 'default' });
expect(moneyText(null)).toBe('-');
expect(moneyText(1234.5)).toBe('¥1,234.50');
expect(validateWithdrawAmount('0', 100)).toBe('提现金额必须大于 0');
expect(validateWithdrawAmount('120', 100)).toBe('超过可用余额');
expect(validateWithdrawAmount('10.123', 100)).toBe('金额最多两位小数');
expect(validateWithdrawAmount('10.50', 100)).toBeNull();
expect(validateKycForm({ accountName: '', idCardNo: '1', bankCardNo: '1', bankName: '' })).not.toBeNull();
expect(validateKycForm({ accountName: '张三', idCardNo: '110101199001011234', bankCardNo: '6222020200112233445', bankName: '工商银行' })).toBeNull();
```
状态中文固定：结算 `holding 担保中 / pending_settle 待打款 / settled 已结算 / withdrawn 已提现 / closed 已关闭 / clawback 已扣回`；提现 `pending 待审核 / approved 待打款 / paid 已打款 / rejected 已驳回`；实名 `unverified 未实名 / pending 审核中 / verified 已通过 / rejected 已驳回`；差异 `open 待处理 / resolved 已处理`。

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/admin/settlementShared.test.ts --watchAll=false`
Expected: 模块不存在失败。

- [ ] **Step 3: 实现 API 与纯函数**

API 用 `request`，参数形状与 Task 11 一致；`moneyText` 复用 `formatPrice`（`ui/src/pages/marketplace/utils.ts`）。纯函数不依赖 antd/React。
颜色固定（测试断言）：结算 `holding=blue/pending_settle=gold/settled=green/withdrawn=cyan/closed=default/clawback=red`；提现 `pending=gold/approved=blue/paid=green/rejected=red`；实名 `unverified=default/pending=gold/verified=green/rejected=red`；差异 `open=red/resolved=green`。

- [ ] **Step 4: 运行并确认 GREEN + 类型检查**

Run: 同 Step 2；then `cd ui && npx tsc --noEmit`
Expected: 测试全绿；tsc 0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/services/marketplace/api.ts ui/src/pages/marketplace/admin/settlementShared.ts ui/src/pages/marketplace/admin/settlementShared.test.ts
git commit -m "feat(ui): 结算 API 与共享状态函数"
```

---

### Task 2: C 端卖家中心

**Files:**
- Create: `ui/src/pages/marketplace/MySettlement.tsx`
- Create: `ui/src/pages/marketplace/mySettlementView.ts` + `mySettlementView.test.ts`（KYC 表单 → 提交负载、提现可提交判断）
- Modify: `ui/config/routes.ts`（`/user/settlement`，`name: '卖家中心'`）

**Interfaces:**
- Consumes: `sellerAccount`、`sellerKycApply`、`sellerWithdrawApply`、`sellerWithdrawPage`、`validateKycForm`、`validateWithdrawAmount`、`moneyText`、`statusMeta`
- Produces: `toKycPayload(form)`（trim、证件号大写 X）；`canSubmitKyc(form)`；页面路径 `/user/settlement`

- [ ] **Step 1: 写失败测试**

`mySettlementView.test.ts`：
```ts
expect(toKycPayload({ accountName: ' 张三 ', idCardNo: '11010119900101123x', bankCardNo: ' 6222...', bankName: ' 工商银行 ' }))
  .toEqual({ accountName: '张三', idCardNo: '11010119900101123X', bankCardNo: '6222...', bankName: '工商银行' });
expect(canSubmitKyc({ accountName: '张三', idCardNo: '110101199001011234', bankCardNo: '6222020200112233445', bankName: '工商银行' })).toBe(true);
expect(canSubmitKyc({ accountName: '', idCardNo: '', bankCardNo: '', bankName: '' })).toBe(false);
```

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/mySettlementView.test.ts --watchAll=false`
Expected: 模块不存在。

- [ ] **Step 3: 实现纯函数与页面**

页面结构：
- `PageContainer` + `Card`：
  - 未实名/驳回：实名表单（姓名、证件号、银行卡号、开户行），提交调 `sellerKycApply`；提示「审核通过后可结算与提现」。
  - `pending`：显示审核中，禁用表单与提现。
  - `verified`：展示 `balance`（`moneyText`）与 `frozen` 提示；提现表单（金额）+「申请提现」；下方 `Table` 我的提现记录（`sellerWithdrawPage`，列：单号/金额/状态/时间）。
  - 页面加载 `sellerAccount()`，`res.data` 解包（`unwrap` 与仓库一致）。
- 表单不回显脱敏值到输入框；重新提交时输入框为空。
- 路由：`ui/config/routes.ts` 的 C 端区块（`/user/*` 同级）加 `{ path: '/user/settlement', name: '卖家中心', component: './marketplace/MySettlement' }`。

- [ ] **Step 4: 运行并确认 GREEN + tsc**

Run: 同 Step 2；`cd ui && npx tsc --noEmit`。
Expected: 2/2 通过；tsc 0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/MySettlement.tsx ui/src/pages/marketplace/mySettlementView.ts ui/src/pages/marketplace/mySettlementView.test.ts ui/config/routes.ts
git commit -m "feat(ui): 卖家中心实名与提现"
```

---

### Task 3: 运营实名审核页

**Files:**
- Create: `ui/src/pages/marketplace/admin/Kyc.tsx`
- Modify: `ui/config/routes.ts`（`/admin/marketplace/kyc`，`name: '实名审核'`）
- Test: `ui/src/pages/marketplace/admin/kycView.test.ts` + `kycView.ts`

**Interfaces:**
- Consumes: `adminKycPage`、`adminKycReview`、`statusMeta(KYC_STATUS)`
- Produces: `kycActions(status)` → `pending` 时 `['verified','rejected']`，其它 `[]`；`toReviewPayload(row, result, note)`

- [ ] **Step 1: 写失败测试**

```ts
expect(kycActions('pending')).toEqual(['verified', 'rejected']);
expect(kycActions('verified')).toEqual([]);
expect(toReviewPayload({ accountId: 3 }, 'rejected', '证件不清晰')).toEqual({ accountId: 3, result: 'rejected', note: '证件不清晰' });
```
（驳回时备注必填：`toReviewPayload` 返回 `null`；页面禁用提交。）

- [ ] **Step 2: RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/admin/kycView.test.ts --watchAll=false`
Expected: 模块不存在。

- [ ] **Step 3: 实现**

`ProTable` 列：卖家ID(id)/姓名(脱敏)/证件号(脱敏)/银行卡(脱敏)/开户行/状态(`Tag`)/提交时间/操作。状态筛选 `unverified/pending/verified/rejected`。操作抽屉显示脱敏信息，按钮「通过」「驳回」，驳回弹窗备注必填。成功后 `action.reload()`。路由与菜单 component `marketplace/admin/Kyc` 一致。

- [ ] **Step 4: GREEN + tsc**

Run: 同 Step 2；`cd ui && npx tsc --noEmit`。
Expected: 全绿；0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/admin/Kyc.tsx ui/src/pages/marketplace/admin/kycView.ts ui/src/pages/marketplace/admin/kycView.test.ts ui/config/routes.ts
git commit -m "feat(ui): 运营实名审核页"
```

---

### Task 4: 运营结算单页

**Files:**
- Create: `ui/src/pages/marketplace/admin/Settlement.tsx`
- Modify: `ui/config/routes.ts`（`/admin/marketplace/settlement`，`name: '结算单'`）
- Test: `ui/src/pages/marketplace/admin/settlementView.test.ts` + `settlementView.ts`

**Interfaces:**
- Consumes: `adminSettlementPage`、`adminSettlementDetail`、`statusMeta(SETTLEMENT_STATUS)`、`moneyText`
- Produces: `settlementColumns` 所需派生函数 `settlementRow(row)` → `{ grossText, commissionText, netText, statusText, holdUntilText }`

- [ ] **Step 1: 写失败测试**

```ts
expect(settlementRow({ grossAmount: 100, commissionAmount: 0, netAmount: 100, status: 'holding', holdUntil: '2026-11-09 10:00:00' }))
  .toMatchObject({ grossText: '¥100.00', commissionText: '¥0.00', netText: '¥100.00', statusText: '担保中' });
expect(settlementRow({ grossAmount: null, status: 'settled' }).grossText).toBe('-');
```

- [ ] **Step 2: RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/admin/settlementView.test.ts --watchAll=false`
Expected: 模块不存在。

- [ ] **Step 3: 实现**

`ProTable`：结算单ID/订单ID/卖家ID/应结金额(`netText`)/佣金/状态(`Tag`)/完成时间/售后期至/渠道流水；筛选 `status` 六态。点击行打开 `Drawer` 调 `adminSettlementDetail`，展示全字段（含 `refundId`、`failCount`）。路由与菜单一致。

- [ ] **Step 4: GREEN + tsc**

Run: 同 Step 2；`cd ui && npx tsc --noEmit`。
Expected: 全绿；0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/admin/Settlement.tsx ui/src/pages/marketplace/admin/settlementView.ts ui/src/pages/marketplace/admin/settlementView.test.ts ui/config/routes.ts
git commit -m "feat(ui): 运营结算单页"
```

---

### Task 5: 运营提现审核页

**Files:**
- Create: `ui/src/pages/marketplace/admin/Withdraw.tsx`
- Modify: `ui/config/routes.ts`（`/admin/marketplace/withdraw`，`name: '提现审核'`）
- Test: `ui/src/pages/marketplace/admin/withdrawView.test.ts` + `withdrawView.ts`

**Interfaces:**
- Consumes: `adminWithdrawPage`、`adminWithdrawReview`、`adminWithdrawPay`、`statusMeta(WITHDRAW_STATUS)`
- Produces: `withdrawActions(status)` → `pending: ['approve','reject']`，`approved: ['pay']`，其它 `[]`；`validateVoucher(url, reason)`（pay 需凭证；reject 需原因）

- [ ] **Step 1: 写失败测试**

```ts
expect(withdrawActions('pending')).toEqual(['approve', 'reject']);
expect(withdrawActions('approved')).toEqual(['pay']);
expect(withdrawActions('paid')).toEqual([]);
expect(validateVoucher('', '')).toBe('请上传打款凭证');
expect(validateVoucher('/dfs/a.png', '')).toBeNull();
expect(validateVoucher('', '驳回')).toBe('请填写驳回原因'); // reject 场景单独调用 validateReason
```
把驳回原因校验拆成 `validateReason(text)` → 空返回 `'请填写驳回原因'`。

- [ ] **Step 2: RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/admin/withdrawView.test.ts --watchAll=false`
Expected: 模块不存在。

- [ ] **Step 3: 实现**

`ProTable`：提现单号/卖家ID/金额/状态/申请时间/操作。审核弹窗：通过/驳回（原因必填）；打款弹窗：`UploadImages` 上传凭证（单图，取 `response.data ?? response.imgUrl`），提交 `adminWithdrawPay`。成功后 reload。路由与菜单一致。

- [ ] **Step 4: GREEN + tsc**

Run: 同 Step 2；`cd ui && npx tsc --noEmit`。
Expected: 全绿；0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/admin/Withdraw.tsx ui/src/pages/marketplace/admin/withdrawView.ts ui/src/pages/marketplace/admin/withdrawView.test.ts ui/config/routes.ts
git commit -m "feat(ui): 运营提现审核页"
```

---

### Task 6: 运营结算对账页

**Files:**
- Create: `ui/src/pages/marketplace/admin/Recon.tsx`
- Modify: `ui/config/routes.ts`（`/admin/marketplace/recon`，`name: '结算对账'`）
- Test: `ui/src/pages/marketplace/admin/reconView.test.ts` + `reconView.ts`

**Interfaces:**
- Consumes: `adminReconPage`、`adminReconResolve`、`adminReconUnfreeze`、`statusMeta(RECON_STATUS)`、`RECON_TYPES`
- Produces: `reconTypeText(type)`；`reconActions(status)` → `open: ['resolve','unfreeze']`，`resolved: []`；`validateResolveNote(note)`、`validateUnfreezeReason(reason)`

- [ ] **Step 1: 写失败测试**

```ts
expect(reconTypeText('missing_settlement')).toBe('漏单：已支付无结算单');
expect(reconTypeText('phantom_settlement')).toBe('虚单：结算单无支付流水');
expect(reconTypeText('amount_mismatch')).toBe('金额不一致');
expect(reconTypeText('status_mismatch')).toBe('退款后结算状态未收口');
expect(reconTypeText('ledger_mismatch')).toBe('账本与余额不一致');
expect(reconActions('open')).toEqual(['resolve', 'unfreeze']);
expect(validateResolveNote('')).toBe('请填写处理说明');
expect(validateUnfreezeReason('')).toBe('请填写解冻原因');
```

- [ ] **Step 2: RED**

Run: `npm --prefix ui test -- --runTestsByPath src/pages/marketplace/admin/reconView.test.ts --watchAll=false`
Expected: 模块不存在。

- [ ] **Step 3: 实现**

`ProTable`：业务日（默认昨日，`DatePicker` 可改）/类型（中文，`reconTypeText`）/订单ID/结算单ID/卖家ID/渠道金额/平台金额/状态/操作。处理弹窗：备注必填 → `adminReconResolve`；解冻按钮独立弹窗（原因必填，提交 `{sellerId: row.sellerId, reason}` → `adminReconUnfreeze`，后端按 `sellerId+tenantId` 定位账户）。成功后 reload。路由与菜单一致。

- [ ] **Step 4: GREEN + tsc**

Run: 同 Step 2；`cd ui && npx tsc --noEmit`。
Expected: 全绿；0 errors。

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/admin/Recon.tsx ui/src/pages/marketplace/admin/reconView.ts ui/src/pages/marketplace/admin/reconView.test.ts ui/config/routes.ts
git commit -m "feat(ui): 运营结算对账页"
```

---

### Task 7: Phase 1B 验收

**Files:** 无新增

- [ ] **Step 1: 前端全量测试**

Run: `cd ui && npm test -- --watchAll=false`
Expected: 全绿（新增 5 套件 + 既有 20 套件）。

- [ ] **Step 2: 类型检查**

Run: `cd ui && npx tsc --noEmit`
Expected: 0 errors。

- [ ] **Step 3: 菜单 / 路由 / 文件一致性断言**

Run:
```bash
for p in Kyc Settlement Withdraw Recon; do
  test -f ui/src/pages/marketplace/admin/$p.tsx || echo "MISSING $p.tsx"
  grep -q "marketplace/admin/$p'" ui/config/routes.ts || echo "MISSING route $p"
  grep -q "'marketplace/admin/$p'" backend/sql/settlement_menu.sql || echo "MISSING menu component $p"
done
grep -q "marketplace/MySettlement" ui/config/routes.ts backend/sql/settlement_menu.sql || echo "MISSING MySettlement"
```
Expected: 无 MISSING 输出。

- [ ] **Step 4: 后端权限复核（不跑业务）**

Run: `powershell -File scripts/check-perms.ps1 -DbPassword 123456`
Expected: `无漂移 ✅`。

- [ ] **Step 5: 记录结果并收尾**

写最终汇报；按 superpowers:finishing-a-development-branch 处理分支。

---

## Acceptance（Phase 1B 完成定义）

1. 运营可在「实名审核」通过/驳回卖家实名（对应 spec §7 人工实名）。
2. 运营可在「结算单」按状态筛选、看金额/售后期/渠道流水。
3. 运营可在「提现审核」审核（通过/驳回）并回填打款凭证置 `paid`。
4. 运营可在「结算对账」查看五类差异、处理并独立解冻。
5. C 端卖家可提交实名、查看余额、申请提现、查看自己的提现记录。
6. 五个页面路由、菜单 component、实际文件三者一致；前端全量测试与 `tsc` 绿。

## Out of Scope

- 组件级渲染测试（仓库无此先例，Phase 0B 已定纯函数测试惯例）。
- 真实打款凭证预览/下载、导出报表。
- 法务确认后的真实渠道页面文案（担保/分账宣传）。
- 手工端到端冒烟（需完整栈与种子数据，与 1A 一并留待首次联调）。

## Self-Review

- **Spec coverage**：§7「人工实名 + 人工提现 + mock 打款」的运营侧 → Task 3/5；结算可见性 → Task 4；对账队列 → Task 6；卖家侧 → Task 2；验收 6（无 SQL 手工改数）→ 全流程页面覆盖审核/打款/解冻。
- **类型一致性**：金额参数 `number`（后端 `BigDecimal` 反序列化为数字）；解冻接口统一收 `sellerId`（Phase 1A 已定，差异行只有 sellerId）；API 名称与 Task 1 Produces 一致。
- **Review Focus 落点**：1 → Task 2–6 接线 + Task 7 Step 3；2 → Task 1/4；3 → Task 1/2；4 → Task 2；5 → Task 6。
- **比例**：约 1.2 倍于 spec 的 §7 篇幅；无实现体转录，测试断言即契约。