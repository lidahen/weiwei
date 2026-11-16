"""by-type 接口的图片尺寸白名单解析。

约定：只有模型**显式声明** sizes 时才限制前端选项，未声明一律返回 []（不限制），
避免误伤 agnes 等未声明尺寸的图片模型。
"""
from app.routers.model_config import _declared_sizes


def test_from_json_string():
    raw = '{"price_per_img": 0.5, "sizes": ["1024x1024", "2048x1152"]}'
    assert _declared_sizes(raw) == ["1024x1024", "2048x1152"]


def test_from_dict_normalizes_case():
    assert _declared_sizes({"sizes": ["1536X864"]}) == ["1536x864"]


def test_empty_when_not_declared():
    assert _declared_sizes(None) == []
    assert _declared_sizes("{}") == []
    assert _declared_sizes('{"price_per_img": 0.2}') == []
    assert _declared_sizes({"sizes": "1024x1024"}) == []


def test_ignores_invalid_entries():
    assert _declared_sizes({"sizes": ["1024x1024", "bad", "", "1x1", None]}) == ["1024x1024"]


def test_malformed_json_is_safe():
    assert _declared_sizes("{not json") == []
