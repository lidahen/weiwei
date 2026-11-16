"""SenseNova 图片协议适配的纯函数测试。

覆盖三件事：
1. 默认尺寸白名单本身必须全部合法（32 倍数 / 单边 512~4096 / 比例 ≤3:1）
2. 尺寸归一化：白名单内原样返回，白名单外就近匹配且不破坏构图比例
3. 请求体构造：文生图走 generations 顶层字段、图生图走 edits 的 images[].image_url JSON 结构
"""
import pytest

from app.services.sensenova_image import (
    DEFAULT_SIZES,
    SIZE_MULTIPLE,
    MAX_SIDE,
    MIN_SIDE,
    build_edits_body,
    build_generation_body,
    is_sensenova_provider,
    normalize_sensenova_size,
    parse_declared_sizes,
    size_is_legal,
)


class _Model:
    def __init__(self, provider):
        self.model_provider = provider


# ── 白名单自洽 ──

def test_default_sizes_all_legal():
    illegal = [s for s in DEFAULT_SIZES if not size_is_legal(s)]
    assert illegal == [], f"默认白名单存在非法尺寸: {illegal}"


def test_default_sizes_cover_every_ratio():
    """8 个宽高比（含 21:9）都要有对应尺寸，否则前端会隐藏该比例选项。"""
    expected = {"1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3", "21:9"}
    covered = set()
    for size in DEFAULT_SIZES:
        w, h = (int(x) for x in size.split("x"))
        for candidate in expected:
            a, b = candidate.split(":")
            if abs(w / h - int(a) / int(b)) < 0.02:
                covered.add(candidate)
    assert covered == expected, f"缺失比例: {expected - covered}"


@pytest.mark.parametrize(
    "size",
    ["1024x1024", "4096x4096", "512x512", "512x1536", "1536x512", "3456x2592"],
)
def test_size_is_legal_accepts_boundary(size):
    assert size_is_legal(size) is True


@pytest.mark.parametrize(
    "size,reason",
    [
        ("1000x1000", "非 32 倍数"),
        ("480x480", "低于最小边"),
        ("4128x4128", "超过最大边"),
        ("4096x1024", "比例超过 3:1"),
        ("1K", "非 WxH"),
        ("", "空值"),
    ],
)
def test_size_is_legal_rejects(size, reason):
    assert size_is_legal(size) is False, reason


# ── 尺寸归一化 ──

def test_normalize_keeps_whitelisted_size():
    assert normalize_sensenova_size("2624x1472", DEFAULT_SIZES) == "2624x1472"


def test_normalize_maps_illegal_size_to_nearest_ratio():
    # 3K 21:9（4704 超上限）→ 收敛到 2K 21:9，构图比例保持不变
    assert normalize_sensenova_size("4704x2016", DEFAULT_SIZES) == "3136x1344"


def test_normalize_prefers_ratio_over_area():
    # 9:16 的 3K 尺寸不在白名单 → 选同比例的 2K，而不是面积更近的其它比例
    result = normalize_sensenova_size("2208x3936", DEFAULT_SIZES)
    w, h = (int(x) for x in result.split("x"))
    assert h > w
    assert abs(w / h - 9 / 16) < 0.02


def test_normalize_falls_back_to_first_when_unparsable():
    assert normalize_sensenova_size("auto", DEFAULT_SIZES) == DEFAULT_SIZES[0]
    assert normalize_sensenova_size(None, DEFAULT_SIZES) == DEFAULT_SIZES[0]


def test_normalize_without_whitelist_clamps_to_constraints():
    result = normalize_sensenova_size("5000x1000", [])
    assert size_is_legal(result), result
    w, h = (int(x) for x in result.split("x"))
    assert w % SIZE_MULTIPLE == 0 and h % SIZE_MULTIPLE == 0
    assert MIN_SIDE <= w <= MAX_SIDE and MIN_SIDE <= h <= MAX_SIDE


# ── ext_param 解析 ──

def test_parse_declared_sizes_from_json_string():
    assert parse_declared_sizes('{"sizes": ["1024x1024", "bad", "2048X2048"]}') == [
        "1024x1024",
        "2048x2048",
    ]


def test_parse_declared_sizes_missing_returns_empty():
    assert parse_declared_sizes(None) == []
    assert parse_declared_sizes("{}") == []
    assert parse_declared_sizes("not json") == []


# ── 请求体 ──

def test_generation_body_shape():
    body = build_generation_body(
        model_name="sensenova-u1.5-fast", prompt="一只猫", size="2048x2048"
    )
    assert body["model"] == "sensenova-u1.5-fast"
    assert body["n"] == 1  # 官方仅支持 1
    assert body["size"] == "2048x2048"
    assert body["response_format"] == "url"  # result_url 列只能存 URL
    assert body["watermark"] is False
    assert body["output_format"] == "png"
    assert "images" not in body


def test_edits_body_uses_json_images_array_not_multipart():
    body = build_edits_body(
        model_name="sensenova-u1.5-lite",
        prompt="改成夜景",
        image_urls=["https://cdn.example.com/a.png", "https://cdn.example.com/b.png"],
        size="1472x2624",
    )
    assert body["images"] == [
        {"image_url": "https://cdn.example.com/a.png"},
        {"image_url": "https://cdn.example.com/b.png"},
    ]
    assert body["n"] == 1
    # edits 不接受 watermark/prompt_extend 以外的多余字段时也不应带 generations 专有字段
    assert "prompt_extend" not in body


def test_edits_body_truncates_to_five_images():
    body = build_edits_body(
        model_name="sensenova-u1.5-lite",
        prompt="合成",
        image_urls=[f"https://cdn.example.com/{i}.png" for i in range(8)],
        size="1024x1024",
    )
    assert len(body["images"]) == 5


# ── 供应商判定 ──

@pytest.mark.parametrize("provider,expected", [
    ("sensenova", True),
    ("SenseNova", True),
    ("sense-nova", True),
    ("senseaudio", False),
    ("agnes", False),
    (None, False),
    ("", False),
])
def test_is_sensenova_provider(provider, expected):
    assert is_sensenova_provider(_Model(provider)) is expected
