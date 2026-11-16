import {
    runnerCredit,
    taskDetail,
    taskLogs,
    taskReview,
    taskReviewInfo,
} from '@/services/runner/api';
import { PageContainer } from '@ant-design/pro-components';
import { useModel, useRequest, useSearchParams } from '@umijs/max';
import {
    Alert,
    Button,
    Card,
    Col,
    Descriptions,
    Input,
    message,
    Modal,
    Rate,
    Row,
    Space,
    Spin,
    Statistic,
    Tag,
    Timeline,
    Typography,
} from 'antd';
import React, { useState } from 'react';
import {
    formatDateTime,
    formatPrice,
    LOG_ACTION_MAP,
    parseImageList,
    PAY_STATUS_MAP,
    TASK_STATUS_MAP,
    TASK_TYPE_MAP,
} from './utils';

const { Text } = Typography;

const Detail: React.FC = () => {
    const [params] = useSearchParams();
    const id = params.get('id');
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);

    const [reviewOpen, setReviewOpen] = useState(false);
    const [rating, setRating] = useState<number>(5);
    const [content, setContent] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const { data: detailData, loading } = useRequest(
        () => (id ? taskDetail(id) : Promise.resolve(null)),
        { refreshDeps: [id] },
    );
    const { data: logsData } = useRequest(() => (id ? taskLogs(id) : Promise.resolve(null)), {
        refreshDeps: [id],
    });
    const { data: creditData, run: fetchCredit } = useRequest(
        (uid: number | string) => runnerCredit(uid),
        { manual: true },
    );
    const { data: reviewData, refresh: refreshReview } = useRequest(
        () => (id ? taskReviewInfo(id) : Promise.resolve(null)),
        { refreshDeps: [id] },
    );

    // hooks 必须在提前 return 之前调用，否则条件渲染时会报
    // "Rendered more hooks than during the previous render"
    const t: any = (detailData as any)?.data ?? detailData;
    const assigneeId = t?.assigneeId;
    React.useEffect(() => {
        if (assigneeId) fetchCredit(assigneeId);
    }, [assigneeId]);

    if (loading) return <Spin />;
    if (!detailData) {
        return (
            <PageContainer header={{ title: '任务详情', breadcrumb: {} }}>
                <Alert message="任务不存在或无权查看" type="error" showIcon />
            </PageContainer>
        );
    }

    const logs: any[] = (logsData as any)?.data ?? logsData ?? [];
    const review: any = (reviewData as any)?.data ?? reviewData ?? null;
    const credit: any = (creditData as any)?.data ?? creditData ?? null;
    const isMyPublished = t?.publisherId && Number(t.publisherId) === myId;
    const canReview = isMyPublished && t?.status === 'completed' && !review;

    const submitReview = async () => {
        if (!id) return;
        setSubmitting(true);
        try {
            await taskReview(id, { rating, content: content || undefined });
            message.success('评价成功');
            setReviewOpen(false);
            refreshReview();
        } finally {
            setSubmitting(false);
        }
    };
    const type = TASK_TYPE_MAP[t.taskType];
    const status = TASK_STATUS_MAP[t.status];
    const payStatus = PAY_STATUS_MAP[t.payStatus || 'unpaid'];

    return (
        <PageContainer
            header={{
                title: `${type?.emoji || ''} ${t.title}`,
                breadcrumb: {},
            }}
        >
            <Row gutter={[16, 16]}>
                <Col xs={24} xl={16}>
                    <Card title="基本信息" style={{ marginBottom: 16 }}>
                        <Descriptions column={{ xs: 1, sm: 2 }}>
                            <Descriptions.Item label="任务编号">{t.taskNo}</Descriptions.Item>
                            <Descriptions.Item label="类型">{type?.label}</Descriptions.Item>
                            <Descriptions.Item label="状态">
                                <Tag color={status?.color}>{status?.text || t.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="支付状态">
                                <Tag color={payStatus?.color}>{payStatus?.text}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="取件地址">{t.pickupAddr}</Descriptions.Item>
                            <Descriptions.Item label="送达地址">{t.deliverAddr}</Descriptions.Item>
                            <Descriptions.Item label="接单截止">
                                {formatDateTime(t.deadline)}
                            </Descriptions.Item>
                            <Descriptions.Item label="期望完成">
                                {formatDateTime(t.expectTime)}
                            </Descriptions.Item>
                            <Descriptions.Item label="报酬" span={2}>
                                {t.isPaid === 1 ? (
                                    <span
                                        style={{ color: '#FF6B35', fontWeight: 600, fontSize: 18 }}
                                    >
                                        {formatPrice(t.price)}
                                    </span>
                                ) : (
                                    <Tag color="blue">免费任务（无报酬）</Tag>
                                )}
                            </Descriptions.Item>
                            {t.description && (
                                <Descriptions.Item label="详细描述" span={2}>
                                    {t.description}
                                </Descriptions.Item>
                            )}
                            {t.assigneeId && (
                                <Descriptions.Item label="接单人" span={2}>
                                    ID: {t.assigneeId}
                                </Descriptions.Item>
                            )}
                            {t.cancelReason && (
                                <Descriptions.Item label="取消原因" span={2}>
                                    <Text type="danger">{t.cancelReason}</Text>
                                </Descriptions.Item>
                            )}
                        </Descriptions>
                    </Card>

                    {parseImageList(t.finishImages).length > 0 && (
                        <Card title="完成凭证" style={{ marginBottom: 16 }}>
                            <Space wrap>
                                {parseImageList(t.finishImages).map((url, i) => (
                                    <img
                                        key={i}
                                        src={url}
                                        alt="凭证"
                                        style={{
                                            width: 120,
                                            height: 120,
                                            objectFit: 'cover',
                                            borderRadius: 6,
                                        }}
                                    />
                                ))}
                            </Space>
                        </Card>
                    )}

                    <Card title="流转记录">
                        {logs.length === 0 ? (
                            <Text type="secondary">暂无记录</Text>
                        ) : (
                            <Timeline
                                items={logs.map((l) => ({
                                    color: LOG_ACTION_MAP[l.action]?.color || 'gray',
                                    children: (
                                        <Space direction="vertical" size={0}>
                                            <Space>
                                                <Tag color={LOG_ACTION_MAP[l.action]?.color}>
                                                    {LOG_ACTION_MAP[l.action]?.label || l.action}
                                                </Tag>
                                                <Text type="secondary">
                                                    {formatDateTime(l.createTime)} · 操作人{' '}
                                                    {l.operatorId}
                                                </Text>
                                            </Space>
                                            {l.remark && <Text>{l.remark}</Text>}
                                        </Space>
                                    ),
                                }))}
                            />
                        )}
                    </Card>
                </Col>
                <Col xs={24} xl={8}>
                    <Card title="时间节点" style={{ marginBottom: 16 }}>
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <div>
                                <Text type="secondary">创建：</Text>
                                <Text>{formatDateTime(t.createTime)}</Text>
                            </div>
                            {t.assignTime && (
                                <div>
                                    <Text type="secondary">接单：</Text>
                                    <Text>{formatDateTime(t.assignTime)}</Text>
                                </div>
                            )}
                            {t.finishTime && (
                                <div>
                                    <Text type="secondary">完成：</Text>
                                    <Text>{formatDateTime(t.finishTime)}</Text>
                                </div>
                            )}
                            {t.confirmTime && (
                                <div>
                                    <Text type="secondary">确认：</Text>
                                    <Text>{formatDateTime(t.confirmTime)}</Text>
                                </div>
                            )}
                        </Space>
                    </Card>

                    {t.assigneeId && credit && (
                        <Card title="接单人信用" style={{ marginBottom: 16 }} size="small">
                            <Row gutter={8}>
                                <Col span={8}>
                                    <Statistic
                                        title="信用分"
                                        value={credit.creditScore ?? 80}
                                        suffix={credit.hasData ? '' : '(默认)'}
                                    />
                                </Col>
                                <Col span={8}>
                                    <Statistic
                                        title="平均评分"
                                        value={credit.avgRating ?? '-'}
                                        suffix={credit.avgRating ? '/5' : ''}
                                    />
                                </Col>
                                <Col span={8}>
                                    <Statistic
                                        title="完成单数"
                                        value={credit.completedCount ?? 0}
                                    />
                                </Col>
                            </Row>
                        </Card>
                    )}

                    {canReview && (
                        <Card title="评价" style={{ marginBottom: 16 }} size="small">
                            <Button type="primary" onClick={() => setReviewOpen(true)}>
                                评价本次互助服务
                            </Button>
                        </Card>
                    )}

                    {review && (
                        <Card title="服务评价" style={{ marginBottom: 16 }} size="small">
                            <Space direction="vertical" size={4}>
                                <Rate disabled value={review.rating || 0} />
                                {review.content && <Text>{review.content}</Text>}
                                <Text type="secondary">{formatDateTime(review.createTime)}</Text>
                            </Space>
                        </Card>
                    )}
                </Col>
            </Row>
            <Modal
                title="评价本次互助服务"
                open={reviewOpen}
                confirmLoading={submitting}
                onOk={submitReview}
                onCancel={() => setReviewOpen(false)}
                okText="提交评价"
            >
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <div>
                        <Text>服务评分：</Text>
                        <Rate value={rating} onChange={setRating} />
                    </div>
                    <Input.TextArea
                        rows={3}
                        maxLength={500}
                        placeholder="评语（可选，最多500字）"
                        value={content}
                        onChange={(e) => setContent(e.target.value)}
                    />
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default Detail;
