# AI 服务拆分：Java → Python FastAPI 设计文档

**日期：** 2026-07-11
**状态：** 已批准

---

## 1. 背景

当前 twelvet-fast 项目的 AI 功能全部内嵌在 Java 中，使用 Spring AI + WebClient 调用外部 AI API。存在以下问题：

1. Python 是 AI/ML 生态的主力语言，Java 生态 AI 库相对薄弱
2. RAG 知识库功能已搭框架但未实现（processDocAsync 被禁用），Python 端有成熟的 LangChain/LlamaIndex
3. 图片/视频生成的 Agnes API 调用逻辑（900+ 行）用 Java WebClient 实现复杂，Python httpx 更简洁
4. AI 购物导购的 Tool Calling 在 Spring AI 中实现受限，LangChain 支持更丰富
5. AI 相关依赖（Spring AI、WebFlux）增加了 Java 项目的复杂度和依赖面

**目标：** 将全部 AI 功能迁移到独立的 Python FastAPI 服务，Java 通过 HTTP 调用，共享 MySQL 数据库。

---

## 2. 架构设计

```
                          ┌─────────────────────────────────────┐
                          │        前端 (twelvet-ui)             │
                          └──────────────┬──────────────────────┘
                                         │ HTTP
                          ┌──────────────▼──────────────────────┐
                          │   Java (twelvet-admin:8080)          │
                          │                                     │
                          │  Controller (Sa-Token 权限校验)       │
                          │       │                             │
                          │  Service 层 (HTTP → Python)         │
                          │       │                             │
                          └───────┼─────────────────────────────┘
                                  │ HTTP (内部调用)
                          ┌───────▼─────────────────────────────┐
                          │  Python (twelvet-ai-service:8001)    │
                          │                                     │
                          │  FastAPI + LangChain                 │
                          │  ├── /ai/chat    (LLM 对话+导购)     │
                          │  ├── /ai/rag     (RAG 知识库)        │
                          │  ├── /ai/image   (图片生成)          │
                          │  ├── /ai/video   (视频生成)          │
                          │  └── /ai/model   (模型配置)          │
                          │                                     │
                          └───────┬─────────────────────────────┘
                                  │
                          ┌───────▼─────────────────────────────┐
                          │     MySQL (共享 ai_* 表)              │
                          │     + FAISS 向量存储 (Python 本地)    │
                          └─────────────────────────────────────┘
```

### 通信方式

- **Java → Python**：HTTP REST 调用，Java 用 WebClient
- **流式响应**：Python 返回 FastAPI StreamingResponse（SSE），Java 用 SseEmitter 转发给前端
- **认证**：Python 不做用户认证，Java Controller 层完成 Sa-Token 权限校验后才调 Python
- **服务发现**：Python 地址配置在 Java 的 `application.yml`（`ai.service.url: http://localhost:8001`）

### 数据归属

| 数据 | 存储位置 | 读写方 |
|------|---------|-------|
| ai_model（模型配置） | MySQL | Python 读写，Java 代理 CRUD |
| ai_knowledge（知识库） | MySQL | Python 读写 |
| ai_doc / ai_doc_slice | MySQL | Python 读写 |
| ai_chat_history | MySQL | Python 读写 |
| ai_image_task / ai_video_task | MySQL | Python 读写，Java 代理查询 |
| 向量嵌入 | FAISS（本地文件） | Python 独占 |
| product 数据 | MySQL | Python 只读（商品搜索） |

---

## 3. Python 项目结构

```
twelvet-ai-service/                    # 位于 F:\code\weiwei\twelvet-ai-service\
├── app/
│   ├── main.py                        # FastAPI 入口，挂载路由、CORS、生命周期
│   ├── config.py                      # 配置管理（MySQL、AI API keys、服务端口等）
│   ├── database.py                    # SQLAlchemy 2.0 async 连接 + session 管理
│   ├── models/                        # SQLAlchemy ORM 模型（对应 MySQL ai_* 表）
│   │   ├── ai_model.py
│   │   ├── ai_knowledge.py
│   │   ├── ai_doc.py
│   │   ├── ai_doc_slice.py
│   │   ├── ai_chat_history.py
│   │   ├── ai_image_task.py
│   │   └── ai_video_task.py
│   ├── routers/                       # FastAPI 路由
│   │   ├── chat.py                    # POST /ai/chat, /ai/chat/stream
│   │   ├── rag.py                     # /ai/rag/* (知识库/文档/对话)
│   │   ├── image.py                   # POST /ai/image/generate, GET /ai/image/status/{id}
│   │   ├── video.py                   # POST /ai/video/generate, GET /ai/video/status/{id}
│   │   └── model_config.py            # /ai/model/* (CRUD)
│   ├── services/                      # 业务逻辑
│   │   ├── llm_service.py             # LangChain ChatClient（DeepSeek/OpenAI 兼容）
│   │   ├── rag_service.py             # RAG：文档切片 + 向量化 + 检索
│   │   ├── product_search.py          # 商品搜索（Tool Calling 用）
│   │   ├── agnes_image.py             # Agnes 图片生成（异步 + 重试）
│   │   ├── agnes_video.py             # Agnes 视频生成（异步 + 状态轮询）
│   │   └── embedding_service.py       # Embedding 生成 + 向量存储
│   ├── schemas/                       # Pydantic 请求/响应模型
│   │   ├── chat.py
│   │   ├── rag.py
│   │   ├── image.py
│   │   ├── video.py
│   │   └── model_config.py
│   └── vectorstore/                   # 向量存储
│       └── faiss_store.py             # FAISS 索引管理
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

### 技术选型

| 功能 | 技术 | 说明 |
|------|------|------|
| Web 框架 | FastAPI | 异步、自动 OpenAPI 文档 |
| ORM | SQLAlchemy 2.0 + async | 直连共享 MySQL |
| AI 框架 | LangChain | LLM 调用 + RAG + Tool Calling |
| 向量存储 | FAISS（本地） | RAG 文档嵌入检索 |
| 异步任务 | asyncio + BackgroundTasks | 图片/视频生成异步处理 |
| HTTP 客户端 | httpx | 调 Agnes API |

---

## 4. API 接口设计

### Python FastAPI 端点

#### LLM 对话
```
POST   /ai/chat              # 同步对话（AI 购物导购）
       Request:  { messages: [{role, content}], user_id? }
       Response: { reply, products: [...] }

POST   /ai/chat/stream       # 流式对话（SSE）
       Request:  同上
       Response: SSE events:
         data: {"type":"text","content":"..."}
         data: {"type":"products","products":[...]}
         data: {"type":"error","message":"..."}
```

#### RAG 知识库
```
GET    /ai/rag/knowledge     # 知识库列表
POST   /ai/rag/knowledge     # 创建知识库
PUT    /ai/rag/knowledge     # 更新知识库
DELETE /ai/rag/knowledge/{id}# 删除知识库
POST   /ai/rag/doc/upload    # 上传文档（切片+向量化）
DELETE /ai/rag/doc/{id}      # 删除文档
GET    /ai/rag/doc/{id}/slices # 查看文档切片
POST   /ai/rag/chat          # RAG 对话
POST   /ai/rag/chat/stream   # RAG 流式对话
```

#### 图片生成
```
POST   /ai/image/generate    # 创建图片生成任务
       Request:  { model_id?, prompt, negative_prompt?, size?, gen_type?, input_image?, ... }
       Response: { task_id, status: "queued" }

GET    /ai/image/status/{id} # 查询状态
GET    /ai/image/list        # 任务列表（分页）
DELETE /ai/image/{ids}       # 删除任务
```

#### 视频生成
```
POST   /ai/video/generate    # 创建视频生成任务
       Request:  { model_id?, prompt, gen_type?, width?, height?, ... }
       Response: { task_id, status: "queued" }

GET    /ai/video/status/{id} # 查询状态（含实时 Agnes 查询）
GET    /ai/video/list        # 任务列表（分页）
DELETE /ai/video/{ids}       # 删除任务
```

#### 模型配置
```
GET    /ai/model/list        # 模型列表（分页）
GET    /ai/model/{id}        # 模型详情
POST   /ai/model             # 新增模型
PUT    /ai/model             # 更新模型
DELETE /ai/model/{ids}       # 删除模型
PUT    /ai/model/status      # 切换默认模型
```

---

## 5. Java 端改动

### 删除的文件/代码
- `AgnesImageService.java`（525 行）→ Python agnes_image.py
- `AgnesVideoService.java`（407 行）→ Python agnes_video.py
- `AiGuideService.java` → Python llm_service.py
- `ProductAiService.java` → Python product_search.py
- `ProductSearchTools.java` → Python tool calling
- `AiConfig.java`（Spring AI EmbeddingModel bean）→ 不再需要
- `WebClientConfig.java`（Agnes 专用）→ 改为通用 Python 服务调用配置

### 修改的文件
- `twelvet-ai-server/pom.xml`：移除 `spring-ai-bom`、`spring-ai-starter-model-openai`、`spring-boot-starter-webflux`
- `twelvet-product-server/pom.xml`：移除 `spring-ai-bom`、`spring-ai-starter-model-openai`
- `AiImageController.java`：Service 层改为 HTTP 调 Python
- `AiVideoController.java`：Service 层改为 HTTP 调 Python
- `AiModelController.java`：Service 层改为 HTTP 调 Python
- `AiGuideController.java`：Service 层改为 HTTP 调 Python
- `application.yml`：移除 `spring.ai` 配置，新增 `ai.service.url`
- `application-dev.yml`：新增 `ai.service.url: http://localhost:8001`
- `application-pro.yml`：新增 `ai.service.url: http://twelvet-ai:8001`

### 新增的文件
- `AiServiceClient.java`：HTTP 客户端，封装对 Python 服务的调用（包含流式 SSE 转发）

### Java Service 层改造模式

每个 AI Service 从"直接调 API"改为"HTTP 调 Python"：

```java
// 之前
@Service
public class AiImageServiceImpl {
    @Autowired private AgnesImageService agnesImageService;
    public AiImageTask generate(AiImageTask task) {
        agnesImageService.createImageTaskAsync(task);
        return task;
    }
}

// 之后
@Service
public class AiImageServiceImpl {
    @Autowired private AiServiceClient aiClient;
    public AiImageTask generate(AiImageTask task) {
        return aiClient.post("/ai/image/generate", task, AiImageTask.class);
    }
}
```

### 流式响应转发

```
前端 ←SSE← Java SseEmitter ←SSE← Python StreamingResponse
```

Java AiGuideController 的 `/product/ai-guide/chat/stream`：
1. 创建 SseEmitter
2. 用 WebClient 订阅 Python `/ai/chat/stream` 的 SSE 流
3. 逐条转发 SSE event 给前端
4. Python 流结束 → SseEmitter.complete()

---

## 6. 错误处理

| 场景 | Java 端处理 | Python 端处理 |
|------|-----------|-------------|
| Python 服务不可用 | 捕获 ConnectException，返回 `JsonResult.error("AI 服务暂不可用")` | — |
| Python 返回 4xx | 解析错误响应体，透传错误信息 | 返回 `{"detail": "..."}` |
| Python 返回 5xx | 返回 `JsonResult.error("AI 服务异常")` | 内部异常捕获 + 日志 |
| AI API Key 无效 | 透传 Python 的 503 错误 | 返回 503 + 明确错误信息 |
| Agnes API 超时 | Java 轮询时得到 Python 的最新状态 | 内部重试 5 次 + 状态更新到 DB |

---

## 7. 部署方案

### 开发环境
```bash
# Java
cd twelvet-fast && mvn spring-boot:run  # localhost:8080

# Python
cd twelvet-ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001  # localhost:8001
```

### 生产环境（Docker）
在 docker-compose.yml 中新增：

```yaml
twelvet-ai:
  build: ../twelvet-ai-service
  ports:
    - "8001:8001"
  environment:
    - DATABASE_URL=mysql+aiomysql://root:xxx@twelvet-mysql:3306/twelvet-fast
    - AI_BASE_URL=https://api.deepseek.com
    - AI_API_KEY=${AI_API_KEY}
  depends_on:
    - twelvet-mysql
  networks:
    - twelvet-network
```

---

## 8. 迁移步骤（概要）

1. **搭建 Python 项目骨架**：FastAPI + SQLAlchemy + 目录结构
2. **实现模型配置 CRUD**：Python 端读写 ai_model 表
3. **迁移图片生成**：AgnesImageService → Python agnes_image.py
4. **迁移视频生成**：AgnesVideoService → Python agnes_video.py
5. **迁移 LLM 对话**：AiGuideService → Python llm_service.py + product_search.py
6. **实现 RAG 知识库**：Python rag_service.py + faiss_store.py（新功能）
7. **Java 端改造**：Service 层改为 HTTP 调 Python
8. **移除 Java AI 依赖**：Spring AI、WebFlux 等
9. **联调测试**：端到端验证所有 AI 功能
10. **部署配置**：Docker Compose + 环境配置
