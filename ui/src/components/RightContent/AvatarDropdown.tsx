import { outLogin } from '@/services/ant-design-pro/api';
import {
    LogoutOutlined,
    SettingOutlined,
} from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { Spin } from 'antd';
import { createStyles } from 'antd-style';
import React from 'react';
import HeaderDropdown from '../HeaderDropdown';
import TWT from '../../setting';

export type GlobalHeaderRightProps = {
    menu?: boolean;
    children?: React.ReactNode;
};

export const AvatarName = () => {
    const { initialState } = useModel('@@initialState');
    const { currentUser } = initialState || {};
    return (
        // 移动端收纳（复查报告 P1-3）：<768px 隐藏用户名文字，仅显示头像
        <span className="anticon tw-desktop-only">
            {currentUser?.user?.nickName || currentUser?.user?.username}
        </span>
    );
};

const useStyles = createStyles(({ token }) => {
    return {
        action: {
            display: 'flex',
            height: '48px',
            marginLeft: 'auto',
            overflow: 'hidden',
            alignItems: 'center',
            padding: '0 8px',
            cursor: 'pointer',
            borderRadius: token.borderRadius,
            '&:hover': {
                backgroundColor: token.colorBgTextHover,
            },
        },
    };
});

export const AvatarDropdown: React.FC<GlobalHeaderRightProps> = ({ children }) => {
    const { initialState, setInitialState } = useModel('@@initialState');
    const { styles } = useStyles();

    /**
     * 退出登录,跳回 /login
     */
    const loginOut = async () => {
        try {
            await outLogin();
        } catch (e) {
            /* 即使后端登出失败也清前端 */
        }
        // 清除当前用户状态（在 navigate 之前完成，避免触发 onPageChange 重复导航）
        setInitialState((s) => ({ ...s, currentUser: undefined }));
        // 只清除授权相关数据，保留 Umi 运行时所需的其他 localStorage 键
        localStorage.removeItem(TWT.accessToken);
        localStorage.removeItem(TWT.refreshToken);
        localStorage.removeItem(TWT.preAuthorize);
        history.replace({
            pathname: '/login',
        });
    };

    if (!initialState)
        return (
            <span className={styles.action}>
                <Spin size="small" />
            </span>
        );

    const { currentUser } = initialState;
    // 游客态（公开页未登录）：渲染「登录」入口，不能转圈 —— 公开页永远等不到 currentUser，
    // 一直转圈会被当成页面卡死，且游客没有任何进入登录页的入口。
    if (!currentUser || !currentUser?.user?.username) {
        return (
            <span
                className={styles.action}
                role="button"
                tabIndex={0}
                onClick={() => history.push('/login')}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') history.push('/login');
                }}
            >
                登录
            </span>
        );
    }

    const onMenuClick = (event: { key: string }) => {
        if (event.key === 'logout') {
            loginOut();
        } else if (event.key === 'my-settings') {
            history.push('/user/settings');
        }
    };

    const menuItems = [
        { key: 'my-settings', icon: <SettingOutlined />, label: '账户设置' },
        { type: 'divider' as const },
        { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
    ];

    return (
        <HeaderDropdown
            menu={{
                selectedKeys: [],
                onClick: onMenuClick,
                items: menuItems,
            }}
        >
            {children}
        </HeaderDropdown>
    );
};
