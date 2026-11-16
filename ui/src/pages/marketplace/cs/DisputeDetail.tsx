import { csDisputeClaim, csDisputeDetail, csDisputeJudge } from '@/services/marketplace/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useParams, useRequest } from '@umijs/max';
import {
    Button,
    Card,
    Col,
    Descriptions,
    Image,
    Input,
    message,
    Modal,
    Radio,
    Row,
    Space,
    Spin,
    Tag,
} from 'antd';
import React, { useState } from 'react';

const statusMap: Record<string, string> = {
    pending: '待处理',
    in_progress: '处理中',
    closed: '已结案',
};
const typeMap: Record<string, string> = {
    refund: '仅退款',
    return: '退货退款',
    no_received: '未收到货',
    fake: '假货',
    other: '其他',
};

const DisputeDetail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const { data, loading, refresh } = useRequest(() => csDisputeDetail(id!), {
        refreshDeps: [id],
    });
    const d = (data as any)?.data ?? (data as any);

    const [decision, setDecision] = useState<string>('buyer');
    const [judgeReason, setJudgeReason] = useState('');
    const [submitting, setSubmitting] = useState(false);

    if (loading || !d)
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );

    const handleJudge = async () => {
        if (!judgeReason.trim()) {
            message.warning('请填写仲裁说明');
            return;
        }
        setSubmitting(true);
        try {
            await csDisputeJudge(d.disputeId, { decision, reason: judgeReason, amount: d.amount });
            message.success('仲裁已提交');
            refresh();
        } catch (e) {
            message.error('提交失败');
        }
        setSubmitting(false);
    };

    const closed = d.status === 'closed';

    return (
        <PageContainer
            header={{
                title: `纠纷工单 DS${String(d.disputeId).padStart(6, '0')}`,
                breadcrumb: {},
                extra: [
                    <Tag
                        key="s"
                        color={
                            d.status === 'closed'
                                ? 'green'
                                : d.status === 'in_progress'
                                ? 'orange'
                                : 'red'
                        }
                    >
                        {statusMap[d.status]}
                    </Tag>,
                ],
            }}
        >
            <Row gutter={16}>
                <Col span={16}>
                    <Card title="订单信息" style={{ marginBottom: 16 }}>
                        <Descriptions column={2}>
                            <Descriptions.Item label="订单编号">{d.orderNo}</Descriptions.Item>
                            <Descriptions.Item label="纠纷类型">
                                {typeMap[d.type] || d.type}
                            </Descriptions.Item>
                            <Descriptions.Item label="争议金额">
                                <span style={{ color: '#FF6B35', fontWeight: 600 }}>
                                    ¥{d.amount}
                                </span>
                            </Descriptions.Item>
                            <Descriptions.Item label="优先级">
                                <Tag color={d.priority === 'high' ? 'red' : 'default'}>
                                    {d.priority}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="买家">
                                {d.buyerNickName} ({d.buyerMobile})
                            </Descriptions.Item>
                            <Descriptions.Item label="卖家">
                                {d.sellerNickName} ({d.sellerMobile})
                            </Descriptions.Item>
                        </Descriptions>
                        {d.productSnapshot && (
                            <div>
                                <Image
                                    src={
                                        typeof d.productSnapshot === 'string'
                                            ? JSON.parse(d.productSnapshot)?.cover
                                            : d.productSnapshot
                                    }
                                    width={80}
                                />
                            </div>
                        )}
                    </Card>
                    <Card title="裁决面板" style={{ marginBottom: 16 }}>
                        {closed ? (
                            <Descriptions column={1}>
                                <Descriptions.Item label="裁决结果">
                                    <Tag
                                        color={
                                            d.judgeDecision === 'buyer'
                                                ? 'red'
                                                : d.judgeDecision === 'seller'
                                                ? 'blue'
                                                : 'gold'
                                        }
                                    >
                                        {d.judgeDecision === 'buyer'
                                            ? '支持买家'
                                            : d.judgeDecision === 'seller'
                                            ? '支持卖家'
                                            : '部分退款'}
                                    </Tag>
                                </Descriptions.Item>
                                {d.judgeAmount && (
                                    <Descriptions.Item label="金额">
                                        ¥{d.judgeAmount}
                                    </Descriptions.Item>
                                )}
                                <Descriptions.Item label="原因">{d.judgeReason}</Descriptions.Item>
                                <Descriptions.Item label="仲裁人">
                                    客服 #{d.judgeCsId}
                                </Descriptions.Item>
                                <Descriptions.Item label="仲裁时间">{d.judgeAt}</Descriptions.Item>
                            </Descriptions>
                        ) : (
                            <>
                                <Radio.Group
                                    value={decision}
                                    onChange={(e) => setDecision(e.target.value)}
                                >
                                    <Space direction="vertical">
                                        <Radio value="buyer" style={{ marginBottom: 8 }}>
                                            支持买家 全额退款
                                        </Radio>
                                        <Radio value="seller" style={{ marginBottom: 8 }}>
                                            支持卖家 驳回诉求
                                        </Radio>
                                        <Radio value="partial">部分退款</Radio>
                                    </Space>
                                </Radio.Group>
                                <div style={{ marginTop: 16 }}>
                                    <div style={{ marginBottom: 8, fontWeight: 500 }}>
                                        仲裁说明 (必填)
                                    </div>
                                    <Input.TextArea
                                        rows={4}
                                        value={judgeReason}
                                        onChange={(e) => setJudgeReason(e.target.value)}
                                        placeholder="请详细说明裁决依据,如:经核买家提供的开箱视频显示摄像头确有灰点,非人为损坏,支持退货退款"
                                    />
                                </div>
                                <Button
                                    type="primary"
                                    loading={submitting}
                                    style={{ marginTop: 16 }}
                                    onClick={() =>
                                        Modal.confirm({
                                            title: '确认仲裁结果',
                                            content: `决定: ${
                                                decision === 'buyer'
                                                    ? '支持买家'
                                                    : decision === 'seller'
                                                    ? '支持卖家'
                                                    : '部分退款'
                                            }`,
                                            onOk: handleJudge,
                                        })
                                    }
                                >
                                    提交裁决
                                </Button>
                            </>
                        )}
                    </Card>
                </Col>
                <Col span={8}>
                    <Card title="操作" style={{ marginBottom: 16 }}>
                        <Space direction="vertical" style={{ width: '100%' }}>
                            {d.status === 'pending' && (
                                <Button
                                    block
                                    onClick={async () => {
                                        await csDisputeClaim(d.disputeId);
                                        message.success('已领取');
                                        refresh();
                                    }}
                                >
                                    领取工单
                                </Button>
                            )}
                            <Button
                                block
                                onClick={() =>
                                    history.push(`/trade/order/detail?orderId=${d.orderId}`)
                                }
                            >
                                查看订单
                            </Button>
                        </Space>
                    </Card>
                    <Card title="时间轴">
                        <div>下单: {d.createTime}</div>
                        <div style={{ marginTop: 4 }}>最新: {d.updateTime || d.createTime}</div>
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default DisputeDetail;
