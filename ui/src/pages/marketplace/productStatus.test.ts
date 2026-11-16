import { canPurchase, unavailableReason } from './productStatus';

describe('canPurchase', () => {
    it('只有在售商品可购买', () => {
        expect(canPurchase('onsale')).toBe(true);
    });

    it.each(['sold', 'offshelf', 'locked', 'rejected', 'draft', 'auditing', undefined])(
        '%s 不可购买',
        (status) => {
            expect(canPurchase(status as any)).toBe(false);
        },
    );
});

describe('unavailableReason', () => {
    it('在售商品没有不可用原因', () => {
        expect(unavailableReason('onsale')).toBeNull();
    });

    it('已下架给出明确原因', () => {
        expect(unavailableReason('offshelf')).toBe('该商品已下架，暂时无法购买');
    });

    it('已售出给出明确原因', () => {
        expect(unavailableReason('sold')).toBe('该商品已售出，暂时无法购买');
    });

    it('未知状态给出兜底原因', () => {
        expect(unavailableReason(undefined)).toBe('该商品当前不可购买');
    });
});
