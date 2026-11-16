# C 端公开范围与 SEO 地基设计（v2）

> 状态：**待评审，未实施**
> 版本：2026-10-28 v2（v1 只有 SEO 地基；v2 按「本该公开什么」补入访客公开范围与索引策略）
> 相关代码：`ui/src/app.tsx` / `ui/src/setting.tsx` / `ui/config/config.ts` / `ui/config/routes.ts` /
> `ui/public/` / `ui/src/pages/marketplace/*` / `ui/src/pages/Exchange/*` / `ui/src/pages/Runner/*` /
> `backend/twelvet-exchange/.../ExchangeSquareController.java`
> 相关文档：`CLAUDE.md`、`docs/ONBOARDING.md`、
> `docs/superpowers/specs/2026-10-27-tenant-isolation-design.md`（游客租户策略）

## 1. 背景：两个事实

### 事实一：现在没有任何页面是公开的

| 证据 | 内容 |
|---|---|
| `ui/src/app.tsx:61-75` | 除登录页外，所有路由都先 `fetchUserInfo()`，失败即 `history.push('/login')` |
| `ui/src/app.tsx:131-141` | `onPageChange` 同样把未登录访客重定向到登录页 |
| `ui/src/setting.tsx:71-75` | `authIgnore` 只有 `/login/oauth2/github` 与 `/ai/flow` |
| 接口层 | 商品 / 动态 / 他人主页已是 `@SaIgnore`（product 模块 22 处）；换物 3 个只读接口、互助全部为 `@SaCheckLogin` |

**结论：搜索引擎抓取 weiwei 只能看到登录页。** 不做访客公开，SEO 的一切工作都不会被收录。

### 事实二：没有线上站点

`ui/config/config.ts` 无 `ssr` / `exportStatic` / `prerender`；`ui/public/` 无 `robots.txt` / `sitemap.xml`；
全仓库无 canonical / OG / JSON-LD；`setting.tsx` 只有 OSS 图片域，ONBOARDING 无站点域名。

## 2. 公开范围决策（产品判断，不以改动量取舍）

判断标准有两条：

- **可公开浏览**：信息是发布者主动公开的交易/社区内容，且不含联系方式、精确地址、订单与资金信息
- **可被收录**：页面有独立检索价值、不是无限分页、不是私有状态、不会很快失效

| 页面 | 游客可浏览 | 可收录 | 依据 |
|---|---|---|---|
| `/home` 首页 | ✅ | ✅ | 门户，SEO 主入口 |
| `/search` 搜索 | ✅ | ❌ `noindex,follow` | 搜索结果页无独立价值，且无限分页 |
| `/product/detail/:id` 商品详情 | ✅ | ✅ | 长尾流量主力；字段仅含标题/描述/图/价格/城市/卖家昵称，无联系方式 |
| `/feed/stream` 动态广场 | ✅ | ✅ | 社区公开内容 |
| `/feed/detail/:feedId` 动态详情 | ✅ | ✅（仅 `approved`） | UGC；`pending` / `rejected` / 已删除必须 noindex |
| `/user/profile/:userId` 用户主页 | ✅ | ❌ `noindex,follow` | 公开主页有价值，但聚合型隐私（信用、关注、在售）+ 收录价值低 |
| `/exchange/square` 换物广场 | ✅ | ✅ | 公开置换信息 |
| `/exchange/detail/:id` 换物详情 | ✅ | ✅ | 字段仅含 `estValue` 估值、城市/区域，无联系方式 |
| `/runner/hall` 互助大厅 | ⚠️ **条件性** | ❌ | 原则上应公开（任务墙），但 `RunnerTask` 含 `pickupAddr` / `deliverAddr` / 经纬度，`hall` 直接返回完整实体。**必须先做脱敏 VO 才能公开**；本轮保持登录 |
| `/runner/task/detail` 互助详情 | ❌ | ❌ | 含取送地址与经纬度；任务时效性强，收录后很快变死页 |
| `/user/orders`、`/trade/*`、`/user/address`、`/user/settings` | ❌ | ❌ | 订单、资金、地址等私有信息 |
| `/product/publish`、`/product/my`、`/feed/publish`、`/exchange/publish`、`/exchange/my-*`、`/runner/task/publish`、`/runner/task/my-list` | ❌ | ❌ | 需登录的写操作与个人资产 |
| `/login`、`/account`、`/system`、`/admin`、`/Human`、`/Monitor`、AI 工作台、IM | ❌ | ❌ | 账户与后台 |

**租户口径**：游客只看默认租户（`tenant_code=default`），与
`2026-10-27-tenant-isolation-design.md` 的决策 1 一致，本设计不改变租户隔离策略。

## 3. SEO 决策

| # | 决策 | 取值 |
|---|---|---|
| D1 | 本轮范围 | 访客公开范围 + SEO 地基（meta / JSON-LD / robots / sitemap / 语义化 / 上线清单） |
| D2 | SSR / 预渲染 | 不做，Phase 3（见 §10） |
| D3 | URL 结构 | 不改 |
| D4 | 新增依赖 | 不加；`@umijs/max` 已导出 `Helmet` |
| D5 | 站点根 URL | `Settings.siteUrl`：优先 `UMI_APP_SITE_URL`，否则运行时 `window.location.origin`（跳过 localhost / 127.0.0.1）；两者都不可用时不输出 canonical 与绝对 URL 类产物 |
| D6 | 搜索页 | `noindex,follow` |
| D7 | 内容索引条件 | **只有 `onsale` 商品、`approved` 且未删除的动态可索引**；`sold` 输出 `OutOfStock` 但仍可保留索引；`draft` / `auditing` / `locked` / `offshelf` / `rejected` 及动态 `pending` / `rejected` / `deleted` → `noindex`，且页面明确展示不可交易状态 |
| D8 | 换物结构化数据 | 不套 `Product`：`estValue` 是主人自评估值，页面已注明「不构成交易价格」，当作 `Offer.price` 是发布虚假价格。只输出 `WebPage` + `BreadcrumbList` |
| D9 | 互助详情 canonical | 该路由用 query 参数（`/runner/task/detail?id=`），且不公开——从索引矩阵移除，不处理 |
| D10 | h1 口径 | 「内容区恰好一个 h1」；ProLayout 顶栏自带的 `<h1>` 不视为内容区（不为 SEO 改布局） |
| D11 | 社交分享预览 | **不是本轮收益**：微信 / Facebook / Slack 不执行 JS，看不到 Helmet 注入的 OG。仅 Google / Bing 能消费；预览要等 Phase 3 |

## 4. 目标 / 非目标 / 反目标

**目标**

1. 未登录访客可浏览 §2 中标 ✅ 的页面；登录动作仍在需要时触发
2. 每个可索引页面有唯一 `title`、`description`、`canonical`、`robots`，可索引页输出 JSON-LD
3. `robots.txt` 放行公开页、屏蔽私有页；`sitemap.xml` 覆盖固定页（动态页 Phase 2 由后端输出）
4. 语义化修正：内容区唯一 h1、图片 alt、可爬的站内链接
5. 交付上线即可执行的 `docs/SEO-AUDIT-CHECKLIST.md`

**非目标**

- SSR / 预渲染（Phase 3）
- 互助大厅的公开与脱敏（独立后续项，见 §10）
- 部署、域名、ICP、CDN
- 动态 sitemap 的后端实现（Phase 2，只立契约）
- 关键词运营、外链建设

**反目标**

- 为了 SEO 破坏现有构建/开发体验
- 改 URL 结构
- 在私有页面加 meta「假装做了 SEO」
- 把含地址、联系方式、资金信息的字段公开

## 5. 前置改造：访客浏览

### 5.1 前端

| 文件 | 改动 |
|---|---|
| `ui/src/setting.tsx` | 新增 `publicPaths`（前缀数组，对应 §2 的 ✅ 浏览项）与 `siteUrl` |
| `ui/src/app.tsx` | `getInitialState` 与 `onPageChange`：命中 `publicPaths` 时**不调用** `fetchUserInfo`、不重定向；未登录时返回 `currentUser: undefined` |
| `ui/src/app.tsx` / 布局 | 游客态：头像区渲染「登录」按钮；菜单只保留公开入口（首页/动态/换物/互助可点击但会引导登录） |
| 各公开页 | 需登录的动作（收藏、关注、点赞、下单、接单）在游客态点击时弹登录，不发请求 |

实现要点：`fetchUserInfo` 现在承担「刷新 Token + 拉菜单」，公开路径必须**整段跳过**，
否则接口 401 会再次触发跳转。菜单在游客态为空即可，不报错。

### 5.2 后端

| 文件 | 改动 |
|---|---|
| `ExchangeSquareController` | `/square`、`/item/{id}`、`/category/tree` 由 `@SaCheckLogin` 改为 `@SaIgnore`；`detail` 的 `userId` 改为 `StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null`（与 `UserProfileController.profile` 同款写法） |
| `ExchangeSquareController` 其余 | `offer/assign`、`recommend`、`credit` 保持 `@SaCheckLogin` |
| 互助模块 | **本轮不变**。`hall` 返回完整 `RunnerTask`（含取送地址与经纬度），在脱敏 VO 落地前不开放 |

商品 / 动态 / 他人主页的只读接口已具备游客能力，无需改 Java。

## 6. SEO 地基

### 6.1 `Seo` 组件

新建 `ui/src/components/Seo/index.tsx`，基于 `Helmet`。

```ts
interface SeoProps {
  title: string;
  description?: string;       // 缺省用站点文案；截断 155 字
  canonicalPath?: string;     // 站点内路径
  image?: string;             // 绝对 URL
  type?: 'website' | 'article' | 'product';
  noindex?: boolean;
  jsonLd?: object | object[];
}
```

输出：`<title>{title} - 邻里二手</title>`、`meta description`、`link canonical`（D5 生效时）、
`og:*`、`twitter:card`、`meta robots`、`script[type=application/ld+json]`。

### 6.2 页面接入矩阵

| 路由 | 文件 | title | description | JSON-LD | robots |
|---|---|---|---|---|---|
| `/home` | `marketplace/Home.tsx` | 邻里二手 - 同城闲置交易与互助 | 固定 | WebSite + Organization + SearchAction | index |
| `/search` | `marketplace/Search.tsx` | 搜索「{q}」 - 邻里二手 | 固定 | — | noindex,follow |
| `/product/detail/:id` | `marketplace/ProductDetail.tsx` | `{title} - 邻里二手` | 商品描述 | Product + Offer + BreadcrumbList | index（D7） |
| `/feed/stream` | `marketplace/FeedStream.tsx` | 动态广场 - 邻里二手 | 固定 | — | index |
| `/feed/detail/:feedId` | `marketplace/FeedDetail.tsx` | `{摘要前 20 字} - 邻里二手` | 摘要 | Article | index（仅 approved） |
| `/exchange/square` | `Exchange/Square.tsx` | 交换广场 - 邻里二手 | 固定 | — | index |
| `/exchange/detail/:id` | `Exchange/Detail.tsx` | `{title} - 邻里二手` | 物品描述 | WebPage + BreadcrumbList | index（D8） |
| `/user/profile/:userId` | `marketplace/UserProfile.tsx` | `{昵称} 的闲置 - 邻里二手` | 固定 | ProfilePage + Person | noindex,follow |

私有路由（§2 中所有 ❌）统一 `noindex,nofollow`：新建 `ui/src/wrappers/NoIndex.tsx`，
在 `routes.ts` 对应路由组挂 `wrappers`；若布局不兼容则逐页注入。

### 6.3 JSON-LD 纯函数

`ui/src/utils/seo.ts`：

| 函数 | 说明 |
|---|---|
| `resolveSiteUrl()` | `UMI_APP_SITE_URL` → `location.origin`（非 localhost）→ `''` |
| `absoluteUrl(path)` | 有 `siteUrl` 才返回绝对 URL，否则返回 `null`（调用方据此决定是否输出该字段） |
| `buildWebSiteJsonLd()` | WebSite + SearchAction → `/search?q={search_term_string}` |
| `buildOrganizationJsonLd()` | Organization（name / url / logo） |
| `buildProductJsonLd(p)` | Product：name / description / image[] / offers{price, CNY, availability, itemCondition, url} / seller(Person)；**`siteUrl` 为空或状态不允许时不输出** |
| `buildBreadcrumbJsonLd(items)` | 首页 > 商品 > {title} |
| `buildProfilePageJsonLd(u)` | ProfilePage + Person |

映射：`condition` `brand_new` → `NewCondition`，其余 → `UsedCondition`；`stock > 0` → `InStock`，
否则 `OutOfStock`；`sold` → `OutOfStock` + 页面标注已售；价格统一 `String(Number(price))`；
图片经 `staticUrl()` 转绝对 URL。

**绝对 URL 约束**：`WebSite.url`、`Organization.logo`、`Product.url`、`BreadcrumbList.item`、
`Offer.url` 都必须是绝对 URL。`siteUrl` 为空时**整段 JSON-LD 不输出**，宁缺勿错。

### 6.4 `robots.txt`

`ui/public/robots.txt`：

```
User-agent: *
Allow: /
Disallow: /login
Disallow: /account
Disallow: /system
Disallow: /admin
Disallow: /trade
Disallow: /user/orders
Disallow: /user/settings
Disallow: /user/address
Disallow: /product/publish
Disallow: /product/my
Disallow: /feed/publish
Disallow: /exchange/publish
Disallow: /exchange/my-
Disallow: /runner
```

`Sitemap:` 行需要绝对 URL，静态文件无法插值——`siteUrl` 确定后再补（写进上线清单）。

### 6.5 `sitemap.xml`

Phase 1 用构建脚本生成，而不是手写静态文件：

- 新建 `ui/scripts/gen-sitemap.js`，读 `UMI_APP_SITE_URL`，输出 `public/sitemap.xml`（固定页：
  `/home`、`/feed/stream`、`/exchange/square`）
- `package.json` 的 `build` 前挂 `prebuild`；环境变量为空则**跳过生成**并打印提示
- 产物加入 `.gitignore`（避免把某个环境的域名提交进仓库）

Phase 2（不在本轮）：后端 `GET /sitemap.xml` 输出在售商品 / 换物 / 已审动态，`lastmod` 取
`update_time`，单文件 ≤ 50,000 URL / 50 MB。部署时 Caddy 需把 `/sitemap.xml` 反代到 8080
并移除静态文件。

### 6.6 语义化修正

- `ProductDetail.tsx:217` 商品标题 `<h2>` → `<h1>`（保持视觉不变）
- 商品详情主图 `<Image>` 补 `alt={product.title}`；封面补 `loading="lazy"` 与宽高
- `ProductCard` 已有 `alt={title}`（`index.tsx:286`），不改
- `Home` 已有内容区 `<h1>邻里二手</h1>`，不改
- 站内主要导航使用 `Link`

### 6.7 路由 title

为 §6.2 的 8 条公开路由在 `routes.ts` 补 `title`。

## 7. 文件清单

**新建**：`ui/src/components/Seo/index.tsx`、`ui/src/components/Seo/index.test.tsx`、
`ui/src/utils/seo.ts`、`ui/src/utils/seo.test.ts`、`ui/src/wrappers/NoIndex.tsx`、
`ui/public/robots.txt`、`ui/scripts/gen-sitemap.js`、`docs/SEO-AUDIT-CHECKLIST.md`

**修改（前端）**：`ui/src/setting.tsx`、`ui/src/app.tsx`、`ui/config/routes.ts`、
`ui/package.json`、8 个公开页（Home / Search / ProductDetail / FeedStream / FeedDetail /
UserProfile / Exchange-Square / Exchange-Detail）

**修改（后端）**：`ExchangeSquareController.java`（3 个只读接口放开 + detail 游客 viewerId）

## 8. 测试与验收

- 前端单测：`seo.test.ts`（价格序列化、stock→availability、condition→itemCondition、
  `siteUrl` 为空时 JSON-LD 不输出、canonical 缺省）；`Seo` 组件用 `renderToStaticMarkup` +
  `HelmetProvider` 断言 head 输出
- 后端：`ExchangeSquareController` 游客访问 3 个接口返回 200；写接口仍 401
- `npm run tsc`、`npm run build`、`mvn -pl twelvet-exchange/twelvet-exchange-server test` 通过
- 手工：无痕窗口打开 `/home`、`/product/detail/1`、`/exchange/detail/1` 不跳登录；
  DevTools 检查 head；`dist/robots.txt` 存在；`UMI_APP_SITE_URL` 为空时无 `sitemap.xml`
- 无新增依赖

## 9. 上线后审计清单（交付物）

`docs/SEO-AUDIT-CHECKLIST.md`：

1. GSC + 百度搜索资源平台验证站点；补 `robots.txt` 的 Sitemap 行
2. 提交 sitemap；「网址检查」确认首页可抓取
3. Rich Results Test 验证 Product / BreadcrumbList
4. Lighthouse 移动端 SEO ≥ 95
5. `claude-seo` 的 `/seo audit <域名>` 跑基线
6. 月度：收录数、点击、CTR、索引覆盖率、软 404

## 10. 后续阶段

| 阶段 | 内容 | 触发条件 |
|---|---|---|
| Phase 2 | 后端动态 sitemap、`lastmod`、过期内容下架策略 | 站点上线且商品量 > 1k |
| Phase 2.5 | 互助大厅脱敏 VO（title / 类型 / 报酬 / 时间 / 模糊区域），再开放游客浏览 | 产品决定公开任务墙 |
| Phase 3 | Umi `ssr` + Node 服务，或 rendertron 式预渲染；OG 预览 | 上线 ≥ 4 周且百度收录为 0，或 GSC「已抓取未编入索引」占比高 |

## 11. 需要你拍板

| # | 问题 | 我的默认 |
|---|---|---|
| 1 | 公开范围按 §2 表执行？ | 是（互助保持登录，换物放开） |
| 2 | 用户主页是否允许收录 | 否，`noindex,follow` |
| 3 | 动态详情是否允许收录 | 是，仅 `approved` |
| 4 | 未配置域名时是否产出 sitemap | 否，构建时跳过并提示 |