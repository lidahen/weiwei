/** 我的作品 + 一键文生歌创作入口 */
import ModelSelect from '@/components/ModelSelect';
import {
    deleteWork,
    draftLyrics,
    listMyWorks,
    listPlatformVoices,
    LyricsItem,
    MusicWork,
    PlatformVoice,
    pollSongStatus,
    submitSong,
} from '@/services/ant-design-pro/aimusic';
import { DeleteOutlined, PlayCircleOutlined, PlusOutlined, SoundOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Alert,
    Button,
    Card,
    Col,
    Empty,
    Form,
    Image,
    Input,
    message,
    Modal,
    Popconfirm,
    Radio,
    Row,
    Segmented,
    Select,
    Space,
    Tag,
    Typography,
} from 'antd';
import { useEffect, useState } from 'react';
import { history } from 'umi';

const { Text } = Typography;

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
    0: { text: '排队中', color: 'default' },
    1: { text: '生成中', color: 'processing' },
    2: { text: '已完成', color: 'success' },
    3: { text: '失败', color: 'error' },
};

const TYPE_LABEL: Record<number, string> = {
    1: '文生歌',
    2: '平台音色翻唱',
    3: '克隆说唱',
    4: '纯音乐',
};

const GENDERS = [
    { label: '不限', value: 'any' },
    { label: '男声', value: 'male' },
    { label: '女声', value: 'female' },
    { label: '对唱', value: 'duet' },
];

const STYLE_OPTIONS = [
    '温暖流行',
    '治愈民谣',
    '电子舞曲',
    '中国风',
    '摇滚',
    'R&B',
    '说唱',
    '古风',
    '校园民谣',
    '伤感抒情',
];

const Works: React.FC = () => {
    const [list, setList] = useState<MusicWork[]>([]);
    const [loading, setLoading] = useState(false);
    const [statusFilter, setStatusFilter] = useState<number | undefined>();
    const [createOpen, setCreateOpen] = useState(
        () => new URLSearchParams(window.location.search).get('action') === 'create',
    );
    const [platformVoices, setPlatformVoices] = useState<PlatformVoice[]>([]);
    const [lyricCandidates, setLyricCandidates] = useState<LyricsItem[]>([]);
    const [drafting, setDrafting] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();
    const type = Form.useWatch('type', form) as string | undefined;
    const style = Form.useWatch('style', form) as string | undefined;

    const load = async () => {
        setLoading(true);
        try {
            const res = await listMyWorks({ status: statusFilter });
            setList(res.records ?? []);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
        // 商汤 voice_type: system(系统预置) / voice_clone(用户克隆) / voice_generation / all
        // 这里要的是平台预置音色，传 voice_clone 会拿到空列表（账号未克隆过音色）
        listPlatformVoices('all')
            .then((r) => setPlatformVoices(r.voices))
            .catch(() => {});
    }, [statusFilter]);

    // 自动轮询生成中的作品
    useEffect(() => {
        const pending = list.filter((w) => w.status === 1);
        if (pending.length === 0) return;
        const timer = setInterval(async () => {
            await Promise.allSettled(pending.map((w) => pollSongStatus(w.id)));
            load();
        }, 5000);
        return () => clearInterval(timer);
    }, [list]);

    const onDraftLyrics = async () => {
        const prompt = form.getFieldValue('prompt');
        if (!prompt) {
            message.warning('请先填写主题/描述');
            return;
        }
        setDrafting(true);
        try {
            const res = await draftLyrics({
                prompt,
                style: style || undefined,
                expect_duration: form.getFieldValue('expectDuration'),
            });
            setLyricCandidates(res.candidates);
            if (res.candidates.length > 0 && res.candidates[0].lyrics) {
                form.setFieldsValue({ lyrics: res.candidates[0].lyrics });
                message.success('已生成歌词,可在编辑框中修改');
            }
        } catch (e: any) {
            message.error('写词失败: ' + (e?.data?.msg || e.message));
        } finally {
            setDrafting(false);
        }
    };

    const onSubmit = async (vals: any) => {
        if (vals.type === 'platform_cover' && !vals.voiceId) {
            message.warning('请选择平台音色');
            return;
        }
        setSubmitting(true);
        try {
            await submitSong({
                type: vals.type,
                prompt:
                    vals.type === 'text_to_song' || vals.type === 'instrumental'
                        ? vals.prompt
                        : undefined,
                style: vals.style,
                lyrics: vals.lyrics,
                voiceId: vals.voiceId,
                gender: vals.gender,
                expectDuration: vals.expectDuration,
                title: vals.title,
                styleTags: vals.styleTags,
                modelId: vals.modelId,
            });
            message.success('已提交,生成中,请稍候');
            setCreateOpen(false);
            form.resetFields();
            setLyricCandidates([]);
            setStatusFilter(undefined);
            load();
        } catch (e: any) {
            message.error('提交失败: ' + (e?.data?.msg || e.message));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <PageContainer
            title="我的作品"
            extra={[
                <Button
                    key="create"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => setCreateOpen(true)}
                >
                    一键文生歌
                </Button>,
            ]}
        >
            <Card style={{ marginBottom: 16 }}>
                <Segmented
                    value={statusFilter}
                    onChange={(v) => setStatusFilter(v as number | undefined)}
                    options={[
                        { label: '全部', value: undefined as any },
                        { label: '生成中', value: 1 },
                        { label: '已完成', value: 2 },
                        { label: '失败', value: 3 },
                    ]}
                />
            </Card>

            {list.length === 0 && !loading ? (
                <Empty description="还没有作品,点击右上角创建吧" />
            ) : (
                <Row gutter={[16, 16]}>
                    {list.map((w) => (
                        <Col xs={12} sm={8} lg={6} key={w.id}>
                            <Card
                                hoverable
                                loading={loading}
                                cover={
                                    w.coverUrl ? (
                                        <Image
                                            src={w.coverUrl}
                                            preview={false}
                                            height={160}
                                            style={{ objectFit: 'cover' }}
                                        />
                                    ) : (
                                        <div
                                            style={{
                                                height: 160,
                                                background: '#f0f0f0',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                            }}
                                        >
                                            <SoundOutlined
                                                style={{ fontSize: 48, color: '#bbb' }}
                                            />
                                        </div>
                                    )
                                }
                                actions={
                                    [
                                        w.audioUrl ? (
                                            <Button
                                                key="play"
                                                type="link"
                                                icon={<PlayCircleOutlined />}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    const a = new Audio(w.audioUrl);
                                                    a.play();
                                                }}
                                            >
                                                试听
                                            </Button>
                                        ) : null,
                                        <Popconfirm
                                            key="del"
                                            title="确认删除?"
                                            onConfirm={async (e) => {
                                                e?.stopPropagation();
                                                await deleteWork(w.id);
                                                message.success('已删除');
                                                load();
                                            }}
                                            onCancel={(e) => e?.stopPropagation()}
                                        >
                                            <Button
                                                type="link"
                                                danger
                                                icon={<DeleteOutlined />}
                                                onClick={(e) => e.stopPropagation()}
                                            >
                                                删除
                                            </Button>
                                        </Popconfirm>,
                                    ].filter(Boolean) as React.ReactNode[]
                                }
                                onClick={() => history.push(`/ai/music/works/${w.id}`)}
                            >
                                <Card.Meta
                                    title={w.title || `作品 #${w.id}`}
                                    description={
                                        <Space direction="vertical" size={2}>
                                            <Space>
                                                <Tag>{TYPE_LABEL[w.type]}</Tag>
                                                <Tag color={STATUS_LABEL[w.status]?.color}>
                                                    {STATUS_LABEL[w.status]?.text}
                                                </Tag>
                                            </Space>
                                            {w.audioDurationMs ? (
                                                <Text type="secondary">
                                                    {(w.audioDurationMs / 1000).toFixed(0)}s
                                                </Text>
                                            ) : null}
                                        </Space>
                                    }
                                />
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}

            <Modal
                title="创建歌曲"
                open={createOpen}
                onCancel={() => setCreateOpen(false)}
                onOk={() => form.submit()}
                confirmLoading={submitting}
                width={680}
                okText="提交生成"
            >
                <Alert
                    type="info"
                    showIcon
                    message="先写主题 → AI 生成歌词 → 选风格/音色 → 提交生成。预计 1~3 分钟完成。"
                    style={{ marginBottom: 16 }}
                />
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onSubmit}
                    initialValues={{ type: 'text_to_song', gender: 'any' }}
                >
                    <Form.Item label="创作类型" name="type" rules={[{ required: true }]}>
                        <Radio.Group style={{ display: 'flex', flexWrap: 'wrap', rowGap: 8 }}>
                            <Radio.Button value="text_to_song">文生歌</Radio.Button>
                            <Radio.Button value="platform_cover">平台音色翻唱</Radio.Button>
                            <Radio.Button value="instrumental">纯音乐</Radio.Button>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item
                        label="主题/描述"
                        name="prompt"
                        rules={[{ required: true, max: 500 }]}
                    >
                        <Input.TextArea
                            rows={2}
                            placeholder="例如:夏日海边告别的青春记忆,温暖但带着淡淡忧伤"
                        />
                    </Form.Item>

                    <Row gutter={12}>
                        <Col xs={24} sm={12}>
                            <Form.Item label="风格" name="style">
                                <Select
                                    allowClear
                                    showSearch
                                    options={STYLE_OPTIONS.map((s) => ({ label: s, value: s }))}
                                    placeholder="选择风格(可选)"
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={12} sm={6}>
                            <Form.Item label="人声" name="gender">
                                <Select options={GENDERS} />
                            </Form.Item>
                        </Col>
                        <Col xs={12} sm={6}>
                            <Form.Item label="时长(秒)" name="expectDuration">
                                <Select
                                    allowClear
                                    options={[
                                        { label: '30s', value: 30 },
                                        { label: '1 分钟', value: 60 },
                                        { label: '2 分钟', value: 120 },
                                        { label: '3 分钟', value: 180 },
                                    ]}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    {type === 'platform_cover' && (
                        <Form.Item label="选择平台音色" name="voiceId" rules={[{ required: true }]}>
                            <Select
                                placeholder="选择一个平台音色"
                                options={platformVoices.map((v) => ({
                                    label: `${v.voice_name} · ${v.description?.join('/') || ''}`,
                                    value: v.voice_id,
                                }))}
                            />
                        </Form.Item>
                    )}

                    <Form.Item
                        label="生成模型"
                        name="modelId"
                        tooltip="默认走推荐模型, 可选其他模型对比效果"
                    >
                        <ModelSelect modelType="MUSIC" />
                    </Form.Item>

                    <Form.Item label="歌词" name="lyrics">
                        <Input.TextArea
                            rows={6}
                            placeholder="点击下方'AI 写词'自动生成,或自行粘贴"
                        />
                    </Form.Item>

                    {lyricCandidates.length > 1 && (
                        <Form.Item label="AI 候选歌词">
                            <Space wrap>
                                {lyricCandidates.map((c, i) => (
                                    <Button
                                        key={i}
                                        size="small"
                                        onClick={() => form.setFieldsValue({ lyrics: c.lyrics })}
                                    >
                                        候选 {i + 1}: {c.title || '无题'}
                                    </Button>
                                ))}
                            </Space>
                        </Form.Item>
                    )}

                    <Button icon={<SoundOutlined />} onClick={onDraftLyrics} loading={drafting}>
                        AI 写词
                    </Button>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default Works;
