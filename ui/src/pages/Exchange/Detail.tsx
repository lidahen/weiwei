import {
    categoryTree,
    itemDetail,
    itemMyItems,
    itemWanters,
    offerAccept,
    offerCreate,
    offerReceived,
    offerReject,
    userCredit,
} from '@/services/exchange/api';
import TWT from '@/setting';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import { isLoggedIn, requireLogin } from '@/utils/loginGuard';
import { buildBreadcrumbJsonLd, resolveSiteUrl } from '@/utils/seo';
import { history, useModel, useParams, useRequest } from '@umijs/max';
import {
    Alert,
    Avatar,
    Button,
    Card,
    Col,
    Descriptions,
    Empty,
    Image,
    Input,
    InputNumber,
    Modal,
    Radio,
    Rate,
    Row,
    Select,
    Space,
    Spin,
    Statistic,
    Tag,
    Typography,
    message,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import {
    CONDITION_MAP,
    DELIVERY_MODE_MAP,
    ITEM_STATUS_MAP,
    OFFER_STATUS_MAP,
    formatDateTime,
    formatPrice,
    parseImageList,
    parseWantCats,
    shouldPrefetchMyItems,
} from './utils';

const { Text, Title } = Typography;
const { TextArea } = Input;

// 信用分徽标配色（<40 为二期发起意向门槛 MIN_CREDIT_TO_OFFER，标红；>=80 优秀标绿）
const creditTagColor = (score: number): string =>
    score >= 80 ? 'green' : score >= 60 ? 'blue' : score >= 40 ? 'orange' : 'red';

/**
 * 换物物品详情
 * - 浏览者视角：大图 + 描述 + 期望交换 + 主人摘要 +【发起交换】（选我的 onswap 物品或文字快照）
 * - 主人视角：额外显示该物品收到的意向列表（逐条 接受/婉拒）
 */
const Detail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const { initialState } = useModel('@@initialState');
    const myId = Number((initialState as any)?.currentUser?.user?.userId);
    const loggedIn = isLoggedIn();

    // ===== 发起交换弹窗状态 =====
    const [offerOpen, setOfferOpen] = useState(false);
    const [offerMode, setOfferMode] = useState<'item' | 'snapshot'>('item');
    const [offerItemId, setOfferItemId] = useState<number | undefined>(undefined);
    const [offerSnapshot, setOfferSnapshot] = useState('');
    const [offerMessage, setOfferMessage] = useState('');
    const [offerDiff, setOfferDiff] = useState<number | undefined>(undefined);
    const [offerSubmitting, setOfferSubmitting] = useState(false);

    const { data: detailResp, loading } = useRequest(
        () => (id ? itemDetail(id) : Promise.resolve(null)),
        { refreshDeps: [id] },
    );
    const { data: catResp } = useRequest(() => categoryTree(), { refreshDeps: [] });
    // 我在换物中的物品（发起交换弹窗的候选）
    const { data: myItemsResp, run: fetchMyItems } = useRequest(
        () => itemMyItems({ status: 'onswap' }),
        { manual: true },
    );
    // 主人视角：收到的意向（前端按 itemId 过滤出本物品的）
    const { data: receivedResp, refresh: refreshReceived } = useRequest(
        () => (loggedIn ? offerReceived() : Promise.resolve(null)),
        { refreshDeps: [id, loggedIn] },
    );
    // 可能想换 TA 的人（向量反向召回需求侧物品；空态/降级整块隐藏）
    const { data: wantersResp } = useRequest(
        () => (loggedIn && id ? itemWanters(id, { limit: 6 }) : Promise.resolve(null)),
        { refreshDeps: [id, loggedIn] },
    );
    const wanters: any[] = useMemo(
        () => ((wantersResp as any)?.data ?? wantersResp ?? []) as any[],
        [wantersResp],
    );

    // 每个需求方主人的信用分徽标（逐个取实时信用分，失败静默不展示，绝不阻塞详情主流程）
    const [wanterCredits, setWanterCredits] = useState<Record<number, number>>({});
    useEffect(() => {
        const ownerIds = Array.from(
            new Set(
                wanters
                    .map((w: any) => Number(w.ownerId))
                    .filter((n) => Number.isFinite(n) && n > 0),
            ),
        );
        setWanterCredits({});
        if (ownerIds.length === 0) return;
        let cancelled = false;
        Promise.all(
            ownerIds.map((uid) =>
                userCredit(uid)
                    .then((r: any): [number, any] => [uid, (r?.data ?? r)?.creditScore])
                    .catch((): [number, null] => [uid, null]),
            ),
        ).then((pairs) => {
            if (cancelled) return;
            const map: Record<number, number> = {};
            pairs.forEach(([uid, score]) => {
                if (score === null || score === undefined) return;
                const s = Number(score);
                if (!Number.isNaN(s)) map[uid] = s;
            });
            setWanterCredits(map);
        });
        return () => {
            cancelled = true;
        };
    }, [wanters]);

    const d: any = (detailResp as any)?.data ?? detailResp;
    const item: any = d?.item;
    const owner: any = d?.owner;
    const isMine = !!item && Number(item.ownerId) === myId;

    // 我发出的物品候选：仅登录用户浏览他人换物中的物品时预取（游客跳过，见 shouldPrefetchMyItems）
    useEffect(() => {
        if (item && shouldPrefetchMyItems({ loggedIn, isMine, status: item.status })) {
            fetchMyItems();
        }
    }, [item?.id, isMine, item?.status, loggedIn]);

    if (loading) return <Spin style={{ display: 'block', margin: '60px auto' }} />;
    if (!item) {
        return (
            <PageContainer header={{ title: '物品详情', breadcrumb: {} }}>
                {/* 软 404 必须 noindex，否则不存在的换物页会被当成有效页收录 */}
                <Seo title="物品不存在" canonicalPath={`/exchange/detail/${id}`} noindex />
                <Alert message="物品不存在或无权查看" type="error" showIcon />
            </PageContainer>
        );
    }

    const cats: any[] = (catResp as any)?.data ?? catResp ?? [];
    const catLabel = (code: string) => cats.find((c: any) => c.code === code)?.name || code;
    const wantCatCodes = parseWantCats(item.wantCats);
    const imgs =
        parseImageList(item.images).length > 0
            ? parseImageList(item.images)
            : item.cover
            ? [item.cover]
            : [];
    const abs = (p: string) => (p.startsWith('http') ? p : `${TWT.static}${p}`);

    // 主人视角：本物品收到的意向
    const offers: any[] = (((receivedResp as any)?.data ?? receivedResp ?? []) as any[]).filter(
        (o) => Number(o.itemId) === Number(id),
    );

    const myItems: any[] = (myItemsResp as any)?.data ?? myItemsResp ?? [];

    // ===== 发起交换 =====
    const openOfferModal = () => {
        if (!requireLogin()) return;
        setOfferMode(myItems.length > 0 ? 'item' : 'snapshot');
        setOfferItemId(undefined);
        setOfferSnapshot('');
        setOfferMessage('');
        setOfferDiff(undefined);
        setOfferOpen(true);
    };

    const submitOffer = async () => {
        if (offerMode === 'item' && !offerItemId) {
            message.warning('请选择一件您的换物中物品');
            return;
        }
        if (offerMode === 'snapshot' && !offerSnapshot.trim()) {
            message.warning('请描述您想用来交换的物品');
            return;
        }
        setOfferSubmitting(true);
        try {
            await offerCreate({
                itemId: Number(id),
                offerItemId: offerMode === 'item' ? offerItemId : null,
                offerSnapshot: offerMode === 'snapshot' ? offerSnapshot.trim() : null,
                message: offerMessage.trim() || null,
                diffAmount: offerDiff ?? null,
            });
            message.success('换物意向已发起，72 小时内有效，请留意对方回复');
            setOfferOpen(false);
            history.push('/exchange/offers');
        } catch (e: any) {
            message.error(e?.msg || e?.message || '发起意向失败');
        } finally {
            setOfferSubmitting(false);
        }
    };

    // ===== 主人视角处理意向 =====
    const handleAccept = async (offerId: number) => {
        try {
            const r: any = await offerAccept(offerId);
            const data = r?.data ?? r;
            message.success('已接受，交换订单已建立');
            if (data?.orderId) {
                history.push(`/exchange/order-detail/${data.orderId}`);
            } else {
                refreshReceived();
            }
        } catch (e: any) {
            message.error(e?.msg || e?.message || '接受失败');
            refreshReceived();
        }
    };

    const handleReject = (offerId: number) => {
        Modal.confirm({
            title: '婉拒该意向',
            content: '婉拒后将通知对方，对方可继续发起其他意向。',
            okText: '婉拒',
            okButtonProps: { danger: true },
            onOk: async () => {
                try {
                    await offerReject(offerId);
                    message.success('已婉拒');
                    refreshReceived();
                } catch (e: any) {
                    message.error(e?.msg || e?.message || '操作失败');
                }
            },
        });
    };

    const renderOfferList = () => (
        <Card title={`收到的换物意向（${offers.length}）`} style={{ marginTop: 16 }}>
            {offers.length === 0 ? (
                <Empty description="暂无意向" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    {offers.map((o) => (
                        <div
                            key={o.id}
                            style={{
                                border: '1px solid #EAEAE8',
                                borderRadius: 8,
                                padding: 12,
                            }}
                        >
                            <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
                                <Space wrap>
                                    <Tag color={OFFER_STATUS_MAP[o.status]?.color}>
                                        {OFFER_STATUS_MAP[o.status]?.text || o.status}
                                    </Tag>
                                    <Text strong>发起人 ID：{o.offerorId}</Text>
                                    {o.diffAmount !== null && o.diffAmount !== undefined && (
                                        <Tag color="orange">
                                            愿补差价 {formatPrice(o.diffAmount)}
                                        </Tag>
                                    )}
                                </Space>
                                {o.status === 'pending' && (
                                    <Space>
                                        <Button
                                            type="primary"
                                            size="small"
                                            onClick={() => handleAccept(o.id)}
                                        >
                                            接受
                                        </Button>
                                        <Button
                                            size="small"
                                            danger
                                            onClick={() => handleReject(o.id)}
                                        >
                                            婉拒
                                        </Button>
                                    </Space>
                                )}
                            </Space>
                            {/* 对方出价：快照描述 / 库内物品链接 */}
                            <div style={{ marginTop: 8 }}>
                                {o.offerSnapshot ? (
                                    <Text>对方物品：{o.offerSnapshot}</Text>
                                ) : o.offerItemId ? (
                                    <a
                                        onClick={() =>
                                            history.push(`/exchange/detail/${o.offerItemId}`)
                                        }
                                    >
                                        查看对方物品（#{o.offerItemId}）
                                    </a>
                                ) : (
                                    <Text type="secondary">对方未附物品</Text>
                                )}
                            </div>
                            {o.message && (
                                <div style={{ marginTop: 4 }}>
                                    <Text type="secondary">留言：{o.message}</Text>
                                </div>
                            )}
                            <div style={{ marginTop: 4 }}>
                                <Text type="secondary">
                                    发起于 {formatDateTime(o.createTime)}
                                    {o.status === 'pending' &&
                                        ` · ${formatDateTime(o.expireTime)} 前有效`}
                                </Text>
                            </div>
                        </div>
                    ))}
                </Space>
            )}
        </Card>
    );

    return (
        <PageContainer header={{ title: item.title, breadcrumb: {} }}>
            <Seo
                title={item.title || '换物详情'}
                description={item.description || `邻里二手换物：${item.title || ''}`}
                canonicalPath={`/exchange/detail/${id}`}
                noindex={item.status !== 'onswap'}
                jsonLd={buildBreadcrumbJsonLd(
                    [
                        { name: '首页', path: '/home' },
                        { name: '交换广场', path: '/exchange/square' },
                        { name: item.title || '换物详情', path: `/exchange/detail/${id}` },
                    ],
                    resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin),
                )}
            />
            <Row gutter={[16, 16]}>
                <Col xs={24} xl={16}>
                    <Card style={{ marginBottom: 16 }}>
                        {imgs.length > 0 ? (
                            <Image.PreviewGroup>
                                <Image
                                    src={abs(imgs[0])}
                                    alt={item.title}
                                    style={{ maxHeight: 420, objectFit: 'cover', borderRadius: 8 }}
                                />
                                {imgs.length > 1 && (
                                    <Space wrap style={{ marginTop: 8 }}>
                                        {imgs.slice(1).map((p: string, i: number) => (
                                            <Image
                                                key={i}
                                                src={abs(p)}
                                                width={80}
                                                height={80}
                                                style={{ objectFit: 'cover', borderRadius: 6 }}
                                            />
                                        ))}
                                    </Space>
                                )}
                            </Image.PreviewGroup>
                        ) : (
                            <div
                                style={{
                                    height: 240,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: 48,
                                    background: '#F5F1E8',
                                    borderRadius: 8,
                                }}
                            >
                                🎁
                            </div>
                        )}
                        {/* D10：内容区恰好一个 h1（level=1 渲染 h1，字号保持原视觉大小） */}
                        <Title
                            level={1}
                            style={{ marginTop: 16, marginBottom: 8, fontSize: 22, fontWeight: 600 }}
                        >
                            {item.title}
                        </Title>
                        <Space wrap style={{ marginBottom: 12 }}>
                            <Tag color={ITEM_STATUS_MAP[item.status]?.color}>
                                {ITEM_STATUS_MAP[item.status]?.text || item.status}
                            </Tag>
                            <Tag color="green">
                                {CONDITION_MAP[item.conditionLevel] || item.conditionLevel}
                            </Tag>
                            <Tag>{DELIVERY_MODE_MAP[item.deliveryMode] || item.deliveryMode}</Tag>
                            {item.catL1 && (
                                <Tag>
                                    {catLabel(item.catL1)}
                                    {item.catL2 ? ` / ${catLabel(item.catL2)}` : ''}
                                </Tag>
                            )}
                        </Space>
                        <Descriptions column={1} size="small" style={{ marginBottom: 8 }}>
                            <Descriptions.Item label="自估参考价值">
                                {item.estValue !== null && item.estValue !== undefined ? (
                                    <span
                                        style={{ color: '#FF6B35', fontWeight: 600, fontSize: 16 }}
                                    >
                                        {formatPrice(item.estValue)}
                                    </span>
                                ) : (
                                    <Text type="secondary">
                                        未填写（仅展示参考，不构成交易价格）
                                    </Text>
                                )}
                            </Descriptions.Item>
                            {(item.city || item.location) && (
                                <Descriptions.Item label="面交区域">
                                    📍 {[item.city, item.location].filter(Boolean).join(' ')}
                                </Descriptions.Item>
                            )}
                            <Descriptions.Item label="浏览 / 意向">
                                {item.viewCount ?? 0} 次浏览 · {d?.offerCount ?? 0} 条意向
                            </Descriptions.Item>
                            {item.publishTime && (
                                <Descriptions.Item label="上架时间">
                                    {formatDateTime(item.publishTime)}
                                </Descriptions.Item>
                            )}
                        </Descriptions>

                        {/* 期望交换 */}
                        <Card type="inner" title="想换什么" style={{ marginTop: 8 }}>
                            <Space direction="vertical" size={4} style={{ width: '100%' }}>
                                <Space wrap>
                                    {wantCatCodes.length > 0 ? (
                                        wantCatCodes.map((c) => (
                                            <Tag key={c} color="green">
                                                {catLabel(c)}
                                            </Tag>
                                        ))
                                    ) : (
                                        <Tag>不限类目</Tag>
                                    )}
                                    {item.acceptAny === 1 && <Tag color="orange">接受任意洽谈</Tag>}
                                </Space>
                                {item.wantDesc && <Text>{item.wantDesc}</Text>}
                            </Space>
                        </Card>

                        {item.description && (
                            <Card type="inner" title="物品描述" style={{ marginTop: 12 }}>
                                <Text style={{ whiteSpace: 'pre-wrap' }}>{item.description}</Text>
                            </Card>
                        )}

                        {item.status === 'rejected' && item.rejectReason && (
                            <Alert
                                message={`审核驳回：${item.rejectReason}`}
                                type="error"
                                showIcon
                                style={{ marginTop: 12 }}
                            />
                        )}

                        {/* 浏览者视角：发起交换 */}
                        {!isMine && item.status === 'onswap' && (
                            <Button
                                type="primary"
                                size="large"
                                block
                                style={{ marginTop: 16 }}
                                onClick={openOfferModal}
                            >
                                发起交换
                            </Button>
                        )}
                        {!isMine && item.status !== 'onswap' && (
                            <Alert
                                message="该物品当前不在换物中，暂不能发起交换"
                                type="info"
                                showIcon
                                style={{ marginTop: 16 }}
                            />
                        )}
                    </Card>

                    {/* 主人视角：收到的意向列表 */}
                    {isMine && renderOfferList()}
                </Col>

                <Col xs={24} xl={8}>
                    {/* 主人换物主页摘要 */}
                    <Card title="主人换物主页" style={{ marginBottom: 16 }} size="small">
                        <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Space>
                                <Avatar style={{ background: '#4A6B57' }}>换</Avatar>
                                <Text strong>用户 {owner?.userId ?? item.ownerId}</Text>
                                {owner?.ownerCredit !== null &&
                                    owner?.ownerCredit !== undefined && (
                                        <Tag
                                            color={creditTagColor(Number(owner.ownerCredit))}
                                            style={{ marginRight: 0 }}
                                        >
                                            信用分 {owner.ownerCredit}
                                        </Tag>
                                    )}
                            </Space>
                            <Row gutter={8} style={{ marginTop: 8 }}>
                                <Col span={8}>
                                    <Statistic title="累计换出" value={owner?.swapCount ?? 0} />
                                </Col>
                                <Col span={8}>
                                    <Statistic title="被评次数" value={owner?.reviewCount ?? 0} />
                                </Col>
                                <Col span={8}>
                                    <Statistic
                                        title="平均评分"
                                        value={
                                            owner?.avgScore !== null &&
                                            owner?.avgScore !== undefined
                                                ? Number(owner.avgScore)
                                                : '-'
                                        }
                                        suffix={
                                            owner?.avgScore !== null &&
                                            owner?.avgScore !== undefined
                                                ? '/5'
                                                : ''
                                        }
                                    />
                                </Col>
                            </Row>
                            {owner?.avgScore !== null && owner?.avgScore !== undefined && (
                                <Rate
                                    disabled
                                    allowHalf
                                    value={Number(owner.avgScore)}
                                    style={{ fontSize: 14 }}
                                />
                            )}
                        </Space>
                    </Card>

                    {/* 可能想换 TA 的人（向量反向召回，空态/降级整块隐藏；点击跳对方物品详情形成以物找人闭环） */}
                    {wanters.length > 0 && (
                        <Card title="可能想换 TA 的人" size="small" style={{ marginBottom: 16 }}>
                            <Space direction="vertical" size={8} style={{ width: '100%' }}>
                                {wanters.map((w: any) => {
                                    const credit = wanterCredits[Number(w.ownerId)];
                                    return (
                                        <div
                                            key={w.id}
                                            style={{
                                                border: '1px solid #EAEAE8',
                                                borderRadius: 8,
                                                padding: 8,
                                                cursor: 'pointer',
                                            }}
                                            onClick={() =>
                                                history.push(`/exchange/detail/${w.id}`)
                                            }
                                        >
                                            <Text strong style={{ fontSize: 13 }}>
                                                {w.title}
                                            </Text>
                                            <div
                                                style={{
                                                    marginTop: 4,
                                                    display: 'flex',
                                                    flexWrap: 'wrap',
                                                    alignItems: 'center',
                                                    gap: 4,
                                                }}
                                            >
                                                <Tag color="green" style={{ marginRight: 0 }}>
                                                    {CONDITION_MAP[w.conditionLevel] ||
                                                        w.conditionLevel}
                                                </Tag>
                                                <Tag style={{ marginRight: 0 }}>
                                                    {w.ownerName || `用户 ${w.ownerId}`}
                                                </Tag>
                                                {credit !== undefined && (
                                                    <Tag
                                                        color={creditTagColor(credit)}
                                                        style={{ marginRight: 0 }}
                                                    >
                                                        信用 {credit}
                                                    </Tag>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </Space>
                        </Card>
                    )}

                    <Card title="交换须知" size="small">
                        <Space direction="vertical" size={4}>
                            <Text type="secondary">
                                · 以物换物无资金托管，建议优先选择面交当面核验
                            </Text>
                            <Text type="secondary">· 面交时双方互相出示 6 位交换码核对身份</Text>
                            <Text type="secondary">· 自估参考价值仅展示，不构成交易价格</Text>
                            <Text type="secondary">· 意向 72 小时内有效，物品主人可接受或婉拒</Text>
                        </Space>
                    </Card>
                </Col>
            </Row>

            {/* 发起交换弹窗 */}
            <Modal
                title={`发起交换：${item.title}`}
                open={offerOpen}
                confirmLoading={offerSubmitting}
                onOk={submitOffer}
                onCancel={() => setOfferOpen(false)}
                okText="发起意向"
                width={520}
            >
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Radio.Group
                        value={offerMode}
                        onChange={(e) => setOfferMode(e.target.value)}
                        optionType="button"
                        buttonStyle="solid"
                    >
                        <Radio.Button value="item">用我的物品换</Radio.Button>
                        <Radio.Button value="snapshot">文字快照描述</Radio.Button>
                    </Radio.Group>

                    {offerMode === 'item' ? (
                        myItems.length > 0 ? (
                            <div>
                                <Text type="secondary">从您的换物中物品里选一件：</Text>
                                <Select
                                    style={{ width: '100%', marginTop: 4 }}
                                    placeholder="选择我的物品"
                                    value={offerItemId}
                                    onChange={setOfferItemId}
                                    options={myItems.map((it: any) => ({
                                        value: it.id,
                                        label: `${it.title}（${
                                            CONDITION_MAP[it.conditionLevel] || it.conditionLevel
                                        }）`,
                                    }))}
                                />
                            </div>
                        ) : (
                            <Alert
                                message="您暂无换物中的物品，请先发布一件，或改用文字快照描述"
                                type="warning"
                                showIcon
                            />
                        )
                    ) : (
                        <div>
                            <Text type="secondary">
                                快速描述您想用来交换的物品（成交前需补发布正式物品）：
                            </Text>
                            <TextArea
                                rows={3}
                                style={{ marginTop: 4 }}
                                maxLength={1000}
                                placeholder="例：小米手环 8 NFC 版，99 新，用了两个月"
                                value={offerSnapshot}
                                onChange={(e) => setOfferSnapshot(e.target.value)}
                            />
                        </div>
                    )}

                    <div>
                        <Text type="secondary">交换留言（可选）：</Text>
                        <TextArea
                            rows={2}
                            style={{ marginTop: 4 }}
                            maxLength={500}
                            placeholder="向对方介绍一下您的诚意"
                            value={offerMessage}
                            onChange={(e) => setOfferMessage(e.target.value)}
                        />
                    </div>

                    <div>
                        <Text type="secondary">愿补差价（可选，仅展示）：</Text>
                        <InputNumber
                            min={0}
                            max={999999}
                            precision={2}
                            prefix="¥"
                            style={{ width: 200, marginTop: 4 }}
                            placeholder="0.00"
                            value={offerDiff}
                            onChange={(v) => setOfferDiff(v ?? undefined)}
                        />
                    </div>
                </Space>
            </Modal>
        </PageContainer>
    );
};

export default Detail;
