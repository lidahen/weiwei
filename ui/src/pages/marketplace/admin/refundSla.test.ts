import { refundSla } from './refundSla';

const NOW = Date.UTC(2026, 10, 2, 12, 0, 0);

describe('refundSla', () => {
    it('待卖家处理 48h 内显示剩余时间', () => {
        const row = { status: 'pending_seller', createTime: '2026-11-02T00:00:00.000Z' };
        expect(refundSla(row, NOW)).toEqual({ text: '剩余 36h', color: 'green', overdue: false });
    });

    it('待卖家处理超过 48h 显示超时', () => {
        // 10-30 00:00 + 48h = 11-01 00:00；当前 11-02 12:00 → 超时 36h
        const row = { status: 'pending_seller', createTime: '2026-10-30T00:00:00.000Z' };
        expect(refundSla(row, NOW)).toEqual({ text: '超时 36h', color: 'red', overdue: true });
    });

    it('buyer_returned 用 updateTime 起算 7 天', () => {
        // 10-28 12:00 + 7 天 = 11-04 12:00；当前 11-02 12:00 → 剩余 48h
        const row = { status: 'buyer_returned', updateTime: '2026-10-28T12:00:00.000Z' };
        expect(refundSla(row, NOW)?.text).toBe('剩余 48h');
    });

    it('剩余不足 6h 转为橙色预警', () => {
        // 10-31 17:00 + 48h = 11-02 17:00；当前 11-02 12:00 → 剩余 5h
        const row = { status: 'pending_seller', createTime: '2026-10-31T17:00:00.000Z' };
        expect(refundSla(row, NOW)).toEqual({ text: '剩余 5h', color: 'orange', overdue: false });
    });

    it('终态返回 null', () => {
        expect(refundSla({ status: 'refund_success', createTime: '2026-11-01T00:00:00.000Z' }, NOW)).toBeNull();
    });
});