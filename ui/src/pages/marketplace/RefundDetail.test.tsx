import { refundDetail } from '@/services/marketplace/api';
import { render, screen } from '@testing-library/react';
import React from 'react';
import RefundDetail from './RefundDetail';

jest.mock('@/services/marketplace/api', () => ({
    refundDetail: jest.fn(),
    orderDetail: jest.fn(),
    refundCancel: jest.fn(),
    refundMediate: jest.fn(),
    refundReturn: jest.fn(),
    refundSellerAgree: jest.fn(),
    refundSellerConfirm: jest.fn(),
    refundSellerReject: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
    useSearchParams: () => [new URLSearchParams('')],
    useModel: () => ({ initialState: { currentUser: { user: { userId: 1 } } } }),
    history: { push: jest.fn() },
}));

jest.mock('@/components/marketplace/RefundTimeline', () => ({
    __esModule: true,
    default: () => null,
}));

describe('退款详情页', () => {
    it('缺少 refundId 时展示空态', () => {
        render(React.createElement(RefundDetail));

        expect(screen.getByText('缺少退款单参数')).toBeTruthy();
        expect(refundDetail).not.toHaveBeenCalled();
    });
});