export { reconTypeText } from './settlementShared';

/**
 * 差异状态 → 可执行动作。
 * 后端 unfreezeBySeller 要求该卖家无未闭环差异，开放差异行上解冻必然失败；
 * 解冻入口因此挂在 resolved 行（差异闭环后人工解冻）。
 */
export function reconActions(status: string): string[] {
    if (status === 'open') return ['resolve'];
    if (status === 'resolved') return ['unfreeze'];
    return [];
}

/** 处理说明必填 */
export function validateResolveNote(note?: string | null): string | null {
    return note && String(note).trim() ? null : '请填写处理说明';
}

/** 解冻原因必填 */
export function validateUnfreezeReason(reason?: string | null): string | null {
    return reason && String(reason).trim() ? null : '请填写解冻原因';
}