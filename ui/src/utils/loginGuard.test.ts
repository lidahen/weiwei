import { history } from '@umijs/max';
import { getToken } from '@/utils/twelvet';
import { isLoggedIn, requireLogin } from './loginGuard';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/utils/twelvet', () => ({ getToken: jest.fn() }));

const mockGetToken = getToken as unknown as jest.Mock;
const mockPush = history.push as unknown as jest.Mock;

describe('loginGuard', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('有 token 时 isLoggedIn 为 true', () => {
        mockGetToken.mockReturnValue('token-1');
        expect(isLoggedIn()).toBe(true);
    });

    it('无 token 时 requireLogin 跳登录并返回 false', () => {
        mockGetToken.mockReturnValue('');
        expect(requireLogin()).toBe(false);
        expect(mockPush).toHaveBeenCalledWith('/login');
    });

    it('有 token 时 requireLogin 不跳转并返回 true', () => {
        mockGetToken.mockReturnValue('token-1');
        expect(requireLogin()).toBe(true);
        expect(mockPush).not.toHaveBeenCalled();
    });
});