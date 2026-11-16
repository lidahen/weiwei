import { request } from '@umijs/max';

/**
 * 二手交易 - 统一 API
 */

// 商品
export async function productRecommend(params?: Record<string, any>) {
    return request('/product/home/recommend', { method: 'GET', params });
}
export async function productNearby(params?: Record<string, any>) {
    return request('/product/home/nearby', { method: 'GET', params });
}
export async function productFollow(params?: Record<string, any>) {
    return request('/product/home/follow', { method: 'GET', params });
}
export async function productSearch(params: Record<string, any>) {
    return request('/product/search', { method: 'GET', params });
}
export async function searchHistory() {
    return request('/product/search/history', { method: 'GET' });
}
export async function searchHistoryClear() {
    return request('/product/search/history', { method: 'DELETE' });
}
export async function searchHot() {
    return request('/product/search/hot', { method: 'GET' });
}
export async function interestInit(cats: string[]) {
    return request('/product/interest/init', { method: 'POST', data: { cats } });
}
export async function interestDislike(productId: number) {
    return request('/product/interest/dislike', { method: 'POST', params: { productId } });
}

// 推荐效果（管理端）
export async function adminRecommendStats(params: { days?: number; bucket?: number }) {
    return request('/admin/recommend/stats', { method: 'GET', params });
}
// 社区漏斗（管理端）
export async function adminFunnelStats(params?: { days?: number; tenantId?: number }) {
    return request('/admin/marketplace/funnel/stats', { method: 'GET', params });
}
export async function productDetail(id: number | string) {
    return request(`/product/detail/${id}`, { method: 'GET' });
}
export async function productSellerOthers(params: Record<string, any>) {
    return request('/product/seller-others', { method: 'GET', params });
}
export async function productSimilar(id: number | string, limit = 6) {
    return request(`/product/similar/${id}`, { method: 'GET', params: { limit } });
}

// 发布/编辑/操作
export async function productPublish(data: Record<string, any>) {
    return request('/product/publish', { method: 'POST', data });
}
export async function productUpdate(data: Record<string, any>) {
    return request('/product/update', { method: 'PUT', data });
}
export async function productOffshelf(productId: number) {
    return request('/product/offshelf', { method: 'POST', params: { productId } });
}
export async function productDelete(productId: number) {
    return request('/product/delete', { method: 'DELETE', params: { productId } });
}

// 草稿
export async function productDraftGet() {
    return request('/product/draft/get', { method: 'GET' });
}
export async function productDraftSave(snapshot: string) {
    return request('/product/draft/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: snapshot,
    });
}
export async function productDraftDelete() {
    return request('/product/draft/delete', { method: 'DELETE' });
}

// 我的商品列表
export async function productMyPage(params?: Record<string, any>) {
    return request('/product/my/page', { method: 'GET', params });
}

// 字典（商品分类/城市/快递）
export async function dictCategoryTree() {
    return request('/product/category/product-cat', { method: 'GET' });
}
export async function dictCities() {
    return request('/product/category/cities', { method: 'GET' });
}
export async function dictExpress() {
    return request('/product/category/express', { method: 'GET' });
}

// 收藏
export async function favoritePage(params?: Record<string, any>) {
    return request('/user/favorites/page', { method: 'GET', params });
}
export async function favoriteAdd(productId: number) {
    return request('/user/favorites/add', { method: 'POST', params: { productId } });
}
export async function favoriteRemove(productId: number) {
    return request(`/user/favorites/${productId}`, { method: 'DELETE' });
}

// 用户
export async function userProfileMe() {
    return request('/user/profile/me', { method: 'GET' });
}
export async function userProfile(userId: number | string) {
    return request(`/user/profile/${userId}`, { method: 'GET' });
}
/** 卖家信用画像（信用分 + 完成率 + 失信扣分明细），需登录 */
export async function userCredit(userId: number | string) {
    return request(`/user/credit/${userId}`, { method: 'GET' });
}
export async function userFollow(targetUserId: number) {
    return request('/user/follow', { method: 'POST', params: { targetUserId } });
}
export async function userUnfollow(targetUserId: number) {
    return request('/user/unfollow', { method: 'POST', params: { targetUserId } });
}
export async function userFollowsList(tab: 'follows' | 'fans' = 'follows') {
    return request('/user/follows/list', { method: 'GET', params: { tab } });
}
export async function addressList() {
    return request('/user/address/list', { method: 'GET' });
}
export async function addressSave(data: Record<string, any>) {
    return request('/user/address/save', { method: 'POST', data });
}
export async function addressRemove(addressId: number) {
    return request(`/user/address/${addressId}`, { method: 'DELETE' });
}

// 订单
export async function orderPage(params: Record<string, any>) {
    return request('/trade/order/page', { method: 'GET', params });
}
export async function orderDetail(orderId: number | string) {
    return request(`/trade/order/detail/${orderId}`, { method: 'GET' });
}
export async function orderCreate(data: Record<string, any>) {
    return request('/trade/order/create', { method: 'POST', data });
}
export async function orderConfirm(orderId: number) {
    return request('/trade/order/confirm', { method: 'POST', params: { orderId } });
}
export async function orderShip(data: Record<string, any>) {
    return request('/trade/order/ship', { method: 'POST', data });
}
/** 买家取消未支付订单（仅 unpaid 可取消，成功后释放库存） */
export async function orderCancel(orderId: number) {
    return request(`/trade/order/cancel?orderId=${orderId}`, { method: 'POST' });
}

// 退款售后
export async function refundApply(data: Record<string, any>) {
    return request('/trade/refund/apply', { method: 'POST', data });
}
export async function refundDetail(refundId: number | string) {
    return request(`/trade/refund/detail/${refundId}`, { method: 'GET' });
}
export async function refundPage(params?: Record<string, any>) {
    return request('/trade/refund/page', { method: 'GET', params });
}
export async function refundByOrder(orderId: number | string) {
    return request(`/trade/refund/by-order/${orderId}`, { method: 'GET' });
}
export async function refundReasons() {
    return request('/trade/refund/reasons', { method: 'GET' });
}
export async function refundCancel(refundId: number) {
    return request('/trade/refund/cancel', { method: 'POST', params: { refundId } });
}
export async function refundReturn(data: Record<string, any>) {
    return request('/trade/refund/return', { method: 'POST', data });
}
export async function refundMediate(refundId: number) {
    return request('/trade/refund/mediate', { method: 'POST', params: { refundId } });
}
export async function refundSellerPage(params?: Record<string, any>) {
    return request('/trade/refund/seller/page', { method: 'GET', params });
}
export async function refundSellerAgree(data: Record<string, any>) {
    return request('/trade/refund/seller/agree', { method: 'POST', data });
}
export async function refundSellerReject(data: Record<string, any>) {
    return request('/trade/refund/seller/reject', { method: 'POST', data });
}
export async function refundSellerConfirm(refundId: number) {
    return request('/trade/refund/seller/confirm-receipt', { method: 'POST', params: { refundId } });
}
export async function adminRefundPage(params?: Record<string, any>) {
    return request('/admin/marketplace/refund/page', { method: 'GET', params });
}
export async function adminRefundDetail(refundId: number | string) {
    return request(`/admin/marketplace/refund/detail/${refundId}`, { method: 'GET' });
}
export async function adminRefundJudge(data: Record<string, any>) {
    return request('/admin/marketplace/refund/judge', { method: 'POST', data });
}
export async function adminRefundResolve(data: Record<string, any>) {
    return request('/admin/marketplace/refund/resolve', { method: 'POST', data });
}
export async function userPrivacyGet() {
    return request('/user/privacy', { method: 'GET' });
}
export async function userPrivacySave(data: Record<string, any>) {
    return request('/user/privacy', { method: 'POST', data });
}

// 交易评价
export async function reviewSubmit(data: Record<string, any>) {
    return request('/trade/review', { method: 'POST', data });
}
export async function reviewByOrder(orderId: number | string) {
    return request(`/trade/review/order/${orderId}`, { method: 'GET' });
}
export async function reviewPageByProduct(productId: number | string, params?: Record<string, any>) {
    return request(`/trade/review/product/${productId}`, { method: 'GET', params });
}
export async function reviewPageByUser(userId: number | string, params?: Record<string, any>) {
    return request(`/trade/review/user/${userId}`, { method: 'GET', params });
}

// IM
export async function imConversations() {
    return request('/im/conversation/list', { method: 'GET' });
}
export async function imPeerInfo(peerId: number | string) {
    return request('/im/peer/info', { method: 'GET', params: { peerId } });
}
export async function imMessages(peerId: number | string, productId?: number) {
    return request('/im/message/list', { method: 'GET', params: { peerId, productId } });
}
export async function imSend(data: Record<string, any>) {
    return request('/im/message/send', { method: 'POST', data });
}
export async function imNotices() {
    return request('/im/notice/list', { method: 'GET' });
}

// 动态
export async function feedRecommend(params?: Record<string, any>) {
    return request('/feed/stream', { method: 'GET', params: { ...params, type: 'latest' } });
}
export async function feedFollow(params?: Record<string, any>) {
    return request('/feed/stream', { method: 'GET', params: { ...params, type: 'hot' } });
}
export async function feedDetail(feedId: number | string) {
    return request(`/feed/detail/${feedId}`, { method: 'GET' });
}
export async function feedLike(feedId: number, action: 1 | 0 = 1) {
    return request('/feed/like', { method: 'POST', params: { feedId, action } });
}
export async function feedComment(data: Record<string, any>) {
    return request('/feed/comment', { method: 'POST', data });
}

// 动态 - 发布/编辑/删除/我的/转发/举报/搜索/评论列表
export async function feedPublish(data: Record<string, any>) {
    return request('/feed/publish', { method: 'POST', data });
}
export async function feedEdit(feedId: number, data: Record<string, any>) {
    return request(`/feed/${feedId}`, { method: 'PUT', data });
}
export async function feedDelete(feedId: number) {
    return request(`/feed/${feedId}`, { method: 'DELETE' });
}
export async function feedMyPage(params?: Record<string, any>) {
    return request('/feed/my', { method: 'GET', params });
}
export async function feedMySelf() {
    return request('/feed/my/self', { method: 'GET' });
}
export async function feedForward(feedId: number) {
    return request('/feed/forward', { method: 'POST', params: { feedId } });
}
export async function feedReport(feedId: number, data: Record<string, any>) {
    return request('/feed/report', { method: 'POST', params: { feedId, ...data } });
}
/** 举报统一入口：商品 / 动态 / 用户（后端落 report 表） */
export async function reportSubmit(data: Record<string, any>) {
    return request('/report/submit', { method: 'POST', data });
}
export async function feedSearch(keyword: string) {
    return request('/feed/search', { method: 'GET', params: { keyword } });
}
export async function feedCommentList(feedId: number | string) {
    return request(`/feed/comments/${feedId}`, { method: 'GET' });
}

// 运营 admin
export async function adminOverview() {
    return request('/admin/marketplace/dashboard/overview', { method: 'GET' });
}
/** 自营运营核心指标（发布量/成交量/履约率/复购率/纠纷率 + 窗口趋势），days 仅 7/30/90 */
export async function adminOperationMetrics(days: number = 30) {
    return request('/admin/marketplace/dashboard/operation-metrics', {
        method: 'GET',
        params: { days },
    });
}
export async function adminProductAuditPage(params?: Record<string, any>) {
    return request('/admin/marketplace/product-audit/page', { method: 'GET', params });
}
export async function adminProductPass(productId: number) {
    return request('/admin/marketplace/product-audit/pass', {
        method: 'POST',
        params: { productId },
    });
}
export async function adminProductReject(data: Record<string, any>) {
    return request('/admin/marketplace/product-audit/reject', { method: 'POST', data });
}

// 动态审核
export async function adminFeedAuditPage(params?: Record<string, any>) {
    return request('/feed/audit/page', { method: 'GET', params });
}
export async function adminFeedPass(feedId: number) {
    return request('/feed/audit/pass', { method: 'POST', params: { feedId } });
}
export async function adminFeedReject(data: Record<string, any>) {
    return request('/feed/audit/reject', { method: 'POST', data });
}
export async function adminReportPage(params?: Record<string, any>) {
    return request('/admin/marketplace/report/page', { method: 'GET', params });
}
export async function adminReportHandle(data: Record<string, any>) {
    return request('/admin/marketplace/report/handle', { method: 'POST', data });
}
export async function adminReviewPage(params?: Record<string, any>) {
    return request('/admin/marketplace/review/page', { method: 'GET', params });
}
export async function adminReviewHide(reviewId: number) {
    return request('/admin/marketplace/review/hide', { method: 'POST', data: { reviewId } });
}
export async function adminReviewShow(reviewId: number) {
    return request('/admin/marketplace/review/show', { method: 'POST', data: { reviewId } });
}
export async function adminUserPage(params?: Record<string, any>) {
    return request('/admin/marketplace/user/page', { method: 'GET', params });
}
export async function adminUserBan(data: Record<string, any>) {
    return request('/admin/marketplace/user/ban', { method: 'POST', data });
}
export async function adminFinanceOverview() {
    return request('/admin/marketplace/finance/overview', { method: 'GET' });
}

// 客服 cs
export async function csDisputePage(params?: Record<string, any>) {
    return request('/admin/marketplace/dispute/page', { method: 'GET', params });
}
export async function csDisputeStats() {
    return request('/admin/marketplace/dispute/stats', { method: 'GET' });
}
export async function csDisputeDetail(disputeId: number | string) {
    return request(`/admin/marketplace/dispute/${disputeId}`, { method: 'GET' });
}
export async function csDisputeClaim(disputeId: number) {
    return request(`/admin/marketplace/dispute/claim/${disputeId}`, { method: 'POST' });
}
export async function csDisputeJudge(disputeId: number, data: Record<string, any>) {
    return request(`/admin/marketplace/dispute/${disputeId}/judge`, { method: 'POST', data });
}

// 支付
export async function payCreate(data: Record<string, any>) {
    return request('/trade/pay/create', { method: 'POST', data });
}
export async function payStatus(payNo: string) {
    return request(`/trade/pay/status/${payNo}`, { method: 'GET' });
}
// 模拟支付（仅 dev：billing.pay.mock=true 时后端可用；生产该接口直接抛异常）
export async function mockPayOrder(payNo: string) {
    return request(`/payment/mock-pay/${payNo}`, { method: 'POST' });
}

// AI 商品发布辅助
export async function assistProductCopy(data: Record<string, any>) {
    return request('/ai/assist/product-copy', { method: 'POST', data });
}
export async function assistProductImage(data: Record<string, any>) {
    return request('/ai/assist/product-image', { method: 'POST', data });
}

// AI 识图填单：从商品实拍图识别类目/标题/描述/成色
export async function assistProductRecognition(data: Record<string, any>) {
    return request('/ai/assist/product-recognition', { method: 'POST', data });
}
// AI 估价：真实成交分位数 × 成色系数（价格数字来自业务库统计）
export async function assistProductValuation(data: Record<string, any>) {
    return request('/ai/assist/product-valuation', { method: 'POST', data });
}

// 首页小组件：天气 + 日期 + AI 激励语池（游客可见）
// city 显式指定城市；不传时由后端按客户端 IP 定位（生产走 X-Forwarded-For，本地开发走出口 IP）
export async function homeWidget(params?: { city?: string }) {
    return request('/ai/assist/home-widget', { method: 'GET', params });
}

// IP 归属地城市识别（首页定位默认值，游客可见）：返回 { ip, city }，city 可能为 null
export async function ipCity() {
    return request('/ai/assist/ip-city', { method: 'GET' });
}

// 本人资料修改（SysProfileController#updateProfile）：只改传了的字段，
// 后端 updateUser 对空值跳过（手机/邮箱/昵称留空 = 保持原值，不是清空）
export async function updateMyProfile(data: Record<string, any>) {
    return request('/system/user/profile', { method: 'PUT', data });
}

// 本人修改登录密码（SysProfileController#updatePwd）：
// 旧密码错误 / 新旧相同 都由后端返回 {code:500,msg}，前端按 msg 提示
export async function updateMyPassword(data: {
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
}) {
    return request('/system/user/profile/updatePwd', { method: 'PUT', data });
}

// 结算（Phase 1）：卖家侧
export async function sellerAccount() {
    return request('/trade/seller/account', { method: 'GET' });
}
export async function sellerKycApply(data: {
    accountName: string;
    idCardNo: string;
    bankCardNo: string;
    bankName: string;
}) {
    return request('/trade/seller/account/apply', { method: 'POST', data });
}
export async function sellerWithdrawApply(amount: number) {
    return request('/trade/seller/withdraw/apply', { method: 'POST', data: { amount } });
}
export async function sellerWithdrawPage(params?: { current?: number; pageSize?: number }) {
    return request('/trade/seller/withdraw/page', { method: 'GET', params });
}

// 结算（Phase 1）：运营侧
export async function adminKycPage(params?: {
    kycStatus?: string;
    current?: number;
    pageSize?: number;
}) {
    return request('/admin/marketplace/kyc/page', { method: 'GET', params });
}
export async function adminKycReview(data: { accountId: number; result: string; note?: string }) {
    return request('/admin/marketplace/kyc/review', { method: 'POST', data });
}
export async function adminSettlementPage(params?: {
    status?: string;
    current?: number;
    pageSize?: number;
}) {
    return request('/admin/marketplace/settlement/page', { method: 'GET', params });
}
export async function adminSettlementDetail(settlementId: number | string) {
    return request(`/admin/marketplace/settlement/detail/${settlementId}`, { method: 'GET' });
}
export async function adminWithdrawPage(params?: {
    status?: string;
    current?: number;
    pageSize?: number;
}) {
    return request('/admin/marketplace/withdraw/page', { method: 'GET', params });
}
export async function adminWithdrawReview(data: {
    withdrawId: number;
    action: 'approve' | 'reject';
    reason?: string;
}) {
    return request('/admin/marketplace/withdraw/review', { method: 'POST', data });
}
export async function adminWithdrawPay(data: { withdrawId: number; voucherUrl: string }) {
    return request('/admin/marketplace/withdraw/pay', { method: 'POST', data });
}
export async function adminReconPage(params?: {
    bizDate?: string;
    status?: string;
    current?: number;
    pageSize?: number;
}) {
    return request('/admin/marketplace/recon/page', { method: 'GET', params });
}
export async function adminReconResolve(data: { diffId: number; note: string }) {
    return request('/admin/marketplace/recon/resolve', { method: 'POST', data });
}
export async function adminReconUnfreeze(data: { sellerId: number; reason: string }) {
    return request('/admin/marketplace/recon/unfreeze', { method: 'POST', data });
}
