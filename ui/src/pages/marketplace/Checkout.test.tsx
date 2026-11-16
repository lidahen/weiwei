import { orderCreate, productDetail, addressList } from '@/services/marketplace/api';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import React from 'react';
import Checkout from './Checkout';

jest.mock('@/services/marketplace/api', () => ({
    orderCreate: jest.fn(),
    productDetail: jest.fn(),
    addressList: jest.fn(),
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
    useSearchParams: () => [new URLSearchParams('productId=1')],
    useRequest: (fn: any) => {
        // 与真实 useRequest 语义对齐：立即执行 fn，返回 reactive data/loading
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const React = require('react');
        const [state, setState] = React.useState({ data: undefined, loading: true });
        React.useEffect(() => {
            Promise.resolve(fn()).then(
                (d: any) => setState({ data: d, loading: false }),
                () => setState({ data: undefined, loading: false }),
            );
        }, []);
        return state;
    },
    history: { push: jest.fn() },
}));

jest.mock('antd', () => {
    const actual = jest.requireActual('antd');
    return { ...actual, Image: () => null };
});

describe('结算页（Checkout）', () => {
    it('快递商品未选地址时提交按钮禁用并提示选择地址', async () => {
        (productDetail as jest.Mock).mockResolvedValue({
            data: {
                productId: 1,
                title: '测试商品',
                price: '10.00',
                shippingMethod: 'express',
                shippingFee: '5.00',
            },
        });
        (addressList as jest.Mock).mockResolvedValue({ data: [] });

        render(React.createElement(Checkout));

        await waitFor(() => screen.getByText('测试商品'));
        const btn = screen.getByRole('button', { name: '提交订单' }) as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
        expect(orderCreate).not.toHaveBeenCalled();
    });

    it('同城面交商品无需地址即可提交下单', async () => {
        (productDetail as jest.Mock).mockResolvedValue({
            data: {
                productId: 1,
                title: '面交商品',
                price: '20.00',
                shippingMethod: 'self_pickup',
            },
        });
        (addressList as jest.Mock).mockResolvedValue({ data: [] });
        (orderCreate as jest.Mock).mockResolvedValue({ data: { orderId: 99 } });

        render(React.createElement(Checkout));

        await waitFor(() => screen.getByText('面交商品'));
        const btn = screen.getByRole('button', { name: '提交订单' }) as HTMLButtonElement;
        await waitFor(() => expect(btn.disabled).toBe(false));
        fireEvent.click(btn);

        await waitFor(() => expect(orderCreate).toHaveBeenCalledWith(
            expect.objectContaining({ productId: 1, addressId: undefined }),
        ));
    });

    it('费用明细合计 = 商品金额 + 运费', async () => {
        (productDetail as jest.Mock).mockResolvedValue({
            data: {
                productId: 1,
                title: '测试商品',
                price: '10.00',
                shippingMethod: 'express',
                shippingFee: '5.00',
            },
        });
        (addressList as jest.Mock).mockResolvedValue({
            data: [{ addressId: 8, receiver: '张三', mobile: '13800000000', isDefault: true }],
        });

        render(React.createElement(Checkout));

        await waitFor(() => screen.getByText('张三'));
        // 合计 15.00
        expect(screen.getAllByText('¥15.00').length).toBeGreaterThan(0);
    });
});
