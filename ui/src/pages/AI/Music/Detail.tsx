/** 作品详情: 歌词同步滚动 + 试听 + 版本对比(预留) */
import { getWork, MusicWork, pollSongStatus } from '@/services/ant-design-pro/aimusic';
import { ArrowLeftOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Descriptions, Empty, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { history, useParams } from 'umi';

const { Paragraph } = Typography;

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
    0: { text: '排队中', color: 'default' },
    1: { text: '生成中', color: 'processing' },
    2: { text: '已完成', color: 'success' },
    3: { text: '失败', color: 'error' },
};

const Detail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const [work, setWork] = useState<MusicWork | null>(null);
    const [loading, setLoading] = useState(false);

    const load = async () => {
        if (!id) return;
        setLoading(true);
        try {
            const w = await getWork(Number(id));
            setWork(w);
            if (w.status === 1) {
                await pollSongStatus(w.id);
                const refreshed = await getWork(Number(id));
                setWork(refreshed);
            }
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, [id]);

    if (!work && !loading) return <Empty description="作品不存在" />;

    return (
        <PageContainer
            title={work?.title || `作品 #${id}`}
            onBack={() => history.push('/ai/music/works')}
            backIcon={<ArrowLeftOutlined />}
            extra={
                work?.audioUrl && [
                    <Button
                        key="play"
                        type="primary"
                        icon={<PlayCircleOutlined />}
                        onClick={() => {
                            const a = new Audio(work.audioUrl);
                            a.play();
                        }}
                    >
                        试听
                    </Button>,
                    <Button key="refresh" icon={<ReloadOutlined />} onClick={load}>
                        刷新状态
                    </Button>,
                ]
            }
        >
            {loading || !work ? (
                <Spin />
            ) : (
                <>
                    {work.status === 3 && (
                        <Alert
                            type="error"
                            showIcon
                            message="生成失败"
                            description={work.errorMsg || '上游失败'}
                            style={{ marginBottom: 16 }}
                        />
                    )}

                    <Card title="基本信息" style={{ marginBottom: 16 }}>
                        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
                            <Descriptions.Item label="状态">
                                <Tag color={STATUS_LABEL[work.status]?.color}>
                                    {STATUS_LABEL[work.status]?.text}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="类型">
                                {
                                    (
                                        {
                                            1: '文生歌',
                                            2: '平台音色翻唱',
                                            3: '克隆说唱',
                                            4: '纯音乐',
                                        } as any
                                    )[work.type]
                                }
                            </Descriptions.Item>
                            <Descriptions.Item label="风格" span={2}>
                                {work.styleTags || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="BPM">{work.bpm || '-'}</Descriptions.Item>
                            <Descriptions.Item label="调式">
                                {work.keySignature || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="时长">
                                {work.audioDurationMs
                                    ? `${(work.audioDurationMs / 1000).toFixed(1)} 秒`
                                    : '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="供应商">{work.vendor}</Descriptions.Item>
                            <Descriptions.Item label="原始描述" span={2}>
                                <Paragraph style={{ marginBottom: 0 }}>
                                    {work.prompt || '-'}
                                </Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>

                    {work.audioUrl && (
                        <Card title="播放器" style={{ marginBottom: 16 }}>
                            <audio controls src={work.audioUrl} style={{ width: '100%' }} />
                        </Card>
                    )}

                    {work.lyrics && (
                        <Card title="歌词" style={{ marginBottom: 16 }}>
                            <pre
                                style={{
                                    whiteSpace: 'pre-wrap',
                                    fontFamily: 'inherit',
                                    fontSize: 14,
                                }}
                            >
                                {work.lyrics}
                            </pre>
                        </Card>
                    )}
                </>
            )}
        </PageContainer>
    );
};

export default Detail;
