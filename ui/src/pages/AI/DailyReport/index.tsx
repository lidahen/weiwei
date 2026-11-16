import { createStyles } from 'antd-style';
import {
    ArrowLeftOutlined,
    LoadingOutlined,
    PauseCircleOutlined,
    SoundOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Empty, message, Modal, Pagination, Spin, Tag, Tooltip } from 'antd';
import Markdown from 'react-markdown';
import React, { useEffect, useRef, useState } from 'react';
import { request, useModel } from '@umijs/max';
import { system } from '@/utils/twelvet';
import { generateDailyReport, getDailyReport, listDailyReports } from './service';

const PAGE_SIZE = 6;

const SOURCE_MAP: Record<string, { text: string; color: string }> = {
    llm: { text: 'AI 生成', color: 'processing' },
    fallback: { text: '模板兜底', color: 'default' },
};

const useStyles = createStyles(({ token, css }) => {
    return {
        header: css`
            border-radius: ${token.borderRadiusLG}px;
            background: linear-gradient(120deg, ${token.colorPrimary} 0%, #6d8a77 100%);
            color: #fff;
            padding: 24px 28px;
            margin-bottom: 16px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
            row-gap: 10px;
            @media (max-width: 767px) {
                padding: 18px 16px;
            }
        `,
        headerTitle: css`
            font-size: 20px;
            font-weight: 600;
            margin: 0 0 4px;
        `,
        headerDesc: css`
            margin: 0;
            opacity: 0.85;
            font-size: 13px;
        `,
        reportCard: css`
            border-radius: ${token.borderRadiusLG}px;
            cursor: pointer;
            transition: box-shadow 0.2s, transform 0.2s;
            &:hover {
                box-shadow: ${token.boxShadowTertiary};
                transform: translateY(-2px);
            }
        `,
        cardTitle: css`
            font-size: 15px;
            font-weight: 600;
            margin: 8px 0 6px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        `,
        cardExcerpt: css`
            font-size: 13px;
            color: ${token.colorTextSecondary};
            line-height: 1.7;
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
            min-height: 66px;
        `,
        detailContent: css`
            font-size: 14px;
            line-height: 1.9;
            h2 {
                font-size: 16px;
                font-weight: 600;
                margin: 20px 0 8px;
                padding-left: 10px;
                border-left: 3px solid ${token.colorPrimary};
            }
            p {
                margin: 6px 0;
                color: ${token.colorText};
            }
            li {
                margin: 4px 0;
            }
        `,
    };
});

/** 去掉 Markdown 标记取摘要（与后端 IM 摘要口径一致） */
const excerpt = (content: string, len = 90) =>
    (content || '')
        .replace(/^#{1,6}\s*/gm, '')
        .replace(/[*_`>-]/g, '')
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, len);

const DailyReportPage: React.FC = () => {
    const { styles } = useStyles();
    const { initialState } = useModel('@@initialState');

    const [loading, setLoading] = useState(false);
    const [reports, setReports] = useState<Record<string, any>[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(1);
    // 当前查看的日报详情（点击卡片进入全文，null 返回列表）
    const [detail, setDetail] = useState<Record<string, any> | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [generating, setGenerating] = useState(false);
    // 晨间广播（语音播报）状态：idle / loading / playing
    const [audioState, setAudioState] = useState<'idle' | 'loading' | 'playing'>('idle');
    const audioRef = useRef<HTMLAudioElement | null>(null);

    // 切换详情/卸载时停止播放并释放音频
    useEffect(() => {
        audioRef.current?.pause();
        audioRef.current = null;
        setAudioState('idle');
    }, [detail?.id]);

    /** 播放/停止 AI 主播晨间播报（按需合成，带鉴权头取回 blob 再播放） */
    const playBroadcast = async () => {
        if (audioState === 'playing') {
            audioRef.current?.pause();
            audioRef.current = null;
            setAudioState('idle');
            return;
        }
        if (!detail?.id) return;
        try {
            setAudioState('loading');
            const blob: Blob = await request(`/ai/report/${detail.id}/audio`, {
                method: 'get',
                responseType: 'blob',
                skipErrorHandler: true,
            });
            if (blob.type.includes('audio')) {
                const audio = new Audio(URL.createObjectURL(blob));
                audio.onended = () => {
                    setAudioState('idle');
                    URL.revokeObjectURL(audio.src);
                };
                audioRef.current = audio;
                setAudioState('playing');
                await audio.play();
            } else {
                // TTS 未配置/上游失败：后端返回 JSON 错误
                const res = JSON.parse(await blob.text());
                message.error(res.msg || '语音播报失败');
                setAudioState('idle');
            }
        } catch (e) {
            system.error(e);
            setAudioState('idle');
        }
    };

    // 管理员判定：权限含 ai:report:generate；后端超管标识是 "*"（历史代码里还写过 "*:*:*"，一并兼容）
    const permissions: string[] = (initialState as any)?.permissions || [];
    const canGenerate = permissions.includes('ai:report:generate')
        || permissions.includes('*')
        || permissions.includes('*:*:*');

    const loadReports = async (nextPage = page) => {
        try {
            setLoading(true);
            const { code, msg, data } = await listDailyReports({ current: nextPage, pageSize: PAGE_SIZE });
            if (code !== 200) {
                return message.error(msg || '获取日报失败');
            }
            setReports(data?.records || []);
            setTotal(data?.total || 0);
            setPage(nextPage);
        } catch (e) {
            system.error(e);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadReports(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    /** 点击卡片查看全文 */
    const openDetail = async (id: number) => {
        try {
            setDetailLoading(true);
            const { code, msg, data } = await getDailyReport(id);
            if (code !== 200) {
                return message.error(msg || '获取日报详情失败');
            }
            setDetail(data);
        } catch (e) {
            system.error(e);
        } finally {
            setDetailLoading(false);
        }
    };

    /** 立即生成/覆盖当日日报（幂等，后端同日覆盖更新） */
    const handleGenerate = () => {
        Modal.confirm({
            title: '立即生成今日日报？',
            content: '将聚合最新数据重新生成当日日报并推送给全体成员（当天已生成则覆盖更新）。',
            okText: '立即生成',
            cancelText: '取消',
            onOk: async () => {
                try {
                    setGenerating(true);
                    const { code, msg, data } = await generateDailyReport();
                    if (code !== 200) {
                        return message.error(msg || '生成失败');
                    }
                    message.success(msg || '日报已生成');
                    if (data?.id) {
                        await loadReports(1);
                        await openDetail(data.id);
                    } else {
                        await loadReports(1);
                    }
                } catch (e) {
                    system.error(e);
                } finally {
                    setGenerating(false);
                }
            },
        });
    };

    const renderList = () => (
        <>
            <div
                className={styles.header}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
            >
                <div>
                    <h2 className={styles.headerTitle}>社区日报</h2>
                    <p className={styles.headerDesc}>
                        每天早上 7 点，AI 小编为你汇总社区的新鲜事，也会通过站内消息提醒你
                    </p>
                </div>
                {canGenerate && (
                    <Tooltip title="聚合最新数据重新生成当日日报（当天已生成则覆盖）">
                        <Button
                            type="primary"
                            ghost
                            icon={<ThunderboltOutlined />}
                            loading={generating}
                            onClick={handleGenerate}
                            style={{ background: 'rgba(255,255,255,0.12)', borderColor: '#fff', color: '#fff' }}
                        >
                            立即生成
                        </Button>
                    </Tooltip>
                )}
            </div>
            <Spin spinning={loading}>
                {reports.length === 0 ? (
                    <Card bordered={false} style={{ borderRadius: 12 }}>
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={canGenerate ? '还没有日报，点击右上角「立即生成」创建第一份' : '还没有日报'}
                        />
                    </Card>
                ) : (
                    <>
                        {reports.map((r) => {
                            const source = SOURCE_MAP[r.source] || { text: r.source, color: 'default' };
                            return (
                                <Card
                                    key={r.id}
                                    className={styles.reportCard}
                                    bordered
                                    style={{ borderRadius: 12, marginBottom: 12 }}
                                    styles={{ header: { whiteSpace: 'normal', height: 'auto', minHeight: 57 } }}
                                    onClick={() => openDetail(r.id)}
                                    title={
                                        <span>
                                            <Tag color="green">{r.reportDate}</Tag>
                                            <span style={{ marginLeft: 4 }}>{r.title}</span>
                                        </span>
                                    }
                                    extra={<Tag color={source.color}>{source.text}</Tag>}
                                >
                                    <div className={styles.cardExcerpt}>{excerpt(r.content)}</div>
                                    <div style={{ marginTop: 8, fontSize: 12, color: '#999' }}>
                                        点击查看全文 →
                                    </div>
                                </Card>
                            );
                        })}
                        {total > PAGE_SIZE && (
                            <div style={{ textAlign: 'right', marginTop: 8 }}>
                                <Pagination
                                    simple
                                    current={page}
                                    pageSize={PAGE_SIZE}
                                    total={total}
                                    onChange={(p) => loadReports(p)}
                                />
                            </div>
                        )}
                    </>
                )}
            </Spin>
        </>
    );

    const renderDetail = () => (
        <Spin spinning={detailLoading}>
            <Button
                type="link"
                icon={<ArrowLeftOutlined />}
                onClick={() => setDetail(null)}
                style={{ paddingLeft: 0, marginBottom: 4 }}
            >
                返回列表
            </Button>
            <Card
                bordered={false}
                style={{ borderRadius: 12 }}
                styles={{ header: { whiteSpace: 'normal', height: 'auto', minHeight: 57 } }}
                title={
                    <span>
                        <Tag color="green">{detail?.reportDate}</Tag>
                        <span style={{ marginLeft: 4 }}>{detail?.title}</span>
                    </span>
                }
                extra={
                    detail?.source && (
                        <Tag color={(SOURCE_MAP[detail.source] || { color: 'default' }).color}>
                            {(SOURCE_MAP[detail.source] || { text: detail.source }).text}
                        </Tag>
                    )
                }
            >
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        marginBottom: 12,
                        padding: '10px 14px',
                        background: '#F5F8F4',
                        borderRadius: 8,
                    }}
                >
                    <Button
                        shape="circle"
                        icon={
                            audioState === 'loading' ? (
                                <LoadingOutlined />
                            ) : audioState === 'playing' ? (
                                <PauseCircleOutlined />
                            ) : (
                                <SoundOutlined />
                            )
                        }
                        onClick={playBroadcast}
                        style={{ background: '#4A6B57', borderColor: '#4A6B57', color: '#fff' }}
                        title={audioState === 'playing' ? '停止播报' : '播放晨间广播'}
                    />
                    <div>
                        <div style={{ fontWeight: 600, fontSize: 14 }}>AI 主播 · 晨间广播</div>
                        <div style={{ fontSize: 12, color: '#999' }}>
                            {audioState === 'playing' ? '正在播报，点击左侧按钮停止' : '点击收听今日社区播报'}
                        </div>
                    </div>
                </div>
                <div className={styles.detailContent}>
                    <Markdown>{detail?.content || ''}</Markdown>
                </div>
            </Card>
        </Spin>
    );

    return <PageContainer header={{ title: null, breadcrumb: {} }}>{detail ? renderDetail() : renderList()}</PageContainer>;
};

export default DailyReportPage;
