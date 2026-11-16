/** 实名审核：仅待审核（pending）可操作，其余状态无按钮 */
export function kycActions(status: string): string[] {
    return status === 'pending' ? ['verified', 'rejected'] : [];
}

export type ReviewPayload = { accountId: number; result: string; note?: string };

/** 审核负载：驳回必须填写备注，否则返回 null（页面据此禁用提交） */
export function toReviewPayload(
    row: { accountId: number },
    result: string,
    note?: string,
): ReviewPayload | null {
    const trimmed = (note ?? '').trim();
    if (result === 'rejected' && !trimmed) return null;
    return { accountId: row.accountId, result, note: trimmed || undefined };
}