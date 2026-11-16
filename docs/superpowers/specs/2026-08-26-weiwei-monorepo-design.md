# Weiwei Monorepo Migration Design

**Date:** 2026-08-26
**Status:** Approved
**Author:** Claude (with lixin)
**Target repo:** `https://github.com/lidahen/weiwei.git`

## Background

`F:\code\weiwei\` 目录下当前有三个独立服务，每个都是独立 git 仓库：

| 目录 | 技术栈 | 远程仓库 | HEAD |
|------|--------|---------|------|
| `twelvet-fast/` | Java Spring Boot 3.5.4 | `example.com/private/neighborswap.git` | `78e4e12` |
| `twelvet-ai-service/` | Python FastAPI | `example.com/private/neighborswap.git` | `c21022b` |
| `twelvet-ui-master/` | Umi Max + React 18 | `example.com/private/neighborswap-ui.git` | `1b39cbc` |

**痛点：**
1. 三个仓库、两套发布模型（Java+Python 共享 gitee 仓库，UI 独立），版本号需要手工同步
2. Git 协作易出错（曾在 Python 服务目录里 pull Java 仓库导致 unrelated histories 冲突）
3. 跨服务改动需要三个 PR、三个 release tag

**架构关系（commit `4681ec6` 明确）：** Java 主项目通过 HTTP 代理调用 Python AI 服务，前端通过 Umi 代理调用后端。三者是一套产品的三部分，应该一起发布。

## Goals

- **G1**: 三个服务用同一个 git 仓库管理，从物理结构上杜绝"pull 错仓库"
- **G2**: 一套版本号（`v6.0.0` 风格）覆盖整个产品
- **G3**: 跨服务改动可以在一个 commit / 一个 PR 内完成
- **G4**: 各服务的内部历史仍可独立追溯（`git log --follow <file>` 可看完整 history）
- **G5**: 不丢失任何文件（.env、FAISS 索引、未跟踪文件、历史 commit 全部保留）

## Non-Goals

- **NG1**: 不改变三个服务的运行时架构（Java 仍 HTTP 代理 Python，UI 仍走 Umi 代理）
- **NG2**: 不引入 monorepo 构建工具（nx / turborepo / lerna 等）—— 三个服务各自独立构建，根目录只做编排
- **NG3**: 不删除老的 gitee 仓库 `neighborswap` 和 `neighborswap-ui`（保留作长期回滚保险）
- **NG4**: 不做权限拆分 / 团队级 Git 权限（单人项目不需要）

## Architecture Decision

**决策：** 采用 **完整 Monorepo**（一个仓库包含三个服务子目录），用 **`git subtree add --squash`** 合并历史。

**评估过的方案：**

| 方案 | 评分 | 原因 |
|------|------|------|
| A. 完整 Monorepo + subtree 合并 | ✅ 选中 | 直接解决 G1/G2/G3；历史可追溯；操作可逆 |
| B. 后端合并 + UI 独立 | ❌ | 仍有 2 仓库；UI 与后端的版本同步问题没解决 |
| C. 保持现状 + manifest | ❌ | 仓库结构没变，G1 没解决 |

## Target Repository Layout

```
weiwei/                                          ← 新仓库根 (gitee.com:lixin/weiwei.git)
├── .gitignore                                   ← 统一忽略（合并三个服务的规则）
├── .env.example                                 ← 全局环境变量模板
├── docker-compose.yml                           ← 根目录编排三服务
├── README.md                                    ← 仓库总入口
├── docs/                                        ← 项目文档（保留现有）
│   └── superpowers/
│       ├── plans/
│       └── specs/
│              └── 2026-08-26-weiwei-monorepo-design.md   ← 本文档
├── openspec/                                    ← 保留现有
├── CLAUDE.md                                    ← 保留现有
├── .atomcode/                                   ← 保留现有
├── Superpowers新功能开发指令.md                  ← 保留现有
│
├── backend/                                     ← Java 主项目（来自 twelvet-fast/）
│   ├── pom.xml
│   ├── twelvet-admin/
│   ├── twelvet-framework/   (9 个子模块)
│   ├── twelvet-system/
│   ├── twelvet-product/
│   ├── twelvet-trade/
│   ├── twelvet-gen/
│   ├── twelvet-quartz/
│   ├── twelvet-im/
│   ├── twelvet-dfs/
│   └── data/                                    ← SQL 脚本
│
├── ai-service/                                  ← Python AI 服务（来自 twelvet-ai-service/）
│   ├── app/
│   │   ├── main.py
│   │   ├── config.py
│   │   ├── database.py
│   │   ├── models/
│   │   ├── routers/
│   │   ├── schemas/
│   │   └── services/
│   ├── tests/
│   ├── requirements.txt
│   ├── requirements.lock.txt
│   ├── Dockerfile
│   ├── start.py
│   ├── start.sh
│   ├── start.bat
│   ├── .env.example                             ← 模板（不含密钥）
│   └── data/                                    ← FAISS 索引目录（gitignore）
│
└── ui/                                          ← 前端（来自 twelvet-ui-master/）
    ├── package.json
    ├── src/
    ├── config/
    └── ...
```

## Key Configurations

### Root `.gitignore`

合并三个服务的忽略规则，分组清晰：

```gitignore
# === Java (backend/) ===
.gradle
/build/
target/
!.mvn/wrapper/maven-wrapper.jar
.idea/
*.iws
*.iml
*.ipr
*.log
*.xml.versionsBackup

# === Python (ai-service/) ===
__pycache__/
*.pyc
*.pyo
.pytest_cache/
*.egg-info/
dist/
build/
.venv/
venv/

# === Node (ui/) ===
node_modules/
dist/
.parcel-cache/
.yarn/
.pnp.*

# === Secrets (所有服务) ===
.env
.env.local
*.pem
*.key

# === Data (运行时生成) ===
data/faiss_indexes/   # FAISS 索引（重建成本低，不入库）
data/uploads/

# === IDE / OS / 临时 ===
.DS_Store
Thumbs.db
.vscode/
.archive/             # 迁移期临时备份目录（30 天后删）
```

### Root `docker-compose.yml`

把 `twelvet-fast/docker/docker-compose.yml` 提升到 monorepo 根，路径改为相对：

```yaml
version: '3.9'
services:
  mysql:
    image: mysql:8.0.32
    # ... (沿用原配置)
  redis:
    image: redis:7.2.4
  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on: [mysql, redis]
    # env_file: ./backend/.env.pro   # 仅在本地有 .env.pro 时取消注释
  ai-service:
    build: ./ai-service
    ports: ["8000:8000"]
    env_file: ./ai-service/.env   # .env 不入库，本地维护
  ui:
    build: ./ui
    ports: ["3000:3000"]
    depends_on: [backend]
```

### `.env` 管理

| 文件 | 位置 | 入库 | 维护方 |
|------|------|------|--------|
| `ai-service/.env` | `ai-service/` | ❌ gitignore | 开发者本地 |
| `ai-service/.env.example` | `ai-service/` | ✅ | 仓库（密钥脱敏） |
| `backend/.../application-dev.yml` | `backend/` | ✅ | 仓库（无密钥） |
| `backend/.../application-pro.yml` | `backend/` | ✅ | 仓库（Jasypt 加密） |

**关键迁移操作（必做）**：从 `.archive/twelvet-ai-service/.env` 复制到新位置 `ai-service/.env`（因 .env 不入库，必须手动复制）。

### 版本号规范

- monorepo 顶层 tag：`v6.0.0`（一套版本号覆盖整个产品）
- 子目录如需独立版本号（极少用）：`backend-v6.0.0`、`ai-service-v6.0.0` 等
- README 中维护 `## Releases` 段落记录每次发版

## Migration Procedure

### Phase 1: 准备与安全检查

```bash
cd F:\code\weiwei

# 1.1 三个旧仓库必须 working tree clean
for d in twelvet-fast twelvet-ai-service twelvet-ui-master; do
  (cd $d && \
   [ -z "$(git status --porcelain)" ] && echo "✓ $d clean" || \
   (echo "✗ $d has uncommitted changes — stop and fix first"; exit 1))
done

# 1.2 备份所有未跟踪的关键文件
ls twelvet-ai-service/.env 2>/dev/null && \
  cp twelvet-ai-service/.env /tmp/.env.original
ls twelvet-ai-service/data/faiss_indexes/ 2>/dev/null && \
  cp -r twelvet-ai-service/data/faiss_indexes /tmp/faiss_indexes.original
```

### Phase 2: 检查并备份 weiwei/ 根

```bash
cd F:\code\weiwei

# 2.1 检查 weiwei/ 根是否已有 .git
# 预期：没有；如果有，记录并备份
if [ -d .git ]; then
  echo "WARN: weiwei/ 已有 .git 目录，按计划会被新仓库替换"
  mv .git .git.bak
fi
```

### Phase 3: 创建新仓库 + 三次 subtree add

```bash
# 3.1 在 Gitee 上手动创建空仓库 weiwei（已完成，2026-08-26 by lixin）
# 仓库 URL: https://github.com/lidahen/weiwei.git

# 3.2 在 weiwei/ 根初始化新仓库
cd F:\code\weiwei
git init -b master
git remote add origin https://github.com/lidahen/weiwei.git

# 3.3 第一次提交：项目元数据（CLAUDE.md、docs、openspec 等）
git add CLAUDE.md .atomcode docs openspec Superpowers新功能开发指令.md \
        AI功能开发路由指令.md agent.md agent异常处理.md \
        模型调用文档说明.md 2>/dev/null
git commit -m "chore: bootstrap weiwei monorepo meta files"

# 3.4 subtree add 三个服务（顺序：先大的，再小的，减少冲突概率）
git subtree add --prefix=backend      ../twelvet-fast        master --squash
git subtree add --prefix=ai-service   ../twelvet-ai-service  master --squash
git subtree add --prefix=ui           ../twelvet-ui-master   master --squash
```

### Phase 4: 处理冲突与产物

- 若 subtree add 报告冲突（极少见，因为是空仓库起步），按 git 提示解决后 `git commit`
- 验证三个目录的 .gitignore 文件名（重命名为小写等）—— 不强制改名

### Phase 5: 推送新仓库

```bash
git push -u origin master
# 验证 Gitee 页面能看到三个子目录
```

### Phase 6: 归档旧目录

```bash
# 6.1 创建归档目录，移走三个老目录（保留 30 天保险）
mkdir -p F:\code\weiwei\.archive
mv F:\code\weiwei\twelvet-fast        F:\code\weiwei\.archive\
mv F:\code\weiwei\twelvet-ai-service  F:\code\weiwei\.archive\
mv F:\code\weiwei\twelvet-ui-master   F:\code\weiwei\.archive\

# 6.2 还原 .env 和 FAISS 索引到新位置（关键！）
cp /tmp/.env.original           F:\code\weiwei\ai-service/.env
cp -r /tmp/faiss_indexes.original F:\code\weiwei\ai-service/data/faiss_indexes/

# 6.3 清理 weiwei/ 的 .git.bak
rm -rf F:\code\weiwei\.git.bak
```

## Verification Checklist

迁移完成后**必须**逐项验证：

| # | 验证项 | 期望结果 |
|---|--------|---------|
| V1 | `git log --oneline -10` | 看到 1 个 bootstrap + 3 个 subtree add commit |
| V2 | `ls backend/ ai-service/ ui/` | 三个子目录都在 |
| V3 | `ls backend/twelvet-admin/ ai-service/app/ ui/src/` | 各服务核心目录完整 |
| V4 | `git log --follow backend/pom.xml \| head -5` | 看到 Java 历史 |
| V5 | `git log --follow ai-service/app/main.py \| head -5` | 看到 Python 历史 |
| V6 | `git log --follow ui/package.json \| head -5` | 看到 UI 历史 |
| V7 | `ls .archive/` | 三个老目录都在 |
| V8 | `ls ai-service/.env` | .env 已从 .archive 复制回来 |
| V9 | `ls ai-service/data/faiss_indexes/` | FAISS 索引已从 .archive 恢复到工作目录（gitignored 文件） |
| V10 | `git remote -v` | 看到 `gitee.com:lixin/weiwei.git` |
| V11 | `cd backend && mvn -version` | Java 工具链正常 |
| V12 | `cd ai-service && python --version` | Python 工具链正常 |
| V13 | `cd ui && node --version` | Node 工具链正常 |
| V14 | `git status` | clean（不算 ai-service/.env 和 data/faiss_indexes/ 这两个 gitignored） |

## Rollback Plan

| 阶段 | 回滚方式 | 数据损失风险 |
|------|---------|-------------|
| Phase 1-4 失败（未推送） | `rm -rf .git` → 恢复 `.git.bak` → 删除已建子目录 | 无（所有改动都在 weiwei/ 内） |
| Phase 5 推送后 24 小时内 | Gitee 删仓库 → `rm -rf .git` → 恢复 `.git.bak` → 把 `.archive/` 三个目录移回原位 | 无 |
| 稳定 30 天后 | 删除 `.archive/` 整个目录 | 不可逆（但 gitee 老仓库仍可访问） |

**数据保护硬约束**：
1. `.archive/` 目录绝不动（除非用户明确同意）
2. `.env` 和 FAISS 索引有显式复制步骤（Phase 1 备份 + Phase 6 还原）
3. 每个 phase 后做 V1-V14 验证

## Risks

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Gitee push 失败（网络/认证/仓库大小） | 低 | 阻塞 | 失败时本地已完整，可重试或换 SSH/HTTPS |
| subtree add 冲突 | 极低 | 阻塞 | 空仓库起步，无冲突源 |
| .env 复制遗漏 | 中 | AI 服务无法启动 | V8 显式检查 + .env.example 提示 |
| FAISS 索引路径错 | 低 | 知识库丢失 | V9 显式检查 + 备份在 .archive/ |
| 老仓库被误删 | 极低 | 历史丢失 | .archive/ 30 天保护期 |
| `git subtree` 后续协作 | 中 | 新人不熟 | README 写明 `git subtree pull --prefix=<dir> <remote>` 用法 |

## Follow-up Tasks（迁移后）

1. 写 `weiwei/README.md` 介绍三服务启动方式（`docker compose up` 一键）
2. 更新 `CLAUDE.md` 把双服务架构段落改为"monorepo 内的三服务"
3. 更新 `weiwei-dual-service-architecture` memory 为 monorepo 视角
4. 给三个 gitee 老仓库加 `ARCHIVED` 说明（不删）
5. 30 天后清理 `.archive/`

## Decision Log

- 2026-08-26: 用户确认需求（统一版本 + 避免协作混乱 + 全栈一起发布）
- 2026-08-26: 选定方案 A（完整 monorepo）+ subtree --squash
- 2026-08-26: 确认子目录命名 `backend/ ai-service/ ui/`
- 2026-08-26: 确认仓库名 `weiwei`
- 2026-08-26: 用户授权"看着弄，只要不丢失文件"
