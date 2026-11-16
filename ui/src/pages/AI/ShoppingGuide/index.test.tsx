import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import ShoppingGuide from './index';
import { aiGuideChatStream } from './service';

const mockPush = jest.fn();

jest.mock('@umijs/max', () => ({
    history: { push: (...args: any[]) => mockPush(...args) },
}));

jest.mock('./service', () => ({
    aiGuideChatStream: jest.fn(),
}));

// 简化 ProductCard,聚焦于数据透传与点击跳转
jest.mock('@/components/marketplace/ProductCard', () => ({
    __esModule: true,
    default: ({ title, onClick }: any) => {
        const ReactLib = require('react');
        return ReactLib.createElement('div', { 'data-testid': 'product-card', onClick }, title);
    },
}));

const mockChatStream = aiGuideChatStream as unknown as jest.Mock;

describe('ShoppingGuide 页面', () => {
    beforeAll(() => {
        // jsdom 未实现 Element.scrollTo
        (window.HTMLElement.prototype as any).scrollTo = jest.fn();
    });

    beforeEach(() => {
        mockPush.mockReset();
        mockChatStream.mockReset();
    });

    it('流式发送后渲染 AI 文案与商品卡片,点击卡片跳转详情', async () => {
        // 模拟流式:依次推送文案、商品,然后结束
        mockChatStream.mockImplementation((_messages: any, onEvent: any, onDone: any) => {
            onEvent({ type: 'text', content: '为你找到这些键盘:' });
            onEvent({
                type: 'products',
                products: [{ productId: 88, title: '机械键盘', cover: 'c.jpg', price: 399 }],
            });
            onDone();
            return Promise.resolve({ abort: jest.fn() });
        });

        render(React.createElement(ShoppingGuide));

        fireEvent.change(screen.getByPlaceholderText(/描述你想要的商品/), {
            target: { value: '机械键盘' },
        });
        fireEvent.click(screen.getByText('发送'));

        await waitFor(() => expect(screen.getByText('为你找到这些键盘:')).toBeTruthy());

        const card = await screen.findByTestId('product-card');
        expect(card.textContent).toContain('机械键盘');

        fireEvent.click(card);
        expect(mockPush).toHaveBeenCalledWith('/product/detail/88');
    });
});
