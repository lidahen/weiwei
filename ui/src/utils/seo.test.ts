import {
    buildArticleJsonLd,
    buildBreadcrumbJsonLd,
    buildOrganizationJsonLd,
    buildProductJsonLd,
    buildProfilePageJsonLd,
    buildWebSiteJsonLd,
    isFeedIndexable,
    isProductIndexable,
    resolveSiteUrl,
} from './seo';

describe('resolveSiteUrl', () => {
    it('优先使用构建时环境变量并去掉尾部斜杠', () => {
        expect(resolveSiteUrl('https://weiwei.test/', 'http://localhost:8000')).toBe(
            'https://weiwei.test',
        );
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
    it.each(['onsale', 'sold'])('%s 可索引', (status) =>
        expect(isProductIndexable(status)).toBe(true),
    );
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
        expect(ld.image[0]).toMatch(
            /^https:\/\/your-bucket\.oss-cn-beijing\.aliyuncs\.com\//,
        );
        expect(ld.seller).toEqual({ '@type': 'Person', name: '阿黎' });
    });

    it('在售但库存字段缺失时按有货处理（不能把 onsale 报成 OutOfStock）', () => {
        const ld: any = buildProductJsonLd({ ...product, stock: undefined }, 'https://weiwei.test');
        expect(ld.offers.availability).toBe('https://schema.org/InStock');
    });

    it('价格缺失时不输出 offers.price（"0" 元是无效 Offer）', () => {
        const ld: any = buildProductJsonLd({ ...product, price: undefined }, 'https://weiwei.test');
        expect(ld.offers.price).toBeUndefined();
    });

    it('价格为 0 时同样不输出 offers.price', () => {
        const ld: any = buildProductJsonLd({ ...product, price: 0 }, 'https://weiwei.test');
        expect(ld.offers.price).toBeUndefined();
    });

    it('sold 或 stock=0 输出 OutOfStock', () => {
        const ld: any = buildProductJsonLd(
            { ...product, status: 'sold', stock: 0 },
            'https://weiwei.test',
        );
        expect(ld.offers.availability).toBe('https://schema.org/OutOfStock');
    });

    it('全新商品输出 NewCondition', () => {
        const ld: any = buildProductJsonLd(
            { ...product, condition: 'brand_new' },
            'https://weiwei.test',
        );
        expect(ld.offers.itemCondition).toBe('https://schema.org/NewCondition');
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
            {
                content: '出闲置键盘',
                status: 'normal',
                auditStatus: 'approved',
                publishTime: '2026-10-01T10:00:00',
                userNickName: '阿黎',
            },
            'https://weiwei.test',
        );
        expect(ld['@type']).toBe('Article');
        expect(ld.headline).toBe('出闲置键盘');
        expect(ld.author).toEqual({ '@type': 'Person', name: '阿黎' });
    });
    it('pending 动态返回 null', () => {
        expect(
            buildArticleJsonLd(
                { content: 'x', status: 'normal', auditStatus: 'pending' },
                'https://weiwei.test',
            ),
        ).toBeNull();
    });
});

describe('buildProfilePageJsonLd', () => {
    it('输出 ProfilePage + Person', () => {
        const ld: any = buildProfilePageJsonLd(
            { nickName: '阿黎', userId: 7 },
            'https://weiwei.test',
        );
        expect(ld['@type']).toBe('ProfilePage');
        expect(ld.mainEntity).toEqual({
            '@type': 'Person',
            name: '阿黎',
            url: 'https://weiwei.test/user/profile/7',
        });
    });
    it('无昵称返回 null', () => {
        expect(buildProfilePageJsonLd({ userId: 7 }, 'https://weiwei.test')).toBeNull();
    });
});

describe('buildOrganizationJsonLd', () => {
    it('输出站点 logo 的绝对地址', () => {
        const ld: any = buildOrganizationJsonLd('https://weiwei.test');
        expect(ld['@type']).toBe('Organization');
        expect(ld.logo).toBe('https://weiwei.test/logo.svg');
    });
});

describe('buildWebSiteJsonLd', () => {
    it('包含站内搜索 SearchAction', () => {
        const ld: any = buildWebSiteJsonLd('https://weiwei.test');
        expect(ld.potentialAction.target).toBe(
            'https://weiwei.test/search?q={search_term_string}',
        );
        expect(buildWebSiteJsonLd('')).toBeNull();
    });
});