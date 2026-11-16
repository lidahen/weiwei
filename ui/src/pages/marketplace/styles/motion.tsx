import { createGlobalStyle } from 'antd-style';

/**
 * marketplace 全局微交互动画（挂载一次即可全站生效，见 app.tsx rootContainer）。
 *
 * 提供的原子类：
 * - .twt-fade-up    内容进场：淡入 + 上移 14px（配合内联 animation-delay 做 stagger）
 * - .twt-pop        强调弹跳：点赞/收藏等轻量状态变化的回弹
 * - .twt-shimmer    骨架屏流光（SkeletonCards / 图片占位使用）
 * - .twt-press      可点击元素的按压缩放反馈（active 状态）
 * - .twt-page-in    页面级进场（自动应用于 PageContainer 内容区，路由切换整页淡入上移）
 * - .twt-msg-in     IM 气泡入场：淡入 + 轻微上浮缩放（key 稳定时重渲染不重放）
 * - .twt-caret      AI 流式输出打字机光标（闪烁竖线，追加在流式文本尾部）
 * - .twt-dots       AI 思考中三点跳动（<span className="twt-dots"><i/><i/><i/></span>）
 * - .twt-breathe    悬浮球呼吸光圈（伪元素 scale + opacity，transform-only）
 * - .twt-fade-img / .twt-loaded   图片加载淡入（onLoad 后加 .twt-loaded）
 * prefers-reduced-motion 用户自动禁用全部动画。
 */
export const MarketplaceMotion = createGlobalStyle`
  @keyframes twt-fade-up {
    from { opacity: 0; transform: translateY(14px); }
    to { opacity: 1; transform: translateY(0); }
  }
  @keyframes twt-pop {
    0% { transform: scale(1); }
    40% { transform: scale(1.3); }
    100% { transform: scale(1); }
  }
  @keyframes twt-shimmer {
    0% { background-position: -400px 0; }
    100% { background-position: 400px 0; }
  }
  @keyframes twt-page-in {
    from { opacity: 0; transform: translateY(8px); }
    to { opacity: 1; transform: translateY(0); }
  }
  @keyframes twt-msg-in {
    from { opacity: 0; transform: translateY(8px) scale(0.98); }
    to { opacity: 1; transform: translateY(0) scale(1); }
  }
  @keyframes twt-caret {
    0%, 45% { opacity: 1; }
    50%, 95% { opacity: 0; }
  }
  @keyframes twt-dot-bounce {
    0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
    30% { transform: translateY(-4px); opacity: 1; }
  }
  @keyframes twt-breathe {
    0%, 100% { transform: scale(1); opacity: 0.35; }
    50% { transform: scale(1.35); opacity: 0; }
  }
  .twt-fade-up {
    animation: twt-fade-up 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
  }
  .twt-pop {
    animation: twt-pop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
  .twt-shimmer {
    background: linear-gradient(90deg, #f2f1ec 25%, #faf9f4 37%, #f2f1ec 63%);
    background-size: 400px 100%;
    animation: twt-shimmer 1.3s ease-in-out infinite;
  }
  .twt-press {
    transition: transform 0.12s ease;
    &:active { transform: scale(0.96); }
  }
  /* 页面级进场：所有 PageContainer 内容区随路由切换整体淡入（零 JS，key 随路由重挂自然重放） */
  .ant-pro-page-container-children-container {
    animation: twt-page-in 0.3s cubic-bezier(0.22, 1, 0.36, 1) both;
  }
  .twt-msg-in {
    animation: twt-msg-in 0.25s cubic-bezier(0.22, 1, 0.36, 1) both;
  }
  .twt-caret {
    display: inline-block;
    width: 2px;
    height: 1em;
    margin-left: 2px;
    vertical-align: -0.15em;
    background: #FF6B35;
    border-radius: 1px;
    animation: twt-caret 0.9s step-end infinite;
  }
  .twt-dots {
    display: inline-flex;
    align-items: flex-end;
    gap: 3px;
    height: 1em;
    > i {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: #FF6B35;
      animation: twt-dot-bounce 1.1s ease-in-out infinite;
      &:nth-child(2) { animation-delay: 0.15s; }
      &:nth-child(3) { animation-delay: 0.3s; }
    }
  }
  .twt-breathe {
    position: relative;
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: 50%;
      border: 2px solid #FF6B35;
      animation: twt-breathe 2.4s cubic-bezier(0.4, 0, 0.2, 1) infinite;
      pointer-events: none;
    }
  }
  .twt-fade-img {
    opacity: 0;
    transition: opacity 0.4s ease;
    &.twt-loaded { opacity: 1; }
  }
  @media (prefers-reduced-motion: reduce) {
    .twt-fade-up, .twt-pop, .twt-shimmer, .twt-msg-in, .twt-caret {
      animation: none !important;
    }
    .twt-press { transition: none; &:active { transform: none; } }
    .ant-pro-page-container-children-container { animation: none; }
    .twt-fade-img { opacity: 1; transition: none; }
    .twt-dots > i { animation: none; opacity: 0.6; }
    /* 呼吸光圈是纯装饰，reduced-motion 下直接隐藏，避免残留静态圆环 */
    .twt-breathe::after { display: none; }
  }
`;

export default MarketplaceMotion;
