# 管理台 UI 统一改造（第二轮 UI 轮）设计

- 日期：2026-08-29
- 主题：修复坏点（用户管理崩溃/父路由空白）+ 管理台全局视觉统一
- 状态：执行中（纯 UI 小轮次，直接 master 提交，与欢迎页移除同流程）

## 背景

全局主题（Forest+Cream+Sand，墨绿主色）已由 app.tsx rootContainer ConfigProvider 覆盖所有页面；
但巡查发现三类问题：

| # | 问题 | 根因 |
|---|---|---|
| 1 | `/human/staff`（用户管理）整页崩溃 `Cannot read properties of undefined (reading 'deptName')` | `row.dept.deptName` 未判空——第四轮 API 建的测试用户无 dept_id，`selectUserList` 的 dept 关联为空 |
| 2 | `/admin/marketplace`、`/cs/marketplace` 父路由直接访问只有页脚，内容区空白 | 父路由无 index 子路由/redirect（子页面本身正常） |
| 3 | 各管理页观感不统一：默认 PageContainer 灰头、表格直铺、无卡片感 | 无全局页面级样式约束（token 只管组件色板） |

## 改动

1. `Human/Staff/index.tsx`：`row.dept?.deptName ?? '-'`。
2. `routes.ts`：`/admin/marketplace` redirect → `/admin/marketplace/dashboard`；`/cs/marketplace` redirect → `/cs/marketplace/disputes`。
3. `global.less` 追加「管理台统一视觉」段：PageContainer 头部 cream 渐变 + 标题排版、内容区卡片化间距、表头浅墨绿底、卡片轻阴影、细滚动条。一次覆盖所有管理页，不逐页改动。

## 验证

- 巡查 8 个代表页无崩溃/空白；
- /human/staff 正常出表格（无部门用户显示 -）；
- 父路由跳转到子页；
- 截图对比统一视觉生效。

## 验证记录（2026-08-29 实测）

| # | 用例 | 结果 |
|---|---|---|
| 1 | /human/staff | 表格 8 行、无部门用户显示「-」、无崩溃 ✅ |
| 2 | /admin/marketplace → 子页 | redirect 到 dashboard 且 KPI 正常渲染 ✅ |
| 3 | /cs/marketplace → 子页 | redirect 到 disputes 且工单表渲染 ✅ |
| 4 | 8 页巡查 | 全部有内容、无 TypeError ✅ |
| 5 | 视觉 | 页头 cream 渐变+表头浅墨绿+卡片阴影生效（截图 docs/ui-after-unify-role.png，改造前对比 docs/ui-audit-ops.png）✅ |

实现补充说明：
- umi 4 父级 redirect 与 routes 并存会吞掉子页渲染——必须用「与父路径相同的空路径子路由 redirect」写法。
- 用户管理崩溃是第四轮 API 建号（无 dept_id）暴露的存量脆弱点，同类判空已排查（Human 目录仅此一处）。
