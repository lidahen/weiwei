# AI 模块后端设计文档

## 一、概述

### 1.1 目标
实现 AI 模块后端服务，支持 RAG（检索增强生成）功能，包括：
- 知识库管理
- 文档管理与切片
- 模型配置管理
- AI 对话（SSE 流式 + RAG 检索）

### 1.2 范围
**第一期范围：**
- 知识库 CRUD
- 文档上传、切片、向量化
- 模型配置管理（LLM + Embedding）
- AI 对话（SSE 流式、知识库检索）
- 向量存储（SimpleVectorStore + 文件持久化）

**后续迭代：**
- MCP 工具协议
- TTS 语音播报
- 联网搜索

### 1.3 技术栈
| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.4 |
| AI 框架 | Spring AI | 1.0.0 |
| ORM | MyBatis | 3.0.3 |
| 数据库 | MySQL | 8.0+ |
| 向量存储 | SimpleVectorStore | Spring AI 内置 |

---

## 二、模块架构

### 2.1 目录结构
```
twelvet-fast/
├── twelvet-ai/
│   ├── twelvet-ai-api/                  # API 层：实体、DTO
│   │   └── src/main/java/com/twelvet/ai/api/domain/
│   │       ├── AiKnowledge.java
│   │       ├── AiDoc.java
│   │       ├── AiDocSlice.java
│   │       ├── AiModel.java
│   │       └── AiChatHistory.java
│   │
│   └── twelvet-ai-server/               # 服务层
│       └── src/main/java/com/twelvet/ai/server/
│           ├── controller/
│           ├── service/
│           ├── mapper/
│           └── rag/
│
├── data/
│   └── vector-store/                    # 向量存储目录
```

### 2.2 模块依赖
```
twelvet-admin
    └── twelvet-ai-server
            ├── twelvet-ai-api
            ├── twelvet-framework-core
            ├── twelvet-framework-datasource
            ├── twelvet-framework-redis
            ├── twelvet-framework-security
            ├── twelvet-framework-log
            └── spring-ai-starter-model-openai
```

---

## 三、数据模型

### 3.1 数据库表设计

#### ai_knowledge（知识库表）
```sql
CREATE TABLE ai_knowledge (
    knowledge_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识库ID',
    knowledge_name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    knowledge_desc VARCHAR(500) COMMENT '知识库描述',
    knowledge_sort INT DEFAULT 0 COMMENT '排序',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新者',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标志'
) COMMENT 'AI知识库表';
```

#### ai_doc（文档表）
```sql
CREATE TABLE ai_doc (
    doc_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识库ID',
    doc_name VARCHAR(200) NOT NULL COMMENT '文档名称',
    source_type VARCHAR(20) NOT NULL COMMENT '来源类型：INPUT-手动输入，UPLOAD-文件上传',
    content TEXT COMMENT '原始内容（手动输入时存储）',
    file_url VARCHAR(500) COMMENT '文件URL（上传类型）',
    file_name VARCHAR(200) COMMENT '原始文件名',
    slice_count INT DEFAULT 0 COMMENT '切片数量',
    char_count INT DEFAULT 0 COMMENT '字符数',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标志',
    INDEX idx_knowledge_id (knowledge_id)
) COMMENT 'AI文档表';
```

#### ai_doc_slice（文档切片表）
```sql
CREATE TABLE ai_doc_slice (
    slice_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '切片ID',
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识库ID',
    slice_index INT NOT NULL COMMENT '切片序号',
    content TEXT NOT NULL COMMENT '切片内容',
    char_count INT COMMENT '字符数',
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT '向量化状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_doc_id (doc_id),
    INDEX idx_knowledge_id (knowledge_id)
) COMMENT 'AI文档切片表';
```

#### ai_model（模型配置表）
```sql
CREATE TABLE ai_model (
    model_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型ID',
    model_provider VARCHAR(50) NOT NULL COMMENT '供应商：openai, deepseek, zhipu, ollama等',
    model_type VARCHAR(20) NOT NULL COMMENT '模型类型：LLM-大语言模型，EMBEDDING-嵌入模型',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称（标准名称）',
    model_alias VARCHAR(100) NOT NULL COMMENT '模型别名（调用时使用）',
    api_key VARCHAR(200) NOT NULL COMMENT 'API密钥',
    base_url VARCHAR(200) NOT NULL COMMENT 'API基础地址',
    temperature DECIMAL(2,1) DEFAULT 0.8 COMMENT '温度参数（LLM）',
    top_p DECIMAL(2,1) DEFAULT 0.8 COMMENT 'TopP参数（LLM）',
    ext_param TEXT COMMENT '扩展参数JSON',
    default_flag TINYINT DEFAULT 0 COMMENT '是否默认模型',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标志'
) COMMENT 'AI模型配置表';
```

#### ai_chat_history（聊天历史表）
```sql
CREATE TABLE ai_chat_history (
    msg_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识库ID',
    session_id VARCHAR(64) COMMENT '会话ID（可选，用于多会话）',
    role VARCHAR(20) NOT NULL COMMENT '角色：USER-用户，ASSISTANT-AI',
    content TEXT NOT NULL COMMENT '消息内容',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_create_time (create_time)
) COMMENT 'AI聊天历史表';
```

### 3.2 实体类设计

#### AiKnowledge.java
```java
public class AiKnowledge extends BaseEntity {
    private Long knowledgeId;
    private String knowledgeName;
    private String knowledgeDesc;
    private Integer knowledgeSort;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private Integer delFlag;
}
```

#### AiDoc.java
```java
public class AiDoc extends BaseEntity {
    private Long docId;
    private Long knowledgeId;
    private String docName;
    private String sourceType;  // INPUT, UPLOAD
    private String content;
    private String fileUrl;
    private String fileName;
    private Integer sliceCount;
    private Integer charCount;
    private String status;  // pending, processing, completed, failed
    private String createBy;
    private Date createTime;
    private Date updateTime;
    private Integer delFlag;
}
```

#### AiModel.java
```java
public class AiModel extends BaseEntity {
    private Long modelId;
    private String modelProvider;
    private String modelType;  // LLM, EMBEDDING
    private String modelName;
    private String modelAlias;
    private String apiKey;
    private String baseUrl;
    private BigDecimal temperature;
    private BigDecimal topP;
    private String extParam;
    private Integer defaultFlag;
    private Integer status;
    private String createBy;
    private Date createTime;
    private Date updateTime;
    private Integer delFlag;
}
```

---

## 四、API 设计

### 4.1 知识库管理 `/ai/knowledge`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /pageQuery | 分页查询知识库列表 |
| GET | /list | 查询所有知识库（下拉选择用） |
| GET | /{knowledgeId} | 查询知识库详情 |
| POST | / | 新增知识库 |
| PUT | / | 修改知识库 |
| DELETE | /{knowledgeIds} | 删除知识库 |
| GET | /export | 导出知识库数据 |

### 4.2 文档管理 `/ai/doc`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /pageQuery | 分页查询文档列表 |
| GET | /{docId} | 查询文档详情 |
| POST | / | 新增文档（手动输入） |
| POST | /upload | 上传文档文件 |
| PUT | / | 修改文档 |
| DELETE | /{docIds} | 删除文档 |
| GET | /export | 导出文档数据 |
| GET | /{docId}/slices | 查询文档切片列表 |
| POST | /{docId}/process | 重新处理文档（切片+向量化） |

### 4.3 模型配置 `/ai/model`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /pageQuery | 分页查询模型列表 |
| GET | /{modelId} | 查询模型详情 |
| POST | / | 新增模型配置 |
| PUT | / | 修改模型配置 |
| DELETE | /{modelIds} | 删除模型配置 |
| PUT | /changeStatus | 切换默认模型 |
| GET | /export | 导出模型配置 |

### 4.4 AI 对话 `/ai/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | / | SSE 流式对话 |
| GET | /history/page | 分页查询聊天历史 |
| DELETE | /history/{knowledgeId} | 清空知识库聊天历史 |

### 4.5 请求/响应示例

#### 对话请求
```json
POST /ai/chat
Content-Type: application/json

{
    "knowledgeId": 1,
    "content": "什么是 Spring AI？",
    "chatType": "TEXT",
    "carryContextFlag": true,
    "internetFlag": false
}
```

#### SSE 响应
```
data: {"type":"text","content":"Spring"}
data: {"type":"text","content":" AI"}
data: {"type":"text","content":" 是"}
data: {"type":"text","content":" Spring"}
data: {"type":"text","content":" 官方"}
data: {"type":"text","content":" 提供"}
data: {"type":"text","content":" 的"}
data: {"type":"text","content":" AI"}
data: {"type":"text","content":" 集成"}
data: {"type":"text","content":" 框架"}
data: {"type":"text","content":"。"}
data: {"type":"done","msgId":"123456"}
```

---

## 五、核心服务设计

### 5.1 RAG 服务流程

```
用户提问
    │
    ▼
┌─────────────────┐
│ 1. 构建查询向量  │  Embedding Model
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 2. 向量相似度搜索 │  SimpleVectorStore
│    Filter: knowledgeId
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 3. 构建 Prompt   │  System Prompt + Context + Question
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 4. LLM 生成回答  │  ChatClient.stream()
└────────┬────────┘
         │
         ▼
   SSE 流式输出
```

### 5.2 文档处理流程

```
文档上传/输入
    │
    ▼
┌─────────────────┐
│ 1. 文档解析      │  Tika / POI / 手动输入
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 2. 文本切片      │  TokenTextSplitter
│    chunkSize=500 │
│    overlap=50    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 3. 生成向量      │  Embedding Model
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 4. 存储向量      │  SimpleVectorStore
│    + 持久化文件  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 5. 更新状态      │  MySQL: slice_count, status
└─────────────────┘
```

### 5.3 向量存储设计

使用 Spring AI 的 SimpleVectorStore，按知识库隔离存储：

```
data/vector-store/
├── knowledge_1.json    # 知识库1的向量数据
├── knowledge_2.json    # 知识库2的向量数据
└── ...
```

每个知识库一个 JSON 文件，包含：
- 文档切片内容
- 向量数据（float[]）
- 元数据（docId, sliceIndex 等）

### 5.4 动态模型配置

根据 AiModel 表配置，动态创建 ChatClient 和 EmbeddingModel：

```java
@Service
public class AiModelService {

    private final Map<Long, ChatClient> chatClientCache = new ConcurrentHashMap<>();
    private final Map<Long, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    public ChatClient getChatClient(Long modelId) {
        return chatClientCache.computeIfAbsent(modelId, id -> {
            AiModel model = getById(id);
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(model.getBaseUrl())
                .apiKey(model.getApiKey())
                .modelName(model.getModelAlias())
                .temperature(model.getTemperature())
                .topP(model.getTopP())
                .build();
            return ChatClient.builder(chatModel).build();
        });
    }

    public EmbeddingModel getEmbeddingModel(Long modelId) {
        return embeddingModelCache.computeIfAbsent(modelId, id -> {
            AiModel model = getById(id);
            return new OpenAiEmbeddingModel(
                OpenAiApi.builder()
                    .baseUrl(model.getBaseUrl())
                    .apiKey(model.getApiKey())
                    .build()
            );
        });
    }
}
```

---

## 六、错误处理

### 6.1 异常类型
| 异常 | 场景 | 处理方式 |
|------|------|---------|
| KnowledgeNotFoundException | 知识库不存在 | 返回 404 错误 |
| DocProcessException | 文档处理失败 | 更新状态为 failed，记录错误信息 |
| ModelNotConfiguredException | 模型未配置 | 返回友好提示"AI功能暂未配置" |
| VectorStoreException | 向量存储失败 | 记录日志，返回错误提示 |

### 6.2 优雅降级
- 未配置模型时，接口返回友好提示而非报错
- 文档处理失败时，不影响其他文档
- 向量检索无结果时，LLM 直接回答（不使用 RAG）

---

## 七、安全考虑

### 7.1 权限控制
- 所有 AI 接口需要登录（Sa-Token）
- 知识库、文档按用户隔离（create_by 字段）
- 管理端接口需管理员权限

### 7.2 输入验证
- 文档大小限制（10MB）
- 切片数量限制（单文档最多 1000 切片）
- 对话历史长度限制（最多保留 20 轮）

### 7.3 敏感信息
- API Key 加密存储（Jasypt）
- 日志中不输出 API Key

---

## 八、性能优化

### 8.1 向量检索优化
- 按知识库 ID 过滤，减少搜索范围
- 设置相似度阈值（0.7），过滤不相关内容
- 限制返回切片数量（TopK=5）

### 8.2 缓存策略
- ChatClient 缓存（避免重复创建）
- EmbeddingModel 缓存
- 向量文件懒加载

### 8.3 异步处理
- 文档上传后异步处理切片和向量化
- 使用 @Async + 线程池

---

## 九、测试策略

### 9.1 单元测试
- Service 层核心逻辑测试
- 切片算法测试
- 向量检索测试

### 9.2 集成测试
- 完整 RAG 流程测试
- SSE 流式输出测试
- 多知识库隔离测试

### 9.3 测试数据
- 预置测试知识库和文档
- 模拟对话场景

---

## 十、部署说明

### 10.1 环境要求
- JDK 17+
- MySQL 8.0+
- 磁盘空间：向量文件存储（按文档量估算）

### 10.2 配置项
```yaml
# application.yml
ai:
  vector-store:
    path: data/vector-store    # 向量存储路径
  embedding:
    default-model-id: 1        # 默认嵌入模型ID
  chat:
    default-model-id: 2        # 默认对话模型ID
    max-history: 20            # 最大历史轮数
```

### 10.3 初始化
1. 执行 `twelvet-ai.sql` 创建表结构
2. 配置默认模型（LLM + Embedding）
3. 创建知识库
4. 上传文档

---

## 十一、后续迭代

### 11.1 第二期
- MCP 工具协议支持
- 文档切片预览和编辑
- 批量文档处理

### 11.2 第三期
- TTS 语音播报
- 联网搜索
- 多模态支持（图片理解）

### 11.3 未来考虑
- 切换到专业向量数据库（Milvus/PGVector）
- 知识库权限管理
- 对话分享功能
