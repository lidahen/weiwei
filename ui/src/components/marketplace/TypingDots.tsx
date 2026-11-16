import React from 'react';

/**
 * AI 思考中三点跳动指示（配合 motion.tsx 的 .twt-dots 原子类）。
 * 用于 AI 导购流式响应的 status 阶段，替代灰色纯文本提示。
 */
const TypingDots: React.FC<{ style?: React.CSSProperties }> = ({ style }) => (
    <span className="twt-dots" style={style} aria-label="AI 思考中">
        <i />
        <i />
        <i />
    </span>
);

export default TypingDots;
