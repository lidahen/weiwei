# AI 创作工作台改造设计（抖音向）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 目标：在 weiwei 现有三层架构（ui / backend / ai-service）内新增 **"AI 创作工作台"** 能力域，
> 实现"复刻爆款 + 自创视频"全 AI 生成产线，面向抖音（9:16 竖屏短视频）。
> 本文档为实施蓝图：表结构、API、服务模块、前端页面、环境变更、分期计划均已落到可照做的粒度。

---

## 1. 背景与目标

### 1.1 要解决的问题

运营抖音账号的两类核心内容需求，全部由 AI 生成：

1. **复刻爆款**：粘贴一条抖音爆款视频 → 自动拆解其结构（钩子/节奏/镜头语言）→ 套结构模板生成自己的脚本与分镜 → 全 AI 生成画面与配音 → 合成竖屏成片。
2. **自创视频**：给定选题 → 结合账号人设与沉淀的爆款模板库 → 走同一条合成产线出片。

### 1.2 为什么做在 weiwei 里

创作工作台 = 模型调用层 + 创作业务层 + 工作台 UI。前一层 weiwei 已完整具备：

| 已有能力 | 位置 | 复用方式 |
|---|---|---|
| 生图 vendor（agnes-image-2.5-flash 等） | `ai-service/app/services/agnes_image.py` + `vendor_router` | composer 内部直接调用 |
| 生视频 vendor（agnes-video-2.5-flash） | `agnes_video.py`，任务表 `ai_video_task` + 轮询 | 每条分镜一个视频任务 |
| TTS（SiliconFlow CosyVoice2） | `tts_service.py`，端点 `/ai/music/tts` | 口播配音 |
| 声音克隆 | `siliconflow_clone.py`，端点 `/ai/music/voices` | 人设固定音色 |
| 歌曲生成（Senseaudio） | `music_service.py`，端点 `/ai/music/songs` | BGM / 卡点音乐 |
| 歌词草稿/修改 | `/ai/music/lyrics/*` | 歌曲类内容 |
| 提示词增强 skill | `app/skills/prompt-enhance`、`video-prompt-pro` | 分镜 prompt 生成时注入 |
| 内容审核 | `moderation.py` | 脚本/分镜生成后过审 |
| 配额与计费账本 | `quota_service.py`、`ai_usage_daily` | 合成流水线各步计入 |
| 文件转储 | `oss_transfer.py` | 分镜图/片段/成片落存储 |
| 模型管理（ai_model + 默认模型） | `model_resolver.py` | 无需新增配置面 |
| 用户/权限/菜单 | Sa-Token + `sys_menu` | 新增 `ai:creation:*` 权限串 |

自建独立项目等于重写以上全部。**本改造只新增"创作业务层"**，并保持 `creation` 命名空间干净，未来可整体拆出。

### 1.3 模型能力边界（决定架构的硬约束）

来自 `docs/ai/模型调用文档说明.md` 与 `skills/video-prompt-pro/SKILL.md`：

- 视频模型单段 **4–12 秒、720P、无声音**，不支持视频输入与视频编辑
  → 长视频必须走 **分镜编排 + 逐镜生成 + FFmpeg 合成**，音频轨（配音 + BGM）独立于画面轨。
- 视频支持 `reference`（≤5 张参考图 / ≤3 段参考音频）与 `keyframe`（首尾帧）模式
  → **角色一致性**靠人设定妆照做参考图；**镜间衔接**靠上一镜尾帧传下一镜首帧。
- 视频模型**不生成口型** → 口播画面采用"人物中景 + B-roll 混切"的剪辑语言，不做数字人口型同步（P2+ 探索项，见 §11）。
- 生图支持 9:16（736x1312 @1K）、图生图、多图合成 → 定妆照、分镜图、封面均有原生支持。

---

## 2. 总体架构

```
[ui:3000]                       [backend:8080]                    [ai-service:8001]
pages/AI/Creation/*  ──HTTP──>  AiCreationController  ──透传──>  /ai/creation/*
（工作台页面）                    （鉴权/配额/薄代理）                creation 域（新增）
                                                                    ├─ decomposer  爆款拆解
                                                                    ├─ storyboard  脚本→分镜
                                                                    ├─ composer    合成引擎
                                                                    ├─ persona     人设库
                                                                    ├─ 复用: agnes_image / agnes_video
                                                                    │        tts / music / moderation
                                                                    │        oss_transfer / prompt skill
                                                                    └─ media 工具: yt-dlp / PySceneDetect / FFmpeg
```

### 2.1 复刻产线（P0 主链路）

```
抖音分享链接（或手动上传视频文件）
  → yt-dlp 下载（转储 OSS）
  → ASR 转写（SiliconFlow）+ PySceneDetect 切镜 + 抽帧
  → LLM 拆解：钩子类型 / 分镜骨架 / 每镜时长分布 / 画面风格 / 转场逻辑
  → 存为爆款模板（ai_viral_template）
  → 套模板 + 人设库 → 生成新口播稿（LLM，真二创：只借结构，不搬文案素材）
  → 分镜 JSON（ai_storyboard_shot，人工可编辑）
  → 逐镜：生图（参考定妆照）→ 图生视频 → TTS（克隆音色）
  → 时间轴对齐（音频时长定镜长）+ 句级字幕
  → FFmpeg 合成 720x1280 + BGM + 封面
  → 发布包（标题/文案/标签/AI 生成声明提醒）
```

### 2.2 自创产线

同一合成引擎，起点不同：`选题 + 人设 + 模板库` → 脚本 → 分镜 → 合成。
复刻沉淀的模板越多，自创质量越高——两条产线共享一个引擎，只是入口不同。

### 2.3 状态机

```
draft（脚本/分镜草稿）
  → storyboard_ready（分镜确认）
  → generating（逐镜生成中：每镜独立 image/video/audio 状态）
  → composing（FFmpeg 合成中）
  → done（成片可下载）  /  failed（镜级错误，可单镜重试）
```

---

## 3. 数据库设计

> 遵循现有 `ai_*` 表约定：BIGINT 自增主键、`create_by/create_time/update_time/del_flag` 审计列、
> SQLAlchemy 模型放 `app/models/`。建表 SQL 增量脚本放 `ai-service/scripts/`，同时更新 `docs/db/` 的全量导出。

### 3.1 `ai_persona` 人设表

```sql
CREATE TABLE `ai_persona` (
  `persona_id`     BIGINT       NOT NULL AUTO_INCREMENT,
  `name`           VARCHAR(64)  NOT NULL COMMENT '人设名称',
  `description`    VARCHAR(500)          COMMENT '账号定位描述',
  `style_fewshot`  TEXT                  COMMENT '文案风格样例（历史爆款片段，few-shot 用）',
  `look_image_urls` TEXT                 COMMENT '定妆照 URL JSON 数组（≤5 张，视频 reference 用）',
  `voice_id`       VARCHAR(128)          COMMENT '克隆音色 ID（SiliconFlow voice uri）',
  `voice_name`     VARCHAR(128)          COMMENT '音色显示名',
  `banned_words`   TEXT                  COMMENT '禁用词，脚本生成时规避',
  `status`         SMALLINT     DEFAULT 1 COMMENT '1 启用 0 停用',
  `create_by`      VARCHAR(64),
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`       SMALLINT     DEFAULT 0,
  PRIMARY KEY (`persona_id`)
) COMMENT='创作人设库';
```

### 3.2 `ai_viral_template` 爆款模板表

```sql
CREATE TABLE `ai_viral_template` (
  `template_id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `source_url`       VARCHAR(500)          COMMENT '爆款来源链接',
  `source_video_url` VARCHAR(500)          COMMENT '下载后转储的视频地址',
  `title`            VARCHAR(200)          COMMENT '爆款标题',
  `asr_text`         TEXT                  COMMENT '转写全文',
  `structure_json`   TEXT                  COMMENT '拆解出的结构模板（钩子/分镜骨架/节奏/风格）',
  `hook_type`        VARCHAR(50)           COMMENT '钩子类型标签（悬念/冲突/反常识/福利…）',
  `duration_sec`     INT                   COMMENT '原片时长',
  `shot_count`       INT                   COMMENT '拆解出的分镜数',
  `status`           VARCHAR(20)  DEFAULT 'analyzing' COMMENT 'analyzing/ready/failed',
  `error_msg`        TEXT,
  `create_by`        VARCHAR(64),
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`         SMALLINT     DEFAULT 0,
  PRIMARY KEY (`template_id`),
  KEY `idx_hook_type` (`hook_type`)
) COMMENT='爆款结构模板库';
```

### 3.3 `ai_creation_project` 作品项目表

```sql
CREATE TABLE `ai_creation_project` (
  `project_id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `title`                VARCHAR(200)          COMMENT '作品标题',
  `source_type`          VARCHAR(20)  NOT NULL COMMENT 'viral_clone/original/image_text',
  `template_id`          BIGINT                COMMENT '套用的爆款模板',
  `persona_id`           BIGINT                COMMENT '使用的人设',
  `topic`                VARCHAR(500)          COMMENT '选题/主题描述',
  `script_text`          TEXT                  COMMENT '口播稿全文',
  `storyboard_json`      TEXT                  COMMENT '当前生效的分镜 JSON（见 §4）',
  `status`               VARCHAR(30)  DEFAULT 'draft'
      COMMENT 'draft/storyboard_ready/generating/composing/done/failed',
  `final_video_url`      VARCHAR(500)          COMMENT '成片地址',
  `cover_url`            VARCHAR(500)          COMMENT '封面地址',
  `publish_package_json` TEXT                  COMMENT '发布包：标题/文案/标签/AI声明',
  `error_msg`            TEXT,
  `create_by`            VARCHAR(64),
  `create_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`             SMALLINT     DEFAULT 0,
  PRIMARY KEY (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_source_type` (`source_type`)
) COMMENT='创作作品项目';
```

### 3.4 `ai_storyboard_shot` 分镜表

```sql
CREATE TABLE `ai_storyboard_shot` (
  `shot_id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`       BIGINT       NOT NULL,
  `shot_index`       INT          NOT NULL COMMENT '镜序号，从 1 开始',
  `text`             VARCHAR(1000)         COMMENT '该镜台词',
  `mode`             VARCHAR(20)  DEFAULT 'img2vid' COMMENT 'text2vid/img2vid/keyframe',
  `image_prompt`     TEXT                  COMMENT '生图提示词（增强后）',
  `video_prompt`     TEXT                  COMMENT '生视频提示词（增强后）',
  `reference_images` TEXT                  COMMENT '参考图 URL JSON 数组',
  `image_task_id`    BIGINT                COMMENT '关联 ai_image_task',
  `video_task_id`    BIGINT                COMMENT '关联 ai_video_task',
  `image_url`        VARCHAR(500)          COMMENT '分镜图成品',
  `clip_url`         VARCHAR(500)          COMMENT '分镜视频成品',
  `audio_url`        VARCHAR(500)          COMMENT '该镜 TTS 成品',
  `audio_duration_ms` INT                  COMMENT '音频时长，决定该镜时间轴长度',
  `shot_status`      VARCHAR(20)  DEFAULT 'pending'
      COMMENT 'pending/image_done/video_done/audio_done/ready/failed',
  `error_msg`        TEXT,
  `create_by`        VARCHAR(64),
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`         SMALLINT     DEFAULT 0,
  PRIMARY KEY (`shot_id`),
  KEY `idx_project` (`project_id`, `shot_index`)
) COMMENT='作品分镜';
```

> 注：`ai_image_task` / `ai_video_task` / `ai_model` 全部复用现有表，不新增列。
> 多租户：现有 `ai_*` 表均未带 `tenant_id`，本域沿用（见 §11 开放问题 1）。

### 3.5 菜单权限（sys_menu 增量 SQL）

```
一级目录：AI 创作工作台（/ai/creation）
├─ 创作项目   ai:creation:list / query / add / edit / remove
├─ 人设管理   ai:creation:persona
└─ 模板库     ai:creation:template
```

与现有 `ai:video:*` 权限串风格一致，随建表 SQL 一起出增量脚本。

---

## 4. 分镜 Schema（核心资产）

LLM 只产出此结构，渲染/合成代码只消费此结构——AI 不稳定没关系，消费端永远稳定。

```jsonc
{
  "meta": {
    "title": "…",
    "aspect": "9:16",
    "target_duration_sec": 25,
    "persona_id": 1,
    "template_id": 3            // 自创产线可为 null
  },
  "audio": {
    "voice": { "type": "clone", "voice_id": "…" },       // 人设克隆音色
    "bgm":   { "type": "song", "style": "轻快电子", "bpm": 120,
               "lyrics": null, "volume": 0.18 }          // lyrics 为空 = 纯氛围向
  },
  "subtitles": { "style": "sentence", "font_size": 56, "highlight": "#FFE24A" },
  "shots": [
    {
      "index": 1,
      "role": "hook",                                     // hook/body/proof/cta
      "text": "这条视频 3 秒钟决定你划不划走",             // ≤ 25 字（≈6s 语速）
      "mode": "img2vid",
      "image_prompt": "…（经 prompt-enhance skill 增强）",
      "video_prompt": "…（经 video-prompt-pro 时间戳分镜法增强）",
      "reference_images": ["人设定妆照1", "分镜图"],
      "transition": "cut"                                 // cut/fade/whip
    }
  ]
}
```

**编排规则（写死在 storyboard.py，不依赖 LLM 自觉）：**

- 单镜台词 ≤ 25 字，超长自动拆镜（保证镜长落在视频模型 4–12s 区间）
- 每镜必有 `image_prompt`（img2vid/keyframe 模式先出图再图生视频）
- 人设定妆照自动注入每镜 `reference_images` 首位
- 生成后按 JSON Schema 校验，不合格自动让 LLM 重排一次，仍不合格落 `draft` 等人工改

---

## 5. API 设计

### 5.1 Python 路由（ai-service，`app/routers/creation.py`，前缀 `/ai/creation`）

| 方法 | 路径 | 说明 | 模式 |
|---|---|---|---|
| POST | `/decompose` | 提交拆解：`{source_url}` 或 `{video_file}`（手动上传兜底），`persona_id?` | 后台任务，返回 `template_id` |
| GET | `/template/{id}` | 拆解结果（结构模板 + 转写文本 + 关键帧图） | 同步 |
| GET | `/templates` | 模板库列表（`hook_type` 筛选） | 同步 |
| DELETE | `/template/{id}` | 删除模板 | 同步 |
| POST | `/projects` | 创建项目：`{source_type, template_id?, topic?, persona_id}` → LLM 生成脚本 + 分镜草稿 | 同步（LLM 30s 内） |
| GET | `/projects` | 项目列表（分页） | 同步 |
| GET | `/projects/{id}` | 项目详情：状态机 + 每镜状态/产物 URL | 同步 |
| PUT | `/projects/{id}/storyboard` | 保存人工编辑后的分镜 JSON | 同步 |
| POST | `/projects/{id}/render` | 启动合成流水线 | 后台任务 |
| POST | `/projects/{id}/shots/{shotId}/retry` | 单镜重试（重生图/重出视频/重配音，`{stage}` 指定） | 后台任务 |
| GET | `/projects/{id}/download` | 成片下载（本地流式） | 同步 |
| POST | `/projects/{id}/publish-package` | 生成发布包（标题/文案/标签 + AI 声明提醒） | 同步 |
| GET/POST/PUT/DELETE | `/personas[/{id}]` | 人设 CRUD；POST 时可触发定妆照生成（复用生图）与音色克隆（透传 `/ai/music/voices`） | 同步/后台 |

响应体沿用现有 `ApiResponse` 包装；长任务一律"立即返回任务 ID + 轮询详情"，与 video 域模式一致。

### 5.2 Java 层（backend，仿 `AiVideoController`）

新增 `AiCreationController`（约 120 行，纯透传 + 本地分页）：

- `AiServiceClient.post/get` 代理 §5.1 全部端点，`@SaCheckPermission("ai:creation:*")`
- `pageQuery` 走本地 `IAiCreationProjectService`（查 `ai_creation_project`，与 video 域同构）
- `twelvet-ai-api` 增加域对象 `AiCreationProject` / `AiPersona` / `AiViralTemplate`
- 菜单 SQL 见 §3.5

---

## 6. 服务模块详细设计（ai-service）

新增目录 `app/services/creation/`：

### 6.1 `decomposer.py` 爆款拆解

```
输入: source_url 或已上传视频文件
1. yt-dlp 下载（抖音分享链接解析）；失败 → 提示改用手动上传
2. oss_transfer 转储原片；ffmpeg 抽帧（1fps）供 LLM 视觉分析
3. ASR: SiliconFlow 语音识别（httpx 调用，复用 TTS 同族 key，无新增重依赖）
4. PySceneDetect 切镜 → 镜头边界 + 每镜时长分布
5. LLM（agnes-3.0-flash，视觉输入抽帧图）输出 structure_json：
   { hook: {type, 首镜画面特征, 台词模式}, shots: [{role, 时长占比, 画面类型, 转场}],
     rhythm: "…", visual_style: "…", cta: "…" }
6. 落 ai_viral_template（status=ready）
```

### 6.2 `storyboard.py` 脚本与分镜

```
输入: topic + persona + template(可选)
1. 口播稿：LLM 生成（system 注入 persona.style_fewshot + banned_words；
   套 template.structure_json 的骨架；moderation 过审）
2. 分镜：按 §4 编排规则切镜 → 每镜调 prompt_enhancer（注入 video-prompt-pro skill）
3. JSON Schema 校验 + 一次自动重排
4. 落 ai_creation_project.storyboard_json + ai_storyboard_shot 行
```

### 6.3 `composer.py` 合成引擎（核心）

```
输入: project（storyboard_ready）
后台线程执行（不阻塞事件循环），project.status = generating:
1. 逐镜并行度 2:
   a. 生图: resolve_model("IMAGE") → agnes_image（图生图，参考定妆照）→ ai_image_task
   b. 生视频: resolve_model("VIDEO") → agnes_video（img2vid，first_frame=分镜图，
      reference=定妆照）→ ai_video_task → 轮询至 completed
      P1 优化: 镜 N 尾帧 = 镜 N+1 首帧（keyframe 模式串联）
   c. TTS: tts_service.synthesize(text, voice=persona.voice_id) → audio_url + 时长
   d. 每镜状态推进 shot_status，失败可单镜重试
2. 时间轴: 镜长 = max(音频时长, 视频时长)；视频偏短 → 末帧定格(freeze)补齐
3. 字幕: 句级 SRT（P0）；whisperx 词级强制对齐（P1 可选增强）
4. BGM: 若 audio.bgm.type=song → music_service.submit_song(style, bpm, lyrics=null)
   轮询取曲；音量 ducking（ffmpeg sidechaincompress，人声优先）
5. FFmpeg 合成（subprocess 封装，工作目录 ai-service/data/creation/{project_id}/）:
   逐镜 clip scale/crop 到 720x1280 → concat demuxer 拼接
   → 叠加配音轨 + BGM 轨 → subtitles filter 烧字幕
   → 输出 final.mp4 → oss_transfer 转储 → status=done
6. 封面: 首镜帧 + ffmpeg drawtext 标题大字（P0）；生图模型定制封面（P1）
7. 配额: 各步经 quota_service；用量入 ai_usage_daily
```

### 6.4 `persona.py` 人设库

- 定妆照：`agnes_image` 多图合成/图生图，一次生成 3–5 张不同机位，写入 `look_image_urls`
- 音色：透传现有 `/ai/music/voices`（SiliconFlow 克隆，同步返回可用 voice）
- 提供 `persona.to_prompt_context()` 给 storyboard 注入 few-shot

### 6.5 后台任务与并发

- 沿用 `BackgroundTasks + 任务表状态 + 前端轮询`（video 域已验证的模式）
- 合成/下载为 CPU/IO 重活，统一丢 `asyncio.to_thread`/线程池，**不阻塞事件循环**
- 工作目录临时文件 24h 清理（Quartz 侧或 ai-service 启动钩子均可，P0 用后者）

---

## 7. 前端设计（ui，`pages/AI/Creation/`）

| 页面 | 路由 | 内容 |
|---|---|---|
| 项目列表 | `/ai/creation` | 卡片列表（状态/封面/时长），新建入口（复刻/自创/图文三选） |
| 拆解向导 | `/ai/creation/new` | 粘贴链接或上传视频 → 拆解进度 → 模板结果预览 → "用此模板创建作品" |
| 分镜编辑器 | `/ai/creation/editor/:id` | **核心页**。左列分镜表（台词可编辑、prompt 可改）；右列实时预览。每镜操作：重生图 / 重出视频 / 重配音 / 试听；顶部：整体渲染 + 进度条；失败镜红色标记一键重试 |
| 人设管理 | `/ai/creation/persona` | 定妆照生成、音色克隆录入、风格样例维护 |
| 模板库 | `/ai/creation/template` | 拆解模板列表（钩子类型/时长/镜数），删除/复用 |

配套：`src/services/creation.ts`（接口封装，复用现有 request）、菜单路由注册。
分镜编辑器交互可大量参考现有 `pages/AI/Video` 的任务轮询写法。

---

## 8. 环境与部署变更

**`ai-service/requirements.txt` 增量：**

```
yt-dlp>=2024.8.6
scenedetect[opencv]>=0.6.4      # 含 opencv-python-headless
```

> FFmpeg 不进 requirements（系统级二进制）；ASR 走 SiliconFlow HTTP，无需新 Python 依赖；
> 词级对齐（P1 可选）再评估 whisperx（含 CPU torch，体积大）。

**`ai-service/Dockerfile` 增量：**

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
```

**`.env` 增量：** `ASR_API_KEY`（可复用 SiliconFlow key）、`CREATION_WORK_DIR=./data/creation`

**Windows 开发环境：** `winget install ffmpeg`（或手动加入 PATH）；yt-dlp 随 pip 安装。

**部署方式：** 本仓库已不含 docker-compose.yml（2026-10-21 删除）；三端均在本地直接运行。

---

## 9. 实施计划

### P0 —— 复刻闭环（目标：链接进，成片出）

| # | 任务 | 产出 |
|---|---|---|
| 1 | 建表 SQL + SQLAlchemy 模型 + Java 域对象 | 四张新表可读写 |
| 2 | `creation.py` 路由骨架 + persona CRUD | API 可调 |
| 3 | `decomposer.py`（yt-dlp + ASR + 切镜 + LLM 拆解） | 模板入库 |
| 4 | `storyboard.py`（脚本 + 分镜 + 校验） | 分镜 JSON 可编辑 |
| 5 | `composer.py`（逐镜生成 + TTS + 字幕 + FFmpeg） | 成片 URL |
| 6 | Java `AiCreationController` + 菜单 SQL | 全链路透传 |
| 7 | ui：列表页 + 拆解向导 + 最小分镜编辑器 | 可操作 |

**验收：** 粘贴一条抖音分享链接 → ≤15 分钟产出 720x1280、15–30 秒、含配音/句级字幕/封面的 MP4；
全程工作台可见状态、可单镜重试。

### P1 —— 人设与自创

定妆照流程、克隆音色接入、自创产线（无模板套用）、BGM/Senseaudio 歌曲、词级字幕（whisperx 对齐）、
封面生图定制、keyframe 镜间串联、图文产线（生图 + 大字封面，无视频合成，成本最低的日更形态）。

### P2 —— 增强与放大

一键多变体（同模板换钩子出 N 版）、数据复盘（手动录入播放/完播 → LLM 归因反哺选题）、
发布队列（半自动：成片+文案打包，跳转创作者网页版）、数字人口播探索（MuseTalk 本地或云 API）。

---

## 10. 风险与合规

| 风险 | 对策 |
|---|---|
| yt-dlp 对抖音解析不稳 | 拆解入口同时支持"手动上传视频文件"，链路不断 |
| 搬运判定/限流 | 拆解模板**只存结构与镜头语言，不存素材与文案**；脚本强制重写、画面全 AI 生成 |
| AI 内容平台声明要求 | 发布包固定输出"AI 生成内容"勾选提醒；发布文案模板含声明位 |
| 内容安全 | 脚本与分镜两道过 `moderation`；成片发布前人工确认 |
| Agnes 免费期结束 | 刊例成本约 $0.025/s（30s 成片 ≈ $0.75）；用量已在 `ai_usage_daily`，可按镜重试控制浪费 |
| 合成阻塞服务 | 全部重活后台线程 + 任务表状态；后续量大可拆独立 worker 进程（同一代码，uvicorn 外单跑 consumer） |
| 域边界污染 | 所有新增代码收敛在 `creation` 命名空间（路由/services/表前缀 `ai_creation*`/`ai_persona` 等），可整体拆出 |

---

## 11. 开放问题（实施前确认）

1. **租户隔离**：现有 `ai_*` 表无 `tenant_id`，创作域沿用"平台级 + create_by"。是否需要租户级隔离？
2. **BGM 形态**：Senseaudio `mode` 是否支持纯伴奏/instrumental？若不支持，P0 先用"带轻人声"或外置曲库。
3. **数字人口播**：当前模型无口型同步，是否接受 P0 用"中景 + B-roll 混切"叙事，数字人放 P2 探索？
4. **图文产线优先级**：图文（生图 + 封面，无视频合成）成本最低、日更最快，是否提到 P1 首位？
5. **发布环节**：P2 的半自动发布（跳转网页版人工点发送）与浏览器全自动，选哪档？

---

## 12. P0 实施记录（2026-09-30）

P0 代码已全部落地。

**ai-service（Python）**

| 文件 | 内容 |
|---|---|
| `app/models/ai_persona.py` / `ai_viral_template.py` / `ai_creation_project.py` / `ai_storyboard_shot.py` | 四张表模型（`text` 列与 sqlalchemy `text()` 冲突，已用 `sa_text` 别名） |
| `app/schemas/creation.py` | 请求 schema |
| `app/services/creation/media_store.py` | 工作目录、OSS 字节上传（含本地回退 `/ai/creation/media/*`）、ffprobe/ffmpeg 封装 |
| `app/services/creation/video_ingest.py` | yt-dlp 下载（懒加载）、SiliconFlow ASR、PySceneDetect 切镜、抽帧、句级切分 |
| `app/services/creation/decomposer.py` | 拆解：下载→ASR→切镜→抽帧→LLM 结构分析→模板入库 |
| `app/services/creation/storyboard.py` | 口播稿→安全审核→分镜（prompt 增强 skill 注入）→校验落库；分镜保存（text 变更自动失效 audio）；发布包 |
| `app/services/creation/composer.py` | 逐镜 TTS→生图→图生视频（复用 ai_image_task/ai_video_task + vendor 分发，轮询至完成）→时间轴（音频锚定）→句级 SRT→FFmpeg 合成→封面；镜级重试 |
| `app/services/creation/persona.py` | 人设 CRUD + 定妆照生成（生图 2K 9:16） |
| `app/routers/creation.py` | 16 端点（拆解/模板/项目/渲染/重试/发布包/人设/本地媒体服务） |
| `app/config.py` | `asr_*`、`creation_*` 配置；`requirements.txt` +yt-dlp/scenedetect/opencv-headless；`Dockerfile` +ffmpeg/fonts-noto-cjk |

自检：`py_compile` 全部通过；`app.openapi()` 确认 16 个 `/ai/creation/*` 路径注册成功。

**backend（Java）**：`twelvet-ai-api/domain/AiCreationProject.java`、`AiCreationProjectMapper.java` + XML（本地分页/软删）、`IAiCreationProjectService` + impl、`AiCreationController`（透传 + `ai:creation:*` 权限）。代码镜像 AiVideo 五件套模式，未跑 Maven 编译。

**SQL**：`backend/sql/ai_creation_schema.sql`（四表，幂等）、`ai_creation_menu.sql`（menu_id 2600-2613，挂在 AI 目录 2112 下）。

**ui（React）**：`config/routes.ts` 新增 `/ai/creation` 路由组；`src/pages/AI/Creation/` 下 `service.ts`、`index.tsx`（项目卡片列表 + 复刻/自创新建向导 + 拆解轮询）、`Editor.tsx`（分镜编辑器：台词/prompt 编辑、逐镜预览与试听、镜级重试、渲染轮询、发布包）、`Template.tsx`、`Persona.tsx`。tsc 复查通过（`@umijs/max` 的 request 报错为裸 tsc 误报，与现有页面一致）。

**上线步骤**

1. 执行 `backend/sql/ai_creation_schema.sql`、`ai_creation_menu.sql`
2. `ai-service/.env` 按 `.env.example` 增量配置（ASR key 可空回退）；确认模型管理中 LLM/IMAGE/VIDEO 默认模型可用
3. 安装依赖：`pip install -r requirements.txt`；ffmpeg 加入 PATH（Windows 可 `winget install ffmpeg`，容器内镜像已带）；Windows 开发把 `CREATION_SUBTITLE_FONT` 改为 `Microsoft YaHei`
4. 重启 ai-service 与 backend，ui 访问「AI → AI 创作工作台」

**P0 已知边界**：BGM 走 `storyboard.audio.bgm.url` 直链（Senseauto 生成歌曲为 P1）；镜间衔接未用 keyframe 串联（P1）；数字人口播未做（P2）。

---

## 13. P1 增量实施记录（2026-09-30，hypit 思路落地轮）

本轮把 hypit（github.com/hypit-ai/hypit）的"锚定到词"与"正文不动只换钩子"两个核心思想落到产线：

1. **WhisperX 词级对齐（可选增强，本地用）**：新增 `app/services/creation/align.py`。
   懒加载 whisperx（本地 `pip install whisperx`，容器镜像不带）；TTS 干声对齐用 base 档足够；
   device=auto（cuda 可用即 float16，失败/无卡退 CPU int8），全局单例 + asyncio.Lock 串行化；
   任一镜对齐失败或 ASR 与台词相似度 <0.5（difflib 守卫，防 ASR 误听污染字幕）→ 整体回退句级 SRT。
2. **词级卡拉OK字幕（ASS）**：`composer._build_ass`——`{\k 厘秒}` 逐词填充，
   未读白（Secondary）/已读高亮（Primary，取 storyboard.subtitles.highlight，默认抖音黄 #FFE24A）；
   PlayRes 720x1280、词间停顿并入前词时长、按 14 字切行。这是"词锚定动效"在纯 FFmpeg/libass
   路线下的最大收益点，不引入 headless Chromium（渲染动效组件的 Web 路线留作本地 P2 可选）。
3. **一键多变体**：`POST /ai/creation/projects/{id}/variants`（ai:creation:add）。
   LLM 生成 N 个差异化策略钩子 → 克隆项目：正文各镜与父项目产物 URL 全复用（图/视频/配音），
   仅首镜换词并失效其配音 → 变体渲染只需重跑首镜 TTS，近乎零成本。storyboard_json.meta
   记 variant_of/variant_angle。Java 透传 + 编辑器「钩子多变体」入口。

新增配置：`creation_align_enabled/model/language/device/compute_type`（见 .env.example 注释）。
