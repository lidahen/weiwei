# 上线后 SEO 审计清单

> 适用版本：C 端 SEO 地基（`docs/superpowers/specs/2026-10-28-seo-foundation-design.md`）
> 前置：站点有正式域名并已部署；`UMI_APP_SITE_URL` 在构建时已设置为该域名。

## 1. 上线前（构建与产物）

- [ ] 构建命令带域名：`UMI_APP_SITE_URL=https://<域名> npm run build`（PowerShell 用 `$env:UMI_APP_SITE_URL=...`）
- [ ] `dist/robots.txt` 存在且内容与 `ui/public/robots.txt` 一致
- [ ] `dist/sitemap.xml` 存在，`<loc>` 全部是正式域名的绝对 URL
- [ ] 抽查 `dist/index.html`：确认静态 head 里的 title/description（SPA 的 head 由 JS 注入，见第 6 节限制）

## 2. 上线后第一小时

- [ ] `curl https://<域名>/robots.txt` 返回 200
- [ ] `curl https://<域名>/sitemap.xml` 返回 200 且为合法 XML
- [ ] 在 `robots.txt` 末尾补一行：`Sitemap: https://<域名>/sitemap.xml`（静态文件无法插值，需手工补）
- [ ] 无痕窗口访问 `/home`、`/product/detail/<id>`、`/exchange/square`，不跳登录
- [ ] DevTools 检查 `<head>`：`<title>`、`meta description`、`link rel=canonical`、`meta robots`、`application/ld+json`

## 3. 搜索引擎接入

- [ ] Google Search Console 添加并验证站点（DNS 或 HTML 文件）
- [ ] 百度搜索资源平台添加并验证站点
- [ ] GSC 提交 `sitemap.xml`；百度提交 sitemap
- [ ] GSC「网址检查」确认首页已抓取，且「已编入索引」
- [ ] 百度「抓取诊断」确认首页可抓取

## 4. 结构化数据验证

- [ ] [Rich Results Test](https://search.google.com/test/rich-results) 验证一个商品详情页：Product / Offer / BreadcrumbList 无错误
- [ ] 验证首页：WebSite / Organization 无错误
- [ ] 换物详情：只应有 BreadcrumbList，**不应**出现 Product 或价格（`estValue` 不是售价）

## 5. 性能与可访问性

- [ ] Lighthouse（移动端）SEO ≥ 95
- [ ] Lighthouse 无「可抓取性」类失败项
- [ ] 商品详情页内容区有且仅有一个 `<h1>`（ProLayout 顶栏自带的 h1 不计）
- [ ] 商品图片有 `alt`

## 6. 已知限制（Phase 1）

- **百度对 JS 渲染支持不稳定**：Helmet 注入的 meta 对 Google / Bing 有效，百度可能读不到。
  若百度收录长期为 0，进入 Phase 3（SSR / 预渲染）。
- **社交分享预览不可用**：微信 / Facebook / Slack 不执行 JS，看不到 OG 标签。Phase 3 解决。
- **动态页不进 sitemap**：Phase 1 的 sitemap 只含固定页；商品 / 换物 / 动态详情由 Phase 2 的后端 `GET /sitemap.xml` 提供。

## 7. 基线记录（上线后第 1 天）

跑一次完整审计并记录基线：

```bash
claude
/seo audit https://<域名>
```

记录：

| 指标 | 基线值 | 记录日期 |
|---|---|---|
| GSC 已编入索引页数 | | |
| GSC 点击 / 展示 | | |
| 百度收录数 | | |
| Lighthouse SEO 分 | | |
| Rich Results 错误数 | | |

## 8. 月度观察

- [ ] 收录数变化
- [ ] 点击 / 展示 / CTR
- [ ] 索引覆盖率（「已抓取 — 尚未编入索引」占比）
- [ ] 软 404（已下架商品、已删除动态是否仍被索引）

## 9. 进入 Phase 3 的触发条件

满足任一条：

- 上线 ≥ 4 周后百度收录数仍为 0
- GSC「已抓取 — 尚未编入索引」占比显著偏高
- 社交分享预览成为明确需求

Phase 3 方案：Umi `ssr` + Node 服务，或 rendertron 式动态预渲染；届时重新评估 ProLayout 的 SSR 兼容性。