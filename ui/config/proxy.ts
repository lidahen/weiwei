/**
 * @name 代理的配置
 * @see 在生产环境 代理是无法生效的，所以这里没有生产环境的配置
 * -------------------------------
 * The agent cannot take effect in the production environment
 * so there is no configuration of the production environment
 * For details, please see
 * https://pro.ant.design/docs/deploy
 *
 * @doc https://umijs.org/docs/guides/proxy
 */
export default {
    dev: {
        // ⚠️ 架构规则：前端只允许和 Java BFF(8080) 通信，再由 Java 转发给 Python ai-service(8001)。
        // 微服务拆分后 controller 都在 twelvet-ai-server，Python 只做算法层。
        // 任何 /api/** 直连 8001 都是 bug：会绕过鉴权/租户/配额，且 Python 的
        // lyrics 等接口强制要求 X-User-Id（只有 Java 会注入），直连必然 401 AUTH_REQUIRED。
        // 新增 AI 接口时，若 Java 侧缺路由，应在 twelvet-ai-server 补转发，而不是在这里加代理。
        '/api/': {
            target: 'http://localhost:8080',
            changeOrigin: true,
            pathRewrite: {
                '^/api': '',
            },
        },
    },
    /**
     * @name 详细的代理配置
     * @doc https://github.com/chimurai/http-proxy-middleware
     */
    test: {
        '/api/': {
            target: 'https://proapi.azurewebsites.net',
            changeOrigin: true,
            pathRewrite: {'^': ''},
        },
    },
    pre: {
        '/api/': {
            target: 'your pre url',
            changeOrigin: true,
            pathRewrite: {'^': ''},
        },
    },
};
