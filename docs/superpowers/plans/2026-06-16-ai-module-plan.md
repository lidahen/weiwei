# AI 模块后端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现完整的 AI 模块后端服务，支持 RAG（检索增强生成）功能，包括知识库管理、文档处理、模型配置和 AI 对话。

**Architecture:** 新建 twelvet-ai 模块（api + server），使用 Spring AI + SimpleVectorStore 实现 RAG，按知识库隔离存储向量数据，支持动态模型配置和 SSE 流式对话。

**Tech Stack:** Spring Boot 3.5.4, Spring AI 1.0.0, MyBatis 3.0.3, MySQL 8.0+, SimpleVectorStore

---

## 文件结构

```
twelvet-fast/
├── twelvet-ai/
│   ├── pom.xml
│   ├── twelvet-ai-api/
│   │   ├── pom.xml
│   │   └── src/main/java/com/twelvet/ai/api/domain/
│   │       ├── AiKnowledge.java
│   │       ├── AiDoc.java
│   │       ├── AiDocSlice.java
│   │       ├── AiModel.java
│   │       └── AiChatHistory.java
│   │
│   └── twelvet-ai-server/
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/twelvet/ai/server/
│           │   ├── controller/
│           │   │   ├── AiKnowledgeController.java
│           │   │   ├── AiDocController.java
│           │   │   ├── AiModelController.java
│           │   │   └── AiChatController.java
│           │   ├── service/
│           │   │   ├── IAiKnowledgeService.java
│           │   │   ├── impl/AiKnowledgeServiceImpl.java
│           │   │   ├── IAiDocService.java
│           │   │   ├── impl/AiDocServiceImpl.java
│           │   │   ├── IAiModelService.java
│           │   │   ├── impl/AiModelServiceImpl.java
│           │   │   ├── IAiChatService.java
│           │   │   ├── impl/AiChatServiceImpl.java
│           │   │   └── IAiChatHistoryService.java
│           │   ├── mapper/
│           │   │   ├── AiKnowledgeMapper.java
│           │   │   ├── AiDocMapper.java
│           │   │   ├── AiDocSliceMapper.java
│           │   │   ├── AiModelMapper.java
│           │   │   └── AiChatHistoryMapper.java
│           │   ├── rag/
│           │   │   ├── VectorStoreService.java
│           │   │   └── DocumentProcessService.java
│           │   └── config/
│           │       └── AiConfig.java
│           └── resources/
│               └── mapper/
│                   ├── AiKnowledgeMapper.xml
│                   ├── AiDocMapper.xml
│                   ├── AiDocSliceMapper.xml
│                   ├── AiModelMapper.xml
│                   └── AiChatHistoryMapper.xml
│
├── data/
│   ├── vector-store/                    # 向量存储目录
│   └── twelvet-ai.sql                   # 数据库初始化脚本
│
├── twelvet-admin/
│   └── pom.xml                          # 需添加 twelvet-ai-server 依赖
│
└── pom.xml                              # 需添加 twelvet-ai 模块
```

---

## Task 1: 创建数据库表结构

**Files:**
- Create: `data/twelvet-ai.sql`

- [ ] **Step 1: 创建 SQL 初始化脚本**

```sql
-- ----------------------------
-- AI 模块数据库初始化脚本
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_knowledge
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge`;
CREATE TABLE `ai_knowledge` (
    `knowledge_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识库ID',
    `knowledge_name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `knowledge_desc` VARCHAR(500) COMMENT '知识库描述',
    `knowledge_sort` INT DEFAULT 0 COMMENT '排序',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) COMMENT '更新者',
    `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI知识库表';

-- ----------------------------
-- Table structure for ai_doc
-- ----------------------------
DROP TABLE IF EXISTS `ai_doc`;
CREATE TABLE `ai_doc` (
    `doc_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '知识库ID',
    `doc_name` VARCHAR(200) NOT NULL COMMENT '文档名称',
    `source_type` VARCHAR(20) NOT NULL COMMENT '来源类型：INPUT-手动输入，UPLOAD-文件上传',
    `content` TEXT COMMENT '原始内容（手动输入时存储）',
    `file_url` VARCHAR(500) COMMENT '文件URL（上传类型）',
    `file_name` VARCHAR(200) COMMENT '原始文件名',
    `slice_count` INT DEFAULT 0 COMMENT '切片数量',
    `char_count` INT DEFAULT 0 COMMENT '字符数',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成，failed-失败',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志',
    INDEX `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档表';

-- ----------------------------
-- Table structure for ai_doc_slice
-- ----------------------------
DROP TABLE IF EXISTS `ai_doc_slice`;
CREATE TABLE `ai_doc_slice` (
    `slice_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '切片ID',
    `doc_id` BIGINT NOT NULL COMMENT '文档ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '知识库ID',
    `slice_index` INT NOT NULL COMMENT '切片序号',
    `content` TEXT NOT NULL COMMENT '切片内容',
    `char_count` INT COMMENT '字符数',
    `vector_status` VARCHAR(20) DEFAULT 'pending' COMMENT '向量化状态',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_doc_id` (`doc_id`),
    INDEX `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档切片表';

-- ----------------------------
-- Table structure for ai_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE `ai_model` (
    `model_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型ID',
    `model_provider` VARCHAR(50) NOT NULL COMMENT '供应商：openai, deepseek, zhipu, ollama等',
    `model_type` VARCHAR(20) NOT NULL COMMENT '模型类型：LLM-大语言模型，EMBEDDING-嵌入模型',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称（标准名称）',
    `model_alias` VARCHAR(100) NOT NULL COMMENT '模型别名（调用时使用）',
    `api_key` VARCHAR(200) NOT NULL COMMENT 'API密钥',
    `base_url` VARCHAR(200) NOT NULL COMMENT 'API基础地址',
    `temperature` DECIMAL(2,1) DEFAULT 0.8 COMMENT '温度参数（LLM）',
    `top_p` DECIMAL(2,1) DEFAULT 0.8 COMMENT 'TopP参数（LLM）',
    `ext_param` TEXT COMMENT '扩展参数JSON',
    `default_flag` TINYINT DEFAULT 0 COMMENT '是否默认模型',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- ----------------------------
-- Table structure for ai_chat_history
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_history`;
CREATE TABLE `ai_chat_history` (
    `msg_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '知识库ID',
    `session_id` VARCHAR(64) COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：USER-用户，ASSISTANT-AI',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_knowledge_id` (`knowledge_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天历史表';

-- ----------------------------
-- 初始化字典数据（模型供应商）
-- ----------------------------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('AI模型供应商', 'ai_model_provider', '0', 'admin', NOW(), 'AI模型供应商列表');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES
(1, 'OpenAI', 'openai', 'ai_model_provider', '0', 'admin', NOW()),
(2, 'DeepSeek', 'deepseek', 'ai_model_provider', '0', 'admin', NOW()),
(3, '智谱AI', 'zhipu', 'ai_model_provider', '0', 'admin', NOW()),
(4, 'Ollama', 'ollama', 'ai_model_provider', '0', 'admin', NOW());

-- ----------------------------
-- 初始化字典数据（模型类型）
-- ----------------------------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('AI模型类型', 'ai_model_type', '0', 'admin', NOW(), 'AI模型类型列表');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES
(1, '大语言模型', 'LLM', 'ai_model_type', '0', 'admin', NOW()),
(2, '嵌入模型', 'EMBEDDING', 'ai_model_type', '0', 'admin', NOW());

-- ----------------------------
-- 初始化字典数据（文档来源类型）
-- ----------------------------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES ('AI文档来源类型', 'rag_source_type', '0', 'admin', NOW(), 'AI文档来源类型');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES
(1, '手动输入', 'INPUT', 'rag_source_type', '0', 'admin', NOW()),
(2, '文件上传', 'UPLOAD', 'rag_source_type', '0', 'admin', NOW());

SET FOREIGN_KEY_CHECKS = 1;
```

- [ ] **Step 2: 创建向量存储目录**

```bash
mkdir -p data/vector-store
```

- [ ] **Step 3: Commit**

```bash
git add data/twelvet-ai.sql
git commit -m "feat(ai): add AI module database schema"
```

---

## Task 2: 创建 twelvet-ai 父模块

**Files:**
- Create: `twelvet-fast/twelvet-ai/pom.xml`
- Modify: `twelvet-fast/pom.xml`（添加模块）

- [ ] **Step 1: 在父 pom.xml 中添加模块**

编辑 `twelvet-fast/pom.xml`，在 `<modules>` 中添加：

```xml
<module>twelvet-ai</module>
```

- [ ] **Step 2: 创建 twelvet-ai 父模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>twelvet-fast</artifactId>
        <groupId>com.twelvet</groupId>
        <version>3.5.4</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>twelvet-ai</artifactId>
    <packaging>pom</packaging>

    <description>AI 模块 - RAG 知识库与智能对话</description>

    <modules>
        <module>twelvet-ai-api</module>
        <module>twelvet-ai-server</module>
    </modules>

</project>
```

- [ ] **Step 3: Commit**

```bash
git add twelvet-fast/pom.xml twelvet-fast/twelvet-ai/pom.xml
git commit -m "feat(ai): add twelvet-ai parent module"
```

---

## Task 3: 创建 twelvet-ai-api 模块

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/pom.xml`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiKnowledge.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiDoc.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiDocSlice.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiModel.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiChatHistory.java`

- [ ] **Step 1: 创建 twelvet-ai-api pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>twelvet-ai</artifactId>
        <groupId>com.twelvet</groupId>
        <version>3.5.4</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>twelvet-ai-api</artifactId>

    <description>AI 模块 API - 实体类定义</description>

    <dependencies>
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-core</artifactId>
        </dependency>
    </dependencies>

</project>
```

- [ ] **Step 2: 创建 AiKnowledge 实体类**

```java
package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI知识库实体
 */
@Schema(description = "AI知识库")
public class AiKnowledge extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @Schema(description = "知识库名称")
    private String knowledgeName;

    @Schema(description = "知识库描述")
    private String knowledgeDesc;

    @Schema(description = "排序")
    private Integer knowledgeSort;

    @Schema(description = "删除标志")
    private Integer delFlag;

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getKnowledgeName() {
        return knowledgeName;
    }

    public void setKnowledgeName(String knowledgeName) {
        this.knowledgeName = knowledgeName;
    }

    public String getKnowledgeDesc() {
        return knowledgeDesc;
    }

    public void setKnowledgeDesc(String knowledgeDesc) {
        this.knowledgeDesc = knowledgeDesc;
    }

    public Integer getKnowledgeSort() {
        return knowledgeSort;
    }

    public void setKnowledgeSort(Integer knowledgeSort) {
        this.knowledgeSort = knowledgeSort;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
```

- [ ] **Step 3: 创建 AiDoc 实体类**

```java
package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI文档实体
 */
@Schema(description = "AI文档")
public class AiDoc extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文档ID")
    private Long docId;

    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @Schema(description = "文档名称")
    private String docName;

    @Schema(description = "来源类型：INPUT-手动输入，UPLOAD-文件上传")
    private String sourceType;

    @Schema(description = "原始内容")
    private String content;

    @Schema(description = "文件URL")
    private String fileUrl;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "切片数量")
    private Integer sliceCount;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "状态：pending-待处理，processing-处理中，completed-已完成，failed-失败")
    private String status;

    @Schema(description = "删除标志")
    private Integer delFlag;

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getSliceCount() {
        return sliceCount;
    }

    public void setSliceCount(Integer sliceCount) {
        this.sliceCount = sliceCount;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
```

- [ ] **Step 4: 创建 AiDocSlice 实体类**

```java
package com.twelvet.ai.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI文档切片实体
 */
@Schema(description = "AI文档切片")
public class AiDocSlice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "切片ID")
    private Long sliceId;

    @Schema(description = "文档ID")
    private Long docId;

    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @Schema(description = "切片序号")
    private Integer sliceIndex;

    @Schema(description = "切片内容")
    private String content;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "向量化状态")
    private String vectorStatus;

    @Schema(description = "创建时间")
    private Date createTime;

    public Long getSliceId() {
        return sliceId;
    }

    public void setSliceId(Long sliceId) {
        this.sliceId = sliceId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public Integer getSliceIndex() {
        return sliceIndex;
    }

    public void setSliceIndex(Integer sliceIndex) {
        this.sliceIndex = sliceIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public String getVectorStatus() {
        return vectorStatus;
    }

    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
```

- [ ] **Step 5: 创建 AiModel 实体类**

```java
package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI模型配置实体
 */
@Schema(description = "AI模型配置")
public class AiModel extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模型ID")
    private Long modelId;

    @Schema(description = "供应商：openai, deepseek, zhipu, ollama等")
    private String modelProvider;

    @Schema(description = "模型类型：LLM-大语言模型，EMBEDDING-嵌入模型")
    private String modelType;

    @Schema(description = "模型名称（标准名称）")
    private String modelName;

    @Schema(description = "模型别名（调用时使用）")
    private String modelAlias;

    @Schema(description = "API密钥")
    private String apiKey;

    @Schema(description = "API基础地址")
    private String baseUrl;

    @Schema(description = "温度参数")
    private BigDecimal temperature;

    @Schema(description = "TopP参数")
    private BigDecimal topP;

    @Schema(description = "扩展参数JSON")
    private String extParam;

    @Schema(description = "是否默认模型")
    private Integer defaultFlag;

    @Schema(description = "状态：0-停用，1-启用")
    private Integer status;

    @Schema(description = "删除标志")
    private Integer delFlag;

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelAlias() {
        return modelAlias;
    }

    public void setModelAlias(String modelAlias) {
        this.modelAlias = modelAlias;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getTopP() {
        return topP;
    }

    public void setTopP(BigDecimal topP) {
        this.topP = topP;
    }

    public String getExtParam() {
        return extParam;
    }

    public void setExtParam(String extParam) {
        this.extParam = extParam;
    }

    public Integer getDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(Integer defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
```

- [ ] **Step 6: 创建 AiChatHistory 实体类**

```java
package com.twelvet.ai.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI聊天历史实体
 */
@Schema(description = "AI聊天历史")
public class AiChatHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息ID")
    private Long msgId;

    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "角色：USER-用户，ASSISTANT-AI")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "创建者")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    public Long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgId) {
        this.msgId = msgId;
    }

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-api/
git commit -m "feat(ai): add twelvet-ai-api module with entities"
```

---

## Task 4: 创建 twelvet-ai-server 模块基础结构

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/pom.xml`
- Modify: `twelvet-fast/pom.xml`（添加 ai 模块版本管理）
- Modify: `twelvet-fast/twelvet-admin/pom.xml`（添加依赖）

- [ ] **Step 1: 在父 pom.xml 中添加 ai 模块版本管理**

编辑 `twelvet-fast/pom.xml`，在 `<dependencyManagement>` 中添加：

```xml
<!-- Fast ai API -->
<dependency>
    <groupId>com.twelvet</groupId>
    <artifactId>twelvet-ai-api</artifactId>
    <version>${twelvet.version}</version>
</dependency>

<!-- Fast ai Server -->
<dependency>
    <groupId>com.twelvet</groupId>
    <artifactId>twelvet-ai-server</artifactId>
    <version>${twelvet.version}</version>
</dependency>
```

- [ ] **Step 2: 创建 twelvet-ai-server pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>twelvet-ai</artifactId>
        <groupId>com.twelvet</groupId>
        <version>3.5.4</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>twelvet-ai-server</artifactId>

    <description>AI 模块服务实现 - RAG 知识库与智能对话</description>

    <properties>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- twelvet-ai-api -->
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-ai-api</artifactId>
        </dependency>

        <!-- framework -->
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-datasource</artifactId>
        </dependency>
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.twelvet</groupId>
            <artifactId>twelvet-framework-log</artifactId>
        </dependency>

        <!-- Spring AI -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>

    </dependencies>

</project>
```

- [ ] **Step 3: 在 twelvet-admin 中添加 ai-server 依赖**

编辑 `twelvet-fast/twelvet-admin/pom.xml`，添加：

```xml
<!-- twelvet-ai-server -->
<dependency>
    <groupId>com.twelvet</groupId>
    <artifactId>twelvet-ai-server</artifactId>
</dependency>
```

- [ ] **Step 4: Commit**

```bash
git add twelvet-fast/pom.xml twelvet-fast/twelvet-ai/twelvet-ai-server/pom.xml twelvet-fast/twelvet-admin/pom.xml
git commit -m "feat(ai): add twelvet-ai-server module configuration"
```

---

## Task 5: 创建 Mapper 接口和 XML

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiKnowledgeMapper.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiDocMapper.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiDocSliceMapper.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiModelMapper.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiChatHistoryMapper.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/resources/mapper/AiKnowledgeMapper.xml`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/resources/mapper/AiDocMapper.xml`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/resources/mapper/AiDocSliceMapper.xml`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/resources/mapper/AiModelMapper.xml`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/resources/mapper/AiChatHistoryMapper.xml`

- [ ] **Step 1: 创建 AiKnowledgeMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI知识库Mapper
 */
@Mapper
public interface AiKnowledgeMapper {

    /**
     * 查询知识库
     */
    AiKnowledge selectAiKnowledgeById(Long knowledgeId);

    /**
     * 查询知识库列表
     */
    List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge);

    /**
     * 查询所有知识库（下拉选择用）
     */
    List<AiKnowledge> selectAiKnowledgeAll();

    /**
     * 新增知识库
     */
    int insertAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 修改知识库
     */
    int updateAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 删除知识库
     */
    int deleteAiKnowledgeById(Long knowledgeId);

    /**
     * 批量删除知识库
     */
    int deleteAiKnowledgeByIds(Long[] knowledgeIds);
}
```

- [ ] **Step 2: 创建 AiDocMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiDoc;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI文档Mapper
 */
@Mapper
public interface AiDocMapper {

    /**
     * 查询文档
     */
    AiDoc selectAiDocById(Long docId);

    /**
     * 查询文档列表
     */
    List<AiDoc> selectAiDocList(AiDoc aiDoc);

    /**
     * 新增文档
     */
    int insertAiDoc(AiDoc aiDoc);

    /**
     * 修改文档
     */
    int updateAiDoc(AiDoc aiDoc);

    /**
     * 删除文档
     */
    int deleteAiDocById(Long docId);

    /**
     * 批量删除文档
     */
    int deleteAiDocByIds(Long[] docIds);

    /**
     * 根据知识库ID删除文档
     */
    int deleteAiDocByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 3: 创建 AiDocSliceMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiDocSlice;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI文档切片Mapper
 */
@Mapper
public interface AiDocSliceMapper {

    /**
     * 查询切片
     */
    AiDocSlice selectAiDocSliceById(Long sliceId);

    /**
     * 查询文档的切片列表
     */
    List<AiDocSlice> selectAiDocSliceByDocId(Long docId);

    /**
     * 查询知识库的所有切片
     */
    List<AiDocSlice> selectAiDocSliceByKnowledgeId(Long knowledgeId);

    /**
     * 批量插入切片
     */
    int batchInsertAiDocSlice(List<AiDocSlice> list);

    /**
     * 删除文档的所有切片
     */
    int deleteAiDocSliceByDocId(Long docId);

    /**
     * 删除知识库的所有切片
     */
    int deleteAiDocSliceByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 4: 创建 AiModelMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiModel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI模型配置Mapper
 */
@Mapper
public interface AiModelMapper {

    /**
     * 查询模型配置
     */
    AiModel selectAiModelById(Long modelId);

    /**
     * 查询模型配置列表
     */
    List<AiModel> selectAiModelList(AiModel aiModel);

    /**
     * 查询默认LLM模型
     */
    AiModel selectDefaultLLMModel();

    /**
     * 查询默认Embedding模型
     */
    AiModel selectDefaultEmbeddingModel();

    /**
     * 新增模型配置
     */
    int insertAiModel(AiModel aiModel);

    /**
     * 修改模型配置
     */
    int updateAiModel(AiModel aiModel);

    /**
     * 删除模型配置
     */
    int deleteAiModelById(Long modelId);

    /**
     * 批量删除模型配置
     */
    int deleteAiModelByIds(Long[] modelIds);

    /**
     * 清除同类型默认标记
     */
    int clearDefaultFlagByType(String modelType);
}
```

- [ ] **Step 5: 创建 AiChatHistoryMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiChatHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI聊天历史Mapper
 */
@Mapper
public interface AiChatHistoryMapper {

    /**
     * 查询聊天历史
     */
    AiChatHistory selectAiChatHistoryById(Long msgId);

    /**
     * 查询聊天历史列表
     */
    List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory);

    /**
     * 查询知识库最近的聊天历史
     */
    List<AiChatHistory> selectRecentHistoryByKnowledgeId(Long knowledgeId, Integer limit);

    /**
     * 新增聊天历史
     */
    int insertAiChatHistory(AiChatHistory aiChatHistory);

    /**
     * 删除知识库的聊天历史
     */
    int deleteAiChatHistoryByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 6: 创建 AiKnowledgeMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiKnowledgeMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiKnowledge" id="AiKnowledgeResult">
        <result property="knowledgeId" column="knowledge_id"/>
        <result property="knowledgeName" column="knowledge_name"/>
        <result property="knowledgeDesc" column="knowledge_desc"/>
        <result property="knowledgeSort" column="knowledge_sort"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateBy" column="update_by"/>
        <result property="updateTime" column="update_time"/>
        <result property="delFlag" column="del_flag"/>
    </resultMap>

    <sql id="selectAiKnowledgeVo">
        select knowledge_id, knowledge_name, knowledge_desc, knowledge_sort, create_by, create_time, update_by, update_time, del_flag
        from ai_knowledge
    </sql>

    <select id="selectAiKnowledgeById" parameterType="Long" resultMap="AiKnowledgeResult">
        <include refid="selectAiKnowledgeVo"/>
        where knowledge_id = #{knowledgeId} and del_flag = 0
    </select>

    <select id="selectAiKnowledgeList" parameterType="com.twelvet.ai.api.domain.AiKnowledge" resultMap="AiKnowledgeResult">
        <include refid="selectAiKnowledgeVo"/>
        <where>
            del_flag = 0
            <if test="knowledgeName != null and knowledgeName != ''">
                AND knowledge_name like concat('%', #{knowledgeName}, '%')
            </if>
        </where>
        order by knowledge_sort, create_time desc
    </select>

    <select id="selectAiKnowledgeAll" resultMap="AiKnowledgeResult">
        <include refid="selectAiKnowledgeVo"/>
        where del_flag = 0
        order by knowledge_sort, create_time desc
    </select>

    <insert id="insertAiKnowledge" parameterType="com.twelvet.ai.api.domain.AiKnowledge" useGeneratedKeys="true" keyProperty="knowledgeId">
        insert into ai_knowledge
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="knowledgeName != null and knowledgeName != ''">knowledge_name,</if>
            <if test="knowledgeDesc != null">knowledge_desc,</if>
            <if test="knowledgeSort != null">knowledge_sort,</if>
            <if test="createBy != null">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="knowledgeName != null and knowledgeName != ''">#{knowledgeName},</if>
            <if test="knowledgeDesc != null">#{knowledgeDesc},</if>
            <if test="knowledgeSort != null">#{knowledgeSort},</if>
            <if test="createBy != null">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <update id="updateAiKnowledge" parameterType="com.twelvet.ai.api.domain.AiKnowledge">
        update ai_knowledge
        <trim prefix="SET" suffixOverrides=",">
            <if test="knowledgeName != null and knowledgeName != ''">knowledge_name = #{knowledgeName},</if>
            <if test="knowledgeDesc != null">knowledge_desc = #{knowledgeDesc},</if>
            <if test="knowledgeSort != null">knowledge_sort = #{knowledgeSort},</if>
            <if test="updateBy != null">update_by = #{updateBy},</if>
            update_time = sysdate()
        </trim>
        where knowledge_id = #{knowledgeId}
    </update>

    <update id="deleteAiKnowledgeById" parameterType="Long">
        update ai_knowledge set del_flag = 1 where knowledge_id = #{knowledgeId}
    </update>

    <update id="deleteAiKnowledgeByIds" parameterType="Long">
        update ai_knowledge set del_flag = 1 where knowledge_id in
        <foreach item="knowledgeId" collection="array" open="(" separator="," close=")">
            #{knowledgeId}
        </foreach>
    </update>

</mapper>
```

- [ ] **Step 7: 创建 AiDocMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiDocMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiDoc" id="AiDocResult">
        <result property="docId" column="doc_id"/>
        <result property="knowledgeId" column="knowledge_id"/>
        <result property="docName" column="doc_name"/>
        <result property="sourceType" column="source_type"/>
        <result property="content" column="content"/>
        <result property="fileUrl" column="file_url"/>
        <result property="fileName" column="file_name"/>
        <result property="sliceCount" column="slice_count"/>
        <result property="charCount" column="char_count"/>
        <result property="status" column="status"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateTime" column="update_time"/>
        <result property="delFlag" column="del_flag"/>
    </resultMap>

    <sql id="selectAiDocVo">
        select doc_id, knowledge_id, doc_name, source_type, content, file_url, file_name, slice_count, char_count, status, create_by, create_time, update_time, del_flag
        from ai_doc
    </sql>

    <select id="selectAiDocById" parameterType="Long" resultMap="AiDocResult">
        <include refid="selectAiDocVo"/>
        where doc_id = #{docId} and del_flag = 0
    </select>

    <select id="selectAiDocList" parameterType="com.twelvet.ai.api.domain.AiDoc" resultMap="AiDocResult">
        <include refid="selectAiDocVo"/>
        <where>
            del_flag = 0
            <if test="knowledgeId != null">
                AND knowledge_id = #{knowledgeId}
            </if>
            <if test="docName != null and docName != ''">
                AND doc_name like concat('%', #{docName}, '%')
            </if>
            <if test="sourceType != null and sourceType != ''">
                AND source_type = #{sourceType}
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
        </where>
        order by create_time desc
    </select>

    <insert id="insertAiDoc" parameterType="com.twelvet.ai.api.domain.AiDoc" useGeneratedKeys="true" keyProperty="docId">
        insert into ai_doc
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="knowledgeId != null">knowledge_id,</if>
            <if test="docName != null and docName != ''">doc_name,</if>
            <if test="sourceType != null and sourceType != ''">source_type,</if>
            <if test="content != null">content,</if>
            <if test="fileUrl != null">file_url,</if>
            <if test="fileName != null">file_name,</if>
            <if test="sliceCount != null">slice_count,</if>
            <if test="charCount != null">char_count,</if>
            <if test="status != null">status,</if>
            <if test="createBy != null">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="knowledgeId != null">#{knowledgeId},</if>
            <if test="docName != null and docName != ''">#{docName},</if>
            <if test="sourceType != null and sourceType != ''">#{sourceType},</if>
            <if test="content != null">#{content},</if>
            <if test="fileUrl != null">#{fileUrl},</if>
            <if test="fileName != null">#{fileName},</if>
            <if test="sliceCount != null">#{sliceCount},</if>
            <if test="charCount != null">#{charCount},</if>
            <if test="status != null">#{status},</if>
            <if test="createBy != null">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <update id="updateAiDoc" parameterType="com.twelvet.ai.api.domain.AiDoc">
        update ai_doc
        <trim prefix="SET" suffixOverrides=",">
            <if test="knowledgeId != null">knowledge_id = #{knowledgeId},</if>
            <if test="docName != null and docName != ''">doc_name = #{docName},</if>
            <if test="sourceType != null and sourceType != ''">source_type = #{sourceType},</if>
            <if test="content != null">content = #{content},</if>
            <if test="fileUrl != null">file_url = #{fileUrl},</if>
            <if test="fileName != null">file_name = #{fileName},</if>
            <if test="sliceCount != null">slice_count = #{sliceCount},</if>
            <if test="charCount != null">char_count = #{charCount},</if>
            <if test="status != null">status = #{status},</if>
            update_time = sysdate()
        </trim>
        where doc_id = #{docId}
    </update>

    <update id="deleteAiDocById" parameterType="Long">
        update ai_doc set del_flag = 1 where doc_id = #{docId}
    </update>

    <update id="deleteAiDocByIds" parameterType="Long">
        update ai_doc set del_flag = 1 where doc_id in
        <foreach item="docId" collection="array" open="(" separator="," close=")">
            #{docId}
        </foreach>
    </update>

    <delete id="deleteAiDocByKnowledgeId" parameterType="Long">
        update ai_doc set del_flag = 1 where knowledge_id = #{knowledgeId}
    </delete>

</mapper>
```

- [ ] **Step 8: 创建 AiDocSliceMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiDocSliceMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiDocSlice" id="AiDocSliceResult">
        <result property="sliceId" column="slice_id"/>
        <result property="docId" column="doc_id"/>
        <result property="knowledgeId" column="knowledge_id"/>
        <result property="sliceIndex" column="slice_index"/>
        <result property="content" column="content"/>
        <result property="charCount" column="char_count"/>
        <result property="vectorStatus" column="vector_status"/>
        <result property="createTime" column="create_time"/>
    </resultMap>

    <sql id="selectAiDocSliceVo">
        select slice_id, doc_id, knowledge_id, slice_index, content, char_count, vector_status, create_time
        from ai_doc_slice
    </sql>

    <select id="selectAiDocSliceById" parameterType="Long" resultMap="AiDocSliceResult">
        <include refid="selectAiDocSliceVo"/>
        where slice_id = #{sliceId}
    </select>

    <select id="selectAiDocSliceByDocId" parameterType="Long" resultMap="AiDocSliceResult">
        <include refid="selectAiDocSliceVo"/>
        where doc_id = #{docId}
        order by slice_index
    </select>

    <select id="selectAiDocSliceByKnowledgeId" parameterType="Long" resultMap="AiDocSliceResult">
        <include refid="selectAiDocSliceVo"/>
        where knowledge_id = #{knowledgeId}
        order by doc_id, slice_index
    </select>

    <insert id="batchInsertAiDocSlice" parameterType="java.util.List">
        insert into ai_doc_slice (doc_id, knowledge_id, slice_index, content, char_count, vector_status, create_time)
        values
        <foreach collection="list" item="item" separator=",">
            (#{item.docId}, #{item.knowledgeId}, #{item.sliceIndex}, #{item.content}, #{item.charCount}, #{item.vectorStatus}, sysdate())
        </foreach>
    </insert>

    <delete id="deleteAiDocSliceByDocId" parameterType="Long">
        delete from ai_doc_slice where doc_id = #{docId}
    </delete>

    <delete id="deleteAiDocSliceByKnowledgeId" parameterType="Long">
        delete from ai_doc_slice where knowledge_id = #{knowledgeId}
    </delete>

</mapper>
```

- [ ] **Step 9: 创建 AiModelMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiModelMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiModel" id="AiModelResult">
        <result property="modelId" column="model_id"/>
        <result property="modelProvider" column="model_provider"/>
        <result property="modelType" column="model_type"/>
        <result property="modelName" column="model_name"/>
        <result property="modelAlias" column="model_alias"/>
        <result property="apiKey" column="api_key"/>
        <result property="baseUrl" column="base_url"/>
        <result property="temperature" column="temperature"/>
        <result property="topP" column="top_p"/>
        <result property="extParam" column="ext_param"/>
        <result property="defaultFlag" column="default_flag"/>
        <result property="status" column="status"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateTime" column="update_time"/>
        <result property="delFlag" column="del_flag"/>
    </resultMap>

    <sql id="selectAiModelVo">
        select model_id, model_provider, model_type, model_name, model_alias, api_key, base_url, temperature, top_p, ext_param, default_flag, status, create_by, create_time, update_time, del_flag
        from ai_model
    </sql>

    <select id="selectAiModelById" parameterType="Long" resultMap="AiModelResult">
        <include refid="selectAiModelVo"/>
        where model_id = #{modelId} and del_flag = 0
    </select>

    <select id="selectAiModelList" parameterType="com.twelvet.ai.api.domain.AiModel" resultMap="AiModelResult">
        <include refid="selectAiModelVo"/>
        <where>
            del_flag = 0
            <if test="modelProvider != null and modelProvider != ''">
                AND model_provider = #{modelProvider}
            </if>
            <if test="modelType != null and modelType != ''">
                AND model_type = #{modelType}
            </if>
            <if test="modelName != null and modelName != ''">
                AND model_name like concat('%', #{modelName}, '%')
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
        </where>
        order by default_flag desc, create_time desc
    </select>

    <select id="selectDefaultLLMModel" resultMap="AiModelResult">
        <include refid="selectAiModelVo"/>
        where del_flag = 0 and status = 1 and model_type = 'LLM' and default_flag = 1
        limit 1
    </select>

    <select id="selectDefaultEmbeddingModel" resultMap="AiModelResult">
        <include refid="selectAiModelVo"/>
        where del_flag = 0 and status = 1 and model_type = 'EMBEDDING' and default_flag = 1
        limit 1
    </select>

    <insert id="insertAiModel" parameterType="com.twelvet.ai.api.domain.AiModel" useGeneratedKeys="true" keyProperty="modelId">
        insert into ai_model
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="modelProvider != null and modelProvider != ''">model_provider,</if>
            <if test="modelType != null and modelType != ''">model_type,</if>
            <if test="modelName != null and modelName != ''">model_name,</if>
            <if test="modelAlias != null and modelAlias != ''">model_alias,</if>
            <if test="apiKey != null and apiKey != ''">api_key,</if>
            <if test="baseUrl != null and baseUrl != ''">base_url,</if>
            <if test="temperature != null">temperature,</if>
            <if test="topP != null">top_p,</if>
            <if test="extParam != null">ext_param,</if>
            <if test="defaultFlag != null">default_flag,</if>
            <if test="status != null">status,</if>
            <if test="createBy != null">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="modelProvider != null and modelProvider != ''">#{modelProvider},</if>
            <if test="modelType != null and modelType != ''">#{modelType},</if>
            <if test="modelName != null and modelName != ''">#{modelName},</if>
            <if test="modelAlias != null and modelAlias != ''">#{modelAlias},</if>
            <if test="apiKey != null and apiKey != ''">#{apiKey},</if>
            <if test="baseUrl != null and baseUrl != ''">#{baseUrl},</if>
            <if test="temperature != null">#{temperature},</if>
            <if test="topP != null">#{topP},</if>
            <if test="extParam != null">#{extParam},</if>
            <if test="defaultFlag != null">#{defaultFlag},</if>
            <if test="status != null">#{status},</if>
            <if test="createBy != null">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <update id="updateAiModel" parameterType="com.twelvet.ai.api.domain.AiModel">
        update ai_model
        <trim prefix="SET" suffixOverrides=",">
            <if test="modelProvider != null and modelProvider != ''">model_provider = #{modelProvider},</if>
            <if test="modelType != null and modelType != ''">model_type = #{modelType},</if>
            <if test="modelName != null and modelName != ''">model_name = #{modelName},</if>
            <if test="modelAlias != null and modelAlias != ''">model_alias = #{modelAlias},</if>
            <if test="apiKey != null and apiKey != ''">api_key = #{apiKey},</if>
            <if test="baseUrl != null and baseUrl != ''">base_url = #{baseUrl},</if>
            <if test="temperature != null">temperature = #{temperature},</if>
            <if test="topP != null">top_p = #{topP},</if>
            <if test="extParam != null">ext_param = #{extParam},</if>
            <if test="defaultFlag != null">default_flag = #{defaultFlag},</if>
            <if test="status != null">status = #{status},</if>
            update_time = sysdate()
        </trim>
        where model_id = #{modelId}
    </update>

    <update id="deleteAiModelById" parameterType="Long">
        update ai_model set del_flag = 1 where model_id = #{modelId}
    </update>

    <update id="deleteAiModelByIds" parameterType="Long">
        update ai_model set del_flag = 1 where model_id in
        <foreach item="modelId" collection="array" open="(" separator="," close=")">
            #{modelId}
        </foreach>
    </update>

    <update id="clearDefaultFlagByType" parameterType="String">
        update ai_model set default_flag = 0 where model_type = #{modelType} and del_flag = 0
    </update>

</mapper>
```

- [ ] **Step 10: 创建 AiChatHistoryMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiChatHistoryMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiChatHistory" id="AiChatHistoryResult">
        <result property="msgId" column="msg_id"/>
        <result property="knowledgeId" column="knowledge_id"/>
        <result property="sessionId" column="session_id"/>
        <result property="role" column="role"/>
        <result property="content" column="content"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
    </resultMap>

    <sql id="selectAiChatHistoryVo">
        select msg_id, knowledge_id, session_id, role, content, create_by, create_time
        from ai_chat_history
    </sql>

    <select id="selectAiChatHistoryById" parameterType="Long" resultMap="AiChatHistoryResult">
        <include refid="selectAiChatHistoryVo"/>
        where msg_id = #{msgId}
    </select>

    <select id="selectAiChatHistoryList" parameterType="com.twelvet.ai.api.domain.AiChatHistory" resultMap="AiChatHistoryResult">
        <include refid="selectAiChatHistoryVo"/>
        <where>
            <if test="knowledgeId != null">
                AND knowledge_id = #{knowledgeId}
            </if>
            <if test="sessionId != null and sessionId != ''">
                AND session_id = #{sessionId}
            </if>
            <if test="role != null and role != ''">
                AND role = #{role}
            </if>
        </where>
        order by create_time desc
    </select>

    <select id="selectRecentHistoryByKnowledgeId" resultMap="AiChatHistoryResult">
        <include refid="selectAiChatHistoryVo"/>
        where knowledge_id = #{knowledgeId}
        order by create_time desc
        limit #{limit}
    </select>

    <insert id="insertAiChatHistory" parameterType="com.twelvet.ai.api.domain.AiChatHistory" useGeneratedKeys="true" keyProperty="msgId">
        insert into ai_chat_history
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="knowledgeId != null">knowledge_id,</if>
            <if test="sessionId != null">session_id,</if>
            <if test="role != null and role != ''">role,</if>
            <if test="content != null">content,</if>
            <if test="createBy != null">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="knowledgeId != null">#{knowledgeId},</if>
            <if test="sessionId != null">#{sessionId},</if>
            <if test="role != null and role != ''">#{role},</if>
            <if test="content != null">#{content},</if>
            <if test="createBy != null">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <delete id="deleteAiChatHistoryByKnowledgeId" parameterType="Long">
        delete from ai_chat_history where knowledge_id = #{knowledgeId}
    </delete>

</mapper>
```

- [ ] **Step 11: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/
git commit -m "feat(ai): add mapper interfaces and XML files"
```

---

## Task 6: 创建 Service 接口和实现类

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiKnowledgeService.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiKnowledgeServiceImpl.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiDocService.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiDocServiceImpl.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiModelService.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiModelServiceImpl.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiChatHistoryService.java`

- [ ] **Step 1: 创建 IAiKnowledgeService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiKnowledge;

import java.util.List;

/**
 * AI知识库Service接口
 */
public interface IAiKnowledgeService {

    /**
     * 查询知识库
     */
    AiKnowledge selectAiKnowledgeById(Long knowledgeId);

    /**
     * 查询知识库列表
     */
    List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge);

    /**
     * 查询所有知识库
     */
    List<AiKnowledge> selectAiKnowledgeAll();

    /**
     * 新增知识库
     */
    int insertAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 修改知识库
     */
    int updateAiKnowledge(AiKnowledge aiKnowledge);

    /**
     * 删除知识库
     */
    int deleteAiKnowledgeById(Long knowledgeId);

    /**
     * 批量删除知识库
     */
    int deleteAiKnowledgeByIds(Long[] knowledgeIds);
}
```

- [ ] **Step 2: 创建 AiKnowledgeServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiKnowledge;
import com.twelvet.ai.server.mapper.AiDocMapper;
import com.twelvet.ai.server.mapper.AiDocSliceMapper;
import com.twelvet.ai.server.mapper.AiKnowledgeMapper;
import com.twelvet.ai.server.rag.VectorStoreService;
import com.twelvet.ai.server.service.IAiKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI知识库Service实现
 */
@Service
public class AiKnowledgeServiceImpl implements IAiKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeServiceImpl.class);

    private final AiKnowledgeMapper aiKnowledgeMapper;
    private final AiDocMapper aiDocMapper;
    private final AiDocSliceMapper aiDocSliceMapper;
    private final VectorStoreService vectorStoreService;

    public AiKnowledgeServiceImpl(AiKnowledgeMapper aiKnowledgeMapper, AiDocMapper aiDocMapper,
                                   AiDocSliceMapper aiDocSliceMapper, VectorStoreService vectorStoreService) {
        this.aiKnowledgeMapper = aiKnowledgeMapper;
        this.aiDocMapper = aiDocMapper;
        this.aiDocSliceMapper = aiDocSliceMapper;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public AiKnowledge selectAiKnowledgeById(Long knowledgeId) {
        return aiKnowledgeMapper.selectAiKnowledgeById(knowledgeId);
    }

    @Override
    public List<AiKnowledge> selectAiKnowledgeList(AiKnowledge aiKnowledge) {
        return aiKnowledgeMapper.selectAiKnowledgeList(aiKnowledge);
    }

    @Override
    public List<AiKnowledge> selectAiKnowledgeAll() {
        return aiKnowledgeMapper.selectAiKnowledgeAll();
    }

    @Override
    public int insertAiKnowledge(AiKnowledge aiKnowledge) {
        return aiKnowledgeMapper.insertAiKnowledge(aiKnowledge);
    }

    @Override
    public int updateAiKnowledge(AiKnowledge aiKnowledge) {
        return aiKnowledgeMapper.updateAiKnowledge(aiKnowledge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiKnowledgeById(Long knowledgeId) {
        // 删除向量数据
        vectorStoreService.deleteByKnowledgeId(knowledgeId);
        // 删除切片
        aiDocSliceMapper.deleteAiDocSliceByKnowledgeId(knowledgeId);
        // 删除文档
        aiDocMapper.deleteAiDocByKnowledgeId(knowledgeId);
        // 删除知识库
        return aiKnowledgeMapper.deleteAiKnowledgeById(knowledgeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiKnowledgeByIds(Long[] knowledgeIds) {
        for (Long knowledgeId : knowledgeIds) {
            deleteAiKnowledgeById(knowledgeId);
        }
        return knowledgeIds.length;
    }
}
```

- [ ] **Step 3: 创建 IAiDocService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;

import java.util.List;

/**
 * AI文档Service接口
 */
public interface IAiDocService {

    /**
     * 查询文档
     */
    AiDoc selectAiDocById(Long docId);

    /**
     * 查询文档列表
     */
    List<AiDoc> selectAiDocList(AiDoc aiDoc);

    /**
     * 新增文档（手动输入）
     */
    int insertAiDoc(AiDoc aiDoc);

    /**
     * 修改文档
     */
    int updateAiDoc(AiDoc aiDoc);

    /**
     * 删除文档
     */
    int deleteAiDocById(Long docId);

    /**
     * 批量删除文档
     */
    int deleteAiDocByIds(Long[] docIds);

    /**
     * 查询文档切片列表
     */
    List<AiDocSlice> selectDocSlices(Long docId);

    /**
     * 处理文档（切片+向量化）
     */
    void processDoc(Long docId);
}
```

- [ ] **Step 4: 创建 AiDocServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;
import com.twelvet.ai.server.mapper.AiDocMapper;
import com.twelvet.ai.server.mapper.AiDocSliceMapper;
import com.twelvet.ai.server.rag.DocumentProcessService;
import com.twelvet.ai.server.rag.VectorStoreService;
import com.twelvet.ai.server.service.IAiDocService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI文档Service实现
 */
@Service
public class AiDocServiceImpl implements IAiDocService {

    private static final Logger log = LoggerFactory.getLogger(AiDocServiceImpl.class);

    private final AiDocMapper aiDocMapper;
    private final AiDocSliceMapper aiDocSliceMapper;
    private final DocumentProcessService documentProcessService;
    private final VectorStoreService vectorStoreService;

    public AiDocServiceImpl(AiDocMapper aiDocMapper, AiDocSliceMapper aiDocSliceMapper,
                            DocumentProcessService documentProcessService, VectorStoreService vectorStoreService) {
        this.aiDocMapper = aiDocMapper;
        this.aiDocSliceMapper = aiDocSliceMapper;
        this.documentProcessService = documentProcessService;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public AiDoc selectAiDocById(Long docId) {
        return aiDocMapper.selectAiDocById(docId);
    }

    @Override
    public List<AiDoc> selectAiDocList(AiDoc aiDoc) {
        return aiDocMapper.selectAiDocList(aiDoc);
    }

    @Override
    public int insertAiDoc(AiDoc aiDoc) {
        aiDoc.setStatus("pending");
        int rows = aiDocMapper.insertAiDoc(aiDoc);
        // 异步处理文档
        if (rows > 0 && aiDoc.getDocId() != null) {
            processDocAsync(aiDoc.getDocId());
        }
        return rows;
    }

    @Override
    public int updateAiDoc(AiDoc aiDoc) {
        return aiDocMapper.updateAiDoc(aiDoc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiDocById(Long docId) {
        AiDoc doc = aiDocMapper.selectAiDocById(docId);
        if (doc != null) {
            // 删除向量
            vectorStoreService.deleteByDocId(doc.getKnowledgeId(), docId);
            // 删除切片
            aiDocSliceMapper.deleteAiDocSliceByDocId(docId);
        }
        return aiDocMapper.deleteAiDocById(docId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiDocByIds(Long[] docIds) {
        for (Long docId : docIds) {
            deleteAiDocById(docId);
        }
        return docIds.length;
    }

    @Override
    public List<AiDocSlice> selectDocSlices(Long docId) {
        return aiDocSliceMapper.selectAiDocSliceByDocId(docId);
    }

    @Override
    public void processDoc(Long docId) {
        processDocAsync(docId);
    }

    @Async
    public void processDocAsync(Long docId) {
        try {
            documentProcessService.processDocument(docId);
        } catch (Exception e) {
            log.error("文档处理失败: docId={}", docId, e);
            // 更新状态为失败
            AiDoc doc = new AiDoc();
            doc.setDocId(docId);
            doc.setStatus("failed");
            aiDocMapper.updateAiDoc(doc);
        }
    }
}
```

- [ ] **Step 5: 创建 IAiModelService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiModel;

import java.util.List;

/**
 * AI模型配置Service接口
 */
public interface IAiModelService {

    /**
     * 查询模型配置
     */
    AiModel selectAiModelById(Long modelId);

    /**
     * 查询模型配置列表
     */
    List<AiModel> selectAiModelList(AiModel aiModel);

    /**
     * 查询默认LLM模型
     */
    AiModel selectDefaultLLMModel();

    /**
     * 查询默认Embedding模型
     */
    AiModel selectDefaultEmbeddingModel();

    /**
     * 新增模型配置
     */
    int insertAiModel(AiModel aiModel);

    /**
     * 修改模型配置
     */
    int updateAiModel(AiModel aiModel);

    /**
     * 删除模型配置
     */
    int deleteAiModelById(Long modelId);

    /**
     * 批量删除模型配置
     */
    int deleteAiModelByIds(Long[] modelIds);

    /**
     * 切换默认模型
     */
    int changeDefaultFlag(Long modelId, Integer defaultFlag);
}
```

- [ ] **Step 6: 创建 AiModelServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.mapper.AiModelMapper;
import com.twelvet.ai.server.service.IAiModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI模型配置Service实现
 */
@Service
public class AiModelServiceImpl implements IAiModelService {

    private final AiModelMapper aiModelMapper;

    public AiModelServiceImpl(AiModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }

    @Override
    public AiModel selectAiModelById(Long modelId) {
        return aiModelMapper.selectAiModelById(modelId);
    }

    @Override
    public List<AiModel> selectAiModelList(AiModel aiModel) {
        return aiModelMapper.selectAiModelList(aiModel);
    }

    @Override
    public AiModel selectDefaultLLMModel() {
        return aiModelMapper.selectDefaultLLMModel();
    }

    @Override
    public AiModel selectDefaultEmbeddingModel() {
        return aiModelMapper.selectDefaultEmbeddingModel();
    }

    @Override
    public int insertAiModel(AiModel aiModel) {
        return aiModelMapper.insertAiModel(aiModel);
    }

    @Override
    public int updateAiModel(AiModel aiModel) {
        return aiModelMapper.updateAiModel(aiModel);
    }

    @Override
    public int deleteAiModelById(Long modelId) {
        return aiModelMapper.deleteAiModelById(modelId);
    }

    @Override
    public int deleteAiModelByIds(Long[] modelIds) {
        return aiModelMapper.deleteAiModelByIds(modelIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeDefaultFlag(Long modelId, Integer defaultFlag) {
        AiModel model = aiModelMapper.selectAiModelById(modelId);
        if (model == null) {
            return 0;
        }

        if (defaultFlag == 1) {
            // 清除同类型的其他默认标记
            aiModelMapper.clearDefaultFlagByType(model.getModelType());
        }

        // 更新当前模型的默认标记
        AiModel updateModel = new AiModel();
        updateModel.setModelId(modelId);
        updateModel.setDefaultFlag(defaultFlag);
        return aiModelMapper.updateAiModel(updateModel);
    }
}
```

- [ ] **Step 7: 创建 IAiChatHistoryService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiChatHistory;

import java.util.List;

/**
 * AI聊天历史Service接口
 */
public interface IAiChatHistoryService {

    /**
     * 查询聊天历史列表
     */
    List<AiChatHistory> selectAiChatHistoryList(AiChatHistory aiChatHistory);

    /**
     * 查询知识库最近的聊天历史
     */
    List<AiChatHistory> selectRecentHistory(Long knowledgeId, Integer limit);

    /**
     * 新增聊天历史
     */
    int insertAiChatHistory(AiChatHistory aiChatHistory);

    /**
     * 清空知识库的聊天历史
     */
    int deleteAiChatHistoryByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 8: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/
git commit -m "feat(ai): add service interfaces and implementations"
```

---

## Task 7: 创建 RAG 核心服务

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/config/AiConfig.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/rag/VectorStoreService.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/rag/DocumentProcessService.java`

- [ ] **Step 1: 创建 AiConfig.java**

```java
package com.twelvet.ai.server.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI配置类
 */
@Configuration
@EnableAsync
public class AiConfig {

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    /**
     * 默认嵌入模型（用于文档向量化）
     * 注意：实际使用时会根据数据库配置动态创建
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            return null;
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(defaultBaseUrl)
                .apiKey(defaultApiKey)
                .build();
        return new OpenAiEmbeddingModel(openAiApi);
    }
}
```

- [ ] **Step 2: 创建 VectorStoreService.java**

```java
package com.twelvet.ai.server.rag;

import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.service.IAiModelService;
import org.ai.simple.vectorstore.SimpleVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储服务
 * 按知识库隔离存储向量数据
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    @Value("${ai.vector-store.path:data/vector-store}")
    private String vectorStorePath;

    private final IAiModelService aiModelService;
    private final Map<Long, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();
    private final Map<Long, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    public VectorStoreService(IAiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @PostConstruct
    public void init() {
        // 确保向量存储目录存在
        File dir = new File(vectorStorePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 获取知识库的向量存储
     */
    public VectorStore getVectorStore(Long knowledgeId) {
        return vectorStoreCache.computeIfAbsent(knowledgeId, id -> {
            EmbeddingModel embeddingModel = getEmbeddingModel();
            if (embeddingModel == null) {
                throw new RuntimeException("Embedding模型未配置");
            }

            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

            // 加载已有数据
            Path filePath = getVectorFilePath(id);
            if (Files.exists(filePath)) {
                try {
                    store.load(filePath);
                    log.info("加载知识库向量数据: knowledgeId={}, file={}", id, filePath);
                } catch (Exception e) {
                    log.warn("加载向量数据失败: knowledgeId={}, error={}", id, e.getMessage());
                }
            }

            return store;
        });
    }

    /**
     * 获取嵌入模型
     */
    public EmbeddingModel getEmbeddingModel() {
        AiModel model = aiModelService.selectDefaultEmbeddingModel();
        if (model == null) {
            return null;
        }
        return embeddingModelCache.computeIfAbsent(model.getModelId(), id -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(model.getBaseUrl())
                    .apiKey(model.getApiKey())
                    .build();
            return new OpenAiEmbeddingModel(openAiApi);
        });
    }

    /**
     * 添加文档切片到向量库
     */
    public void addDocuments(Long knowledgeId, List<Document> documents) {
        VectorStore store = getVectorStore(knowledgeId);
        store.add(documents);
        saveVectorStore(knowledgeId);
        log.info("添加向量文档: knowledgeId={}, count={}", knowledgeId, documents.size());
    }

    /**
     * 相似度搜索
     */
    public List<Document> similaritySearch(Long knowledgeId, String query, int topK, double threshold) {
        VectorStore store = getVectorStore(knowledgeId);
        SearchRequest request = SearchRequest.query(query)
                .withTopK(topK)
                .withSimilarityThreshold(threshold);
        return store.similaritySearch(request);
    }

    /**
     * 删除文档的向量
     */
    public void deleteByDocId(Long knowledgeId, Long docId) {
        // SimpleVectorStore 不支持按条件删除，需要重建
        // 这里采用标记删除的方式，在检索时过滤
        log.info("标记删除文档向量: knowledgeId={}, docId={}", knowledgeId, docId);
    }

    /**
     * 删除知识库的所有向量
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        vectorStoreCache.remove(knowledgeId);
        Path filePath = getVectorFilePath(knowledgeId);
        try {
            Files.deleteIfExists(filePath);
            log.info("删除知识库向量数据: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("删除向量文件失败: {}", e.getMessage());
        }
    }

    /**
     * 保存向量存储到文件
     */
    private void saveVectorStore(Long knowledgeId) {
        VectorStore store = vectorStoreCache.get(knowledgeId);
        if (store instanceof SimpleVectorStore simpleStore) {
            Path filePath = getVectorFilePath(knowledgeId);
            simpleStore.save(filePath);
            log.debug("保存向量数据: {}", filePath);
        }
    }

    /**
     * 获取向量文件路径
     */
    private Path getVectorFilePath(Long knowledgeId) {
        return Paths.get(vectorStorePath, "knowledge_" + knowledgeId + ".json");
    }
}
```

- [ ] **Step 3: 创建 DocumentProcessService.java**

```java
package com.twelvet.ai.server.rag;

import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;
import com.twelvet.ai.server.mapper.AiDocMapper;
import com.twelvet.ai.server.mapper.AiDocSliceMapper;
import org.ai.simple.vectorstore.TokenTextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务
 * 负责文档切片和向量化
 */
@Service
public class DocumentProcessService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessService.class);

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final AiDocMapper aiDocMapper;
    private final AiDocSliceMapper aiDocSliceMapper;
    private final VectorStoreService vectorStoreService;

    public DocumentProcessService(AiDocMapper aiDocMapper, AiDocSliceMapper aiDocSliceMapper,
                                   VectorStoreService vectorStoreService) {
        this.aiDocMapper = aiDocMapper;
        this.aiDocSliceMapper = aiDocSliceMapper;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 处理文档（切片 + 向量化）
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDocument(Long docId) {
        AiDoc doc = aiDocMapper.selectAiDocById(docId);
        if (doc == null) {
            log.warn("文档不存在: docId={}", docId);
            return;
        }

        // 更新状态为处理中
        updateDocStatus(docId, "processing");

        try {
            // 获取文档内容
            String content = getDocumentContent(doc);
            if (content == null || content.isBlank()) {
                log.warn("文档内容为空: docId={}", docId);
                updateDocStatus(docId, "failed");
                return;
            }

            // 切片
            List<String> chunks = splitText(content);
            log.info("文档切片完成: docId={}, chunks={}", docId, chunks.size());

            // 删除旧切片
            aiDocSliceMapper.deleteAiDocSliceByDocId(docId);

            // 保存切片到数据库
            List<AiDocSlice> slices = new ArrayList<>();
            List<Document> documents = new ArrayList<>();
            int index = 0;
            for (String chunk : chunks) {
                AiDocSlice slice = new AiDocSlice();
                slice.setDocId(docId);
                slice.setKnowledgeId(doc.getKnowledgeId());
                slice.setSliceIndex(index);
                slice.setContent(chunk);
                slice.setCharCount(chunk.length());
                slice.setVectorStatus("pending");
                slices.add(slice);

                // 构建 Spring AI Document
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("docId", docId);
                metadata.put("knowledgeId", doc.getKnowledgeId());
                metadata.put("sliceIndex", index);
                documents.add(new Document(chunk, metadata));

                index++;
            }
            aiDocSliceMapper.batchInsertAiDocSlice(slices);

            // 向量化
            vectorStoreService.addDocuments(doc.getKnowledgeId(), documents);
            log.info("文档向量化完成: docId={}, vectors={}", docId, documents.size());

            // 更新文档状态
            AiDoc update = new AiDoc();
            update.setDocId(docId);
            update.setSliceCount(chunks.size());
            update.setCharCount(content.length());
            update.setStatus("completed");
            aiDocMapper.updateAiDoc(update);

        } catch (Exception e) {
            log.error("文档处理失败: docId={}", docId, e);
            updateDocStatus(docId, "failed");
            throw e;
        }
    }

    /**
     * 获取文档内容
     */
    private String getDocumentContent(AiDoc doc) {
        if ("INPUT".equals(doc.getSourceType())) {
            return doc.getContent();
        }
        // TODO: 文件上传类型的解析，后续迭代实现
        return doc.getContent();
    }

    /**
     * 文本切片
     */
    private List<String> splitText(String text) {
        TokenTextSplitter splitter = new TokenTextSplitter(
                CHUNK_SIZE,
                CHUNK_OVERLAP,
                5,  // minChunkSizeChars
                10000,  // maxNumChunks
                true  // keepSeparator
        );
        return splitter.apply(text);
    }

    /**
     * 更新文档状态
     */
    private void updateDocStatus(Long docId, String status) {
        AiDoc update = new AiDoc();
        update.setDocId(docId);
        update.setStatus(status);
        aiDocMapper.updateAiDoc(update);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/config/
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/rag/
git commit -m "feat(ai): add RAG core services (VectorStore, DocumentProcess)"
```

---

## Task 8: 创建 Controller

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiKnowledgeController.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiDocController.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiModelController.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiChatController.java`

- [ ] **Step 1: 创建 AiKnowledgeController.java**

```java
package com.twelvet.ai.server.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.twelvet.ai.api.domain.AiKnowledge;
import com.twelvet.ai.server.service.IAiKnowledgeService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI知识库Controller
 */
@Tag(description = "AI知识库", name = "AiKnowledgeController")
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController extends TWTController {

    private final IAiKnowledgeService aiKnowledgeService;

    public AiKnowledgeController(IAiKnowledgeService aiKnowledgeService) {
        this.aiKnowledgeService = aiKnowledgeService;
    }

    @Operation(summary = "查询知识库列表")
    @GetMapping("/pageQuery")
    public JsonResult<PageInfo<AiKnowledge>> pageQuery(AiKnowledge aiKnowledge,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AiKnowledge> list = aiKnowledgeService.selectAiKnowledgeList(aiKnowledge);
        return JsonResult.success(new PageInfo<>(list));
    }

    @Operation(summary = "查询所有知识库")
    @GetMapping("/list")
    public JsonResult<List<AiKnowledge>> list() {
        return JsonResult.success(aiKnowledgeService.selectAiKnowledgeAll());
    }

    @Operation(summary = "获取知识库详情")
    @GetMapping("/{knowledgeId}")
    public JsonResult<AiKnowledge> getInfo(@PathVariable Long knowledgeId) {
        return JsonResult.success(aiKnowledgeService.selectAiKnowledgeById(knowledgeId));
    }

    @Operation(summary = "新增知识库")
    @PostMapping
    public JsonResult<String> add(@RequestBody AiKnowledge aiKnowledge) {
        return json(aiKnowledgeService.insertAiKnowledge(aiKnowledge));
    }

    @Operation(summary = "修改知识库")
    @PutMapping
    public JsonResult<String> edit(@RequestBody AiKnowledge aiKnowledge) {
        return json(aiKnowledgeService.updateAiKnowledge(aiKnowledge));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{knowledgeIds}")
    public JsonResult<String> remove(@PathVariable Long[] knowledgeIds) {
        return json(aiKnowledgeService.deleteAiKnowledgeByIds(knowledgeIds));
    }
}
```

- [ ] **Step 2: 创建 AiDocController.java**

```java
package com.twelvet.ai.server.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.twelvet.ai.api.domain.AiDoc;
import com.twelvet.ai.api.domain.AiDocSlice;
import com.twelvet.ai.server.service.IAiDocService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI文档Controller
 */
@Tag(description = "AI文档", name = "AiDocController")
@RestController
@RequestMapping("/ai/doc")
public class AiDocController extends TWTController {

    private final IAiDocService aiDocService;

    public AiDocController(IAiDocService aiDocService) {
        this.aiDocService = aiDocService;
    }

    @Operation(summary = "查询文档列表")
    @GetMapping("/pageQuery")
    public JsonResult<PageInfo<AiDoc>> pageQuery(AiDoc aiDoc,
                                                  @RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AiDoc> list = aiDocService.selectAiDocList(aiDoc);
        return JsonResult.success(new PageInfo<>(list));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{docId}")
    public JsonResult<AiDoc> getInfo(@PathVariable Long docId) {
        return JsonResult.success(aiDocService.selectAiDocById(docId));
    }

    @Operation(summary = "新增文档")
    @PostMapping
    public JsonResult<String> add(@RequestBody AiDoc aiDoc) {
        return json(aiDocService.insertAiDoc(aiDoc));
    }

    @Operation(summary = "修改文档")
    @PutMapping
    public JsonResult<String> edit(@RequestBody AiDoc aiDoc) {
        return json(aiDocService.updateAiDoc(aiDoc));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{docIds}")
    public JsonResult<String> remove(@PathVariable Long[] docIds) {
        return json(aiDocService.deleteAiDocByIds(docIds));
    }

    @Operation(summary = "查询文档切片")
    @GetMapping("/{docId}/slices")
    public JsonResult<List<AiDocSlice>> getSlices(@PathVariable Long docId) {
        return JsonResult.success(aiDocService.selectDocSlices(docId));
    }

    @Operation(summary = "重新处理文档")
    @PostMapping("/{docId}/process")
    public JsonResult<String> process(@PathVariable Long docId) {
        aiDocService.processDoc(docId);
        return JsonResult.success("文档处理已提交");
    }
}
```

- [ ] **Step 3: 创建 AiModelController.java**

```java
package com.twelvet.ai.server.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.service.IAiModelService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI模型配置Controller
 */
@Tag(description = "AI模型配置", name = "AiModelController")
@RestController
@RequestMapping("/ai/model")
public class AiModelController extends TWTController {

    private final IAiModelService aiModelService;

    public AiModelController(IAiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @Operation(summary = "查询模型列表")
    @GetMapping("/pageQuery")
    public JsonResult<PageInfo<AiModel>> pageQuery(AiModel aiModel,
                                                    @RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AiModel> list = aiModelService.selectAiModelList(aiModel);
        return JsonResult.success(new PageInfo<>(list));
    }

    @Operation(summary = "获取模型详情")
    @GetMapping("/{modelId}")
    public JsonResult<AiModel> getInfo(@PathVariable Long modelId) {
        return JsonResult.success(aiModelService.selectAiModelById(modelId));
    }

    @Operation(summary = "新增模型配置")
    @PostMapping
    public JsonResult<String> add(@RequestBody AiModel aiModel) {
        return json(aiModelService.insertAiModel(aiModel));
    }

    @Operation(summary = "修改模型配置")
    @PutMapping
    public JsonResult<String> edit(@RequestBody AiModel aiModel) {
        return json(aiModelService.updateAiModel(aiModel));
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/{modelIds}")
    public JsonResult<String> remove(@PathVariable Long[] modelIds) {
        return json(aiModelService.deleteAiModelByIds(modelIds));
    }

    @Operation(summary = "切换默认模型")
    @PutMapping("/changeStatus")
    public JsonResult<String> changeStatus(@RequestBody AiModel aiModel) {
        return json(aiModelService.changeDefaultFlag(aiModel.getModelId(), aiModel.getDefaultFlag()));
    }
}
```

- [ ] **Step 4: 创建 AiChatController.java**

```java
package com.twelvet.ai.server.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.twelvet.ai.api.domain.AiChatHistory;
import com.twelvet.ai.server.service.IAiChatHistoryService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI对话Controller
 */
@Tag(description = "AI对话", name = "AiChatController")
@RestController
@RequestMapping("/ai/chat")
public class AiChatController extends TWTController {

    private final IAiChatHistoryService aiChatHistoryService;

    public AiChatController(IAiChatHistoryService aiChatHistoryService) {
        this.aiChatHistoryService = aiChatHistoryService;
    }

    @Operation(summary = "AI对话（SSE流式）")
    @PostMapping(produces = "text/event-stream")
    public SseEmitter chat(@RequestBody Map<String, Object> params) {
        Long knowledgeId = params.get("knowledgeId") != null ? Long.valueOf(params.get("knowledgeId").toString()) : null;
        String content = params.get("content") != null ? params.get("content").toString() : "";

        // TODO: 调用 AiChatService 进行对话，后续 Task 实现
        SseEmitter emitter = new SseEmitter(120000L);
        try {
            emitter.send(SseEmitter.event().data("{\"type\":\"text\",\"content\":\"AI对话功能开发中...\"}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Operation(summary = "查询聊天历史")
    @GetMapping("/history/page")
    public JsonResult<PageInfo<AiChatHistory>> historyPage(AiChatHistory aiChatHistory,
                                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AiChatHistory> list = aiChatHistoryService.selectAiChatHistoryList(aiChatHistory);
        return JsonResult.success(new PageInfo<>(list));
    }

    @Operation(summary = "清空聊天历史")
    @DeleteMapping("/history/{knowledgeId}")
    public JsonResult<String> clearHistory(@PathVariable Long knowledgeId) {
        return json(aiChatHistoryService.deleteAiChatHistoryByKnowledgeId(knowledgeId));
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/
git commit -m "feat(ai): add controllers for knowledge, doc, model, chat"
```

---

## Task 9: 实现 AI 对话服务

**Files:**
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiChatService.java`
- Create: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiChatServiceImpl.java`
- Modify: `twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiChatController.java`

- [ ] **Step 1: 创建 IAiChatService.java**

```java
package com.twelvet.ai.server.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI对话Service接口
 */
public interface IAiChatService {

    /**
     * SSE流式对话
     * @param knowledgeId 知识库ID
     * @param content 用户输入
     * @param carryContextFlag 是否携带上下文
     * @param createBy 创建者
     * @return SSE发射器
     */
    SseEmitter chat(Long knowledgeId, String content, Boolean carryContextFlag, String createBy);
}
```

- [ ] **Step 2: 创建 AiChatServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twelvet.ai.api.domain.AiChatHistory;
import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.server.mapper.AiChatHistoryMapper;
import com.twelvet.ai.server.rag.VectorStoreService;
import com.twelvet.ai.server.service.IAiChatService;
import com.twelvet.ai.server.service.IAiModelService;
import org.ai.simple.vectorstore.TokenTextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI对话Service实现
 */
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private static final int MAX_HISTORY = 20;
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，请根据用户的提问和提供的参考信息进行回答。
            规则：
            1. 如果参考信息中有相关内容，请基于参考信息回答；
            2. 如果参考信息中没有相关内容，请诚实告知用户，不要编造；
            3. 回答要简洁、准确、友好。
            """;

    private final AiChatHistoryMapper aiChatHistoryMapper;
    private final VectorStoreService vectorStoreService;
    private final IAiModelService aiModelService;
    private final ObjectMapper objectMapper;

    private final Map<Long, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    public AiChatServiceImpl(AiChatHistoryMapper aiChatHistoryMapper,
                             VectorStoreService vectorStoreService,
                             IAiModelService aiModelService,
                             ObjectMapper objectMapper) {
        this.aiChatHistoryMapper = aiChatHistoryMapper;
        this.vectorStoreService = vectorStoreService;
        this.aiModelService = aiModelService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter chat(Long knowledgeId, String content, Boolean carryContextFlag, String createBy) {
        SseEmitter emitter = new SseEmitter(120000L);

        // 检查模型配置
        AiModel llmModel = aiModelService.selectDefaultLLMModel();
        if (llmModel == null) {
            sendErrorAndComplete(emitter, "AI功能暂未配置，请联系管理员配置模型");
            return emitter;
        }

        try {
            // 保存用户消息
            AiChatHistory userHistory = new AiChatHistory();
            userHistory.setKnowledgeId(knowledgeId);
            userHistory.setRole("USER");
            userHistory.setContent(content);
            userHistory.setCreateBy(createBy);
            aiChatHistoryMapper.insertAiChatHistory(userHistory);

            // 构建消息
            List<Message> messages = buildMessages(knowledgeId, content, carryContextFlag);

            // 获取 ChatClient
            ChatClient chatClient = getChatClient(llmModel);

            // 流式对话
            StringBuilder fullResponse = new StringBuilder();

            chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .content()
                    .subscribe(
                            token -> {
                                fullResponse.append(token);
                                sendEvent(emitter, "text", token);
                            },
                            error -> {
                                log.error("AI对话失败", error);
                                sendErrorAndComplete(emitter, "AI对话失败，请稍后重试");
                            },
                            () -> {
                                // 保存AI回复
                                AiChatHistory aiHistory = new AiChatHistory();
                                aiHistory.setKnowledgeId(knowledgeId);
                                aiHistory.setRole("ASSISTANT");
                                aiHistory.setContent(fullResponse.toString());
                                aiHistory.setCreateBy("AI");
                                aiChatHistoryMapper.insertAiChatHistory(aiHistory);

                                // 发送完成事件
                                sendEvent(emitter, "done", null);
                                emitter.complete();
                            }
                    );

        } catch (Exception e) {
            log.error("AI对话异常", e);
            sendErrorAndComplete(emitter, "AI对话异常：" + e.getMessage());
        }

        return emitter;
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(Long knowledgeId, String content, Boolean carryContextFlag) {
        List<Message> messages = new ArrayList<>();

        // 系统提示
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // RAG 检索
        List<Document> docs = vectorStoreService.similaritySearch(knowledgeId, content, TOP_K, SIMILARITY_THRESHOLD);
        if (!docs.isEmpty()) {
            StringBuilder contextBuilder = new StringBuilder("参考信息：\n");
            for (int i = 0; i < docs.size(); i++) {
                contextBuilder.append((i + 1)).append(". ").append(docs.get(i).getContent()).append("\n");
            }
            messages.add(new SystemMessage(contextBuilder.toString()));
        }

        // 携带历史上下文
        if (Boolean.TRUE.equals(carryContextFlag)) {
            List<AiChatHistory> historyList = aiChatHistoryMapper.selectRecentHistoryByKnowledgeId(knowledgeId, MAX_HISTORY);
            // 倒序变正序
            Collections.reverse(historyList);
            for (AiChatHistory history : historyList) {
                if ("USER".equals(history.getRole())) {
                    messages.add(new UserMessage(history.getContent()));
                } else {
                    messages.add(new AssistantMessage(history.getContent()));
                }
            }
        }

        // 当前用户输入
        messages.add(new UserMessage(content));

        return messages;
    }

    /**
     * 获取 ChatClient
     */
    private ChatClient getChatClient(AiModel model) {
        return chatClientCache.computeIfAbsent(model.getModelId(), id -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(model.getBaseUrl())
                    .apiKey(model.getApiKey())
                    .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model.getModelAlias())
                    .temperature(model.getTemperature() != null ? model.getTemperature().doubleValue() : 0.8)
                    .topP(model.getTopP() != null ? model.getTopP().doubleValue() : 0.8)
                    .build();

            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
            return ChatClient.builder(chatModel).build();
        });
    }

    /**
     * 发送 SSE 事件
     */
    private void sendEvent(SseEmitter emitter, String type, String content) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type);
            if (content != null) {
                event.put("content", content);
            }
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.warn("SSE发送失败", e);
        }
    }

    /**
     * 发送错误并完成
     */
    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "error");
            event.put("message", message);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE发送失败", e);
            emitter.completeWithError(e);
        }
    }
}
```

- [ ] **Step 3: 更新 AiChatController.java**

```java
package com.twelvet.ai.server.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.twelvet.ai.api.domain.AiChatHistory;
import com.twelvet.ai.server.service.IAiChatHistoryService;
import com.twelvet.ai.server.service.IAiChatService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI对话Controller
 */
@Tag(description = "AI对话", name = "AiChatController")
@RestController
@RequestMapping("/ai/chat")
public class AiChatController extends TWTController {

    private final IAiChatService aiChatService;
    private final IAiChatHistoryService aiChatHistoryService;

    public AiChatController(IAiChatService aiChatService, IAiChatHistoryService aiChatHistoryService) {
        this.aiChatService = aiChatService;
        this.aiChatHistoryService = aiChatHistoryService;
    }

    @Operation(summary = "AI对话（SSE流式）")
    @PostMapping(produces = "text/event-stream")
    public SseEmitter chat(@RequestBody Map<String, Object> params) {
        Long knowledgeId = params.get("knowledgeId") != null ? Long.valueOf(params.get("knowledgeId").toString()) : null;
        String content = params.get("content") != null ? params.get("content").toString() : "";
        Boolean carryContextFlag = params.get("carryContextFlag") != null ? Boolean.valueOf(params.get("carryContextFlag").toString()) : false;
        String createBy = StpUtil.getLoginIdAsString();

        return aiChatService.chat(knowledgeId, content, carryContextFlag, createBy);
    }

    @Operation(summary = "查询聊天历史")
    @GetMapping("/history/page")
    public JsonResult<PageInfo<AiChatHistory>> historyPage(AiChatHistory aiChatHistory,
                                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AiChatHistory> list = aiChatHistoryService.selectAiChatHistoryList(aiChatHistory);
        return JsonResult.success(new PageInfo<>(list));
    }

    @Operation(summary = "清空聊天历史")
    @DeleteMapping("/history/{knowledgeId}")
    public JsonResult<String> clearHistory(@PathVariable Long knowledgeId) {
        return json(aiChatHistoryService.deleteAiChatHistoryByKnowledgeId(knowledgeId));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiChatService.java
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiChatServiceImpl.java
git add twelvet-fast/twelvet-ai/twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiChatController.java
git commit -m "feat(ai): implement AI chat service with RAG and SSE streaming"
```

---

## Task 10: 更新配置文件

**Files:**
- Modify: `twelvet-fast/twelvet-admin/src/main/resources/application.yml`

- [ ] **Step 1: 添加 AI 模块配置**

在 `application.yml` 中添加：

```yaml
# AI 模块配置
ai:
  vector-store:
    path: data/vector-store
```

- [ ] **Step 2: 更新 springdoc 配置**

在 `springdoc.group-configs` 中添加 AI 模块：

```yaml
- group: 9.AI模块
  packages-to-scan: com.twelvet.ai
```

- [ ] **Step 3: Commit**

```bash
git add twelvet-fast/twelvet-admin/src/main/resources/application.yml
git commit -m "feat(ai): add AI module configuration"
```

---

## Task 11: 编译验证

- [ ] **Step 1: 编译项目**

```bash
cd twelvet-fast
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 检查是否有编译错误**

如果有错误，根据错误信息修复。

- [ ] **Step 3: Commit（如果有修复）**

```bash
git add -A
git commit -m "fix(ai): fix compilation errors"
```

---

## Task 12: 最终提交

- [ ] **Step 1: 确保所有代码已提交**

```bash
git status
git add -A
git commit -m "feat(ai): complete AI module implementation with RAG support"
```

- [ ] **Step 2: 验证实现完整性**

- [x] 数据库表结构（5张表）
- [x] 实体类（5个实体）
- [x] Mapper 接口和 XML（5个 Mapper）
- [x] Service 接口和实现（4个 Service）
- [x] RAG 核心服务（VectorStoreService、DocumentProcessService）
- [x] Controller（4个 Controller）
- [x] 配置文件更新

---

## 实现总结

本期实现完成了 AI 模块的核心功能：

1. **知识库管理**：CRUD 操作，删除时级联清理向量数据
2. **文档管理**：CRUD + 异步切片向量化
3. **模型配置**：支持 LLM 和 Embedding 模型的动态配置
4. **AI 对话**：SSE 流式 + RAG 检索增强

**后续迭代计划：**
- MCP 工具协议支持
- TTS 语音播报
- 联网搜索
- 文件上传文档解析
