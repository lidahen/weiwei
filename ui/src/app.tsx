import MarketplaceFooter from '@/components/Footer/MarketplaceFooter';
import ImBell from '@/components/ImBell';
import MobileTabBar from '@/components/MobileTabBar';
import AiGuideFloat from '@/pages/marketplace/AiGuideFloat';
import { getToken, timedRefreshToken } from '@/utils/twelvet';
import { isPublicPath } from '@/utils/publicAccess';
import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { PageLoading } from '@ant-design/pro-components';
import { history, RunTimeLayoutConfig, SelectLang } from '@umijs/max';
import { ConfigProvider, message } from 'antd';
import React from 'react';
import defaultSettings from '../config/defaultSettings';
import { AvatarDropdown, AvatarName } from './components/RightContent/AvatarDropdown';
import { getCurrentUser, getRouters } from './pages/Login/service';
import { errorConfig } from './requestErrorConfig';
import TWT from './setting';

const loginPath = '/login';

/**
 * @see  https://umijs.org/zh-CN/plugins/plugin-initial-state
 * */
export async function getInitialState(): Promise<{
    settings?: Partial<LayoutSettings>;
    currentUser?: any;
    loading?: boolean;
    fetchUserInfo?: any;
}> {
    const fetchUserInfo = async (redirectOnFailure = true) => {
        try {
            const res = await getCurrentUser();
            const { code, msg } = res;
            if (code !== 200) {
                if (redirectOnFailure) message.error(msg);
                return undefined;
            }
            // getInfo 返回 JsonResult 包装，用户信息在 data 内层
            const { user = {}, roles, permissions } = res.data;

            localStorage.setItem(TWT.preAuthorize, JSON.stringify(permissions));

            // 从后端动态加载当前用户的角色菜单树 (RBAC 统一管理)
            let menus: any[] = [];
            try {
                const { data } = await getRouters();
                menus = data;
            } catch (e) {
                menus = [];
            }

            return {
                user,
                menus,
                roles,
                permissions,
            };
        } catch (error) {
            if (redirectOnFailure) history.push(loginPath);
            return undefined;
        }
        return undefined;
    };
    const pathname = history.location.pathname;

    if (pathname !== loginPath) {
        if (isPublicPath(pathname) || TWT.authIgnore.includes(pathname)) {
            // 公开页：有 token 就尝试恢复登录态，失败静默；无 token 直接当游客
            const currentUser = getToken() ? await fetchUserInfo(false) : undefined;
            return { fetchUserInfo, currentUser, settings: defaultSettings };
        }

        const currentUser = await fetchUserInfo();

        // 定时刷新Token
        timedRefreshToken();

        return {
            fetchUserInfo,
            currentUser,
            settings: defaultSettings,
        };
    }
    return {
        fetchUserInfo,
        settings: defaultSettings,
    };
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout，需要定制化的可以查看。
export const layout: RunTimeLayoutConfig = ({ initialState }) => {
    return {
        // 自定义头的 render 方法
        // headerRender: () => {
        //   return 'headerRender test'
        // },
        // 自定义菜单的 render 方法
        // menuRender: () => {
        //   return 'menuRender test'
        // },
        // 自定义底部 —— weiwei marketplace 风格
        // 桌面端渲染 MarketplaceFooter；移动端(<768px)换底部 TabBar（组件内部判断，桌面返回 null）
        footerRender: () => (
            <>
                <MarketplaceFooter />
                <MobileTabBar />
            </>
        ),
        // 渲染菜单数据：登录用户走后端 RBAC 菜单树；游客只给公开入口，
        // 否则桌面端游客在公开页上没有任何站内导航（移动端还有底部 TabBar）。
        menuDataRender: () =>
            initialState?.currentUser?.menus
                ? initialState.currentUser.menus
                : [
                      { path: '/home', name: '首页' },
                      { path: '/search', name: '搜索' },
                      { path: '/feed/stream', name: '动态' },
                      { path: '/exchange/square', name: '换物' },
                  ],
        // 全局 IM 未读铃铛（runner 状态通知经 IM 下发 system 消息，见 docs/跑腿消息通知设计.md）
        actionsRender: () => [
            <ImBell key="imBell" />,
            // 移动端收纳（复查报告 P1-3）：<768px 隐藏语言切换，仅留铃铛+头像
            <span key="SelectLang" className="tw-desktop-only">
                <SelectLang />
            </span>,
        ],
        avatarProps: {
            src: (() => {
                const avatar = initialState?.currentUser?.user?.avatar;
                if (!avatar) return undefined;
                // avatar 可能是完整 URL（如 dicebear），也可能是相对路径（如 /avatar.jpg）
                // 完整 URL 直接使用，相对路径拼接 OSS 域名
                return avatar.startsWith('http') ? avatar : `${TWT.static}${avatar}`;
            })(),
            onError: () => true,
            title: <AvatarName />,
            render: (_, avatarChildren) => {
                return <AvatarDropdown>{avatarChildren}</AvatarDropdown>;
            },
        },
        disableContentMargin: false,
        // 水印设置
        /*waterMarkProps: {
          content: initialState?.currentUser?.user?.username,
        },*/
        onPageChange: () => {
            const { location } = history;
            // 如果没有登录，重定向到 login
            if (
                !initialState?.currentUser &&
                !loginPath.includes(location.pathname) &&
                !isPublicPath(location.pathname) &&
                !TWT.authIgnore.includes(location.pathname)
            ) {
                history.push(loginPath);
            }
        },
        menu: {
            defaultOpenAll: false,
            // 关闭菜单多语言
            locale: false,
        },
        links: [],
        menuHeaderRender: undefined,
        // 自定义 403 页面
        unAccessible: <div>unAccessible</div>,
        // 增加一个 loading 的状态
        childrenRender: (children, props) => {
            if (initialState?.loading) return <PageLoading />;
            // C 端商店页面挂 AI 导购悬浮入口（登录用户可见；管理台页面不显示）
            const cEndPrefixes = [
                '/home',
                '/search',
                '/product',
                '/feed',
                '/user',
                '/trade',
                '/im',
            ];
            // childrenRender 的 props.location 可能为空，用 history 取当前路径
            const pathname = props.location?.pathname || history.location.pathname || '';
            const isCEnd = cEndPrefixes.some((p) => pathname === p || pathname.startsWith(`${p}/`));
            return (
                <>
                    {children}
                    {isCEnd && initialState?.currentUser && <AiGuideFloat />}
                </>
            );
        },
        ...initialState?.settings,
    };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 和 ahooks 的 useRequest 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request = {
    ...errorConfig,
};

/**
 * 全局主题: 极简留白 (Forest + Cream + Sand)
 */
import MarketplaceMotion from './pages/marketplace/styles/motion';

export function rootContainer(container: React.ReactNode) {
    return React.createElement(
        ConfigProvider,
        {
            theme: {
                token: {
                    // 主色系 —— 墨绿
                    colorPrimary: '#4A6B57',
                    colorPrimaryHover: '#5C7E68',
                    colorPrimaryActive: '#3A5444',
                    colorPrimaryBg: 'rgba(74, 107, 87, 0.08)',
                    colorPrimaryBgHover: 'rgba(74, 107, 87, 0.12)',
                    colorPrimaryBorder: 'rgba(74, 107, 87, 0.5)',
                    colorPrimaryBorderHover: '#5C7E68',
                    colorPrimaryText: '#4A6B57',
                    colorPrimaryTextHover: '#3A5444',
                    colorPrimaryTextActive: '#2D4035',

                    // 功能色 —— 全部低饱和，与主色协调
                    colorSuccess: '#5C7E68',
                    colorWarning: '#C9A961',
                    colorError: '#B85450',
                    colorInfo: '#7A8A8C',

                    // 中性色 —— 极简留白
                    colorBgLayout: '#F5F1E8',
                    colorBgContainer: '#FFFFFF',
                    colorBgElevated: '#FFFFFF',
                    colorBgSpotlight: '#FAF8F2',
                    colorBorder: 'rgba(107, 107, 102, 0.18)',
                    colorBorderSecondary: 'rgba(107, 107, 102, 0.12)',
                    colorSplit: 'rgba(107, 107, 102, 0.10)',

                    // 文字
                    colorText: '#2D2D2A',
                    colorTextSecondary: '#6B6B66',
                    colorTextTertiary: '#9A9A92',
                    colorTextQuaternary: '#B8B8B0',
                    colorTextDescription: '#6B6B66',

                    // 形状 —— 略小、干净
                    borderRadius: 6,
                    borderRadiusLG: 10,
                    borderRadiusSM: 4,

                    // 字体与行高
                    fontFamily:
                        "'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
                    fontSize: 14,
                    fontSizeHeading1: 38,
                    fontSizeHeading2: 28,
                    fontSizeHeading3: 22,
                    lineHeight: 1.7,
                    lineHeightHeading1: 1.25,

                    // 控件
                    controlHeight: 44,
                    controlHeightLG: 52,
                    controlHeightSM: 32,
                    paddingContentHorizontal: 20,
                    paddingContentVertical: 16,

                    // 阴影 —— 极轻
                    boxShadow: '0 1px 2px rgba(45, 45, 42, 0.04)',
                    boxShadowSecondary: '0 2px 8px rgba(45, 45, 42, 0.05)',
                    boxShadowTertiary: '0 4px 16px rgba(45, 45, 42, 0.06)',

                    // 动效曲线
                    motionDurationFast: '0.15s',
                    motionDurationMid: '0.25s',
                    motionDurationSlow: '0.4s',
                    motionEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
                    motionEaseOut: 'cubic-bezier(0.0, 0, 0.2, 1)',
                    // 此处原有一行 motionEaseIn: 'cubic-bezier(0.4, 0, 1, 1)'。
                    // antd 5.29 的 AliasToken 并无该 token（只有 motionEaseInBack /
                    // motionEaseInQuint / motionEaseInOut 等变体），故这行从未生效，
                    // 属死配置；其曲线语义（ease-in）与上面的 motionEaseInOut 不同，
                    // 直接改名会覆盖掉正确的 ease-in-out 值，因此删除而非改名。
                },
                components: {
                    Tabs: {
                        itemColor: '#9A9A92',
                        itemActiveColor: '#4A6B57',
                        itemHoverColor: '#2D2D2A',
                        itemSelectedColor: '#4A6B57',
                        inkBarColor: '#4A6B57',
                        titleFontSize: 14,
                    },
                    Button: {
                        primaryShadow: 'none',
                        defaultBorderColor: 'rgba(107, 107, 102, 0.2)',
                        defaultColor: '#2D2D2A',
                    },
                    Input: {
                        activeBorderColor: '#4A6B57',
                        hoverBorderColor: '#5C7E68',
                        activeShadow: '0 0 0 3px rgba(74, 107, 87, 0.10)',
                    },
                    Checkbox: {
                        colorPrimary: '#4A6B57',
                        colorPrimaryHover: '#5C7E68',
                    },
                    Alert: {
                        colorErrorBg: 'rgba(184, 84, 80, 0.06)',
                        colorErrorBorder: 'rgba(184, 84, 80, 0.2)',
                    },
                    Form: {
                        labelColor: '#2D2D2A',
                    },
                },
            },
        },
        React.createElement(MarketplaceMotion),
        container,
    );
}
