# AI 提示词智能增强 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 AI 图片/视频生成流程中，当用户提示词过短（< 10 字）时，自动调用 LLM 扩展为详尽提示词，提升生成质量。

**Architecture:** Python 侧新增 `prompt_enhancer.py` 服务，在 `image.py` / `video.py` 创建任务记录前调用。Java 层透传零改动。LLM 调用失败降级为原始 prompt，绝不阻断生成流程。

**Tech Stack:** Python 3.11+, FastAPI, Pydantic Settings, langchain_openai.ChatOpenAI, pytest, pytest-asyncio

## Global Constraints

- 增强逻辑仅存在于 `twelvet-ai-service`（Python 侧），Java 层零改动
- LLM 增强失败必须降级原始 prompt，记录日志，**绝不阻断**图片/视频生成流程
- `enhance_prompt` 返回 `(str, bool)` 元组，签名固定
- 测试使用 pytest + pytest-asyncio，mock LLM 避免真实 API 调用
- 配置项使用 Pydantic Settings（`.env` 环境变量覆盖），均有安全默认值
- SYSTEM_PROMPT 指导 LLM 输出纯提示词文本，不含任何额外格式

---

### Task 1: 新增提示词增强配置项

**Files:**
- Modify: `twelvet-ai-service/app/config.py`

**Interfaces:**
- Produces: 4 个新 Settings 字段供 `prompt_enhancer` 消费

- [ ] **Step 1: 修改 config.py，新增 4 个配置字段**

在 `Settings` 类中 `doc_download_timeout` 之后添加：

```python
    # 提示词智能增强
    ai_prompt_enhance_enabled: bool = True
    ai_prompt_enhance_model: str = ""
    ai_prompt_enhance_temperature: float = 0.7
    ai_prompt_min_length: int = 10
```

完整修改后 `Settings` 类应为：

```python
class Settings(BaseSettings):
    app_host: str = "0.0.0.0"
    app_port: int = 8001
    database_url: str = "mysql+aiomysql://root:123456@localhost:3306/twelvet-fast"
    ai_base_url: str = "https://api.deepseek.com"
    ai_api_key: str = ""
    ai_model: str = "deepseek-chat"
    ai_temperature: float = 0.3
    agnes_connect_timeout: int = 10
    agnes_read_timeout: int = 600
    # RAG 配置
    embedding_base_url: str = "https://api.siliconflow.cn/v1"
    embedding_api_key: str = ""
    embedding_model: str = "BAAI/bge-m3"
    faiss_index_path: str = "./data/faiss_indexes"
    # 文档解析
    static_base_url: str = "https://static.twelvet.cn"
    doc_download_timeout: int = 30

    # 提示词智能增强
    ai_prompt_enhance_enabled: bool = True
    ai_prompt_enhance_model: str = ""
    ai_prompt_enhance_temperature: float = 0.7
    ai_prompt_min_length: int = 10

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}
```

- [ ] **Step 2: 验证配置项默认值**

```bash
cd twelvet-ai-service && python -c "from app.config import settings; print(settings.ai_prompt_enhance_enabled, settings.ai_prompt_enhance_model, settings.ai_prompt_enhance_temperature, settings.ai_prompt_min_length)"
```

Expected: `True  0.7 10`

---

### Task 2: 新增提示词增强核心服务 + 单元测试

**Files:**
- Create: `twelvet-ai-service/app/services/prompt_enhancer.py`
- Create: `twelvet-ai-service/tests/test_prompt_enhance.py`

**Interfaces:**
- Consumes: `app.config.settings` (ai_prompt_enhance_enabled / ai_prompt_enhance_model / ai_prompt_enhance_temperature / ai_prompt_min_length / ai_base_url / ai_api_key / ai_model)
- Produces: `enhance_prompt(raw_prompt: str) -> tuple[str, bool]`

- [ ] **Step 1: 编写 prompt_enhancer.py**

```python
"""AI 提示词智能增强服务。

当用户给出的提示词过短（< ai_prompt_min_length）时，调用 LLM 自动扩展
为详尽提示词，补充风格、光影、构图、材质等细节，提升图片/视频生成质量。

异常安全：LLM 调用失败时降级为原始 prompt，绝不阻断生成流程。
"""
import logging
from typing import Tuple

from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import settings

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "你是一个专业的 AI 绘画/视频提示词扩展专家。用户会给你一个简短的描述，"
    "你需要将其扩展为一个详尽、专业的高质量提示词。\n"
    "扩展要求：\n"
    "1. 基于用户描述，补充以下方面的细节（根据内容合理选择）：\n"
    "   - 主体细节：具体的品种、年龄、姿态、表情、服饰等\n"
    "   - 风格：如超写实摄影、动漫风格、3D 渲染、水墨画、赛博朋克等\n"
    "   - 光影：如柔和自然光、金色夕阳、戏剧性布光、逆光剪影等\n"
    "   - 构图：如浅景深、黄金分割、对称构图、低角度仰拍等\n"
    "   - 色彩：如暖色调、冷色调、高饱和度、莫兰迪色系等\n"
    "   - 氛围：如宁静祥和、神秘诡异、温馨浪漫、史诗宏大等\n"
    "   - 材质/质感：如细腻毛发、金属光泽、丝绒质感、玻璃通透等\n"
    "   - 分辨率/画质：如 8K 超高清、HDR、专业摄影级画质等\n"
    "2. 必须保留用户原始意图，不得偏离主题或添加无关内容\n"
    "3. 若用户描述中已包含风格/光影等细节，不要重复，而是适当丰富\n"
    "4. 输出纯提示词文本，不要使用引号、标题、编号或任何解释性文字\n"
    "5. 提示词应控制在 100-300 字以内，避免过长\n"
    "6. 如果用户描述本身就是高质量详细提示词，直接原样输出，不要画蛇添足"
)


async def _get_enhance_llm() -> ChatOpenAI:
    """构建用于提示词增强的 LLM 实例。"""
    model_name = settings.ai_prompt_enhance_model or settings.ai_model
    return ChatOpenAI(
        base_url=settings.ai_base_url,
        api_key=settings.ai_api_key or "sk-placeholder",
        model=model_name,
        temperature=settings.ai_prompt_enhance_temperature,
    )


async def enhance_prompt(raw_prompt: str) -> Tuple[str, bool]:
    """根据原始提示词判断是否需要智能增强。

    Returns:
        (enhanced_prompt, is_enhanced)
        - is_enhanced=True:  返回 LLM 扩展后的提示词
        - is_enhanced=False: 返回原始提示词（未增强或增强失败降级）
    """
    # 空提示词直接返回，不做增强
    if not raw_prompt or not raw_prompt.strip():
        return raw_prompt or "", False

    # 增强功能开关关闭
    if not settings.ai_prompt_enhance_enabled:
        logger.debug("Prompt enhancement disabled, returning raw prompt")
        return raw_prompt, False

    # 提示词已足够详细，无需增强
    if len(raw_prompt.strip()) >= settings.ai_prompt_min_length:
        return raw_prompt, False

    # API 未配置，降级
    if not settings.ai_api_key:
        logger.warning(
            "AI API key not configured, skipping prompt enhancement for: %s", raw_prompt
        )
        return raw_prompt, False

    try:
        llm = await _get_enhance_llm()
        messages = [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=raw_prompt),
        ]
        response = await llm.ainvoke(messages)
        enhanced = (response.content or "").strip()

        if not enhanced:
            logger.warning("LLM returned empty content for prompt: %s", raw_prompt)
            return raw_prompt, False

        logger.info(
            "Prompt enhanced: '%s' -> '%s'", raw_prompt, enhanced[:80]
        )
        return enhanced, True

    except Exception as exc:
        logger.warning(
            "Prompt enhancement failed, falling back to raw prompt: %s (error: %s)",
            raw_prompt,
            exc,
        )
        return raw_prompt, False
```

- [ ] **Step 2: 编写单元测试 tests/test_prompt_enhance.py**

```python
"""提示词增强服务单元测试。"""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from app.services import prompt_enhancer


@pytest.fixture
def mock_response():
    """模拟 LLM 返回增强后的提示词。"""
    resp = MagicMock()
    resp.content = "一只毛茸茸的橘猫慵懒地坐在窗台上，柔和的自然光透过纱帘洒下，超写实摄影风格"
    return resp


@pytest.fixture
def mock_llm_class(mock_response):
    """模拟 ChatOpenAI 类，返回 mock 实例，其 ainvoke 返回 mock_response。"""
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_response)
    mock_cls = MagicMock(return_value=mock_instance)
    return mock_cls, mock_instance


class TestEnhancePromptShort:
    def test_long_prompt_no_enhance(self, monkeypatch):
        """长提示词不触发增强，直接返回原文。"""
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)

        raw = "超写实水墨山水画，远山如黛，晨雾缭绕"
        result = prompt_enhancer.enhance_prompt(raw)
        # 结果是协程，需要 pytest-asyncio 或手动跑
        assert False, "This test needs pytest-asyncio mark"

    @pytest.mark.asyncio
    async def test_long_prompt_no_enhance(self, monkeypatch):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)

        raw = "超写实水墨山水画，远山如黛，晨雾缭绕"
        prompt, enhanced = await prompt_enhancer.enhance_prompt(raw)
        assert prompt == raw
        assert enhanced is False

    @pytest.mark.asyncio
    async def test_short_prompt_triggers_enhance(self, monkeypatch, mock_llm_class):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
        mock_cls, mock_instance = mock_llm_class
        with patch("app.services.prompt_enhancer.ChatOpenAI", mock_cls):
            prompt, enhanced = await prompt_enhancer.enhance_prompt("一只猫")
            assert enhanced is True
            assert "一只猫" not in prompt  # 增强后不再只有原文
            mock_instance.ainvoke.assert_called_once()

    @pytest.mark.asyncio
    async def test_short_prompt_llm_exception_fallback(self, monkeypatch):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
        mock_cls = MagicMock(side_effect=Exception("network error"))
        with patch("app.services.prompt_enhancer.ChatOpenAI", mock_cls):
            prompt, enhanced = await prompt_enhancer.enhance_prompt("天空")
            assert prompt == "天空"
            assert enhanced is False

    @pytest.mark.asyncio
    async def test_enhance_disabled(self, monkeypatch):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", False)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)

        prompt, enhanced = await prompt_enhancer.enhance_prompt("一只猫")
        assert prompt == "一只猫"
        assert enhanced is False

    @pytest.mark.asyncio
    async def test_no_api_key_fallback(self, monkeypatch):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "")

        prompt, enhanced = await prompt_enhancer.enhance_prompt("猫")
        assert prompt == "猫"
        assert enhanced is False

    @pytest.mark.asyncio
    async def test_empty_prompt(self, monkeypatch):
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
        monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)

        prompt, enhanced = await prompt_enhancer.enhance_prompt("")
        assert prompt == ""
        assert enhanced is False

        prompt, enhanced = await prompt_enhancer.enhance_prompt("   ")
        assert prompt == "   "
        assert enhanced is False
```

- [ ] **Step 3: 运行测试，确认通过**

```bash
cd twelvet-ai-service && python -m pytest tests/test_prompt_enhance.py -v
```

Expected: all tests PASS

---

### Task 3: 集成增强逻辑到图片生成路由

**Files:**
- Modify: `twelvet-ai-service/app/routers/image.py`
- Test: `twelvet-ai-service/tests/test_image.py` (追加测试)

**Interfaces:**
- Consumes: `app.services.prompt_enhancer.enhance_prompt(raw_prompt: str) -> tuple[str, bool]`

- [ ] **Step 1: 修改 routers/image.py**

在文件顶部的 import 区域添加：

```python
from app.services.prompt_enhancer import enhance_prompt
```

在 `generate_image()` 函数中，`# Resolve model` 之前添加增强调用，并将 `enhanced_prompt` 用于任务记录和 Agnes 调用：

将当前代码：

```python
@router.post("/generate")
async def generate_image(
    body: ImageGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create an image generation task and execute it asynchronously."""
    # Resolve model
    model = await _resolve_model(db, body.model_id)
```

修改为：

```python
@router.post("/generate")
async def generate_image(
    body: ImageGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create an image generation task and execute it asynchronously."""
    # Smart prompt enhancement: expand short prompts via LLM
    enhanced_prompt, _ = await enhance_prompt(body.prompt)

    # Resolve model
    model = await _resolve_model(db, body.model_id)
```

并将任务创建处的 `prompt=body.prompt` 改为 `prompt=enhanced_prompt`：

```python
task = AiImageTask(
    model_id=model.model_id,
    prompt=enhanced_prompt,
    negative_prompt=body.negative_prompt,
```

- [ ] **Step 2: 追加图片生成增强集成测试到 tests/test_image.py**

在文件末尾追加：

```python
@pytest.mark.asyncio
async def test_image_generate_with_enhancement(mock_db):
    """Short prompt triggers enhancement; task stores enhanced prompt."""
    mock_model = MagicMock()
    mock_model.model_id = 1
    mock_model.model_name = "test-image-model"
    mock_model.base_url = "https://api.example.com"
    mock_model.api_key = "key123"
    mock_model.model_alias = None

    mock_task = MagicMock()
    mock_task.task_id = 10
    mock_task.prompt = None

    mock_db.execute = AsyncMock(return_value=_make_scalar_result(mock_model))
    mock_db.add = MagicMock(side_effect=lambda obj: setattr(mock_task, "task_id", 10))
    mock_db.commit = AsyncMock()
    mock_db.refresh = AsyncMock()

    captured_prompt = None

    def capture_enhance(prompt):
        nonlocal captured_prompt
        captured_prompt = prompt
        return ("enhanced prompt content", True)

    with patch("app.routers.image.enhance_prompt", side_effect=capture_enhance):
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/ai/image/generate",
                json={"prompt": "一只猫", "gen_type": "text2img"},
            )
        assert response.status_code == 200
        data = response.json()
        assert data["code"] == 200
        # enhance_prompt was called
        assert captured_prompt == "一只猫"
        # task prompt should be enhanced
        assert data["data"]["prompt"] == "enhanced prompt content"
```

- [ ] **Step 3: 运行图片相关测试，确认通过**

```bash
cd twelvet-ai-service && python -m pytest tests/test_image.py -v
```

Expected: all tests PASS

---

### Task 4: 集成增强逻辑到视频生成路由

**Files:**
- Modify: `twelvet-ai-service/app/routers/video.py`
- Test: `twelvet-ai-service/tests/test_video.py` (追加测试)

**Interfaces:**
- Consumes: `app.services.prompt_enhancer.enhance_prompt(raw_prompt: str) -> tuple[str, bool]`

- [ ] **Step 1: 修改 routers/video.py**

在文件顶部的 import 区域添加：

```python
from app.services.prompt_enhancer import enhance_prompt
```

将 `generate_video()` 中 `# Auto-select default VIDEO model if model_id not provided` 之前添加增强调用：

将当前代码：

```python
@router.post("/generate")
async def generate_video(
    body: VideoGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create a video generation task.
    ...
    """
    # Auto-select default VIDEO model if model_id not provided
    model_id = body.model_id
```

修改为：

```python
@router.post("/generate")
async def generate_video(
    body: VideoGenerateRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Create a video generation task.
    ...
    """
    # Smart prompt enhancement: expand short prompts via LLM
    enhanced_prompt, _ = await enhance_prompt(body.prompt)

    # Auto-select default VIDEO model if model_id not provided
    model_id = body.model_id
```

并将任务创建处的 `prompt=body.prompt` 改为 `prompt=enhanced_prompt`：

```python
task = AiVideoTask(
    model_id=model_id,
    prompt=enhanced_prompt,
```

- [ ] **Step 2: 查看并追加视频生成增强集成测试**

首先查看 test_video.py 的现有结构：

```bash
Read twelvet-ai-service/tests/test_video.py
```

然后在文件末尾追加（适配现有 mock 模式）：

```python
@pytest.mark.asyncio
async def test_video_generate_with_enhancement(mock_db):
    """Short prompt triggers enhancement; task stores enhanced prompt."""
    mock_model = MagicMock()
    mock_model.model_id = 1
    mock_model.model_name = "test-video-model"
    mock_model.model_type = "VIDEO"
    mock_model.default_flag = 1
    mock_model.del_flag = 0
    mock_model.status = 1

    mock_task = MagicMock()
    mock_task.task_id = 20

    mock_db.execute = AsyncMock(return_value=_make_scalar_result(mock_model))
    mock_db.add = MagicMock(side_effect=lambda obj: setattr(mock_task, "task_id", 20))
    mock_db.commit = AsyncMock()
    mock_db.refresh = AsyncMock()

    captured_prompt = None

    def capture_enhance(prompt):
        nonlocal captured_prompt
        captured_prompt = prompt
        return ("enhanced video prompt", True)

    with patch("app.routers.video.enhance_prompt", side_effect=capture_enhance):
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/ai/video/generate",
                json={"prompt": "飞翔", "gen_type": "text2vid"},
            )
        assert response.status_code == 200
        data = response.json()
        assert data["code"] == 200
        assert captured_prompt == "飞翔"
        assert data["data"]["prompt"] == "enhanced video prompt"
```

- [ ] **Step 3: 运行视频相关测试，确认通过**

```bash
cd twelvet-ai-service && python -m pytest tests/test_video.py -v
```

Expected: all tests PASS

---

### Task 5: 全量测试验证 + 自我审查

**Files:** 所有已修改文件

- [ ] **Step 1: 运行全量测试套件**

```bash
cd twelvet-ai-service && python -m pytest -v
```

Expected: all tests PASS（包含已有测试 + 新增测试）

- [ ] **Step 2: 自我审查**

- [ ] 配置项: `ai_prompt_enhance_enabled` / `ai_prompt_enhance_model` / `ai_prompt_enhance_temperature` / `ai_prompt_min_length` 均有安全默认值
- [ ] `enhance_prompt` 返回 `(str, bool)` 元组，签名与 spec 一致
- [ ] 降级策略: 空 prompt / 长 prompt / 增强关闭 / 无 API key / LLM 异常 均返回原始 prompt + `is_enhanced=False`
- [ ] image.py 和 video.py 均在创建任务记录**之前**调用增强，且任务 `prompt` 字段存增强后文本
- [ ] Java 层零改动
- [ ] SYSTEM_PROMPT 指导 LLM 输出纯提示词文本，无额外格式
- [ ] 所有测试通过

- [ ] **Step 3: 提交**

```bash
cd twelvet-ai-service
git add -A
git commit -m "feat: add AI prompt enhancement for image/video generation

- Add prompt_enhancer service with LLM-based prompt expansion
- Auto-enhance short prompts (< 10 chars) in image/video generation
- Graceful fallback to raw prompt on LLM failure
- Add comprehensive unit and integration tests"
```

---

## 设计覆盖审查

| 设计需求 | 对应 Task |
|----------|-----------|
| 新增 `prompt_enhancer.py` 核心服务 | Task 2 |
| 新增 4 个配置项 | Task 1 |
| 图片生成前插入增强调用 | Task 3 |
| 视频生成前插入增强调用 | Task 4 |
| LLM 失败降级不阻断流程 | Task 2 (测试覆盖) |
| 空/长/关闭/无key 场景 | Task 2 (测试覆盖) |
| 集成测试验证任务存储增强 prompt | Task 3 + Task 4 |
| Java 层零改动 | 全局约束 |

## 占位符扫描

无 TBD/TODO/占位符。所有步骤包含完整代码和命令。

## 类型一致性

- `enhance_prompt(raw_prompt: str) -> Tuple[str, bool]` — Task 2 定义，Task 3/4 消费，一致
- 配置项字段名与 `config.py` 定义一致
- 测试 mock 路径 `app.services.prompt_enhancer.ChatOpenAI` 与导入路径一致