# C 端公开范围与 SEO 地基实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让未登录访客能浏览商品/动态/换物等公开页面，并让这些页面具备被搜索引擎正确抓取与收录的 title、meta、canonical、JSON-LD、robots 与 sitemap。

**Architecture:** 前端新增「公开路径判定」与「登录守卫」两个纯工具，`app.tsx` 据此放行游客；新增 `Seo` 组件（`react-helmet-async`，Umi 内置同一 provider）与 `seo.ts` 纯函数生成 head 与 JSON-LD；后端只放开换物 3 个只读接口。SEO 产物一律要求站点根 URL 存在，缺失时降级为不输出，避免本地地址被收录。

**Tech Stack:** Umi Max 4.1.1 / React 18 / TypeScript / Jest（`@umijs/max/test`）/ react-helmet-async 1.3 / Sa-Token。

**Spec:** `docs/superpowers/specs/2026-10-28-seo-foundation-design.md`

## Global Constraints

- 不新增运行时依赖（`react-helmet-async` 已是 `dependencies`）
- 不改 URL 结构；不引入 SSR / 预渲染
- 站点名固定 `邻里二手`；title 模板固定 `{页面标题} - 邻里二手`
- 所有 meta 的 description 截断到 **155** 字
- 可索引规则：商品 `status ∈ {onsale, sold}`；动态 `status === 'normal' && auditStatus === 'approved'`
- 换物物品**不得**输出 `Offer.price`（`estValue` 是估值，不是售价）
- 站点根 URL 解析顺序：`process.env.UMI_APP_SITE_URL` → `window.location.origin`（localhost / 127.0.0.1 视为无效）→ 空
- 根 URL 为空时：不输出 canonical、不输出 JSON-LD、构建时跳过 sitemap 生成
- 后端只读接口放开，写接口与互助模块一律不动
- 代码格式跟随仓库现有风格（4 空格缩进、单引号）

## Review Focus

1. **过期 token 的游客打开公开页**：必须不跳登录、不白屏，页面照常渲染，只是没有头像/菜单。
2. **已登录用户打开公开页**：头像、菜单、关注 Tab 与改动前一致。
3. **非在售商品**（`offshelf` / `rejected` / `locked` / `draft`）被直接访问：页面不输出 Product JSON-LD，且 `meta robots` 为 `noindex`。
4. **根 URL 为空（本地开发）**：head 里没有 canonical、没有 JSON-LD，页面不报错；`npm run build` 不生成 `public/sitemap.xml` 且打印提示。
5. **游客访问换物详情**：不发起 `offerReceived` / `itemWanters` 等需登录请求，不弹出「发起交换」入口，页面仍完整展示物品。

---

### Task 1: 公开路径判定工具

**Files:**
- Create: `ui/src/utils/publicAccess.ts`
- Test: `ui/src/utils/publicAccess.test.ts`

**Interfaces:**
- Produces: `isPublicPath(pathname: string): boolean`

- [ ] **Step 1: Write the failing test**

```ts
import { isPublicPath } from './publicAccess';

describe('isPublicPath', () => {
    it.each([
        '/home',
        '/home/',
        '/search',
        '/feed/stream',
        '/feed/detail/9',
        '/exchange/square',
        '/exchange/detail/3',
        '/product/detail/12',
        '/user/profile/7',
    ])('公开路径 %s 返回 true', (path) => {
        expect(isPublicPath(path)).toBe(true);
    });

    it.each([
        '/login',
        '/product/publish',
        '/product/my',
        '/feed/publish',
        '/exchange/my-items',
        '/exchange/publish',
        '/runner/hall',
        '/runner/task/detail',
        '/trade/pay',
        '/system/menu',
        '/admin/exchange',
        '/user/orders',
        '/user/profile/me',
    ])('私有路径 %s 返回 false', (path) => {
        expect(isPublicPath(path)).toBe(false);
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- src/utils/publicAccess.test.ts --runInBand`
Expected: FAIL — `Cannot find module './publicAccess'`

- [ ] **Step 3: Implement**

`ui/src/utils/publicAccess.ts`：

```ts
/** 游客可浏览的路径（与设计文档 §2 一致）。前缀必须以 `/` 结尾，避免 /product/my 匹配到 /product/detail。 */
const PUBLIC_EXACT = ['/home', '/search', '/feed/stream', '/exchange/square'];

const PUBLIC_PREFIX = [
    '/product/detail/',
    '/feed/detail/',
    '/exchange/detail/',
    '/user/profile/',
];

/** 命中公开前缀但属于私有页面的精确路径 */
const PRIVATE_EXACT = ['/user/profile/me'];

function normalize(pathname: string): string {
    if (pathname.length > 1 && pathname.endsWith('/')) return pathname.slice(0, -1);
    return pathname;
}

export function isPublicPath(pathname: string): boolean {
    const path = normalize(pathname);
    if (PRIVATE_EXACT.includes(path)) return false;
    if (PUBLIC_EXACT.includes(path)) return true;
    return PUBLIC_PREFIX.some((prefix) => path.startsWith(prefix) && path.length > prefix.length);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- src/utils/publicAccess.test.ts --runInBand`
Expected: PASS（22 个用例）

- [ ] **Step 5: Commit**

```bash
git add ui/src/utils/publicAccess.ts ui/src/utils/publicAccess.test.ts
git commit -m "feat(seo): 新增游客公开路径判定

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 登录守卫工具

**Files:**
- Create: `ui/src/utils/loginGuard.ts`
- Test: `ui/src/utils/loginGuard.test.ts`

**Interfaces:**
- Consumes: `getToken()` from `@/utils/twelvet`
- Produces: `isLoggedIn(): boolean`、`requireLogin(): boolean`

- [ ] **Step 1: Write the failing test**

```ts
import { history } from '@umijs/max';
import { getToken } from '@/utils/twelvet';
import { isLoggedIn, requireLogin } from './loginGuard';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/utils/twelvet', () => ({ getToken: jest.fn() }));

const mockGetToken = getToken as unknown as jest.Mock;
const mockPush = history.push as unknown as jest.Mock;

describe('loginGuard', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('有 token 时 isLoggedIn 为 true', () => {
        mockGetToken.mockReturnValue('token-1');
        expect(isLoggedIn()).toBe(true);
    });

    it('无 token 时 requireLogin 跳登录并返回 false', () => {
        mockGetToken.mockReturnValue('');
        expect(requireLogin()).toBe(false);
        expect(mockPush).toHaveBeenCalledWith('/login');
    });

    it('有 token 时 requireLogin 不跳转并返回 true', () => {
        mockGetToken.mockReturnValue('token-1');
        expect(requireLogin()).toBe(true);
        expect(mockPush).not.toHaveBeenCalled();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- src/utils/loginGuard.test.ts --runInBand`
Expected: FAIL — `Cannot find module './loginGuard'`

- [ ] **Step 3: Implement**

```ts
import { history } from '@umijs/max';
import { getToken } from '@/utils/twelvet';

export function isLoggedIn(): boolean {
    return !!getToken();
}

/** 需登录动作的守卫：未登录时跳登录页并返回 false */
export function requireLogin(): boolean {
    if (isLoggedIn()) return true;
    history.push('/login');
    return false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- src/utils/loginGuard.test.ts --runInBand`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src/utils/loginGuard.ts ui/src/utils/loginGuard.test.ts
git commit -m "feat(seo): 新增登录守卫工具

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `app.tsx` 游客放行

**Files:**
- Modify: `ui/src/app.tsx:22-80`（`getInitialState`）、`:131-141`（`onPageChange`）

**Interfaces:**
- Consumes: `isPublicPath`（Task 1）、`getToken`（`@/utils/twelvet`）
- Produces: `fetchUserInfo(redirectOnFailure?: boolean)`——登录页会以无参形式调用，默认 `true`

- [ ] **Step 1: 修改 `fetchUserInfo` 签名**

把 `const fetchUserInfo = async () => {` 改为
`const fetchUserInfo = async (redirectOnFailure = true) => {`；
catch 分支改为：

```ts
} catch (error) {
    if (redirectOnFailure) history.push(loginPath);
    return undefined;
}
```

同时把 `if (code !== 200) { return message.error(msg); }` 改为：

```ts
if (code !== 200) {
    if (redirectOnFailure) message.error(msg);
    return undefined;
}
```

- [ ] **Step 2: 改写 `getInitialState` 的分支**

把 `:60-75` 的整段替换为：

```ts
const pathname = history.location.pathname;

if (pathname !== loginPath) {
    if (isPublicPath(pathname) || TWT.authIgnore.includes(pathname)) {
        // 公开页：有 token 就尝试恢复登录态，失败静默；无 token 直接当游客
        const currentUser = getToken() ? await fetchUserInfo(false) : undefined;
        return { fetchUserInfo, currentUser, settings: defaultSettings };
    }

    const currentUser = await fetchUserInfo();
    timedRefreshToken();
    return { fetchUserInfo, currentUser, settings: defaultSettings };
}
return { fetchUserInfo, settings: defaultSettings };
```

新增 import：`import { getToken } from '@/utils/twelvet';` 与
`import { isPublicPath } from '@/utils/publicAccess';`（若已有 `@/utils/twelvet` 导入则合并）。

- [ ] **Step 3: 改写 `onPageChange`**

```ts
onPageChange: () => {
    const { location } = history;
    if (
        !initialState?.currentUser &&
        !loginPath.includes(location.pathname) &&
        !isPublicPath(location.pathname) &&
        !TWT.authIgnore.includes(location.pathname)
    ) {
        history.push(loginPath);
    }
},
```

- [ ] **Step 4: 验证**

Run: `cd ui && npm run tsc`
Expected: 无错误

手工验证（Task 12 统一做）：无痕窗口打开 `/home` 不跳登录；已登录打开 `/home` 仍有头像。

- [ ] **Step 5: Commit**

```bash
git add ui/src/app.tsx
git commit -m "feat(auth): 公开页放行未登录访客

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: 后端换物只读接口放开

**Files:**
- Modify: `backend/twelvet-exchange/twelvet-exchange-server/src/main/java/com/twelvet/exchange/server/controller/ExchangeSquareController.java`

**Interfaces:**
- Produces: 游客可读 `GET /exchange/square`、`GET /exchange/item/{id}`、`GET /exchange/category/tree`

- [ ] **Step 1: 放开三个只读接口**

把 `square`、`detail`、`categoryTree` 三个方法上的 `@SaCheckLogin` 改为 `@SaIgnore`，
并新增 `import cn.dev33.satoken.annotation.SaIgnore;`。

`detail` 方法内：

```java
Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
```

其余方法（`recommend` / `credit`）保持 `@SaCheckLogin`；`ExchangeOfferController`、
`ExchangeOrderController`、`ExchangeItemController` 不动。

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn -q -pl twelvet-exchange/twelvet-exchange-server -am -DskipTests compile`
Expected: BUILD SUCCESS（无输出即成功）

- [ ] **Step 3: 静态检查**

Run: `cd backend && grep -n "SaCheckLogin\|SaIgnore" twelvet-exchange/twelvet-exchange-server/src/main/java/com/twelvet/exchange/server/controller/ExchangeSquareController.java`
Expected: `square` / `detail` / `categoryTree` 上为 `@SaIgnore`，`recommend` / `credit` 上为 `@SaCheckLogin`

- [ ] **Step 4: Commit**

```bash
git add backend/twelvet-exchange/twelvet-exchange-server/src/main/java/com/twelvet/exchange/server/controller/ExchangeSquareController.java
git commit -m "feat(exchange): 换物广场与详情只读接口开放游客访问

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

> 说明：`@SaIgnore` 由 Sa-Token 拦截器在运行期生效，纯单元测试无法证明；
> 游客 200 / 写接口 401 的验证放在 Task 12 的手工冒烟里。

---

### Task 5: `seo.ts` 纯函数

**Files:**
- Create: `ui/src/utils/seo.ts`
- Test: `ui/src/utils/seo.test.ts`

**Interfaces:**
- Consumes: `TWT.static`（`@/setting`）
- Produces:
  - `SITE_NAME: string`、`SITE_DESCRIPTION: string`
  - `resolveSiteUrl(envUrl?: string, origin?: string): string`
  - `absoluteUrl(path: string, siteUrl: string): string | null`
  - `isProductIndexable(status?: string): boolean`
  - `isFeedIndexable(feed: { status?: string; auditStatus?: string }): boolean`
  - `buildWebSiteJsonLd(siteUrl: string): object | null`
  - `buildOrganizationJsonLd(siteUrl: string): object | null`
  - `buildProductJsonLd(product: SeoProduct | null | undefined, siteUrl: string): object | null`
  - `buildBreadcrumbJsonLd(items: { name: string; path: string }[], siteUrl: string): object | null`
  - `buildArticleJsonLd(feed: SeoFeed | null | undefined, siteUrl: string): object | null`
  - `buildProfilePageJsonLd(profile: SeoProfile | null | undefined, siteUrl: string): object | null`

入参类型（与后端实体字段同名）：

```ts
export interface SeoProduct {
    productId?: number | string;
    title?: string;
    description?: string;
    images?: string | string[];
    cover?: string;
    price?: number | string;
    stock?: number;
    status?: string;
    city?: string;
    sellerNickName?: string;
    condition?: string;
}

export interface SeoFeed {
    content?: string;
    status?: string;
    auditStatus?: string;
    publishTime?: string;
    createTime?: string;
    userNickName?: string;
}

export interface SeoProfile {
    nickName?: string;
    userId?: number | string;
    signature?: string;
    onsaleCount?: number;
}
```

- [ ] **Step 1: Write the failing test**

```ts
import {
    buildArticleJsonLd,
    buildBreadcrumbJsonLd,
    buildProductJsonLd,
    buildWebSiteJsonLd,
    isFeedIndexable,
    isProductIndexable,
    resolveSiteUrl,
} from './seo';

describe('resolveSiteUrl', () => {
    it('优先使用构建时环境变量并去掉尾部斜杠', () => {
        expect(resolveSiteUrl('https://weiwei.test/', 'http://localhost:8000')).toBe('https://weiwei.test');
    });
    it('无环境变量时用运行时域名', () => {
        expect(resolveSiteUrl('', 'https://thu.weiwei.test')).toBe('https://thu.weiwei.test');
    });
    it('localhost / 127.0.0.1 视为无效', () => {
        expect(resolveSiteUrl('', 'http://localhost:8000')).toBe('');
        expect(resolveSiteUrl('', 'http://127.0.0.1:8000')).toBe('');
    });
    it('两者都没有时返回空串', () => {
        expect(resolveSiteUrl('', '')).toBe('');
    });
});

describe('isProductIndexable', () => {
    it.each(['onsale', 'sold'])('%s 可索引', (status) => expect(isProductIndexable(status)).toBe(true));
    it.each(['draft', 'auditing', 'locked', 'offshelf', 'rejected', undefined])(
        '%s 不可索引',
        (status) => expect(isProductIndexable(status)).toBe(false),
    );
});

describe('isFeedIndexable', () => {
    it('normal + approved 可索引', () =>
        expect(isFeedIndexable({ status: 'normal', auditStatus: 'approved' })).toBe(true));
    it.each([
        { status: 'deleted', auditStatus: 'approved' },
        { status: 'normal', auditStatus: 'pending' },
        { status: 'normal', auditStatus: 'rejected' },
        { status: 'normal' },
    ])('%o 不可索引', (feed) => expect(isFeedIndexable(feed)).toBe(false));
});

describe('buildProductJsonLd', () => {
    const product = {
        productId: 12,
        title: '九成新机械键盘',
        description: '自用一年，功能正常',
        images: '["/upload/a.jpg","/upload/b.jpg"]',
        price: 199.0,
        stock: 1,
        status: 'onsale',
        condition: '90',
        city: '深圳',
        sellerNickName: '阿黎',
    };

    it('输出 Product，价格去尾零，图片转 OSS 绝对地址', () => {
        const ld: any = buildProductJsonLd(product, 'https://weiwei.test');
        expect(ld['@type']).toBe('Product');
        expect(ld.name).toBe('九成新机械键盘');
        expect(ld.offers.price).toBe('199');
        expect(ld.offers.priceCurrency).toBe('CNY');
        expect(ld.offers.availability).toBe('https://schema.org/InStock');
        expect(ld.offers.itemCondition).toBe('https://schema.org/UsedCondition');
        expect(ld.offers.url).toBe('https://weiwei.test/product/detail/12');
        expect(ld.image[0]).toMatch(/^https:\/\/your-bucket\.oss-cn-beijing\.aliyuncs\.com\//);
        expect(ld.seller).toEqual({ '@type': 'Person', name: '阿黎' });
    });

    it('sold 或 stock=0 输出 OutOfStock', () => {
        const ld: any = buildProductJsonLd({ ...product, status: 'sold', stock: 0 }, 'https://weiwei.test');
        expect(ld.offers.availability).toBe('https://schema.org/OutOfStock');
    });

    it('非在售状态返回 null', () => {
        expect(buildProductJsonLd({ ...product, status: 'offshelf' }, 'https://weiwei.test')).toBeNull();
    });

    it('siteUrl 为空返回 null', () => {
        expect(buildProductJsonLd(product, '')).toBeNull();
    });
});

describe('buildBreadcrumbJsonLd', () => {
    it('输出带绝对 URL 的面包屑', () => {
        const ld: any = buildBreadcrumbJsonLd(
            [
                { name: '首页', path: '/home' },
                { name: '商品', path: '/search' },
                { name: '九成新机械键盘', path: '/product/detail/12' },
            ],
            'https://weiwei.test',
        );
        expect(ld['@type']).toBe('BreadcrumbList');
        expect(ld.itemListElement).toHaveLength(3);
        expect(ld.itemListElement[2].item).toBe('https://weiwei.test/product/detail/12');
    });
});

describe('buildArticleJsonLd', () => {
    it('approved 动态输出 Article', () => {
        const ld: any = buildArticleJsonLd(
            { content: '出闲置键盘', status: 'normal', auditStatus: 'approved', publishTime: '2026-10-01T10:00:00', userNickName: '阿黎' },
            'https://weiwei.test',
        );
        expect(ld['@type']).toBe('Article');
        expect(ld.headline).toBe('出闲置键盘');
        expect(ld.author).toEqual({ '@type': 'Person', name: '阿黎' });
    });
    it('pending 动态返回 null', () => {
        expect(
            buildArticleJsonLd({ content: 'x', status: 'normal', auditStatus: 'pending' }, 'https://weiwei.test'),
        ).toBeNull();
    });
});

describe('buildWebSiteJsonLd', () => {
    it('包含站内搜索 SearchAction', () => {
        const ld: any = buildWebSiteJsonLd('https://weiwei.test');
        expect(ld.potentialAction.target).toBe('https://weiwei.test/search?q={search_term_string}');
        expect(buildWebSiteJsonLd('')).toBeNull();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- src/utils/seo.test.ts --runInBand`
Expected: FAIL — `Cannot find module './seo'`

- [ ] **Step 3: Implement**

要点（实现者按此写，签名见 Interfaces）：

- `resolveSiteUrl`：`envUrl` 去尾斜杠后非空即返回；否则校验 `origin` 以 `http(s)://` 开头且
  hostname 不是 `localhost` / `127.0.0.1`（用 `new URL(origin)` 取 hostname），通过则返回去尾斜杠的 origin，否则 `''`
- `absoluteUrl(path, siteUrl)`：`siteUrl` 为空返回 `null`；`path` 已是 `http(s)` 开头原样返回；否则 `${siteUrl}${path.startsWith('/') ? path : '/' + path}`
- `isProductIndexable`：`status === 'onsale' || status === 'sold'`
- `isFeedIndexable`：`feed?.status === 'normal' && feed?.auditStatus === 'approved'`
- 内部 `parseImages(raw: unknown): string[]`：字符串先 `JSON.parse`（失败返回 `[]`），数组直接用；
  每项经 `toImageUrl` 转绝对地址——非 http 开头则 `${TWT.static}${path}`
- `buildProductJsonLd`：`siteUrl` 为空或 `!isProductIndexable(product?.status)` 返回 `null`；
  `offers.price = String(Number(product.price))`；`availability` 由 `status === 'sold' || !(stock > 0)` 决定；
  `itemCondition`：`condition === 'brand_new'` → `NewCondition`，其余 `UsedCondition`；
  `seller` 仅在 `sellerNickName` 存在时输出
- `buildArticleJsonLd`：`siteUrl` 为空或 `!isFeedIndexable` 返回 `null`；`headline` 取 `content` 前 110 字
- `buildProfilePageJsonLd`：`siteUrl` 为空或 `!profile?.nickName` 返回 `null`；
  `{ '@type': 'ProfilePage', mainEntity: { '@type': 'Person', name, url } }`
- `buildWebSiteJsonLd` / `buildOrganizationJsonLd`：`siteUrl` 为空返回 `null`；URL 统一 `${siteUrl}/`，
  logo 固定 `${siteUrl}/logo.svg`
- 所有 JSON-LD 第一项均为 `'@context': 'https://schema.org'`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- src/utils/seo.test.ts --runInBand`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src/utils/seo.ts ui/src/utils/seo.test.ts
git commit -m "feat(seo): 新增 JSON-LD 与站点 URL 纯函数

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: `Seo` 组件

**Files:**
- Create: `ui/src/components/Seo/index.tsx`
- Test: `ui/src/components/Seo/index.test.tsx`

**Interfaces:**
- Consumes: `resolveSiteUrl`、`SITE_NAME`、`SITE_DESCRIPTION`（Task 5）
- Produces: `<Seo title? description? canonicalPath? image? type? noindex? jsonLd? />`

- [ ] **Step 1: Write the failing test**

```tsx
import React from 'react';
import { renderToString } from 'react-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import Seo from './index';

function headOf(element: React.ReactElement) {
    const context: any = {};
    renderToString(<HelmetProvider context={context}>{element}</HelmetProvider>);
    return context.helmet;
}

describe('Seo', () => {
    beforeEach(() => {
        process.env.UMI_APP_SITE_URL = 'https://weiwei.test';
    });
    afterEach(() => {
        delete process.env.UMI_APP_SITE_URL;
    });

    it('输出 title、description、canonical、OG 与 JSON-LD', () => {
        const helmet = headOf(
            <Seo
                title="九成新机械键盘"
                description="自用一年"
                canonicalPath="/product/detail/12"
                image="https://img.test/a.jpg"
                jsonLd={{ '@type': 'Product' }}
            />,
        );
        expect(helmet.title.toString()).toContain('<title>九成新机械键盘 - 邻里二手</title>');
        expect(helmet.meta.toString()).toContain('name="description"');
        expect(helmet.meta.toString()).toContain('property="og:title"');
        expect(helmet.meta.toString()).toContain('content="index,follow"');
        expect(helmet.link.toString()).toContain('href="https://weiwei.test/product/detail/12"');
        expect(helmet.script.toString()).toContain('application/ld+json');
    });

    it('noindex 时输出 noindex,follow', () => {
        const helmet = headOf(<Seo title="搜索" noindex />);
        expect(helmet.meta.toString()).toContain('content="noindex,follow"');
    });

    it('无 title 时只输出站点名', () => {
        const helmet = headOf(<Seo />);
        expect(helmet.title.toString()).toContain('<title>邻里二手</title>');
    });

    it('站点 URL 为空时不输出 canonical 与 JSON-LD script', () => {
        delete process.env.UMI_APP_SITE_URL;
        const helmet = headOf(<Seo title="首页" canonicalPath="/home" jsonLd={{ '@type': 'Thing' }} />);
        expect(helmet.link.toString()).not.toContain('canonical');
        expect(helmet.script.toString()).not.toContain('application/ld+json');
    });

    it('jsonLd 为 null 时不渲染 script', () => {
        const helmet = headOf(<Seo title="首页" jsonLd={null} />);
        expect(helmet.script.toString()).not.toContain('application/ld+json');
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- src/components/Seo/index.test.tsx --runInBand`
Expected: FAIL — `Cannot find module './index'`

- [ ] **Step 3: Implement**

`ui/src/components/Seo/index.tsx` 要点：

- `import { Helmet } from 'react-helmet-async';`（Umi 的 provider 来自同一个包，见 `src/.umi/core/helmet.ts`）
- props 见 Interfaces；`title` 可缺省
- `const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, typeof window !== 'undefined' ? window.location.origin : '')`
- `const fullTitle = title ? `${title} - ${SITE_NAME}` : SITE_NAME`
- description 截断：`description ? description.slice(0, 155) : SITE_DESCRIPTION`
- canonical：`siteUrl && canonicalPath` 时 `absoluteUrl(canonicalPath, siteUrl)`，非空才渲染 `<link rel="canonical">`
- OG：`og:title` / `og:description` / `og:type`（默认 `website`）/ `og:site_name`；`og:url` 仅 canonical 存在时；
  `og:image` 仅 `image` 存在时；`twitter:card` 固定 `summary_large_image`
- robots：`noindex ? 'noindex,follow' : 'index,follow'`
- jsonLd：数组过滤 `null`/`undefined`，为空则不渲染；否则渲染一个
  `<script type="application/ld+json">{JSON.stringify(payload)}</script>`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- src/components/Seo/index.test.tsx --runInBand`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src/components/Seo/index.tsx ui/src/components/Seo/index.test.tsx
git commit -m "feat(seo): 新增统一 head 注入组件

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: 首页 / 搜索 / 动态流 / 动态详情接入

**Files:**
- Modify: `ui/src/pages/marketplace/Home.tsx`
- Modify: `ui/src/pages/marketplace/Search.tsx`
- Modify: `ui/src/pages/marketplace/FeedStream.tsx`
- Modify: `ui/src/pages/marketplace/FeedDetail.tsx`

**Interfaces:**
- Consumes: `Seo`（Task 6）、`buildWebSiteJsonLd` / `buildOrganizationJsonLd` / `buildArticleJsonLd` / `isFeedIndexable` /
  `resolveSiteUrl`（Task 5）、`isLoggedIn` / `requireLogin`（Task 2）

- [ ] **Step 1: 首页**

- `import Seo from '@/components/Seo';` 以及 `buildWebSiteJsonLd, buildOrganizationJsonLd, resolveSiteUrl`
- 在页面根节点（`return (` 的第一个子元素之前）渲染：

```tsx
const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin);
<Seo
    title="邻里二手 - 同城闲置交易与互助"
    description="邻里二手是同城闲置交易与互助平台：发布二手商品、以物换物、社区动态与邻里互助。"
    canonicalPath="/home"
    jsonLd={[buildWebSiteJsonLd(siteUrl), buildOrganizationJsonLd(siteUrl)]}
/>
```

- 关注 Tab 游客不可用：`const visibleTabs = isLoggedIn() ? TABS : TABS.filter((t) => t.key !== 'follow');`
  并把 `TABS.map` 改为 `visibleTabs.map`

- [ ] **Step 2: 搜索页**

在根节点渲染：

```tsx
<Seo
    title={`搜索「${q || '全部'}」`}
    description="在邻里二手搜索同城闲置商品，支持关键词、类目与城市筛选。"
    canonicalPath="/search"
    noindex
/>
```

- [ ] **Step 3: 动态流**

```tsx
<Seo
    title="动态广场"
    description="邻里二手社区动态：同城闲置、换物与互助信息实时更新。"
    canonicalPath="/feed/stream"
/>
```

- [ ] **Step 4: 动态详情**

数据变量是 `feed`（`FeedDetail.tsx:20`）：

```tsx
const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin);
<Seo
    title={(feed?.content || '动态详情').slice(0, 20)}
    description={(feed?.content || '').slice(0, 155)}
    canonicalPath={`/feed/detail/${feedId}`}
    noindex={!isFeedIndexable(feed)}
    jsonLd={buildArticleJsonLd(feed, siteUrl)}
/>
```

点赞/评论入口：点击处理函数开头加 `if (!requireLogin()) return;`。

- [ ] **Step 5: 验证**

Run: `cd ui && npm run tsc`
Expected: 无错误

- [ ] **Step 6: Commit**

```bash
git add ui/src/pages/marketplace/Home.tsx ui/src/pages/marketplace/Search.tsx ui/src/pages/marketplace/FeedStream.tsx ui/src/pages/marketplace/FeedDetail.tsx
git commit -m "feat(seo): 首页、搜索、动态页接入 head 与结构化数据

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: 商品详情 / 用户主页

**Files:**
- Modify: `ui/src/pages/marketplace/ProductDetail.tsx`
- Modify: `ui/src/pages/marketplace/UserProfile.tsx`

**Interfaces:**
- Consumes: Task 5、Task 6、Task 2

- [ ] **Step 1: 商品详情接入**

数据变量 `product`（`ProductDetail.tsx:61`）：

```tsx
const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin);
const indexable = isProductIndexable(product?.status);
<Seo
    title={product?.title || '商品详情'}
    description={product?.description || `邻里二手在售：${product?.title || ''}`}
    canonicalPath={`/product/detail/${id}`}
    image={staticUrl(product?.cover)}
    type="product"
    noindex={!indexable}
    jsonLd={[
        buildProductJsonLd(product, siteUrl),
        buildBreadcrumbJsonLd(
            [
                { name: '首页', path: '/home' },
                { name: '商品', path: '/search' },
                { name: product?.title || '商品详情', path: `/product/detail/${id}` },
            ],
            siteUrl,
        ),
    ]}
/>
```

- [ ] **Step 2: 语义化修正**

- `ProductDetail.tsx:217` 的 `<h2 style={{ marginBottom: 12 }}>{product.title}</h2>`
  改为 `<h1 style={{ marginBottom: 12 }}>{product.title}</h1>`
- 主图 `<Image ...>` 增加 `alt={product.title}`
- 收藏 / 立即购买点击处理开头加 `if (!requireLogin()) return;`

- [ ] **Step 3: 用户主页接入**

```tsx
<Seo
    title={`${profile?.nickName || '用户'} 的闲置`}
    description={profile?.signature || '邻里二手用户主页：在售闲置与交易信用。'}
    canonicalPath={`/user/profile/${userId}`}
    noindex
    jsonLd={buildProfilePageJsonLd(profile, resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin))}
/>
```

关注按钮点击处理开头加 `if (!requireLogin()) return;`。

- [ ] **Step 4: 验证**

Run: `cd ui && npm run tsc && npm test -- src/utils --runInBand`
Expected: 无 TS 错误，工具测试通过

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/marketplace/ProductDetail.tsx ui/src/pages/marketplace/UserProfile.tsx
git commit -m "feat(seo): 商品详情与用户主页接入 head，补齐 h1 与图片 alt

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: 换物广场 / 换物详情

**Files:**
- Modify: `ui/src/pages/Exchange/Square.tsx`
- Modify: `ui/src/pages/Exchange/Detail.tsx`

**Interfaces:**
- Consumes: Task 5、Task 6、Task 2

- [ ] **Step 1: 广场**

```tsx
<Seo
    title="交换广场"
    description="邻里二手交换广场：以物换物，按类目、成色与交付方式筛选邻居的闲置。"
    canonicalPath="/exchange/square"
/>
```

「为你推荐」请求仅在登录时发起：`useEffect` 内先 `if (!isLoggedIn()) return;`（依赖数组加 `[]` 不变）。

- [ ] **Step 2: 详情**

数据变量 `item`（`Exchange/Detail.tsx:135`）：

```tsx
<Seo
    title={item?.title || '换物详情'}
    description={item?.description || `邻里二手换物：${item?.title || ''}`}
    canonicalPath={`/exchange/detail/${id}`}
    noindex={!item || item?.status !== 'onswap'}
    jsonLd={buildBreadcrumbJsonLd(
        [
            { name: '首页', path: '/home' },
            { name: '交换广场', path: '/exchange/square' },
            { name: item?.title || '换物详情', path: `/exchange/detail/${id}` },
        ],
        resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin),
    )}
/>
```

**不得**调用 `buildProductJsonLd`（物品没有售价，`estValue` 是估值）。
不传 `image`：`Exchange/utils.ts` 未导出 `staticUrl`，且社交预览不在本轮收益内（D11）。

- [ ] **Step 3: 游客守卫**

```tsx
const loggedIn = isLoggedIn();
const { data: receivedResp, refresh: refreshReceived } = useRequest(
    () => (loggedIn ? offerReceived() : Promise.resolve(null)),
    { refreshDeps: [id, loggedIn] },
);
const { data: wantersResp } = useRequest(
    () => (loggedIn && id ? itemWanters(id, { limit: 6 }) : Promise.resolve(null)),
    { refreshDeps: [id, loggedIn] },
);
```

「发起交换」按钮：`loggedIn` 为 false 时不渲染，或点击时 `if (!requireLogin()) return;`。

- [ ] **Step 4: 验证**

Run: `cd ui && npm run tsc`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/Exchange/Square.tsx ui/src/pages/Exchange/Detail.tsx
git commit -m "feat(seo): 换物页接入 head，游客不发需登录请求

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: robots.txt 与 sitemap 生成

**Files:**
- Create: `ui/public/robots.txt`
- Create: `ui/scripts/gen-sitemap.js`
- Create: `ui/scripts/gen-sitemap.test.js`
- Modify: `ui/package.json`（新增 `prebuild`）
- Modify: `ui/.gitignore`（忽略 `public/sitemap.xml`）

**Interfaces:**
- Produces: `buildSitemapXml(siteUrl: string, paths: string[], lastmod: string): string`

- [ ] **Step 1: Write the failing test**

```js
const { buildSitemapXml, SITE_PATHS } = require('./gen-sitemap');

describe('buildSitemapXml', () => {
    it('输出带绝对 loc 的标准 sitemap', () => {
        const xml = buildSitemapXml('https://weiwei.test', SITE_PATHS, '2026-10-28');
        expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
        expect(xml).toContain('<loc>https://weiwei.test/home</loc>');
        expect(xml).toContain('<loc>https://weiwei.test/feed/stream</loc>');
        expect(xml).toContain('<loc>https://weiwei.test/exchange/square</loc>');
        expect(xml).not.toContain('/product/detail');
        expect(xml).toContain('<lastmod>2026-10-28</lastmod>');
    });

    it('siteUrl 为空时抛错，避免产出非法 sitemap', () => {
        expect(() => buildSitemapXml('', SITE_PATHS, '2026-10-28')).toThrow();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- scripts/gen-sitemap.test.js --runInBand`
Expected: FAIL — `Cannot find module './gen-sitemap'`

- [ ] **Step 3: Implement `scripts/gen-sitemap.js`**

```js
const fs = require('fs');
const path = require('path');

const SITE_PATHS = ['/home', '/feed/stream', '/exchange/square'];
const OUTPUT = path.resolve(__dirname, '../public/sitemap.xml');

function buildSitemapXml(siteUrl, paths, lastmod) {
    const base = String(siteUrl || '').replace(/\/+$/, '');
    if (!base) throw new Error('UMI_APP_SITE_URL 为空，跳过 sitemap 生成');
    const urls = paths
        .map((p) => `  <url><loc>${base}${p}</loc><lastmod>${lastmod}</lastmod></url>`)
        .join('\n');
    return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
}

if (require.main === module) {
    const siteUrl = process.env.UMI_APP_SITE_URL || '';
    if (!siteUrl) {
        console.log('[gen-sitemap] UMI_APP_SITE_URL 未设置，跳过 sitemap 生成');
        process.exit(0);
    }
    const lastmod = new Date().toISOString().slice(0, 10);
    fs.writeFileSync(OUTPUT, buildSitemapXml(siteUrl, SITE_PATHS, lastmod), 'utf8');
    console.log(`[gen-sitemap] 已生成 ${OUTPUT}`);
}

module.exports = { buildSitemapXml, SITE_PATHS, OUTPUT };
```

- [ ] **Step 4: 新增 `public/robots.txt`**

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
Disallow: /user/profile/me
Disallow: /product/publish
Disallow: /product/my
Disallow: /feed/publish
Disallow: /exchange/publish
Disallow: /exchange/my-
Disallow: /runner
```

- [ ] **Step 5: 接入构建**

`ui/package.json` 的 `scripts` 增加：

```json
"prebuild": "node scripts/gen-sitemap.js",
```

`ui/.gitignore` 追加一行：`public/sitemap.xml`

- [ ] **Step 6: 验证**

Run: `cd ui && npm test -- scripts/gen-sitemap.test.js --runInBand`
Expected: PASS

Run: `cd ui && UMI_APP_SITE_URL=https://weiwei.test node scripts/gen-sitemap.js && cat public/sitemap.xml`
Expected: 生成 3 个 `<loc>`，均为绝对 URL；随后执行 `rm public/sitemap.xml`

Run: `cd ui && node scripts/gen-sitemap.js`
Expected: 打印「UMI_APP_SITE_URL 未设置，跳过 sitemap 生成」，退出码 0

- [ ] **Step 7: Commit**

```bash
git add ui/public/robots.txt ui/scripts/gen-sitemap.js ui/scripts/gen-sitemap.test.js ui/package.json ui/.gitignore
git commit -m "feat(seo): 新增 robots.txt 与构建期 sitemap 生成

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: 路由 title 补全

**Files:**
- Modify: `ui/config/routes.ts`

**Interfaces:**
- Consumes: 无
- Produces: 8 条公开路由的 `title`（Umi 据此设置 `document.title`，`Seo` 组件负责最终覆盖）

- [ ] **Step 1: 补 title**

| path | title |
|---|---|
| `/home` | `首页` |
| `/search` | `搜索` |
| `/product/detail/:id` | `商品详情` |
| `/feed/stream` | `动态广场` |
| `/feed/detail/:feedId` | `动态详情` |
| `/exchange/square` | `交换广场` |
| `/exchange/detail/:id` | `换物详情` |
| `/user/profile/:userId` | `用户主页` |

- [ ] **Step 2: 验证**

Run: `cd ui && npm run tsc`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add ui/config/routes.ts
git commit -m "feat(seo): 公开路由补齐页面标题

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 12: 上线后审计清单

**Files:**
- Create: `docs/SEO-AUDIT-CHECKLIST.md`

- [ ] **Step 1: 写清单**

内容必须包含（按顺序）：

1. 上线前：确认 `UMI_APP_SITE_URL` 已设置为正式域名，`dist/robots.txt` 与 `dist/sitemap.xml` 均存在
2. 上线后：`robots.txt` 末尾补 `Sitemap: https://<域名>/sitemap.xml`
3. Google Search Console 与百度搜索资源平台验证站点
4. 提交 sitemap；用「网址检查」确认首页与一个商品页可抓取
5. Rich Results Test 验证 Product / BreadcrumbList 无错误
6. Lighthouse 移动端 SEO ≥ 95
7. `claude-seo` 的 `/seo audit <域名>` 跑一次，记录基线
8. 月度观察：收录数、点击、CTR、索引覆盖率、软 404
9. 触发 Phase 3 的条件：百度收录为 0，或 GSC「已抓取未编入索引」占比偏高

- [ ] **Step 2: Commit**

```bash
git add docs/SEO-AUDIT-CHECKLIST.md
git commit -m "docs(seo): 新增上线后审计清单

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 13: 全量验证

**Files:** 无（只运行验证）

- [ ] **Step 1: 前端类型与单测**

Run: `cd ui && npm run tsc && npm test -- --runInBand`
Expected: 类型无错误；全部测试通过

- [ ] **Step 2: 前端构建（两种环境各一次）**

Run: `cd ui && npm run build`
Expected: 构建成功；`public/sitemap.xml` 未生成（日志含跳过提示）；`dist/robots.txt` 存在

Run: `cd ui && UMI_APP_SITE_URL=https://weiwei.test npm run build && test -f dist/sitemap.xml && grep -c "<loc>" dist/sitemap.xml`
Expected: 构建成功；`dist/sitemap.xml` 存在且含 3 个 `<loc>`

- [ ] **Step 3: 后端编译**

Run: `cd backend && mvn -q -pl twelvet-exchange/twelvet-exchange-server -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手工冒烟（需本地 MySQL / Redis 与后端运行）**

| 场景 | 期望 |
|---|---|
| 无痕打开 `/home` | 不跳登录，商品可浏览，「关注」Tab 不出现 |
| 无痕打开 `/product/detail/1` | 不跳登录，head 有 title/description；非在售商品无 Product JSON-LD |
| 无痕打开 `/exchange/square` 与 `/exchange/detail/1` | 不跳登录；详情不发起意向/推荐请求 |
| 未登录直接调 `GET /exchange/square` | 200 |
| 未登录直接调 `POST /exchange/offer` | 401 |
| 登录后打开 `/home` | 头像、菜单、关注 Tab 与改动前一致 |
| 打开 `/system/menu` | 仍跳登录 |
| 设置 `UMI_APP_SITE_URL` 后构建 | `dist/sitemap.xml` 含 3 条绝对 URL |

- [ ] **Step 5: 记录结果**

把每条的实际输出填进 PR 描述；未通过项不得标记完成。

---

## 实现备注（与 spec 的偏差，需在完成时说明）

1. **未做 `NoIndex` wrapper**：私有页在未登录时本就重定向，登录后也不应被爬（`robots.txt` 已 `Disallow`）。
   Umi 的 `/user` 路由组公私混排，父级 wrapper 需要额外处理 `Outlet`，收益不抵风险。
2. **`Seo` 组件直接 import `react-helmet-async`**：Umi 的 `Helmet` 就是该包的再导出，
   Umi 内部 Provider（`src/.umi/core/helmet.ts`）与之同源；直接 import 让 Jest 可独立测试。
3. **后端 `@SaIgnore` 无自动化测试**：Sa-Token 注解在拦截器运行期生效，单元测试无法证明；
   以编译 + 手工冒烟为准。
4. **换物详情不输出 Product JSON-LD**：`estValue` 是估值，`offers` 必填会导致虚假价格；
   只输出面包屑。