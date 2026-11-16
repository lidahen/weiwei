const { NODE_ENV } = process.env;

const isDev: boolean = NODE_ENV === 'development';

/**
 * 请求 API 根地址。
 *
 * ⚠️ 生产环境必须用同源相对路径 `/api`，不能写成绝对域名：
 *   - 前端与 API 同源部署时，由反向代理把 `/api/*` 反代到 Java BFF(8080) 并 strip 掉
 *     `/api` 前缀（与 dev 的 proxy.ts 约定一致；原 deploy/Caddyfile 即按此配置）。
 *   - 原实现写死 `https://api.twelvet.cn`（twelvet 原版作者的域名），会导致部署后
 *     页面能打开但所有接口请求发往他人域名 → 登录失败。2026-09-22 修复。
 *   - 如需跨域部署（前端与 API 不同源），改为完整 URL，且必须同步配置 CORS 与租户头。
 */
const REQUEST_URI = isDev ? 'http://localhost:8080' : '/api';
/**
 * proTable默认配置
 */
export const proTableConfigs = {
    // 支持横向超出自适应
    scroll: {
        x: 'auto',
    },
    // 分页设置
    // pagination: {
    //     // 是否允许每页大小更改
    //     showSizeChanger: true,
    //     // 每页显示条数
    //     pageSize: 10,
    // },
    // 处理返回数据
    // postData: (data: any[]) => {
    //     if (data instanceof Array) {
    //         return data
    //     }
    //     const { records, total } = data
    //     console.log(records)
    //     return records
    // },
    // 展开子列表设置
    expandable: {
        // 展开列名称
        childrenColumnName: 'children',
        // 展开缩进px
        indentSize: 15,
        defaultExpandAllRows: false,
    },
};

const TWT: any = {
    // 请求API
    requestUri: REQUEST_URI,
    // 租户编码（商业化为每个租户单独部署前端时配置；置空 '' 则不发 X-Tenant-Code，
    // 后端 tenant.enabled=true 时写操作会因缺少租户上下文被拒）
    tenantCode: 'default',
    // OAuth2
    accessToken: 'TWT_access_token',
    refreshToken: 'TWT_refresh_token',
    // 权限cookie
    preAuthorize: 'TWT_pre_authorize',
    // 是否dev模式
    isDev,
    action: `${REQUEST_URI}/`,
    uploadUrl: `${REQUEST_URI}/dfs/commonUpload`,
    // 静态资源地址（阿里云OSS）
    // 2026-09-23：站点已迁到新账号 your-account-id 的 bucket your-bucket。
    // 原生产值 `https://your-old-account-id.onaliyun.com` 实测**无 DNS 解析、完全不可用**
    // （凡走 static 拼接的相对路径图片都显示不出来），故 dev/prod 统一改用 OSS 原生三级域名。
    static: 'https://your-bucket.oss-cn-beijing.aliyuncs.com',
    // 忽略无需登录的路径
    authIgnore: [
        '/login/oauth2/github',
        // TODO 调试使用，后续移除
        '/ai/flow',
    ],
};

export default TWT;
