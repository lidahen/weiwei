import { eventSource } from '@/utils/twelvet';
import { aiGuideChatStream } from './service';

jest.mock('@/utils/twelvet', () => ({
    eventSource: jest.fn(),
}));

const mockEventSource = eventSource as unknown as jest.Mock;

describe('aiGuideChatStream', () => {
    beforeEach(() => {
        mockEventSource.mockReset();
    });

    it('以 SSE 调用 /product/ai-guide/chat/stream 并携带 messages 与回调', () => {
        const onEvent = jest.fn();
        const onDone = jest.fn();
        const onError = jest.fn();
        const messages = [{ role: 'user' as const, content: '想要机械键盘' }];

        aiGuideChatStream(messages, onEvent, onDone, onError);

        expect(mockEventSource).toHaveBeenCalledWith(
            '/product/ai-guide/chat/stream',
            { messages },
            onEvent,
            onDone,
            onError,
        );
    });
});
