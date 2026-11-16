/**
 * 互助模块公共常量与工具
 */

export const TASK_TYPE_MAP: Record<number, { label: string; emoji: string }> = {
    1: { label: '代取快递', emoji: '📦' },
    2: { label: '代买东西', emoji: '🛒' },
    3: { label: '代排队', emoji: '⏳' },
    4: { label: '其他', emoji: '✨' },
};

export const TASK_STATUS_MAP: Record<string, { text: string; color: string }> = {
    pending: { text: '待接单', color: 'orange' },
    accepted: { text: '已接单', color: 'blue' },
    running: { text: '服务中', color: 'cyan' },
    pending_confirm: { text: '待确认', color: 'gold' },
    completed: { text: '已完成', color: 'green' },
    cancelled: { text: '已取消', color: 'default' },
    disputed: { text: '纠纷中', color: 'red' },
    refund: { text: '退款中', color: 'volcano' },
};

export const PAY_STATUS_MAP: Record<string, { text: string; color: string }> = {
    unpaid: { text: '未支付', color: 'default' },
    paid: { text: '已支付', color: 'green' },
    refunded: { text: '已退款', color: 'red' },
};

export const LOG_ACTION_MAP: Record<string, { label: string; color: string }> = {
    publish: { label: '发布', color: 'blue' },
    assign: { label: '接单', color: 'cyan' },
    reject: { label: '拒单', color: 'orange' },
    start: { label: '开始服务', color: 'geekblue' },
    finish: { label: '提交完成', color: 'gold' },
    confirm: { label: '确认完成', color: 'green' },
    cancel: { label: '取消', color: 'default' },
    refund: { label: '申请退款', color: 'volcano' },
    dispute: { label: '投诉', color: 'red' },
    review: { label: '评价', color: 'purple' },
};

/** 时间显示：YYYY-MM-DD HH:mm */
export function formatDateTime(input?: string | number | Date | null): string {
    if (input === undefined || input === null || input === '') return '-';
    const d = new Date(input);
    if (Number.isNaN(d.getTime())) return String(input);
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 金额格式化（元） */
export function formatPrice(input?: number | string | null): string {
    if (input === undefined || input === null || input === '') return '-';
    const n = Number(input);
    if (Number.isNaN(n)) return String(input);
    return `¥${n.toFixed(2)}`;
}

/** 把 Java 后端的 images JSON 字符串（"['a','b']"）解析为字符串数组 */
export function parseImageList(json?: string | null): string[] {
    if (!json) return [];
    try {
        const v = JSON.parse(json);
        if (Array.isArray(v)) return v.filter((x) => typeof x === 'string');
    } catch {
        /* fall through */
    }
    // 兜底：逗号分隔
    return json.split(',').map((s) => s.trim()).filter(Boolean);
}
