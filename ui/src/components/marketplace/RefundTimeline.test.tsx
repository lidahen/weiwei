import { render, screen } from '@testing-library/react';
import React from 'react';
import RefundTimeline from './RefundTimeline';

describe('退款时间线', () => {
    it('按 action 渲染中文节点', () => {
        render(
            React.createElement(RefundTimeline, {
                logs: [
                    {
                        action: 'apply',
                        content: '申请与描述不符，金额 ¥1107.00',
                        createTime: '2026-10-30 10:00:00',
                    },
                ],
            }),
        );
        expect(screen.getByText('提交申请')).toBeTruthy();
        expect(screen.getByText('申请与描述不符，金额 ¥1107.00')).toBeTruthy();
    });

    it('未知 action 回退显示原始值', () => {
        render(
            React.createElement(RefundTimeline, {
                logs: [{ action: 'weird', createTime: '2026-10-30 10:00:00' }],
            }),
        );
        expect(screen.getByText('weird')).toBeTruthy();
    });

    it('空日志显示占位文案', () => {
        render(React.createElement(RefundTimeline, { logs: [] }));
        expect(screen.getByText('暂无处理记录')).toBeTruthy();
    });

    it('12 个 action 全部有中文文案', () => {
        const actions = [
            'apply',
            'seller_agree',
            'seller_reject',
            'buyer_return',
            'seller_confirm',
            'buyer_cancel',
            'mediate',
            'judge',
            'refund_ok',
            'manual_required',
            'manual_resolved',
            'timeout_close',
        ];
        render(
            React.createElement(RefundTimeline, {
                logs: actions.map((action) => ({ action, createTime: '2026-10-30 10:00:00' })),
            }),
        );
        ['提交申请', '卖家同意', '卖家拒绝', '买家已寄回', '卖家确认收货', '买家撤销', '平台介入', '平台裁决',
            '退款成功', '转人工处理', '人工已处理', '超时关闭'].forEach((label) => {
            expect(screen.getAllByText(label).length).toBeGreaterThan(0);
        });
    });
});