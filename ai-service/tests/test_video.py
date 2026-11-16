import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.database import get_db
from app.services import prompt_enhancer


@pytest.fixture
def mock_db():
    """Create a mock async database session."""
    session = AsyncMock()
    session.commit = AsyncMock()
    session.close = AsyncMock()
    session.add = MagicMock()
    session.refresh = AsyncMock()
    return session


@pytest.fixture(autouse=True)
def override_get_db(mock_db):
    """Override the get_db dependency with a mock for all video tests."""
    async def _get_db_override():
        yield mock_db

    app.dependency_overrides[get_db] = _get_db_override
    yield
    app.dependency_overrides.clear()


@pytest.fixture
def mock_llm_class():
    """模拟 ChatOpenAI 类，返回 mock 实例，其 ainvoke 返回增强后的提示词。"""
    mock_response = MagicMock()
    mock_response.content = (
        "壮丽的雪山日出延时摄影，云海翻涌，"
        "金色阳光洒满山谷，航拍 4K 超高清"
    )
    mock_instance = AsyncMock()
    mock_instance.ainvoke = AsyncMock(return_value=mock_response)
    mock_cls = MagicMock(return_value=mock_instance)
    return mock_cls, mock_instance


def _make_scalar_result(obj):
    """Build a sync mock result that supports .scalar_one_or_none() returning obj."""
    result = MagicMock()
    result.scalar_one_or_none.return_value = obj
    # model_resolver 统一入口按 limit(2) + scalars().all() 读取，这里一并补上该 API
    result.scalars.return_value.all.return_value = [obj] if obj is not None else []
    return result


def _make_scalars_result(objs):
    """Build a sync mock result that supports .scalars().all() returning objs."""
    scalars_mock = MagicMock()
    scalars_mock.all.return_value = objs
    result = MagicMock()
    result.scalars.return_value = scalars_mock
    return result


@pytest.mark.asyncio
async def test_video_generate_endpoint_no_model(mock_db):
    # Mock model lookup to return None (no model found)
    mock_db.execute = AsyncMock(return_value=_make_scalar_result(None))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/ai/video/generate", json={"prompt": "test", "gen_type": "text2vid"}
        )
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 500
    assert "模型" in data["msg"]


@pytest.mark.asyncio
async def test_video_status_endpoint_not_found(mock_db):
    mock_db.execute = AsyncMock(return_value=_make_scalar_result(None))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/ai/video/status/1")
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 500
    assert "任务" in data["msg"]


@pytest.mark.asyncio
async def test_video_list_endpoint_empty(mock_db):
    mock_db.execute = AsyncMock(return_value=_make_scalars_result([]))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/ai/video/list")
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 200
    assert data["data"]["total"] == 0


@pytest.mark.asyncio
async def test_video_delete_endpoint(mock_db):
    mock_db.execute = AsyncMock(return_value=MagicMock())

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.delete("/ai/video/1")
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 200


@pytest.mark.asyncio
async def test_video_generate_with_enhancement(mock_db, mock_llm_class, monkeypatch):
    """Short prompt triggers enhancement; enhanced prompt is stored in task."""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")
    mock_cls, mock_instance = mock_llm_class

    # Mock model lookup: first call for VIDEO model returns None, second for specific model_id
    mock_model = MagicMock()
    mock_model.model_id = 88
    mock_model.model_type = "VIDEO"
    # 统一入口会校验启用状态与软删标记，mock 需具备真实字段
    mock_model.status = 1
    mock_model.del_flag = 0
    mock_db.execute = AsyncMock(return_value=_make_scalar_result(mock_model))

    # db.refresh(task) must set task_id
    mock_db.refresh = AsyncMock(side_effect=lambda obj: setattr(obj, "task_id", 77))

    with patch("app.services.model_resolver.build_chat_llm", mock_cls), patch(
        "app.routers.video.create_video_task_async"
    ):
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/ai/video/generate",
                json={"prompt": "雪山日出", "model_id": 88, "gen_type": "text2vid"},
            )
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 200
    assert data["data"]["prompt"] != "雪山日出"
    mock_instance.ainvoke.assert_called_once()


@pytest.mark.asyncio
async def test_video_generate_with_enhancement_disabled(mock_db, monkeypatch):
    """enhance_prompt=false 时不调用 LLM，原始 prompt 存入任务。"""
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_enhance_enabled", True)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_prompt_min_length", 10)
    monkeypatch.setattr(prompt_enhancer.settings, "ai_api_key", "test-key")

    mock_model = MagicMock()
    mock_model.model_id = 88
    mock_model.model_type = "VIDEO"
    # 统一入口会校验启用状态与软删标记，mock 需具备真实字段
    mock_model.status = 1
    mock_model.del_flag = 0
    mock_db.execute = AsyncMock(return_value=_make_scalar_result(mock_model))
    mock_db.refresh = AsyncMock(side_effect=lambda obj: setattr(obj, "task_id", 78))

    with patch("app.routers.video.create_video_task_async"):
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post(
                "/ai/video/generate",
                json={
                    "prompt": "雪山日出",
                    "model_id": 88,
                    "gen_type": "text2vid",
                    "enhance_prompt": False,
                },
            )
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 200
    assert data["data"]["prompt"] == "雪山日出"
