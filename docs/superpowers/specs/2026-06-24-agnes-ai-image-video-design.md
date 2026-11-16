# Agnes AI 图片/视频生成功能设计文档

> **日期：** 2026-06-24
> **状态：** 已批准
> **复杂度：** L（大功能，涉及前后端多模块）

---

## 1. 概述

将 **Agnes Image 2.1 Flash**（文生图/图生图）和 **Agnes Video V2.0**（文生视频/图生视频/多图视频/关键帧动画）集成到现有 `twelvet-ai` 模块中。

**集成方式：** 扩展现有 AI 模块，复用 `ai_model` 表管理 Agnes API Key 和 Base URL。

**技术方案：** 独立 HTTP 客户端（WebClient）直接调用 Agnes REST API，不依赖 Spring AI 的 ImageModel 抽象层。

---

## 2. 功能范围

| 功能 | 说明 |
|------|------|
| 文生图 | 根据文本提示词生成图片，支持 URL 和 Base64 输出 |
| 图生图 | 根据提示词对已有图片进行风格转换/编辑，支持 URL 和 Data URI Base64 输入 |
| 文生视频 | 根据文本提示词生成视频（异步任务，轮询查询结果） |
| 图生视频 | 将静态图片动画化为视频，包括多图视频和关键帧动画 |
| 历史记录 | 保存每次生成的参数、结果、状态等信息到数据库 |
| DFS 存储 | 生成的图片/视频保存到系统 DFS 文件存储 |

---

## 3. 架构设计

### 3.1 模块划分

在现有 `twelvet-ai` 模块中扩展：

```
twelvet-ai/
├── twelvet-ai-api/              # 新增领域对象
│   └── domain/
│       ├── AiImageTask.java      # 图片生成任务
│       └── AiVideoTask.java      # 视频生成任务
│
├── twelvet-ai-server/           # 新增业务逻辑
│   └── service/
│       ├── AgnesImageService.java       # 图片生成核心服务（WebClient 调用 Agnes API）
│       ├── AgnesVideoService.java       # 视频生成核心服务（WebClient 调用 Agnes API）
│       ├── IAiImageTaskService.java     # 图片任务 CRUD 接口
│       ├── AiImageTaskServiceImpl.java  # 图片任务 CRUD 实现
│       ├── IAiVideoTaskService.java     # 视频任务 CRUD 接口
│       └── AiVideoTaskServiceImpl.java  # 视频任务 CRUD 实现
│   └── controller/
│       ├── AiImageController.java       # /ai/image/* 接口
│       └── AiVideoController.java       # /ai/video/* 接口
```

### 3.2 数据流

**图片生成（同步）：**
```
用户提交请求 → AiImageController
  → AgnesImageService（WebClient 调用 Agnes API）
  → 下载生成图片 → 保存到 DFS → 返回 DFS URL
  → AiImageTaskService（保存任务记录到 ai_image_task 表）
```

**视频生成（异步）：**
```
用户提交请求 → AiVideoController
  → AgnesVideoService（创建任务，返回 video_id）
  → AiVideoTaskService（保存任务记录到 ai_video_task 表，状态=queued）
  → 前端轮询 /ai/video/status/{id}
  → AgnesVideoService（查询 Agnes API 状态）
  → 完成时：下载视频 → 保存到 DFS → 更新任务状态为 completed + DFS URL
```

### 3.3 模型管理扩展

在 `ai_model` 表的 `model_provider` 字典中新增 `AGNES`，`model_type` 字典中新增 `IMAGE` 和 `VIDEO`。Agnes 的 API Key 和 Base URL 通过 `ai_model` 表统一管理。

---

## 4. 数据模型

### 4.1 ai_image_task — 图片生成任务表

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | bigint (PK, auto) | 任务 ID |
| model_id | bigint | 关联 ai_model.model_id |
| prompt | text | 提示词 |
| size | varchar(20) | 输出尺寸，如 1024x768 |
| gen_type | varchar(10) | 生成类型：text2img / img2img |
| input_image | text | 图生图输入图片 URL 或 Base64（可为空） |
| response_format | varchar(10) | 输出格式：url / b64_json |
| result_url | text | 生成结果 DFS URL（完成后填入） |
| status | varchar(10) | 状态：pending / completed / failed |
| error_msg | text | 错误信息 |
| create_by | varchar(64) | 创建者 |
| create_time | datetime | 创建时间 |

### 4.2 ai_video_task — 视频生成任务表

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | bigint (PK, auto) | 本地任务 ID |
| model_id | bigint | 关联 ai_model.model_id |
| agnes_task_id | varchar(64) | Agnes 返回的 task_id |
| agnes_video_id | varchar(64) | Agnes 返回的 video_id |
| prompt | text | 提示词 |
| gen_type | varchar(20) | 生成类型：text2vid / img2vid / multi2vid / keyframes |
| input_images | text | 输入图片 JSON 数组（可为空） |
| width | int | 视频宽度，默认 1152 |
| height | int | 视频高度，默认 768 |
| num_frames | int | 帧数，默认 121 |
| frame_rate | int | 帧率，默认 24 |
| negative_prompt | text | 负向提示词（可为空） |
| seed | bigint | 随机种子（可为空） |
| status | varchar(10) | 状态：queued / in_progress / completed / failed |
| progress | int | 进度百分比 0-100 |
| result_url | text | 生成结果 DFS URL（完成后填入） |
| video_seconds | varchar(10) | 视频时长（秒） |
| video_size | varchar(20) | 视频分辨率 |
| error_msg | text | 错误信息 |
| create_by | varchar(64) | 创建者 |
| create_time | datetime | 创建时间 |

### 4.3 字典数据扩展

- `ai_model_provider` 新增：`AGNES`（Agnes AI）
- `ai_model_type` 新增：`IMAGE`（图片生成）、`VIDEO`（视频生成）

### 4.4 预置模型数据

```sql
INSERT INTO ai_model (model_provider, model_type, model_name, model_alias, api_key, base_url, temperature, top_p, default_flag, status)
VALUES ('AGNES', 'IMAGE', 'agnes-image-2.1-flash', 'Agnes Image 2.1 Flash', '', 'https://apihub.agnes-ai.com', 0, 0, 1, 0);

INSERT INTO ai_model (model_provider, model_type, model_name, model_alias, api_key, base_url, temperature, top_p, default_flag, status)
VALUES ('AGNES', 'VIDEO', 'agnes-video-v2.0', 'Agnes Video V2.0', '', 'https://apihub.agnes-ai.com', 0, 0, 1, 0);
```

---

## 5. API 设计

### 5.1 图片生成 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/image/generate` | 提交图片生成任务（同步，等待返回） |
| GET | `/ai/image/pageQuery` | 分页查询图片生成历史 |
| GET | `/ai/image/{taskId}` | 查询单个任务详情 |
| DELETE | `/ai/image/{taskId}` | 删除任务记录 |

**POST /ai/image/generate 请求体：**
```json
{
  "prompt": "A luminous floating city...",
  "size": "1024x768",
  "genType": "text2img",
  "inputImage": "",
  "responseFormat": "url"
}
```

**响应：**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": 1,
    "resultUrl": "/dfs/ai-image/2026/06/24/xxx.png",
    "status": "completed"
  }
}
```

### 5.2 视频生成 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/video/generate` | 提交视频生成任务（异步，立即返回） |
| GET | `/ai/video/status/{taskId}` | 查询任务状态和进度 |
| GET | `/ai/video/pageQuery` | 分页查询视频生成历史 |
| GET | `/ai/video/{taskId}` | 查询单个任务详情 |
| DELETE | `/ai/video/{taskId}` | 删除任务记录 |

**POST /ai/video/generate 请求体：**
```json
{
  "prompt": "A cinematic shot of a cat...",
  "genType": "text2vid",
  "inputImages": [],
  "width": 1152,
  "height": 768,
  "numFrames": 121,
  "frameRate": 24,
  "negativePrompt": "",
  "seed": null
}
```

**响应（立即返回）：**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "taskId": 1,
    "agnesVideoId": "video_xxxxxx",
    "status": "queued",
    "progress": 0
  }
}
```

**GET /ai/video/status/{taskId} 响应：**
```json
{
  "code": 200,
  "data": {
    "taskId": 1,
    "status": "completed",
    "progress": 100,
    "resultUrl": "/dfs/ai-video/2026/06/24/xxx.mp4",
    "videoSeconds": "10.0",
    "videoSize": "1280x768"
  }
}
```

---

## 6. 前端设计

### 6.1 /ai/image — 图片生成页面

**布局：** 左右分栏

**左侧 — 生成表单区：**
- 生成类型切换：文生图 / 图生图（Tab 切换）
- 提示词输入（TextArea，支持多行）
- 图生图时：输入图片上传或 URL 输入
- 输出尺寸选择（下拉：1024x768, 768x1024, 1024x1024）
- 输出格式选择（URL / Base64）
- 生成按钮

**右侧 — 结果展示区：**
- 生成的图片预览
- 下载按钮
- 下方：历史记录列表（Ant Design ProTable，分页查询，支持删除）

### 6.2 /ai/video — 视频生成页面

**布局：** 同样左右分栏

**左侧 — 生成表单区：**
- 生成类型切换：文生视频 / 图生视频 / 多图视频 / 关键帧动画（Tab 切换）
- 提示词输入（TextArea）
- 图生视频时：输入图片上传
- 多图/关键帧时：多张图片上传
- 高级参数折叠面板：宽度、高度、帧数、帧率、负向提示词、随机种子
- 生成按钮

**右侧 — 结果展示区：**
- 视频播放器（生成完成后展示）
- 进度条（生成中时轮询展示）
- 下载按钮
- 下方：历史记录列表

### 6.3 路由注册

在 `config/routes.ts` 的 AI 路由组下新增：
```ts
{ path: '/ai/image', name: '图片生成', component: './AI/Image' },
{ path: '/ai/video', name: '视频生成', component: './AI/Video' },
```

### 6.4 菜单图标

- 图片生成：`<PictureOutlined />`
- 视频生成：`<VideoCameraOutlined />`

---

## 7. 错误处理策略

### 7.1 后端错误处理

| 场景 | 处理方式 |
|------|---------|
| Agnes API 返回 401 | 返回前端：`"API Key 无效，请在模型管理中检查 Agnes 模型配置"` |
| Agnes API 返回 400 | 解析错误信息，返回前端具体原因 |
| Agnes API 返回 503 | 返回前端：`"服务繁忙，请稍后重试"` |
| Agnes API 超时 | 图片生成设置 120s 超时；视频生成创建任务设置 30s 超时 |
| DFS 保存失败 | 任务标记为 failed，error_msg 记录 DFS 错误，不丢失 Agnes 原始 URL |
| 视频轮询超过 10 分钟 | 标记任务为 failed，error_msg = "生成超时" |

### 7.2 前端错误处理

| 场景 | 处理方式 |
|------|---------|
| 图片生成失败 | Message.error 提示错误信息，保留表单内容方便重试 |
| 视频生成提交失败 | Message.error 提示，不创建历史记录 |
| 视频轮询失败 | 显示"查询状态失败"，提供手动刷新按钮 |
| 输入图片 URL 不可访问 | 前端预校验提示 |

### 7.3 重试策略

- 图片生成：前端提供"重试"按钮，用户手动触发
- 视频生成：不自动重试，用户可重新提交任务

---

## 8. 测试策略

### 8.1 后端测试

| 测试类型 | 覆盖范围 |
|---------|---------|
| 单元测试 | `AgnesImageService`（Mock WebClient）、`AgnesVideoService`（Mock WebClient） |
| 集成测试 | Controller 层（MockMvc），验证请求/响应格式 |
| 关键测试用例 | 文生图成功、图生图成功、图生图缺少 inputImage 报错、视频创建任务成功、视频状态轮询、DFS 保存失败降级 |

### 8.2 前端测试

暂不编写前端单元测试，通过手动验证功能完整性。

---

## 9. Agnes API 关键注意事项

- `response_format` 不能放在请求体顶层，必须放在 `extra_body` 中
- 图生图不需要传 `tags: ["img2img"]`
- 文生图 Base64 输出使用顶层参数 `return_base64: true`
- 图生图 Base64 输出使用 `extra_body.response_format: "b64_json"`
- 图生图输入图片放在 `extra_body.image` 数组中
- 视频生成是异步任务，先创建再轮询
- `num_frames` 必须 ≤ 441 且满足 8n+1
- 推荐轮询间隔 5s，客户端超时 60-360s
