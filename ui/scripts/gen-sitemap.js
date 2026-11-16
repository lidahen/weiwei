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

/**
 * 清掉上次带域名构建遗留的 sitemap。
 * public/ 会被原样拷进 dist/，跳过生成时若不清，dist 会继续发布上一轮的域名。
 */
function cleanupStaleSitemap(outputPath = OUTPUT) {
    fs.rmSync(outputPath, { force: true });
}

if (require.main === module) {
    const siteUrl = process.env.UMI_APP_SITE_URL || '';
    if (!siteUrl) {
        cleanupStaleSitemap();
        console.log('[gen-sitemap] UMI_APP_SITE_URL 未设置，跳过 sitemap 生成（已清理遗留文件）');
        process.exit(0);
    }
    const lastmod = new Date().toISOString().slice(0, 10);
    fs.writeFileSync(OUTPUT, buildSitemapXml(siteUrl, SITE_PATHS, lastmod), 'utf8');
    console.log(`[gen-sitemap] 已生成 ${OUTPUT}`);
}

module.exports = { buildSitemapXml, cleanupStaleSitemap, SITE_PATHS, OUTPUT };