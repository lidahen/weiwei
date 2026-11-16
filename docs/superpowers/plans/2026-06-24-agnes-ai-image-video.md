# Agnes AI 图片/视频生成功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 twelvet-ai 模块中集成 Agnes Image 2.1 Flash（文生图/图生图）和 Agnes Video V2.0（文生视频/图生视频/多图视频/关键帧动画）功能，新增图片生成和视频生成两个前端页面。

**架构：** 后端使用 WebClient 直接调用 Agnes REST API，复用 ai_model 表管理 API Key 和 Base URL；新增 ai_image_task 和 ai_video_task 表记录生成历史；新增 Controller 提供 REST API；前端新增 /ai/image 和 /ai/video 两个独立页面。

**Tech Stack:** Java 17, Spring Boot 3.x, MyBatis, WebClient, React 18 + Umi Max 4.x, Ant Design 5.x + ProComponents

## Global Constraints

- Java 17 + Spring Boot 3.x
- MyBatis + PageHelper 分页
- Sa-Token 权限校验 (`@SaCheckPermission`)
- Knife4j `@Schema` + `@Operation` 注解编写 API 文档
- Spring Java Format 代码格式
- RESTful API 风格
- 软删除（del_flag）
- 无 Lombok，手动 getter/setter
- 构造函数注入（AI 模块新风格）
- 前端使用 TypeScript + Umi Max + Ant Design ProTable

---

## 文件结构

### 后端新增文件

| 文件 | 职责 |
|------|------|
| `twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiImageTask.java` | 图片生成任务实体 |
| `twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiVideoTask.java` | 视频生成任务实体 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiImageTaskMapper.java` | 图片任务 Mapper 接口 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiVideoTaskMapper.java` | 视频任务 Mapper 接口 |
| `twelvet-ai-server/src/main/resources/mapper/AiImageTaskMapper.xml` | 图片任务 Mapper XML |
| `twelvet-ai-server/src/main/resources/mapper/AiVideoTaskMapper.xml` | 视频任务 Mapper XML |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiImageTaskService.java` | 图片任务 Service 接口 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiVideoTaskService.java` | 视频任务 Service 接口 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiImageTaskServiceImpl.java` | 图片任务 Service 实现 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiVideoTaskServiceImpl.java` | 视频任务 Service 实现 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesImageService.java` | Agnes 图片生成核心服务 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesVideoService.java` | Agnes 视频生成核心服务 |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiImageController.java` | 图片生成 Controller |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiVideoController.java` | 视频生成 Controller |
| `twelvet-ai-server/src/main/java/com/twelvet/ai/server/config/WebClientConfig.java` | WebClient 配置 |

### 后端修改文件

| 文件 | 修改内容 |
|------|---------|
| `twelvet-ai-server/pom.xml` | 添加 spring-boot-starter-webflux（WebClient）依赖 |
| `data/twelvet-ai.sql` | 新增 ai_image_task、ai_video_task 表；扩展字典数据；预置 Agnes 模型数据 |

### 前端新增文件

| 文件 | 职责 |
|------|------|
| `twelvet-ui-master/src/pages/AI/Image/index.tsx` | 图片生成页面 |
| `twelvet-ui-master/src/pages/AI/Image/service.ts` | 图片生成 API 服务 |
| `twelvet-ui-master/src/pages/AI/Video/index.tsx` | 视频生成页面 |
| `twelvet-ui-master/src/pages/AI/Video/service.ts` | 视频生成 API 服务 |

### 前端修改文件

| 文件 | 修改内容 |
|------|---------|
| `twelvet-ui-master/config/routes.ts` | 新增 /ai/image 和 /ai/video 路由 |

---

## Task 1: 新增数据库表和字典数据

**Files:**
- Modify: `data/twelvet-ai.sql`

**Interfaces:**
- Produces: `ai_image_task` 表、`ai_video_task` 表、字典扩展数据、预置模型数据

- [ ] **Step 1: 在 ai_model 表的 model_type 字段注释中新增 IMAGE 和 VIDEO**

在 `data/twelvet-ai.sql` 中，找到 `ai_model` 表的 `model_type` 字段注释，修改为：

```sql
`model_type` VARCHAR(20) NOT NULL COMMENT '模型类型：LLM-大语言模型，EMBEDDING-嵌入模型，IMAGE-图片生成，VIDEO-视频生成',
```

- [ ] **Step 2: 新增 ai_image_task 表**

在 `data/twelvet-ai.sql` 的 `ai_chat_history` 表之后，添加：

```sql
-- ----------------------------
-- Table structure for ai_image_task
-- ----------------------------
DROP TABLE IF EXISTS `ai_image_task`;
CREATE TABLE `ai_image_task` (
    `task_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    `model_id` BIGINT NOT NULL COMMENT '关联模型ID',
    `prompt` TEXT NOT NULL COMMENT '提示词',
    `size` VARCHAR(20) DEFAULT '1024x768' COMMENT '输出尺寸',
    `gen_type` VARCHAR(10) NOT NULL COMMENT '生成类型：text2img-文生图，img2img-图生图',
    `input_image` TEXT COMMENT '图生图输入图片URL或Base64',
    `response_format` VARCHAR(10) DEFAULT 'url' COMMENT '输出格式：url-URL，b64_json-Base64',
    `result_url` TEXT COMMENT '生成结果DFS URL',
    `status` VARCHAR(10) DEFAULT 'pending' COMMENT '状态：pending-处理中，completed-已完成，failed-失败',
    `error_msg` TEXT COMMENT '错误信息',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志',
    INDEX `idx_model_id` (`model_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI图片生成任务表';
```

- [ ] **Step 3: 新增 ai_video_task 表**

在 `ai_image_task` 表之后，添加：

```sql
-- ----------------------------
-- Table structure for ai_video_task
-- ----------------------------
DROP TABLE IF EXISTS `ai_video_task`;
CREATE TABLE `ai_video_task` (
    `task_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    `model_id` BIGINT NOT NULL COMMENT '关联模型ID',
    `agnes_task_id` VARCHAR(64) COMMENT 'Agnes任务ID',
    `agnes_video_id` VARCHAR(64) COMMENT 'Agnes视频ID',
    `prompt` TEXT NOT NULL COMMENT '提示词',
    `gen_type` VARCHAR(20) NOT NULL COMMENT '生成类型：text2vid-文生视频，img2vid-图生视频，multi2vid-多图视频，keyframes-关键帧动画',
    `input_images` TEXT COMMENT '输入图片JSON数组',
    `width` INT DEFAULT 1152 COMMENT '视频宽度',
    `height` INT DEFAULT 768 COMMENT '视频高度',
    `num_frames` INT DEFAULT 121 COMMENT '帧数',
    `frame_rate` INT DEFAULT 24 COMMENT '帧率',
    `negative_prompt` TEXT COMMENT '负向提示词',
    `seed` BIGINT COMMENT '随机种子',
    `status` VARCHAR(10) DEFAULT 'queued' COMMENT '状态：queued-排队中，in_progress-生成中，completed-已完成，failed-失败',
    `progress` INT DEFAULT 0 COMMENT '进度百分比0-100',
    `result_url` TEXT COMMENT '生成结果DFS URL',
    `video_seconds` VARCHAR(10) COMMENT '视频时长（秒）',
    `video_size` VARCHAR(20) COMMENT '视频分辨率',
    `error_msg` TEXT COMMENT '错误信息',
    `create_by` VARCHAR(64) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志',
    INDEX `idx_model_id` (`model_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_agnes_video_id` (`agnes_video_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频生成任务表';
```

- [ ] **Step 4: 扩展字典数据**

在 `data/twelvet-ai.sql` 的 `sys_dict_data` 插入语句之后，添加：

```sql
-- 扩展 ai_model_provider 字典：新增 AGNES
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES (5, 'Agnes AI', 'agnes', 'ai_model_provider', '0', 'admin', NOW());

-- 扩展 ai_model_type 字典：新增 IMAGE 和 VIDEO
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES (3, '图片生成', 'IMAGE', 'ai_model_type', '0', 'admin', NOW());

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`)
VALUES (4, '视频生成', 'VIDEO', 'ai_model_type', '0', 'admin', NOW());
```

- [ ] **Step 5: 预置 Agnes 模型数据**

在 `data/twelvet-ai.sql` 末尾，添加：

```sql
-- 预置 Agnes 图片生成模型
INSERT INTO `ai_model` (`model_provider`, `model_type`, `model_name`, `model_alias`, `api_key`, `base_url`, `temperature`, `top_p`, `default_flag`, `status`, `create_by`, `create_time`)
VALUES ('agnes', 'IMAGE', 'agnes-image-2.1-flash', 'Agnes Image 2.1 Flash', '', 'https://apihub.agnes-ai.com', 0, 0, 1, 1, 'admin', NOW());

-- 预置 Agnes 视频生成模型
INSERT INTO `ai_model` (`model_provider`, `model_type`, `model_name`, `model_alias`, `api_key`, `base_url`, `temperature`, `top_p`, `default_flag`, `status`, `create_by`, `create_time`)
VALUES ('agnes', 'VIDEO', 'agnes-video-v2.0', 'Agnes Video V2.0', '', 'https://apihub.agnes-ai.com', 0, 0, 1, 1, 'admin', NOW());
```

- [ ] **Step 6: Commit**

```bash
git add data/twelvet-ai.sql
git commit -m "feat: add ai_image_task and ai_video_task tables, extend dictionary data, seed Agnes models"
```

---

## Task 2: 新增后端领域实体（AiImageTask, AiVideoTask）

**Files:**
- Create: `twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiImageTask.java`
- Create: `twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/AiVideoTask.java`

**Interfaces:**
- Produces: `AiImageTask` 实体类、`AiVideoTask` 实体类

- [ ] **Step 1: 创建 AiImageTask.java**

```java
package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI图片生成任务实体
 */
@Schema(description = "AI图片生成任务")
public class AiImageTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "关联模型ID")
    private Long modelId;

    @Schema(description = "提示词")
    private String prompt;

    @Schema(description = "输出尺寸")
    private String size;

    @Schema(description = "生成类型：text2img-文生图，img2img-图生图")
    private String genType;

    @Schema(description = "图生图输入图片URL或Base64")
    private String inputImage;

    @Schema(description = "输出格式：url-URL，b64_json-Base64")
    private String responseFormat;

    @Schema(description = "生成结果DFS URL")
    private String resultUrl;

    @Schema(description = "状态：pending-处理中，completed-已完成，failed-失败")
    private String status;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "删除标志")
    private Integer delFlag;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getGenType() {
        return genType;
    }

    public void setGenType(String genType) {
        this.genType = genType;
    }

    public String getInputImage() {
        return inputImage;
    }

    public void setInputImage(String inputImage) {
        this.inputImage = inputImage;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
```

- [ ] **Step 2: 创建 AiVideoTask.java**

```java
package com.twelvet.ai.api.domain;

import com.twelvet.framework.core.application.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;

/**
 * AI视频生成任务实体
 */
@Schema(description = "AI视频生成任务")
public class AiVideoTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "关联模型ID")
    private Long modelId;

    @Schema(description = "Agnes任务ID")
    private String agnesTaskId;

    @Schema(description = "Agnes视频ID")
    private String agnesVideoId;

    @Schema(description = "提示词")
    private String prompt;

    @Schema(description = "生成类型：text2vid-文生视频，img2vid-图生视频，multi2vid-多图视频，keyframes-关键帧动画")
    private String genType;

    @Schema(description = "输入图片JSON数组")
    private String inputImages;

    @Schema(description = "视频宽度")
    private Integer width;

    @Schema(description = "视频高度")
    private Integer height;

    @Schema(description = "帧数")
    private Integer numFrames;

    @Schema(description = "帧率")
    private Integer frameRate;

    @Schema(description = "负向提示词")
    private String negativePrompt;

    @Schema(description = "随机种子")
    private Long seed;

    @Schema(description = "状态：queued-排队中，in_progress-生成中，completed-已完成，failed-失败")
    private String status;

    @Schema(description = "进度百分比0-100")
    private Integer progress;

    @Schema(description = "生成结果DFS URL")
    private String resultUrl;

    @Schema(description = "视频时长（秒）")
    private String videoSeconds;

    @Schema(description = "视频分辨率")
    private String videoSize;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "删除标志")
    private Integer delFlag;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getAgnesTaskId() {
        return agnesTaskId;
    }

    public void setAgnesTaskId(String agnesTaskId) {
        this.agnesTaskId = agnesTaskId;
    }

    public String getAgnesVideoId() {
        return agnesVideoId;
    }

    public void setAgnesVideoId(String agnesVideoId) {
        this.agnesVideoId = agnesVideoId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getGenType() {
        return genType;
    }

    public void setGenType(String genType) {
        this.genType = genType;
    }

    public String getInputImages() {
        return inputImages;
    }

    public void setInputImages(String inputImages) {
        this.inputImages = inputImages;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getNumFrames() {
        return numFrames;
    }

    public void setNumFrames(Integer numFrames) {
        this.numFrames = numFrames;
    }

    public Integer getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(Integer frameRate) {
        this.frameRate = frameRate;
    }

    public String getNegativePrompt() {
        return negativePrompt;
    }

    public void setNegativePrompt(String negativePrompt) {
        this.negativePrompt = negativePrompt;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getVideoSeconds() {
        return videoSeconds;
    }

    public void setVideoSeconds(String videoSeconds) {
        this.videoSeconds = videoSeconds;
    }

    public String getVideoSize() {
        return videoSize;
    }

    public void setVideoSize(String videoSize) {
        this.videoSize = videoSize;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add twelvet-ai-api/src/main/java/com/twelvet/ai/api/domain/
git commit -m "feat: add AiImageTask and AiVideoTask domain entities"
```

---

## Task 3: 新增 Mapper 接口和 XML

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiImageTaskMapper.java`
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/AiVideoTaskMapper.java`
- Create: `twelvet-ai-server/src/main/resources/mapper/AiImageTaskMapper.xml`
- Create: `twelvet-ai-server/src/main/resources/mapper/AiVideoTaskMapper.xml`

**Interfaces:**
- Produces: `AiImageTaskMapper` 接口和 XML、`AiVideoTaskMapper` 接口和 XML

- [ ] **Step 1: 创建 AiImageTaskMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiImageTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiImageTaskMapper {
    AiImageTask selectAiImageTaskById(Long taskId);
    List<AiImageTask> selectAiImageTaskList(AiImageTask aiImageTask);
    int insertAiImageTask(AiImageTask aiImageTask);
    int updateAiImageTask(AiImageTask aiImageTask);
    int deleteAiImageTaskById(Long taskId);
    int deleteAiImageTaskByIds(Long[] taskIds);
}
```

- [ ] **Step 2: 创建 AiVideoTaskMapper.java**

```java
package com.twelvet.ai.server.mapper;

import com.twelvet.ai.api.domain.AiVideoTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiVideoTaskMapper {
    AiVideoTask selectAiVideoTaskById(Long taskId);
    List<AiVideoTask> selectAiVideoTaskList(AiVideoTask aiVideoTask);
    int insertAiVideoTask(AiVideoTask aiVideoTask);
    int updateAiVideoTask(AiVideoTask aiVideoTask);
    int deleteAiVideoTaskById(Long taskId);
    int deleteAiVideoTaskByIds(Long[] taskIds);
}
```

- [ ] **Step 3: 创建 AiImageTaskMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiImageTaskMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiImageTask" id="AiImageTaskResult">
        <result property="taskId" column="task_id"/>
        <result property="modelId" column="model_id"/>
        <result property="prompt" column="prompt"/>
        <result property="size" column="size"/>
        <result property="genType" column="gen_type"/>
        <result property="inputImage" column="input_image"/>
        <result property="responseFormat" column="response_format"/>
        <result property="resultUrl" column="result_url"/>
        <result property="status" column="status"/>
        <result property="errorMsg" column="error_msg"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateTime" column="update_time"/>
        <result property="delFlag" column="del_flag"/>
    </resultMap>

    <sql id="selectAiImageTaskVo">
        select task_id, model_id, prompt, size, gen_type, input_image, response_format, result_url, status, error_msg, create_by, create_time, update_time, del_flag
        from ai_image_task
    </sql>

    <select id="selectAiImageTaskById" parameterType="Long" resultMap="AiImageTaskResult">
        <include refid="selectAiImageTaskVo"/>
        where task_id = #{taskId} and del_flag = 0
    </select>

    <select id="selectAiImageTaskList" parameterType="com.twelvet.ai.api.domain.AiImageTask" resultMap="AiImageTaskResult">
        <include refid="selectAiImageTaskVo"/>
        <where>
            del_flag = 0
            <if test="modelId != null">AND model_id = #{modelId}</if>
            <if test="genType != null and genType != ''">AND gen_type = #{genType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="prompt != null and prompt != ''">AND prompt like concat('%', #{prompt}, '%')</if>
        </where>
        order by create_time desc
    </select>

    <insert id="insertAiImageTask" parameterType="com.twelvet.ai.api.domain.AiImageTask" useGeneratedKeys="true" keyProperty="taskId">
        insert into ai_image_task
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="modelId != null">model_id,</if>
            <if test="prompt != null and prompt != ''">prompt,</if>
            <if test="size != null and size != ''">size,</if>
            <if test="genType != null and genType != ''">gen_type,</if>
            <if test="inputImage != null and inputImage != ''">input_image,</if>
            <if test="responseFormat != null and responseFormat != ''">response_format,</if>
            <if test="resultUrl != null and resultUrl != ''">result_url,</if>
            <if test="status != null and status != ''">status,</if>
            <if test="errorMsg != null and errorMsg != ''">error_msg,</if>
            <if test="createBy != null and createBy != ''">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="modelId != null">#{modelId},</if>
            <if test="prompt != null and prompt != ''">#{prompt},</if>
            <if test="size != null and size != ''">#{size},</if>
            <if test="genType != null and genType != ''">#{genType},</if>
            <if test="inputImage != null and inputImage != ''">#{inputImage},</if>
            <if test="responseFormat != null and responseFormat != ''">#{responseFormat},</if>
            <if test="resultUrl != null and resultUrl != ''">#{resultUrl},</if>
            <if test="status != null and status != ''">#{status},</if>
            <if test="errorMsg != null and errorMsg != ''">#{errorMsg},</if>
            <if test="createBy != null and createBy != ''">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <update id="updateAiImageTask" parameterType="com.twelvet.ai.api.domain.AiImageTask">
        update ai_image_task
        <trim prefix="SET" suffixOverrides=",">
            <if test="modelId != null">model_id = #{modelId},</if>
            <if test="prompt != null and prompt != ''">prompt = #{prompt},</if>
            <if test="size != null and size != ''">size = #{size},</if>
            <if test="genType != null and genType != ''">gen_type = #{genType},</if>
            <if test="inputImage != null">input_image = #{inputImage},</if>
            <if test="responseFormat != null and responseFormat != ''">response_format = #{responseFormat},</if>
            <if test="resultUrl != null">result_url = #{resultUrl},</if>
            <if test="status != null and status != ''">status = #{status},</if>
            <if test="errorMsg != null">error_msg = #{errorMsg},</if>
            update_time = sysdate()
        </trim>
        where task_id = #{taskId}
    </update>

    <update id="deleteAiImageTaskById" parameterType="Long">
        update ai_image_task set del_flag = 1 where task_id = #{taskId}
    </update>

    <update id="deleteAiImageTaskByIds" parameterType="Long">
        update ai_image_task set del_flag = 1 where task_id in
        <foreach item="taskId" collection="array" open="(" separator="," close=")">
            #{taskId}
        </foreach>
    </update>

</mapper>
```

- [ ] **Step 4: 创建 AiVideoTaskMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.twelvet.ai.server.mapper.AiVideoTaskMapper">

    <resultMap type="com.twelvet.ai.api.domain.AiVideoTask" id="AiVideoTaskResult">
        <result property="taskId" column="task_id"/>
        <result property="modelId" column="model_id"/>
        <result property="agnesTaskId" column="agnes_task_id"/>
        <result property="agnesVideoId" column="agnes_video_id"/>
        <result property="prompt" column="prompt"/>
        <result property="genType" column="gen_type"/>
        <result property="inputImages" column="input_images"/>
        <result property="width" column="width"/>
        <result property="height" column="height"/>
        <result property="numFrames" column="num_frames"/>
        <result property="frameRate" column="frame_rate"/>
        <result property="negativePrompt" column="negative_prompt"/>
        <result property="seed" column="seed"/>
        <result property="status" column="status"/>
        <result property="progress" column="progress"/>
        <result property="resultUrl" column="result_url"/>
        <result property="videoSeconds" column="video_seconds"/>
        <result property="videoSize" column="video_size"/>
        <result property="errorMsg" column="error_msg"/>
        <result property="createBy" column="create_by"/>
        <result property="createTime" column="create_time"/>
        <result property="updateTime" column="update_time"/>
        <result property="delFlag" column="del_flag"/>
    </resultMap>

    <sql id="selectAiVideoTaskVo">
        select task_id, model_id, agnes_task_id, agnes_video_id, prompt, gen_type, input_images, width, height, num_frames, frame_rate, negative_prompt, seed, status, progress, result_url, video_seconds, video_size, error_msg, create_by, create_time, update_time, del_flag
        from ai_video_task
    </sql>

    <select id="selectAiVideoTaskById" parameterType="Long" resultMap="AiVideoTaskResult">
        <include refid="selectAiVideoTaskVo"/>
        where task_id = #{taskId} and del_flag = 0
    </select>

    <select id="selectAiVideoTaskList" parameterType="com.twelvet.ai.api.domain.AiVideoTask" resultMap="AiVideoTaskResult">
        <include refid="selectAiVideoTaskVo"/>
        <where>
            del_flag = 0
            <if test="modelId != null">AND model_id = #{modelId}</if>
            <if test="genType != null and genType != ''">AND gen_type = #{genType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="prompt != null and prompt != ''">AND prompt like concat('%', #{prompt}, '%')</if>
        </where>
        order by create_time desc
    </select>

    <insert id="insertAiVideoTask" parameterType="com.twelvet.ai.api.domain.AiVideoTask" useGeneratedKeys="true" keyProperty="taskId">
        insert into ai_video_task
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="modelId != null">model_id,</if>
            <if test="agnesTaskId != null and agnesTaskId != ''">agnes_task_id,</if>
            <if test="agnesVideoId != null and agnesVideoId != ''">agnes_video_id,</if>
            <if test="prompt != null and prompt != ''">prompt,</if>
            <if test="genType != null and genType != ''">gen_type,</if>
            <if test="inputImages != null and inputImages != ''">input_images,</if>
            <if test="width != null">width,</if>
            <if test="height != null">height,</if>
            <if test="numFrames != null">num_frames,</if>
            <if test="frameRate != null">frame_rate,</if>
            <if test="negativePrompt != null and negativePrompt != ''">negative_prompt,</if>
            <if test="seed != null">seed,</if>
            <if test="status != null and status != ''">status,</if>
            <if test="progress != null">progress,</if>
            <if test="resultUrl != null and resultUrl != ''">result_url,</if>
            <if test="videoSeconds != null and videoSeconds != ''">video_seconds,</if>
            <if test="videoSize != null and videoSize != ''">video_size,</if>
            <if test="errorMsg != null and errorMsg != ''">error_msg,</if>
            <if test="createBy != null and createBy != ''">create_by,</if>
            create_time
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="modelId != null">#{modelId},</if>
            <if test="agnesTaskId != null and agnesTaskId != ''">#{agnesTaskId},</if>
            <if test="agnesVideoId != null and agnesVideoId != ''">#{agnesVideoId},</if>
            <if test="prompt != null and prompt != ''">#{prompt},</if>
            <if test="genType != null and genType != ''">#{genType},</if>
            <if test="inputImages != null and inputImages != ''">#{inputImages},</if>
            <if test="width != null">#{width},</if>
            <if test="height != null">#{height},</if>
            <if test="numFrames != null">#{numFrames},</if>
            <if test="frameRate != null">#{frameRate},</if>
            <if test="negativePrompt != null and negativePrompt != ''">#{negativePrompt},</if>
            <if test="seed != null">#{seed},</if>
            <if test="status != null and status != ''">#{status},</if>
            <if test="progress != null">#{progress},</if>
            <if test="resultUrl != null and resultUrl != ''">#{resultUrl},</if>
            <if test="videoSeconds != null and videoSeconds != ''">#{videoSeconds},</if>
            <if test="videoSize != null and videoSize != ''">#{videoSize},</if>
            <if test="errorMsg != null and errorMsg != ''">#{errorMsg},</if>
            <if test="createBy != null and createBy != ''">#{createBy},</if>
            sysdate()
        </trim>
    </insert>

    <update id="updateAiVideoTask" parameterType="com.twelvet.ai.api.domain.AiVideoTask">
        update ai_video_task
        <trim prefix="SET" suffixOverrides=",">
            <if test="modelId != null">model_id = #{modelId},</if>
            <if test="agnesTaskId != null">agnes_task_id = #{agnesTaskId},</if>
            <if test="agnesVideoId != null">agnes_video_id = #{agnesVideoId},</if>
            <if test="prompt != null and prompt != ''">prompt = #{prompt},</if>
            <if test="genType != null and genType != ''">gen_type = #{genType},</if>
            <if test="inputImages != null">input_images = #{inputImages},</if>
            <if test="width != null">width = #{width},</if>
            <if test="height != null">height = #{height},</if>
            <if test="numFrames != null">num_frames = #{numFrames},</if>
            <if test="frameRate != null">frame_rate = #{frameRate},</if>
            <if test="negativePrompt != null">negative_prompt = #{negativePrompt},</if>
            <if test="seed != null">seed = #{seed},</if>
            <if test="status != null and status != ''">status = #{status},</if>
            <if test="progress != null">progress = #{progress},</if>
            <if test="resultUrl != null">result_url = #{resultUrl},</if>
            <if test="videoSeconds != null">video_seconds = #{videoSeconds},</if>
            <if test="videoSize != null">video_size = #{videoSize},</if>
            <if test="errorMsg != null">error_msg = #{errorMsg},</if>
            update_time = sysdate()
        </trim>
        where task_id = #{taskId}
    </update>

    <update id="deleteAiVideoTaskById" parameterType="Long">
        update ai_video_task set del_flag = 1 where task_id = #{taskId}
    </update>

    <update id="deleteAiVideoTaskByIds" parameterType="Long">
        update ai_video_task set del_flag = 1 where task_id in
        <foreach item="taskId" collection="array" open="(" separator="," close=")">
            #{taskId}
        </foreach>
    </update>

</mapper>
```

- [ ] **Step 5: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/mapper/
git add twelvet-ai-server/src/main/resources/mapper/
git commit -m "feat: add AiImageTask and AiVideoTask mappers"
```

---

## Task 4: 新增 Service 层

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiImageTaskService.java`
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/IAiVideoTaskService.java`
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiImageTaskServiceImpl.java`
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/impl/AiVideoTaskServiceImpl.java`

**Interfaces:**
- Produces: `IAiImageTaskService` 接口、`IAiVideoTaskService` 接口、实现类

- [ ] **Step 1: 创建 IAiImageTaskService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiImageTask;

import java.util.List;

public interface IAiImageTaskService {
    AiImageTask selectAiImageTaskById(Long taskId);
    List<AiImageTask> selectAiImageTaskList(AiImageTask aiImageTask);
    int insertAiImageTask(AiImageTask aiImageTask);
    int updateAiImageTask(AiImageTask aiImageTask);
    int deleteAiImageTaskById(Long taskId);
    int deleteAiImageTaskByIds(Long[] taskIds);
}
```

- [ ] **Step 2: 创建 IAiVideoTaskService.java**

```java
package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.AiVideoTask;

import java.util.List;

public interface IAiVideoTaskService {
    AiVideoTask selectAiVideoTaskById(Long taskId);
    List<AiVideoTask> selectAiVideoTaskList(AiVideoTask aiVideoTask);
    int insertAiVideoTask(AiVideoTask aiVideoTask);
    int updateAiVideoTask(AiVideoTask aiVideoTask);
    int deleteAiVideoTaskById(Long taskId);
    int deleteAiVideoTaskByIds(Long[] taskIds);
}
```

- [ ] **Step 3: 创建 AiImageTaskServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiImageTask;
import com.twelvet.ai.server.mapper.AiImageTaskMapper;
import com.twelvet.ai.server.service.IAiImageTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiImageTaskServiceImpl implements IAiImageTaskService {

    private final AiImageTaskMapper aiImageTaskMapper;

    public AiImageTaskServiceImpl(AiImageTaskMapper aiImageTaskMapper) {
        this.aiImageTaskMapper = aiImageTaskMapper;
    }

    @Override
    public AiImageTask selectAiImageTaskById(Long taskId) {
        return aiImageTaskMapper.selectAiImageTaskById(taskId);
    }

    @Override
    public List<AiImageTask> selectAiImageTaskList(AiImageTask aiImageTask) {
        return aiImageTaskMapper.selectAiImageTaskList(aiImageTask);
    }

    @Override
    public int insertAiImageTask(AiImageTask aiImageTask) {
        return aiImageTaskMapper.insertAiImageTask(aiImageTask);
    }

    @Override
    public int updateAiImageTask(AiImageTask aiImageTask) {
        return aiImageTaskMapper.updateAiImageTask(aiImageTask);
    }

    @Override
    public int deleteAiImageTaskById(Long taskId) {
        return aiImageTaskMapper.deleteAiImageTaskById(taskId);
    }

    @Override
    public int deleteAiImageTaskByIds(Long[] taskIds) {
        return aiImageTaskMapper.deleteAiImageTaskByIds(taskIds);
    }
}
```

- [ ] **Step 4: 创建 AiVideoTaskServiceImpl.java**

```java
package com.twelvet.ai.server.service.impl;

import com.twelvet.ai.api.domain.AiVideoTask;
import com.twelvet.ai.server.mapper.AiVideoTaskMapper;
import com.twelvet.ai.server.service.IAiVideoTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiVideoTaskServiceImpl implements IAiVideoTaskService {

    private final AiVideoTaskMapper aiVideoTaskMapper;

    public AiVideoTaskServiceImpl(AiVideoTaskMapper aiVideoTaskMapper) {
        this.aiVideoTaskMapper = aiVideoTaskMapper;
    }

    @Override
    public AiVideoTask selectAiVideoTaskById(Long taskId) {
        return aiVideoTaskMapper.selectAiVideoTaskById(taskId);
    }

    @Override
    public List<AiVideoTask> selectAiVideoTaskList(AiVideoTask aiVideoTask) {
        return aiVideoTaskMapper.selectAiVideoTaskList(aiVideoTask);
    }

    @Override
    public int insertAiVideoTask(AiVideoTask aiVideoTask) {
        return aiVideoTaskMapper.insertAiVideoTask(aiVideoTask);
    }

    @Override
    public int updateAiVideoTask(AiVideoTask aiVideoTask) {
        return aiVideoTaskMapper.updateAiVideoTask(aiVideoTask);
    }

    @Override
    public int deleteAiVideoTaskById(Long taskId) {
        return aiVideoTaskMapper.deleteAiVideoTaskById(taskId);
    }

    @Override
    public int deleteAiVideoTaskByIds(Long[] taskIds) {
        return aiVideoTaskMapper.deleteAiVideoTaskByIds(taskIds);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/
git commit -m "feat: add AiImageTask and AiVideoTask services"
```

---

## Task 5: 添加 WebClient 依赖和配置

**Files:**
- Modify: `twelvet-ai-server/pom.xml`
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/config/WebClientConfig.java`

**Interfaces:**
- Produces: `WebClient` Bean，用于 HTTP 调用 Agnes API

- [ ] **Step 1: 在 twelvet-ai-server/pom.xml 中添加 webflux 依赖**

在 `</dependencies>` 标签之前添加：

```xml
<!-- WebClient -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

- [ ] **Step 2: 创建 WebClientConfig.java**

```java
package com.twelvet.ai.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(300));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add twelvet-ai-server/pom.xml
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/config/WebClientConfig.java
git commit -m "feat: add WebClient dependency and configuration for Agnes API calls"
```

---

## Task 6: 实现 Agnes 图片生成核心服务

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesImageService.java`

**Interfaces:**
- Consumes: `AiImageTask` 实体、`AiModel` 配置（通过 `IAiModelService`）
- Produces: `generateImage(AiImageTask)` 方法，返回更新后的任务对象

- [ ] **Step 1: 创建 AgnesImageService.java**

```java
package com.twelvet.ai.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.twelvet.ai.api.domain.AiImageTask;
import com.twelvet.ai.api.domain.AiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AgnesImageService {

    private static final Logger logger = LoggerFactory.getLogger(AgnesImageService.class);

    private final WebClient webClient;
    private final IAiModelService aiModelService;

    public AgnesImageService(WebClient webClient, IAiModelService aiModelService) {
        this.webClient = webClient;
        this.aiModelService = aiModelService;
    }

    public AiImageTask generateImage(AiImageTask task) {
        try {
            AiModel model = aiModelService.selectAiModelById(task.getModelId());
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }

            String apiUrl = model.getBaseUrl() + "/v1/images/generations";
            String apiKey = model.getApiKey();

            Map<String, Object> requestBody = buildImageRequest(task);

            JsonNode response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(120));

            if (response != null && response.has("data") && response.get("data").size() > 0) {
                JsonNode firstResult = response.get("data").get(0);
                String resultUrl = null;
                if (firstResult.has("url") && !firstResult.get("url").isNull()) {
                    resultUrl = firstResult.get("url").asText();
                } else if (firstResult.has("b64_json") && !firstResult.get("b64_json").isNull()) {
                    resultUrl = firstResult.get("b64_json").asText();
                }
                task.setResultUrl(resultUrl);
                task.setStatus("completed");
            } else {
                task.setStatus("failed");
                task.setErrorMsg("Agnes API returned empty response");
            }
        } catch (Exception e) {
            logger.error("Image generation failed", e);
            task.setStatus("failed");
            task.setErrorMsg(e.getMessage());
        }
        return task;
    }

    private Map<String, Object> buildImageRequest(AiImageTask task) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", "agnes-image-2.1-flash");
        request.put("prompt", task.getPrompt());
        request.put("size", task.getSize());

        if ("img2img".equals(task.getGenType()) && task.getInputImage() != null && !task.getInputImage().isEmpty()) {
            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("image", new String[]{task.getInputImage()});
            if ("b64_json".equals(task.getResponseFormat())) {
                extraBody.put("response_format", "b64_json");
            } else {
                extraBody.put("response_format", "url");
            }
            request.put("extra_body", extraBody);
        } else {
            if ("b64_json".equals(task.getResponseFormat())) {
                request.put("return_base64", true);
            }
        }

        return request;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesImageService.java
git commit -m "feat: add Agnes image generation service"
```

---

## Task 7: 实现 Agnes 视频生成核心服务

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesVideoService.java`

**Interfaces:**
- Consumes: `AiVideoTask` 实体、`AiModel` 配置
- Produces: `createVideoTask(AiVideoTask)` 创建任务、`queryVideoStatus(String videoId, Long modelId)` 查询状态

- [ ] **Step 1: 创建 AgnesVideoService.java**

```java
package com.twelvet.ai.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.twelvet.ai.api.domain.AiModel;
import com.twelvet.ai.api.domain.AiVideoTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgnesVideoService {

    private static final Logger logger = LoggerFactory.getLogger(AgnesVideoService.class);

    private final WebClient webClient;
    private final IAiModelService aiModelService;

    public AgnesVideoService(WebClient webClient, IAiModelService aiModelService) {
        this.webClient = webClient;
        this.aiModelService = aiModelService;
    }

    public AiVideoTask createVideoTask(AiVideoTask task) {
        try {
            AiModel model = aiModelService.selectAiModelById(task.getModelId());
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }

            String apiUrl = model.getBaseUrl() + "/v1/videos";
            String apiKey = model.getApiKey();

            Map<String, Object> requestBody = buildVideoRequest(task);

            JsonNode response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));

            if (response != null) {
                if (response.has("task_id")) {
                    task.setAgnesTaskId(response.get("task_id").asText());
                }
                if (response.has("video_id")) {
                    task.setAgnesVideoId(response.get("video_id").asText());
                }
                if (response.has("status")) {
                    task.setStatus(response.get("status").asText());
                }
                if (response.has("seconds")) {
                    task.setVideoSeconds(response.get("seconds").asText());
                }
                if (response.has("size")) {
                    task.setVideoSize(response.get("size").asText());
                }
            }
        } catch (Exception e) {
            logger.error("Video task creation failed", e);
            task.setStatus("failed");
            task.setErrorMsg(e.getMessage());
        }
        return task;
    }

    public AiVideoTask queryVideoStatus(AiVideoTask task) {
        try {
            AiModel model = aiModelService.selectAiModelById(task.getModelId());
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }

            String apiUrl = model.getBaseUrl() + "/agnesapi?video_id=" + task.getAgnesVideoId();
            String apiKey = model.getApiKey();

            JsonNode response = webClient.get()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));

            if (response != null) {
                if (response.has("status")) {
                    task.setStatus(response.get("status").asText());
                }
                if (response.has("progress")) {
                    task.setProgress(response.get("progress").asInt());
                }
                if (response.has("seconds")) {
                    task.setVideoSeconds(response.get("seconds").asText());
                }
                if (response.has("size")) {
                    task.setVideoSize(response.get("size").asText());
                }
                if (response.has("remixed_from_video_id") && !response.get("remixed_from_video_id").isNull()) {
                    task.setResultUrl(response.get("remixed_from_video_id").asText());
                }
                if (response.has("error") && !response.get("error").isNull()) {
                    task.setErrorMsg(response.get("error").toString());
                }
            }
        } catch (Exception e) {
            logger.error("Video status query failed", e);
            task.setErrorMsg(e.getMessage());
        }
        return task;
    }

    private Map<String, Object> buildVideoRequest(AiVideoTask task) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", "agnes-video-v2.0");
        request.put("prompt", task.getPrompt());

        if (task.getWidth() != null) {
            request.put("width", task.getWidth());
        }
        if (task.getHeight() != null) {
            request.put("height", task.getHeight());
        }
        if (task.getNumFrames() != null) {
            request.put("num_frames", task.getNumFrames());
        }
        if (task.getFrameRate() != null) {
            request.put("frame_rate", task.getFrameRate());
        }
        if (task.getNegativePrompt() != null && !task.getNegativePrompt().isEmpty()) {
            request.put("negative_prompt", task.getNegativePrompt());
        }
        if (task.getSeed() != null) {
            request.put("seed", task.getSeed());
        }

        // Handle input images
        if (task.getInputImages() != null && !task.getInputImages().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<String> images = mapper.readValue(task.getInputImages(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));

                if (images.size() == 1) {
                    // Single image: use top-level "image"
                    request.put("image", images.get(0));
                } else {
                    // Multiple images: use extra_body.image
                    Map<String, Object> extraBody = new HashMap<>();
                    extraBody.put("image", images);
                    if ("keyframes".equals(task.getGenType())) {
                        extraBody.put("mode", "keyframes");
                    }
                    request.put("extra_body", extraBody);
                }
            } catch (Exception e) {
                logger.error("Failed to parse input images", e);
            }
        }

        return request;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/service/AgnesVideoService.java
git commit -m "feat: add Agnes video generation service"
```

---

## Task 8: 实现图片生成 Controller

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiImageController.java`

**Interfaces:**
- Consumes: `IAiImageTaskService`、`AgnesImageService`
- Produces: `/ai/image/*` REST API

- [ ] **Step 1: 创建 AiImageController.java**

```java
package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.api.domain.AiImageTask;
import com.twelvet.ai.server.service.AgnesImageService;
import com.twelvet.ai.server.service.IAiImageTaskService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.framework.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(description = "AiImageController", name = "AI图片生成")
@RestController
@RequestMapping("/ai/image")
public class AiImageController extends TWTController {

    private final IAiImageTaskService aiImageTaskService;
    private final AgnesImageService agnesImageService;

    public AiImageController(IAiImageTaskService aiImageTaskService, AgnesImageService agnesImageService) {
        this.aiImageTaskService = aiImageTaskService;
        this.agnesImageService = agnesImageService;
    }

    @Operation(summary = "生成图片")
    @Log(service = "AI图片生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    @SaCheckPermission("ai:image:generate")
    public JsonResult<AiImageTask> generate(@Validated @RequestBody AiImageTask aiImageTask) {
        aiImageTask.setCreateBy(SecurityUtils.getUsername());
        aiImageTask.setStatus("pending");
        aiImageTaskService.insertAiImageTask(aiImageTask);

        AiImageTask result = agnesImageService.generateImage(aiImageTask);
        aiImageTaskService.updateAiImageTask(result);

        return JsonResult.success(result);
    }

    @Operation(summary = "获取图片生成任务列表")
    @GetMapping("/pageQuery")
    @SaCheckPermission("ai:image:list")
    public JsonResult<TableDataInfo<AiImageTask>> pageQuery(AiImageTask aiImageTask) {
        PageUtils.startPage();
        List<AiImageTask> list = aiImageTaskService.selectAiImageTaskList(aiImageTask);
        return JsonResult.success(PageUtils.getDataTable(list));
    }

    @Operation(summary = "根据任务ID获取详情")
    @GetMapping("/{taskId}")
    @SaCheckPermission("ai:image:query")
    public JsonResult<AiImageTask> getById(@PathVariable Long taskId) {
        return JsonResult.success(aiImageTaskService.selectAiImageTaskById(taskId));
    }

    @Operation(summary = "删除图片生成任务")
    @Log(service = "AI图片生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    @SaCheckPermission("ai:image:remove")
    public JsonResult<String> remove(@PathVariable Long[] taskIds) {
        return json(aiImageTaskService.deleteAiImageTaskByIds(taskIds));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiImageController.java
git commit -m "feat: add AiImageController with generate, pageQuery, get, delete endpoints"
```

---

## Task 9: 实现视频生成 Controller

**Files:**
- Create: `twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiVideoController.java`

**Interfaces:**
- Consumes: `IAiVideoTaskService`、`AgnesVideoService`
- Produces: `/ai/video/*` REST API

- [ ] **Step 1: 创建 AiVideoController.java**

```java
package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.twelvet.ai.api.domain.AiVideoTask;
import com.twelvet.ai.server.service.AgnesVideoService;
import com.twelvet.ai.server.service.IAiVideoTaskService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.framework.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(description = "AiVideoController", name = "AI视频生成")
@RestController
@RequestMapping("/ai/video")
public class AiVideoController extends TWTController {

    private final IAiVideoTaskService aiVideoTaskService;
    private final AgnesVideoService agnesVideoService;

    public AiVideoController(IAiVideoTaskService aiVideoTaskService, AgnesVideoService agnesVideoService) {
        this.aiVideoTaskService = aiVideoTaskService;
        this.agnesVideoService = agnesVideoService;
    }

    @Operation(summary = "生成视频")
    @Log(service = "AI视频生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    @SaCheckPermission("ai:video:generate")
    public JsonResult<AiVideoTask> generate(@Validated @RequestBody AiVideoTask aiVideoTask) {
        aiVideoTask.setCreateBy(SecurityUtils.getUsername());
        aiVideoTask.setStatus("queued");
        aiVideoTask.setProgress(0);
        aiVideoTaskService.insertAiVideoTask(aiVideoTask);

        AiVideoTask result = agnesVideoService.createVideoTask(aiVideoTask);
        aiVideoTaskService.updateAiVideoTask(result);

        return JsonResult.success(result);
    }

    @Operation(summary = "查询视频生成任务状态")
    @GetMapping("/status/{taskId}")
    @SaCheckPermission("ai:video:query")
    public JsonResult<AiVideoTask> queryStatus(@PathVariable Long taskId) {
        AiVideoTask task = aiVideoTaskService.selectAiVideoTaskById(taskId);
        if (task == null) {
            return JsonResult.error("任务不存在");
        }
        if (task.getAgnesVideoId() == null || task.getAgnesVideoId().isEmpty()) {
            return JsonResult.success(task);
        }

        AiVideoTask updated = agnesVideoService.queryVideoStatus(task);
        aiVideoTaskService.updateAiVideoTask(updated);

        return JsonResult.success(updated);
    }

    @Operation(summary = "获取视频生成任务列表")
    @GetMapping("/pageQuery")
    @SaCheckPermission("ai:video:list")
    public JsonResult<TableDataInfo<AiVideoTask>> pageQuery(AiVideoTask aiVideoTask) {
        PageUtils.startPage();
        List<AiVideoTask> list = aiVideoTaskService.selectAiVideoTaskList(aiVideoTask);
        return JsonResult.success(PageUtils.getDataTable(list));
    }

    @Operation(summary = "根据任务ID获取详情")
    @GetMapping("/{taskId}")
    @SaCheckPermission("ai:video:query")
    public JsonResult<AiVideoTask> getById(@PathVariable Long taskId) {
        return JsonResult.success(aiVideoTaskService.selectAiVideoTaskById(taskId));
    }

    @Operation(summary = "删除视频生成任务")
    @Log(service = "AI视频生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    @SaCheckPermission("ai:video:remove")
    public JsonResult<String> remove(@PathVariable Long[] taskIds) {
        return json(aiVideoTaskService.deleteAiVideoTaskByIds(taskIds));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ai-server/src/main/java/com/twelvet/ai/server/controller/AiVideoController.java
git commit -m "feat: add AiVideoController with generate, status, pageQuery, get, delete endpoints"
```

---

## Task 10: 前端 — 新增路由

**Files:**
- Modify: `twelvet-ui-master/config/routes.ts`

- [ ] **Step 1: 在 routes.ts 的 AI 路由组中新增图片生成和视频生成**

找到 `config/routes.ts` 中 AI 路由组的 `routes` 数组，在 `aiShoppingGuide` 之后添加：

```typescript
{
    title: '图片生成',
    name: 'aiImage',
    icon: 'icon-ai-image',
    path: '/ai/image',
    component: './AI/Image',
},
{
    title: '视频生成',
    name: 'aiVideo',
    icon: 'icon-ai-video',
    path: '/ai/video',
    component: './AI/Video',
},
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ui-master/config/routes.ts
git commit -m "feat: add /ai/image and /ai/video routes"
```

---

## Task 11: 前端 — 图片生成页面 Service

**Files:**
- Create: `twelvet-ui-master/src/pages/AI/Image/service.ts`

- [ ] **Step 1: 创建 service.ts**

```typescript
import { request } from '@umijs/max';

const controller = '/ai/image';

export async function generateImage(data: { [key: string]: any }) {
    return request(`${controller}/generate`, { method: 'post', data });
}

export async function pageQueryImageTask(query: { [key: string]: any }) {
    return request(`${controller}/pageQuery`, { method: 'get', params: query });
}

export async function getImageTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, { method: 'get' });
}

export async function delImageTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, { method: 'delete' });
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Image/service.ts
git commit -m "feat: add image generation service"
```

---

## Task 12: 前端 — 视频生成页面 Service

**Files:**
- Create: `twelvet-ui-master/src/pages/AI/Video/service.ts`

- [ ] **Step 1: 创建 service.ts**

```typescript
import { request } from '@umijs/max';

const controller = '/ai/video';

export async function generateVideo(data: { [key: string]: any }) {
    return request(`${controller}/generate`, { method: 'post', data });
}

export async function queryVideoStatus(taskId: string | number) {
    return request(`${controller}/status/${taskId}`, { method: 'get' });
}

export async function pageQueryVideoTask(query: { [key: string]: any }) {
    return request(`${controller}/pageQuery`, { method: 'get', params: query });
}

export async function getVideoTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, { method: 'get' });
}

export async function delVideoTask(taskId: string | number) {
    return request(`${controller}/${taskId}`, { method: 'delete' });
}
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/service.ts
git commit -m "feat: add video generation service"
```

---

## Task 13: 前端 — 图片生成页面

**Files:**
- Create: `twelvet-ui-master/src/pages/AI/Image/index.tsx`

**Interfaces:**
- Consumes: `generateImage`、`pageQueryImageTask`、`getImageTask`、`delImageTask`

- [ ] **Step 1: 创建 index.tsx**

```tsx
import React, { useRef, useState } from 'react';
import { Button, Card, Col, Form, Input, message, Radio, Row, Select, Space, Tabs } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { generateImage, pageQueryImageTask, delImageTask } from './service';
import TWT, { proTableConfigs } from '@/setting';

const { TextArea } = Input;
const { TabPane } = Tabs;

const AiImagePage: React.FC = () => {
    const [form] = Form.useForm();
    const acForm = useRef<ActionType>();
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState<string>('');
    const [genType, setGenType] = useState<string>('text2img');

    const handleGenerate = async () => {
        try {
            const values = await form.validateFields();
            setLoading(true);
            const { code, data, msg } = await generateImage({
                ...values,
                genType,
            });
            if (code === 200) {
                message.success('生成成功');
                setResult(data.resultUrl || '');
                acForm.current?.reload();
            } else {
                message.error(msg || '生成失败');
            }
        } catch (e) {
            message.error('生成失败');
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (taskId: number) => {
        const { code, msg } = await delImageTask(taskId);
        if (code === 200) {
            message.success(msg);
            acForm.current?.reload();
        } else {
            message.error(msg);
        }
    };

    const columns: ProColumns<any>[] = [
        {
            title: 'ID',
            dataIndex: 'taskId',
            width: 80,
        },
        {
            title: '提示词',
            dataIndex: 'prompt',
            ellipsis: true,
        },
        {
            title: '类型',
            dataIndex: 'genType',
            width: 100,
            valueEnum: {
                text2img: { text: '文生图' },
                img2img: { text: '图生图' },
            },
        },
        {
            title: '尺寸',
            dataIndex: 'size',
            width: 100,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            valueEnum: {
                pending: { text: '处理中', status: 'processing' },
                completed: { text: '已完成', status: 'success' },
                failed: { text: '失败', status: 'error' },
            },
        },
        {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 180,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 120,
            valueType: 'option',
            render: (_, row) => (
                <Space>
                    {row.resultUrl && (
                        <a href={row.resultUrl} target="_blank" rel="noreferrer">
                            查看
                        </a>
                    )}
                    <a onClick={() => handleDelete(row.taskId)}>删除</a>
                </Space>
            ),
        },
    ];

    return (
        <Row gutter={16}>
            <Col span={8}>
                <Card title="图片生成" bordered={false}>
                    <Form form={form} layout="vertical">
                        <Form.Item label="生成类型">
                            <Radio.Group value={genType} onChange={(e) => setGenType(e.target.value)}>
                                <Radio.Button value="text2img">文生图</Radio.Button>
                                <Radio.Button value="img2img">图生图</Radio.Button>
                            </Radio.Group>
                        </Form.Item>

                        {genType === 'img2img' && (
                            <Form.Item
                                label="输入图片"
                                name="inputImage"
                                rules={[{ required: true, message: '请输入输入图片URL' }]}
                            >
                                <Input placeholder="请输入公网可访问的图片URL" />
                            </Form.Item>
                        )}

                        <Form.Item
                            label="提示词"
                            name="prompt"
                            rules={[{ required: true, message: '请输入提示词' }]}
                        >
                            <TextArea rows={4} placeholder="描述你想要生成的图片..." />
                        </Form.Item>

                        <Form.Item label="输出尺寸" name="size" initialValue="1024x768">
                            <Select>
                                <Select.Option value="1024x768">1024x768</Select.Option>
                                <Select.Option value="768x1024">768x1024</Select.Option>
                                <Select.Option value="1024x1024">1024x1024</Select.Option>
                                <Select.Option value="512x512">512x512</Select.Option>
                            </Select>
                        </Form.Item>

                        <Form.Item label="输出格式" name="responseFormat" initialValue="url">
                            <Radio.Group>
                                <Radio value="url">URL</Radio>
                                <Radio value="b64_json">Base64</Radio>
                            </Radio.Group>
                        </Form.Item>

                        <Form.Item>
                            <Button type="primary" onClick={handleGenerate} loading={loading} block>
                                {loading ? '生成中...' : '生成图片'}
                            </Button>
                        </Form.Item>
                    </Form>
                </Card>
            </Col>

            <Col span={16}>
                <Card title="生成结果" bordered={false}>
                    {result ? (
                        <div style={{ textAlign: 'center' }}>
                            <img src={result} alt="generated" style={{ maxWidth: '100%', maxHeight: 400 }} />
                            <div style={{ marginTop: 16 }}>
                                <Button type="primary" href={result} target="_blank">
                                    下载图片
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <div style={{ textAlign: 'center', color: '#999', padding: '40px 0' }}>
                            暂无生成结果
                        </div>
                    )}
                </Card>

                <Card title="历史记录" bordered={false} style={{ marginTop: 16 }}>
                    <ProTable
                        {...proTableConfigs}
                        actionRef={acForm}
                        rowKey="taskId"
                        columns={columns}
                        request={async (params) => {
                            const { data } = await pageQueryImageTask(params);
                            const { records, total } = data;
                            return Promise.resolve({
                                data: records,
                                success: true,
                                total,
                            });
                        }}
                        pagination={{
                            showSizeChanger: true,
                            defaultPageSize: 10,
                        }}
                    />
                </Card>
            </Col>
        </Row>
    );
};

export default AiImagePage;
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Image/index.tsx
git commit -m "feat: add AI image generation page"
```

---

## Task 14: 前端 — 视频生成页面

**Files:**
- Create: `twelvet-ui-master/src/pages/AI/Video/index.tsx`

**Interfaces:**
- Consumes: `generateVideo`、`queryVideoStatus`、`pageQueryVideoTask`、`getVideoTask`、`delVideoTask`

- [ ] **Step 1: 创建 index.tsx**

```tsx
import React, { useRef, useState, useEffect } from 'react';
import { Button, Card, Col, Form, Input, message, Progress, Row, Select, Space, Tabs } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { generateVideo, queryVideoStatus, pageQueryVideoTask, delVideoTask } from './service';
import { proTableConfigs } from '@/setting';

const { TextArea } = Input;
const { TabPane } = Tabs;

const AiVideoPage: React.FC = () => {
    const [form] = Form.useForm();
    const acForm = useRef<ActionType>();
    const [loading, setLoading] = useState(false);
    const [currentTask, setCurrentTask] = useState<any>(null);
    const [pollingInterval, setPollingInterval] = useState<NodeJS.Timeout | null>(null);
    const [genType, setGenType] = useState<string>('text2vid');

    const handleGenerate = async () => {
        try {
            const values = await form.validateFields();
            setLoading(true);
            const { code, data, msg } = await generateVideo({
                ...values,
                genType,
            });
            if (code === 200) {
                message.success('任务已提交');
                setCurrentTask(data);
                startPolling(data.taskId);
                acForm.current?.reload();
            } else {
                message.error(msg || '提交失败');
            }
        } catch (e) {
            message.error('提交失败');
        } finally {
            setLoading(false);
        }
    };

    const startPolling = (taskId: number) => {
        if (pollingInterval) {
            clearInterval(pollingInterval);
        }
        const interval = setInterval(async () => {
            const { code, data } = await queryVideoStatus(taskId);
            if (code === 200) {
                setCurrentTask(data);
                if (data.status === 'completed' || data.status === 'failed') {
                    clearInterval(interval);
                    setPollingInterval(null);
                    acForm.current?.reload();
                }
            }
        }, 5000);
        setPollingInterval(interval);
    };

    useEffect(() => {
        return () => {
            if (pollingInterval) {
                clearInterval(pollingInterval);
            }
        };
    }, [pollingInterval]);

    const handleDelete = async (taskId: number) => {
        const { code, msg } = await delVideoTask(taskId);
        if (code === 200) {
            message.success(msg);
            acForm.current?.reload();
        } else {
            message.error(msg);
        }
    };

    const columns: ProColumns<any>[] = [
        {
            title: 'ID',
            dataIndex: 'taskId',
            width: 80,
        },
        {
            title: '提示词',
            dataIndex: 'prompt',
            ellipsis: true,
        },
        {
            title: '类型',
            dataIndex: 'genType',
            width: 120,
            valueEnum: {
                text2vid: { text: '文生视频' },
                img2vid: { text: '图生视频' },
                multi2vid: { text: '多图视频' },
                keyframes: { text: '关键帧动画' },
            },
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            valueEnum: {
                queued: { text: '排队中', status: 'default' },
                in_progress: { text: '生成中', status: 'processing' },
                completed: { text: '已完成', status: 'success' },
                failed: { text: '失败', status: 'error' },
            },
        },
        {
            title: '进度',
            dataIndex: 'progress',
            width: 100,
            render: (_, row) => (
                <Progress percent={row.progress || 0} size="small" />
            ),
        },
        {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 180,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 120,
            valueType: 'option',
            render: (_, row) => (
                <Space>
                    {row.resultUrl && (
                        <a href={row.resultUrl} target="_blank" rel="noreferrer">
                            查看
                        </a>
                    )}
                    <a onClick={() => handleDelete(row.taskId)}>删除</a>
                </Space>
            ),
        },
    ];

    return (
        <Row gutter={16}>
            <Col span={8}>
                <Card title="视频生成" bordered={false}>
                    <Form form={form} layout="vertical">
                        <Form.Item label="生成类型">
                            <Select value={genType} onChange={setGenType}>
                                <Select.Option value="text2vid">文生视频</Select.Option>
                                <Select.Option value="img2vid">图生视频</Select.Option>
                                <Select.Option value="multi2vid">多图视频</Select.Option>
                                <Select.Option value="keyframes">关键帧动画</Select.Option>
                            </Select>
                        </Form.Item>

                        {(genType === 'img2vid' || genType === 'multi2vid' || genType === 'keyframes') && (
                            <Form.Item
                                label="输入图片"
                                name="inputImages"
                                rules={[{ required: true, message: '请输入输入图片URL（JSON数组）' }]}
                            >
                                <TextArea
                                    rows={2}
                                    placeholder='["https://example.com/img1.png"]'
                                />
                            </Form.Item>
                        )}

                        <Form.Item
                            label="提示词"
                            name="prompt"
                            rules={[{ required: true, message: '请输入提示词' }]}
                        >
                            <TextArea rows={4} placeholder="描述你想要生成的视频..." />
                        </Form.Item>

                        <Form.Item label="宽度" name="width" initialValue={1152}>
                            <Input type="number" />
                        </Form.Item>

                        <Form.Item label="高度" name="height" initialValue={768}>
                            <Input type="number" />
                        </Form.Item>

                        <Form.Item label="帧数" name="numFrames" initialValue={121}>
                            <Input type="number" />
                        </Form.Item>

                        <Form.Item label="帧率" name="frameRate" initialValue={24}>
                            <Input type="number" />
                        </Form.Item>

                        <Form.Item label="负向提示词" name="negativePrompt">
                            <TextArea rows={2} placeholder="描述需要避免的内容..." />
                        </Form.Item>

                        <Form.Item>
                            <Button type="primary" onClick={handleGenerate} loading={loading} block>
                                {loading ? '提交中...' : '生成视频'}
                            </Button>
                        </Form.Item>
                    </Form>
                </Card>
            </Col>

            <Col span={16}>
                <Card title="生成结果" bordered={false}>
                    {currentTask ? (
                        <div>
                            <p><strong>任务ID:</strong> {currentTask.taskId}</p>
                            <p><strong>状态:</strong> {currentTask.status}</p>
                            <p><strong>进度:</strong></p>
                            <Progress percent={currentTask.progress || 0} />
                            {currentTask.resultUrl && (
                                <div style={{ marginTop: 16 }}>
                                    <video
                                        src={currentTask.resultUrl}
                                        controls
                                        style={{ maxWidth: '100%', maxHeight: 400 }}
                                    />
                                    <div style={{ marginTop: 16 }}>
                                        <Button type="primary" href={currentTask.resultUrl} target="_blank">
                                            下载视频
                                        </Button>
                                    </div>
                                </div>
                            )}
                            {currentTask.errorMsg && (
                                <p style={{ color: 'red' }}>错误: {currentTask.errorMsg}</p>
                            )}
                        </div>
                    ) : (
                        <div style={{ textAlign: 'center', color: '#999', padding: '40px 0' }}>
                            暂无生成结果
                        </div>
                    )}
                </Card>

                <Card title="历史记录" bordered={false} style={{ marginTop: 16 }}>
                    <ProTable
                        {...proTableConfigs}
                        actionRef={acForm}
                        rowKey="taskId"
                        columns={columns}
                        request={async (params) => {
                            const { data } = await pageQueryVideoTask(params);
                            const { records, total } = data;
                            return Promise.resolve({
                                data: records,
                                success: true,
                                total,
                            });
                        }}
                        pagination={{
                            showSizeChanger: true,
                            defaultPageSize: 10,
                        }}
                    />
                </Card>
            </Col>
        </Row>
    );
};

export default AiVideoPage;
```

- [ ] **Step 2: Commit**

```bash
git add twelvet-ui-master/src/pages/AI/Video/index.tsx
git commit -m "feat: add AI video generation page"
```

---

## Task 15: 编译验证与收尾

**Files:**
- 无新增文件

- [ ] **Step 1: 编译后端**

```bash
cd twelvet-fast
mvn clean compile -pl twelvet-ai-server -am
```

- [ ] **Step 2: 编译前端**

```bash
cd twelvet-ui-master
yarn install
yarn build
```

- [ ] **Step 3: Commit 所有变更**

```bash
git add -A
git commit -m "feat: complete Agnes AI image/video generation integration"
```
