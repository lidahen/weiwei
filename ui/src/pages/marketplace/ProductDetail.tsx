import ProductCard from '@/components/marketplace/ProductCard';
import { useIsMobile } from '@/hooks/useIsMobile';
import {
    favoriteAdd,
    favoriteRemove,
    productDetail,
    productSellerOthers,
    productSimilar,
    reviewPageByProduct,
} from '@/services/marketplace/api';
import {
    HeartFilled,
    HeartOutlined,
    MessageOutlined,
    ShoppingCartOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import { requireLogin } from '@/utils/loginGuard';
import {
    buildBreadcrumbJsonLd,
    buildProductJsonLd,
    isProductIndexable,
    resolveSiteUrl,
} from '@/utils/seo';
import { history, useModel, useParams, useRequest } from '@umijs/max';
import {
    Alert,
    Avatar,
    Button,
    Card,
    Col,
    Descriptions,
    Image,
    message,
    Rate,
    Row,
    Space,
    Spin,
    Tag,
} from 'antd';
import React, { useState } from 'react';
import ReportDialog from '@/components/marketplace/ReportDialog';
import { canPurchase, unavailableReason } from './productStatus';
import { marketplaceTokens } from './styles/tokens';
import { formatPrice, formatTime, IMG_FALLBACK, staticUrl } from './utils';

const conditionMap: Record<string, { text: string; color: string }> = {
    brand_new: { text: '全新', color: 'magenta' },
    '99': { text: '99新', color: 'orange' },
    '95': { text: '95新', color: 'orange' },
    '90': { text: '9成新', color: 'gold' },
    '80': { text: '8成新', color: 'gold' },
    '70_lower': { text: '7成及以下', color: 'default' },
};

/** 评价图片/标签 JSON 数组安全解析 */
function parseJsonArray(raw: unknown): string[] {
    if (!raw) return [];
    try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

const ProductDetail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const isMobile = useIsMobile();
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId) || undefined;
    const [reportOpen, setReportOpen] = useState(false);
    const { data, loading, refresh } = useRequest(() => productDetail(id!), { refreshDeps: [id] });
    const product = (data as any)?.data ?? (data as any);

    const { data: othersResp } = useRequest(
        () =>
            productSellerOthers({
                sellerId: product?.sellerId,
                excludeProductId: product?.productId,
                limit: 6,
            }),
        { refreshDeps: [product?.sellerId], ready: !!product?.sellerId },
    );
    const others: any[] = ((othersResp as any)?.data ?? (othersResp as any)) || [];

    // 相似推荐（向量召回，后端降级为同类目热门；为空则不渲染区块）
    const { data: similarResp } = useRequest(() => productSimilar(id!), {
        ready: !!id,
        refreshDeps: [id],
    });
    const similarList: any[] = ((similarResp as any)?.data ?? (similarResp as any)) || [];

    const { data: reviewResp } = useRequest(
        () => reviewPageByProduct(id!, { pageNum: 1, pageSize: 5 }),
        {
            ready: !!id,
            refreshDeps: [id],
        },
    );
    const reviewData = (reviewResp as any)?.data ?? (reviewResp as any) ?? {};
    const reviewRows: any[] = reviewData.rows || [];

    const images: string[] = (() => {
        if (!product?.images) return [];
        try {
            const parsed =
                typeof product.images === 'string' ? JSON.parse(product.images) : product.images;
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    })();

    const toggleFav = async () => {
        if (!requireLogin()) return;
        if (!product) return;
        try {
            if (product.favorited) {
                await favoriteRemove(product.productId);
            } else {
                await favoriteAdd(product.productId);
            }
            refresh();
        } catch (e) {
            message.error('操作失败,请先登录');
        }
    };

    const goChat = () => {
        if (!requireLogin()) return;
        if (!product) return;
        history.push(`/im/chat?peerId=${product.sellerId}&productId=${product.productId}`);
    };

    const buyNow = () => {
        if (!requireLogin()) return;
        if (!product) return;
        // 结算页负责地址/留言/费用明细，收银台负责创建支付单
        history.push(`/trade/checkout?productId=${product.productId}`);
    };

    const siteUrl = resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin);

    if (loading && !product) {
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );
    }

    // 商品不存在/已下架/跨租户时接口返回空，直接给出占位而不是崩溃
    if (!product) {
        return (
            <PageContainer header={{ title: '商品详情', breadcrumb: {} }}>
                {/* 软 404 必须 noindex，否则不存在的商品页会被当成有效页收录 */}
                <Seo title="商品不存在" canonicalPath={`/product/detail/${id}`} noindex />
                <Card>
                    <div style={{ color: '#999', textAlign: 'center', padding: 40 }}>
                        商品不存在或已下架
                    </div>
                </Card>
            </PageContainer>
        );
    }

    return (
        <PageContainer header={{ title: '商品详情', breadcrumb: {} }}>
            <Seo
                title={product?.title || '商品详情'}
                description={product?.description || `邻里二手在售：${product?.title || ''}`}
                canonicalPath={`/product/detail/${id}`}
                image={staticUrl(product?.cover)}
                type="product"
                noindex={!isProductIndexable(product?.status)}
                jsonLd={[
                    buildProductJsonLd(product, siteUrl),
                    buildBreadcrumbJsonLd(
                        [
                            { name: '首页', path: '/home' },
                            { name: '商品', path: '/search' },
                            {
                                name: product?.title || '商品详情',
                                path: `/product/detail/${id}`,
                            },
                        ],
                        siteUrl,
                    ),
                ]}
            />
            <Card>
                <Row gutter={isMobile ? 16 : 32} className="twt-fade-up">
                    <Col xs={24} md={12}>
                        {images.length > 0 ? (
                            <Image.PreviewGroup>
                                <Image
                                    src={staticUrl(images[0])}
                                    alt={product.title}
                                    fallback={IMG_FALLBACK}
                                    width="100%"
                                    style={{ borderRadius: 12 }}
                                    placeholder={
                                        <div
                                            className="twt-shimmer"
                                            style={{
                                                width: '100%',
                                                height: '100%',
                                                minHeight: 320,
                                                borderRadius: 12,
                                            }}
                                        />
                                    }
                                />
                                <Row gutter={8} style={{ marginTop: 8 }}>
                                    {images.slice(1).map((src, idx) => (
                                        <Col span={6} key={idx}>
                                            <Image
                                                src={staticUrl(src)}
                                                alt={`${product.title} 图片 ${idx + 2}`}
                                                fallback={IMG_FALLBACK}
                                                width="100%"
                                                style={{ borderRadius: 8 }}
                                                placeholder={
                                                    <div
                                                        className="twt-shimmer"
                                                        style={{
                                                            width: '100%',
                                                            height: '100%',
                                                            minHeight: 80,
                                                            borderRadius: 8,
                                                        }}
                                                    />
                                                }
                                            />
                                        </Col>
                                    ))}
                                </Row>
                            </Image.PreviewGroup>
                        ) : (
                            <Image
                                src={staticUrl(product.cover)}
                                alt={product.title}
                                fallback={IMG_FALLBACK}
                                width="100%"
                                style={{ borderRadius: 12 }}
                                placeholder={
                                    <div
                                        className="twt-shimmer"
                                        style={{
                                            width: '100%',
                                            height: '100%',
                                            minHeight: 320,
                                            borderRadius: 12,
                                        }}
                                    />
                                }
                            />
                        )}
                    </Col>
                    <Col xs={24} md={12}>
                        <h1 style={{ marginBottom: 12 }}>{product.title}</h1>
                        {/* 不可交易状态必须明确展示（D7），否则游客会对着已下架商品点购买 */}
                        {unavailableReason(product.status) && (
                            <Alert
                                type="warning"
                                showIcon
                                style={{ marginBottom: 12 }}
                                message={unavailableReason(product.status)}
                            />
                        )}
                        <Space size={8} style={{ marginBottom: 16 }}>
                            {product.condition && (
                                <Tag color={conditionMap[product.condition]?.color || 'default'}>
                                    {conditionMap[product.condition]?.text || product.condition}
                                </Tag>
                            )}
                            <Tag>{product.city}</Tag>
                            <span style={{ color: '#999', fontSize: 12 }}>
                                浏览 {product.viewCount ?? 0} · 想要 {product.wantCount ?? 0}
                                {product.publishTime
                                    ? ` · ${formatTime(product.publishTime)} 发布`
                                    : ''}
                            </span>
                        </Space>
                        <div style={{ marginBottom: isMobile ? 16 : 24 }}>
                            <span
                                style={{
                                    color: marketplaceTokens.color.primary,
                                    fontSize: isMobile ? 30 : 36,
                                    fontWeight: 700,
                                }}
                            >
                                {formatPrice(product.price)}
                            </span>
                            {product.originalPrice && (
                                <span
                                    style={{
                                        marginLeft: 12,
                                        color: '#999',
                                        textDecoration: 'line-through',
                                    }}
                                >
                                    {formatPrice(product.originalPrice)}
                                </span>
                            )}
                        </div>
                        <Card size="small" style={{ background: '#FFF8F1', marginBottom: 16 }}>
                            <Descriptions size="small" column={2}>
                                <Descriptions.Item label="运费">
                                    {product.shippingMethod === 'free'
                                        ? '包邮'
                                        : product.shippingMethod === 'self_pickup'
                                        ? '自提'
                                        : `¥${product.shippingFee}`}
                                </Descriptions.Item>
                                <Descriptions.Item label="库存">
                                    {product.stock} 件
                                </Descriptions.Item>
                            </Descriptions>
                        </Card>
                        <Card size="small" style={{ marginBottom: 16 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                <Avatar
                                    src={staticUrl(product.sellerAvatar)}
                                    size={48}
                                    style={{ cursor: 'pointer' }}
                                    onClick={() =>
                                        history.push(`/user/profile/${product.sellerId}`)
                                    }
                                />
                                <div
                                    style={{ flex: 1, cursor: 'pointer' }}
                                    onClick={() =>
                                        history.push(`/user/profile/${product.sellerId}`)
                                    }
                                >
                                    <div style={{ fontWeight: 500 }}>{product.sellerNickName}</div>
                                    <div style={{ fontSize: 12, color: '#999' }}>查看主页</div>
                                </div>
                                <Button
                                    onClick={() =>
                                        history.push(`/user/profile/${product.sellerId}`)
                                    }
                                >
                                    查看主页
                                </Button>
                                {/* 举报入口：不举报自己发布的商品（后端同样会拒） */}
                                {(!myId || product.sellerId !== myId) && (
                                    <a
                                        style={{ fontSize: 12, color: '#999', whiteSpace: 'nowrap' }}
                                        onClick={() => {
                                            if (!requireLogin()) return;
                                            setReportOpen(true);
                                        }}
                                    >
                                        举报
                                    </a>
                                )}
                            </div>
                        </Card>
                        {/* 手机端操作按钮纵向铺满，桌面横向 */}
                        <Space
                            size={12}
                            direction={isMobile ? 'vertical' : 'horizontal'}
                            style={{ width: '100%' }}
                        >
                            <Button
                                size="large"
                                icon={
                                    product.favorited ? (
                                        <HeartFilled style={{ color: '#FF6B35' }} />
                                    ) : (
                                        <HeartOutlined />
                                    )
                                }
                                onClick={toggleFav}
                                className={product.favorited ? 'twt-pop' : 'twt-press'}
                                block={isMobile}
                                style={{ minWidth: isMobile ? undefined : 112 }}
                            >
                                {product.favorited ? '已想要' : '想要'}
                            </Button>
                            <Button
                                size="large"
                                icon={<MessageOutlined />}
                                onClick={goChat}
                                block={isMobile}
                                style={{ minWidth: isMobile ? undefined : 112 }}
                            >
                                聊一聊
                            </Button>
                            <Button
                                size="large"
                                type="primary"
                                icon={<ShoppingCartOutlined />}
                                onClick={buyNow}
                                disabled={!canPurchase(product.status)}
                                className="twt-press"
                                block={isMobile}
                                style={{ flex: isMobile ? undefined : 1 }}
                            >
                                {canPurchase(product.status) ? '立即购买' : '不可购买'}
                            </Button>
                        </Space>
                    </Col>
                </Row>
            </Card>
            <Card title="商品描述" style={{ marginTop: 16 }} className="twt-fade-up">
                <div style={{ whiteSpace: 'pre-wrap', color: '#444', lineHeight: '24px' }}>
                    {product.description || '卖家未填写描述'}
                </div>
            </Card>
            <Card
                title="用户评价"
                style={{ marginTop: 16 }}
                className="twt-fade-up"
                extra={
                    reviewData.reviewCount > 0 ? (
                        <Space size={8}>
                            <Rate
                                disabled
                                allowHalf
                                value={Number(reviewData.avgScore) || 0}
                                style={{ fontSize: 14 }}
                            />
                            <span style={{ color: '#999', fontSize: 12 }}>
                                {reviewData.avgScore ?? '-'} 分 · {reviewData.reviewCount} 条
                            </span>
                        </Space>
                    ) : null
                }
            >
                {reviewRows.length === 0 ? (
                    <div style={{ color: '#999', padding: 20, textAlign: 'center' }}>暂无评价</div>
                ) : (
                    <Space direction="vertical" size={20} style={{ width: '100%' }}>
                        {reviewRows.map((r: any) => (
                            <div key={r.reviewId}>
                                <Space size={12} align="start">
                                    <Avatar src={staticUrl(r.reviewerAvatar)} size={32}>
                                        {(r.reviewerNickName || '匿')[0]}
                                    </Avatar>
                                    <div>
                                        <div style={{ fontSize: 13 }}>
                                            {r.reviewerNickName || '匿名用户'}
                                        </div>
                                        <Space size={8}>
                                            <Rate
                                                disabled
                                                value={r.overallScore || 0}
                                                style={{ fontSize: 12 }}
                                            />
                                            <span style={{ fontSize: 12, color: '#999' }}>
                                                {formatTime(r.createTime)}
                                            </span>
                                        </Space>
                                    </div>
                                </Space>
                                {r.content && (
                                    <div
                                        style={{
                                            marginTop: 8,
                                            color: '#444',
                                            whiteSpace: 'pre-wrap',
                                        }}
                                    >
                                        {r.content}
                                    </div>
                                )}
                                {parseJsonArray(r.tags).length > 0 && (
                                    <Space size={4} wrap style={{ marginTop: 8 }}>
                                        {parseJsonArray(r.tags).map((t) => (
                                            <Tag
                                                key={t}
                                                color="orange"
                                                style={{ marginInlineEnd: 0 }}
                                            >
                                                {t}
                                            </Tag>
                                        ))}
                                    </Space>
                                )}
                                {parseJsonArray(r.images).length > 0 && (
                                    <Image.PreviewGroup>
                                        <Row gutter={8} style={{ marginTop: 8 }}>
                                            {parseJsonArray(r.images).map((src, idx) => (
                                                <Col key={idx}>
                                                    <Image
                                                        src={staticUrl(src)}
                                                        fallback={IMG_FALLBACK}
                                                        width={72}
                                                        height={72}
                                                        style={{
                                                            borderRadius: 6,
                                                            objectFit: 'cover',
                                                        }}
                                                    />
                                                </Col>
                                            ))}
                                        </Row>
                                    </Image.PreviewGroup>
                                )}
                            </div>
                        ))}
                        {reviewData.reviewCount > reviewRows.length && (
                            <div style={{ color: '#999', fontSize: 12 }}>
                                共 {reviewData.reviewCount} 条评价，更多请前往卖家主页查看
                            </div>
                        )}
                    </Space>
                )}
            </Card>
            {similarList.length > 0 && (
                <Card title="相似推荐" style={{ marginTop: 16 }} className="twt-fade-up">
                    <Row gutter={[16, 16]}>
                        {similarList.map((s: any) => (
                            <Col key={s.productId} xs={12} sm={12} md={8} lg={4}>
                                <ProductCard
                                    image={s.cover}
                                    title={s.title}
                                    price={s.price}
                                    condition={s.condition}
                                    city={s.city}
                                    onClick={() => history.push(`/product/detail/${s.productId}`)}
                                />
                            </Col>
                        ))}
                    </Row>
                </Card>
            )}
            <Card title="卖家其他闲置" style={{ marginTop: 16 }} className="twt-fade-up">
                {others.length === 0 ? (
                    <div style={{ color: '#999', padding: 20, textAlign: 'center' }}>
                        卖家暂无其他在售商品
                    </div>
                ) : (
                    <Row gutter={[16, 16]}>
                        {others.map((o: any) => (
                            <Col key={o.productId} xs={12} sm={12} md={8} lg={4}>
                                <ProductCard
                                    image={o.cover}
                                    title={o.title}
                                    price={o.price}
                                    condition={o.condition}
                                    city={o.city}
                                    onClick={() => history.push(`/product/detail/${o.productId}`)}
                                />
                            </Col>
                        ))}
                    </Row>
                )}
            </Card>
            <ReportDialog
                open={reportOpen}
                targetType="product"
                targetId={product?.productId}
                targetLabel={`商品「${product?.title || ''}」`}
                onClose={() => setReportOpen(false)}
            />
        </PageContainer>
    );
};

export default ProductDetail;
