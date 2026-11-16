/**
 * @name umi 的路由配置
 * @description 只支持 path,component,routes,redirect,wrappers,title 的配置
 * @param path  path 只支持两种占位符配置，第一种是动态参数 :id 的形式，第二种是 * 通配符，通配符只能出现路由字符串的最后。
 * @param component 配置 location 和 path 匹配后用于渲染的 React 组件路径。可以是绝对路径，也可以是相对路径，如果是相对路径，会从 src/pages 开始找起。
 * @param routes 配置子路由，通常在需要为多个路径增加 layout 组件时使用。
 * @param redirect 配置路由跳转
 * @param wrappers 配置路由组件的包装组件，通过包装组件可以为当前的路由组件组合进更多的功能。 比如，可以用于路由级别的权限校验
 * @doc https://umijs.org/docs/guides/routes
 */
export default [
    {
        path: '/login',
        layout: false,
        name: '登录',
        component: './Login',
    },
    {
        path: '/',
        // 项目无脚手架欢迎页：根路径直接进邻里二手工作台
        redirect: '/home',
    },
    {
        title: '个人账号',
        name: 'account',
        icon: 'icon-Account',
        path: '/account',
        routes: [
            // {
            //     title: '个人中心',
            //     name: 'center',
            //     icon: 'icon-user-info',
            //     path: '/Account/center',
            //     component: './Account/center'
            // },
            {
                title: '个人设置',
                name: 'settings',
                icon: 'icon-settings',
                path: '/account/settings',
                component: './Account/Settings',
            },
        ],
    },
    {
        title: '系统管理',
        name: 'systemManagement',
        icon: 'icon-system',
        path: '/system',
        routes: [
            {
                title: '菜单管理',
                icon: 'icon-menu',
                name: 'menu',
                path: '/system/menu',
                component: './System/Menu',
            },
            {
                title: '字典管理',
                icon: 'icon-dictionaries',
                name: 'dictionaries',
                path: '/system/dictionaries',
                component: './System/Dictionaries',
            },
            {
                title: 'OAuth2终端',
                icon: 'icon-client',
                name: 'client',
                path: '/system/client',
                component: './System/Client',
            },
            {
                title: '分布式文件管理',
                icon: 'icon-dfs',
                name: 'dfs',
                path: '/system/dfs',
                component: './System/Dfs',
            },
            {
                title: '令牌管理',
                icon: 'icon-token',
                name: 'token',
                path: '/system/token',
                component: './System/Token',
            },
            {
                title: '国际化',
                icon: 'icon-I18n',
                name: 'I18n',
                path: '/system/I18n',
                component: './System/I18n',
            },
            {
                title: '租户管理',
                icon: 'icon-team',
                name: 'tenant',
                path: '/system/tenant',
                component: './System/Tenant',
            },
        ],
    },
    // 跳转
    {
        path: '/monitor',
        redirect: '/monitor/redis',
    },
    {
        title: '系统监控',
        icon: 'icon-monitor',
        name: 'monitor',
        path: '/monitor',
        routes: [
            {
                title: '缓存监控',
                icon: 'icon-job',
                name: 'job',
                path: '/monitor/redis',
                component: './Monitor/Redis',
            },
            {
                title: '定时任务',
                icon: 'icon-job',
                name: 'job',
                path: '/monitor/job',
                component: './Monitor/Job',
            },
        ],
    },
    // 跳转
    {
        path: '/human',
        redirect: '/human/staff',
    },
    {
        title: '人力管理',
        icon: 'icon-human-resources',
        name: 'human',
        path: '/human',
        routes: [
            {
                title: '职员管理',
                icon: 'icon-team',
                name: 'staffManagement',
                path: '/human/staff',
                component: './Human/Staff',
            },
            {
                title: '角色管理',
                icon: 'icon-role',
                name: 'role',
                path: '/human/role',
                component: './Human/Role',
            },
            {
                title: '岗位管理',
                icon: 'icon-post',
                name: 'post',
                path: '/human/post',
                component: './Human/Post',
            },
            {
                title: '部门管理',
                icon: 'icon-dept',
                name: 'dept',
                path: '/human/dept',
                component: './Human/Dept',
            },
        ],
    },
    /*{
        title: '个人账号',
        name: 'Account',
        icon: 'icon-Account',
        path: '/Account',
        routes: [
            {
                title: '个人中心',
                name: 'center',
                icon: 'icon-user-info',
                path: '/Account/center',
                component: './Account/center'
            },
            {
                title: '个人设置',
                name: 'settings',
                icon: 'icon-settings',
                path: '/Account/settings',
                component: './Account/settings'
            }
        ]
    },*/
    {
        title: 'AI知识库',
        name: 'ai',
        icon: 'icon-ai',
        path: '/ai',
        routes: [
            {
                title: '模型管理',
                name: 'aiModel',
                icon: 'icon-ai-model',
                path: '/ai/model',
                component: './AI/Model',
            },
            {
                title: 'MCP服务',
                name: 'aiMCP',
                icon: 'icon-ai-mcp',
                path: '/ai/mcp',
                component: './AI/Mcp',
            },
            {
                title: '知识库管理',
                name: 'aiKnowledge',
                icon: 'icon-ai-knowledge',
                path: '/ai/knowledge',
                component: './AI/Knowledge',
            },
            // 文档管理
            {
                title: '文档管理',
                name: 'aiDoc',
                icon: 'icon-ai-doc',
                path: '/ai/doc',
                component: './AI/Doc',
            },
            {
                title: 'AI导购',
                name: 'aiShoppingGuide',
                icon: 'icon-ai-chat',
                path: '/ai/shopping-guide',
                component: './AI/ShoppingGuide',
            },
            {
                title: '社区日报',
                name: 'aiDailyReport',
                icon: 'icon-ai',
                path: '/ai/report',
                component: './AI/DailyReport',
            },
            {
                title: '社区管家',
                name: 'aiButler',
                icon: 'icon-ai',
                path: '/ai/butler',
                component: './AI/Butler',
            },
            {
                title: '图片生成',
                name: 'aiImage',
                icon: 'icon-ai-image',
                path: '/ai/image',
                component: './AI/Image',
            },
            {
                title: '视频生成',
                name: 'aiVideo',
                icon: 'icon-ai-video',
                path: '/ai/video',
                component: './AI/Video',
            },
            {
                title: 'AI 音乐',
                name: 'aiMusic',
                icon: 'icon-ai-music',
                path: '/ai/music',
                routes: [
                    {
                        path: '/ai/music',
                        component: './AI/Music/index',
                    },
                    {
                        title: '我的作品',
                        path: '/ai/music/works',
                        component: './AI/Music/Works',
                    },
                    {
                        title: '作品详情',
                        path: '/ai/music/works/:id',
                        component: './AI/Music/Detail',
                        hideInMenu: true,
                    },
                    {
                        title: '我的音色',
                        path: '/ai/music/voices',
                        component: './AI/Music/Voices',
                    },
                ],
            },
            {
                title: 'AI 创作工作台',
                name: 'aiCreation',
                icon: 'icon-ai-video',
                path: '/ai/creation',
                routes: [
                    {
                        title: '创作项目',
                        path: '/ai/creation',
                        component: './AI/Creation/index',
                    },
                    {
                        // 后端菜单存绝对路径（与图片/视频生成同惯例），菜单点击跳转此路径
                        title: '创作项目',
                        path: '/ai/creation/index',
                        component: './AI/Creation/index',
                        hideInMenu: true,
                    },
                    {
                        title: '分镜编辑器',
                        path: '/ai/creation/editor/:id',
                        component: './AI/Creation/Editor',
                        hideInMenu: true,
                    },
                    {
                        title: '爆款模板库',
                        path: '/ai/creation/template',
                        component: './AI/Creation/Template',
                    },
                    {
                        title: '人设管理',
                        path: '/ai/creation/persona',
                        component: './AI/Creation/Persona',
                    },
                ],
            },
        ],
    },
    // 跳转
    {
        path: '/log',
        redirect: '/log/operation',
    },
    {
        title: '日志管理',
        name: 'log',
        icon: 'icon-log',
        path: '/log',
        routes: [
            {
                title: '操作日志',
                name: 'operation',
                icon: 'icon-log-operation',
                path: '/log/operation',
                component: './Log/Operation',
            },
            {
                title: '登录日志',
                name: 'login',
                icon: 'icon-log-login',
                path: '/log/login',
                component: './Log/Login',
            },
            {
                title: '定时任务日志',
                name: 'job',
                icon: 'icon-log-job',
                path: '/log/job',
                component: './Log/Job',
            },
        ],
    },
    // 跳转
    {
        path: '/tool',
        redirect: '/tool/gen',
    },
    {
        title: '工具箱',
        name: 'tool',
        icon: 'icon-tool-box',
        path: '/tool',
        routes: [
            {
                title: '开发平台',
                icon: 'icon-gen-code',
                name: 'gen',
                path: '/tool/code',
                routes: [
                    {
                        title: '数据源',
                        icon: 'icon-gen-ds_conf',
                        name: 'ds_conf',
                        path: '/tool/code/ds_conf',
                        component: './Tool/Code/DsConf',
                    },
                    {
                        title: '代码生成器',
                        icon: 'icon-gen-code',
                        name: 'code',
                        path: '/tool/code/gen',
                        component: './Tool/Code/Gen',
                    },
                    {
                        title: '元数据管理',
                        icon: 'icon-gen-metadata',
                        name: 'metadata',
                        path: '/tool/code/metadata',
                        routes: [
                            {
                                title: '字段管理',
                                icon: 'icon-gen-metadata-type',
                                name: 'type',
                                path: '/tool/code/metadata/type',
                                component: './Tool/Code/Metadata/Type',
                            },
                            {
                                title: '模板管理',
                                icon: 'icon-gen-metadata-template',
                                name: 'template',
                                path: '/tool/code/metadata/template',
                                component: './Tool/Code/Metadata/Template',
                            },
                            {
                                title: '模板分组',
                                icon: 'icon-gen-metadata-template_group',
                                name: 'template_group',
                                path: '/tool/code/metadata/template_group',
                                component: './Tool/Code/Metadata/TemplateGroup',
                            },
                        ],
                    },
                ],
            },

            // {
            //     title: '图形化编辑器',
            //     name: 'graphicalEditor',
            //     icon: 'icon-graphical-edit',
            //     path: '/tool/graphicalEditor',
            //     routes: [
            //         {
            //             title: '流程编辑器',
            //             name: 'flow',
            //             icon: 'icon-flow-edit',
            //             path: '/tool/graphicalEditor/flow',
            //             component: './tool/graphicalEditor/flow'
            //         },
            //         {
            //             title: '脑图编辑器',
            //             name: 'mind',
            //             icon: 'icon-mind-edit',
            //             path: '/tool/graphicalEditor/mind',
            //             component: './tool/graphicalEditor/mind'
            //         },
            //         {
            //             title: '拓扑编辑器',
            //             name: 'koni',
            //             icon: 'icon-koni-edit',
            //             path: '/tool/graphicalEditor/koni',
            //             component: './tool/graphicalEditor/koni'
            //         },
            //     ]
            // }
        ],
    },
    // ==================== 二手交易 - 业务路由 ====================
    {
        path: '/home',
        name: '首页',
        title: '首页',
        icon: 'icon-home',
        component: './marketplace/Home',
    },
    {
        path: '/search',
        title: '搜索',
        component: './marketplace/Search',
        hideInMenu: true,
    },
    {
        path: '/product',
        name: '商品',
        icon: 'icon-shop',
        hideInMenu: true,
        routes: [
            { path: '/product/detail/:id', title: '商品详情', component: './marketplace/ProductDetail' },
            { path: '/product/publish', component: './marketplace/ProductPublish' },
            { path: '/product/publish/:id', component: './marketplace/ProductPublish' },
            { path: '/product/my', component: './marketplace/ProductMyList' },
        ],
    },
    {
        path: '/feed',
        name: '动态广场',
        icon: 'icon-fire',
        routes: [
            { path: '/feed/stream', name: '广场', title: '动态广场', component: './marketplace/FeedStream' },
            { path: '/feed/publish', name: '发布', hideInMenu: true, component: './marketplace/FeedPublish' },
        ],
    },
    {
        path: '/feed/detail/:feedId',
        title: '动态详情',
        hideInMenu: true,
        component: './marketplace/FeedDetail',
    },
    {
        path: '/user',
        hideInMenu: true,
        routes: [
            { path: '/user/profile/me', component: './marketplace/UserProfile' },
            { path: '/user/profile/:userId', title: '用户主页', component: './marketplace/UserProfile' },
            { path: '/user/settings', component: './marketplace/UserSettings' },
            { path: '/user/address', component: './marketplace/UserAddress' },
            { path: '/user/follows', component: './marketplace/UserFollows' },
            { path: '/user/favorites', component: './marketplace/UserFavorites' },
            { path: '/user/orders', component: './marketplace/OrderList' },
            { path: '/user/refunds', component: './marketplace/RefundList' },
            { path: '/user/settlement', name: '卖家中心', component: './marketplace/MySettlement' },
        ],
    },
    {
        path: '/trade',
        hideInMenu: true,
        routes: [
            { path: '/trade/checkout', component: './marketplace/Checkout' },
            { path: '/trade/pay', component: './marketplace/Pay' },
            { path: '/trade/pay/result', component: './marketplace/PayResult' },
            { path: '/trade/order/detail', component: './marketplace/OrderDetail' },
            { path: '/trade/ship', component: './marketplace/Ship' },
            { path: '/trade/refund/apply', component: './marketplace/RefundApply' },
            { path: '/trade/refund/detail', component: './marketplace/RefundDetail' },
            { path: '/trade/review', component: './marketplace/Review' },
        ],
    },
    {
        path: '/im',
        hideInMenu: true,
        routes: [
            { path: '/im/conversations', component: './marketplace/ConversationList' },
            { path: '/im/chat', component: './marketplace/Chat' },
        ],
    },
    {
        path: '/admin/marketplace',
        name: '运营管理',
        icon: 'icon-monitor',
        routes: [
            // 精确命中父路径时重定向到首个子页（父级 redirect 会吞掉子页渲染）
            { path: '/admin/marketplace', redirect: '/admin/marketplace/dashboard' },
            {
                path: '/admin/marketplace/dashboard',
                name: '运营仪表盘',
                component: './marketplace/admin/Dashboard',
            },
            {
                path: '/admin/marketplace/product-audit',
                name: '商品审核',
                component: './marketplace/admin/ProductAudit',
            },
            {
                path: '/admin/marketplace/feed-audit',
                name: '动态审核',
                component: './marketplace/admin/FeedAudit',
            },
            {
                path: '/admin/marketplace/review',
                name: '评价管理',
                component: './marketplace/admin/ReviewManage',
            },
            {
                path: '/admin/marketplace/recommend-stats',
                name: '推荐效果',
                component: './marketplace/admin/RecommendStats',
            },
            {
                path: '/admin/marketplace/funnel',
                name: '社区漏斗',
                component: './marketplace/admin/Funnel',
            },
            {
                path: '/admin/marketplace/report',
                name: '举报处理',
                component: './marketplace/admin/Report',
            },
            {
                path: '/admin/marketplace/user',
                name: '用户管理',
                component: './marketplace/admin/User',
            },
            {
                path: '/admin/marketplace/finance',
                name: '资金对账',
                component: './marketplace/admin/Finance',
            },
            {
                path: '/admin/marketplace/refund',
                name: '退款管理',
                component: './marketplace/admin/Refund',
            },
            {
                path: '/admin/marketplace/kyc',
                name: '实名审核',
                component: './marketplace/admin/Kyc',
            },
            {
                path: '/admin/marketplace/settlement',
                name: '结算单',
                component: './marketplace/admin/Settlement',
            },
            {
                path: '/admin/marketplace/withdraw',
                name: '提现审核',
                component: './marketplace/admin/Withdraw',
            },
            {
                path: '/admin/marketplace/recon',
                name: '结算对账',
                component: './marketplace/admin/Recon',
            },
        ],
    },
    {
        path: '/runner',
        name: '社区互助',
        icon: 'icon-run',
        routes: [
            // 精确命中父路径时重定向到首个子页
            { path: '/runner', redirect: '/runner/hall' },
            {
                path: '/runner/hall',
                name: '互助大厅',
                component: './Runner/Hall',
            },
            {
                path: '/runner/hall/my-taken',
                name: '我接的任务',
                component: './Runner/MyTaken',
            },
            {
                path: '/runner/task/publish',
                name: '发布任务',
                component: './Runner/Publish',
            },
            {
                path: '/runner/task/my-list',
                name: '我发布的',
                component: './Runner/MyList',
            },
            {
                path: '/runner/task/detail',
                hideInMenu: true,
                component: './Runner/Detail',
            },
        ],
    },
    {
        path: '/admin/runner',
        name: '互助管理',
        icon: 'icon-monitor',
        routes: [
            { path: '/admin/runner', redirect: '/admin/runner/task' },
            {
                path: '/admin/runner/task',
                name: '任务管理',
                component: './Runner/admin/TaskPage',
            },
            {
                path: '/admin/runner/stats',
                name: '数据看板',
                component: './Runner/admin/Stats',
            },
        ],
    },
    // ==================== 交换广场 - 业务路由 ====================
    {
        path: '/exchange',
        name: '交换广场',
        icon: 'icon-order',
        routes: [
            // 精确命中父路径时重定向到首个子页
            { path: '/exchange', redirect: '/exchange/square' },
            {
                path: '/exchange/square',
                name: '换物广场',
                title: '交换广场',
                component: './Exchange/Square',
            },
            {
                path: '/exchange/my-items',
                name: '我的物品',
                component: './Exchange/MyItems',
            },
            {
                path: '/exchange/offers',
                name: '我的意向',
                component: './Exchange/Offers',
            },
            {
                path: '/exchange/my-orders',
                name: '我的交换',
                component: './Exchange/MyOrders',
            },
            {
                path: '/exchange/publish',
                name: '发布物品',
                hideInMenu: true,
                component: './Exchange/Publish',
            },
            {
                path: '/exchange/detail/:id',
                name: '物品详情',
                title: '换物详情',
                hideInMenu: true,
                component: './Exchange/Detail',
            },
            {
                path: '/exchange/order-detail/:id',
                name: '订单详情',
                hideInMenu: true,
                component: './Exchange/OrderDetail',
            },
        ],
    },
    {
        path: '/admin/exchange',
        name: '交换管理',
        icon: 'icon-monitor',
        routes: [
            { path: '/admin/exchange', redirect: '/admin/exchange/item-audit' },
            {
                path: '/admin/exchange/item-audit',
                name: '物品审核',
                component: './Exchange/admin/ItemAudit',
            },
            {
                path: '/admin/exchange/order',
                name: '订单管理',
                component: './Exchange/admin/OrderPage',
            },
            {
                path: '/admin/exchange/stats',
                name: '数据看板',
                component: './Exchange/admin/Stats',
            },
        ],
    },
    {
        path: '/cs/marketplace',
        name: '客服工作台',
        icon: 'icon-team',
        routes: [
            // 精确命中父路径时重定向到首个子页（父级 redirect 会吞掉子页渲染）
            { path: '/cs/marketplace', redirect: '/cs/marketplace/disputes' },
            {
                path: '/cs/marketplace/disputes',
                name: '纠纷列表',
                component: './marketplace/cs/DisputeList',
            },
            {
                path: '/cs/marketplace/disputes/:id',
                component: './marketplace/cs/DisputeDetail',
                hideInMenu: true,
            },
        ],
    },
    {
        path: '*',
        component: './404',
    },
];
