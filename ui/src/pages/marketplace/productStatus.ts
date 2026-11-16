/**
 * 商品状态文案与「是否可交易」判定。
 *
 * 后端 selectById 不按状态过滤，非在售商品（下架/锁定/驳回/草稿/审核中）详情页
 * 仍会正常返回。若页面照常渲染「立即购买」，游客会看到一个已下架商品的购买入口 ——
 * 与 D7「不可索引时页面须明确展示不可交易状态」相悖。
 */

export const PRODUCT_STATUS_MAP: Record<string, { text: string; color: string }> = {
    draft: { text: '草稿', color: 'default' },
    auditing: { text: '审核中', color: 'orange' },
    onsale: { text: '已上架', color: 'green' },
    sold: { text: '已售出', color: 'blue' },
    offshelf: { text: '已下架', color: 'default' },
    locked: { text: '已锁定', color: 'red' },
    rejected: { text: '已驳回', color: 'red' },
};

/** 是否可下单（只有在售商品能买；已售出/下架/锁定/驳回/草稿/审核中都不行）。 */
export function canPurchase(status?: string): boolean {
    return status === 'onsale';
}

/** 不可交易时给用户看的原因；可交易时返回 null。 */
export function unavailableReason(status?: string): string | null {
    if (canPurchase(status)) return null;
    const label = PRODUCT_STATUS_MAP[status || '']?.text;
    return label ? `该商品${label}，暂时无法购买` : '该商品当前不可购买';
}
