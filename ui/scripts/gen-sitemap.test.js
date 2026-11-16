const fs = require('fs');
const path = require('path');
const os = require('os');
const { buildSitemapXml, SITE_PATHS, cleanupStaleSitemap } = require('./gen-sitemap');

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

describe('cleanupStaleSitemap', () => {
    let tmpFile;

    beforeEach(() => {
        tmpFile = path.join(os.tmpdir(), `sitemap-stale-${process.pid}.xml`);
    });

    afterEach(() => {
        try {
            fs.unlinkSync(tmpFile);
        } catch (e) {
            /* already gone */
        }
    });

    it('删掉上次带域名构建遗留的 sitemap', () => {
        fs.writeFileSync(tmpFile, '<urlset><loc>https://old.example/home</loc></urlset>', 'utf8');

        cleanupStaleSitemap(tmpFile);

        expect(fs.existsSync(tmpFile)).toBe(false);
    });

    it('文件本来不存在时不报错', () => {
        expect(() => cleanupStaleSitemap(tmpFile)).not.toThrow();
    });
});
