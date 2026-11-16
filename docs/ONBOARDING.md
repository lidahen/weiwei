# Weiwei Monorepo — Onboarding Guide

> **Audience:** Anyone setting up the project on a new machine. Assumes fresh Windows / macOS / Linux with no prior environment.

This document is the detailed procedure for first-time setup. The root `README.md` is the quick reference.

> **2026-10-27 update:** Section 3's database setup now points to the root README's
> **Quick Start → Initialize database** section, which imports the 107-table baseline plus the
> post-baseline module scripts. The pre-2026-10-21 init scripts (including the ones that caused the
> alphabetical-order failure) were removed on 2026-10-27. The remaining install, start, and
> troubleshooting steps here are still valid.

## 0. Prerequisites (one-time install)

| Tool | Required Version | Check Command | Install Link |
|------|------------------|---------------|--------------|
| Git | 2.30+ | `git --version` | https://git-scm.com |
| JDK | 17 LTS | `java -version` | https://adoptium.net |
| Maven | 3.8+ | `mvn -version` | https://maven.apache.org |
| Python | 3.11+ | `python --version` | https://python.org |
| Node | 22 LTS | `node --version` | https://nodejs.org |
| npm | 10+（随 Node 22 自带） | `npm --version` | 随 Node 安装 |
| ~~Yarn~~ | — | — | **不要用 yarn**：本机 yarn 报 `Cannot find module .../corepack/dist/yarn.js`（corepack shim 路径拼接 bug）。团队约定统一用 npm，`ui/yarn.lock` 被 gitignore 也是这个原因 |
| Docker Desktop | latest | `docker --version` | https://docker.com |
| MySQL client | 8.0+ | `mysql --version` | (optional, for debugging) |
| Redis client | 7+ | `redis-cli --version` | (optional, for debugging) |

**Windows note:** Run all commands in **Git Bash**, not CMD/PowerShell. Git Bash handles paths correctly.

## 1. Clone the Repository

```bash
cd <your-projects-root>     # e.g., F:\code or ~/code
git clone https://github.com/lidahen/weiwei.git
cd weiwei
```

> The repo is **private**. Gitee will prompt for credentials on first push. Configure Git credentials in advance:
> ```bash
> git config --global user.name "Your Name"
> git config --global user.email "you@example.com"
> ```

## 2. Verify Tracked Secrets

The repo is private, so `.env` and FAISS indexes are tracked. Confirm they're present:

```bash
ls -la ai-service/.env
ls ai-service/data/faiss_indexes/
# Should see: kb_5.faiss, kb_5.json, kb_6.faiss, kb_6.json
```

**If `.env` is missing**, copy from the template inside `ai-service/` and fill in your own API keys:

```bash
cp ai-service/.env.example ai-service/.env
# Edit ai-service/.env — fill in AI_API_KEY, EMBEDDING_API_KEY, DATABASE_URL
```

The template lives at `ai-service/.env.example` (this is also what `ai-service/start.sh` /
`start.bat` copy when `.env` is absent). There is no root-level `.env.example` — it was
removed on 2026-10-21 because it had drifted out of date and silently lacked fields such as
`INTERNAL_API_TOKEN`, `JAVA_BASE_URL`, and the senseaudio/music-quota keys.

## 3. Start MySQL + Redis

You have two options. **Local install is preferred for solo development**; **Docker is fastest for new machines**.

### Option A: Local Install (recommended for solo dev)

**MySQL 8.0+** — Choose one:

- **Windows:** [MySQL Installer for Windows](https://dev.mysql.com/downloads/installer/) — installs MySQL Server + Workbench. During setup, set root password to `123456` (or update `.env` and backend YAMLs to match).
- **macOS:** `brew install mysql@8.0 && brew services start mysql@8.0 && mysql_secure_installation`
- **Linux (Debian/Ubuntu):** `sudo apt install mysql-server-8.0 && sudo systemctl start mysql`

**Initialize the database schema:**

Use the root README's **Quick Start → Initialize database** section. It imports a single full
baseline — `docs/db/twelvet-fast_2026-11-03.sql` (120 tables, seed/demo data included) — which
already contains every post-baseline migration, so **no extra scripts need to be run**.
The older 107-table baseline (`twelvet-fast_2026-10-21.sql`) is kept only for diffing history;
the scripts in `backend/data/` and `backend/sql/` are likewise archived references, not steps.

> Verify the `twelvet-fast` database exists:
> ```bash
> mysql -uroot -p -e "SHOW DATABASES;" | grep twelvet-fast
> ```

**Redis 7+** — Choose one:

- **Windows:** Download from [Memurai](https://www.memurai.com/) or use WSL2 + `apt install redis`. Or use [Microsoft's port](https://github.com/microsoftarchive/redis/releases).
- **macOS:** `brew install redis && brew services start redis`
- **Linux (Debian/Ubuntu):** `sudo apt install redis-server && sudo systemctl start redis-server`

**Verify both:**

```bash
mysql -uroot -p -e "SHOW DATABASES;" | grep twelvet-fast    # should show twelvet-fast
redis-cli ping                                              # should print PONG
```

### Option B: Docker (fastest, for clean machines)

```bash
docker run -d --name weiwei-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  mysql:8.0.32

docker run -d --name weiwei-redis -p 6379:6379 redis:7.2.4
```

**Windows PowerShell users:** use this syntax instead:

```powershell
docker run -d --name weiwei-mysql -p 3306:3306 `
  -e MYSQL_ROOT_PASSWORD=123456 `
  mysql:8.0.32

docker run -d --name weiwei-redis -p 6379:6379 redis:7.2.4
```

> The pre-2026-10-21 init scripts were removed, so do not mount `backend/data/` as an initdb source.
> Import the baseline manually after the container starts — see the root README or
> [db/_导入说明.txt](db/_导入说明.txt).

**Verify database initialized (after importing the baseline):**

```bash
docker exec -it weiwei-mysql mysql -uroot -p123456 -e "SHOW DATABASES;"
# Should list: twelvet-fast, information_schema, mysql, ...
```

**Verify Redis:**

```bash
docker exec -it weiwei-redis redis-cli ping
# Should print: PONG
```

> If port 3306 or 6379 is already in use, change the host port (e.g., `-p 3307:3306`) and update the `DATABASE_URL` in `ai-service/.env` and `backend/.../application-*.yml` accordingly.

## 4. Start `ai-service` (Python)

```bash
cd ai-service

# Optional: create venv for isolation
python -m venv .venv
# Activate:
#   Windows Git Bash:  source .venv/Scripts/activate
#   Windows PowerShell: .\.venv\Scripts\Activate.ps1
#   macOS/Linux:       source .venv/bin/activate

pip install -r requirements.txt    # 3-5 min first time
pip install -r requirements.lock.txt  # exact pins (optional, for reproducibility)
```

**Start the service:**

```bash
python start.py                    # default port 8001
# or
python start.py --port 8000        # match backend's ai.service.url
# or
python start.py --port 8000 --reload  # dev mode with hot reload
```

**Verify:**

```bash
curl http://localhost:8001/docs    # FastAPI Swagger UI
# or whatever port you chose
```

**Expected log line:** `Uvicorn running on http://0.0.0.0:8001`

## 5. Start `backend` (Java)

In a **new terminal**:

```bash
cd backend
mvn clean install -DskipTests       # 5-10 min first time (downloads deps)
mvn spring-boot:run -pl twelvet-admin
```

**If port 8080 is in use**, edit `backend/twelvet-admin/src/main/resources/application-dev.yml` and change `server.port`.

**Verify:**

```bash
curl http://localhost:8080
# Should return HTML or JSON (depends on routing)
```

**Expected log line:** `Started FastApplication in X.XXX seconds`

**Backend → AI service connection:** The Java backend calls Python via HTTP. Configure in `backend/twelvet-admin/src/main/resources/application-dev.yml`:

```yaml
ai:
  service:
    url: http://localhost:8001    # must match ai-service port
```

If you started ai-service on port 8000, change this to `8000`.

## 6. Start `ui` (React)

In a **new terminal**:

```bash
cd ui

# Recommended: speed up with China mirror
npm config set registry https://registry.npmmirror.com

npm ci                             # 3-5 min first time（严格按 package-lock.json 安装，依赖可复现）
npm run start:dev                  # 默认端口 8000
```

**⚠️ Port conflict:** `ui` defaults to port 8000, which **conflicts with `ai-service`**. Fix one of:

**Option A** — Change UI port to 3000:

```bash
PORT=3000 npm run start:dev
# Linux/macOS
# or set in ui/.env: PORT=3000
```

**Option B** — Change ai-service to a non-conflicting port (e.g., 8001) and start it on 8001.

**Verify:** Open browser to `http://localhost:3000` (or whichever port). You should see the login page.

## 7. Service Map

| Service | Port | Started By | Depends On |
|---------|------|------------|------------|
| MySQL | 3306 | Docker | — |
| Redis | 6379 | Docker | — |
| ai-service | 8001 (recommended) | `python start.py --port 8001` | MySQL (for some endpoints) |
| backend | 8080 | `mvn spring-boot:run -pl twelvet-admin` | MySQL, Redis, ai-service |
| ui | 3000 (recommended) | `PORT=3000 npm run start:dev` | backend |

**Startup order:** MySQL → Redis → ai-service → backend → ui

## 8. Health Check Script

Run this in `weiwei/` root to verify everything is up. **Adjust commands for local install vs Docker** (both shown):

### Local install variant

```bash
echo "=== MySQL ===" && mysql -uroot -p123456 -e "SHOW DATABASES;" 2>&1 | grep twelvet-fast
echo "=== Redis ===" && redis-cli ping
echo "=== Backend (8080) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080
echo "=== AI Service (8001) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8001/docs
echo "=== UI (3000) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:3000
```

### Docker variant

```bash
echo "=== MySQL ===" && docker exec weiwei-mysql mysql -uroot -p123456 -e "SHOW DATABASES;" 2>&1 | grep twelvet-fast
echo "=== Redis ===" && docker exec weiwei-redis redis-cli ping
echo "=== Backend (8080) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080
echo "=== AI Service (8001) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8001/docs
echo "=== UI (3000) ===" && curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:3000
```

**Expected:** All show success (MySQL shows `twelvet-fast`, Redis shows `PONG`, others show `HTTP 200` or `HTTP 401` for protected routes).

## 9. Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` on backend → ai-service | Port mismatch | Ensure `application-dev.yml` `ai.service.url` matches ai-service's actual port |
| `Access denied for user 'root'` | MySQL password mismatch | Either change Docker `-e MYSQL_ROOT_PASSWORD` and recreate container, OR edit `ai-service/.env` and backend YAML to match |
| UI shows blank page | npm ci install incomplete | `rm -rf ui/node_modules && npm ci` |
| `pip install` fails on Windows compiling some dep | Missing C++ build tools | Install Visual Studio Build Tools 2019+ with C++ workload, OR use `pip install --only-binary :all:` |
| Maven downloads hang | Network issue in China | Configure Maven mirror in `~/.m2/settings.xml` (e.g., Aliyun mirror) |
| Port 8080/3000/8000/8001 already in use | Other service running | `netstat -ano | findstr :PORT` then `taskkill /PID <pid> /F` (Windows) or `lsof -i :PORT` then `kill -9 <pid>` (macOS/Linux) |
| ai-service crashes on FAISS load | `libomp` missing (macOS) | `brew install libomp` |
| `mvn` complains about Java version | JDK 8/11 installed, not 17 | `JAVA_HOME` env var must point to JDK 17. Check `mvn -version` output. |
| **Windows Git Bash: `mvn` throws `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`** | Maven's own bash launcher mis-resolves the classpath under MSYS/Git Bash. The jar is fine. | Use `mvn.cmd` instead of `mvn` (this is what `verify.sh` does). `verify.sh` still prefers `mvn.cmd` automatically. |
| **Windows Git Bash: `yarn` throws `Cannot find module ...corepack/dist/yarn.js`** | corepack shim path concatenation bug (adds an extra `F:\f\` prefix). | Use `npm` only. The repo standard is npm; `yarn.lock` is gitignored on purpose. |
| **Backend fails to start: `PlaceholderResolutionException: Could not resolve placeholder 'payment.alipay.app-id'`** | `AlipayGatewayImpl` / `WechatGatewayImpl` inject payment config with `@Value` and **no defaults** — all 9 fields are startup hard requirements: alipay `app-id`/`private-key`/`alipay-public-key`/`gateway-url`/`notify-url`, wechat `app-id`/`mch-id`/`api-key`/`notify-url`. | `application-dev.yml` ships placeholder defaults so dev starts fine. If you blank any of them, the app won't boot. Override via env vars (`${PAY_ALIPAY_APP_ID:...}` form is already wired) rather than editing the YAML. |
| **Redis: backend fails to start with `ERR AUTH ... called without any password configured`** | Redisson sends the literal empty string as a password (`AUTH ""`) rather than skipping auth, so a passwordless Redis rejects it. | Make sure `spring.data.redis.password` and the actual Redis `requirepass` agree. Both are `123456` by convention here. |

## 10. After Successful First Run

Once everything is up, you can:

- **Default admin login**: the 107-table baseline contains seed users; inspect `sys_user` if needed.
  Remove the login page's demo credential prefill before any public release (see README → Known limitations).
- **Test AI integration**: In UI, navigate to AI features. They should call backend → ai-service → Agnes API.
- **Test knowledge base (RAG)**: ai-service loads `kb_5` and `kb_6` FAISS indexes automatically on startup (if loaded by `app/services/rag_service.py` on import).

## 11. Daily Development Workflow

```bash
# (Optional) Verify databases are running
# Local:  mysql -uroot -p -e "SELECT 1" && redis-cli ping
# Docker: docker ps | grep weiwei-

# Terminal 2: ai-service with hot reload
cd ai-service && source .venv/Scripts/activate   # or .venv/bin/activate on macOS/Linux
python start.py --port 8001 --reload

# Terminal 3: backend
cd backend && mvn spring-boot:run -pl twelvet-admin

# Terminal 4: ui
cd ui && PORT=3000 npm run start:dev
```

Stop all: `Ctrl+C` in each terminal. For Docker: `docker stop weiwei-mysql weiwei-redis`. For local installs: stop via `brew services stop` / `systemctl stop` / Services panel.

## 12. Reset / Wipe

To start completely fresh:

```bash
# Stop everything
# Local: stop MySQL/Redis via your service manager
# Docker: docker stop weiwei-mysql weiwei-redis && docker rm weiwei-mysql weiwei-redis

# Wipe MySQL data
# Local:  mysql -uroot -p -e "DROP DATABASE twelvet-fast;"
# Docker: docker exec weiwei-mysql mysql -uroot -p123456 -e "DROP DATABASE twelvet-fast;"

# Wipe build artifacts
cd backend && mvn clean
cd ../ui && rm -rf node_modules
cd ../ai-service && rm -rf .venv __pycache__ */__pycache__

# Re-run from Step 3
```

---

## See Also

- [README.md](../README.md) — Quick start reference
- [功能模块详解](功能模块详解.md) — Feature implementation details
- [Monorepo Design Spec](superpowers/specs/2026-08-26-weiwei-monorepo-design.md) — Why this layout
- [Migration Plan](superpowers/plans/2026-08-26-weiwei-monorepo-migration.md) — How the repo was assembled
- [Migration Report](superpowers/plans/2026-08-26-weiwei-monorepo-migration-REPORT.md) — Verification results
- [CLAUDE.md](../CLAUDE.md) — Project-wide guidance for AI assistants
