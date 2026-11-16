/**
 * 租户编码解析（前端唯一入口）
 *
 * 与后端 `TenantFilter` 保持同口径：解析顺序为
 * **Host 子域名 → `X-Tenant-Code` header → 兼容模式（不设租户）**，
 * 未识别租户时后端返回 404（不返回 403，避免泄露存在性）。
 *
 * ⚠️ 历史坑（2026-09-22）：本逻辑曾在 `requestErrorConfig.ts` 与
 * `utils/twelvet.ts` 中各写一份。修复「IP 被误判为子域名」时只改了前者，
 * 导致 SSE 请求仍带错租户。**新增携带租户头的请求必须调用本模块**，
 * 不要再复制粘贴。
 */

import TWT from '@/setting';

/** 保留子域名：不作为租户编码（必须与后端 TenantFilter.RESERVED 一致） */
const RESERVED_SUBDOMAINS = ['www', 'api', 'admin', 'static', 'docs', 'console'];

/**
 * 判断 hostname 是否为 IP 字面量（IPv4 或 IPv6）。
 *
 * 纯 IP 访问时**不能**按「段数 > 2」推断子域名，否则 `49.232.136.146`
 * 的第一段 `49` 会被当成租户编码，后端返回 `404 tenant not found`。
 */
function isIpLiteral(hostname: string): boolean {
    // IPv4：1-3 位数字，四段
    if (/^\d{1,3}(\.\d{1,3}){3}$/.test(hostname)) {
        return true;
    }
    // IPv6：含冒号（含被方括号包裹的形式）
    return hostname.includes(':');
}

/**
 * 从当前访问地址推断租户子域名。
 *
 * 支持两种形态：
 *   - `thu.localhost`（dev，末段 localhost 且恰好 2 段）
 *   - `thu.ershou.com`（生产，段数 > 2）
 *
 * IP 字面量、保留子域名、裸域名一律返回 `''`。
 */
export function resolveTenantSubdomain(hostname?: string): string {
    const host =
        hostname ?? (typeof window !== 'undefined' ? window.location.hostname : '');

    if (!host || isIpLiteral(host)) {
        return '';
    }

    const hostParts = host.split('.');
    const isDevSubdomain = hostParts[hostParts.length - 1] === 'localhost' && hostParts.length === 2;
    const isProdSubdomain = hostParts.length > 2;

    if (!isDevSubdomain && !isProdSubdomain) {
        return '';
    }

    const subdomain = hostParts[0];
    return RESERVED_SUBDOMAINS.includes(subdomain) ? '' : subdomain;
}

/**
 * 得出本次请求应携带的租户编码。
 *
 * 子域名优先；无子域名时回落 `TWT.tenantCode`；
 * 两者皆空则返回 `''`（调用方应据此**不发送** `X-Tenant-Code` 头，走兼容模式）。
 */
export function resolveTenantCode(hostname?: string): string {
    return resolveTenantSubdomain(hostname) || TWT.tenantCode || '';
}
