import React from 'react';

export interface SeoHeadingProps {
    children: React.ReactNode;
}

/**
 * 内容区标题的 h1 载体。
 *
 * D10 要求「内容区恰好一个 h1」，但 D10 同时要求不为 SEO 改布局：这些页面的标题
 * 由 PageContainer 渲染在 `<span>` 里，若改成可见 h1 会改变既有版式。这里用
 * 视觉隐藏的标准做法（等价 sr-only）保留语义，屏幕阅读器与爬虫都能读到，
 * 而页面外观不变。文案必须与该页可见标题一致，避免隐藏文本被判为作弊。
 */
export default function SeoHeading({ children }: SeoHeadingProps) {
    return (
        <h1
            style={{
                position: 'absolute',
                width: 1,
                height: 1,
                padding: 0,
                margin: -1,
                overflow: 'hidden',
                clip: 'rect(0 0 0 0)',
                whiteSpace: 'nowrap',
                border: 0,
            }}
        >
            {children}
        </h1>
    );
}
