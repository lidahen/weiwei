import { eventSource } from '@/utils/twelvet';
import { request } from '@umijs/max';

export interface ChatMsg {
    role: 'user' | 'assistant';
    content: string;
}

export interface ProductCardData {
    productId: number;
    title: string;
    cover: string;
    price: number;
    originalPrice?: number;
    condition?: string;
    city?: string;
    sellerNickName?: string;
}

/**
 * 流式事件:文案增量 / 工具执行状态 / 商品卡片 / 确认请求 / 错误
 */
export type StreamEvent =
    | { type: 'text'; content: string }
    | { type: 'status'; content: string }
    | { type: 'products'; products: ProductCardData[] }
    | { type: 'confirm'; message: string; tool?: string }
    | { type: 'error'; message: string };

/**
 * AI 导购流式对话:上送完整对话历史,逐字接收文案,结束后接收商品卡片。
 * sessionId 非空时走方案 3 多轮停留(工具确认后携带同一 sessionId + resume 恢复)。
 * 返回 AbortController(Promise),可用于中止。
 */
export function aiGuideChatStream(
    messages: ChatMsg[],
    onEvent: (event: StreamEvent) => void,
    onDone: () => void,
    onError?: () => void,
    sessionId?: string,
    resume?: string,
) {
    const payload: Record<string, unknown> = { messages };
    if (sessionId) payload.sessionId = sessionId;
    if (resume) payload.resume = resume;
    return eventSource('/product/ai-guide/chat/stream', payload, onEvent, onDone, onError);
}

/**
 * TTS 语音合成:文本转语音,返回音频 Blob(audio/mpeg)。
 * 后端失败时返回 JSON 错误(application/json),调用方按 blob.type 区分。
 */
export async function ttsSpeak(text: string): Promise<Blob> {
    return request('/ai/chat/tts', {
        method: 'POST',
        data: { text: text.slice(0, 1000) },
        responseType: 'blob',
        skipErrorHandler: true,
    });
}
