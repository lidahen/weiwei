import { Tag, Tooltip, Typography } from 'antd';
import React from 'react';

const riskMeta: Record<string, { label: string; color: string }> = {
    low: { label: 'AI 低风险', color: 'green' },
    medium: { label: 'AI 存疑', color: 'orange' },
    high: { label: 'AI 高风险', color: 'red' },
};

const actionMeta: Record<string, string> = {
    auto_pass: 'AI 自动通过',
    auto_reject: 'AI 自动驳回',
    manual: 'AI 建议人工审核',
};

/**
 * AI 初审结果标签（管理端商品/动态审核列表共用）
 * 展示风险等级 Tag，悬浮显示 AI 结论摘要与违规明细
 * （数据来自 ai_moderation_log 最新一条记录，联表返回 aiRiskLevel/aiAction/aiSummary/aiViolations）
 */
const AiAuditTag: React.FC<{ record: any }> = ({ record }) => {
    const risk = record?.aiRiskLevel as string | undefined;
    const action = record?.aiAction as string | undefined;
    if (!risk && !action) {
        return <span style={{ color: '#c0c4cc' }}>—</span>;
    }
    let violations: any[] = [];
    if (record?.aiViolations) {
        try {
            violations = JSON.parse(record.aiViolations);
        } catch {
            /* violations 非 JSON 时忽略明细 */
        }
    }
    const meta = (risk ? riskMeta[risk] : undefined) || { label: 'AI 已审', color: 'default' };
    const actionText = action ? actionMeta[action] : undefined;
    const content = (
        <div style={{ maxWidth: 320 }}>
            <div style={{ marginBottom: 4, fontWeight: 600 }}>{actionText || 'AI 初审'}</div>
            {record?.aiSummary && <div style={{ marginBottom: 4 }}>{record.aiSummary}</div>}
            {violations.length > 0 && (
                <ul style={{ margin: 0, paddingLeft: 16 }}>
                    {violations.slice(0, 5).map((v: any, i: number) => (
                        <li key={i}>
                            <Typography.Text type="secondary">{v?.detail || v?.type}</Typography.Text>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
    return (
        <Tooltip title={content}>
            <Tag color={meta.color} style={{ cursor: 'default' }}>
                {meta.label}
            </Tag>
        </Tooltip>
    );
};

export default AiAuditTag;
