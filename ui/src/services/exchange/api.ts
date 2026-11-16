import { request } from '@umijs/max';

/**
 * 交换广场（以物换物）- 统一 API（C 端 + 管理端）
 *
 * 路径与参数以后端 twelvet-exchange 实际 Controller 为准：
 * ExchangeSquareController / ExchangeItemController / ExchangeOfferController /
 * ExchangeOrderController / AdminExchangeController
 */

// ==================== 广场与物品（C 端） ====================

// 广场列表（类目/成色/关键词全文/交付方式筛选，hot_score 排序）→ TableDataInfo { total, records }
export async function squareList(params?: Record<string, any>) {
    return request('/exchange/square', { method: 'GET', params });
}

// 物品详情（浏览数+1，含主人换物主页摘要）→ { item, owner, offerCount }
export async function itemDetail(id: number | string) {
    return request(`/exchange/item/${id}`, { method: 'GET' });
}

// 发布物品（进 AI 初审 auditing）
export async function itemPublish(data: Record<string, any>) {
    return request('/exchange/item/publish', { method: 'POST', data });
}

// 我的换物物品（分状态 tab，后端固定 LIMIT 50）
export async function itemMyItems(params?: Record<string, any>) {
    return request('/exchange/item/my-items', { method: 'GET', params });
}

// 下架 / 重新上架（onswap↔offshelf）
export async function itemOffshelf(id: number | string) {
    return request(`/exchange/item/${id}/offshelf`, { method: 'POST' });
}

// 类目树（只读复用商品类目字典 product_category_dict）→ { code, name, parentCode, children }
export async function categoryTree() {
    return request('/exchange/category/tree', { method: 'GET' });
}

// 为你推荐（登录用户 want_desc 向量召回供给物品卡片，向量通道不可用时降级空数组）→ ExchangeItem[]
export async function squareRecommend(params?: Record<string, any>) {
    return request('/exchange/square/recommend', { method: 'GET', params });
}

// 可能想换 TA 的人（物品供给向量反向召回需求侧物品，降级空数组；ownerName 为主人昵称）
export async function itemWanters(id: number | string, params?: Record<string, any>) {
    return request(`/exchange/item/${id}/wanters`, { method: 'GET', params });
}

// 用户换物信用分（实时计算：评价均分 + 30天履约率 - 违约扣减，无数据默认 70）
// → { userId, creditScore, hasData, completedOrders30d, totalOrders30d, completionRate30d, penalizedCount30d, avgScore, reviewCount }
export async function userCredit(userId: number | string) {
    return request(`/exchange/credit/${userId}`, { method: 'GET' });
}

// ==================== 意向（C 端） ====================

// 发起意向（itemId + offerItemId / offerSnapshot 二选一 + message + diffAmount，72h 有效）
export async function offerCreate(data: Record<string, any>) {
    return request('/exchange/offer', { method: 'POST', data });
}

// 我收到的意向（pending 优先）
export async function offerReceived() {
    return request('/exchange/offer/received', { method: 'GET' });
}

// 我发出的意向
export async function offerSent() {
    return request('/exchange/offer/sent', { method: 'GET' });
}

// 接受意向（乐观锁事务，生成订单）→ { offerId, orderId }
export async function offerAccept(id: number | string) {
    return request(`/exchange/offer/${id}/accept`, { method: 'POST' });
}

// 婉拒意向
export async function offerReject(id: number | string) {
    return request(`/exchange/offer/${id}/reject`, { method: 'POST' });
}

// 撤回我发起的意向
export async function offerWithdraw(id: number | string) {
    return request(`/exchange/offer/${id}/withdraw`, { method: 'POST' });
}

// ==================== 订单（C 端） ====================

// 我的交换订单（A/B 双视角，服务端真分页；statuses 逗号分隔如 "created,delivering"，空 = 全部）
export async function orderMyOrders(params?: Record<string, any>) {
    return request('/exchange/order/my-orders', { method: 'GET', params });
}

// 订单详情（双边进度时间线 + 面交码 + 对方信息 + 流转日志 + 双向评价）
// → { order, mySide, peerId, peerInfo, pickupCodes, logs, reviews }
export async function orderDetail(id: number | string) {
    return request(`/exchange/order/${id}`, { method: 'GET' });
}

// 标记我方已交付（face 点按钮 / express 必传运单号）
export async function orderDeliver(id: number | string, trackNo?: string) {
    return request(`/exchange/order/${id}/deliver`, {
        method: 'POST',
        data: trackNo ? { trackNo } : {},
    });
}

// 确认已收到对方物品（双确认 → completed）
export async function orderConfirm(id: number | string) {
    return request(`/exchange/order/${id}/confirm`, { method: 'POST' });
}

// 取消（仅双方均未交付）
export async function orderCancel(id: number | string, reason: string) {
    return request(`/exchange/order/${id}/cancel`, { method: 'POST', data: { reason } });
}

// 发起纠纷（附原因，后台裁决）
export async function orderDispute(id: number | string, reason: string) {
    return request(`/exchange/order/${id}/dispute`, { method: 'POST', data: { reason } });
}

// 提交评价（1-5 星 + 文字，仅 completed 订单参与方）→ { reviewId, revieweeId }
export async function orderReview(id: number | string, data: { score: number; content?: string }) {
    return request(`/exchange/order/${id}/review`, { method: 'POST', data });
}

// ==================== 管理端（@SaCheckPermission） ====================

// 物品管理/待审列表（auditing 置顶）→ TableDataInfo { total, records }
export async function adminItemList(params: Record<string, any>) {
    return request('/admin/exchange/item', { method: 'GET', params });
}

// 人工审核通过（auditing → onswap）
export async function adminItemPass(id: number | string) {
    return request(`/admin/exchange/item/${id}/pass`, { method: 'POST' });
}

// 人工审核驳回（auditing → rejected，IM 通知主人）
export async function adminItemReject(id: number | string, reason: string) {
    return request(`/admin/exchange/item/${id}/reject`, { method: 'POST', data: { reason } });
}

// 订单列表（disputed 置顶）→ TableDataInfo { total, records }
export async function adminOrderPage(params: Record<string, any>) {
    return request('/admin/exchange/order', { method: 'GET', params });
}

// 裁决：按现状完成（disputed → completed）
export async function adminOrderForceComplete(id: number | string, remark: string) {
    return request(`/admin/exchange/order/${id}/force-complete`, {
        method: 'POST',
        data: { remark },
    });
}

// 裁决：取消并解锁物品（disputed → cancelled）
export async function adminOrderForceCancel(id: number | string, remark: string) {
    return request(`/admin/exchange/order/${id}/force-cancel`, {
        method: 'POST',
        data: { remark },
    });
}

// 数据看板（在架物品数、意向量、成交率、纠纷率、热门类目）
export async function adminStat() {
    return request('/admin/exchange/stat', { method: 'GET' });
}
