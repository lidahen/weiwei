import TWT from '@/setting';

/**
 * marketplace 页面公共工具：静态资源拼接、时间格式化、图片占位。
 */

/** 相对路径 → 完整静态资源 URL；已是绝对地址或空值原样/undefined 返回（头像、封面通用兜底）。 */
export function staticUrl(path?: string | null): string | undefined {
    if (!path) return undefined;
    if (/^https?:\/\//i.test(path)) return path;
    return `${TWT.static}${path}`;
}

/** 图片加载失败的灰底占位（antd Image 的 fallback / raw img 的 onError 通用）。 */
export const IMG_FALLBACK =
    'data:image/svg+xml;utf8,' +
    encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">
            <rect width="100%" height="100%" fill="#F2F1EC"/>
            <text x="50%" y="50%" fill="#B8B8B0" font-family="sans-serif" font-size="13"
                text-anchor="middle" dominant-baseline="middle">图片走丢了</text>
        </svg>`,
    );

/** 相对时间格式化：刚刚 / N 分钟前 / N 小时前 / 昨天 HH:mm / MM-DD / YYYY-MM-DD。 */
export function formatTime(input?: string | number | Date | null): string {
    if (input === undefined || input === null || input === '') return '-';
    const d = new Date(input);
    if (Number.isNaN(d.getTime())) return String(input);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return `${diffMin} 分钟前`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour} 小时前`;
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
    const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    if (sameDay(d, yesterday)) return `昨天 ${hm}`;
    if (d.getFullYear() === now.getFullYear()) {
        return `${d.getMonth() + 1}-${d.getDate()} ${hm}`;
    }
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

/** 金额展示：1234.5 → ¥1,234.50；空值返回 '-'。 */
export function formatPrice(value?: number | string | null): string {
    if (value === undefined || value === null || value === '') return '-';
    const n = Number(value);
    if (Number.isNaN(n)) return String(value);
    return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
