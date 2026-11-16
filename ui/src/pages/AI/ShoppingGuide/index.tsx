import ProductCard from '@/components/marketplace/ProductCard';
import TypingDots from '@/components/marketplace/TypingDots';
import { system } from '@/utils/twelvet';
import { PauseCircleOutlined, PlusOutlined, SendOutlined, SoundOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Card, Col, Input, message, Row, Spin } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { aiGuideChatStream, ChatMsg, ProductCardData, StreamEvent, ttsSpeak } from './service';

interface UiMessage {
    role: 'user' | 'assistant';
    content: string;
    products?: ProductCardData[];
    status?: string;
}

const WELCOME: UiMessage = {
    role: 'assistant',
    content:
        '你好,我是 AI 导购~ 告诉我你想要什么,例如「想要 500 元以内、95 新的机械键盘」,我来帮你挑。',
};

const SESSION_KEY = 'twt_ai_guide_session';
const CHAT_KEY = 'twt_ai_guide_chat';

/** 持久化当前对话到 localStorage，刷新页面可恢复 */
function persistChat(messages: UiMessage[]) {
    try {
        localStorage.setItem(CHAT_KEY, JSON.stringify(messages));
    } catch {
        /* ignore */
    }
}

/** 读取持久化的对话，无则返回欢迎语 */
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

/** 获取/生成当前会话 ID（跨轮复用，方案 3 多轮停留的 thread_id） */
function getSessionId(): string {
    let sid = localStorage.getItem(SESSION_KEY);
    if (!sid) {
        sid = crypto.randomUUID?.() || `sg-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        localStorage.setItem(SESSION_KEY, sid);
    }
    return sid;
}

const ShoppingGuide: React.FC = () => {
    const [messages, setMessages] = useState<UiMessage[]>(loadChat);
    const [text, setText] = useState('');
    const [loading, setLoading] = useState(false);
    const listRef = useRef<HTMLDivElement>(null);
    const sessionIdRef = useRef<string>(getSessionId());
    const pendingConfirmRef = useRef(false);

    // TTS 朗读状态：正在加载/播放的消息下标，audioRef 用于停止上一条播放
    const [speakingIdx, setSpeakingIdx] = useState<number | null>(null);
    const [ttsLoadingIdx, setTtsLoadingIdx] = useState<number | null>(null);
    const audioRef = useRef<HTMLAudioElement | null>(null);

    useEffect(
        () => () => {
            // 卸载时停止播放并释放音频资源
            audioRef.current?.pause();
            if (audioRef.current) {
                audioRef.current.src = '';
                audioRef.current = null;
            }
        },
        [],
    );

    /** 朗读/停止某条 AI 回复 */
    const speak = async (content: string, idx: number) => {
        // 再次点击正在播放的消息 → 停止
        if (speakingIdx === idx) {
            audioRef.current?.pause();
            audioRef.current = null;
            setSpeakingIdx(null);
            return;
        }
        audioRef.current?.pause();
        audioRef.current = null;
        setSpeakingIdx(null);

        if (!content?.trim()) return;
        try {
            setTtsLoadingIdx(idx);
            const blob = await ttsSpeak(content);
            if (blob.type.includes('audio')) {
                const audio = new Audio(URL.createObjectURL(blob));
                audio.onended = () => {
                    setSpeakingIdx(null);
                    URL.revokeObjectURL(audio.src);
                };
                audioRef.current = audio;
                setSpeakingIdx(idx);
                await audio.play();
            } else {
                // 后端返回 JSON 错误（TTS 未配置/上游失败）
                const res = JSON.parse(await blob.text());
                message.error(res.msg || '语音合成失败');
            }
        } catch (e) {
            system.error(e);
        } finally {
            setTtsLoadingIdx(null);
        }
    };

    // 对话变更后持久化，刷新页面可恢复
    useEffect(() => {
        persistChat(messages);
    }, [messages]);

    useEffect(() => {
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
    }, [messages, loading]);

    // 更新当前最后一条(assistant 占位)消息
    const updateLast = (updater: (m: UiMessage) => UiMessage) =>
        setMessages((prev) => {
            const copy = [...prev];
            copy[copy.length - 1] = updater(copy[copy.length - 1]);
            return copy;
        });

    const newChat = () => {
        setMessages([WELCOME]);
        sessionIdRef.current =
            crypto.randomUUID?.() || `sg-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        localStorage.setItem(SESSION_KEY, sessionIdRef.current);
        pendingConfirmRef.current = false;
    };

    const send = async () => {
        const content = text.trim();
        if (!content || loading) return;

        const nextMessages: UiMessage[] = [...messages, { role: 'user', content }];
        // 追加用户消息 + 一个空的 assistant 占位(流式逐步填充)
        setMessages([...nextMessages, { role: 'assistant', content: '', products: [] }]);
        setText('');
        setLoading(true);

        // 上送完整对话历史(仅 role/content),实现多轮上下文;后端无状态
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
                // 工具请求确认(下单/改地址等):展示确认文案,提示用户确认或修改
                pendingConfirmRef.current = true;
                updateLast((m) => ({ ...m, content: event.message || '请确认以上操作' }));
            } else if (event.type === 'error') {
                updateLast((m) => ({ ...m, content: (m.content || '') + event.message }));
            }
        };

        const finish = () => setLoading(false);

        try {
            const sessionId = sessionIdRef.current;
            const resume = pendingConfirmRef.current ? content : undefined;
            if (resume) pendingConfirmRef.current = false;
            await aiGuideChatStream(payload, handleEvent, finish, finish, sessionId, resume);
        } catch (e) {
            updateLast((m) => ({
                ...m,
                content: m.content || 'AI 导购暂时不可用,请稍后再试。',
            }));
            setLoading(false);
        }
    };

    // 是否支持 100dvh：移动端地址栏收展时 100vh 会抖动，故优先用 dvh。
    // 注意：optional chaining 只保护「方法不存在」，不保护「CSS 这个全局未定义」——
    // 原先写 CSS.supports?.(...) 在 jsdom 下会抛 ReferenceError（jest 因此长期红），
    // 在其它缺少 CSS 全局的运行环境里同样会崩。故显式判断全局与方法是否都存在。
    const supportsDvh =
        typeof CSS !== 'undefined' && typeof CSS.supports === 'function'
            ? CSS.supports('height', '100dvh')
            : false;

    return (
        <PageContainer header={{ title: 'AI 导购', breadcrumb: {} }}>
            <Card
                bodyStyle={{
                    padding: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    // 100dvh 优先：移动端地址栏收展时 100vh 会抖动；手机端头部更矮，扣除量更小
                    height: 'calc(100vh - 220px)',
                    ...(supportsDvh ? { height: 'calc(100dvh - 220px)' } : {}),
                    ...(window.innerWidth <= 767
                        ? {
                              height: 'calc(100vh - 140px)',
                              ...(supportsDvh ? { height: 'calc(100dvh - 140px)' } : {}),
                          }
                        : {}),
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
                                            maxWidth: '72%',
                                            padding: '8px 12px',
                                            borderRadius: 12,
                                            background: isUser ? '#FF6B35' : '#fff',
                                            color: isUser ? '#fff' : '#333',
                                            whiteSpace: 'pre-wrap',
                                        }}
                                    >
                                        {m.content}
                                        {/* 流式输出中：文本尾部打字机光标 */}
                                        {isStreaming && m.content && <span className="twt-caret" />}
                                        {/* 工具执行状态：文案 + 三点跳动 */}
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
                                {!isUser && m.content && !loading && (
                                    <Button
                                        type="text"
                                        size="small"
                                        icon={
                                            ttsLoadingIdx === idx ? (
                                                <Spin size="small" />
                                            ) : speakingIdx === idx ? (
                                                <PauseCircleOutlined style={{ color: '#FF6B35' }} />
                                            ) : (
                                                <SoundOutlined />
                                            )
                                        }
                                        onClick={() => speak(m.content, idx)}
                                        style={{ marginTop: 4, color: '#999' }}
                                        title={speakingIdx === idx ? '停止朗读' : '朗读回复'}
                                    />
                                )}
                                {!isUser && m.products && m.products.length > 0 && (
                                    <Row
                                        gutter={[12, 12]}
                                        style={{ marginTop: 12 }}
                                        className="twt-fade-up"
                                    >
                                        {m.products.map((p) => (
                                            <Col key={p.productId} xs={12} sm={8} md={6}>
                                                <ProductCard
                                                    image={p.cover}
                                                    title={p.title}
                                                    price={p.price}
                                                    originalPrice={p.originalPrice}
                                                    condition={p.condition}
                                                    sellerName={p.sellerNickName}
                                                    city={p.city}
                                                    onClick={() =>
                                                        history.push(
                                                            `/product/detail/${p.productId}`,
                                                        )
                                                    }
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
                    }}
                >
                    <Button
                        icon={<PlusOutlined />}
                        onClick={() => newChat()}
                        style={{ width: 40 }}
                        title={'开启新对话'}
                    />
                    <Input
                        placeholder="描述你想要的商品,例如:500元内95新的机械键盘"
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
                    >
                        发送
                    </Button>
                </div>
            </Card>
        </PageContainer>
    );
};

export default ShoppingGuide;
