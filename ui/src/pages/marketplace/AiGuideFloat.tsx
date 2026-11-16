import ProductCard from '@/components/marketplace/ProductCard';
import TypingDots from '@/components/marketplace/TypingDots';
import {
    aiGuideChatStream,
    ChatMsg,
    ProductCardData,
    StreamEvent,
} from '@/pages/AI/ShoppingGuide/service';
import { CloseOutlined, PlusOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Col, Drawer, Input, Row } from 'antd';
import React, { useEffect, useRef, useState } from 'react';

interface UiMessage {
    role: 'user' | 'assistant';
    content: string;
    products?: ProductCardData[];
    status?: string;
}

const WELCOME: UiMessage = {
    role: 'assistant',
    content:
        '你好，我是邻里二手的 AI 导购～ 想找什么闲置好物？例如「500 元以内的降噪耳机」，我来帮你挑。',
};

const SESSION_KEY = 'twt_market_ai_guide_session';
const CHAT_KEY = 'twt_market_ai_guide_chat';

/** 持久化当前对话，刷新页面可恢复 */
function persistChat(messages: UiMessage[]) {
    try {
        localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
    } catch {
        /* ignore */
    }
}

function loadChat(): UiMessage[] {
    try {
        const raw = localStorage.getItem(CHAT_KEY);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed) && parsed.length > 0) {
                // 丢弃持久化的瞬时状态（如"正在查询商品"），避免刷新后残留
                return parsed.map((msg: UiMessage) => {
                    const rest = { ...msg };
                    delete rest.status;
                    return rest;
                });
            }
        }
    } catch {
        /* ignore */
    }
    return [WELCOME];
}

function getSessionId(): string {
    let sid = localStorage.getItem(SESSION_KEY);
    if (!sid) {
        sid = crypto.randomUUID?.() || `mg-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        localStorage.setItem(SESSION_KEY, sid);
    }
    return sid;
}

/**
 * C 端 AI 导购悬浮入口：右下角悬浮按钮 + 抽屉聊天。
 * 流式协议与导购上游一致（text 增量 / products / confirm / error / done），
 * 商品卡片可点进详情页；登录用户可见（接口本身需登录）。
 */
const AiGuideFloat: React.FC = () => {
    const [open, setOpen] = useState(false);
    const [messages, setMessages] = useState<UiMessage[]>(loadChat);
    const [text, setText] = useState('');
    const [loading, setLoading] = useState(false);
    const listRef = useRef<HTMLDivElement>(null);
    const sessionIdRef = useRef<string>(getSessionId());
    const pendingConfirmRef = useRef(false);

    useEffect(() => {
        persistChat(messages);
    }, [messages]);

    useEffect(() => {
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
    }, [messages, loading, open]);

    const updateLast = (updater: (m: UiMessage) => UiMessage) =>
        setMessages((prev) => {
            const copy = [...prev];
            copy[copy.length - 1] = updater(copy[copy.length - 1]);
            return copy;
        });

    const newChat = () => {
        setMessages([WELCOME]);
        sessionIdRef.current =
            crypto.randomUUID?.() || `mg-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        localStorage.setItem(SESSION_KEY, sessionIdRef.current);
        pendingConfirmRef.current = false;
    };

    const send = async () => {
        const content = text.trim();
        if (!content || loading) return;

        const nextMessages: UiMessage[] = [...messages, { role: 'user', content }];
        setMessages([...nextMessages, { role: 'assistant', content: '', products: [] }]);
        setText('');
        setLoading(true);

        // 上送完整对话历史（仅 role/content），多轮上下文
        const payload: ChatMsg[] = nextMessages.map((m) => ({ role: m.role, content: m.content }));

        const handleEvent = (event: StreamEvent) => {
            if (event.type === 'text') {
                updateLast((m) => ({ ...m, content: (m.content || '') + event.content }));
            } else if (event.type === 'status') {
                // 工具执行状态提示（"正在查询商品"等），回答文本到达后自然被覆盖
                updateLast((m) => ({ ...m, status: event.content }));
            } else if (event.type === 'products') {
                updateLast((m) => ({ ...m, products: event.products || [] }));
            } else if (event.type === 'confirm') {
                // 工具请求确认（下单/改地址等）：展示确认文案，用户回复后携带 resume 恢复
                pendingConfirmRef.current = true;
                updateLast((m) => ({ ...m, content: event.message || '请确认以上操作' }));
            } else if (event.type === 'error') {
                updateLast((m) => ({ ...m, content: (m.content || '') + event.message }));
            }
        };

        const sessionId = sessionIdRef.current;
        const resume = pendingConfirmRef.current ? content : undefined;
        if (resume) pendingConfirmRef.current = false;

        try {
            await aiGuideChatStream(
                payload,
                handleEvent,
                () => setLoading(false),
                () => setLoading(false),
                sessionId,
                resume,
            );
        } catch (e) {
            updateLast((m) => ({ ...m, content: m.content || 'AI 导购暂时不可用，请稍后再试。' }));
            setLoading(false);
        }
    };

    return (
        <>
            <div
                onClick={() => setOpen(true)}
                className="twt-breathe"
                style={{
                    position: 'fixed',
                    right: 'min(24px, calc(100vw - 64px))',
                    bottom: 'calc(88px + env(safe-area-inset-bottom, 0px))',
                    zIndex: 900,
                    width: 52,
                    height: 52,
                    borderRadius: '50%',
                    background: '#FF6B35',
                    color: '#fff',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    boxShadow: '0 4px 16px rgba(255, 107, 53, 0.45)',
                    userSelect: 'none',
                    transition: 'transform 0.2s ease',
                }}
                title="AI 导购"
                onMouseEnter={(e) => {
                    (e.currentTarget as HTMLDivElement).style.transform = 'scale(1.08)';
                }}
                onMouseLeave={(e) => {
                    (e.currentTarget as HTMLDivElement).style.transform = '';
                }}
            >
                <RobotOutlined style={{ fontSize: 20 }} />
                <span style={{ fontSize: 10, marginTop: 2 }}>AI导购</span>
            </div>
            <Drawer
                title={
                    <div
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                        }}
                    >
                        <span>🛒 AI 导购</span>
                        <Button size="small" icon={<PlusOutlined />} onClick={newChat}>
                            新对话
                        </Button>
                    </div>
                }
                placement="right"
                width="min(420px, 92vw)"
                open={open}
                onClose={() => setOpen(false)}
                closeIcon={<CloseOutlined />}
                styles={{
                    body: { padding: 0, display: 'flex', flexDirection: 'column', height: '100%' },
                }}
            >
                <div
                    ref={listRef}
                    style={{ flex: 1, overflowY: 'auto', padding: 16, background: '#FFF8F1' }}
                >
                    {messages.map((m, idx) => {
                        const isUser = m.role === 'user';
                        const isStreaming = !isUser && loading && idx === messages.length - 1;
                        return (
                            <div key={idx} className="twt-msg-in" style={{ marginBottom: 16 }}>
                                <div
                                    style={{
                                        display: 'flex',
                                        justifyContent: isUser ? 'flex-end' : 'flex-start',
                                    }}
                                >
                                    <div
                                        style={{
                                            maxWidth: '80%',
                                            padding: '8px 12px',
                                            borderRadius: 12,
                                            background: isUser ? '#FF6B35' : '#fff',
                                            color: isUser ? '#fff' : '#333',
                                            whiteSpace: 'pre-wrap',
                                            boxShadow: isUser
                                                ? 'none'
                                                : '0 1px 4px rgba(0,0,0,0.06)',
                                        }}
                                    >
                                        {m.content}
                                        {/* 流式输出中：文本尾部打字机光标 */}
                                        {isStreaming && m.content && <span className="twt-caret" />}
                                        {/* 工具执行状态（"正在查询商品"等）：文案 + 三点跳动 */}
                                        {!isUser && !m.content && m.status && (
                                            <span
                                                style={{
                                                    color: '#bbb',
                                                    fontSize: 12,
                                                    display: 'inline-flex',
                                                    alignItems: 'center',
                                                    gap: 8,
                                                }}
                                            >
                                                {m.status}
                                                <TypingDots />
                                            </span>
                                        )}
                                    </div>
                                </div>
                                {!isUser && m.products && m.products.length > 0 && (
                                    <Row
                                        gutter={[8, 8]}
                                        style={{ marginTop: 12 }}
                                        className="twt-fade-up"
                                    >
                                        {m.products.map((p) => (
                                            <Col key={p.productId} span={12}>
                                                <ProductCard
                                                    image={p.cover}
                                                    title={p.title}
                                                    price={p.price}
                                                    originalPrice={p.originalPrice}
                                                    condition={p.condition}
                                                    sellerName={p.sellerNickName}
                                                    city={p.city}
                                                    onClick={() => {
                                                        setOpen(false);
                                                        history.push(
                                                            `/product/detail/${p.productId}`,
                                                        );
                                                    }}
                                                />
                                            </Col>
                                        ))}
                                    </Row>
                                )}
                            </div>
                        );
                    })}
                    {/* 等待首个 token：三点跳动；开始输出后由气泡内光标接管 */}
                    {loading &&
                        !messages[messages.length - 1]?.content &&
                        !messages[messages.length - 1]?.status && (
                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'flex-start',
                                    padding: '4px 0',
                                }}
                            >
                                <TypingDots />
                            </div>
                        )}
                </div>
                <div
                    style={{
                        padding: 12,
                        borderTop: '1px solid #f0f0f0',
                        display: 'flex',
                        gap: 8,
                        alignItems: 'center',
                        background: '#fff',
                    }}
                >
                    <Input
                        placeholder="想要什么闲置好物？"
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        onPressEnter={send}
                        disabled={loading}
                    />
                    <Button
                        type="primary"
                        icon={<SendOutlined />}
                        onClick={send}
                        loading={loading}
                        disabled={!text.trim()}
                        style={{ background: '#FF6B35' }}
                    />
                </div>
            </Drawer>
        </>
    );
};

export default AiGuideFloat;
