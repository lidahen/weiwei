/**
 * 交换广场模块公共常量与工具
 *
 * 状态枚举与后端常量一一对应：
 * twelvet-exchange-api/.../domain/ExchangeItem.java / ExchangeOffer.java / ExchangeOrder.java
 * 以及 twelvet-exchange-server/.../domain/ExchangeOrderLog.java
 */

// ==================== 物品状态（ExchangeItem.STATUS_*） ====================
export const ITEM_STATUS_MAP: Record<string, { text: string; color: string }> = {
    draft: { text: '草稿', color: 'default' },
    auditing: { text: '审核中', color: 'orange' },
    onswap: { text: '换物中', color: 'green' },
    locked: { text: '已锁定', color: 'blue' },
    swapped: { text: '已换出', color: 'purple' },
    offshelf: { text: '已下架', color: 'default' },
    rejected: { text: '已驳回', color: 'red' },
};

// ==================== 意向状态（ExchangeOffer.STATUS_*） ====================
export const OFFER_STATUS_MAP: Record<string, { text: string; color: string }> = {
    pending: { text: '待处理', color: 'orange' },
    accepted: { text: '已接受', color: 'green' },
    rejected: { text: '已婉拒', color: 'default' },
    expired: { text: '已过期', color: 'default' },
    withdrawn: { text: '已撤回', color: 'default' },
    closed: { text: '已关闭', color: 'default' },
};

// ==================== 订单状态（ExchangeOrder.STATUS_*） ====================
export const ORDER_STATUS_MAP: Record<string, { text: string; color: string }> = {
    created: { text: '待交付', color: 'orange' },
    delivering: { text: '交付中', color: 'cyan' },
    pending_confirm: { text: '待确认', color: 'gold' },
    completed: { text: '已完成', color: 'green' },
    cancelled: { text: '已取消', color: 'default' },
    disputed: { text: '纠纷中', color: 'red' },
};

// ==================== 交付方式（ExchangeItem/ExchangeOrder.DELIVERY_*） ====================
export const DELIVERY_MODE_MAP: Record<string, string> = {
    face: '面交',
    express: '快递',
    both: '面交/快递均可',
};

export const DELIVERY_MODE_OPTIONS = [
    { label: '面交（社区默认）', value: 'face' },
    { label: '快递', value: 'express' },
    { label: '面交/快递均可', value: 'both' },
];

// ==================== 成色（ExchangeItem.CONDITION_*，与商品发布页同一套编码） ====================
export const CONDITION_OPTIONS = [
    { label: '全新', value: 'brand_new' },
    { label: '99新', value: '99' },
    { label: '95新', value: '95' },
    { label: '9成新', value: '90' },
    { label: '8成新', value: '80' },
];

export const CONDITION_MAP: Record<string, string> = Object.fromEntries(
    CONDITION_OPTIONS.map((o) => [o.value, o.label]),
);

// ==================== 订单流转日志动作（ExchangeOrderLog.ACTION_*） ====================
export const ORDER_LOG_ACTION_MAP: Record<string, { label: string; color: string }> = {
    create: { label: '建立订单', color: 'blue' },
    deliver: { label: '标记交付', color: 'cyan' },
    confirm: { label: '确认收货', color: 'green' },
    cancel: { label: '取消', color: 'default' },
    dispute: { label: '发起纠纷', color: 'red' },
    timeout: { label: '超时处理', color: 'volcano' },
    admin_force_complete: { label: '裁决完成', color: 'purple' },
    admin_force_cancel: { label: '裁决取消', color: 'purple' },
    review: { label: '评价', color: 'geekblue' },
};

/** 时间显示：YYYY-MM-DD HH:mm */
export function formatDateTime(input?: string | number | Date | null): string {
    if (input === undefined || input === null || input === '') return '-';
    const d = new Date(input);
    if (Number.isNaN(d.getTime())) return String(input);
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 自估值格式化（元，仅展示不构成交易价格） */
export function formatPrice(input?: number | string | null): string {
    if (input === undefined || input === null || input === '') return '-';
    const n = Number(input);
    if (Number.isNaN(n)) return String(input);
    return `¥${n.toFixed(2)}`;
}

/** 把后端 images JSON 字符串（JSON 数组）解析为图片 URL 数组（兜底逗号分隔） */
export function parseImageList(json?: string | null): string[] {
    if (!json) return [];
    try {
        const v = JSON.parse(json);
        if (Array.isArray(v)) return v.filter((x) => typeof x === 'string');
    } catch {
        /* fall through */
    }
    return json.split(',').map((s) => s.trim()).filter(Boolean);
}

/** 把后端 wantCats JSON 字符串解析为类目 code 数组 */
export function parseWantCats(json?: string | null): string[] {
    if (!json) return [];
    try {
        const v = JSON.parse(json);
        if (Array.isArray(v)) return v.filter((x) => typeof x === 'string');
    } catch {
        /* fall through */
    }
    return [];
}

/**
 * 是否预取「我换物中的物品」候选（发起交换弹窗用）。
 *
 * my-items 接口是 @SaCheckLogin，游客请求会 401；401 走全局错误处理，
 * 在公开页（换物详情已开放给游客）会把游客踢去登录页。因此游客必须整段跳过。
 */
export function shouldPrefetchMyItems(args: {
    loggedIn: boolean;
    isMine: boolean;
    status?: string;
}): boolean {
    return args.loggedIn && !args.isMine && args.status === 'onswap';
}
