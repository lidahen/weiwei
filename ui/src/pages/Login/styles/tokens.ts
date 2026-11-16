/** 登录页专用 design tokens —— 不污染全局 ConfigProvider，仅 Login 子树引用 */

export const loginTokens = {
    color: {
        cream: '#F5F1E8',
        sand: '#E8DCC4',
        forest: '#4A6B57',
        forestHover: '#5C7E68',
        forestActive: '#3A5444',
        stone: '#6B6B66',
        stoneLight: '#9A9A92',
        ink: '#2D2D2A',
        white: '#FFFFFF',
        error: '#B85450',
    },
    space: {
        xs: 8,
        sm: 16,
        md: 24,
        lg: 32,
        xl: 48,
        xxl: 56,
    },
    radius: {
        sm: 4,
        md: 6,
        lg: 10,
    },
    shadow: {
        card: '0 1px 0 rgba(45,45,42,0.04), 0 24px 64px -32px rgba(45,45,42,0.10)',
        soft: '0 2px 8px rgba(45,45,42,0.05)',
    },
    font: {
        serif: "'Cormorant Garamond', 'Source Han Serif SC', 'Noto Serif SC', serif",
        sans: "'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif",
    },
    motion: {
        fast: 0.15,
        base: 0.25,
        slow: 0.4,
    },
} as const;

export type LoginTokens = typeof loginTokens;
