/** 游客可浏览的路径（与设计文档 §2 一致）。前缀必须以 `/` 结尾，避免 /product/my 匹配到 /product/detail。 */
// `/` 本身不是页面，它 redirect 到 /home；但它出现在任何外部链接与用户手输里，
// 若判为私有，游客一进根域名就被踢去 /login —— 对 SEO 是最坏结果。
const PUBLIC_EXACT = ['/', '/home', '/search', '/feed/stream', '/exchange/square'];

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