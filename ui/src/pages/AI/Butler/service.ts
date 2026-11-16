/**
 * AI 社区管家页面：与 AI 导购共用同一条 SSE 对话链路（/product/ai-guide/chat/stream，
 * Python agent 已内置 community 域路由），此处仅做管家品牌与示例引导。
 */
export {
    aiGuideChatStream,
    ttsSpeak,
} from '@/pages/AI/ShoppingGuide/service';
export type { ChatMsg, ProductCardData, StreamEvent } from '@/pages/AI/ShoppingGuide/service';
