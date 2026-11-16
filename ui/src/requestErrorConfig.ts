import type { RequestOptions } from '@@/plugin-request/request';
import type { RequestConfig } from '@umijs/max';
import { request, getLocale, history } from '@umijs/max';
import { message, notification } from 'antd';
import { refreshToken as refreshTokenService } from './pages/Login/service';
import TWT from './setting';
import { resolveTenantCode } from './utils/tenant';
import { logout, removeAuthority, setAuthority, system } from './utils/twelvet';
import { isPublicPath } from './utils/publicAccess';

// 与后端约定的响应数据格式
interface ResponseStructure {
    code: number;
    msg: string;
    data?: any;
}

/**
 * 自定义刷新token失效
 */
class RefreshTokenError extends Error {
    constructor(message: string) {
        super(message); // 调用父类的构造函数，设置错误消息
        this.name = 'RefreshTokenError'; // 可以设置自定义的错误名称
        this.stack = new Error().stack; // 捕获堆栈信息
    }
}

/**
 * 刷新token
 * @param url 访问URL（成功刷新后重新请求地址）
 * @param method 请求方式
 * @param responseType 请求类型
 * @param data
 * @param params body
 * @param params query
 * @returns
 */
const refreshToken = async (
    url?: string,
    method?: string,
    responseType?: string,
    data?: any,
    params?: any,
) => {
    // 续签失败将要求重新登录
    const res = await refreshTokenService();

    if (res.code === 400) {
        notification.error({
            message: `续签失败`,
            description: `续签失败,请重新登录`,
        });
        logout();
        throw new RefreshTokenError('刷新token操作失败');
    }

    setAuthority(res.data);

    // 重新请求本次数据
    if (url) {
        return await request(url?.replace(TWT.requestUri, ''), {
            method,
            responseType: responseType === 'blob' ? 'blob' : 'json',
            // 需要原始响应头
            getResponse: true,
            data,
            params,
        });
    }
    throw new RefreshTokenError('刷新token操作失败');
};

/**
 * 响应处理器
 * @param response Response
 */
const responseHeaderInterceptor = (response: any) => {
    const {
        data: { code },
        config,
    } = response;

    let newResponse = response;

    // 处理401状态
    if (code === 401) {
        const { data, params, method, url, responseType } = config;
        // 执行刷新token
        newResponse = refreshToken(url, method, responseType, data, params);
        return newResponse;
    } else {
        return newResponse;
    }
};

/**
 * pro 自带的错误处理， 可以在这里做自己的改动
 * @doc https://umijs.org/docs/max/request#配置
 */
export const errorConfig: RequestConfig = {
    // 错误处理： umi@3 的错误处理方案。
    errorConfig: {
        // 错误抛出
        errorThrower: (res) => {
            const { code, data, msg } = res as unknown as ResponseStructure;
            if (code !== 200) {
                const error: any = new Error(msg);
                error.name = 'BizError';
                error.info = { code, msg, data };
                throw error; // 抛出自制的错误
            }
        },
        // 错误接收及处理
        errorHandler: async (error: any, opts: any) => {
            if (opts?.skipErrorHandler) throw error;
            try {
                if (error.response) {
                    // Axios 的错误
                    // 请求成功发出且服务器也响应了状态码，但状态代码超出了 2xx 的范围
                    //message.error('Response status:', error.response.status);

                    const {
                        data: { msg },
                        status,
                    } = error.response;
                    if (status === 504) {
                        message.error('服务无响应');
                        throw error;
                    }
                    if (status === 401) {
                        // 公开页（游客可浏览）：401 只说明当前是游客或 token 已过期，
                        // 清掉失效凭证后静默降级为游客态，绝不跳登录 —— 否则游客一进
                        // 公开页就被踢走，SEO 索引与游客浏览全部失效。
                        if (isPublicPath(history.location.pathname)) {
                            removeAuthority();
                            return;
                        }
                        message.warning('Token已失效,请重新登录！');
                        return logout();
                    } else {
                        message.error(msg);
                        throw error;
                    }
                } else if (error.request) {
                    // 请求已经成功发起，但没有收到响应
                    // \`error.request\` 在浏览器中是 XMLHttpRequest 的实例，
                    // 而在node.js中是 http.ClientRequest 的实例
                    message.error('None response! Please retry.');
                    throw error;
                } else {
                    // 发送请求时出了点问题
                    throw error;
                }
            } catch (error) {
                system.error(error);
                if (error instanceof RefreshTokenError) {
                    return;
                }
                // catch 中的 error 是 unknown：显式收窄后再取 HTTP 状态码，
                // 语义与原写法（error.response && error.response.status === 500）一致
                const httpStatus = (error as { response?: { status?: number } })?.response?.status;
                if (httpStatus === 500) {
                    return;
                }
                system.error('=====', error);
                message.error('无法链接API，请联系管理！');
            }
        },
    },

    // 请求拦截器
    requestInterceptors: [
        (config: RequestOptions) => {
            const local = localStorage.getItem(TWT.accessToken);

            const { access_token } = local ? JSON.parse(local) : { access_token: '' };

            // 显式声明索引签名：两个分支构造的对象都没有索引签名，
            // 下面写 authHeader['X-Tenant-Code'] 会报 TS7053。
            // 值类型按 HTTP 头实际可能的取值声明，避免用 any。
            let authHeader: Record<string, string | number | boolean>;
            if (!config.headers?.Authorization) {
                authHeader = {
                    ...config.headers,
                    Authorization: `Bearer ${access_token}`,
                    // 国际化
                    'Accept-Language': getLocale(),
                };
            } else {
                authHeader = {
                    ...config.headers,
                };
            }
            // 租户隔离：所有请求携带租户编码（后端 TenantFilter 识别；配置为空则不发，走兼容模式）
            // P2 子域名：访问 thu.localhost / thu.ershou.com 时，子域名优先于配置的 tenantCode
            // 统一走 utils/tenant（IP 字面量不取子域名，保留字回落 —— 详见该模块注释）
            const tenantCode = resolveTenantCode();
            if (tenantCode) {
                authHeader['X-Tenant-Code'] = tenantCode;
            }
            // 拦截请求配置，进行个性化处理。
            const url = config?.url;
            const requestUri = TWT.requestUri.endsWith('/')
                ? TWT.requestUri.slice(0, -1)
                : TWT.requestUri;
            return {
                ...config,
                // 设置请求URI
                url: url?.charAt(0) === '/' ? `${requestUri}${url}` : `${requestUri}/${url}`,
                headers: authHeader,
            };
        },
    ],

    // 响应拦截器
    responseInterceptors: [
        (response) => {
            return responseHeaderInterceptor(response);
        },
    ],
};
