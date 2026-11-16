import {request} from '@umijs/max'

// 请求的控制器名称
const controller = "/system/tenant";

// 已知的租户编码（seed 数据，超管只读不增删）
// 后续如做动态化（新增租户），可换成 pageQuery 端点
export const KNOWN_TENANT_CODES = ['default', 'thu', 'pku'];

/**
 * 获取单个租户套餐详情
 * @param tenantCode 租户编码
 */
export async function getPlanInfo(tenantCode: string) {
    return request(`${controller}/${tenantCode}`, {
        method: 'GET',
    });
}

/**
 * 续费/升级套餐
 * @param tenantCode 租户编码
 * @param params {planCode, months}
 */
export async function renew(tenantCode: string, params: { planCode: string; months: number }) {
    return request(`${controller}/${tenantCode}/renew`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        data: params,
    });
}

/**
 * 创建扫码续费支付单（P2）
 * @param params {planCode, months, channel}
 */
export async function createRenewOrder(
    tenantCode: string,
    params: { planCode: string; months: number; channel: string }
) {
    return request<{ code: number; msg: string; data: SystemTenant.RenewOrderResult }>(`/payment/tenant-renew/${tenantCode}/order`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        data: params,
    });
}

/**
 * 查询续费支付状态（前端轮询，P2）
 */
export async function getRenewStatus(payNo: string) {
    return request<{ code: number; msg: string; data: SystemTenant.RenewStatusResult }>(`/payment/tenant-renew/status/${payNo}`, {
        method: 'GET',
    });
}

/**
 * 模拟支付成功（仅 billing.pay.mock=true 的 dev 演示，P2）
 */
export async function mockPayRenew(payNo: string) {
    return request(`/payment/tenant-renew/mock-pay/${payNo}`, {
        method: 'POST',
    });
}

/**
 * 拉取所有已知租户（前端硬编码 3 个 seed，循环调 getPlanInfo）
 */
export async function listAll() {
    const results = await Promise.all(
        KNOWN_TENANT_CODES.map(async (code) => {
            try {
                const {data} = await getPlanInfo(code);
                return data;
            } catch (e) {
                // 单个租户拉取失败不阻塞其他，console 留痕
                // eslint-disable-next-line no-console
                console.error(`[tenant-mgmt] fetch ${code} failed`, e);
                return null;
            }
        })
    );
    return results.filter(Boolean);
}

/**
 * AI 用量日账单（近 N 天明细 + 合计）
 * @param tenantCode 目标租户编码（仅平台超管生效，其他用户强制当前租户）
 * @param days 统计天数
 */
export async function getAiUsage(tenantCode: string, days: number = 30) {
    // 注意：@umijs/max 的 request 返回「完整响应体」{code,msg,data}（见
    // src/services/ant-design-pro/aimusic.ts:14 的说明，全项目 154 处解构信封）。
    // 因此类型参数必须是信封形状；此前误写为 payload 形状，导致调用方
    // `const {data} = await getAiUsage(...)` 在 tsc 下报错（运行时其实是正确的）。
    return request<{
        code: number;
        msg: string;
        data: {
            days: number;
            rows: { statDate: string; calls: number; promptTokens: number; completionTokens: number }[];
            total: { calls: number; promptTokens: number; completionTokens: number };
        };
    }>(`/ai/usage/daily`, {
        method: 'GET',
        params: {tenantCode, days},
    });
}
