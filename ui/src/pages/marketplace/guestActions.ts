/**
 * C 端公开页的游客动作守卫。
 *
 * 公开页（首页/搜索/商品详情/动态/换物/用户主页）已对游客开放，页面上的写操作
 * 多数仍是 @SaCheckLogin。游客触发这些请求会 401，而 401 走全局错误处理 ——
 * 在公开页会把人踢去登录页。因此这类动作在游客态必须先拦下来引导登录。
 */

/** 是否应发「不感兴趣」负反馈请求（/product/interest/dislike 需登录）。 */
export function shouldSendDislike(args: { loggedIn: boolean }): boolean {
    return args.loggedIn;
}
