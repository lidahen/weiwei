# weiwei · 前端（`ui/`）

Weiwei 商城的前端工程：**管理端（B 端）+ 用户端（C 端）**。

> 本 README 于 2026-10-13 重写。此前这里放的是上游 `twelvet-projects/twelvet`
> （Spring Cloud Alibaba **微服务**脚手架）的 README，与本仓库没有任何关系，会严重误导接手者。

---

## 技术栈

| 项 | 值 |
|---|---|
| 框架 | Umi Max 4.x（`@umijs/max`） |
| UI | React 18 + TypeScript + Ant Design 5.x + `@ant-design/pro-components` |
| 图表 | ECharts 5.x |
| 状态/请求 | `@umijs/max` 的 `request`（返回**完整响应体** `{code,msg,data}`）+ ahooks |
| 包管理器 | **npm**（⚠️ 不要用 yarn —— 见下方「包管理器」） |
| Node | 22 LTS（`engines: >=12.0.0`，实测 22.22.1） |

---

## 架构不变量（务必遵守）

**前端只与 Java BFF(8080) 通信，由 Java 再转发到 Python `ai-service`(8001)。**

任何让 `/api/**` 直连 8001 的做法都是 bug：会绕过鉴权 / 租户 / 配额，且 Python 侧部分接口
强制要求 `X-User-Id`（只有 Java 会注入），直连必然 `401 AUTH_REQUIRED`。新增 AI 接口时若 Java 侧
缺路由，应去 `backend/twelvet-ai` 补转发，**不要在这里加代理**。依据见 `config/proxy.ts` 的注释。

---

## 快速开始

```bash
cd ui
npm ci                    # 严格按 package-lock.json 安装（依赖可复现；首次约 3-5 分钟）
npm run start:dev         # 开发服务器，默认 8000 端口
# 端口与 ai-service 默认端口冲突时指定其他端口：
PORT=3000 npm run start:dev
```

国内加速（可选）：`npm config set registry https://registry.npmmirror.com`

**构建**：`npm run build` → 产物在 `ui/dist/`（由 Caddy 托管；生产走同源 `/api`，无需代理）。

---

## 配置

| 位置 | 作用 |
|---|---|
| `src/setting.tsx` | 请求地址（`isDev ? 'http://localhost:8080' : '/api'`）、token 键名 `TWT_access_token`、上传地址 `/dfs/commonUpload`、OSS 静态域名 |
| `config/proxy.ts` | dev 代理：`/api/` → `http://localhost:8080`，`pathRewrite` 去掉 `/api` 前缀 |
| `config/config.ts` | Umi 配置（路由、主题、`hash: true` 资源指纹） |
| `.env` | 环境开关（**当前内容只有注释，无密钥**） |

> ⚠️ `config/proxy.ts` 里的 `test` / `pre` 两段仍指向上游演示站与占位符 `'your pre url'`，
> 使用 `start:test` / `start:pre` 前需自行改对。

---

## 目录结构

```
ui/
├── config/            # Umi 配置与 dev 代理
├── public/            # 静态资源
├── src/
│   ├── pages/         # 页面（按业务域分目录：System/AI/Trade/Product/…）
│   ├── components/    # 公共组件（含 TwelveT/ 下的历史组件）
│   ├── services/      # 接口封装
│   ├── utils/         # 工具（含 tenant 解析、twelvet 请求辅助）
│   ├── requestErrorConfig.ts   # 请求/响应拦截器、错误处理、租户头注入
│   └── app.tsx        # 运行时配置（布局、主题 token）
├── tests/             # jest setup（真实用例在各页面目录内，*.test.tsx）
└── jest.config.ts
```

---

## 质量门禁

前端部分由仓库根的 `verify.sh` 统一执行（**不要再单独记忆命令**）：

```bash
cd /f/code/weiwei && bash verify.sh      # Windows Git Bash
```

其中与前端相关的两项：

| 检查 | 命令 | 当前状态 |
|---|---|---|
| 类型检查 | `node node_modules/typescript/bin/tsc --noEmit` | ✅ 0 error（2026-10-13 前有 18 个，已修） |
| 单元测试 | `node node_modules/jest/bin/jest.js --ci` | ✅ 2 passed（`ShoppingGuide` 页面与 SSE 封装） |

---

## 已知问题（截至 2026-10-13）

1. **登录页预填 demo 账号与口令，并硬编码 Basic 认证串** —— 且**已打进 `ui/dist`**。
   生产站点打开登录页即可看到默认凭据。按用户决定，该项排在阶段 D（凭据治理）处理。
2. **真实测试用例仅 2 个**（`src` 下 300+ 文件），覆盖率不是可信信号。
3. `config/proxy.ts` 的 `test`/`pre` 环境配置不可用（见上）。
4. 仓库原先未交付任何锁文件（三把锁全被 `.gitignore` 忽略），2026-10-13 起交付
   `package-lock.json` 并统一为 npm。

---

## 相关文档

- 新人总入口：[../docs/ONBOARDING.md](../docs/ONBOARDING.md)
- 项目结构与后端/AI 端说明：[../CLAUDE.md](../CLAUDE.md)
- 可交付落地改造设计与计划：[../docs/superpowers/specs/2026-10-13-delivery-readiness-design.md](../docs/superpowers/specs/2026-10-13-delivery-readiness-design.md)
