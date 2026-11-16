# 二手平台 C 端页面修复与优化 · 测试指引

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 更新日期：2026-08-30
> 范围：marketplace 全部 C 端页签的功能修复、布局优化、信息展示与交互动画
> 状态：代码已完成并通过 tsc 类型检查 + 生产构建；**浏览器实测未完成**（原测试账号无 token），待切换账号后按本文档清单逐项验证

---

## 一、本轮做了什么

### 1.1 高危功能修复（8 个，均已改完待验证）

| # | 页面 | 问题 | 修复 | 预期验证结果 |
|---|---|---|---|---|
| 1 | 动态广场 `/feed/stream` | 后端返回 `JsonResult<List>`，前端解包错 → `list.map` 抛 TypeError **整页白屏崩溃** | 解包改为 `res?.data` + 数组守卫 | 广场能正常显示动态列表，不再崩溃 |
| 2 | 动态详情 `/feed/detail/:id` | 响应未解包 + `JSON.parse(images)` 无防护 → 详情内容全空白 | 解包 `data` 字段 + try/catch 防护 | 详情页正文/图片/评论正常显示 |
| 3 | 动态发布 `/feed/publish` | 读 `file.url`（antd 上传后不存在）→ 提交时 TypeError **发布必失败**；`onRemove` 签名错误 | 改从 `file.response.data` 取相对路径回填 `url`；onRemove 修正 | 能正常上传图片（有缩略图）、发布成功 |
| 4 | 关注列表 `/user/follows` | 后端返回 snake_case（`user_id/nick_name`），前端读 camelCase → **昵称空白、跳转 undefined** | 字段全部对齐 snake_case | 昵称正常显示，点头像能进对方主页 |
| 5 | 收银台 `/trade/pay` | 轮询依赖 URL 的 `payNo`，但从订单进入时 URL 没有该参数 → **支付成功永远检测不到**；「查看订单」跳不存在路由 404 | `payCreate` 返回的 `payNo` 接线进轮询；死路由改 `/user/orders`；补 closed/failed 状态与超时手动刷新 | 付款后 3 分钟内页面自动变「支付成功」 |
| 6 | 支付结果 `/trade/pay/result` | 展示字段（camelCase）对不上后端 `trade_pay` 表（snake_case）→ 详情区全空 | 字段对齐 `pay_amount/pay_channel/paid_at/out_trade_no` | 支付金额/时间/方式正常显示 |
| 7 | 订单列表/详情 | 后端支付成功状态是 `paid`，前端状态映射没有该键 → **已付款订单显示英文原文、步骤条错位、「待发货」Tab 查不到** | 前端补 `paid` 映射；后端 `TradeOrderMapper.xml` 支持逗号分隔多状态，「待发货」Tab 查 `unshipped,paid` | 已付款订单正确显示「已付款/待发货」标签和步骤条 |
| 8 | 商品编辑 `/product/publish/:id` | 编辑回显的图片无 `response` 字段被过滤 → 路径为空 **不改图就提交不了** | `getImagePath` 增加 url 回退还原路径；草稿序列化只存路径数组 | 不改图直接点发布能成功；草稿保存/恢复正常 |

### 1.2 其他修复（中低）

- **商品详情 `/product/detail/:id`**：`buyNow` 把 orderNo 伪造当 payNo 传给收银台（必查无此单）→ 已改为只带 orderId；图片解析加防护；金额格式化（千分位）。
- **头像/图片破图**：Chat 消息图、会话列表头像、用户设置头像、订单商品图全部补 `TWT.static` 前缀 + 加载失败占位图（"图片走丢了"）。
- **确认收货**：订单列表/详情均加二次确认弹窗 + 错误提示（原来静默调用无反馈）。
- **订单详情时间**：ISO 串改为相对时间格式化。
- **Feed 详情**：回复可取消（原来只能切换对象）；评论数可点击聚焦输入框。

### 1.3 布局 / 信息展示 / 动画优化

- **全局微交互基建**（`marketplace/styles/motion.tsx`，挂在 app.tsx 一次性生效）：
  - `.twt-fade-up` 内容进场淡入上移（列表带 stagger 逐条延迟）
  - `.twt-pop` 点赞回弹动画
  - `.twt-shimmer` 骨架屏流光
  - `.twt-press` 按压缩放反馈
  - `prefers-reduced-motion` 用户自动禁用全部动画
- **骨架屏组件** `components/marketplace/SkeletonCards`：搜索页、动态广场加载中显示与卡片同构的流光骨架（替代原来的转圈，避免布局跳动）。
- **工具函数** `marketplace/utils.ts`：`staticUrl`（静态资源拼接+绝对地址透传）、`formatTime`（刚刚/N 分钟前/昨天 HH:mm）、`formatPrice`（千分位）、`IMG_FALLBACK`（占位图）。
- **搜索页**：筛选栏卡片化（白底描边投影）+ 结果计数高亮 + 清空条件按钮 + 空态带关键字提示 + 列表逐条进场动画。
- **动态广场**：单图大图展示（原来强制三等分）、关联商品卡片化（橙色底+标签）、分享按钮可点击（复制链接）、点赞有回弹动画、内容限宽 760 居中阅读。
- **订单详情**：左右两栏改响应式（`xs=24 lg=16/8`，移动端不再溢出）；收货信息地址拼接（省市区详）。
- **订单列表**：表格加横向滚动（移动端）；翻页参数修正（原来硬编码第 1 页假分页）。

### 1.4 本轮新增的公共文件

| 文件 | 用途 |
|---|---|
| `ui/src/pages/marketplace/styles/motion.tsx` | 全局动画原子类（GlobalStyle） |
| `ui/src/pages/marketplace/utils.ts` | staticUrl / formatTime / formatPrice / IMG_FALLBACK |
| `ui/src/components/marketplace/SkeletonCards/index.tsx` | 卡片网格骨架屏 |

---

## 二、环境启动（已验证的步骤）

前置：MySQL（3306）与 Redis（6379）服务已由用户启动。

```bash
# 1. ai-service（端口 8001）
cd E:\code\weiwei\ai-service
python start.py
# 健康检查：curl http://localhost:8001/health → {"status":"ok"}
# 注意：连 MySQL8 需要 cryptography 包（已 pip install）

# 2. backend（端口 8080）—— 改过 Java 代码后需要重新打包
cd E:\code\weiwei\backend
mvn -q -pl twelvet-admin -am package -DskipTests
java -jar twelvet-admin/target/twelvet-admin.jar

# 3. ui（端口 8000）
cd E:\code\weiwei\ui
npm run dev
```

**已知坑：ui dev server 首启时报 `dependence not found: E:/code/weiwei/.worktrees/ai-cend/ui/node_modules/...`**
→ 原因：MFSU 缓存指向旧 worktree 路径。解决：删 `ui/node_modules/.cache` 和 `ui/.mfsu` 后重启；首编约 10 秒，之后 MFSU buildDepsAgain 阶段如卡住，重启一次 `npm run dev` 即可。

验证方式：`curl http://localhost:8000/` 返回 200 且页面不再是"正在加载资源"。

## 三、测试账号

均来自 `docs/db/twelvet-fast_2026-10-21.sql` 的 `sys_user` 预置数据，密码统一 `123456`：

| 账号 | 昵称 | 角色 | 用途 |
|---|---|---|---|
| `buyer_demo` | 阿花-买买买 | 普通用户 | 下单/收藏/关注/AI 导购主测号 |
| `seller_demo` | 老张-数码铺 | 卖家 | 卖出列表/发货 |
| `ops_demo` | 平台运营小张 | 运营 | 管理端 |
| `cs_demo` | 纠纷仲裁老李 | 客服 | 纠纷仲裁 |

---

## 四、测试清单（按优先级）

### A. 上轮修复点的回归验证（高优先）

- [ ] **A1 动态广场**：登录后访问 `/feed/stream` → 列表正常渲染（不白屏）；图片单张时大图展示；点赞有回弹动画；分享按钮点击复制链接
- [ ] **A2 动态详情**：点任意动态进详情 → 正文/图片/评论可见（不再空白）；点「回复」后出现「取消回复」按钮
- [ ] **A3 动态发布**：`/feed/publish` 拖拽上传 2 张图 → 缩略图正常显示（不破图）→ 填内容发布 → 成功跳回广场
- [ ] **A4 关注列表**：`/user/follows` → 两个 Tab 昵称/头像/时间正常（不空白）；点头像进对方主页
- [ ] **A5 下单全链路**：商品详情 → 立即购买 → 收银台出现二维码 →（手动把 DB 里 `trade_pay.status` 改 `paid` 模拟支付成功，或走真实沙箱）→ 页面 3 分钟内自动变「支付成功」→ 点「查看订单」进入 `/user/orders`（不再 404）
- [ ] **A6 订单状态**：支付后订单列表显示「已付款」标签；「待发货」Tab 能看到该订单（多状态 IN 查询）；订单详情步骤条停在「待发货」
- [ ] **A7 商品编辑**：`/product/my` 进编辑 → 不动图片直接保存 → 成功提示「已重新提交审核」（原来必报"请至少上传一张商品图片"）
- [ ] **A8 支付结果页**：带 `?payNo=xxx` 访问 `/trade/pay/result` → 金额/方式/时间正常显示（不再全空）

### B. 布局 / 动画观察项（中优先）

- [ ] B1 搜索页 `/search`：筛选栏卡片化样式；加载时流光骨架屏；结果逐条淡入；清空条件按钮
- [ ] B2 订单详情：窗口缩到手机宽度（F12 设备模拟）→ 左右栏变单列，无横向溢出
- [ ] B3 确认收货：订单列表点「确认收货」→ 弹二次确认框
- [ ] B4 全局动画：各列表页进场有淡入上移；系统开"减少动态效果"时动画关闭
- [ ] B5 头像/图片：IM 聊天、会话列表、用户设置页头像不再破图

### C. AI 导购（依赖 ai-service 已启动）

- [ ] C1 C 端右下角悬浮 AI 导购 → 发「找一台 500 元内的机械键盘」→ 气泡内先出现「正在为你查找商品…」状态提示（status 事件），随后流式回答 + 商品卡片
- [ ] C2 搜索无结果场景（如「找一台外星飞船」）→ 回复中应说明放宽了条件/推荐热门，而非冷冰冰"没有找到"
- [ ] C3 点回复气泡下的小喇叭 → 朗读播放；再点停止（TTS 走 SiliconFlow，依赖 .env 的 EMBEDDING_API_KEY）
- [ ] C4 确认流程：说「下单第一个」→ 复述订单+地址等确认 → 回「确认」→ 下单成功；再测回「不买了」→ 不应产生订单（上轮安全修复）
- [ ] C5 Redis 持久化：重启 ai-service 后继续同一会话 → 上下文仍在（Redis checkpointer 生效，日志有"Agent checkpointer：Redis"字样；若 Redis 缺模块会自动回退内存版并在日志告警）

### D. 已知未完成项（不在本轮范围，测试时跳过）

- **5 个占位页**：`/trade/checkout`（确认订单）、`/trade/ship`（发货）、`/trade/review`（评价）、`/trade/refund/apply`、`/trade/refund/detail` —— 均为"业务功能开发中"占位，订单详情里对应的按钮会进入这些空页（属预期）
- 后端部分接口（feed/stream、favorites 等）本身不接收分页参数，翻页是全量数据前端分页（后端改造未做）
- 商品发布页的字典加载失败无兜底提示（低优）

---

## 五、测试中如遇问题

1. **页面白屏**：F12 看 Console 是否有 `list.map is not a function` 类报错 → 记录接口 URL 和响应结构，反馈给我对齐解包
2. **接口 404**：确认 backend jar 是重新打包过的（含订单多状态 SQL 和 TTS 透传）
3. **AI 导购无响应**：看 ai-service 控制台日志（路由/工具调用都有 info 日志）；确认 8080 的 Java 侧 `ai.service.url` 指向 8001
4. **Redis 相关**：ai-service 启动日志若出现 `Redis checkpointer 不可用...回退 InMemorySaver`，功能不受影响但重启丢会话——检查 Redis 是否带了 RediSearch 模块（本地裸 redis-server 会出现，compose 里用的是 redis-stack 镜像）
