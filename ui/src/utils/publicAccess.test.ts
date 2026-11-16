import { isPublicPath } from './publicAccess';

describe('isPublicPath', () => {
    describe('根路径', () => {
        it('/ 视为公开（会 redirect 到 /home，游客不应被拦在登录页）', () => {
            expect(isPublicPath('/')).toBe(true);
        });
    });

    describe('精确公开页', () => {
        it.each(['/home', '/search', '/feed/stream', '/exchange/square'])('%s 公开', (p) => {
            expect(isPublicPath(p)).toBe(true);
        });

        it('尾斜杠归一化后仍公开', () => {
            expect(isPublicPath('/home/')).toBe(true);
            expect(isPublicPath('/exchange/square/')).toBe(true);
        });
    });

    describe('前缀公开页', () => {
        it.each([
            '/product/detail/12',
            '/feed/detail/8',
            '/exchange/detail/3',
            '/user/profile/5',
        ])('%s 公开', (p) => {
            expect(isPublicPath(p)).toBe(true);
        });

        it('前缀无 id 时不公开（避免空白详情页被索引）', () => {
            expect(isPublicPath('/product/detail/')).toBe(false);
            expect(isPublicPath('/exchange/detail')).toBe(false);
        });
    });

    describe('私有页', () => {
        it.each([
            '/login',
            '/user/orders',
            '/user/settings',
            '/product/publish',
            '/product/my',
            '/feed/publish',
            '/exchange/publish',
            '/exchange/my-items',
            '/runner',
        ])('%s 不公开', (p) => {
            expect(isPublicPath(p)).toBe(false);
        });

        it('/user/profile/me 不公开（本人主页）', () => {
            expect(isPublicPath('/user/profile/me')).toBe(false);
            expect(isPublicPath('/user/profile/me/')).toBe(false);
        });
    });

    describe('前缀误匹配防护', () => {
        it('/product/my 不被 /product/detail/ 前缀误判', () => {
            expect(isPublicPath('/product/my')).toBe(false);
        });

        it('/feed/publish 不被 /feed/detail/ 前缀误判', () => {
            expect(isPublicPath('/feed/publish')).toBe(false);
        });
    });
});
