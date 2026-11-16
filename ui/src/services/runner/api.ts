import { request } from '@umijs/max';

/**
 * 互助服务 - 统一 API
 */

// 下单人：发布任务（免费/付费）
export async function taskPublish(data: Record<string, any>) {
    return request('/runner/task/publish', { method: 'POST', data });
}

// 任务详情
export async function taskDetail(id: number | string) {
    return request(`/runner/task/${id}`, { method: 'GET' });
}

// 取消任务
export async function taskCancel(id: number | string, reason: string) {
    return request(`/runner/task/${id}/cancel`, { method: 'POST', data: { reason } });
}

// 确认完成
export async function taskConfirm(id: number | string) {
    return request(`/runner/task/${id}/confirm`, { method: 'POST' });
}

// 申请退款
export async function taskRefund(id: number | string, reason: string) {
    return request(`/runner/task/${id}/refund`, { method: 'POST', data: { reason } });
}

// 我发布的
export async function taskMyList(params?: Record<string, any>) {
    return request('/runner/task/my-list', { method: 'GET', params });
}

// 接单人：互助大厅
export async function hallList(params?: Record<string, any>) {
    return request('/runner/hall', { method: 'GET', params });
}

// 接单
export async function hallAssign(id: number | string) {
    return request(`/runner/hall/${id}/assign`, { method: 'POST' });
}

// 开始服务
export async function hallStart(id: number | string) {
    return request(`/runner/hall/${id}/start`, { method: 'POST' });
}

// 提交完成
export async function hallFinish(id: number | string, finishImages?: string) {
    return request(`/runner/hall/${id}/finish`, {
        method: 'POST',
        data: finishImages ? { finishImages } : {},
    });
}

// 拒单
export async function hallReject(id: number | string, reason: string) {
    return request(`/runner/hall/${id}/reject`, { method: 'POST', data: { reason } });
}

// 我接的
export async function hallMyTaken(params?: Record<string, any>) {
    return request('/runner/hall/my-taken', { method: 'GET', params });
}

// 流转日志
export async function taskLogs(id: number | string) {
    return request(`/runner/hall/${id}/logs`, { method: 'GET' });
}

// 评价任务（发布者对已确认完成任务）
export async function taskReview(id: number | string, data: { rating: number; content?: string; tags?: string }) {
    return request(`/runner/task/${id}/review`, { method: 'POST', data });
}

// 接单人信用画像
export async function runnerCredit(userId: number | string) {
    return request(`/runner/credit/${userId}`, { method: 'GET' });
}

// 查询任务评价（未评返回空）
export async function taskReviewInfo(id: number | string) {
    return request(`/runner/task/${id}/review`, { method: 'GET' });
}

// 运营后台
export async function adminTaskPage(params: Record<string, any>) {
    return request('/runner/task/admin/page', { method: 'GET', params });
}

export async function adminStatistics() {
    return request('/admin/runner/statistics', { method: 'GET' });
}

// dev 模拟支付（后端 billing.pay.mock=true 时开放，生产不可用，见 Tenant 续费同一闭环）
export async function paymentMockPay(payNo: string) {
    return request(`/payment/mock-pay/${payNo}`, { method: 'POST' });
}
