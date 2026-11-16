# Weiwei Monorepo Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge three independent git repos (twelvet-fast Java, twelvet-ai-service Python, twelvet-ui-master React) into a single monorepo at `F:\code\weiwei\` and push to `https://github.com/lidahen/weiwei.git`.

**Architecture:** Use `git subtree add --prefix=<dir> --squash` to merge each service's history into a subdirectory. Preserve untracked files (`.env`, FAISS indexes) via explicit backup-restore. Archive old repos to `.archive/` for 30-day rollback insurance. Root-level configs (`.gitignore`, `docker-compose.yml`, `.env.example`, `README.md`) created manually.

**Tech Stack:** git 2.x (Windows), Gitee HTTPS remote, three existing services preserved as-is

**Spec:** `F:\code\weiwei\docs\superpowers\specs\2026-08-26-weiwei-monorepo-design.md`

## Global Constraints

- **Remote URL:** `https://github.com/lidahen/weiwei.git` (HTTPS, not SSH)
- **Subdirectory names:** `backend/` (Java), `ai-service/` (Python), `ui/` (React)
- **Subtree method:** `git subtree add --prefix=<dir> --squash` (squash per service, internal history preserved via `git log --follow`)
- **`.env` and FAISS indexes:** NEVER in git; explicit backup-restore only
- **Old repos location after migration:** `weiwei/.archive/` (do not delete for 30 days)
- **All destructive operations require verification before commit**
- **No service code modification during migration** (preserves history cleanly)
- **Local git config user:** `lixin <you@example.com>` (already set globally based on prior commits)

---

## File Structure

**Created at `weiwei/` root:**
- `.gitignore` — Combined ignore rules (Java, Python, Node, secrets, runtime data, OS, `.archive/`)
- `docker-compose.yml` — Three-service orchestration (mysql, redis, backend, ai-service, ui)
- `.env.example` — Global env var template (no real secrets)
- `README.md` — Monorepo entry point with quickstart

**Migrated via git subtree (preserved with full internal history):**
- `backend/` ← `twelvet-fast/` (Java Spring Boot, `78e4e12`)
- `ai-service/` ← `twelvet-ai-service/` (Python FastAPI, `c21022b`)
- `ui/` ← `twelvet-ui-master/` (Umi Max React, `1b39cbc`)

**Preserved as untracked working-tree files (gitignored, never committed):**
- `ai-service/.env` (with API keys)
- `ai-service/data/faiss_indexes/kb_5.faiss`, `kb_5.json`, `kb_6.faiss`, `kb_6.json`

---

## Task 1: Pre-flight Safety Checks

**Files:** None (read-only inspection)

- [ ] **Step 1: Verify three sub-repos are clean**

```bash
cd /f/code/weiwei
for d in twelvet-fast twelvet-ai-service twelvet-ui-master; do
  echo "=== $d ==="
  (cd $d && git status --porcelain | wc -l)
done
```

**Expected:** All three print `0` (clean). If any print non-zero, STOP and ask user to commit/stash.

- [ ] **Step 2: Record HEADs of three repos (for migration report)**

```bash
cd /f/code/weiwei
echo "Backend (Java): $(cd twelvet-fast && git rev-parse --short HEAD) - $(cd twelvet-fast && git log -1 --format=%s)"
echo "AI Service:     $(cd twelvet-ai-service && git rev-parse --short HEAD) - $(cd twelvet-ai-service && git log -1 --format=%s)"
echo "UI:             $(cd twelvet-ui-master && git rev-parse --short HEAD) - $(cd twelvet-ui-master && git log -1 --format=%s)"
```

**Expected:**
```
Backend (Java): 78e4e12 - feat: 提交文件
AI Service:     c21022b - feat: 提交文件
UI:             1b39cbc - feat: 提交文件
```

- [ ] **Step 3: Backup `.env` and FAISS indexes (CRITICAL — these are untracked)**

```bash
mkdir -p /tmp/weiwei-backup
cp /f/code/weiwei/twelvet-ai-service/.env /tmp/weiwei-backup/.env.original
cp -r /f/code/weiwei/twelvet-ai-service/data/faiss_indexes /tmp/weiwei-backup/
ls -la /tmp/weiwei-backup/ /tmp/weiwei-backup/faiss_indexes/
```

**Expected:**
- `/tmp/weiwei-backup/.env.original` (~777 bytes)
- `/tmp/weiwei-backup/faiss_indexes/` contains `kb_5.faiss`, `kb_5.json`, `kb_6.faiss`, `kb_6.json`

- [ ] **Step 4: Verify backup integrity (byte-level)**

```bash
diff -q /f/code/weiwei/twelvet-ai-service/.env /tmp/weiwei-backup/.env.original && echo "✓ .env backup identical"
diff -r /f/code/weiwei/twelvet-ai-service/data/faiss_indexes /tmp/weiwei-backup/faiss_indexes && echo "✓ FAISS indexes backup identical"
```

**Expected:** Both diffs succeed (no output before "✓" line). If any diff fails, STOP — re-do backup.

- [ ] **Step 5: No commit needed** (Task 1 is read-only inspection)

---

## Task 2: Initialize New Monorepo at `weiwei/` Root

**Files:**
- Create: `weiwei/.git/` (via `git init`)
- Modify: `weiwei/.git/config` (via `git remote add`)

- [ ] **Step 1: Check if `weiwei/` has existing `.git` (should NOT)**

```bash
cd /f/code/weiwei
[ -d .git ] && echo "WARN: weiwei/ has .git — must back up first" || echo "✓ no existing .git"
```

**Expected:** `✓ no existing .git`. If WARN appears, STOP and resolve.

- [ ] **Step 2: Initialize new git repo with `master` branch**

```bash
cd /f/code/weiwei
git init -b master
```

**Expected:** `Initialized empty Git repository in F:/code/weiwei/.git/`

- [ ] **Step 3: Add remote**

```bash
cd /f/code/weiwei
git remote add origin https://github.com/lidahen/weiwei.git
git remote -v
```

**Expected:**
```
origin	https://github.com/lidahen/weiwei.git (fetch)
origin	https://github.com/lidahen/weiwei.git (push)
```

- [ ] **Step 4: Configure local user (fallback if global missing)**

```bash
cd /f/code/weiwei
git config user.name 2>/dev/null || git config user.name "lixin"
git config user.email 2>/dev/null || git config user.email "you@example.com"
git config user.name && git config user.email
```

**Expected:** `lixin` and `you@example.com` printed.

- [ ] **Step 5: Verify clean state (no files tracked yet)**

```bash
cd /f/code/weiwei
git status
```

**Expected:** `On branch master` / `No commits yet` / `Untracked files:` (list of files including `twelvet-fast/`, `twelvet-ai-service/`, `twelvet-ui-master/`, etc.)

---

## Task 3: First Commit — Project Meta Files

**Files committed in this task:**
- `CLAUDE.md`
- `.atomcode/`
- `docs/`
- `openspec/`
- `Superpowers新功能开发指令.md`
- `AI功能开发路由指令.md`
- `agent.md`
- `agent异常处理.md`
- `模型调用文档说明.md`

**Files NOT committed yet:** `twelvet-fast/`, `twelvet-ai-service/`, `twelvet-ui-master/` (will come via subtree add in Tasks 4-6), `ai-service/.env`, `data/faiss_indexes/`

- [ ] **Step 1: Add meta files explicitly (avoid accidentally adding old repos)**

```bash
cd /f/code/weiwei
git add CLAUDE.md .atomcode docs openspec \
        Superpowers新功能开发指令.md AI功能开发路由指令.md \
        agent.md agent异常处理.md 模型调用文档说明.md
```

- [ ] **Step 2: Verify what will be committed**

```bash
cd /f/code/weiwei
git status --short
```

**Expected:** List of `A` (added) entries for the 9 files/dirs above; NO entries for `twelvet-fast/`, `twelvet-ai-service/`, `twelvet-ui-master/`.

- [ ] **Step 3: First commit (bootstrap)**

```bash
cd /f/code/weiwei
git commit -m "chore: bootstrap weiwei monorepo meta files

- Project guidance: CLAUDE.md
- Architecture docs: .atomcode/
- Process docs: docs/ (superpowers, etc.)
- OpenSpec: openspec/
- Working notes: AI功能开发路由指令.md, agent.md, etc."
```

**Expected:** `[master (root-commit) <hash>] chore: bootstrap...`

- [ ] **Step 4: Verify commit**

```bash
cd /f/code/weiwei
git log --oneline -1
```

**Expected:** One commit shown with the message above.

---

## Task 4: Subtree Add — Backend (Java)

**Files migrated:** `twelvet-fast/*` → `backend/*`

- [ ] **Step 1: Add backend subtree**

```bash
cd /f/code/weiwei
git subtree add --prefix=backend ../twelvet-fast master --squash
```

**Expected:** Output like `git fetch ../twelvet-fast master` then merge commit. May take 10-30s for history scan.

- [ ] **Step 2: Verify `backend/` exists with Java files**

```bash
cd /f/code/weiwei
ls backend/ | head -20
```

**Expected:** Java project files like `pom.xml`, `twelvet-admin/`, `twelvet-framework/`, `twelvet-system/`, `twelvet-product/`, etc.

- [ ] **Step 3: Verify Java history preserved**

```bash
cd /f/code/weiwei
git log --follow backend/pom.xml --oneline | head -5
```

**Expected:** At least 2 commits — newest is the squash merge, older is the original Java commit.

- [ ] **Step 4: Verify working tree clean**

```bash
cd /f/code/weiwei
git status
```

**Expected:** `nothing to commit, working tree clean`

---

## Task 5: Subtree Add — AI Service (Python)

**Files migrated:** `twelvet-ai-service/*` → `ai-service/*`

- [ ] **Step 1: Add ai-service subtree**

```bash
cd /f/code/weiwei
git subtree add --prefix=ai-service ../twelvet-ai-service master --squash
```

**Expected:** Similar to Task 4 — fetch from local, merge commit.

- [ ] **Step 2: Verify `ai-service/` exists with Python files**

```bash
cd /f/code/weiwei
ls ai-service/
```

**Expected:** `app/`, `requirements.txt`, `Dockerfile`, `start.py`, `tests/`, `.env.example`, etc.

- [ ] **Step 3: Verify Python history preserved**

```bash
cd /f/code/weiwei
git log --follow ai-service/requirements.txt --oneline | head -5
```

**Expected:** At least 2 commits.

- [ ] **Step 4: Restore `.env` from backup (CRITICAL — must happen after subtree add, before continuing)**

```bash
cd /f/code/weiwei
mkdir -p ai-service/data
cp /tmp/weiwei-backup/.env.original ai-service/.env
ls -la ai-service/.env
```

**Expected:** File exists, ~777 bytes.

- [ ] **Step 5: Restore FAISS indexes from backup**

```bash
cd /f/code/weiwei
cp -r /tmp/weiwei-backup/faiss_indexes ai-service/data/
ls -la ai-service/data/faiss_indexes/
```

**Expected:** 4 files (`kb_5.faiss`, `kb_5.json`, `kb_6.faiss`, `kb_6.json`).

- [ ] **Step 6: Verify restored files match originals (byte-level)**

```bash
cd /f/code/weiwei
diff -q /tmp/weiwei-backup/.env.original ai-service/.env && echo "✓ .env restored"
diff -r /tmp/weiwei-backup/faiss_indexes ai-service/data/faiss_indexes && echo "✓ FAISS restored"
```

**Expected:** Both diffs succeed with "✓" lines.

---

## Task 6: Subtree Add — UI (React)

**Files migrated:** `twelvet-ui-master/*` → `ui/*`

- [ ] **Step 1: Add ui subtree**

```bash
cd /f/code/weiwei
git subtree add --prefix=ui ../twelvet-ui-master master --squash
```

**Expected:** Merge commit for UI.

- [ ] **Step 2: Verify `ui/` exists with React files**

```bash
cd /f/code/weiwei
ls ui/ | head -20
```

**Expected:** `package.json`, `src/`, `config/`, etc.

- [ ] **Step 3: Verify UI history preserved**

```bash
cd /f/code/weiwei
git log --follow ui/package.json --oneline | head -5
```

**Expected:** At least 2 commits.

- [ ] **Step 4: Verify all three subtrees in log**

```bash
cd /f/code/weiwei
git log --oneline -10
```

**Expected:** 4 commits visible — bootstrap + 3 subtree adds.

---

## Task 7: Configure Monorepo Root Files

**Files created:**
- `weiwei/.gitignore` (combined ignore rules)
- `weiwei/docker-compose.yml` (three-service orchestration)
- `weiwei/.env.example` (template)
- `weiwei/README.md` (monorepo entry point)

- [ ] **Step 1: Create root `.gitignore`**

```bash
cd /f/code/weiwei
cat > .gitignore <<'EOF'
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

# === Secrets ===
.env
.env.local
*.pem
*.key

# === Runtime data ===
data/faiss_indexes/
data/uploads/

# === IDE / OS / Migration backup ===
.DS_Store
Thumbs.db
.vscode/
.archive/
EOF
```

- [ ] **Step 2: Verify `.env` and `faiss_indexes` now gitignored**

```bash
cd /f/code/weiwei
git status --ignored | head -30
```

**Expected:** `.env` and `data/faiss_indexes/` should appear in the "Ignored files" section, NOT in untracked. If they appear as untracked, the root `.gitignore` is not being read — STOP and investigate.

- [ ] **Step 3: Create root `docker-compose.yml`**

```bash
cd /f/code/weiwei
cat > docker-compose.yml <<'EOF'
version: '3.9'
services:
  mysql:
    image: mysql:8.0.32
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: 123456
    ports: ["3306:3306"]
    volumes:
      - ./backend/data:/docker-entrypoint-initdb.d
  redis:
    image: redis:7.2.4
    restart: always
    ports: ["6379:6379"]
  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on: [mysql, redis]
  ai-service:
    build: ./ai-service
    ports: ["8000:8000"]
    env_file: ./ai-service/.env
    depends_on: [mysql]
  ui:
    build: ./ui
    ports: ["3000:3000"]
    depends_on: [backend]
EOF
```

- [ ] **Step 4: Create root `.env.example`**

```bash
cd /f/code/weiwei
cat > .env.example <<'EOF'
# === ai-service env (real values in ai-service/.env, not committed) ===
DATABASE_URL=mysql+aiomysql://root:123456@localhost:3306/twelvet-fast
AI_BASE_URL=https://apihub.example.com/v1
AI_API_KEY=replace-with-your-key
AI_MODEL=your-model-name
EMBEDDING_BASE_URL=https://api.example.com/v1
EMBEDDING_API_KEY=replace-with-your-key
EMBEDDING_MODEL=your-embedding-model
STATIC_BASE_URL=https://static.example.com

# === backend env ===
# Database, Redis, Sa-Token, etc. configured in backend/src/main/resources/application-*.yml
# Production secrets encrypted via Jasypt

# === ui env ===
# Set UI_API_BASE_URL in ui/.env or via build args
EOF
```

- [ ] **Step 5: Create root `README.md`**

```bash
cd /f/code/weiwei
cat > README.md <<'EOF'
# Weiwei — TwelveT Fast Monorepo

A unified monorepo for the TwelveT Fast v3.5.4 stack (formerly three separate repos).

## Services

| Path | Stack | Port | Source |
|------|-------|------|--------|
| `backend/` | Java Spring Boot 3.5.4 | 8080 | [neighborswap](https://example.com/private/neighborswap) @ 78e4e12 |
| `ai-service/` | Python FastAPI | 8000 | [neighborswap](https://example.com/private/neighborswap) @ c21022b |
| `ui/` | Umi Max 4.x + React 18 | 3000 | [neighborswap-ui](https://example.com/private/neighborswap-ui) @ 1b39cbc |

## Quick Start

```bash
# 1. Copy env template
cp .env.example ai-service/.env
# Edit ai-service/.env with your real API keys

# 2. Start everything via Docker Compose
docker compose up -d

# 3. Or run individually
cd backend && mvn spring-boot:run
cd ai-service && pip install -r requirements.txt && python start.py
cd ui && yarn install && yarn start
```

## Architecture

```
[ui:3000] -> [backend:8080] ---+
                              +--> [mysql/redis]
        [ai-service:8000] <----+    (HTTP proxy)
```

Java backend's AI controllers proxy HTTP calls to Python ai-service (see commit 4681ec6).

## Versioning

Single `v6.0.0` style tag for the whole product. Subtree history preserved per service (`git log --follow backend/<file>`).

## See Also

- [Design Spec](docs/superpowers/specs/2026-08-26-weiwei-monorepo-design.md)
- [CLAUDE.md](CLAUDE.md) — Project guidance
EOF
```

- [ ] **Step 6: Verify all 4 new files**

```bash
cd /f/code/weiwei
ls -la .gitignore docker-compose.yml .env.example README.md
```

**Expected:** All 4 exist with non-zero size.

- [ ] **Step 7: Stage and verify**

```bash
cd /f/code/weiwei
git add .gitignore docker-compose.yml .env.example README.md
git status --short
```

**Expected:** 4 `A` entries; no other changes.

- [ ] **Step 8: Commit monorepo root configs**

```bash
cd /f/code/weiwei
git commit -m "chore: add monorepo root config

- .gitignore: combined rules for Java, Python, Node, secrets, runtime data
- docker-compose.yml: three-service orchestration (mysql, redis, backend, ai-service, ui)
- .env.example: env var template (no real secrets)
- README.md: monorepo entry point with quickstart"
```

- [ ] **Step 9: Verify final local state**

```bash
cd /f/code/weiwei
git log --oneline
git status
```

**Expected:**
- 5 commits (1 bootstrap + 3 subtree + 1 config)
- Working tree clean (except possibly `ai-service/.env` and `data/faiss_indexes/` which are gitignored)

---

## Task 8: Push to Remote

**Files:** Remote repository at `https://github.com/lidahen/weiwei.git`

- [ ] **Step 1: Push to remote**

```bash
cd /f/code/weiwei
git push -u origin master
```

**Expected:** Output like `Enumerating objects: ...`, `Writing objects: 100%`, `To https://github.com/lidahen/weiwei.git`, `* [new branch] master -> master`, `Branch 'master' set up to track remote 'origin/master'`.

If authentication fails: check Gitee credentials / 2FA / use HTTPS token.

- [ ] **Step 2: Verify remote tracking**

```bash
cd /f/code/weiwei
git ls-remote origin
```

**Expected:** Shows `refs/heads/master` pointing to the latest commit SHA.

- [ ] **Step 3: Verify on Gitee (manual check)**

Open browser to `https://github.com/lidahen/weiwei` and verify:
- 3 directories at root: `backend/`, `ai-service/`, `ui/`
- 4 root config files: `.gitignore`, `docker-compose.yml`, `.env.example`, `README.md`
- Commit history shows 5 commits

---

## Task 9: Archive Old Repos to `.archive/`

**Files moved:**
- `weiwei/twelvet-fast/` → `weiwei/.archive/twelvet-fast/`
- `weiwei/twelvet-ai-service/` → `weiwei/.archive/twelvet-ai-service/`
- `weiwei/twelvet-ui-master/` → `weiwei/.archive/twelvet-ui-master/`

- [ ] **Step 1: Create `.archive/` directory**

```bash
cd /f/code/weiwei
mkdir -p .archive
```

- [ ] **Step 2: Move three old repos (their `.git` history preserved as backups)**

```bash
cd /f/code/weiwei
mv twelvet-fast .archive/
mv twelvet-ai-service .archive/
mv twelvet-ui-master .archive/
ls -la .archive/
```

**Expected:** `.archive/` contains 3 directories.

- [ ] **Step 3: Verify `weiwei/` root is now lean**

```bash
cd /f/code/weiwei
ls -la
```

**Expected:**
- 3 service directories: `backend/`, `ai-service/`, `ui/`
- 4 root config files
- Meta dirs: `docs/`, `openspec/`, `.atomcode/`, `.git/`, `.archive/`
- Old service dirs GONE from root

- [ ] **Step 4: Verify each service still functional (sanity check)**

```bash
cd /f/code/weiwei
[ -d backend/twelvet-admin ] && echo "✓ backend intact"
[ -d ai-service/app ] && echo "✓ ai-service intact"
[ -d ui/src ] && echo "✓ ui intact"
[ -f ai-service/.env ] && echo "✓ .env preserved"
[ -d ai-service/data/faiss_indexes ] && echo "✓ FAISS preserved"
```

**Expected:** All 5 checks pass.

- [ ] **Step 5: Commit the directory move (as deletion commits — git tracks content, not directory names)**

```bash
cd /f/code/weiwei
git status
```

**Expected:** Should show clean (because old dirs were never tracked in the new monorepo). If anything shows, inspect — should only be ignored files.

- [ ] **Step 6: Clean up `/tmp/weiwei-backup/` (only AFTER Task 10 verification passes)**

```bash
rm -rf /tmp/weiwei-backup
```

DO NOT run this until Task 10 verification confirms `.env` and FAISS are in correct location.

---

## Task 10: Final Verification (V1-V14)

**Files:** Read-only inspection

- [ ] **Step 1: V1 — git log shows expected commits**

```bash
cd /f/code/weiwei
git log --oneline -10
```

**Expected:** 5 commits: bootstrap + 3 subtrees + root config.

- [ ] **Step 2: V2-V3 — directory structure**

```bash
cd /f/code/weiwei
ls backend/ ai-service/ ui/
ls backend/twelvet-admin/ ai-service/app/ ui/src/
```

**Expected:** All three top-level dirs and three core subdirs exist.

- [ ] **Step 3: V4-V6 — history preserved**

```bash
cd /f/code/weiwei
echo "V4 backend history:"; git log --follow backend/pom.xml --oneline | head -3
echo "V5 ai-service history:"; git log --follow ai-service/requirements.txt --oneline | head -3
echo "V6 ui history:"; git log --follow ui/package.json --oneline | head -3
```

**Expected:** Each shows 2+ commits (initial + squash).

- [ ] **Step 4: V7 — old repos in `.archive`**

```bash
cd /f/code/weiwei
ls .archive/
```

**Expected:** 3 directories (`twelvet-fast`, `twelvet-ai-service`, `twelvet-ui-master`).

- [ ] **Step 5: V8-V9 — `.env` and FAISS restored**

```bash
cd /f/code/weiwei
ls -la ai-service/.env ai-service/data/faiss_indexes/
```

**Expected:** `.env` exists (~777 bytes), `faiss_indexes/` has 4 files.

- [ ] **Step 6: V10 — remote configured**

```bash
cd /f/code/weiwei
git remote -v
```

**Expected:** `origin https://github.com/lidahen/weiwei.git (fetch/push)`.

- [ ] **Step 7: V11-V13 — toolchains (best-effort, missing is OK)**

```bash
java -version 2>&1 | head -1 || echo "Java not installed (OK if backend not running)"
python --version 2>&1 || echo "Python not installed (OK if ai-service not running)"
node --version 2>&1 || echo "Node not installed (OK if ui not running)"
```

**Expected:** Versions printed OR "not installed" message. Not blockers for migration completion.

- [ ] **Step 8: V14 — final git status**

```bash
cd /f/code/weiwei
git status --ignored
```

**Expected:**
- Branch: `master`
- "nothing to commit, working tree clean"
- Ignored files section lists: `ai-service/.env`, `ai-service/data/faiss_indexes/`, possibly `ai-service/.venv/`, `ui/node_modules/`, `.archive/`

- [ ] **Step 9: Generate migration report**

```bash
cd /f/code/weiwei
cat > docs/superpowers/plans/2026-08-26-weiwei-monorepo-migration-REPORT.md <<'EOF'
# Weiwei Monorepo Migration — Completion Report

**Date:** 2026-08-26
**Status:** Complete

## Result

- 3 repos merged into 1 monorepo at https://github.com/lidahen/weiwei.git
- All 3 service histories preserved (squashed at top level, full history per service)
- `.env` restored to `ai-service/.env`
- FAISS indexes restored to `ai-service/data/faiss_indexes/`
- Old repos archived at `.archive/` for 30-day rollback
- Root configs in place: `.gitignore`, `docker-compose.yml`, `.env.example`, `README.md`

## Verification

All V1-V14 checks passed (see Task 10).

## Rollback Window

Old repos: `weiwei/.archive/{twelvet-fast,twelvet-ai-service,twelvet-ui-master}/`
Expires: 2026-09-25 (30 days)
Action: `rm -rf .archive` after that date if no rollback needed.

## Follow-up

- Update `weiwei-dual-service-architecture` memory to monorepo perspective
- Add ARCHIVED notice to old gitee repos (neighborswap, neighborswap-ui)
- After 30 days, delete `.archive/`
EOF
```

- [ ] **Step 10: Commit and push migration report**

```bash
cd /f/code/weiwei
git add docs/superpowers/plans/2026-08-26-weiwei-monorepo-migration-REPORT.md
git commit -m "docs: add monorepo migration completion report"
git push origin master
```

- [ ] **Step 11: Clean up `/tmp/weiwei-backup/` (only after all verifications pass)**

```bash
rm -rf /tmp/weiwei-backup
echo "✓ /tmp/weiwei-backup cleaned up"
```

---

## Self-Review

**1. Spec coverage:**

| Spec Section | Task |
|--------------|------|
| Phase 1 (备份 + 检查) | Task 1 |
| Phase 2 (检查 weiwei 根) | Task 2 |
| Phase 3.1-3.3 (init + bootstrap commit) | Tasks 2-3 |
| Phase 3.4 (subtree add × 3) | Tasks 4-6 |
| Phase 3.5 (root configs) | Task 7 |
| Phase 5 (push) | Task 8 |
| Phase 6 (archive) | Task 9 |
| Verification V1-V14 | Task 10 |
| Rollback plan | Task 9 (30-day window) + spec doc |

All spec requirements covered.

**2. Placeholder scan:** No TBD/TODO/"implement later" in plan. All commands have actual content.

**3. Type consistency:** N/A (shell commands only, no function signatures).

**4. Risk mitigations explicit:**
- `.env` backup at Task 1.3, restore at Task 5.4
- FAISS backup at Task 1.3, restore at Task 5.5
- Byte-level diff verification at Tasks 1.4, 5.6
- Old repos archived at Task 9, not deleted

---

## Execution Handoff

Plan complete and saved to `F:\code\weiwei\docs\superpowers\plans\2026-08-26-weiwei-monorepo-migration.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints
