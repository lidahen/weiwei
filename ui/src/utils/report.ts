/**
 * 举报相关的前端纯函数与常量。
 *
 * REPORT_REASONS 与后端 ReportReason.LABELS 一一对应，改这里必须同步后端，
 * 否则 compose 出来的「标签：详情」在运营列表里会显示成未知标签。
 */

/** 举报原因（与后端 ReportReason 白名单一一对应） */
export const REPORT_REASONS: { value: string; label: string }[] = [
    { value: 'spam', label: '垃圾广告 / 引流' },
    { value: 'fraud', label: '欺诈 / 骗钱' },
    { value: 'fake', label: '假冒伪劣 / 描述不符' },
    { value: 'prohibited', label: '违禁品 / 违规内容' },
    { value: 'abuse', label: '辱骂 / 骚扰' },
    { value: 'other', label: '其他' },
];

/**
 * 拆分后端合成文本 reason 的逻辑已删除：
 * 后端 ReportServiceImpl.adminList 直接把 reason 拆成 reasonType / reasonDetail 两个字段返回，
 * 前端再做一次解析属于重复实现（且必然与后端漂移）。运营列表直接读 row.reasonType / row.reasonDetail。
 */

/**
 * 处理动作（与后端 adminHandle 白名单一致；下架/删除按对象类型过滤）
 *
 * 注意顺序：下架/删除放在最前，因为它是运营最常用的动作。
 */
export function reportActionOptions(targetType: string): { value: string; label: string }[] {
    const base = [
        { value: 'dismiss', label: '驳回（未违规）' },
        { value: 'warn', label: '警告' },
        { value: 'ban_user', label: '停用账号' },
    ];
    if (targetType === 'product') {
        return [{ value: 'remove_product', label: '下架商品' }, ...base];
    }
    if (targetType === 'feed') {
        return [{ value: 'remove_feed', label: '删除动态' }, ...base];
    }
    return base;
}

/** 被举报对象类型的中文名 */
export const TARGET_TYPE_LABELS: Record<string, string> = {
    product: '商品',
    feed: '动态',
    user: '用户',
};
