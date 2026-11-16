# AI 提示词智能增强 — 设计文档

> **日期**: 2026-08-11
> **状态**: Approved
> **关联功能**: AI 图片生成 / AI 视频生成

---

## 1. 需求概述

用户在图片/视频生成时，给出的提示词可能非常简单（如"一只猫"），导致生成效果不佳。系统应根据用户意图自动扩展提示词，补充风格、光影、构图、材质等细节，提升生成质量。

---

## 2. 架构

### 2.1 调用链路

```
前端 → Java Controller (AiImageController / AiVideoController, 透传)
      → AiServiceClient (REST, camelCase → snake_case)
        → Python FastAPI (routers/image.py / routers/video.py)
          → [NEW] prompt_enhancer.enhance()   ← LLM 智能扩展
          → 创建任务记录（存储增强后的 prompt）
          → 发起 Agnes 生成任务（background task）
```

**核心原则**: 增强逻辑集中在 Python 侧（`twelvet-ai-service`），Java 侧零改动。

### 2.2 组件划分

| 组件 | 路径 | 职责 |
|------|------|------|
| `prompt_enhancer.py` | `app/services/` | 核心增强服务，判断是否需要增强 + 调用 LLM |
| `config.py` | `app/config.py` | 新增增强相关配置项 |
| `image.py` | `app/routers/image.py` | 图片生成前插入增强调用 |
| `video.py` | `app/routers/video.py` | 视频生成前插入增强调用 |

---

## 3. 设计细节

### 3.1 `app/services/prompt_enhancer.py` — 核心服务

**接口签名:**

```python
def enhance_prompt(raw_prompt: str) -> tuple[str, bool]:
    """
    根据原始提示词判断是否需要智能增强。

    Returns:
        (enhanced_prompt, is_enhanced)
        - is_enhanced=True:  返回 LLM 扩展后的提示词
        - is_enhanced=False: 返回原始提示词（未增强或增强失败降级）
    """
```

**增强判断逻辑:**

1. 若 `ai_prompt_enhance_enabled = False` → 跳过，返回 `(raw_prompt, False)`
2. 若 `raw_prompt` 长度为空或 `None` → 返回 `(raw_prompt, False)`
3. 若 `len(raw_prompt) >= ai_prompt_min_length`（默认 10）→ 返回 `(raw_prompt, False)`
4. 其余情况 → 触发 LLM 增强

**LLM 增强逻辑:**

- 使用 `langchain_openai.ChatOpenAI`，复用已有 `_get_llm` 模式
- 模型: `ai_prompt_enhance_model`（非空时专用），否则复用 `ai_model`
- 温度: `ai_prompt_enhance_temperature`（默认 0.7）
- SYSTEM_PROMPT 指导方向:
  - 基于用户描述，扩展为详尽的提示词
  - 补充: 主体细节、风格、光影、构图、色彩、氛围、材质、分辨率等
  - 保留用户原始意图，不得偏离主题或添加无关内容
  - 输出纯文本提示词，不含任何额外格式、引号或解释
- 使用 `ainvoke` 异步调用

**降级策略 (异常安全):**

| 失败场景 | 行为 |
|----------|------|
| 未配置 AI API key | 返回原始 prompt + WARN 日志 |
| LLM 超时 / 网络异常 | 返回原始 prompt + WARN 日志 |
| LLM 返回空内容 | 返回原始 prompt + WARN 日志 |
| 任何其他异常 | 返回原始 prompt + ERROR 日志 |

**降级铁律**: LLM 增强失败**绝不**阻断正常的图片/视频生成流程。

### 3.2 `app/config.py` — 新增配置项

```python
# 提示词智能增强
ai_prompt_enhance_enabled: bool = True          # 增强功能全局开关
ai_prompt_enhance_model: str = ""               # 增强专用模型（空=复用 ai_model）
ai_prompt_enhance_temperature: float = 0.7      # 增强场景温度，增加多样性
ai_prompt_min_length: int = 10                  # 原始提示词低于此长度时触发增强
```

配置来源: `.env` 文件（环境变量前缀无，按 Settings 默认规则）。

### 3.3 `routers/image.py` — 修改点

在 `generate_image()` 中，创建 `AiImageTask` 记录之前插入增强调用:

```python
enhanced_prompt, _ = enhance_prompt(body.prompt)
```

然后将 `enhanced_prompt` 传入任务记录和后续 Agnes 调用。

### 3.4 `routers/video.py` — 修改点

在 `generate_video()` 中，创建 `AiVideoTask` 记录之前插入增强调用:

```python
enhanced_prompt, _ = enhance_prompt(body.prompt)
```

然后将 `enhanced_prompt` 传入任务记录和后续 Agnes 调用。

---

## 4. 数据流示例

### 示例 1: 简短提示词 → 触发增强

```
输入:  prompt = "一只猫"
      → len("一只猫") = 3 < 10 → 触发增强
      → LLM 输出: "一只毛茸茸的橘猫慵懒地坐在窗台上，
                   柔和的自然光透过纱帘洒下，温暖的午后氛围，
                   超写实摄影风格，细腻毛发细节，浅景深虚化背景"
      → task.prompt = 增强后文本
      → Agnes 按增强后提示词生成
```

### 示例 2: 详细提示词 → 不增强

```
输入:  prompt = "超写实水墨山水画，远山如黛，晨雾缭绕，留白构图"
      → len = 24 ≥ 10 → 不增强
      → task.prompt = 原始文本
      → Agnes 按原始提示词生成
```

### 示例 3: LLM 调用失败 → 降级

```
输入:  prompt = "天空"
      → len("天空") = 2 < 10 → 触发增强
      → LLM 调用超时
      → 降级: task.prompt = "天空"（原始文本）
      → Agnes 按原始提示词生成（流程不中断）
```

---

## 5. 错误处理

| 层次 | 错误类型 | 处理方式 |
|------|---------|---------|
| 配置层 | 配置项缺失/无效 | 使用默认值（Pyrantic Settings 自动处理） |
| 增强层 | LLM API 未配置 | 跳过增强，返回原始 prompt，WARN 日志 |
| 增强层 | LLM 调用超时/异常 | 降级原始 prompt，ERROR 日志，不阻断流程 |
| 路由层 | 模型未找到 | 原有逻辑不变（返回 500 提示配置模型） |

---

## 6. 非目标 (Out of Scope)

- 不修改数据模型（不新增数据库字段）
- 不修改 Java 层代码
- 不修改前端页面
- 不修改 Agnes API 调用逻辑
- 不增加风格模板选择（用户可在 prompt 中直接指定风格）
- 不增加提示词增强历史记录

---

## 7. 测试策略

| 测试项 | 方式 |
|--------|------|
| 简短 prompt 触发增强 | 单元测试 mock LLM，验证返回增强文本 |
| 详细 prompt 不触发增强 | 单元测试 mock LLM，验证 LLM 未被调用 |
| LLM 异常降级 | 单元测试 mock LLM 抛出异常，验证返回原始 prompt |
| 增强开关关闭 | 单元测试 mock config，验证跳过增强 |
| 图片生成完整链路 | 集成测试 mock Agnes，验证任务记录含增强后 prompt |
| 视频生成完整链路 | 集成测试 mock Agnes，验证任务记录含增强后 prompt |

---

## 8. 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `twelvet-ai-service/app/services/prompt_enhancer.py` | 提示词增强核心服务 |
| 修改 | `twelvet-ai-service/app/config.py` | 新增 4 个配置项 |
| 修改 | `twelvet-ai-service/app/routers/image.py` | 图片生成前插入增强调用 |
| 修改 | `twelvet-ai-service/app/routers/video.py` | 视频生成前插入增强调用 |
| 新增 | `twelvet-ai-service/tests/test_prompt_enhance.py` | 增强服务单元测试 |