/**
 * 社区漏斗看板的纯展示逻辑（与组件分离，便于单测）。
 * 后端返回结构见 AdminFunnelController#stats。
 */
export const EVENT_LABELS: Record<string, string> = {
    product_exposure: '商品曝光',
    product_detail_view: '详情浏览',
    im_start: '发起会话',
    order_create: '创建订单',
    pay_success: '支付成功',
    ship: '卖家发货',
    confirm_receipt: '确认收货',
    refund_apply: '申请退款',
    refund_success: '退款成功',
    review_create: '提交评价',
};

export function unwrap(res: any) {
    return res?.data ?? res;
}

/** 未选租户时自动选中第一个有数据的社区；已选则保持。 */
export function pickTenant(payload: any, current?: number): number | undefined {
    if (current) return current;
    const tenants = payload?.tenants;
    return Array.isArray(tenants) && tenants.length > 0 ? tenants[0].tenantId : undefined;
}

/** 转化率 0.5 → "50.0%"；null/undefined → "-"。 */
export function formatConversion(value: unknown): string {
    return typeof value === 'number' ? `${(value * 100).toFixed(1)}%` : '-';
}

/** 主漏斗 + 退款漏斗，按后端给的固定顺序拼接。 */
export function funnelRows(payload: any) {
    return [...(payload?.funnel || []), ...(payload?.refundFunnel || [])];
}

/** 指定事件的次数；缺失按 0。 */
export function summaryCount(payload: any, eventType: string): number {
    return payload?.summary?.[eventType]?.count ?? 0;
}

/** 指定环节的转化率；缺失返回 null。 */
export function conversionOf(payload: any, eventType: string): number | null {
    const row = (payload?.funnel || []).find((item: any) => item.key === eventType);
    return typeof row?.conversion === 'number' ? row.conversion : null;
}