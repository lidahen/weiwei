import TWT from '@/setting';
import { request } from '@umijs/max';

// 请求的控制器名称
const controller = '/auth';

/**
 * 登录
 * @param params 登录参数
 * @returns
 */
export async function login(params: Record<string, any>) {
    return request(`${controller}/oauth2/token`, {
        method: 'POST',
        headers: {
            'X-Requested-With': 'XMLHttpRequest',
            Accept: '*/*',
        },
        data: {
            ...params,
        },
        params: {
            scope: 'server',
        },
    });
}

/**
 * 发送短信验证码
 * @param mobile 手机号
 */
export async function sendSmsCode(mobile: string) {
    return request(`${controller}/sms/send`, {
        method: 'POST',
        data: { mobile },
    });
}

/**
 * 手机号验证码登录（首次登录自动注册）
 * @param mobile 手机号
 * @param code 验证码
 */
export async function smsLogin(mobile: string, code: string) {
    return request(`${controller}/sms/login`, {
        method: 'POST',
        data: { mobile, code },
    });
}

/**
 * 获取当前用户登录信息
 * @returns
 */
export async function getCurrentUser(): Promise<any> {
    return request(`system/user/getInfo`, {
        method: 'GET',
    });
}

/**
 * 获取当前用户菜单路由
 * @returns
 */
export async function getRouters(): Promise<any> {
    return request(`system/menu/getRouters`, {
        method: 'GET',
    });
}

/**
 * 刷新令牌
 */
export async function refreshToken() {
    const formData = new URLSearchParams();
    formData.append('refresh_token', localStorage.getItem(TWT.refreshToken) || '');
    formData.append('grant_type', 'refresh_token');
    return request(`${controller}/oauth2/refresh`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        data: formData,
    });
}
