# Weiwei 项目宣传片设计方案

> 目标：用 HyperFrames 生成一段 **~30 秒功能展示宣传片**，走 `product-launch-video` 路线。
> 受众：潜在用户 / 社区运营方 / 技术社区分享。
> 交付物：`videos/weiwei-promo/renders/video.mp4`（1080p / 30fps）。

---

## 一、项目定位与卖点梳理

依据 `README.md`，weiwei 是一个**面向社区 / 园区的多租户二手交易平台**，卖点集中在：

1. **业务广度**：二手市场 + 社区动态 + 跑腿互助 + 即时通讯，四大业务模块闭环
2. **AI 能力密集**：AI 导购、知识库 RAG、AI 内容生成（文案/配图/视频）、AI 初审、AI 识图填单 + AI 估价、社区日报、社区管家
3. **推荐系统**：六路召回 + 规则打分 + 向量/协同 + CTR 度量 + A/B 实验（差异化亮点）
4. **多租户**：租户隔离 + 套餐配额 + 隐私设置，面向 B 端落地

宣传向视频重点讲 **1 + 2 + 4**，推荐系统可作为"技术护城河"一句带过。

---

## 二、视频规格

| 项 | 值 |
| --- | --- |
| 时长 | 28~32 秒 |
| 分辨率 | 1920×1080 (16:9) |
| 帧率 | 30 fps |
| 场景数 | 8 个 |
| 配音 | 无（音乐 + 字幕） |
| 输出 | `videos/weiwei-promo/renders/video.mp4` |

**为什么不配解说**：30 秒节奏快，解说容易抢字幕信息量；且 HyperFrames 生成 BGM + 字幕已足够表达。如果后续要做 60/120 秒完整版，再加 TTS 解说。

---

## 三、场景脚本（STORYBOARD 骨架）

| # | 时长 | 场景 | 核心文案 | 视觉主体 |
|---|---|---|---|---|
| 01 | 0~3s | 开场钩子 | "你的社区，正在发生什么" | 大字标题 + 社区图标粒子汇聚 |
| 02 | 3~7s | 二手市场 | "邻里二手 · 一键发布，同城直卖" | 商品卡片列表滚动 + 价格标签弹出 |
| 03 | 7~10s | 社区动态 | "邻里动态 · 分享生活，关注邻居" | 帖子流卡片堆叠 |
| 04 | 10~13s | 跑腿互助 | "邻里跑腿 · 悬赏任务，接单即时" | 任务卡 + 接单倒计时动画 |
| 05 | 13~16s | 即时通讯 | "邻里 IM · 未读实时，沟通不打断" | 聊天气泡 + 未读红点闪烁 |
| 06 | 16~22s | AI 能力（重头） | "AI 全栈赋能：导购 · 内容生成 · 初审 · 识图估价 · 社区日报" | AI 图标网格 + 数据流动线 |
| 07 | 22~27s | 多租户 & 推荐 | "多租户隔离 · 六路召回推荐 · 面向 B 端落地" | 租户节点 + 推荐链路可视化 |
| 08 | 27~32s | 结束 | "Weiwei · 邻里二手与服务平台" + 二维码/项目地址 | Logo 定格 + 淡出 |

> 场景 06 是差异化重点，占 6 秒。其他场景 3~5 秒，符合快节奏。

---

## 四、视觉风格（FRAME DESIGN SYSTEM）

| 项 | 值 |
| --- | --- |
| 主色 | `#4F6BFF`（靛蓝，冷静科技感） |
| 辅色 | `#00C9A7`（薄荷绿，社区活力） |
| 强调色 | `#FFB703`（琥珀黄，用于数据高亮） |
| 背景 | 深色渐变 `#0B0F1E → #1A1F35` |
| 字体 | 中文 Noto Sans SC，英文 Inter |
| 动效基调 | 平滑曲线 + 弹性缓动（GSAP `expo.out` / `back.out`） |
| 转场 | 溶解 + 方向性位移（0.6s） |

**为什么不选浅色底**：30 秒快节奏 + AI 主题，深色底视觉冲击更强，也更符合 SaaS 宣传视频的主流审美。

---

## 五、音频

- **BGM**：HeyGen catalog 里挑 `tech / corporate / uplifting` 类，30 秒单曲
- **SFX**：场景切换点加轻微 whoosh 转场音（2~3 处）
- 不用 TTS 解说（见 §二）

---

## 六、技术栈与执行流程

### 6.1 技术栈

| 组件 | 用途 |
| --- | --- |
| HyperFrames | 视频合成框架（HTML → MP4） |
| npx hyperframes CLI | 项目初始化、校验、渲染 |
| HeyGen 媒体服务 | BGM/SFX/图标素材（需 `npx hyperframes auth` 登录） |
| GSAP | 时间轴动效 |
| Tailwind | 样式工具类 |

### 6.2 执行步骤

1. **本方案评审**（当前节点）
2. **启动 HyperFrames 项目**
   ```bash
   cd "D:/Program Files (x86)/hermes"
   npx hyperframes init weiwei-promo
   ```
3. **写 BRIEF.md**（把本方案固化成 HyperFrames 能读的意图文档）
4. **写 STORYBOARD.md + SCRIPT.md**（8 个场景逐帧细化）
5. **获取音频**：`npx hyperframes auth` → HeyGen catalog 选 BGM
6. **视觉设计**：填充每个场景的动画与素材清单
7. **构建帧**：逐场景写 `compositions/frames/NN-*.html`
8. **校验**：`npx hyperframes validate`
9. **渲染**：`npx hyperframes render` → `renders/video.mp4`
10. **交付**：把 MP4 复制到 `D:/Program Files (x86)/hermes/`

### 6.3 前置检查

- [x] Node 22 就绪
- [x] npx 10.9.4 就绪
- [ ] `npx hyperframes auth status` —— 检查 HeyGen 登录状态（首次运行前必查）
- [ ] 磁盘空间（30 秒 1080p MP4 约 10~20 MB，渲染中间文件约 200 MB）

---

## 七、素材来源

| 素材 | 来源 | 备注 |
| --- | --- | --- |
| Logo / 品牌名 | 直接用 "Weiwei" 文字 + 简洁图标 | 项目暂无品牌 Logo 文件 |
| 商品图/图标 | HeyGen catalog（icon library） | 需登录 |
| 社区场景背景 | CSS 渐变 + SVG 装饰（HyperFrames 内联） | 本地生成，无需外网 |
| BGM | HeyGen catalog | 需登录 |
| 二维码 | 场景 08 生成 `https://github.com/lidahen/weiwei` 二维码 | 用 svg-qr 库或在线生成 |

---

## 八、验收标准

- 总时长 28~32 秒
- 8 个场景按脚本顺序出现，转场平滑
- 中文文字清晰无截断
- BGM 音量适中，不盖过关键文字信息
- 文件大小 < 30 MB（便于微信/朋友圈分享）
- 无水印、无版权风险素材

---

## 九、可选扩展

如果 30 秒版本反响好，可扩展：

- **60 秒完整版**：加推荐系统细节（六路召回动画）+ 多租户架构图动画 + 管理后台仪表盘
- **120 秒深度版**：每个业务模块独立 10 秒 + 完整推荐链路可视化 + 数据看板
- **英文版**：面向国际社区分享

---

## 十、风险与预案

| 风险 | 应对 |
| --- | --- |
| HeyGen 未登录，BGM 拉不到 | 方案 A：用本地免版权音频库；方案 B：请用户登录 `npx hyperframes auth` |
| HyperFrames 渲染报中文字体缺失 | 手动安装 Noto Sans SC 或改用系统字体（微软雅黑） |
| 30 秒内容太挤 | 减少到 6 个场景，合并"IM"到"社区动态" |
| 视频风格与项目调性不符 | 先出场景 01 草稿帧确认风格，再批量生成其余场景 |

---

## 附录：参考链接

- HyperFrames 入口 skill：`video-creation/hyperframes`
- product-launch-video 工作流：`video-creation/product-launch-video`
- HyperFrames 核心技术：`video-creation/hyperframes-core`
- 项目 README：`F:\code\weiwei\README.md`
- 项目设计文档目录：`F:\code\weiwei\docs\`
