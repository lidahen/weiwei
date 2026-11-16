/**
 * 退款单 SLA（运营后台）：按状态停留时长显示剩余/超时。
 * 阈值与后端超时策略一致：待卖家处理 48h、退货中 7 天、待收口 24h。
 */
const SLA_HOURS: Record<string, number> = {
    pending_seller: 48,
    seller_agreed: 168,
    buyer_returned: 168,
    mediation: 24,
    refunding: 24,
};

export function refundSla(row: any, nowMs: number = Date.now()) {
    const hours = SLA_HOURS[row?.status];
    if (!hours) return null;
    const base = row.status === 'pending_seller' ? row.createTime : row.updateTime || row.createTime;
    const baseMs = new Date(base).getTime();
    if (!Number.isFinite(baseMs)) return null;
    const remainMs = baseMs + hours * 3600_000 - nowMs;
    const absHours = Math.ceil(Math.abs(remainMs) / 3600_000);
    if (remainMs < 0) {
        return { text: `超时 ${absHours}h`, color: 'red', overdue: true };
    }
    return {
        text: `剩余 ${absHours}h`,
        color: remainMs < 6 * 3600_000 ? 'orange' : 'green',
        overdue: false,
    };
}