"""IP 归属地城市识别（首页定位默认值）。

GET /ai/assist/ip-city 的业务实现：

- 客户端 IP 从代理链路头提取：X-Client-Ip（Java 侧 IpUtils 显式透传）
  > X-Forwarded-For 首段 > X-Real-IP > 直连地址（request.client.host）；
- 私网/回环地址无法定位，返回 city=None（前端按「手动选择 > IP 定位 > 默认城市」回落）；
- 公网 IP 走 ip-api.com 免费接口（lang=zh-CN，中文城市名），按 IP 缓存 24h
  （Redis 优先、进程内存兜底，复用 home_widget 的双层缓存）；
- 任何失败静默返回 city=None —— 定位失败不影响首页可用性。

选型说明：生产站点为 HTTP（非 HTTPS），浏览器 Geolocation API 在非安全上下文不可用，
IP 归属地是当前唯一可行的自动定位方式；后续升级 HTTPS 后可叠加 GPS 精确定位。
ip-api 免费档仅限 HTTP 出站 + 45 次/分钟，首页场景按 IP 缓存后远低于限额。
"""

import logging
from typing import Any, Dict, Optional

import httpx
from fastapi import Request

from app.config import settings
from app.services.home_widget import _cache_get, _cache_set

logger = logging.getLogger(__name__)

_IPAPI_URL = "http://ip-api.com/json/{ip}"
_IPAPI_TIMEOUT = 3.5
# 腾讯位置服务 IP 定位（合规白名单厂商，key 免费申请 lbs.qq.com，仅存服务端 .env）
_TENCENT_IP_URL = "https://apis.map.qq.com/ws/location/v1/ip"
_TENCENT_TIMEOUT = 3.5
_IP_TTL = 24 * 3600
# 负缓存：私网/定位失败的 IP 短暂记住，避免每次首页加载都重打外网
_IP_NEGATIVE_TTL = 600


async def extract_client_ip(request: Request) -> Optional[str]:
    """从代理链路头提取真实客户端 IP；解析不出返回 None。

    信任链：Caddy 反代时附加 X-Forwarded-For，Java IpUtils.getIpAddr 解析后
    以 X-Client-Ip 显式透传 —— 优先级最高（语义最明确）。
    """
    for header in ("X-Client-Ip", "X-Forwarded-For", "X-Real-Ip"):
        raw = request.headers.get(header)
        if not raw:
            continue
        # X-Forwarded-For 可能是「客户端, 代理1, 代理2」链，取首段（最接近真实客户端）
        ip = raw.split(",")[0].strip()
        if ip:
            return ip
    client = getattr(request, "client", None)
    return getattr(client, "host", None) if client else None


def _is_public_ip(ip: str) -> bool:
    """仅公网地址可定位；回环/私网/链路本地/未指定地址一律排除。"""
    import ipaddress

    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return False
    return addr.is_global


async def _lookup_city_tencent(client: httpx.AsyncClient, ip: str) -> Optional[str]:
    """腾讯位置服务 IP 定位：返回市级中文名（如「郑州市」）。

    响应结构：{status: 0, result: {ad_info: {province, city, district, adcode}, ...}}。
    取 ad_info.city（市级）；个别号段 city 为空时回落 province。
    """
    key = (settings.tencent_lbs_key or "").strip()
    if not key:
        return None
    try:
        resp = await client.get(_TENCENT_IP_URL, params={"ip": ip, "key": key})
        resp.raise_for_status()
        data = resp.json() or {}
    except Exception as e:
        logger.warning("ip_city 腾讯 LBS 查询失败 ip=%s: %s", ip, e)
        return None
    if int(data.get("status", -1)) != 0:
        logger.info("ip_city 腾讯 LBS 非零状态 ip=%s status=%s", ip, data.get("status"))
        return None
    ad = ((data.get("result") or {}).get("ad_info") or {})
    city = str(ad.get("city") or "").strip()
    return (city or str(ad.get("province") or "").strip())[:30] or None


async def _lookup_city_ipapi(client: httpx.AsyncClient, ip: str) -> Optional[str]:
    """ip-api 免 key 兜底：中文返回，但对部分国内号段粒度到区（如「管城」）。"""
    try:
        resp = await client.get(
            _IPAPI_URL.format(ip=ip),
            params={"lang": "zh-CN", "fields": "status,city"},
        )
        resp.raise_for_status()
        data = resp.json() or {}
    except Exception as e:
        logger.warning("ip_city ip-api 查询失败 ip=%s: %s", ip, e)
        return None
    if data.get("status") == "success" and str(data.get("city") or "").strip():
        return str(data["city"]).strip()[:30]
    return None


async def lookup_city(ip: Optional[str]) -> Optional[str]:
    """公网 IP → 中文城市名（如「郑州」）；失败/私网返回 None。

    数据源链：腾讯 LBS（配了 key 时，市级中文）→ ip-api（免 key，中文）。
    结果按 IP 缓存 24h；失败负缓存 10min。缓存层复用 home_widget 的
    Redis+内存双层实现（Redis 不可用时自动降级进程内）。
    """
    if not ip or not _is_public_ip(ip):
        return None

    cache_key = f"home_widget:ipcity:{ip}"
    cached = await _cache_get(cache_key)
    if cached is not None:
        return cached.get("city")

    city: Optional[str] = None
    async with httpx.AsyncClient(timeout=max(_TENCENT_TIMEOUT, _IPAPI_TIMEOUT)) as client:
        city = await _lookup_city_tencent(client, ip)
        if city is None:
            city = await _lookup_city_ipapi(client, ip)

    # 成功与失败都写缓存：成功 24h，失败负缓存 10min
    await _cache_set(cache_key, {"city": city}, _IP_TTL if city else _IP_NEGATIVE_TTL)
    if city is None:
        logger.info("ip_city 全部数据源未识别到城市 ip=%s", ip)
    return city


async def detect_city(request: Request) -> Dict[str, Any]:
    """ip-city 端点的业务入口：返回 {ip, city}，city 可为 None。"""
    ip = await extract_client_ip(request)
    if not ip:
        return {"ip": None, "city": None}
    city = await lookup_city(ip)
    return {"ip": ip, "city": city}
