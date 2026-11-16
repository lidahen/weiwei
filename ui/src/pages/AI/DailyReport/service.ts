import { request } from '@umijs/max';

// 请求的控制器名称
const controller = '/ai/report';

/**
 * 查询当前租户日报分页（日期倒序）
 * @param query 查询参数（current / pageSize，后端 TableSupport 分页）
 */
export async function listDailyReports(query: { [key: string]: any }) {
    return request(`${controller}/list`, {
        method: `get`,
        params: query,
    });
}

/**
 * 查询日报详情
 * @param id 日报ID
 */
export async function getDailyReport(id: string | number) {
    return request(`${controller}/${id}`, {
        method: `get`,
    });
}

/**
 * 立即生成/覆盖当日日报（管理员，幂等）
 */
export async function generateDailyReport() {
    return request(`${controller}/generate`, {
        method: `post`,
    });
}
