import { shouldPrefetchMyItems } from './utils';

describe('换物详情 - 发起交换候选预取守卫', () => {
    it('游客不预取（my-items 需登录，游客请求会被 401 踢去登录页）', () => {
        expect(shouldPrefetchMyItems({ loggedIn: false, isMine: false, status: 'onswap' })).toBe(
            false,
        );
    });

    it('登录用户浏览他人换物中的物品时预取', () => {
        expect(shouldPrefetchMyItems({ loggedIn: true, isMine: false, status: 'onswap' })).toBe(
            true,
        );
    });

    it('看自己的物品不预取（候选列表无意义）', () => {
        expect(shouldPrefetchMyItems({ loggedIn: true, isMine: true, status: 'onswap' })).toBe(
            false,
        );
    });

    it('物品不在换物中不预取', () => {
        expect(shouldPrefetchMyItems({ loggedIn: true, isMine: false, status: 'offshelf' })).toBe(
            false,
        );
    });
});
