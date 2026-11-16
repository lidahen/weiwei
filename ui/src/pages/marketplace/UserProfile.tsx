import ProductCard from '@/components/marketplace/ProductCard';
import { useIsMobile } from '@/hooks/useIsMobile';
import { userFollow, userProfile, userProfileMe, userUnfollow } from '@/services/marketplace/api';
import TWT from '@/setting';
import { MessageOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import Seo from '@/components/Seo';
import SeoHeading from '@/components/Seo/Heading';
import { requireLogin } from '@/utils/loginGuard';
import { buildProfilePageJsonLd, resolveSiteUrl } from '@/utils/seo';
import { history, useParams, useRequest } from '@umijs/max';
import {
    Avatar,
    Button,
    Card,
    Col,
    message,
    Row,
    Space,
    Spin,
    Statistic,
    Tabs,
    Tag,
    Tooltip,
} from 'antd';
import React, { useState } from 'react';
import ReportDialog from '@/components/marketplace/ReportDialog';

/** 信用分等级 → 颜色/说明（与后端 creditLevel 分档一致） */
const CREDIT_LEVEL_STYLE: Record<string, { color: string; desc: string }> = {
    优秀: { color: '#2E7D5B', desc: '评价优秀且履约稳定' },
    良好: { color: '#3B82F6', desc: '整体可靠' },
    一般: { color: '#D48806', desc: '存在个别失信记录' },
    较差: { color: '#CF1322', desc: '失信记录较多，交易请谨慎' },
};

const UserProfile: React.FC = () => {
    const { userId } = useParams<{ userId?: string }>();
    const isMobile = useIsMobile();
    const isMe = !userId;
    const [reportOpen, setReportOpen] = useState(false);

    const { data, loading, refresh } = useRequest(
        () => (isMe ? userProfileMe() : userProfile(userId!)),
        { refreshDeps: [userId] },
    );
    const profile: any = (data as any)?.data ?? (data as any);

    const handleFollow = async () => {
        if (!requireLogin()) return;
        try {
            if (profile.followed) {
                await userUnfollow(profile.userId);
                message.success('已取消关注');
            } else {
                await userFollow(profile.userId);
                message.success('关注成功');
            }
            refresh();
        } catch (e) {
            message.error('操作失败');
        }
    };

    if (loading || !profile)
        return (
            <div style={{ textAlign: 'center', padding: 100 }}>
                <Spin />
            </div>
        );

    const userTypeMap: Record<string, { text: string; color: string }> = {
        '00': { text: '平台运营', color: 'blue' },
        '01': { text: '平台客服', color: 'red' },
        '02': { text: '普通用户', color: 'orange' },
    };

    // 信用分（后端已算好；无评价用户为默认 80，前端兜底同值）
    const creditScore: number = profile.creditScore ?? 80;
    const creditLevel: string = profile.creditLevel || '良好';
    const levelStyle = CREDIT_LEVEL_STYLE[creditLevel] || CREDIT_LEVEL_STYLE['良好'];
    // 扣分明细仅登录用户可见（后端对匿名访客不下发）
    const credit: any = profile.credit;

    return (
        <PageContainer header={{ title: profile.isMe ? '我的主页' : '用户主页', breadcrumb: {} }}>
            <Seo
                title={`${profile.nickName || '用户'} 的闲置`}
                description={profile.signature || '邻里二手用户主页：在售闲置与交易信用。'}
                canonicalPath={`/user/profile/${userId}`}
                noindex
                jsonLd={buildProfilePageJsonLd(
                    profile,
                    resolveSiteUrl(process.env.UMI_APP_SITE_URL, window.location.origin),
                )}
            />
            <SeoHeading>{`${profile.nickName || '用户'} 的闲置`}</SeoHeading>
            <Card>
                <Row
                    gutter={isMobile ? 16 : 32}
                    align={isMobile ? 'top' : 'middle'}
                    justify={isMobile ? 'center' : 'start'}
                >
                    <Col style={isMobile ? { textAlign: 'center', width: '100%' } : undefined}>
                        <Avatar
                            src={profile.avatar ? `${TWT.static}${profile.avatar}` : undefined}
                            size={isMobile ? 72 : 96}
                        />
                    </Col>
                    <Col flex={isMobile ? '100%' : 1}>
                        <h2 style={{ marginBottom: 8, textAlign: isMobile ? 'center' : 'left' }}>
                            {profile.nickName}
                            {profile.userType && (
                                <Tag
                                    color={userTypeMap[profile.userType]?.color || 'default'}
                                    style={{ marginLeft: 12 }}
                                >
                                    {userTypeMap[profile.userType]?.text || profile.userType}
                                </Tag>
                            )}
                        </h2>
                        <div style={{ color: '#666', marginBottom: 12 }}>
                            {profile.signature || '这个人很懒,什么都没留下'}
                        </div>
                        <Space
                            size={isMobile ? 16 : 32}
                            align="start"
                            wrap
                            style={
                                isMobile ? { width: '100%', justifyContent: 'center' } : undefined
                            }
                        >
                            <Statistic
                                title={
                                    <Tooltip
                                        title={`信用分 = 评分×20 ×(0.7+0.3×近30天完成率) − 5×近30天失信单，取 0~100；无评价默认 80。当前：评分 ${
                                            profile.sellerRating ?? '—'
                                        }、${levelStyle.desc}`}
                                    >
                                        <span
                                            style={{
                                                borderBottom: '1px dashed #d9d9d9',
                                                cursor: 'help',
                                            }}
                                        >
                                            信用分
                                        </span>
                                    </Tooltip>
                                }
                                value={creditScore}
                                suffix={
                                    <span style={{ fontSize: 14, color: levelStyle.color }}>
                                        {creditLevel}
                                    </span>
                                }
                                valueStyle={{ color: levelStyle.color, fontWeight: 600 }}
                            />
                            <Statistic title="在售" value={profile.onsaleCount || 0} />
                            <Statistic title="已售" value={profile.soldCount || 0} />
                            <Statistic title="关注" value={profile.followCount || 0} />
                            <Statistic title="粉丝" value={profile.fansCount || 0} />
                            <Statistic
                                title="好评率"
                                value={profile.goodReviewRate || 100}
                                suffix="%"
                            />
                        </Space>
                        {credit && (
                            <div style={{ marginTop: 12, fontSize: 12, color: '#8c8c8c' }}>
                                近 30 天：成交 {credit.paidCount30d || 0} 单 · 完成{' '}
                                {credit.completedCount30d || 0} 单
                                {credit.completionRate30d !== null &&
                                credit.completionRate30d !== undefined
                                    ? `（完成率 ${Math.round(credit.completionRate30d * 100)}%）`
                                    : ''}
                                {' · 失信单 '}
                                <span
                                    style={{
                                        color:
                                            (credit.penalizedCount30d || 0) > 0
                                                ? '#CF1322'
                                                : '#8c8c8c',
                                    }}
                                >
                                    {credit.penalizedCount30d || 0}
                                </span>
                                {(credit.penalizedCount30d || 0) > 0 && credit.penaltyDetail && (
                                    <span>
                                        （退款 {credit.penaltyDetail.refund} · 纠纷{' '}
                                        {credit.penaltyDetail.dispute} · 超时未发货{' '}
                                        {credit.penaltyDetail.overdue}）
                                    </span>
                                )}
                            </div>
                        )}
                    </Col>
                    <Col
                        flex={isMobile ? '100%' : undefined}
                        style={isMobile ? { textAlign: 'center' } : undefined}
                    >
                        {!profile.isMe && (
                            <Space>
                                <Button
                                    type={profile.followed ? 'default' : 'primary'}
                                    onClick={handleFollow}
                                >
                                    {profile.followed ? '已关注' : '关注'}
                                </Button>
                                <Button
                                    icon={<MessageOutlined />}
                                    onClick={() =>
                                        history.push(`/im/chat?peerId=${profile.userId}`)
                                    }
                                >
                                    聊一聊
                                </Button>
                                <Button
                                    danger
                                    onClick={() => {
                                        if (!requireLogin()) return;
                                        setReportOpen(true);
                                    }}
                                >
                                    举报
                                </Button>
                            </Space>
                        )}
                        {profile.isMe && (
                            <Button onClick={() => history.push('/user/settings')}>编辑资料</Button>
                        )}
                    </Col>
                </Row>
            </Card>
            {/* 互助服务入口（复查报告遗留项）：移动端侧栏抽屉层级深，这里给显式宫格入口 */}
            {isMobile && (
                <Card style={{ marginTop: 16 }} styles={{ body: { padding: '12px 0' } }}>
                    <Row justify="space-around">
                        {[
                            { icon: '🤝', label: '互助大厅', path: '/runner/hall' },
                            { icon: '📥', label: '我接的互助', path: '/runner/hall/my-taken' },
                            { icon: '📋', label: '我发的互助', path: '/runner/task/my-list' },
                        ].map((item) => (
                            <Col
                                key={item.path}
                                style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    gap: 6,
                                    cursor: 'pointer',
                                    padding: '8px 16px',
                                }}
                                onClick={() => history.push(item.path)}
                            >
                                <span style={{ fontSize: 28 }}>{item.icon}</span>
                                <span style={{ fontSize: 13, color: '#444' }}>{item.label}</span>
                            </Col>
                        ))}
                    </Row>
                </Card>
            )}
            <Card style={{ marginTop: 16 }}>
                <Tabs
                    items={[
                        {
                            key: 'onsale',
                            label: `在售 ${profile.onsaleCount || 0}`,
                            children:
                                (profile.onsaleProducts || []).length === 0 ? (
                                    <div
                                        style={{ padding: 40, textAlign: 'center', color: '#999' }}
                                    >
                                        暂无在售商品
                                    </div>
                                ) : (
                                    <Row gutter={[16, 16]}>
                                        {(profile.onsaleProducts || []).map((p: any) => (
                                            <Col key={p.productId} xs={12} sm={12} md={8} lg={6}>
                                                <ProductCard
                                                    image={p.cover}
                                                    title={p.title}
                                                    price={p.price}
                                                    originalPrice={p.originalPrice}
                                                    condition={p.condition}
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
                                ),
                        },
                        {
                            key: 'sold',
                            label: `已售 ${profile.soldCount || 0}`,
                            children: (
                                <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>
                                    暂无已售商品
                                </div>
                            ),
                        },
                        {
                            key: 'feed',
                            label: '动态',
                            children: (
                                <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>
                                    该用户还没有发布动态
                                </div>
                            ),
                        },
                    ]}
                />
            </Card>
            <ReportDialog
                open={reportOpen}
                targetType="user"
                targetId={profile?.userId}
                targetLabel={`用户 ${profile?.nickName || ''}`}
                onClose={() => setReportOpen(false)}
            />
        </PageContainer>
    );
};

export default UserProfile;
