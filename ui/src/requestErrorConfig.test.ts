const mockPush = jest.fn();
const mockLocation = { pathname: '/home' };

jest.mock('@umijs/max', () => ({
    request: jest.fn(),
    getLocale: () => 'zh-CN',
    history: {
        push: (...args: any[]) => mockPush(...args),
        get location() {
            return mockLocation;
        },
    },
}));

jest.mock('./utils/twelvet', () => ({
    logout: jest.fn(() => mockPush('/login')),
    removeAuthority: jest.fn(),
    setAuthority: jest.fn(),
    system: { error: jest.fn() },
}));

jest.mock('./pages/Login/service', () => ({
    refreshToken: jest.fn(),
}));

import { errorConfig } from './requestErrorConfig';
import { logout, removeAuthority } from './utils/twelvet';

const handler = (errorConfig.errorConfig as any).errorHandler;

const http401 = () => ({
    response: { status: 401, data: { msg: '未登录' } },
});

describe('全局 401 处理', () => {
    beforeEach(() => {
        mockPush.mockReset();
        (logout as jest.Mock).mockClear();
        (removeAuthority as jest.Mock).mockClear();
    });

    it('公开页遇 401 静默降级为游客，不跳登录', async () => {
        mockLocation.pathname = '/home';

        await handler(http401(), {});

        expect(mockPush).not.toHaveBeenCalled();
        expect(removeAuthority).toHaveBeenCalled();
    });

    it('公开页前缀路由遇 401 同样不跳登录', async () => {
        mockLocation.pathname = '/exchange/detail/12';

        await handler(http401(), {});

        expect(mockPush).not.toHaveBeenCalled();
        expect(removeAuthority).toHaveBeenCalled();
    });

    it('私有页遇 401 仍跳登录', async () => {
        mockLocation.pathname = '/user/orders';

        await handler(http401(), {});

        expect(mockPush).toHaveBeenCalledWith('/login');
        expect(logout).toHaveBeenCalled();
    });
});
