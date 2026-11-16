import { refundByOrder, orderDetail, refundReasons } from '@/services/marketplace/api';
import { useSearchParams } from '@umijs/max';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import RefundApply from './RefundApply';

jest.mock('@/services/marketplace/api', () => ({
    refundApply: jest.fn(),
    refundByOrder: jest.fn(),
    orderDetail: jest.fn(),
    refundReasons: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
    useSearchParams: jest.fn(() => [new URLSearchParams('orderId=1')]),
    history: { push: jest.fn() },
}));

const mockUseSearchParams = useSearchParams as unknown as jest.Mock;

jest.mock('@/components/marketplace/UploadImages', () => ({
    __esModule: true,
    default: () => {
        const ReactLib = require('react');
        return ReactLib.createElement('div', { 'data-testid': 'upload' });
    },
}));

const mockByOrder = refundByOrder as unknown as jest.Mock;
const mockOrderDetail = orderDetail as unknown as jest.Mock;
const mockReasons = refundReasons as unknown as jest.Mock;

describe('退款申请页', () => {
    beforeEach(() => {
        mockUseSearchParams.mockReturnValue([new URLSearchParams('orderId=1')]);
        mockByOrder.mockReset();
        mockOrderDetail.mockReset();
        mockReasons.mockReset();
        mockByOrder.mockResolvedValue({ code: 200, data: { canApply: true } });
        mockOrderDetail.mockResolvedValue({
            code: 200,
            data: { orderId: 1, orderNo: 'TW001', totalAmount: '1107.00', status: 'shipped' },
        });
        mockReasons.mockResolvedValue({
            code: 200,
            data: [
                { code: 'not_received', label: '未收到货', types: ['refund_only'] },
                { code: 'damaged', label: '商品破损', types: ['refund_only', 'return_refund'] },
            ],
        });
    });

    function renderPage(props: Record<string, any> = {}) {
        return render(React.createElement(RefundApply, props));
    }

    it('原因按类型过滤：not_received 只出现在仅退款', async () => {
        renderPage();

        await waitFor(() => expect(screen.getByText('未收到货')).toBeTruthy());
        expect(screen.getByText('商品破损')).toBeTruthy();

        fireEvent.click(screen.getByText('退货退款'));

        await waitFor(() => expect(screen.queryByText('未收到货')).toBeNull());
        expect(screen.getByText('商品破损')).toBeTruthy();
    });

    it('金额超过实付时禁用提交并提示', async () => {
        renderPage();

        const amountInput = await screen.findByRole('spinbutton');
        fireEvent.change(amountInput, { target: { value: '2000' } });

        await waitFor(() => expect(screen.getByText('退款金额不能超过实付金额')).toBeTruthy());
        const submit = screen.getByText('提交申请').closest('button') as HTMLButtonElement;
        expect(submit.disabled).toBe(true);
    });

    it('canApply=false 时展示 denyReason 并隐藏提交按钮', async () => {
        mockByOrder.mockResolvedValue({
            code: 200,
            data: { canApply: false, denyReason: '已超过 7 天售后期限' },
        });

        renderPage();

        await waitFor(() => expect(screen.getByText('已超过 7 天售后期限')).toBeTruthy());
        expect(screen.queryByText('提交申请')).toBeNull();
    });

    it('缺少 orderId 时展示空态而不是一直转圈', () => {
        mockUseSearchParams.mockReturnValue([new URLSearchParams('')]);

        renderPage();

        expect(screen.getByText('缺少订单参数，无法申请退款')).toBeTruthy();
    });

    it('后端只允许仅退款时隐藏退货退款选项', async () => {
        mockByOrder.mockResolvedValue({
            code: 200,
            data: { canApply: true, orderStatus: 'unshipped', allowedTypes: ['refund_only'] },
        });

        renderPage();

        await waitFor(() => expect(screen.getByText('仅退款')).toBeTruthy());
        expect(screen.queryByText('退货退款')).toBeNull();
    });

    it('后端允许两种类型时展示两个选项', async () => {
        mockByOrder.mockResolvedValue({
            code: 200,
            data: { canApply: true, orderStatus: 'shipped', allowedTypes: ['refund_only', 'return_refund'] },
        });

        renderPage();

        await waitFor(() => expect(screen.getByText('退货退款')).toBeTruthy());
        expect(screen.getByText('仅退款')).toBeTruthy();
    });
});