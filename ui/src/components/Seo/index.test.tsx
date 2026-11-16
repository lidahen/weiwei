import React from 'react';
import { renderToString } from 'react-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import Seo from './index';

function headOf(element: React.ReactElement) {
    // jsdom 下 provider 默认走 DOM 分支，不会回填 context.helmet；强制走服务端分支
    (HelmetProvider as any).canUseDOM = false;
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
        expect(helmet.title.toString()).toContain('九成新机械键盘 - 邻里二手</title>');
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
        expect(helmet.title.toString()).toContain('>邻里二手</title>');
    });

    it('站点 URL 为空时不输出 canonical 与 JSON-LD script', () => {
        delete process.env.UMI_APP_SITE_URL;
        const helmet = headOf(
            <Seo title="首页" canonicalPath="/home" jsonLd={{ '@type': 'Thing' }} />,
        );
        expect(helmet.link.toString()).not.toContain('canonical');
        expect(helmet.script.toString()).not.toContain('application/ld+json');
    });

    it('jsonLd 为 null 时不渲染 script', () => {
        const helmet = headOf(<Seo title="首页" jsonLd={null} />);
        expect(helmet.script.toString()).not.toContain('application/ld+json');
    });
});