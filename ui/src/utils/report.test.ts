import { REPORT_REASONS, reportActionOptions } from './report';

describe('reportActionOptions', () => {
    it('商品可选下架', () => {
        const values = reportActionOptions('product').map((o) => o.value);
        expect(values).toContain('remove_product');
        expect(values).not.toContain('remove_feed');
    });

    it('动态可选删除，不含下架商品', () => {
        const values = reportActionOptions('feed').map((o) => o.value);
        expect(values).toContain('remove_feed');
        expect(values).not.toContain('remove_product');
    });

    it('驳回/警告/封号对三类都可用', () => {
        for (const t of ['product', 'feed', 'user']) {
            const values = reportActionOptions(t).map((o) => o.value);
            expect(values).toEqual(expect.arrayContaining(['dismiss', 'warn', 'ban_user']));
        }
    });
});

describe('REPORT_REASONS', () => {
    it('六项，值与后端白名单一致', () => {
        expect(REPORT_REASONS.map((r) => r.value)).toEqual([
            'spam',
            'fraud',
            'fake',
            'prohibited',
            'abuse',
            'other',
        ]);
    });
});
