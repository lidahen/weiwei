import { formatPrice, formatTime, IMG_FALLBACK, staticUrl } from './utils';
import { orderDetail, reviewByOrder, reviewSubmit } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useRequest, useSearchParams } from '@umijs/max';
import {
    Button,
    Card,
    Checkbox,
    Col,
    Descriptions,
    Image,
    Input,
    message,
    Rate,
    Row,
    Space,
    Spin,
    Switch,
    Tag,
} from 'antd';
import React, { useState } from 'react';
import UploadImages from '@/components/marketplace/UploadImages';
import { useIsMobile } from '@/hooks/useIsMobile';

/** 预设标签：按评价方向区分 */
const TAG_PRESETS: Record<string, string[]> = {
    buyer: ['描述相符', '发货及时', '包装仔细', '态度很好'],
    seller: ['付款爽快', '沟通愉快', '爱护商品'],
};

/** 快照 JSON 安全解析 */
function parseSnapshot(raw: unknown): Record<string, any> {
    if (!raw) return {};
    if (typeof raw !== 'string') return raw as Record<string, any>;
    try {
        return JSON.parse(raw);
    } catch {
        return {};
    }
}

/** 评价 JSON 数组字段安全解析 */
function parseJsonArray(raw: unknown): string[] {
    if (!raw) return [];
    try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

const Review: React.FC = () => {
    const [params] = useSearchParams();
    const orderId = params.get('orderId');

    const { data: orderResp, loading: orderLoading } = useRequest(() => orderDetail(orderId!), {
        ready: !!orderId,
        refreshDeps: [orderId],
    });
    const order = (orderResp as any)?.data ?? (orderResp as any);

    const { data: viewResp, loading: viewLoading, refresh } = useRequest(() => reviewByOrder(orderId!), {
        ready: !!orderId,
        refreshDeps: [orderId],
    });
    const view = (viewResp as any)?.data ?? (viewResp as any) ?? {};
    const isMobile = useIsMobile();

    const [rating, setRating] = useState<number>(0);
    const [content, setContent] = useState('');
    const [files, setFiles] = useState<any[]>([]);
    const [selectedTags, setSelectedTags] = useState<string[]>([]);
    const [anonymous, setAnonymous] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    if (orderLoading || viewLoading || !orderId)
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );

    if (!order?.orderId) {
        return (
            <PageContainer header={{ title: '评价', breadcrumb: {} }}>
                <Card>
                    <div style={{ color: '#999', textAlign: 'center', padding: 40 }}>订单不存在</div>
                </Card>
            </PageContainer>
        );
    }

    const snap = parseSnapshot(order.productSnapshot);
    const myRole: string | null = view.myRole ?? null;
    const myReview = myRole === 'buyer' ? view.buyerReview : myRole === 'seller' ? view.sellerReview : null;
    const counterpartReview =
        myRole === 'buyer' ? view.sellerReview : myRole === 'seller' ? view.buyerReview : null;
    const presetTags = TAG_PRESETS[myRole || 'buyer'] || TAG_PRESETS.buyer;

    const toggleTag = (tag: string) => {
        setSelectedTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]));
    };

    const doSubmit = async () => {
        if (!rating) {
            message.warning('请先打星');
            return;
        }
        setSubmitting(true);
        try {
            const paths = files
                .filter((f) => f.status === 'done' && f?.response?.data)
                .map((f) => f.response.data);
            await reviewSubmit({
                orderId: Number(orderId),
                rating,
                content: content || undefined,
                images: paths.length > 0 ? JSON.stringify(paths) : undefined,
                tags: selectedTags.length > 0 ? JSON.stringify(selectedTags) : undefined,
                anonymous,
            });
            message.success('评价成功');
            refresh();
        } catch (e: any) {
            message.error(e?.msg || e?.message || '提交失败');
        } finally {
            setSubmitting(false);
        }
    };

    /** 单条评价展示 */
    const renderReview = (r: any) => (
        <div>
            <Space size={12} align="start">
                <span style={{ fontSize: 12, color: '#999', minWidth: 64 }}>
                    {r.role === 'buyer' ? '买家评价' : '卖家评价'}
                </span>
                <Rate disabled value={r.overallScore || 0} style={{ fontSize: 14 }} />
            </Space>
            {r.content && (
                <div style={{ marginTop: 8, whiteSpace: 'pre-wrap', color: '#444' }}>{r.content}</div>
            )}
            {parseJsonArray(r.tags).length > 0 && (
                <Space size={4} wrap style={{ marginTop: 8 }}>
                    {parseJsonArray(r.tags).map((t) => (
                        <Tag key={t} color="orange" style={{ marginInlineEnd: 0 }}>
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
                                    width={isMobile ? 64 : 72}
                                    height={isMobile ? 64 : 72}
                                    style={{ borderRadius: 6, objectFit: 'cover' }}
                                />
                            </Col>
                        ))}
                    </Row>
                </Image.PreviewGroup>
            )}
            <div style={{ marginTop: 8, fontSize: 12, color: '#999' }}>
                {r.reviewerNickName || '匿名用户'} · {formatTime(r.createTime)}
            </div>
        </div>
    );

    return (
        <PageContainer header={{ title: '评价', breadcrumb: {} }}>
            <Row gutter={16}>
                <Col xs={24} lg={16}>
                    <Card title="交易信息" style={{ marginBottom: 16 }} className="twt-fade-up">
                        <Descriptions column={{ xs: 1, sm: 2 }}>
                            <Descriptions.Item label="商品">{snap.title || '-'}</Descriptions.Item>
                            <Descriptions.Item label="金额">{formatPrice(snap.price)}</Descriptions.Item>
                            <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item>
                            <Descriptions.Item label="对方">
                                {myRole === 'seller'
                                    ? order.buyerNickName || '买家'
                                    : order.sellerNickName || '卖家'}
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>

                    {myReview ? (
                        <Card title="我的评价" style={{ marginBottom: 16 }} className="twt-fade-up">
                            {renderReview(myReview)}
                            {!counterpartReview && (
                                <div style={{ color: '#999', marginTop: 16, fontSize: 13 }}>
                                    对方还没有评价，可在订单详情查看评价进展。
                                </div>
                            )}
                        </Card>
                    ) : (
                        <Card
                            title={myRole === 'seller' ? '评价买家' : '评价卖家'}
                            style={{ marginBottom: 16 }}
                            className="twt-fade-up"
                        >
                            <Space direction="vertical" size={16} style={{ width: '100%' }}>
                                <div>
                                    <div style={{ marginBottom: 8 }}>交易评分</div>
                                    <Rate value={rating} onChange={setRating} />
                                </div>
                                <div>
                                    <div style={{ marginBottom: 8 }}>选择标签（可选）</div>
                                    <Space size={8} wrap>
                                        {presetTags.map((tag) => (
                                            <Checkbox
                                                key={tag}
                                                checked={selectedTags.includes(tag)}
                                                onChange={() => toggleTag(tag)}
                                                style={{ marginInlineStart: 0, paddingInlineStart: 0 }}
                                            >
                                                {tag}
                                            </Checkbox>
                                        ))}
                                    </Space>
                                </div>
                                <Input.TextArea
                                    rows={4}
                                    maxLength={500}
                                    showCount
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    placeholder="说说这次的交易体验吧（选填）"
                                />
                                <div>
                                    <div style={{ marginBottom: 8 }}>晒图（可选，最多 9 张）</div>
                                    <UploadImages value={files} onChange={setFiles} max={9} />
                                </div>
                                <Space size={8}>
                                    <Switch checked={anonymous} onChange={setAnonymous} />
                                    <span style={{ color: '#666' }}>匿名评价</span>
                                </Space>
                                <Button
                                    type="primary"
                                    block
                                    loading={submitting}
                                    className="twt-press"
                                    onClick={doSubmit}
                                >
                                    提交评价
                                </Button>
                            </Space>
                        </Card>
                    )}
                </Col>
                <Col xs={24} lg={8}>
                    <Card title="对方的评价" className="twt-fade-up">
                        {counterpartReview ? (
                            renderReview(counterpartReview)
                        ) : (
                            <div style={{ color: '#999', textAlign: 'center', padding: 20 }}>
                                {myReview ? '对方还未评价' : '提交评价后可查看对方的评价'}
                            </div>
                        )}
                    </Card>
                    <Card style={{ marginTop: 16 }} className="twt-fade-up">
                        <Button block onClick={() => history.push(`/trade/order/detail?orderId=${orderId}`)}>
                            返回订单详情
                        </Button>
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default Review;
