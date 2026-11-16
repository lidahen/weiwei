# 首页「今日好心情」小组件设计（天气 + 日期 + AI 激励语）

> 2026-09-10 · feature · 复杂度 M · 对应需求：二手交易市场首页小组件显示天气、日期、AI 生成的激励语

## 1. 需求与目标

在二手交易市场首页（`ui/src/pages/marketplace/Home.tsx`）Hero 区域下方新增一张横向小组件卡片：

- **天气**：城市 + 天气现象 + 当前温度（含今日最高/最低）
- **日期**：今天是几月几日 + 星期
- **激励语**：由 AI 生成，与天气/日期/二手交易场景结合；支持"换一句"
- 游客可见；任一依赖故障时静默降级，小组件永不报错

## 2. 架构与数据流（对齐现有三层链路）

```
[HomeWidget.tsx] ──GET /ai/assist/home-widget?city=深圳──> [AiAssistController @SaIgnore]
    ──透传 X-Tenant-Id / X-User-Id──> [ai-service GET /ai/assist/home-widget]
        ├─ 天气: open-meteo（geocoding + forecast，免费无 key）
        │        缓存 key weather:{city}:{hour} TTL 1h（Redis → 内存兜底）
        │        失败 → weather=null（前端隐藏天气块）
        ├─ 激励语: LLM（ai_model 表 model_type=LLM 默认模型）一次生成 5 条池
        │        缓存 key quotes:{tenant}:{北京日期} TTL 至次日 04:00
        │        游客/无模型/配额超限/异常 → 内置模板池按日期稳定轮换（零成本）
        └─ 配额: 仅 LLM 生成时 check_and_count；QuotaExceeded 静默降级（首页永不 429）
                 成功后 log_usage(feature="home-quote")
```

设计原则（与项目惯例对齐）：

1. **可选登录**：`@SaIgnore` + `StpUtil` try-catch（同 FeedController C 端模式），游客返回模板池；
   登录租户（有 X-Tenant-Id）每天仅触发 1 次 LLM 调用，成本可控。
2. **全链路静默降级**（对齐推荐系统 4.14 精神）：天气 API 挂 → 隐藏天气块；LLM 挂/配额超 → 模板池；Redis 挂 → 进程内存缓存。
3. **计费入统一账本**：ai_usage_daily / ai_usage_log，feature=home-quote。

## 3. API 设计

### GET /ai/assist/home-widget

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| city | string | 否 | 城市名（首页默认收货地址城市，默认"深圳"），截断 30 字 |

响应（CamelResponseModel，驼峰）：

```json
{
  "code": 200,
  "data": {
    "city": "深圳",
    "date": "2026-09-10",
    "weekday": "星期四",
    "weather": {
      "text": "多云",
      "icon": "⛅",
      "temp": 30.1,
      "tempMax": 32.0,
      "tempMin": 27.0
    },
    "quotes": ["...", "...", "...", "...", "..."],
    "source": "ai"
  }
}
```

- `weather` 可为 null（天气源故障）；`quotes` 恒为 5 条（模板兜底也 5 条）；`source`: `ai | template | cache`。
- Python 侧错误统一 ApiResponse{code,msg}，Java unwrapBody 透传——但本接口自身永不主动报错（模板兜底）。

## 4. 数据模型

不新增数据库表。缓存结构：

| key | 值 | TTL |
|---|---|---|
| `home_widget:weather:{city}:{yyyyMMddHH}` | {text, icon, temp, temp_max, temp_min} | 3600s |
| `home_widget:quotes:{tenant_id}:{yyyyMMdd}` | [5 条激励语] | 至次日 04:00（秒数动态计算） |

Redis 不可用时退化为进程内 dict（与 redis_client.py 优雅降级惯例一致）。

## 5. 关键实现点

- **WMO weather_code → 中文文案 + emoji**：0 晴☀️ / 1-3 多云🌤⛅☁️ / 45,48 雾🌫️ / 51-67 雨🌧️ / 71-77 雪❄️ / 80-82 阵雨🌦️ / 95-99 雷雨⛈️。
- **时区**：统一 Asia/Shanghai（固定 UTC+8，目标用户为中国社区场景），避免服务器时区漂移导致缓存 key 跨天抖动。
- **LLM Prompt**：要求输出 JSON `{"quotes": [...]}`，每条 ≤20 字、口语化、结合当天天气/星期与"闲置好物/社区生活"场景，禁止空洞口号堆砌；解析失败按行兜底。
- **模板池**：24 条高质量中文激励语，`templates[day_of_year % 24]` 起始向后取 5 条循环，保证同一天所有游客看到的 5 条一致且逐日轮换。
- **open-meteo 超时**：geocoding 5s / forecast 5s，httpx AsyncClient；单次进程内并发去重（同城市同时刻只打一次外网）。

## 6. 前端 UI

- 位置：`Home.tsx` Hero 区之后、Tab 之前。
- 组件：`ui/src/components/marketplace/HomeWidget/index.tsx`。
- 风格：marketplaceTokens 白卡片 + 1px 边框 + radius.md；内部三段式 —— 天气（emoji 大图标 + 温度 + 现象/城市）｜日期（"9月10日" + 星期四 + 年份）｜激励语（引号装饰 + 换一句按钮）；`>=768px` 横向排列，小屏纵向。
- 交互：加载中 shimmer 骨架；`换一句` 本地轮换 quotes 池（不重复请求）；天气为 null 时隐藏天气块。
- 数据：城市复用首页 `cityName`（默认收货地址城市，回落"深圳"）。

## 7. 错误处理策略

| 故障 | 行为 |
|---|---|
| open-meteo 不可达/超时 | weather=null，其余正常 |
| LLM 未配置/调用失败 | 模板池（source=template） |
| 配额超限（QuotaExceeded） | 模板池，不返回 429 |
| Redis 不可用 | 进程内缓存兜底（容量 100 条 LRU-ish 截断） |
| 前端请求失败 | 组件仍渲染本地日期 + 固定默认激励语 |

## 8. 测试策略

`ai-service/tests/test_home_widget.py`（pytest，不访问外网）：

- WMO 映射正确性；模板池同日稳定、逐日轮换、恒 5 条
- LLM 响应解析（纯 JSON / ```json 围栏 / 每行一条兜底 / 空响应拒绝）
- 缓存命中不触发 LLM（mock llm 计数）；配额超限降级模板
- 城市名清洗（去"市"后缀、空值回落）

前端 `cd ui && npm run tsc` 类型检查；Java 随项目编译验证。
