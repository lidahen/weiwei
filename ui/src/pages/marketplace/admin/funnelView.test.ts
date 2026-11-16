import {
    EVENT_LABELS,
    conversionOf,
    formatConversion,
    funnelRows,
    pickTenant,
    summaryCount,
} from './funnelView';

describe('社区漏斗看板展示逻辑', () => {
    it('未选租户时自动选中第一个社区，已选则保持', () => {
        const payload = { tenants: [{ tenantId: 2 }, { tenantId: 3 }] };
        expect(pickTenant(payload, undefined)).toBe(2);
        expect(pickTenant(payload, 3)).toBe(3);
        expect(pickTenant({ tenants: [] }, undefined)).toBeUndefined();
        expect(pickTenant(undefined, undefined)).toBeUndefined();
    });

    it('转化率格式：0.5 → 50.0%，null → -', () => {
        expect(formatConversion(0.5)).toBe('50.0%');
        expect(formatConversion(0)).toBe('0.0%');
        expect(formatConversion(null)).toBe('-');
        expect(formatConversion(undefined)).toBe('-');
    });

    it('漏斗行按主漏斗 + 退款漏斗拼接', () => {
        const rows = funnelRows({
            funnel: [{ key: 'product_exposure' }],
            refundFunnel: [{ key: 'refund_apply' }],
        });
        expect(rows.map((r: any) => r.key)).toEqual(['product_exposure', 'refund_apply']);
    });

    it('完成交易数取 summary，缺失为 0', () => {
        const payload = { summary: { confirm_receipt: { count: 8, uv: 7 } } };
        expect(summaryCount(payload, 'confirm_receipt')).toBe(8);
        expect(summaryCount(payload, 'pay_success')).toBe(0);
    });

    it('取指定环节转化率，缺失为 null', () => {
        const payload = { funnel: [{ key: 'confirm_receipt', conversion: 0.8 }] };
        expect(conversionOf(payload, 'confirm_receipt')).toBe(0.8);
        expect(conversionOf(payload, 'im_start')).toBeNull();
    });

    it('10 个事件都有中文名', () => {
        expect(Object.keys(EVENT_LABELS)).toHaveLength(10);
        expect(EVENT_LABELS.product_exposure).toBe('商品曝光');
        expect(EVENT_LABELS.review_create).toBe('提交评价');
    });
});