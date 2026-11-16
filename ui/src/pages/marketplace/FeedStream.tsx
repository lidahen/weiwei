import EmptyState from '@/components/marketplace/EmptyState';
import SkeletonCards from '@/components/marketplace/SkeletonCards';
import { feedFollow, feedLike, feedRecommend } from '@/services/marketplace/api';
import {
    CommentOutlined,
    HeartFilled,
    HeartOutlined,
    PlusOutlined,
    ShareAltOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import SeoHeading from '@/components/Seo/Heading';
import { requireLogin } from '@/utils/loginGuard';
import { useRequest } from '@umijs/max';
import { Avatar, Button, Card, Col, Image, message, Row, Tabs, Tag } from 'antd';
import React, { useCallback, useRef, useState } from 'react';
import { history } from 'umi';
import { marketplaceTokens } from './styles/tokens';
import { formatTime, IMG_FALLBACK, staticUrl } from './utils';

const FeedStream: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'latest' | 'hot'>('latest');
    const fetcher = activeTab === 'latest' ? feedRecommend : feedFollow;

    const [list, setList] = useState<any[]>([]);
    // 点赞弹跳动画：记录刚点赞的动态 ID，渲染后清除
    const [popId, setPopId] = useState<number | null>(null);

    const { loading } = useRequest(() => fetcher({ current: 1, pageSize: 20 }), {
        refreshDeps: [activeTab],
        onSuccess: (res: any) => {
            // 后端 JsonResult<List<Feed>>：数组在 data 字段；做数组守卫防接口变动导致整页崩溃
            const payload = res?.data ?? res;
            setList(
                Array.isArray(payload)
                    ? payload
                    : Array.isArray(payload?.records)
                    ? payload.records
                    : [],
            );
        },
    });

    const handleLike = useCallback(async (feedId: number) => {
        if (!requireLogin()) return;
        try {
            const res: any = await feedLike(feedId);
            const data = res?.data ?? res;
            const liked = data?.liked ?? false;
            const likeCount = data?.likeCount ?? 0;
            if (liked) setPopId(feedId);
            // 乐观更新，不重新请求列表
            setList((prev) =>
                prev.map((item: any) => {
                    if (item.feedId !== feedId) return item;
                    return { ...item, liked, likeCount };
                }),
            );
        } catch {
            message.error('操作失败');
        }
    }, []);

    const handleShare = useCallback((feedId: number) => {
        const link = `${window.location.origin}/feed/detail/${feedId}`;
        navigator.clipboard?.writeText(link).then(
            () => message.success('链接已复制，去分享吧'),
            () => message.info(link),
        );
    }, []);

    // 点赞弹跳动画只播一次，400ms 后复位
    const likePopRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const markPop = useCallback(() => {
        if (likePopRef.current) clearTimeout(likePopRef.current);
        likePopRef.current = setTimeout(() => setPopId(null), 400);
    }, []);

    return (
        <PageContainer
            header={{
                title: '动态广场',
                breadcrumb: {},
                extra: (
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => {
                            if (requireLogin()) history.push('/feed/publish');
                        }}
                    >
                        发布动态
                    </Button>
                ),
            }}
        >
            <Seo
                title="动态广场"
                description="邻里二手社区动态：同城闲置、换物与互助信息实时更新。"
                canonicalPath="/feed/stream"
            />
            <SeoHeading>动态广场</SeoHeading>
            <Tabs
                activeKey={activeTab}
                onChange={(k) => setActiveTab(k as any)}
                items={[
                    { key: 'latest', label: '最新' },
                    { key: 'hot', label: '热门' },
                ]}
            />
            <div style={{ maxWidth: 760, margin: '0 auto' }}>
                {loading ? (
                    <SkeletonCards
                        count={3}
                        coverHeight={120}
                        xs={24}
                        sm={24}
                        md={24}
                        lg={24}
                        xl={24}
                    />
                ) : list.length === 0 ? (
                    <EmptyState
                        text="暂无动态，发布第一条吧"
                        ctaText="发布动态"
                        onCtaClick={() => {
                            if (requireLogin()) history.push('/feed/publish');
                        }}
                    />
                ) : (
                    list.map((item: any, idx: number) => {
                        const rawImages = item.images
                            ? (() => {
                                  try {
                                      const parsed = JSON.parse(item.images);
                                      return Array.isArray(parsed) ? parsed : [];
                                  } catch {
                                      return [];
                                  }
                              })()
                            : [];
                        const images = rawImages.map((img: string) => staticUrl(img) || '');
                        return (
                            <Card
                                key={item.feedId}
                                hoverable
                                className="twt-fade-up twt-press"
                                style={{
                                    marginBottom: 16,
                                    borderRadius: marketplaceTokens.radius.md,
                                    animationDelay: `${Math.min(idx, 8) * 60}ms`,
                                }}
                                onClick={() => history.push(`/feed/detail/${item.feedId}`)}
                            >
                                <Card.Meta
                                    avatar={
                                        <Avatar
                                            src={staticUrl(item.userAvatar || item.authorAvatar)}
                                        />
                                    }
                                    title={
                                        <span
                                            style={{
                                                display: 'inline-flex',
                                                alignItems: 'center',
                                                gap: 8,
                                            }}
                                        >
                                            {item.userNickName || item.authorNickName}
                                            <span
                                                style={{
                                                    fontWeight: 400,
                                                    fontSize: 12,
                                                    color: marketplaceTokens.color.mute,
                                                }}
                                            >
                                                {formatTime(item.publishTime || item.createTime)}
                                            </span>
                                        </span>
                                    }
                                />
                                {item.content && (
                                    <p
                                        style={{
                                            whiteSpace: 'pre-wrap',
                                            marginTop: 12,
                                            lineHeight: 1.7,
                                        }}
                                    >
                                        {item.content}
                                    </p>
                                )}
                                {images.length > 0 && (
                                    <Row gutter={[8, 8]} style={{ marginTop: 8 }}>
                                        {images.map((img: string, idx: number) => (
                                            <Col
                                                key={idx}
                                                xs={images.length === 1 ? 24 : 8}
                                                sm={images.length === 1 ? 16 : 8}
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
                                                                minHeight: 120,
                                                                borderRadius: 8,
                                                            }}
                                                        />
                                                    }
                                                    style={{
                                                        width: '100%',
                                                        maxHeight: 220,
                                                        objectFit: 'cover',
                                                        borderRadius: 8,
                                                    }}
                                                />
                                            </Col>
                                        ))}
                                    </Row>
                                )}
                                {item.relatedProductId && (
                                    <Card
                                        size="small"
                                        hoverable
                                        style={{
                                            marginTop: 12,
                                            background: marketplaceTokens.color.primarySoft,
                                            border: 'none',
                                        }}
                                        onClick={(e: any) => {
                                            e.stopPropagation();
                                            if (item.relatedProductId)
                                                history.push(
                                                    `/product/detail/${item.relatedProductId}`,
                                                );
                                        }}
                                    >
                                        <span
                                            style={{
                                                display: 'inline-flex',
                                                alignItems: 'center',
                                                gap: 8,
                                                flexWrap: 'wrap',
                                            }}
                                        >
                                            <Tag color="orange" style={{ marginRight: 0 }}>
                                                关联商品
                                            </Tag>
                                            <strong>
                                                {item.relatedProductTitle || '未知商品'}
                                            </strong>
                                            {item.relatedProductPrice !== null &&
                                                item.relatedProductPrice !== undefined && (
                                                    <span
                                                        style={{
                                                            color: marketplaceTokens.color.primary,
                                                            fontWeight: 600,
                                                        }}
                                                    >
                                                        ¥{item.relatedProductPrice}
                                                    </span>
                                                )}
                                        </span>
                                    </Card>
                                )}
                                <div
                                    style={{
                                        display: 'flex',
                                        gap: 28,
                                        flexWrap: 'wrap',
                                        rowGap: 6,
                                        marginTop: 14,
                                        paddingTop: 12,
                                        borderTop: `1px solid ${marketplaceTokens.color.line}`,
                                        fontSize: 14,
                                    }}
                                >
                                    <span
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleLike(item.feedId);
                                            if (item.liked !== true) markPop();
                                        }}
                                        style={{
                                            cursor: 'pointer',
                                            color: item.liked
                                                ? '#ff4d4f'
                                                : marketplaceTokens.color.sub,
                                            transition: 'color .2s',
                                        }}
                                    >
                                        {item.liked ? (
                                            <HeartFilled
                                                className={popId === item.feedId ? 'twt-pop' : ''}
                                                style={{ color: '#ff4d4f' }}
                                            />
                                        ) : (
                                            <HeartOutlined />
                                        )}{' '}
                                        {item.likeCount || 0}
                                    </span>
                                    <span
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            history.push(`/feed/detail/${item.feedId}`);
                                        }}
                                        style={{
                                            cursor: 'pointer',
                                            color: marketplaceTokens.color.sub,
                                        }}
                                    >
                                        <CommentOutlined /> {item.commentCount || 0}
                                    </span>
                                    <span
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleShare(item.feedId);
                                        }}
                                        style={{
                                            cursor: 'pointer',
                                            color: marketplaceTokens.color.sub,
                                        }}
                                    >
                                        <ShareAltOutlined /> {item.forwardCount || 0}
                                    </span>
                                </div>
                            </Card>
                        );
                    })
                )}
            </div>
        </PageContainer>
    );
};

export default FeedStream;
