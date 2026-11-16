import { SETTLEMENT_STATUS, moneyText, statusMeta } from './settlementShared';

/**
 * 结算单行派生字段：金额文本、状态中文、售后期至（精确到分钟，不用相对时间）。
 * 售后期是未来时点，相对时间会把未来日期显示成「刚刚」，故单独格式化。
 */
export function settlementRow(row: any) {
    return {
        grossText: moneyText(row?.grossAmount),
        commissionText: moneyText(row?.commissionAmount),
        netText: moneyText(row?.netAmount),
        statusText: statusMeta(SETTLEMENT_STATUS, row?.status).text,
        holdUntilText: dateTimeText(row?.holdUntil),
    };
}

/** LocalDateTime → 'YYYY-MM-DD HH:mm'；空值 '-'，兼容 ISO 的 T 分隔 */
export function dateTimeText(value?: string | null): string {
    if (value === undefined || value === null || value === '') return '-';
    return String(value).replace('T', ' ').slice(0, 16);
}