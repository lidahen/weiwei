import { shouldSendDislike } from './guestActions';

describe('首页游客动作守卫', () => {
    it('游客点「不感兴趣」不发请求（该接口需登录，401 会把人踢去登录页）', () => {
        expect(shouldSendDislike({ loggedIn: false })).toBe(false);
    });

    it('登录用户点「不感兴趣」正常发请求', () => {
        expect(shouldSendDislike({ loggedIn: true })).toBe(true);
    });
});
