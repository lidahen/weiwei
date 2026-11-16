# 商业化第四轮 — 上下文摘要（2026-08-29 节点）

> ## 当前快照（新会话先读这 8 行）
> - **master = 商业化 P0-P2 + 第四轮用户体系完成**：租户 MVP → 计费 → 租户管理 UI → 隔离（全业务表）→ 过期降级/AI 计量/用户绑定 → 扫码续费支付闭环 → 子域名支持 → **1 用户跨 N 租户（租户级登录 + token 绑定 + 用户读隔离）**。
> - **运行态**：后端 master jar 跑 8080（tenant.enabled=true, base-domain=localhost, billing.pay.mock=true）；UI dev server 8000（`thu.localhost:8000` 看租户店铺）；Redis 6379；MySQL root/123456 库 twelvet-fast。
> - **租户种子**：default=pro(不限)/thu=pku=free(100商品/50AI)，到期 2026-09-27（thu/pku）/2028（default）。
> - **用户种子（本轮新增，密码全 123456）**：alice=default+thu 同手机号串联（gid 相同，租户级登录演示）、bob/thuadmin=仅 thu（thuadmin 有 admin 角色=租户管理员）。
> - **文档地图**：实现细节 `docs/tenant-round4.md`（本轮）/ `tenant-{isolation-2,round3,p2-pay}.md`；各 `*-summary.md` 是续做入口；spec 在 `docs/superpowers/specs/2026-08-29-tenant-round4-design.md`。
> - **本会话验证基线**：node 矩阵 17/17 + 回滚开关 4 步 + 浏览器双租户登录（thu alice / default admin），全过；alice/bob/thuadmin 保留为演示数据。
> - **下一步**：见文末「下次续做的优先级」（第五轮：菜单/角色租户化 / C 端注册串联 / 支付记录页）。

> 下次继续这个工作，先读这个文件。前置：round3、p2-pay、subdomain 均已合 master。

## 当前位置

- **Branch**: `feature/tenant-round4`（worktree `.worktrees/tenant-round4`，已合并 master 则删）
- **内容**：1 用户跨 N 租户 MVP——round3 遗留边界「用户可从任意租户前端登录（绑定≠登录隔离）」本轮闭合

## 已完成 ✅（矩阵 17/17 + 回滚 4 步 + 浏览器实测全过，详见 `docs/tenant-round4.md`）

| 目标 | 状态 |
|---|---|
| 租户级登录：同名账号各租户独立解析（`selectUserByUserNameAndTenant`），无上下文走全局（兼容） | ✅ |
| token 绑定租户：LoginUser.tenantId + `TenantBindInterceptor`，换头 400；超管 userId=1 豁免 | ✅ |
| `sys_user.global_user_id`：回填=自身；同手机号跨租户建号自动串联（实测 alice 两行同 gid） | ✅ 实库已执行 |
| `uk_sys_user_tenant_username(tenant_id,username)` + username/phone/email 唯一性按租户 | ✅ |
| 用户读隔离：列表按上下文过滤（无上下文=平台视角）+ `checkUserDataScope` 补 userId 条件（跨租户写全拦） | ✅ |
| role 1 补全菜单映射（租户管理员 = 本租户 admin 角色用户，本轮可用） | ✅ 实库已执行 |
| 验证中修复：checkUserDataScope 缺 userId 条件 / 超管豁免死锁 / role 1 菜单缺失 | ✅ |

## 关键决策（不要重新讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 用户模型 | 一行 sys_user = 一租户成员关系，跨租户靠 global_user_id 串联 | 不引 membership 表；与业务表隔离同构 |
| global_user_id 取值 | 首个身份的 user_id；同手机号跨租户建号自动串联 | 与存量对齐、可调试；本轮仅锚点列无运行时行为 |
| 串联键 | 手机号（非空才查）；email 不串联 | 手机号最可靠；假手机号误串联可接受（文档注明） |
| 登录解析 | 上下文非空→按租户查；为空→全局（兼容） | 上下文非空即开关开启；回滚零侵入 |
| token 绑定 | 不一致→400；匿名/无上下文/旧 token 跳过；**超管豁免** | 关换头穿越；超管豁免防「无法给其他租户建管理员」死锁 |
| 平台视角 | 无上下文请求=用户列表不过滤；有上下文强制覆盖 tenantId 参数 | 与商品列表兼容模式对称；防 ?tenantId 越权枚举 |
| 跨租户写防护 | checkUserDataScope 注入上下文 + selectUserList 补 userId 条件 | edit/remove/resetPwd/changeStatus 零新增代码全被拦 |
| UI | 零改动 | 登录请求已带 header；Staff 列表自动按上下文过滤 |

## 已知边界（不掩盖）

- ~~GitHub OAuth 登录未按租户圈定~~ → 已整移除（2026-08-29，国内用户不友好且后端端点本就缺失）；无 C 端注册。
- 角色/菜单/部门全局共享：租户管理员权限全同平台（菜单裁剪=第五轮）。
- 超管 token 不绑租户（平台运营语义）；存量旧 token 无 tenantId 跳过绑定，重登生效。
- 单元测试 0。

## 下次续做的优先级（按 ROI）

### P3（第五轮候选）
- [ ] **角色/菜单租户化**（租户管理员菜单裁剪，或 sys_role 加 tenant_id）
- [x] **GitHub 登录移除**（2026-08-29：登录页按钮/组件/回调页/路由全删，后端端点本就不存在）
- [ ] ~~C 端注册~~ **缓办**（需对接阿里云等短信服务，用户决策后置；做其他功能改造）
- [ ] 支付记录管理页（trade_pay 列表+对账，p2-pay 遗留）
- [ ] 审计日志、抽佣闭环、AI 用量月账单

## 环境坑（本会话实测，务必避开）

- **undici（node fetch）剥 Host 头**：带 Host 的子域名用例走 `node:http`，否则静默变兼容模式（全租户混出）。
- **Windows 重启后端先 taskkill**：jar 被运行进程锁定，repackage rename 失败。
- **`-pl twelvet-admin -am` 打包无主清单**（跳过 repackage），要全量 `mvn package`。
- Bigfish dev 弹窗/chunk 陈旧/提交卫生：见 `tenant-p2-pay-summary.md` 环境坑节。

## 启动方式

```bash
/e/Redis-8.8.0-Windows-x64-cygwin-with-Service/redis-server.exe --port 6379 --daemonize yes
cd E:/code/weiwei/backend && java -jar twelvet-admin/target/twelvet-admin.jar   # tenant.enabled=true 默认
cd E:/code/weiwei/ui && node_modules/.bin/max dev
# thu 租户：http://thu.localhost:8000/login → alice/123456 → /home 仅 thu 商品
# default：http://localhost:8000/login → admin/123456
```

## 回滚

`tenant.enabled=false`（全链路关，global_user_id 列无害）；DDL 尾注释 DROP；`git revert <merge>`。

---

**用户体系第四轮闭环完成。下一步建议：第五轮角色/菜单租户化 或 C 端注册。**
