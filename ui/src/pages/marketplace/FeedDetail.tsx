import { feedComment, feedDetail, feedLike } from '@/services/marketplace/api';
import TWT from '@/setting';
import {
    ArrowLeftOutlined,
    CommentOutlined,
    FlagOutlined,
    HeartFilled,
    HeartOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import ReportDialog from '@/components/marketplace/ReportDialog';
import Seo from '@/components/Seo';
import SeoHeading from '@/components/Seo/Heading';
import { requireLogin } from '@/utils/loginGuard';
import { buildArticleJsonLd, isFeedIndexable, resolveSiteUrl } from '@/utils/seo';
import { useModel, useRequest } from '@umijs/max';
import { Avatar, Button, Card, Col, Form, Image, Input, message, Row, Space, Spin } from 'antd';
import React, { useRef, useState } from 'react';
import { history, useParams } from 'umi';
import { formatTime, IMG_FALLBACK, staticUrl } from './utils';

const { TextArea } = Input;

const FeedDetail: React.FC = () => {
    const { feedId } = useParams<{ feedId: string }>();

    const {
        data,
        loading,
        run: fetchDetail,
        mutate,
    } = useRequest(() => feedDetail(feedId!), { ready: !!feedId });

    // 后端 JsonResult<Feed>：实体在 data 字段（未解包会导致整个详情页空白）
    const body: any = data;
    const feed = body?.data ?? body;

    /** 局部更新 feed（兼容 JsonResult 包裹/裸实体两种返回形态） */
    const mutateFeed = (nextFeed: any) => {
        if (!data) return;
        if (body?.data !== undefined) mutate({ ...body, data: nextFeed });
        else mutate(nextFeed);
    };
    const rawImages: string[] = (() => {
        if (!feed?.images) return [];
        try {
            const parsed = JSON.parse(feed.images);
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    })();
    const images = rawImages.map((img: string) => staticUrl(img) || '');
    const comments = (feed?.comments || []) as any[];

    // ===== 二级评论：平铺列表 → 树形（根评论 + 回复挂载，抖音模式） =====
    // 回复链上任意层的回复统一挂到根评论下；目标评论被删时按根评论降级展示
    const commentTree = (() => {
        const byId = new Map<number, any>();
        comments.forEach((c: any) => {
            byId.set(c.commentId, { ...c, replies: [] as any[] });
        });
        const roots: any[] = [];
        byId.forEach((node) => {
            if (!node.replyToId) {
                roots.push(node);
                return;
            }
            let cur = byId.get(node.replyToId);
            let guard = 0; // 防御脏数据成环
            while (cur?.replyToId && guard < 10) {
                cur = byId.get(cur.replyToId);
                guard += 1;
            }
            if (cur) cur.replies.push(node);
            else roots.push(node);
        });
        return roots;
    })();

    const [replyToId, setReplyToId] = useState<number | undefined>();
    const [replyToUser, setReplyToUser] = useState<string>('');
    const [likePop, setLikePop] = useState(false);
    const [reportOpen, setReportOpen] = useState(false);
    const commentInputRef = useRef<any>(null);
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId) || undefined;

    const handleLike = async () => {
        if (!requireLogin()) return;
        if (!feed) return;
        // 乐观更新：本地先切换，避免整页重拉导致闪屏
        mutateFeed({
            ...feed,
            liked: !feed.liked,
            likeCount: (feed.likeCount || 0) + (feed.liked ? -1 : 1),
        });
        setLikePop(true);
        window.setTimeout(() => setLikePop(false), 400);
        try {
            await feedLike(Number(feedId));
            fetchDetail(); // 静默同步服务端计数
        } catch {
            message.error('操作失败');
            fetchDetail(); // 失败回滚为服务端状态
        }
    };

    const handleComment = async (values: any) => {
        if (!requireLogin()) return;
        if (!values.content?.trim()) return;
        try {
            await feedComment({
                feedId: Number(feedId),
                replyToId: replyToId,
                content: values.content.trim(),
            });
            message.success('评论成功');
            fetchDetail();
            setReplyToId(undefined);
            setReplyToUser('');
        } catch {
            message.error('评论失败');
        }
    };

    /** 点「回复」：带入目标评论上下文并聚焦输入框 */
    const handleReply = (c: any) => {
        setReplyToId(c.commentId);
        setReplyToUser(c.userNickName);
        window.setTimeout(() => commentInputRef.current?.focus({ cursor: 'end' }), 50);
    };

    // loading 仅在首次无数据时显示整页 Spin；点赞/评论后的局部刷新不再整页重载（避免闪屏）
    if (loading && !feed) {
        return (
            <PageContainer
                header={{ title: '动态详情', ghost: true, onBack: () => history.back() }}
            >
                <div style={{ textAlign: 'center', padding: 60 }}>
                    <Spin />
                </div>
            </PageContainer>
        );
    }

    if (!feed) {
        return (
            <PageContainer
                header={{ title: '动态详情', ghost: true, onBack: () => history.back() }}
            >
                <div style={{ textAlign: 'center', padding: 60 }}>
                    <Button onClick={() => history.back()} icon={<ArrowLeftOutlined />}>
                        返回
                    </Button>
                    <p>动态不存在</p>
                </div>
            </PageContainer>
        );
    }

    return (
        <PageContainer
            header={{
                title: '动态详情',
                ghost: true,
                onBack: () => history.back(),
            }}
        >
            <Seo
                title={(feed?.content || '动态详情').slice(0, 20)}
                description={(feed?.content || '').slice(0, 155)}
                canonicalPath={`/feed/detail/${feedId}`}
                noindex={!isFeedIndexable(feed)}
                jsonLd={buildArticleJsonLd(
                    feed,
                    resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin),
                )}
            />
            <SeoHeading>{(feed?.content || '动态详情').slice(0, 20)}</SeoHeading>
            <div style={{ maxWidth: 800, margin: '0 auto' }}>
                <Card>
                    <Card.Meta
                        avatar={
                            <Avatar
                                src={
                                    feed.userAvatar ? `${TWT.static}${feed.userAvatar}` : undefined
                                }
                                size="large"
                            />
                        }
                        title={feed.userNickName}
                        description={formatTime(feed.publishTime || feed.createTime)}
                    />

                    {feed.content && (
                        <p
                            style={{
                                whiteSpace: 'pre-wrap',
                                marginTop: 16,
                                fontSize: 16,
                                lineHeight: 1.6,
                            }}
                        >
                            {feed.content}
                        </p>
                    )}

                    {images.length > 0 && (
                        <Row gutter={[8, 8]} style={{ marginTop: 16 }}>
                            {images.map((img: string, idx: number) => (
                                <Col
                                    key={idx}
                                    xs={images.length === 1 ? 24 : 12}
                                    sm={images.length === 1 ? 20 : images.length === 2 ? 12 : 8}
                                >
                                    <Image
                                        src={img}
                                        fallback={IMG_FALLBACK}
                                        placeholder={
                                            <div
                                                className="twt-shimmer"
                                                style={{
                                                    width: '100%',
                                                    height: '100%',
                                                    minHeight: 160,
                                                    borderRadius: 8,
                                                }}
                                            />
                                        }
                                        style={{
                                            width: '100%',
                                            maxHeight: 300,
                                            objectFit: 'cover',
                                            borderRadius: 8,
                                        }}
                                        preview
                                    />
                                </Col>
                            ))}
                        </Row>
                    )}

                    {feed.relatedProductId && (
                        <Card
                            hoverable
                            style={{ marginTop: 16 }}
                            onClick={() => history.push(`/product/detail/${feed.relatedProductId}`)}
                        >
                            <span>关联商品: {feed.relatedProductTitle || '未知'}</span>
                            {feed.relatedProductPrice && (
                                <span style={{ marginLeft: 12, color: '#ff4d4f', fontWeight: 500 }}>
                                    ¥{feed.relatedProductPrice}
                                </span>
                            )}
                        </Card>
                    )}

                    <div
                        style={{
                            display: 'flex',
                            gap: 24,
                            marginTop: 16,
                            padding: '12px 0',
                            borderTop: '1px solid #f0f0f0',
                        }}
                    >
                        <span onClick={handleLike} style={{ cursor: 'pointer', fontSize: 14 }}>
                            {feed.liked ? (
                                <HeartFilled
                                    className={likePop ? 'twt-pop' : ''}
                                    style={{ color: '#ff4d4f', fontSize: 16 }}
                                />
                            ) : (
                                <HeartOutlined style={{ fontSize: 16 }} />
                            )}{' '}
                            点赞 {feed.likeCount || 0}
                        </span>
                        <span
                            onClick={() => commentInputRef.current?.focus({ cursor: 'end' })}
                            style={{ cursor: 'pointer', fontSize: 14 }}
                        >
                            <CommentOutlined /> 评论 {feed.commentCount || 0}
                        </span>
                        {/* 举报入口：不举报自己发的动态（后端同样会拒） */}
                        {(!myId || feed.userId !== myId) && (
                            <span
                                onClick={() => {
                                    if (!requireLogin()) return;
                                    setReportOpen(true);
                                }}
                                style={{ cursor: 'pointer', fontSize: 14, marginLeft: 'auto' }}
                            >
                                <FlagOutlined /> 举报
                            </span>
                        )}
                    </div>

                    <div style={{ marginTop: 24 }}>
                        <h4 style={{ marginBottom: 16 }}>评论 ({comments.length})</h4>
                        {commentTree.length === 0 && <p style={{ color: '#999' }}>暂无评论</p>}
                        {commentTree.map((c: any) => (
                            <div key={c.commentId} style={{ marginBottom: 20 }}>
                                <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                                    <Avatar
                                        src={
                                            c.userAvatar
                                                ? `${TWT.static}${c.userAvatar}`
                                                : undefined
                                        }
                                        size={36}
                                    />
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div
                                            style={{
                                                fontSize: 13,
                                                color: '#8A9AA5',
                                                marginBottom: 2,
                                            }}
                                        >
                                            {c.userNickName}
                                        </div>
                                        <div
                                            style={{
                                                fontSize: 14,
                                                lineHeight: 1.5,
                                                whiteSpace: 'pre-wrap',
                                                wordBreak: 'break-word',
                                            }}
                                        >
                                            {c.content}
                                        </div>
                                        <div
                                            style={{
                                                fontSize: 12,
                                                color: '#999',
                                                marginTop: 4,
                                                display: 'flex',
                                                gap: 16,
                                            }}
                                        >
                                            <span>{formatTime(c.createTime)}</span>
                                            <span
                                                onClick={() => handleReply(c)}
                                                style={{ cursor: 'pointer', color: '#666' }}
                                            >
                                                回复
                                            </span>
                                        </div>

                                        {/* 二级回复：挂在根评论下，抖音式平铺 */}
                                        {c.replies.length > 0 && (
                                            <div
                                                style={{
                                                    marginTop: 8,
                                                    background: '#F7F8FA',
                                                    borderRadius: 8,
                                                    padding: '4px 12px',
                                                }}
                                            >
                                                {c.replies.map((r: any, idx: number) => (
                                                    <div
                                                        key={r.commentId}
                                                        style={{
                                                            padding: '8px 0',
                                                            ...(idx > 0
                                                                ? { borderTop: '1px solid #EFEFEF' }
                                                                : {}),
                                                        }}
                                                    >
                                                        <div
                                                            style={{
                                                                fontSize: 13,
                                                                lineHeight: 1.6,
                                                                wordBreak: 'break-word',
                                                            }}
                                                        >
                                                            <span style={{ color: '#8A9AA5' }}>
                                                                {r.userNickName}
                                                            </span>
                                                            {r.replyToId !== c.commentId &&
                                                                r.replyToUserName && (
                                                                    <span
                                                                        style={{ color: '#8A9AA5' }}
                                                                    >
                                                                        {' '}
                                                                        回复{' '}
                                                                        <span
                                                                            style={{
                                                                                color: '#4E6EF2',
                                                                            }}
                                                                        >
                                                                            @{r.replyToUserName}
                                                                        </span>
                                                                    </span>
                                                                )}
                                                            <span style={{ marginLeft: 4 }}>
                                                                ：{r.content}
                                                            </span>
                                                        </div>
                                                        <div
                                                            style={{
                                                                fontSize: 12,
                                                                color: '#999',
                                                                marginTop: 2,
                                                                display: 'flex',
                                                                gap: 16,
                                                            }}
                                                        >
                                                            <span>{formatTime(r.createTime)}</span>
                                                            <span
                                                                onClick={() => handleReply(r)}
                                                                style={{
                                                                    cursor: 'pointer',
                                                                    color: '#666',
                                                                }}
                                                            >
                                                                回复
                                                            </span>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}

                        <div
                            style={{
                                marginTop: 16,
                                borderTop: '1px solid #f0f0f0',
                                paddingTop: 16,
                            }}
                        >
                            <Form onFinish={handleComment} layout="vertical">
                                <Form.Item name="content">
                                    <TextArea
                                        rows={2}
                                        ref={commentInputRef}
                                        placeholder={
                                            replyToId ? `回复 ${replyToUser}...` : '写评论...'
                                        }
                                    />
                                </Form.Item>
                                <Form.Item style={{ marginBottom: 0 }}>
                                    <Space>
                                        <Button type="primary" size="small" htmlType="submit">
                                            {replyToId ? `回复 ${replyToUser}` : '发表评论'}
                                        </Button>
                                        {replyToId && (
                                            <Button
                                                size="small"
                                                type="text"
                                                onClick={() => {
                                                    setReplyToId(undefined);
                                                    setReplyToUser('');
                                                }}
                                            >
                                                取消回复
                                            </Button>
                                        )}
                                    </Space>
                                </Form.Item>
                            </Form>
                        </div>
                    </div>
                </Card>
            </div>
            <ReportDialog
                open={reportOpen}
                targetType="feed"
                targetId={feed.feedId}
                targetLabel={`动态「${(feed.content || '').slice(0, 20)}」`}
                onClose={() => setReportOpen(false)}
            />
        </PageContainer>
    );
};

export default FeedDetail;
