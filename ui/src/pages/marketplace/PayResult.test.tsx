import { payStatus } from '@/services/marketplace/api';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import PayResult from './PayResult';

jest.mock('@/services/marketplace/api', () => ({
    payStatus: jest.fn(),
}));

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
});

jest.mock('@umijs/max', () => ({
    useSearchParams: () => [new URLSearchParams('payNo=PN123')],
    history: { push: jest.fn() },
}));

describe('支付结果页（PayResult）', () => {
    it('支付单状态为 paid 时展示成功态', async () => {
        (payStatus as jest.Mock).mockResolvedValue({
            data: { status: 'paid', pay_amount: '15.00', out_trade_no: 'T1', pay_channel: 'alipay' },
        });

        render(React.createElement(PayResult));

        await waitFor(() => expect(screen.getByText('支付成功')).toBeTruthy());
        expect(screen.getByText(/PN123/)).toBeTruthy();
    });

    it('支付单未支付时展示失败态', async () => {
        (payStatus as jest.Mock).mockResolvedValue({
            data: { status: 'unpaid' },
        });

        render(React.createElement(PayResult));

        await waitFor(() => expect(screen.getByText('支付未完成')).toBeTruthy());
    });

    it('查询不到支付单（接口异常）时按未完成处理且不崩溃', async () => {
        (payStatus as jest.Mock).mockRejectedValue(new Error('boom'));

        render(React.createElement(PayResult));

        await waitFor(() => expect(screen.getByText('支付未完成')).toBeTruthy());
    });
});
