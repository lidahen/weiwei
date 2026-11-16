import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import ReportDialog from './ReportDialog';
import { reportSubmit } from '@/services/marketplace/api';

jest.mock('@/services/marketplace/api', () => ({
    reportSubmit: jest.fn(),
}));

jest.mock('@/utils/loginGuard', () => ({
    requireLogin: () => true,
}));

// jest.mock 的工厂函数会被提升到 import 之前，不能用外层的 React —— 与 ShoppingGuide 测试同法
jest.mock('./UploadImages', () => ({
    __esModule: true,
    default: () => {
        const ReactLib = require('react');
        return ReactLib.createElement('div', { 'data-testid': 'upload' });
    },
}));

const mockSubmit = reportSubmit as unknown as jest.Mock;

describe('ReportDialog', () => {
    beforeEach(() => {
        mockSubmit.mockReset();
        mockSubmit.mockResolvedValue({ code: 200, msg: '举报已提交' });
    });

    // 与 ShoppingGuide 测试同法：本仓库的 jest 配置不跑 JSX 自动运行时，用 createElement
    function renderDialog(props: Record<string, any> = {}) {
        const onClose = jest.fn();
        render(
            React.createElement(ReportDialog, {
                open: true,
                targetType: 'product',
                targetId: 42,
                targetLabel: '商品「键盘」',
                onClose,
                ...props,
            }),
        );
        return { onClose };
    }

    it('选中原因即可提交，带上对象类型与ID', async () => {
        const { onClose } = renderDialog();

        fireEvent.click(screen.getByText('垃圾广告 / 引流'));
        fireEvent.click(screen.getByText('提交举报'));

        await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
        expect(mockSubmit.mock.calls[0][0]).toMatchObject({
            targetType: 'product',
            targetId: 42,
            reasonType: 'spam',
        });
        await waitFor(() => expect(onClose).toHaveBeenCalled());
    });

    it('选「其他」但未填说明时阻止提交', async () => {
        renderDialog({ targetType: 'user', targetId: 55, targetLabel: '用户 alice' });

        fireEvent.click(screen.getByText('其他'));
        fireEvent.click(screen.getByText('提交举报'));

        await waitFor(() => expect(mockSubmit).not.toHaveBeenCalled());
    });

    it('选「其他」并填了说明后可提交', async () => {
        renderDialog();

        fireEvent.click(screen.getByText('其他'));
        fireEvent.change(screen.getByPlaceholderText(/补充说明/), {
            target: { value: '图片与实物不符' },
        });
        fireEvent.click(screen.getByText('提交举报'));

        await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
        expect(mockSubmit.mock.calls[0][0]).toMatchObject({
            reasonType: 'other',
            reasonDetail: '图片与实物不符',
        });
    });

    it('未选原因时阻止提交', async () => {
        renderDialog();

        fireEvent.click(screen.getByText('提交举报'));

        await waitFor(() => expect(mockSubmit).not.toHaveBeenCalled());
    });

    it('提交失败时不关闭弹窗', async () => {
        mockSubmit.mockRejectedValue({ response: { data: { msg: '你已举报过该内容，正在处理中' } } });
        const { onClose } = renderDialog();

        fireEvent.click(screen.getByText('欺诈 / 骗钱'));
        fireEvent.click(screen.getByText('提交举报'));

        await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
        expect(onClose).not.toHaveBeenCalled();
    });
});
