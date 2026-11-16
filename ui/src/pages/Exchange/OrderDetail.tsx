import {
    orderCancel,
    orderConfirm,
    orderDeliver,
    orderDetail,
    orderDispute,
    orderReview,
} from '@/services/exchange/api';
import { PageContainer } from '@ant-design/pro-components';
import { history, useModel, useParams, useRequest } from '@umijs/max';
import {
    Alert,
    Avatar,
    Button,
    Card,
    Col,
    Descriptions,
    Input,
    Modal,
    Rate,
    Row,
    Space,
    Spin,
    Tag,
    Timeline,
    Typography,
    message,
} from 'antd';
import React, { useState } from 'react';
import { DELIVERY_MODE_MAP, ORDER_LOG_ACTION_MAP, ORDER_STATUS_MAP, formatDateTime } from './utils';

const { Text } = Typography;

/**
 * 交换订单详情（双边履约）
 * - 双边进度时间线：A交付 → B交付 → A确认 → B确认（四个独立时间戳，status 由状态机推进）
 * - 面交：我的 6 位交换码大号展示；快递：运单号录入
 * - 交付 / 确认 / 取消（仅双方均未交付）/ 纠纷 / 完成后双向评价
 */
const OrderDetail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);

    const [disputeOpen, setDisputeOpen] = useState(false);
    const [disputeReason, setDisputeReason] = useState('');
    const [reviewOpen, setReviewOpen] = useState(false);
    const [reviewScore, setReviewScore] = useState<number>(5);
    const [reviewContent, setReviewContent] = useState('');
    const [actionLoading, setActionLoading] = useState(false);

    const {
        data: detailResp,
        loading,
        refresh,
    } = useRequest(() => (id ? orderDetail(id) : Promise.resolve(null)), { refreshDeps: [id] });

    if (loading) return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (!detailResp) {
        return (
            <PageContainer header={{ title: '订单详情', breadcrumb: {} }}>
                <Alert message="订单不存在或无权查看" type="error" showIcon />
            </PageContainer>
        );
    }

    const d: any = (detailResp as any)?.data ?? detailResp;
    const order: any = d?.order;
    if (!order) {
        return (
            <PageContainer header={{ title: '订单详情', breadcrumb: {} }}>
                <Alert message="订单不存在或无权查看" type="error" showIcon />
            </PageContainer>
        );
    }

    const mySide: 'a' | 'b' = d?.mySide === 'b' ? 'b' : 'a';
    const peerSide = mySide === 'a' ? 'b' : 'a';
    const isExpress = order.deliveryMode === 'express';

    // 我方 / 对方侧字段映射（A/B 各自的交付与确认是四个独立时间戳列）
    const myDeliveredTime = mySide === 'a' ? order.aDeliveredTime : order.bDeliveredTime;
    const peerDeliveredTime = mySide === 'a' ? order.bDeliveredTime : order.aDeliveredTime;
    const myConfirmedTime = mySide === 'a' ? order.aConfirmedTime : order.bConfirmedTime;
    const myTrackNo = mySide === 'a' ? order.aTrackNo : order.bTrackNo;

    const pickupCodes: any[] = d?.pickupCodes ?? [];
    const myCode = pickupCodes.find((c) => c.side === mySide);

    const logs: any[] = d?.logs ?? [];
    const reviews: any[] = d?.reviews ?? [];
    const myReview = reviews.find((r) => Number(r.reviewerId) === myId);
    const peerReview = reviews.find((r) => Number(r.reviewerId) !== myId);
    // peerInfo 来自 IImService.selectUserInfo → { user_id, nick_name, avatar, user_type }（resultType=map 列名即键）
    const peerInfo: any = d?.peerInfo;
    const peerName =
        peerInfo?.nick_name ||
        peerInfo?.nickName ||
        (peerInfo?.user_id ? `用户 ${peerInfo.user_id}` : `用户 ${d?.peerId ?? ''}`);

    const bothUndelivered = !order.aDeliveredTime && !order.bDeliveredTime;
    const canDeliver =
        !myDeliveredTime && (order.status === 'created' || order.status === 'delivering');
    const canConfirm = order.status === 'pending_confirm' && !myConfirmedTime;
    const canCancel =
        bothUndelivered && (order.status === 'created' || order.status === 'delivering');
    const canDispute = order.status === 'delivering' || order.status === 'pending_confirm';
    const canReview = order.status === 'completed' && !myReview;

    const sideLabel = (side: 'a' | 'b') =>
        side === mySide ? `${side.toUpperCase()}（我）` : `${side.toUpperCase()}（对方）`;

    // ===== 动作 =====
    const wrapAction = async (fn: () => Promise<any>, successTip: string) => {
        setActionLoading(true);
        try {
            await fn();
            message.success(successTip);
            refresh();
        } catch (e: any) {
            message.error(e?.msg || e?.message || '操作失败');
        } finally {
            setActionLoading(false);
        }
    };

    const handleDeliverFace = () => {
        Modal.confirm({
            title: '标记我方已交付',
            content: '面交现场请先与对方互相出示交换码核对，再点确认。',
            okText: '我已交付',
            onOk: () => wrapAction(() => orderDeliver(id!), '已标记交付'),
        });
    };

    const handleDeliverExpress = () => {
        let trackNo = myTrackNo || '';
        Modal.confirm({
            title: '快递交付',
            content: (
                <div>
                    <p>请输入您寄出的运单号（必填）：</p>
                    <Input
                        defaultValue={myTrackNo || ''}
                        placeholder="快递运单号"
                        onChange={(e) => (trackNo = e.target.value)}
                    />
                </div>
            ),
            okText: '提交交付',
            onOk: async () => {
                if (!trackNo.trim()) {
                    message.warning('快递交换必须填写运单号');
                    return Promise.reject();
                }
                await wrapAction(
                    () => orderDeliver(id!, trackNo.trim()),
                    '运单号已登记，视为已交付',
                );
            },
        });
    };

    const handleConfirm = () => {
        Modal.confirm({
            title: '确认已收到对方物品',
            content: '双方都确认后交换完成，双方物品将标记为已换出。',
            okText: '我已收到',
            onOk: () => wrapAction(() => orderConfirm(id!), '已确认收到'),
        });
    };

    const handleCancel = () => {
        let reason = '';
        Modal.confirm({
            title: '取消交换',
            content: (
                <div>
                    <p>仅双方均未交付时可取消，物品将解锁回换物广场。请输入取消原因：</p>
                    <Input
                        defaultValue="协商一致取消"
                        onChange={(e) => (reason = e.target.value)}
                    />
                </div>
            ),
            okText: '取消交换',
            okButtonProps: { danger: true },
            onOk: async () => {
                await wrapAction(
                    () => orderCancel(id!, reason || '用户取消'),
                    '订单已取消，物品已解锁',
                );
            },
        });
    };

    const submitDispute = async () => {
        if (!disputeReason.trim()) {
            message.warning('请填写纠纷原因');
            return;
        }
        setActionLoading(true);
        try {
            await orderDispute(id!, disputeReason.trim());
            message.success('纠纷已提交，等待平台裁决');
            setDisputeOpen(false);
            setDisputeReason('');
            refresh();
        } catch (e: any) {
            message.error(e?.msg || e?.message || '操作失败');
        } finally {
            setActionLoading(false);
        }
    };

    const submitReview = async () => {
        setActionLoading(true);
        try {
            await orderReview(id!, {
                score: reviewScore,
                content: reviewContent.trim() || undefined,
            });
            message.success('评价成功');
            setReviewOpen(false);
            refresh();
        } catch (e: any) {
            message.error(e?.msg || e?.message || '评价失败');
        } finally {
            setActionLoading(false);
        }
    };

    // ===== 双边进度时间线 =====
    const progressItems = [
        {
            color: order.aDeliveredTime ? 'green' : 'gray',
            children: (
                <Space direction="vertical" size={0}>
                    <Text strong>A 交付 {sideLabel('a')}</Text>
                    <Text type="secondary">
                        {order.aDeliveredTime
                            ? formatDateTime(order.aDeliveredTime) +
                              (order.aTrackNo ? ` · 运单号 ${order.aTrackNo}` : '')
                            : '待交付'}
                    </Text>
                </Space>
            ),
        },
        {
            color: order.bDeliveredTime ? 'green' : 'gray',
            children: (
                <Space direction="vertical" size={0}>
                    <Text strong>B 交付 {sideLabel('b')}</Text>
                    <Text type="secondary">
                        {order.bDeliveredTime
                            ? formatDateTime(order.bDeliveredTime) +
                              (order.bTrackNo ? ` · 运单号 ${order.bTrackNo}` : '')
                            : '待交付'}
                    </Text>
                </Space>
            ),
        },
        {
            color: order.aConfirmedTime ? 'green' : 'gray',
            children: (
                <Space direction="vertical" size={0}>
                    <Text strong>A 确认收到 {sideLabel('a')}</Text>
                    <Text type="secondary">
                        {order.aConfirmedTime ? formatDateTime(order.aConfirmedTime) : '待确认'}
                    </Text>
                </Space>
            ),
        },
        {
            color: order.bConfirmedTime ? 'green' : 'gray',
            children: (
                <Space direction="vertical" size={0}>
                    <Text strong>B 确认收到 {sideLabel('b')}</Text>
                    <Text type="secondary">
                        {order.bConfirmedTime ? formatDateTime(order.bConfirmedTime) : '待确认'}
                    </Text>
                </Space>
            ),
        },
    ];

    return (
        <PageContainer header={{ title: `交换单 ${order.orderNo}`, breadcrumb: {} }}>
            <Row gutter={[16, 16]}>
                <Col xs={24} xl={16}>
                    {/* 基本信息 + 对方 */}
                    <Card style={{ marginBottom: 16 }}>
                        <Descriptions column={{ xs: 1, sm: 2 }}>
                            <Descriptions.Item label="订单编号">{order.orderNo}</Descriptions.Item>
                            <Descriptions.Item label="状态">
                                <Tag color={ORDER_STATUS_MAP[order.status]?.color}>
                                    {ORDER_STATUS_MAP[order.status]?.text || order.status}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="交付方式">
                                {DELIVERY_MODE_MAP[order.deliveryMode] || order.deliveryMode}
                            </Descriptions.Item>
                            <Descriptions.Item label="我的角色">
                                {mySide === 'a' ? 'A · 物品主人' : 'B · 换物发起人'}
                            </Descriptions.Item>
                            <Descriptions.Item label="交换物品" span={2}>
                                {mySide === 'a' ? '我的' : '对方'}物品 #{order.itemAId} ⇄{' '}
                                {mySide === 'b' ? '我的' : '对方'}物品 #{order.itemBId}
                                {'　'}
                                <a
                                    onClick={() =>
                                        history.push(`/exchange/detail/${order.itemAId}`)
                                    }
                                >
                                    查看 A 物品
                                </a>
                                {'　'}
                                <a
                                    onClick={() =>
                                        history.push(`/exchange/detail/${order.itemBId}`)
                                    }
                                >
                                    查看 B 物品
                                </a>
                            </Descriptions.Item>
                            <Descriptions.Item label="交换对象">
                                <Space>
                                    <Avatar
                                        size={24}
                                        src={
                                            peerInfo?.avatar
                                                ? peerInfo.avatar.startsWith('http')
                                                    ? peerInfo.avatar
                                                    : undefined
                                                : undefined
                                        }
                                        style={{ background: '#4A6B57' }}
                                    >
                                        {peerName.slice(0, 1)}
                                    </Avatar>
                                    <Text strong>{peerName}</Text>
                                </Space>
                            </Descriptions.Item>
                            <Descriptions.Item label="建立时间">
                                {formatDateTime(order.createTime)}
                            </Descriptions.Item>
                            {order.completeTime && (
                                <Descriptions.Item label="完成时间">
                                    {formatDateTime(order.completeTime)}
                                </Descriptions.Item>
                            )}
                            {order.cancelReason && (
                                <Descriptions.Item label="取消原因" span={2}>
                                    <Text type="danger">{order.cancelReason}</Text>
                                </Descriptions.Item>
                            )}
                            {order.disputeReason && (
                                <Descriptions.Item label="纠纷原因" span={2}>
                                    <Text type="danger">{order.disputeReason}</Text>
                                </Descriptions.Item>
                            )}
                            {order.disputeResult && (
                                <Descriptions.Item label="裁决结果" span={2}>
                                    <Text type="warning">{order.disputeResult}</Text>
                                </Descriptions.Item>
                            )}
                        </Descriptions>
                    </Card>

                    {/* 双边进度时间线 */}
                    <Card title="双边履约进度" style={{ marginBottom: 16 }}>
                        <Timeline items={progressItems} />
                        {order.status === 'completed' && (
                            <Alert
                                message="交换已完成，双方物品已标记换出"
                                type="success"
                                showIcon
                            />
                        )}
                        {order.status === 'cancelled' && (
                            <Alert
                                message="订单已取消，物品已解锁回换物广场"
                                type="info"
                                showIcon
                            />
                        )}
                        {order.status === 'disputed' && (
                            <Alert
                                message="纠纷处理中，平台将尽快裁决；裁决期间物品保持锁定"
                                type="warning"
                                showIcon
                            />
                        )}
                    </Card>

                    {/* 流转日志 */}
                    <Card title="流转记录">
                        {logs.length === 0 ? (
                            <Text type="secondary">暂无记录</Text>
                        ) : (
                            <Timeline
                                items={logs.map((l) => ({
                                    color: ORDER_LOG_ACTION_MAP[l.action]?.color || 'gray',
                                    children: (
                                        <Space direction="vertical" size={0}>
                                            <Space>
                                                <Tag color={ORDER_LOG_ACTION_MAP[l.action]?.color}>
                                                    {ORDER_LOG_ACTION_MAP[l.action]?.label ||
                                                        l.action}
                                                </Tag>
                                                <Text type="secondary">
                                                    {formatDateTime(l.createTime)}
                                                    {l.operatorId
                                                        ? ` · 操作人 ${l.operatorId}`
                                                        : ' · 系统'}
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
                    {/* 面交码 */}
                    {!isExpress && (
                        <Card title="我的交换码（面交核验）" style={{ marginBottom: 16 }}>
                            {myCode ? (
                                <Space
                                    direction="vertical"
                                    align="center"
                                    style={{ width: '100%' }}
                                    size={8}
                                >
                                    <div
                                        style={{
                                            fontSize: 40,
                                            fontWeight: 700,
                                            letterSpacing: 8,
                                            color: '#4A6B57',
                                            fontFamily: 'monospace',
                                        }}
                                    >
                                        {myCode.code}
                                    </div>
                                    <Text type="secondary">
                                        与对方见面时互相出示，核对一致再交付
                                    </Text>
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                        {myCode.used === 1 ? '已使用' : '未使用'}
                                        {myCode.expireAt
                                            ? ` · ${formatDateTime(myCode.expireAt)} 前有效`
                                            : ''}
                                    </Text>
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                        对方（{sideLabel(peerSide)}
                                        ）另持有一个交换码，请线下要求对方出示核对
                                    </Text>
                                </Space>
                            ) : (
                                <Text type="secondary">交换码未生成</Text>
                            )}
                        </Card>
                    )}

                    {/* 操作区 */}
                    <Card title="我的操作" style={{ marginBottom: 16 }}>
                        <Space direction="vertical" style={{ width: '100%' }} size={8}>
                            {canDeliver && !isExpress && (
                                <Button
                                    type="primary"
                                    block
                                    loading={actionLoading}
                                    onClick={handleDeliverFace}
                                >
                                    我已交付（面交）
                                </Button>
                            )}
                            {canDeliver && isExpress && (
                                <>
                                    {myTrackNo && (
                                        <Alert
                                            message={`我的运单号：${myTrackNo}`}
                                            type="info"
                                            showIcon
                                        />
                                    )}
                                    <Button
                                        type="primary"
                                        block
                                        loading={actionLoading}
                                        onClick={handleDeliverExpress}
                                    >
                                        录入运单号并标记交付
                                    </Button>
                                </>
                            )}
                            {myDeliveredTime && isExpress && myTrackNo && (
                                <Alert message={`我的运单号：${myTrackNo}`} type="info" showIcon />
                            )}
                            {myDeliveredTime && !isExpress && (
                                <Alert
                                    message={`我已交付：${formatDateTime(myDeliveredTime)}`}
                                    type="success"
                                    showIcon
                                />
                            )}
                            {peerDeliveredTime && (
                                <Alert
                                    message={`对方（${sideLabel(
                                        peerSide,
                                    )}）已交付：${formatDateTime(peerDeliveredTime)}${
                                        (peerSide === 'a' ? order.aTrackNo : order.bTrackNo)
                                            ? ` · 运单号 ${
                                                  peerSide === 'a' ? order.aTrackNo : order.bTrackNo
                                              }`
                                            : ''
                                    }`}
                                    type="info"
                                    showIcon
                                />
                            )}
                            {canConfirm && (
                                <Button
                                    type="primary"
                                    block
                                    loading={actionLoading}
                                    onClick={handleConfirm}
                                >
                                    我已收到，确认完成
                                </Button>
                            )}
                            {order.status === 'pending_confirm' && myConfirmedTime && (
                                <Alert
                                    message={`我已确认收到：${formatDateTime(
                                        myConfirmedTime,
                                    )}，等待对方确认`}
                                    type="success"
                                    showIcon
                                />
                            )}
                            {canCancel && (
                                <Button danger block onClick={handleCancel}>
                                    取消交换
                                </Button>
                            )}
                            {canDispute && (
                                <Button danger block onClick={() => setDisputeOpen(true)}>
                                    发起纠纷
                                </Button>
                            )}
                            {canReview && (
                                <Button type="primary" block onClick={() => setReviewOpen(true)}>
                                    评价对方
                                </Button>
                            )}
                            {order.status === 'created' && !canCancel && (
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                    单方已交付后不可直接取消，如有问题请发起纠纷由平台裁决。
                                </Text>
                            )}
                        </Space>
                    </Card>

                    {/* 双向评价 */}
                    {(myReview || peerReview || order.status === 'completed') && (
                        <Card title="双向评价" style={{ marginBottom: 16 }} size="small">
                            {myReview && (
                                <Space
                                    direction="vertical"
                                    size={2}
                                    style={{ width: '100%', marginBottom: 8 }}
                                >
                                    <Text type="secondary">我的评价</Text>
                                    <Rate
                                        disabled
                                        value={myReview.score}
                                        style={{ fontSize: 14 }}
                                    />
                                    {myReview.content && <Text>{myReview.content}</Text>}
                                </Space>
                            )}
                            {peerReview && (
                                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                                    <Text type="secondary">对方的评价</Text>
                                    <Rate
                                        disabled
                                        value={peerReview.score}
                                        style={{ fontSize: 14 }}
                                    />
                                    {peerReview.content && <Text>{peerReview.content}</Text>}
                                </Space>
                            )}
                            {!myReview && !peerReview && order.status === 'completed' && (
                                <Text type="secondary">
                                    交换已完成，快去评价对方吧（14 天内有效）
                                </Text>
                            )}
                        </Card>
                    )}
                </Col>
            </Row>

            {/* 纠纷弹窗 */}
            <Modal
                title="发起纠纷"
                open={disputeOpen}
                confirmLoading={actionLoading}
                onOk={submitDispute}
                onCancel={() => setDisputeOpen(false)}
                okText="提交纠纷"
            >
                <Alert
                    message="发起后订单进入纠纷处理，物品保持锁定，由平台后台裁决（按现状完成 / 取消并解锁）"
                    type="warning"
                    showIcon
                    style={{ marginBottom: 12 }}
                />
                <Input.TextArea
                    rows={4}
                    maxLength={200}
                    showCount
                    placeholder="请描述纠纷原因（如：对方未交付 / 物品与描述不符）"
                    value={disputeReason}
                    onChange={(e) => setDisputeReason(e.target.value)}
                />
            </Modal>

            {/* 评价弹窗 */}
            <Modal
                title="评价本次交换"
                open={reviewOpen}
                confirmLoading={actionLoading}
                onOk={submitReview}
                onCancel={() => setReviewOpen(false)}
                okText="提交评价"
            >
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <div>
                        <Text>对方 {peerName} 的表现：</Text>
                        <Rate value={reviewScore} onChange={setReviewScore} />
                    </div>
                    <Input.TextArea
                        rows={3}
                        maxLength={500}
                        placeholder="评语（可选，最多 500 字）"
                        value={reviewContent}
                        onChange={(e) => setReviewContent(e.target.value)}
                    />
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default OrderDetail;
