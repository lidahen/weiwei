import TWT from '@/setting';

export const SITE_NAME = '邻里二手';
export const SITE_DESCRIPTION =
    '邻里二手是同城闲置交易与互助平台：发布二手商品、以物换物、社区动态与邻里互助。';

const SCHEMA = 'https://schema.org';

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

function normalizeBase(value?: string): string {
    const normalized = String(value || '')
        .trim()
        .replace(/\/+$/, '');
    return /^https?:\/\//i.test(normalized) ? normalized : '';
}

/** 站点根 URL：构建时环境变量优先，其次运行时域名；localhost 类地址视为无效。 */
export function resolveSiteUrl(envUrl = '', origin = ''): string {
    const env = normalizeBase(envUrl);
    if (env) return env;

    const runtime = normalizeBase(origin);
    if (!runtime) return '';

    try {
        const { hostname } = new URL(runtime);
        if (hostname === 'localhost' || hostname === '127.0.0.1') return '';
    } catch {
        return '';
    }
    return runtime;
}

/**
 * 序列化 JSON-LD，转义 `<`。
 *
 * 商品标题、动态正文、昵称都是用户可控内容。JSON.stringify 不转义 `<`，
 * 内容里的 `</script>` 会提前闭合脚本标签，后面接的 HTML 就被当成真实标记解析。
 * Phase 1 是客户端渲染（无 SSR 拼接），危害有限；但 Phase 3 上 SSR 后这就是注入点，
 * 这里先加固，代价是零。
 */
export function serializeJsonLd(value: unknown): string {
    return JSON.stringify(value).replace(/</g, '\\u003c');
}

export function absoluteUrl(path: string, siteUrl: string): string | null {
    if (!siteUrl) return null;
    if (/^https?:\/\//i.test(path)) return path;
    return `${siteUrl}${path.startsWith('/') ? path : `/${path}`}`;
}

export function isProductIndexable(status?: string): boolean {
    return status === 'onsale' || status === 'sold';
}

export function isFeedIndexable(
    feed: { status?: string; auditStatus?: string } | null | undefined,
): boolean {
    return feed?.status === 'normal' && feed?.auditStatus === 'approved';
}

function toImageUrl(path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    return `${TWT.static}${path.startsWith('/') ? path : `/${path}`}`;
}

function parseImages(raw: unknown): string[] {
    if (!raw) return [];
    let list: unknown[];
    if (typeof raw === 'string') {
        const trimmed = raw.trim();
        if (trimmed.startsWith('[')) {
            try {
                const parsed = JSON.parse(trimmed);
                list = Array.isArray(parsed) ? parsed : [];
            } catch {
                list = [];
            }
        } else {
            list = [trimmed];
        }
    } else if (Array.isArray(raw)) {
        list = raw;
    } else {
        list = [];
    }
    return list.filter((item): item is string => typeof item === 'string' && !!item).map(toImageUrl);
}

export function buildProductJsonLd(
    product: SeoProduct | null | undefined,
    siteUrl: string,
): object | null {
    if (!siteUrl || !product || !isProductIndexable(product.status)) return null;

    const url = absoluteUrl(`/product/detail/${product.productId}`, siteUrl);
    const images = parseImages(product.images ?? product.cover);
    // stock 缺失（undefined）不等于无货：DB 默认 1，缺字段时按有货处理，
    // 否则会把一个在售商品报成 OutOfStock。只有明确 0 或已售出才算无货。
    const stock = product.stock === undefined || product.stock === null ? 1 : Number(product.stock);
    const sold = product.status === 'sold' || !(stock > 0);
    const price = Number(product.price);

    const ld: Record<string, any> = {
        '@context': SCHEMA,
        '@type': 'Product',
        name: product.title || '',
    };
    if (product.description) ld.description = product.description;
    if (images.length) ld.image = images;
    ld.offers = {
        '@type': 'Offer',
        priceCurrency: 'CNY',
        availability: sold ? `${SCHEMA}/OutOfStock` : `${SCHEMA}/InStock`,
        itemCondition:
            product.condition === 'brand_new'
                ? `${SCHEMA}/NewCondition`
                : `${SCHEMA}/UsedCondition`,
    };
    // 价格缺失或为 0 时不输出 price：Offer 的 price 必须是正数，填 "0" 会被 Rich Results 判无效
    if (Number.isFinite(price) && price > 0) {
        ld.offers.price = String(price);
    }
    if (url) ld.offers.url = url;
    if (product.sellerNickName) ld.seller = { '@type': 'Person', name: product.sellerNickName };
    return ld;
}

export function buildBreadcrumbJsonLd(
    items: { name: string; path: string }[],
    siteUrl: string,
): object | null {
    if (!siteUrl || !items.length) return null;
    return {
        '@context': SCHEMA,
        '@type': 'BreadcrumbList',
        itemListElement: items.map((item, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: item.name,
            item: absoluteUrl(item.path, siteUrl),
        })),
    };
}

export function buildArticleJsonLd(
    feed: SeoFeed | null | undefined,
    siteUrl: string,
): object | null {
    if (!siteUrl || !feed || !isFeedIndexable(feed)) return null;

    const ld: Record<string, any> = {
        '@context': SCHEMA,
        '@type': 'Article',
        headline: (feed.content || '').slice(0, 110),
    };
    const published = feed.publishTime || feed.createTime;
    if (published) ld.datePublished = published;
    if (feed.userNickName) ld.author = { '@type': 'Person', name: feed.userNickName };
    return ld;
}

export function buildProfilePageJsonLd(
    profile: SeoProfile | null | undefined,
    siteUrl: string,
): object | null {
    if (!siteUrl || !profile?.nickName) return null;
    const url = profile.userId ? absoluteUrl(`/user/profile/${profile.userId}`, siteUrl) : null;
    return {
        '@context': SCHEMA,
        '@type': 'ProfilePage',
        mainEntity: {
            '@type': 'Person',
            name: profile.nickName,
            ...(url ? { url } : {}),
        },
    };
}

export function buildWebSiteJsonLd(siteUrl: string): object | null {
    if (!siteUrl) return null;
    return {
        '@context': SCHEMA,
        '@type': 'WebSite',
        name: SITE_NAME,
        url: `${siteUrl}/`,
        potentialAction: {
            '@type': 'SearchAction',
            target: `${siteUrl}/search?q={search_term_string}`,
            'query-input': 'required name=search_term_string',
        },
    };
}

export function buildOrganizationJsonLd(siteUrl: string): object | null {
    if (!siteUrl) return null;
    return {
        '@context': SCHEMA,
        '@type': 'Organization',
        name: SITE_NAME,
        url: `${siteUrl}/`,
        logo: `${siteUrl}/logo.svg`,
    };
}