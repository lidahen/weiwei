"""OpenAI images 协议兼容（gpt-image 系列，如 OpenOx 中转）的单元测试。

覆盖：协议分发（model_provider）、尺寸钳制、请求体/表单构建、
响应提取，以及任务执行器对新旧两条协议的请求走向。
"""

from unittest.mock import AsyncMock, MagicMock

import pytest

from app.services import agnes_image
from app.services.openai_image import (
    API_FORMAT_AGNES,
    API_FORMAT_OPENAI_IMAGES,
    build_edit_form,
    build_generation_body,
    build_multipart_files,
    extract_openai_image_result,
    map_size_for_gpt_image,
    resolve_api_format,
)


def _model(provider):
    m = MagicMock()
    m.model_provider = provider
    return m


class TestResolveApiFormat:
    def test_default_openai_keeps_agnes(self):
        """历史模型用的是默认 model_provider="openai"，必须保持 Agnes 协议。"""
        assert resolve_api_format(_model("openai")) == API_FORMAT_AGNES

    def test_empty_and_agnes_keep_agnes(self):
        assert resolve_api_format(_model(None)) == API_FORMAT_AGNES
        assert resolve_api_format(_model("")) == API_FORMAT_AGNES
        assert resolve_api_format(_model("agnes")) == API_FORMAT_AGNES

    def test_openai_images_providers(self):
        for provider in ("openox", "OpenOx", "openai-images", "gpt-image", "gpt_image"):
            assert resolve_api_format(_model(provider)) == API_FORMAT_OPENAI_IMAGES


class TestMapSizeForGptImage:
    def test_valid_sizes_pass_through(self):
        assert map_size_for_gpt_image("1024x1024") == "1024x1024"
        assert map_size_for_gpt_image("1312x736") == "1312x736"
        assert map_size_for_gpt_image("2624x1472") == "2624x1472"

    def test_longest_side_clamped_to_3840(self):
        assert map_size_for_gpt_image("3936x2208") == "3840x2144"

    def test_total_pixels_clamped(self):
        assert map_size_for_gpt_image("4096x4096") == "2880x2880"

    def test_below_min_pixels_scaled_up(self):
        assert map_size_for_gpt_image("640x640") == "816x816"

    def test_ratio_clamped_to_3_1(self):
        assert map_size_for_gpt_image("4800x1200") == "3600x1200"

    def test_result_aligned_to_16(self):
        w, h = map_size_for_gpt_image("5000x3000").split("x")
        assert int(w) % 16 == 0 and int(h) % 16 == 0

    def test_invalid_input_falls_back(self):
        assert map_size_for_gpt_image(None) == "1024x1024"
        assert map_size_for_gpt_image("abc") == "1024x1024"
        assert map_size_for_gpt_image("0x100") == "1024x1024"


class TestRequestBuilders:
    def test_generation_body(self):
        body = build_generation_body("gpt-image-2", "一只猫", "1024x1024", "url")
        assert body == {
            "model": "gpt-image-2",
            "prompt": "一只猫",
            "n": 1,
            "size": "1024x1024",
            "response_format": "url",
        }

    def test_edit_form_values_are_strings(self):
        form = build_edit_form("gpt-image-2", "上色", "1024x1024")
        assert form["n"] == "1"
        assert form["response_format"] == "url"

    def test_multipart_files_single_uses_image(self):
        files = build_multipart_files([("a.png", b"xx", "image/png")])
        assert files == [("image", ("a.png", b"xx", "image/png"))]

    def test_multipart_files_multi_uses_image_array(self):
        files = build_multipart_files(
            [("a.png", b"1", "image/png"), ("b.jpg", b"2", "image/jpeg")]
        )
        assert [f[0] for f in files] == ["image[]", "image[]"]


class TestExtractOpenAiResult:
    def test_url_preferred(self):
        value, is_b64 = extract_openai_image_result({"data": [{"url": "https://x/a.png"}]})
        assert value == "https://x/a.png" and is_b64 is False

    def test_b64_fallback(self):
        value, is_b64 = extract_openai_image_result({"data": [{"b64_json": "iVBORw=="}]})
        assert value == "iVBORw==" and is_b64 is True

    def test_empty_data(self):
        assert extract_openai_image_result({"data": []}) == (None, False)
        assert extract_openai_image_result({}) == (None, False)


def _scalar(obj):
    result = MagicMock()
    result.scalar_one_or_none.return_value = obj
    return result


class _SessionCtx:
    def __init__(self, session):
        self._session = session

    async def __aenter__(self):
        return self._session

    async def __aexit__(self, *exc):
        return False


def _make_task(**overrides):
    task = MagicMock()
    task.task_id = 1
    task.model_id = 9
    task.prompt = "一只猫"
    task.size = "1024x1024"
    task.style = None
    task.gen_type = "text2img"
    task.input_image = None
    task.response_format = "url"
    for key, value in overrides.items():
        setattr(task, key, value)
    return task


def _make_model(provider="openox"):
    model = MagicMock()
    model.model_id = 9
    model.base_url = "https://openox.tech"
    model.api_key = "sk-test"
    model.model_name = "gpt-image-2"
    model.model_provider = provider
    return model


def _mock_http_response(payload):
    response = MagicMock()
    response.status_code = 200
    response.json.return_value = payload
    response.text = ""
    return response


def _patch_http_client(monkeypatch, payload):
    """Patch httpx.AsyncClient used by agnes_image; return the post mock."""
    post = AsyncMock(return_value=_mock_http_response(payload))
    client_instance = MagicMock()
    client_instance.post = post
    client_cls = MagicMock()
    client_cls.return_value.__aenter__ = AsyncMock(return_value=client_instance)
    client_cls.return_value.__aexit__ = AsyncMock(return_value=False)
    monkeypatch.setattr(agnes_image.httpx, "AsyncClient", client_cls)
    return post


def _make_runner_mocks(monkeypatch, task, model):
    session = AsyncMock()
    session.execute = AsyncMock(side_effect=[_scalar(task), _scalar(model)])
    session.commit = AsyncMock()
    monkeypatch.setattr(agnes_image, "async_session", lambda: _SessionCtx(session))
    update_mock = AsyncMock()
    monkeypatch.setattr(agnes_image, "_update_task_status", update_mock)
    return session, update_mock


@pytest.mark.asyncio
async def test_runner_openox_text2img_hits_generations(monkeypatch):
    task = _make_task()
    model = _make_model("openox")
    _make_runner_mocks(monkeypatch, task, model)
    post = _patch_http_client(
        monkeypatch, {"data": [{"url": "https://cdn.out/x.png"}], "created": 1}
    )

    await agnes_image.create_image_task_async(1)

    url = post.call_args.args[0]
    body = post.call_args.kwargs["json"]
    assert url == "https://openox.tech/v1/images/generations"
    assert body["model"] == "gpt-image-2"
    assert body["n"] == 1
    assert body["response_format"] == "url"
    # multipart 专有参数不应出现
    assert "data" not in post.call_args.kwargs and "files" not in post.call_args.kwargs
    update_mock = agnes_image._update_task_status
    update_mock.assert_called_once()
    assert update_mock.call_args.args[2] == "completed"
    assert update_mock.call_args.kwargs["result_url"] == "https://cdn.out/x.png"


@pytest.mark.asyncio
async def test_runner_openox_size_clamped(monkeypatch):
    task = _make_task(size="3936x2208")
    model = _make_model("openox")
    _make_runner_mocks(monkeypatch, task, model)
    post = _patch_http_client(monkeypatch, {"data": [{"url": "https://cdn.out/x.png"}]})

    await agnes_image.create_image_task_async(1)

    assert post.call_args.kwargs["json"]["size"] == "3840x2144"


@pytest.mark.asyncio
async def test_runner_openox_img2img_hits_edits_multipart(monkeypatch):
    task = _make_task(gen_type="img2img", input_image="https://dfs.local/a.png")
    model = _make_model("openox")
    _make_runner_mocks(monkeypatch, task, model)
    post = _patch_http_client(monkeypatch, {"data": [{"url": "https://cdn.out/x.png"}]})
    downloaded = [("a.png", b"img-bytes", "image/png")]
    download_mock = AsyncMock(return_value=downloaded)
    monkeypatch.setattr(agnes_image, "download_input_images", download_mock)

    await agnes_image.create_image_task_async(1)

    download_mock.assert_awaited_once_with(["https://dfs.local/a.png"])
    url = post.call_args.args[0]
    assert url == "https://openox.tech/v1/images/edits"
    form = post.call_args.kwargs["data"]
    assert form["model"] == "gpt-image-2" and form["n"] == "1"
    files = post.call_args.kwargs["files"]
    assert files == [("image", ("a.png", b"img-bytes", "image/png"))]
    # multipart 请求不能带手动 Content-Type（需由 httpx 写入 boundary）
    assert "Content-Type" not in post.call_args.kwargs["headers"]
    update_mock = agnes_image._update_task_status
    assert update_mock.call_args.args[2] == "completed"


@pytest.mark.asyncio
async def test_runner_agnes_img2img_keeps_legacy_format(monkeypatch):
    """历史 Agnes 模型（默认 model_provider）图生图仍走 generations + extra_body.image。"""
    task = _make_task(gen_type="img2img", input_image="https://dfs.local/a.png")
    model = _make_model("openai")
    _make_runner_mocks(monkeypatch, task, model)
    post = _patch_http_client(monkeypatch, {"data": [{"url": "https://cdn.out/x.png"}]})

    await agnes_image.create_image_task_async(1)

    url = post.call_args.args[0]
    body = post.call_args.kwargs["json"]
    assert url == "https://openox.tech/v1/images/generations"
    assert body["extra_body"]["image"] == ["https://dfs.local/a.png"]
    assert "files" not in post.call_args.kwargs


@pytest.mark.asyncio
async def test_runner_openox_b64_only_fails_with_hint(monkeypatch):
    """中转只回 Base64 时任务失败并给出明确提示，而不是存入超长数据。"""
    task = _make_task()
    model = _make_model("openox")
    _make_runner_mocks(monkeypatch, task, model)
    _patch_http_client(monkeypatch, {"data": [{"b64_json": "iVBORw=="}]})

    await agnes_image.create_image_task_async(1)

    update_mock = agnes_image._update_task_status
    update_mock.assert_called_once()
    assert update_mock.call_args.args[2] == "failed"
    assert "Base64" in update_mock.call_args.kwargs["error_msg"]
