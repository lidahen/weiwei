import { formatTime } from '@/pages/marketplace/utils';
import { Empty, Timeline } from 'antd';
import React from 'react';

export interface RefundLog {
    /** 后端 trade_refund_log.action */
    action: string;
    /** 附带文案（申请描述 / 拒绝理由 / 裁决说明等） */
    content?: string;
    createTime: string;
}

/** 12 个 action 的中文文案（与后端写入口径一一对应） */
const ACTION_LABELS: Record<string, string> = {
    apply: '提交申请',
    seller_agree: '卖家同意',
    seller_reject: '卖家拒绝',
    buyer_return: '买家已寄回',
    seller_confirm: '卖家确认收货',
    buyer_cancel: '买家撤销',
    mediate: '平台介入',
    judge: '平台裁决',
    refund_ok: '退款成功',
    manual_required: '转人工处理',
    manual_resolved: '人工已处理',
    timeout_close: '超时关闭',
};

interface Props {
    logs: RefundLog[];
}

/**
 * 退款处理时间线：按日志顺序渲染节点，未知 action 回退显示原始值（后端新增 action 时不至于白屏）。
 */
const RefundTimeline: React.FC<Props> = ({ logs }) => {
    if (!logs || logs.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无处理记录" />;
    }
    return (
        <Timeline
            items={logs.map((log, index) => ({
                key: `${log.action}-${index}`,
                label: formatTime(log.createTime),
                children: (
                    <div>
                        <div>{ACTION_LABELS[log.action] ?? log.action}</div>
                        {log.content ? (
                            <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12, marginTop: 2 }}>
                                {log.content}
                            </div>
                        ) : null}
                    </div>
                ),
            }))}
        />
    );
};

export default RefundTimeline;