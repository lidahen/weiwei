"""首页小组件数据服务：天气（open-meteo）+ AI 激励语池。

GET /ai/assist/home-widget 的业务实现（见 docs/superpowers/specs/2026-09-10-home-widget-design.md）：

- 天气：open-meteo 免费 API（geocoding 按城市名查经纬度 → forecast 取当日实况），
  无需 API key；按（城市 × 小时）缓存 1h，Redis 优先、进程内存兜底；失败返回 None（前端隐藏天气块）。
- 激励语：LLM 一次生成 5 条池（结合城市/天气/星期），按（租户 × 北京日期）缓存至次日 04:00；
  游客 / LLM 未配置 / 配额超限 / 任何异常 → 内置模板池按日期稳定轮换（零成本，永不报错）。
- 配额：仅在确实要调 LLM 时 check_and_count（对齐 quota_service 单一账本）；
  QuotaExceededException 静默降级模板池——首页体验优先，不向用户返回 429。
  成功后 log_usage(feature="home-quote")。

时区约定：统一 Asia/Shanghai（固定 UTC+8，目标用户为中国社区场景），
避免服务器时区漂移导致缓存 key 跨天抖动。
"""

import asyncio
import logging
import re
import time
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional, Tuple

import httpx

from app.services.redis_client import json_get, json_set

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
# 时区与日期工具（固定北京时间）
# ─────────────────────────────────────────────
_TZ_CN = timezone(timedelta(hours=8), name="Asia/Shanghai")

WEEKDAY_NAMES = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]


def now_cn() -> datetime:
    """当前北京时间（小组件所有日期口径的唯一来源）。"""
    return datetime.now(_TZ_CN)


# ─────────────────────────────────────────────
# 模板池降级：游客 / LLM 故障 / 配额超限时使用
# ─────────────────────────────────────────────
FALLBACK_QUOTES: List[str] = [
    "旧物有故事，转手即新生。",
    "把闲置交给需要的人，是今天的温柔。",
    "让好物流转，让生活减负。",
    "卖出的每一件闲置，都是一次心动的接力。",
    "少买一点，多爱一点，旧物也很好。",
    "今天也是适合断舍离的好日子。",
    "你的抽屉，也许正是别人的宝藏。",
    "流转一件旧物，结交一位邻里。",
    "闲置不闲，各有归途。",
    "整理旧物的心情，和天气一样明朗。",
    "物尽其用，是对生活最好的诚意。",
    "每一次出闲置，都是给地球的小礼物。",
    "好东西值得第二次心动。",
    "买卖之间，皆是生活滋味。",
    "旧物新主，缘分不早不晚。",
    "把空间还给生活，把好物留给缘分。",
    "邻里之间，闲置也能变惊喜。",
    "今天上新一件闲置，就多一份小确幸。",
    "分享闲置，让社区更有温度。",
    "出手旧爱，遇见新欢。",
    "生活不必全新，有光就够了。",
    "省下的每一笔，都是明天的底气。",
    "旧物不旧，心动常新。",
    "逛逛邻居的闲置，比刷手机治愈多了。",
]

TEMPLATE_POOL_SIZE = 5


def fallback_quotes(day: Optional[datetime] = None) -> List[str]:
    """模板池：以 day-of-year 为起点循环取 5 条，同一天稳定、逐日轮换。"""
    d = day or now_cn()
    start = d.timetuple().tm_yday % len(FALLBACK_QUOTES)
    return [FALLBACK_QUOTES[(start + i) % len(FALLBACK_QUOTES)] for i in range(TEMPLATE_POOL_SIZE)]


# ─────────────────────────────────────────────
# 天气：open-meteo（免费、无 key）+ 双层缓存
# ─────────────────────────────────────────────
_OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
_OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
_WEATHER_TIMEOUT = 5.0

# ── 性能相关常量（首屏秒回优化）──────────────────────────
# 城市名→经纬度几乎不变，长效缓存后冷启动只打 1 次 forecast（省掉一次外网 RTT）
_GEO_TTL = 30 * 24 * 3600
# 当前小时缓存
_WEATHER_TTL = 3600
# last-known-good：软过期兜底，7 天内可秒回旧值并在后台刷新
_WEATHER_LAST_TTL = 7 * 24 * 3600
# 完全冷启动（无任何兜底）时前台最长等待，超时转后台继续刷新
_WEATHER_FIRST_WAIT = 6.0
# LLM 生成激励语的前台最长等待，超时降级模板池（首页不为文案买单）
_QUOTE_LLM_TIMEOUT = 10.0
# 后台预热周期：对近期访问过的城市刷新天气，保持缓存常新
_WARM_INTERVAL = 20 * 60

# WMO weather interpretation codes → (中文现象, emoji)
WMO_MAP: Dict[int, Tuple[str, str]] = {
    0: ("晴", "☀️"),
    1: ("大致晴朗", "🌤️"),
    2: ("多云", "⛅"),
    3: ("阴", "☁️"),
    45: ("雾", "🌫️"),
    48: ("雾凇", "🌫️"),
    51: ("小毛雨", "🌦️"),
    53: ("毛毛雨", "🌦️"),
    55: ("浓毛毛雨", "🌧️"),
    56: ("冻毛毛雨", "🌧️"),
    57: ("浓冻毛毛雨", "🌧️"),
    61: ("小雨", "🌧️"),
    63: ("中雨", "🌧️"),
    65: ("大雨", "🌧️"),
    66: ("冻雨", "🌧️"),
    67: ("强冻雨", "🌧️"),
    71: ("小雪", "🌨️"),
    73: ("中雪", "❄️"),
    75: ("大雪", "❄️"),
    77: ("雪粒", "❄️"),
    80: ("小阵雨", "🌦️"),
    81: ("阵雨", "🌦️"),
    82: ("强阵雨", "⛈️"),
    85: ("小阵雪", "🌨️"),
    86: ("大阵雪", "❄️"),
    95: ("雷阵雨", "⛈️"),
    96: ("雷阵雨伴冰雹", "⛈️"),
    99: ("强雷雨伴冰雹", "⛈️"),
}


def describe_weather_code(code: Optional[int]) -> Tuple[str, str]:
    """WMO weather_code → (中文现象, emoji)；未知编码回落多云。"""
    if code is None:
        return ("多云", "⛅")
    return WMO_MAP.get(int(code), ("多云", "⛅"))


def clean_city(city: Optional[str]) -> str:
    """城市名清洗：去空白与「市」后缀，空值回落深圳（与首页默认一致）。"""
    name = (city or "").strip() or "深圳"
    if len(name) > 1 and name.endswith("市"):
        name = name[:-1]
    return name[:30]


# ── IP → 城市定位（浏览器公网 IP，2026-09-25 新增）──
# ip-api.com 免费接口：限流 45 次/分，必须缓存；lang=zh-CN 直接返回中文城市名；
# 免费档仅支持 http（后端服务间调用，无 HTTPS 混合内容问题）。
_IP_RE = re.compile(r"^(?:\d{1,3}(?:\.\d{1,3}){3}|[0-9A-Fa-f:]{2,45})$")
_IP_CACHE_TTL = 6 * 3600


async def city_from_ip(ip: str) -> str:
    """公网 IP → 中文城市名（如「南京市」），失败/非法 IP 返回空串。

    按 IP 缓存 6h（Redis 优先、进程内存兜底，复用天气缓存设施）。
    """
    ip = (ip or "").strip()
    if not ip or not _IP_RE.match(ip) or _is_private_ip(ip):
        return ""
    cache_key = f"home_widget:ipcity:{ip}"
    cached = await _cache_get(cache_key)
    if cached:
        return str(cached)
    city = ""
    try:
        async with httpx.AsyncClient(timeout=_WEATHER_TIMEOUT) as client:
            resp = await client.get(
                f"http://ip-api.com/json/{ip}",
                params={"lang": "zh-CN", "fields": "status,city"},
            )
            resp.raise_for_status()
            data = resp.json() or {}
            if data.get("status") == "success":
                city = (data.get("city") or "").strip()
    except Exception as e:
        logger.warning("home_widget ip→city 失败 ip=%s: %s", ip, e)
    if city:
        await _cache_set(cache_key, city, _IP_CACHE_TTL)
    return city


def _is_private_ip(ip: str) -> bool:
    """内网/回环/链路本地地址判断（这些 IP 无法定位城市，直接走出口 IP 兜底）。

    用 ipaddress.is_global 统一判断：原前缀匹配写法会把公网 172.2x.x.x
    （如 172.200.1.1）误判成内网。"""
    import ipaddress

    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return True
    return not addr.is_global


async def resolve_request_city(ip: str = "") -> str:
    """请求级城市解析：客户端公网 IP 定位 > 服务端出口 IP 定位 > 空串（调用方回落默认）。

    - 生产：Caddy → Java 取 X-Forwarded-For 传真实客户端 IP；
    - 本地开发：请求 IP 恒为 127.0.0.1/内网，此时服务端出口公网 IP 与浏览器
      同属一个网络出口，解析结果即为用户所在城市。
    出口 IP 结果缓存 6h（出口地址基本不变，且 ip-api 免费档限流 45 次/分）。
    """
    city = await city_from_ip(ip)
    if city:
        return city
    cache_key = "home_widget:ipcity:__egress__"
    cached = await _cache_get(cache_key)
    if cached:
        return str(cached)
    try:
        async with httpx.AsyncClient(timeout=_WEATHER_TIMEOUT) as client:
            resp = await client.get(
                "http://ip-api.com/json/",
                params={"lang": "zh-CN", "fields": "status,city"},
            )
            resp.raise_for_status()
            data = resp.json() or {}
            if data.get("status") == "success":
                egress_city = (data.get("city") or "").strip()
                if egress_city:
                    await _cache_set(cache_key, egress_city, _IP_CACHE_TTL)
                    return egress_city
    except Exception as e:
        logger.warning("home_widget 出口 IP 城市解析失败: %s", e)
    return ""


# 进程内缓存兜底（Redis 不可用时）：key → (expires_at_epoch, value)
_memory_cache: Dict[str, Tuple[float, Any]] = {}
_MEMORY_CACHE_MAX = 100


async def _cache_get(key: str) -> Optional[Any]:
    cached = await json_get(key)
    if cached is not None:
        return cached
    entry = _memory_cache.get(key)
    if entry and entry[0] > time.time():
        return entry[1]
    if entry:
        _memory_cache.pop(key, None)
    return None


async def _cache_set(key: str, value: Any, ttl_seconds: int) -> None:
    await json_set(key, value, ttl_seconds)
    if len(_memory_cache) >= _MEMORY_CACHE_MAX:
        # 简单防膨胀：清掉已过期项，仍超限则整体重置（低频低价值数据，可接受）
        now = time.time()
        for k in [k for k, (exp, _) in _memory_cache.items() if exp <= now]:
            _memory_cache.pop(k, None)
        if len(_memory_cache) >= _MEMORY_CACHE_MAX:
            _memory_cache.clear()
    _memory_cache[key] = (time.time() + ttl_seconds, value)


async def _geocode_city(client: httpx.AsyncClient, city: str) -> Optional[Tuple[float, float]]:
    """open-meteo geocoding：城市名 → (lat, lon)。失败返回 None。"""
    try:
        resp = await client.get(
            _OPEN_METEO_GEOCODING_URL,
            params={"name": city, "count": 1, "language": "zh", "format": "json"},
        )
        resp.raise_for_status()
        results = (resp.json() or {}).get("results") or []
        if not results:
            return None
        first = results[0]
        return (float(first["latitude"]), float(first["longitude"]))
    except Exception as e:
        logger.warning("home_widget geocoding 失败 city=%s: %s", city, e)
        return None


async def _fetch_forecast(
    client: httpx.AsyncClient, lat: float, lon: float
) -> Optional[Dict[str, Any]]:
    """open-meteo forecast：取当日实况温度 + 最高/最低。失败返回 None。"""
    try:
        resp = await client.get(
            _OPEN_METEO_FORECAST_URL,
            params={
                "latitude": lat,
                "longitude": lon,
                "current": "temperature_2m,weather_code",
                "daily": "temperature_2m_max,temperature_2m_min,weather_code",
                "timezone": "auto",
                "forecast_days": 1,
            },
        )
        resp.raise_for_status()
        data = resp.json() or {}
        current = data.get("current") or {}
        daily = data.get("daily") or {}
        temp = current.get("temperature_2m")
        code = current.get("weather_code")
        if code is None and daily.get("weather_code"):
            code = (daily.get("weather_code") or [None])[0]
        text, icon = describe_weather_code(code)
        return {
            "text": text,
            "icon": icon,
            "temp": round(float(temp), 1) if temp is not None else None,
            "temp_max": _round_or_none((daily.get("temperature_2m_max") or [None])[0]),
            "temp_min": _round_or_none((daily.get("temperature_2m_min") or [None])[0]),
        }
    except Exception as e:
        logger.warning("home_widget forecast 失败 lat=%s lon=%s: %s", lat, lon, e)
        return None


def _round_or_none(v: Any) -> Optional[float]:
    try:
        return round(float(v), 1)
    except (TypeError, ValueError):
        return None


async def _geocode_cached(
    client: httpx.AsyncClient, name: str
) -> Optional[Tuple[float, float]]:
    """geocoding 结果长效缓存（30d）：城市经纬度几乎不变，避免每次冷启动多一次外网往返。"""
    key = f"home_widget:geo:{name}"
    cached = await _cache_get(key)
    if isinstance(cached, dict) and cached.get("lat") is not None:
        try:
            return (float(cached["lat"]), float(cached["lon"]))
        except (TypeError, ValueError, KeyError):
            pass
    geo = await _geocode_city(client, name)
    if geo is not None:
        await _cache_set(key, {"lat": geo[0], "lon": geo[1]}, _GEO_TTL)
    return geo


# 同城市同时刻的外网请求去重（并发首页加载只打一次 geocoding+forecast）
_weather_inflight: Dict[str, asyncio.Future] = {}
# 近期访问过的城市 → 后台预热目标
_known_cities: set = set()
_warmer_started = False


async def _refresh_weather(name: str, hour_key: str) -> Optional[Dict[str, Any]]:
    """真正拉取天气：写入「当前小时」缓存 + last-known-good 兜底缓存。"""
    try:
        async with httpx.AsyncClient(timeout=_WEATHER_TIMEOUT) as client:
            geo = await _geocode_cached(client, name)
            if geo is None:
                return None
            weather = await _fetch_forecast(client, geo[0], geo[1])
        if weather is None:
            return None
        result = {"city": name, **weather}
        await _cache_set(f"home_widget:weather:{name}:{hour_key}", result, _WEATHER_TTL)
        await _cache_set(f"home_widget:weather:last:{name}", result, _WEATHER_LAST_TTL)
        return result
    except Exception as e:
        logger.warning("home_widget weather 异常 city=%s: %s", name, e)
        return None


def _spawn_weather_refresh(name: str, hour_key: str) -> asyncio.Future:
    """后台发起一次刷新并登记到在途表（完成后自动摘除），不阻塞当前请求。"""
    task = asyncio.get_running_loop().create_task(_refresh_weather(name, hour_key))
    _weather_inflight[f"home_widget:weather:{name}:{hour_key}"] = task
    task.add_done_callback(
        lambda t: _weather_inflight.pop(f"home_widget:weather:{name}:{hour_key}", None)
    )
    return task


async def _warmer_loop() -> None:
    """后台预热：定期刷新近期城市天气，让前台永远命中缓存（首屏 0 外网等待）。"""
    while True:
        try:
            # 首轮先跑（此时可能还没有人访问，用默认城市垫底），之后按周期刷已知城市
            for name in (list(_known_cities)[:20] or list(_DEFAULT_WARM_CITIES)):
                await _refresh_weather(name, now_cn().strftime("%Y%m%d%H"))
                await asyncio.sleep(0.5)
        except asyncio.CancelledError:
            raise
        except Exception as e:
            logger.warning("home_widget 预热异常: %s", e)
        await asyncio.sleep(_WARM_INTERVAL)


def ensure_warmer_started() -> None:
    """首次调用时拉起预热协程（进程内只起一次）。"""
    global _warmer_started
    if _warmer_started:
        return
    try:
        asyncio.get_running_loop().create_task(_warmer_loop())
        _warmer_started = True
    except RuntimeError:
        pass


async def get_weather(city: str) -> Optional[Dict[str, Any]]:
    """获取城市当日天气（缓存 1h，软过期秒回）。

    三级取数，目标：除首次冷启动外，前台永不等待外网：
    1. 当前小时缓存命中 → 直接返回（~0ms）；
    2. 未命中但有 last-known-good（7 天内旧值）→ 秒回旧值 + 后台刷新；
    3. 完全无兜底 → 前台最多等 _WEATHER_FIRST_WAIT 秒，超时返回 None 并转后台继续。

    任何失败返回 None，由前端隐藏天气块。
    """
    name = clean_city(city)
    hour_key = now_cn().strftime("%Y%m%d%H")
    cache_key = f"home_widget:weather:{name}:{hour_key}"
    last_key = f"home_widget:weather:last:{name}"

    _known_cities.add(name)
    ensure_warmer_started()

    cached = await _cache_get(cache_key)
    if cached is not None:
        return cached

    # 并发去重：同 key 已有在途刷新，不重复打外网
    inflight = _weather_inflight.get(cache_key)
    if inflight is not None and not inflight.done():
        stale = await _cache_get(last_key)
        if stale is not None:
            return stale  # 有旧值就秒回，让在途任务把新值写进缓存
        try:
            return await asyncio.wait_for(asyncio.shield(inflight), _WEATHER_FIRST_WAIT)
        except Exception:
            return None

    # 有 last-known-good：秒回旧值，后台静默刷新
    stale = await _cache_get(last_key)
    if stale is not None:
        _spawn_weather_refresh(name, hour_key)
        return stale

    # 完全冷启动：前台短等，超时后交给后台，本次降级（前端隐藏天气块）
    task = _spawn_weather_refresh(name, hour_key)
    try:
        return await asyncio.wait_for(asyncio.shield(task), _WEATHER_FIRST_WAIT)
    except Exception:
        logger.info("home_widget 天气冷启动超时，转后台刷新 city=%s", name)
        return None


# ─────────────────────────────────────────────
# AI 激励语池：LLM 生成 + 租户×日期缓存
# ─────────────────────────────────────────────
QUOTE_COUNT = 5

QUOTE_SYSTEM_PROMPT = (
    "你是一个社区二手交易平台的文案助手。根据给定的日期、城市、天气，"
    "写 5 条简短温暖的中文激励语，在首页天气卡片中展示。要求：\n"
    "1. 每条不超过 20 个字，口语化、有画面感；\n"
    "2. 自然融入当天的天气或星期氛围，并贴合二手交易/闲置流转/邻里社区场景；\n"
    "3. 不堆砌空洞口号，不使用表情符号；\n"
    '4. 严格输出 JSON：{"quotes": ["...", "...", "...", "...", "..."]}'
)


def build_quote_user_prompt(
    city: str, weather: Optional[Dict[str, Any]], day: Optional[datetime] = None
) -> str:
    """组装激励语生成的用户提示词（天气缺失时仅基于日期）。"""
    d = day or now_cn()
    parts = [f"今天：{d.month}月{d.day}日 {WEEKDAY_NAMES[d.weekday()]}", f"城市：{clean_city(city)}"]
    if weather:
        temp_part = f"，当前 {weather['temp']}°C" if weather.get("temp") is not None else ""
        parts.append(f"天气：{weather.get('text', '未知')}{temp_part}")
    return "。".join(parts) + "。请生成 5 条激励语。"


def parse_quotes_response(text: str) -> List[str]:
    """解析 LLM 输出为激励语列表：JSON → 围栏 JSON → 按行兜底；不足 5 条补齐模板池。"""
    import json as _json

    raw = (text or "").strip()
    if not raw:
        return []

    candidates = [raw]
    if "```" in raw:
        for block in raw.split("```"):
            block = block.strip()
            if block.startswith("json"):
                block = block[4:].strip()
            if block.startswith("{"):
                candidates.insert(0, block)
                break

    quotes: List[str] = []
    for cand in candidates:
        try:
            data = _json.loads(cand)
        except (ValueError, TypeError):
            continue
        items = data.get("quotes") if isinstance(data, dict) else None
        if isinstance(items, list):
            quotes = [str(q).strip() for q in items if str(q).strip()]
            break

    if not quotes:
        # 兜底：非 JSON 输出按非空行截取（去掉序号/引号装饰）
        for line in raw.splitlines():
            line = line.strip().strip('"“”‘’，。 ')
            if not line or line.startswith("{") or line.startswith("}"):
                continue
            if len(line) > 40:
                continue
            quotes.append(line)
            if len(quotes) >= QUOTE_COUNT:
                break

    # 去重、截断、补齐
    seen, unique = set(), []
    for q in quotes:
        if q not in seen:
            seen.add(q)
            unique.append(q[:30])
    if len(unique) < QUOTE_COUNT:
        unique += fallback_quotes()[: QUOTE_COUNT - len(unique)]
    return unique[:QUOTE_COUNT]


def _quotes_cache_key(tenant_id: int) -> str:
    return f"home_widget:quotes:{int(tenant_id or 0)}:{now_cn().strftime('%Y%m%d')}"


def _quotes_ttl_seconds() -> int:
    """缓存至次日 04:00（北京时间），至少 1h——覆盖凌晨场景且跨天后 key 自然切换。"""
    now = now_cn()
    next_4am = (now + timedelta(days=1)).replace(hour=4, minute=0, second=0, microsecond=0)
    return max(3600, int((next_4am - now).total_seconds()))


async def _generate_quotes(
    cache_key: str,
    tenant_id: int,
    llm: Any,
    weather: Optional[Dict[str, Any]] = None,
    quota_check=None,
    log_usage_fn=None,
) -> Dict[str, Any]:
    """真正调用 LLM 生成激励语并写缓存（含超时保护）。"""
    try:
        if quota_check is not None:
            await quota_check()
        prompt = build_quote_user_prompt(str(weather.get("city", "深圳")) if weather else "深圳", weather)
        # 首页不为文案等待：超过 _QUOTE_LLM_TIMEOUT 直接降级模板池
        resp = await asyncio.wait_for(
            llm.ainvoke(
                [
                    ("system", QUOTE_SYSTEM_PROMPT),
                    ("human", prompt),
                ]
            ),
            _QUOTE_LLM_TIMEOUT,
        )
        text = getattr(resp, "content", "")
        if isinstance(text, list):  # 多模态 content 结构，取纯文本段
            text = "".join(p.get("text", "") for p in text if isinstance(p, dict))
        quotes = parse_quotes_response(str(text))
        if quotes:
            await _cache_set(cache_key, quotes, _quotes_ttl_seconds())
            if log_usage_fn is not None:
                try:
                    model_name = str(getattr(llm, "model_name", "") or "")
                    await log_usage_fn(feature="home-quote", model_name=model_name)
                except Exception as e:  # 计量旁路，失败不阻塞
                    logger.warning("home_widget log_usage 失败: %s", e)
            return {"quotes": quotes, "source": "ai"}
    except Exception as e:
        # 配额超限 / LLM 网络失败 / 超时一律静默降级模板池，首页永不报错
        logger.warning("home_widget AI 激励语生成降级模板池 tenant=%s: %s", tenant_id, e)
    return {"quotes": fallback_quotes(), "source": "template"}


async def generate_quotes(
    tenant_id: int,
    llm: Any,
    weather: Optional[Dict[str, Any]] = None,
    quota_check=None,
    log_usage_fn=None,
) -> Dict[str, Any]:
    """生成入口（供后台任务直接调用）：结果写入当日缓存。"""
    return await _generate_quotes(
        _quotes_cache_key(tenant_id), tenant_id, llm, weather, quota_check, log_usage_fn
    )


# 同租户当日的 LLM 生成去重（并发首页加载只发起一次）
_quotes_inflight: Dict[str, asyncio.Future] = {}
# 预热默认城市（进程刚起来、尚无人访问时先备好一份）
_DEFAULT_WARM_CITIES = ["深圳"]


async def get_quotes(
    tenant_id: int,
    llm: Any = None,
    weather: Optional[Dict[str, Any]] = None,
    quota_check=None,
    log_usage_fn=None,
    background: bool = False,
    bg_runner=None,
) -> Dict[str, Any]:
    """获取当日激励语池（5 条）。

    - Redis/内存缓存命中 → 直接返回（source=cache，~0ms）
    - background=True 且未命中 → 立即返回模板池（source=pending），
      同时后台发起一次生成（bg_runner）写入缓存；前端稍后重拉即可拿到 AI 版本
    - 其余未命中 → 同步调用 LLM（source=ai），失败/超时降级模板池（source=template）
    - 游客（llm=None）→ 模板池（source=template），零成本

    quota_check: async fn()（QuotaExceededException 语义对齐 quota_service）
    log_usage_fn: async fn(feature=..., model_name=...)
    bg_runner: async fn() —— 后台生成入口，内部须自带数据库会话（请求级 session 已关闭）
    """
    cache_key = _quotes_cache_key(tenant_id)
    cached = await _cache_get(cache_key)
    if cached:
        return {"quotes": cached, "source": "cache"}

    if llm is None:
        return {"quotes": fallback_quotes(), "source": "template"}

    if background and bg_runner is not None:
        inflight = _quotes_inflight.get(cache_key)
        if inflight is None or inflight.done():
            task = asyncio.get_running_loop().create_task(bg_runner())
            _quotes_inflight[cache_key] = task
            task.add_done_callback(lambda t: _quotes_inflight.pop(cache_key, None))
        return {"quotes": fallback_quotes(), "source": "pending"}

    return await _generate_quotes(
        cache_key, tenant_id, llm, weather, quota_check, log_usage_fn
    )
