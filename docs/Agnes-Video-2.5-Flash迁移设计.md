# Agnes Video 2.5 Flash 迁移设计

> 对应文档：`docs/ai/模型调用文档说明.md`（Agnes 3.0 Flash / Agnes Image 2.5 Flash / Agnes Video 2.5 Flash）
> 目标：把 ai-service 的视频生成链路切换到 Agnes Video 2.5 Flash 协议；图像链路核对结论保留。

## 一、总览

| 服务 | 现状 | 结论 |
| --- | --- | --- |
| 文本 LLM（chat / assist / moderation / recommend） | `POST /v1/chat/completions`，字段与文档一致 | **无需改代码**，只需在 `ai_model` 表配置 `model_name = agnes-3.0-flash` |
| 图像（`agnes_image.py` + `openai_image.py`） | 端点、`extra_body` 布局、尺寸表、禁用 `tags` 全部对齐 | **无需改动**（一处可选优化，见 §二） |
| 视频（`agnes_video.py` + 相关 schema/model/router） | 参数协议与新版完全不兼容 | **必须重写**，见 §三 |

---

## 二、图像链路核对（结论：无需改动）

代码位置：`ai-service/app/services/agnes_image.py`、`ai-service/app/services/openai_image.py`

| 核对项 | 现状 | 文档要求 | 结论 |
| --- | --- | --- | --- |
| 端点 | `POST {base}/v1/images/generations` | 同 | ✅ |
| `response_format` 位置 | 在 `extra_body` 内（`_image_extra_body`） | 必须放 `extra_body`，顶层被 400 拒 | ✅ |
| 图生图参考图 | `extra_body.image`（数组） | 同 | ✅ |
| 多图合成 | 单 URL 或 JSON 数组字符串都兼容 | `extra_body.image` 数组 | ✅ |
| `tags: ["img2img"]` | 未传 | 明确禁止 | ✅ |
| 尺寸表 `SIZE_TABLE` | 精确复刻文档输出尺寸参考表（1K/2K/3K/4K × 8 种 ratio） | 同 | ✅ |
| Base64 输出 | 文生图用 `return_base64`，图生图用 `extra_body.response_format=b64_json` | 同 | ✅ |

### 可选优化（非必修）

文档推荐"档位式 `size` 配合 `ratio` 一起发"。当前实现把 `size` 解析成精确像素后只发 `size`，没发 `ratio`。因为已经解析成精确像素，Agnes 不会二次归一化，功能上没问题。如果想更贴合文档推荐写法，可在 `build_request_body` 里补上 `ratio` 字段。

> 结论：本次不做此项改动。

---

## 三、视频链路差异（结论：重写级）

代码位置：
- `ai-service/app/services/agnes_video.py`
- `ai-service/app/schemas/video.py`
- `ai-service/app/models/ai_video_task.py`
- `ai-service/app/routers/video.py`

### 3.1 参数协议差异

| 项 | 现有代码 | 新文档（Flash） |
| --- | --- | --- |
| 模型名 | `ai_model.model_name` 配置 | `agnes-video-2.5-flash` |
| 创建端点 | `POST /v1/videos` | 同 ✅ |
| 查询端点 | `GET /agnesapi?video_id=...` | `GET /agnesapi?video_id=...&model_name=agnes-video-2.5-flash`（keyframe / reference 模式**必须**带 `model_name`，否则查不到） |
| 时长 | `num_frames`（≤441，8n+1）+ `frame_rate`（1~60） | `seconds`（**字符串** `"4"`~`"12"`，默认 `"5"`） |
| 分辨率 | `width` / `height`（默认 1152×768） | `size`（**必须字符串 `"720P"`**，其他值 HTTP 400）+ `aspect_ratio` |
| 生成模式 | `gen_type`：`text2vid` / `img2vid` / `multi2vid` / `keyframes` | `mode`：`text` / `keyframe` / `reference` |
| 文生视频 | `text2vid` | `mode=text`（禁止 `first_frame` / `last_frame` / `images` / `audios` / `videos`） |
| 图生视频 | `img2vid` → `body.image = 单 URL` | `mode=keyframe` → `first_frame` / `last_frame`（至少一个，**互斥**于 `images` / `audios` / `videos`） |
| 多图参考 | `multi2vid` → `extra_body.image = [urls]` | `mode=reference` → `images` 数组（**≤5 张**，超过 400）+ `audios` 数组（**≤3 段**），**禁止 `videos`** |
| `negative_prompt` | 支持 | 未列出，倾向不再支持 |
| `n` | 未处理 | 仅支持 `1`，默认 `1` |
| `seed` | 支持 | 支持 |

### 3.2 Flash 专属校验

- `size` 仅接受字符串 `"720P"`，其他值 → HTTP 400 `size must be 720P`
- `reference` 模式 `images` 超过 5 张 → HTTP 400 `images length must not exceed 5`
- `reference` 模式 `audios` 超过 3 段 → HTTP 400 `audios length must not exceed 3`
- `reference` 模式传 `videos` → HTTP 400 `videos is not supported`
- 校验在任务创建、排队、计费和推理前执行，失败请求不产生费用

### 3.3 视频尺寸与画幅

`size` 固定 `"720P"`，输出像素由 `aspect_ratio` 决定：

| aspect_ratio | 输出像素 |
| --- | --- |
| 21:9 | 1680×720 |
| 16:9 | 1280×704 |
| 4:3 | 960×720 |
| 1:1 | 720×720 |
| 3:4 | 720×960 |
| 9:16 | 720×1280 |

### 3.4 参考图/音频在 prompt 里的指代

`reference` 模式下 prompt 使用 `<Picture N>` / `<Audio N>` 占位符引用素材：

```
以 <Picture 1> 中的角色和美术风格为参考，角色在花田中自然奔跑，保持外观一致
```

### 3.5 查询协议

推荐方式（全部模式适用）：

```
GET https://apihub.agnes-ai.com/agnesapi?video_id=VIDEO_ID&model_name=agnes-video-2.5-flash
```

仅 `mode=text` 可用简化形式 `?video_id=VIDEO_ID`。**keyframe 和 reference 模式必须指定 `model_name`。**

响应字段与旧版一致：`status` / `progress` / `seconds` / `size` / `metadata.url` / `url` / `error`。

---

## 四、改动清单

### 4.1 数据库

`ai_video_task` 表新增列（nullable，向后兼容旧数据）：

```sql
ALTER TABLE ai_video_task ADD COLUMN mode VARCHAR(20);
ALTER TABLE ai_video_task ADD COLUMN seconds VARCHAR(10);
ALTER TABLE ai_video_task ADD COLUMN aspect_ratio VARCHAR(10);
ALTER TABLE ai_video_task ADD COLUMN first_frame VARCHAR(500);
ALTER TABLE ai_video_task ADD COLUMN last_frame VARCHAR(500);
ALTER TABLE ai_video_task ADD COLUMN images TEXT;
ALTER TABLE ai_video_task ADD COLUMN audios TEXT;
```

旧列 `num_frames` / `frame_rate` / `width` / `height` / `negative_prompt` 保留但不写入（避免破坏历史数据读取）。

### 4.2 代码文件

| 文件 | 改动 |
| --- | --- |
| `app/models/ai_video_task.py` | 新增列：`mode` / `seconds` / `aspect_ratio` / `first_frame` / `last_frame` / `images` / `audios` |
| `app/schemas/video.py` | `VideoGenerateRequest` 字段重命名：删除 `width` / `height` / `num_frames` / `frame_rate` / `negative_prompt`；新增 `mode` / `seconds` / `aspect_ratio` / `first_frame` / `last_frame` / `images` / `audios` |
| `app/services/agnes_video.py` | 重写 `build_video_request_body`（三分支：text / keyframe / reference）；`query_video_status` 查询 URL 追加 `&model_name={model_name}`；删除 `normalize_num_frames` / `clamp_frame_rate`；新增 `validate_flash_constraints`（size / images / audios / videos 四条校验） |
| `app/routers/video.py` | 请求字段名同步；调用新校验函数 |
| `backend/data/*.sql`（迁移脚本） | 新增 `twelve-ai-video-flash-init.sql` 提供 ALTER TABLE |

### 4.3 前端

需确认 `ui/src` 下调用 `/ai/video/generate` 的表单字段是否需要同步。目前已知 Runner 与 AI 相关页面可能涉及，实施阶段单独排查。

---

## 五、方案选择

| 方案 | 描述 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **A（推荐）** | 直接切换到新协议，删除旧字段 | 代码干净，单协议维护 | 前端字段名一起改；旧任务数据字段留空 |
| B | 双协议分发，根据 `ai_model.model_name` 是否含 `-flash` 走新/旧分支 | 保留旧调用不动 | 维护两套协议；后续删除成本更高 |

推荐 **A**。理由：
- 现有旧协议参数（`num_frames` / `frame_rate` / `width` / `height`）在 Flash 上完全无效，保留只是"看起来兼容"，实际跑不通。
- 前端表单改动量可控（改字段名，不是重构页面）。
- 历史任务数据只读不写，旧列留空即可。

---

## 六、`negative_prompt` 处理

文档未列出该字段，倾向认为已不再支持。处理策略：

- 从 `VideoGenerateRequest` schema 中删除字段
- DB 列保留（历史数据可读），但新写入不再存
- 提示词增强流程（`prompt_enhancer.enrich_prompt`）里对视频的 negative_prompt 填充逻辑一并移除

---

## 七、后续步骤

1. 本方案评审通过后，编写 SQL 迁移脚本 `backend/data/twelve-ai-video-flash-init.sql`
2. 按 §四 清单改代码，跑 `ai-service` 现有测试
3. 排查 `ui/src` 下所有调用点并同步字段
4. 集成测试：手动调 Flash 三个模式（text / keyframe / reference）验证 400 校验与成品
5. 提交 PR 合并

---

## 附录 A：新协议请求示例

### 文生视频

```json
{
  "model": "agnes-video-2.5-flash",
  "prompt": "夜晚森林中三只猫组成微型铜管乐队向前行进，镜头平稳后退，月光穿过树叶",
  "seconds": "5",
  "mode": "text",
  "size": "720P",
  "aspect_ratio": "16:9"
}
```

### 首尾帧控制

```json
{
  "model": "agnes-video-2.5-flash",
  "prompt": "人物从首帧姿态自然转身走向窗边，镜头缓慢推进并平滑过渡到尾帧",
  "seconds": "5",
  "mode": "keyframe",
  "size": "720P",
  "first_frame": "https://example.com/first.png",
  "last_frame": "https://example.com/last.png"
}
```

### 图片参考

```json
{
  "model": "agnes-video-2.5-flash",
  "prompt": "以 <Picture 1> 中的角色和美术风格为参考，角色在花田中自然奔跑，保持外观一致",
  "seconds": "5",
  "mode": "reference",
  "size": "720P",
  "aspect_ratio": "16:9",
  "images": ["https://example.com/character.png"]
}
```

### 音频参考

```json
{
  "model": "agnes-video-2.5-flash",
  "prompt": "以 <Audio 1> 的节奏和环境氛围作为参考，生成电影感夜间驾驶画面",
  "seconds": "5",
  "mode": "reference",
  "size": "720P",
  "aspect_ratio": "16:9",
  "audios": ["https://example.com/reference-audio.mp3"]
}
```

## 附录 B：查询示例

```bash
# 推荐（全部模式适用）
curl "https://apihub.agnes-ai.com/agnesapi?video_id=VIDEO_ID&model_name=agnes-video-2.5-flash" \
  -H "Authorization: Bearer $AGNES_API_KEY"

# 仅 text 模式可用
curl "https://apihub.agnes-ai.com/agnesapi?video_id=VIDEO_ID" \
  -H "Authorization: Bearer $AGNES_API_KEY"
```
