import { useIsMobile } from '@/hooks/useIsMobile';
import {
    itemMyItems,
    offerAccept,
    offerReceived,
    offerReject,
    offerSent,
    offerWithdraw,
} from '@/services/exchange/api';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
    Avatar,
    Button,
    Card,
    Col,
    Empty,
    Modal,
    Row,
    Space,
    Spin,
    Tag,
    Typography,
    message,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { OFFER_STATUS_MAP, formatDateTime, formatPrice } from './utils';

const { Text } = Typography;

/**
 * 我的换物意向：收到的（物品主人视角，可接受/婉拒）/ 发出的（可撤回）
 *
 * 说明：后端意向列表只返回意向字段本身，不含物品标题冗余，因此：
 * - 目标物品若是我自己的（收到的意向的 item / 发出意向的 offerItem），用「我的物品」接口映射出标题；
 * - 对方物品仅能以快照文本或物品详情链接呈现（详情页浏览会正常计入浏览数）。
 */
const Offers: React.FC = () => {
    const isMobile = useIsMobile();
    const [tab, setTab] = useState<'received' | 'sent'>('received');
    const [received, setReceived] = useState<any[]>([]);
    const [sent, setSent] = useState<any[]>([]);
    const [myItemMap, setMyItemMap] = useState<Record<number, any>>({});
    const [loading, setLoading] = useState(false);
    const [reloadFlag, setReloadFlag] = useState(0);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                // 我的物品全量（LIMIT 50）→ id → item 映射
                const mineResp: any = await itemMyItems();
                const mine = mineResp?.data ?? mineResp ?? [];
                const map: Record<number, any> = {};
                if (Array.isArray(mine)) {
                    mine.forEach((it: any) => {
                        map[it.id] = it;
                    });
                }
                if (cancelled) return;
                setMyItemMap(map);
                const [recvResp, sentResp] = await Promise.all([offerReceived(), offerSent()]);
                if (cancelled) return;
                const recv = recvResp?.data ?? recvResp ?? [];
                const st = sentResp?.data ?? sentResp ?? [];
                setReceived(Array.isArray(recv) ? recv : []);
                setSent(Array.isArray(st) ? st : []);
            } catch (e: any) {
                message.error(e?.msg || e?.message || '意向加载失败');
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [reloadFlag]);

    const bump = () => setReloadFlag((f) => f + 1);

    const handleAccept = async (offer: any) => {
        try {
            const r: any = await offerAccept(offer.id);
            const data = r?.data ?? r;
            message.success('已接受，交换订单已建立');
            if (data?.orderId) {
                history.push(`/exchange/order-detail/${data.orderId}`);
            } else {
                bump();
            }
        } catch (e: any) {
            message.error(e?.msg || e?.message || '接受失败');
            bump();
        }
    };

    const handleReject = (offer: any) => {
        Modal.confirm({
            title: '婉拒该意向',
            content: '婉拒后将通知对方，对方可继续发起其他意向。',
            okText: '婉拒',
            okButtonProps: { danger: true },
            onOk: async () => {
                try {
                    await offerReject(offer.id);
                    message.success('已婉拒');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const handleWithdraw = (offer: any) => {
        Modal.confirm({
            title: '撤回该意向',
            content: '撤回后对方将不再看到这条意向。',
            onOk: async () => {
                try {
                    await offerWithdraw(offer.id);
                    message.success('已撤回');
                    bump();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const renderOfferCard = (o: any, kind: 'received' | 'sent') => {
        const isReceived = kind === 'received';
        // 目标物品（被申请的物品）：收到的 = 我的物品；发出的 = 对方物品（仅链接）
        const targetItem = isReceived ? myItemMap[o.itemId] : null;
        // 对方出价物品：发出 = 我的物品（若选了库内物品）；收到 = 对方的（仅链接/快照）
        const offerorItem = isReceived ? null : o.offerItemId ? myItemMap[o.offerItemId] : null;
        return (
            <div
                key={o.id}
                style={{
                    border: '1px solid #EAEAE8',
                    borderRadius: 12,
                    padding: 12,
                    marginBottom: 10,
                    background: '#fff',
                }}
            >
                <Row gutter={12} align="top">
                    <Col flex="auto">
                        <Space wrap style={{ marginBottom: 4 }}>
                            <Tag color={OFFER_STATUS_MAP[o.status]?.color}>
                                {OFFER_STATUS_MAP[o.status]?.text || o.status}
                            </Tag>
                            <Text strong>
                                {isReceived ? '我的物品' : '我想换'}：
                                {isReceived ? (
                                    targetItem ? (
                                        <a
                                            onClick={() =>
                                                history.push(`/exchange/detail/${targetItem.id}`)
                                            }
                                        >
                                            {targetItem.title}
                                        </a>
                                    ) : (
                                        `物品 #${o.itemId}`
                                    )
                                ) : (
                                    <a onClick={() => history.push(`/exchange/detail/${o.itemId}`)}>
                                        物品 #{o.itemId}
                                    </a>
                                )}
                            </Text>
                        </Space>
                        <div>
                            <Space wrap align="center">
                                <Avatar size={20} style={{ background: '#4A6B57', fontSize: 11 }}>
                                    {isReceived ? '对' : '我'}
                                </Avatar>
                                <Text>
                                    {isReceived ? '对方物品：' : '我的出价：'}
                                    {isReceived ? (
                                        o.offerSnapshot ? (
                                            o.offerSnapshot
                                        ) : o.offerItemId ? (
                                            <a
                                                onClick={() =>
                                                    history.push(
                                                        `/exchange/detail/${o.offerItemId}`,
                                                    )
                                                }
                                            >
                                                物品 #{o.offerItemId}（点击查看）
                                            </a>
                                        ) : (
                                            '未附物品'
                                        )
                                    ) : offerorItem ? (
                                        <a
                                            onClick={() =>
                                                history.push(`/exchange/detail/${offerorItem.id}`)
                                            }
                                        >
                                            {offerorItem.title}
                                        </a>
                                    ) : (
                                        o.offerSnapshot || '未附物品'
                                    )}
                                </Text>
                            </Space>
                        </div>
                        {o.message && (
                            <div style={{ marginTop: 4 }}>
                                <Text type="secondary">留言：{o.message}</Text>
                            </div>
                        )}
                        <div style={{ marginTop: 4 }}>
                            <Text type="secondary">
                                发起 {formatDateTime(o.createTime)}
                                {o.status === 'pending' &&
                                    ` · ${formatDateTime(o.expireTime)} 前有效`}
                                {o.handleTime && ` · 处理于 ${formatDateTime(o.handleTime)}`}
                                {o.diffAmount !== null &&
                                    o.diffAmount !== undefined &&
                                    ` · 愿补差价 ${formatPrice(o.diffAmount)}`}
                            </Text>
                        </div>
                    </Col>
                    {o.status === 'pending' && (
                        <Col flex="none" style={{ textAlign: 'right' }}>
                            <Space direction="vertical" size={6}>
                                {isReceived ? (
                                    <>
                                        <Button
                                            type="primary"
                                            size="small"
                                            onClick={() => handleAccept(o)}
                                        >
                                            接受
                                        </Button>
                                        <Button size="small" danger onClick={() => handleReject(o)}>
                                            婉拒
                                        </Button>
                                    </>
                                ) : (
                                    <Button size="small" onClick={() => handleWithdraw(o)}>
                                        撤回
                                    </Button>
                                )}
                            </Space>
                        </Col>
                    )}
                </Row>
            </div>
        );
    };

    const list = tab === 'received' ? received : sent;

    const renderList = () => {
        if (loading && list.length === 0) {
            return <Spin style={{ display: 'block', margin: '60px auto' }} />;
        }
        if (list.length === 0) {
            return <Empty description={tab === 'received' ? '暂无收到的意向' : '暂无发出的意向'} />;
        }
        return <div>{list.map((o) => renderOfferCard(o, tab))}</div>;
    };

    return (
        <PageContainer
            header={{
                title: '我的换物意向',
                subTitle: '收到 / 发出的以物换物洽谈',
                breadcrumb: {},
            }}
        >
            <Card>
                {isMobile ? (
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                        <Space>
                            <Button
                                type={tab === 'received' ? 'primary' : 'default'}
                                onClick={() => setTab('received')}
                            >
                                收到的（{received.filter((o) => o.status === 'pending').length}{' '}
                                待处理）
                            </Button>
                            <Button
                                type={tab === 'sent' ? 'primary' : 'default'}
                                onClick={() => setTab('sent')}
                            >
                                发出的
                            </Button>
                        </Space>
                        {renderList()}
                    </Space>
                ) : (
                    <>
                        <Space style={{ marginBottom: 16 }}>
                            <Button
                                type={tab === 'received' ? 'primary' : 'default'}
                                onClick={() => setTab('received')}
                            >
                                收到的（{received.filter((o) => o.status === 'pending').length}{' '}
                                待处理）
                            </Button>
                            <Button
                                type={tab === 'sent' ? 'primary' : 'default'}
                                onClick={() => setTab('sent')}
                            >
                                发出的
                            </Button>
                        </Space>
                        {renderList()}
                    </>
                )}
            </Card>
        </PageContainer>
    );
};

export default Offers;
