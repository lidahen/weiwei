import { useIsMobile } from '@/hooks/useIsMobile';
import {
    CheckCircleOutlined,
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    PlusOutlined,
    ReloadOutlined,
    ScissorOutlined,
    ThunderboltOutlined,
    UploadOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
    Button,
    Card,
    Empty,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Radio,
    Select,
    Space,
    Tag,
    Typography,
    Upload,
} from 'antd';
import { createStyles } from 'antd-style';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    AiModelOption,
    AiPersona,
    AiProject,
    AiViralTemplate,
    createProject,
    decompose,
    decomposeUpload,
    getProject,
    getTemplate,
    listCreationModels,
    listPersonas,
    listProjects,
    removeProjects,
    renderProject,
} from './service';

const STATUS_MAP: Record<string, { text: string; color: string }> = {
    draft: { text: '草稿', color: 'default' },
    storyboard_ready: { text: '分镜待渲染', color: 'processing' },
    generating: { text: '生成中', color: 'processing' },
    composing: { text: '合成中', color: 'processing' },
    done: { text: '已完成', color: 'success' },
    failed: { text: '失败', color: 'error' },
};

const SOURCE_LABELS: Record<string, string> = {
    viral_clone: '爆款复刻',
    original: '自创',
    image_text: '图文',
};

// 拆解上传兜底入口的大小限制（与 Java 层 multipart 上限、Python 层硬上限对齐留有余量）
const MAX_UPLOAD_MB = 200;
const UPLOAD_ACCEPT = ['video/mp4', 'video/quicktime', 'video/webm', 'video/x-m4v'];

const useStyles = createStyles(({ token, css }) => ({
    headerCard: css`
        margin-bottom: 16px;
        border-radius: ${token.borderRadiusLG}px;
    `,
    projectCard: css`
        .ant-card-cover {
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            background: #000;
            border-radius: ${token.borderRadiusLG}px ${token.borderRadiusLG}px 0 0;
            aspect-ratio: 9/12;
        }
    `,
}));

const ProjectList: React.FC = () => {
    const { styles } = useStyles();
    const isMobile = useIsMobile();
    const [projects, setProjects] = useState<AiProject[]>([]);
    const [personas, setPersonas] = useState<AiPersona[]>([]);
    const [llmModels, setLlmModels] = useState<AiModelOption[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalOpen, setModalOpen] = useState(false);
    const [mode, setMode] = useState<'viral_clone' | 'original'>('viral_clone');
    const [template, setTemplate] = useState<AiViralTemplate | null>(null);
    const [templateLoading, setTemplateLoading] = useState(false);
    const [creating, setCreating] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [form] = Form.useForm();
    const pollRef = useRef<any>(null);

    const loadProjects = useCallback(async () => {
        setLoading(true);
        try {
            const res = await listProjects();
            if (res?.code === 200) {
                setProjects(res.data?.rows || []);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    const loadPersonas = useCallback(async () => {
        const res = await listPersonas();
        if (res?.code === 200) {
            setPersonas(res.data?.rows || []);
        }
    }, []);

    const loadLlmModels = useCallback(async () => {
        const res = await listCreationModels('LLM,TEXT');
        if (res?.code === 200) {
            // 客户端再过滤一道，防止代理链参数丢失导致混入 IMAGE/VIDEO 模型
            const rows = (res.data?.rows || []).filter((m) =>
                ['LLM', 'TEXT'].includes(m.model_type),
            );
            // 默认模型排前面，其余按类型分组展示
            rows.sort((a, b) => (b.default_flag || 0) - (a.default_flag || 0));
            setLlmModels(rows);
        }
    }, []);

    useEffect(() => {
        loadProjects();
        loadPersonas();
        return () => clearInterval(pollRef.current);
    }, [loadProjects, loadPersonas]);

    // 打开新建弹窗时拉取可选 LLM 模型列表
    useEffect(() => {
        if (modalOpen) {
            loadLlmModels();
        }
    }, [modalOpen, loadLlmModels]);

    // 存在生成中/合成中的作品时自动刷新列表，无需手动点「刷新」
    const hasActive = projects.some((p) => ['generating', 'composing'].includes(p.status));
    useEffect(() => {
        if (!hasActive) {
            return;
        }
        const timer = setInterval(loadProjects, 8000);
        return () => clearInterval(timer);
    }, [hasActive, loadProjects]);

    const stopTemplatePolling = useCallback(() => {
        clearInterval(pollRef.current);
        pollRef.current = null;
        setTemplateLoading(false);
    }, []);

    const pollTemplate = useCallback(
        (templateId: number) => {
            clearInterval(pollRef.current);
            setTemplateLoading(true);
            let failures = 0;
            const startedAt = Date.now();
            pollRef.current = setInterval(async () => {
                try {
                    const res = await getTemplate(templateId);
                    failures = 0;
                    if (res?.code !== 200) {
                        return;
                    }
                    const t = res.data;
                    if (t?.status === 'ready') {
                        stopTemplatePolling();
                        setTemplate(t);
                        message.success('拆解完成，可基于该结构模板创建作品');
                    } else if (t?.status === 'failed') {
                        stopTemplatePolling();
                        message.error(t?.error_msg || '拆解失败，可尝试上传视频方式');
                    } else if (Date.now() - startedAt > 10 * 60_000) {
                        stopTemplatePolling();
                        message.warning('拆解耗时过长已停止轮询，请稍后在模板库查看结果');
                    }
                } catch {
                    failures += 1;
                    if (failures >= 3) {
                        stopTemplatePolling();
                        message.error('拆解状态查询失败，请稍后重试');
                    }
                }
            }, 3000);
        },
        [stopTemplatePolling],
    );

    const doDecompose = useCallback(async () => {
        const sourceUrl = form.getFieldValue('source_url');
        if (!sourceUrl) {
            message.warning('请粘贴抖音分享链接');
            return;
        }
        const res = await decompose({ source_url: sourceUrl.trim() });
        if (res?.code === 200) {
            message.info('拆解任务已提交，通常需要 1-3 分钟');
            pollTemplate(res.data!.template_id);
        } else {
            message.error(res?.msg || '提交失败');
        }
    }, [form, pollTemplate]);

    const doUploadDecompose = useCallback(
        async (file: File) => {
            const fd = new FormData();
            fd.append('file', file);
            setUploading(true);
            try {
                const res = await decomposeUpload(fd);
                if (res?.code === 200) {
                    message.info('上传成功，拆解中');
                    pollTemplate(res.data!.template_id);
                } else {
                    message.error(res?.msg || '上传失败');
                }
            } finally {
                setUploading(false);
            }
        },
        [pollTemplate],
    );

    const beforeUpload = useCallback(
        (file: File) => {
            if (file.type && !UPLOAD_ACCEPT.includes(file.type)) {
                message.error('仅支持 mp4 / mov / webm 视频文件');
                return Upload.LIST_IGNORE;
            }
            if (file.size > MAX_UPLOAD_MB * 1024 * 1024) {
                message.error(`视频大小不能超过 ${MAX_UPLOAD_MB}MB`);
                return Upload.LIST_IGNORE;
            }
            void doUploadDecompose(file);
            return false;
        },
        [doUploadDecompose],
    );

    const doCreate = useCallback(async () => {
        const topic = form.getFieldValue('topic');
        if (!topic || !topic.trim()) {
            message.warning('请填写选题/主题描述');
            return;
        }
        setCreating(true);
        try {
            const res = await createProject({
                source_type: mode,
                template_id: template?.template_id,
                persona_id: form.getFieldValue('persona_id'),
                topic: topic.trim(),
                llm_model_id: form.getFieldValue('llm_model_id') || undefined,
            });
            if (res?.code === 200) {
                message.success('脚本与分镜已生成');
                // 清掉拆解轮询与旧模板，避免下次新建无意带上 template_id
                stopTemplatePolling();
                setTemplate(null);
                setModalOpen(false);
                form.resetFields();
                history.push(`/ai/creation/editor/${res.data!.project_id}`);
            } else {
                message.error(res?.msg || '创建失败');
            }
        } finally {
            setCreating(false);
        }
    }, [form, mode, stopTemplatePolling, template]);

    const doRender = useCallback(
        async (projectId: number) => {
            const res = await renderProject(projectId);
            if (res?.code === 200) {
                message.info('渲染已启动');
                loadProjects();
            } else {
                message.error(res?.msg || '启动失败');
            }
        },
        [loadProjects],
    );

    const doDownload = useCallback(async (projectId: number) => {
        const res = await getProject(projectId);
        if (res?.code === 200 && res.data?.final_video_url) {
            window.open(res.data.final_video_url, '_blank');
        } else {
            message.warning('成片尚未生成');
        }
    }, []);

    const doRemove = useCallback(
        async (projectId: number) => {
            const res = await removeProjects(String(projectId));
            if (res?.code === 200) {
                message.success('已删除');
                loadProjects();
            } else {
                message.error(res?.msg || '删除失败');
            }
        },
        [loadProjects],
    );

    return (
        <PageContainer title={false}>
            <Card className={styles.headerCard}>
                <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Space direction="vertical" size={0}>
                        <Typography.Title level={4} style={{ margin: 0 }}>
                            <ThunderboltOutlined /> AI 创作工作台
                        </Typography.Title>
                        <Typography.Text type="secondary">
                            复刻爆款 / 自创短视频：拆解 → 脚本 → 分镜 → AI 生图生视频 → 合成成片
                        </Typography.Text>
                    </Space>
                    <Space>
                        <Button icon={<ReloadOutlined />} onClick={loadProjects}>
                            刷新
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => {
                                setTemplate(null);
                                setModalOpen(true);
                            }}
                        >
                            新建作品
                        </Button>
                    </Space>
                </Space>
            </Card>

            {!loading && projects.length === 0 ? (
                <Empty description="还没有作品，点右上角「新建作品」开始" />
            ) : (
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                        gap: 16,
                    }}
                >
                    {projects.map((p) => {
                        const st = STATUS_MAP[p.status] || { text: p.status, color: 'default' };
                        return (
                            <Card
                                key={p.project_id}
                                hoverable
                                className={styles.projectCard}
                                cover={
                                    p.cover_url ? (
                                        <img alt={p.title} src={p.cover_url} loading="lazy" />
                                    ) : (
                                        <ScissorOutlined style={{ fontSize: 42, color: '#666' }} />
                                    )
                                }
                                actions={[
                                    <EditOutlined
                                        key="edit"
                                        onClick={() =>
                                            history.push(`/ai/creation/editor/${p.project_id}`)
                                        }
                                    />,
                                    p.status === 'done' ? (
                                        <DownloadOutlined
                                            key="dl"
                                            onClick={() => doDownload(p.project_id)}
                                        />
                                    ) : (
                                        <CheckCircleOutlined
                                            key="render"
                                            style={{ color: '#999' }}
                                            onClick={() => doRender(p.project_id)}
                                        />
                                    ),
                                    <Popconfirm
                                        key="del"
                                        title="确认删除该作品？"
                                        onConfirm={() => doRemove(p.project_id)}
                                    >
                                        <DeleteOutlined />
                                    </Popconfirm>,
                                ]}
                            >
                                <Card.Meta
                                    title={p.title || `作品 #${p.project_id}`}
                                    description={
                                        <Space direction="vertical" size={4}>
                                            <Space wrap>
                                                <Tag color={st.color}>{st.text}</Tag>
                                                <Tag>
                                                    {SOURCE_LABELS[p.source_type] || p.source_type}
                                                </Tag>
                                            </Space>
                                            {p.error_msg && (
                                                <Typography.Text type="danger" ellipsis>
                                                    {p.error_msg}
                                                </Typography.Text>
                                            )}
                                        </Space>
                                    }
                                />
                            </Card>
                        );
                    })}
                </div>
            )}

            <Modal
                title="新建作品"
                open={modalOpen}
                onCancel={() => {
                    // 中途关闭时停止拆解轮询：否则 loading 卡死 + 旧模板残留到下次新建
                    stopTemplatePolling();
                    setTemplate(null);
                    setModalOpen(false);
                }}
                footer={null}
                width={isMobile ? '94%' : 640}
            >
                <Radio.Group
                    value={mode}
                    onChange={(e) => setMode(e.target.value)}
                    style={{ marginBottom: 16 }}
                >
                    <Radio.Button value="viral_clone">复刻爆款</Radio.Button>
                    <Radio.Button value="original">自创</Radio.Button>
                </Radio.Group>

                {mode === 'viral_clone' && (
                    <Card size="small" style={{ marginBottom: 16 }}>
                        {template ? (
                            <Space direction="vertical" size={4} style={{ width: '100%' }}>
                                <Typography.Text strong>
                                    <CheckCircleOutlined /> 模板：{template.title || '未命名爆款'}
                                </Typography.Text>
                                <Typography.Text type="secondary">
                                    钩子类型：{template.hook_type || '-'} · 镜头数：
                                    {template.shot_count || '-'} · 时长：
                                    {template.duration_sec || '-'}s
                                </Typography.Text>
                            </Space>
                        ) : (
                            <Space direction="vertical" style={{ width: '100%' }}>
                                <Space.Compact style={{ width: '100%' }}>
                                    <Input
                                        placeholder="粘贴抖音分享链接（复制链接得到的长链接）"
                                        onPressEnter={doDecompose}
                                        disabled={templateLoading}
                                    />
                                    <Button
                                        type="primary"
                                        onClick={doDecompose}
                                        loading={templateLoading}
                                    >
                                        拆解
                                    </Button>
                                </Space.Compact>
                                <Upload
                                    accept={UPLOAD_ACCEPT.join(',')}
                                    showUploadList={false}
                                    beforeUpload={beforeUpload}
                                >
                                    <Button
                                        size="small"
                                        icon={<UploadOutlined />}
                                        loading={uploading}
                                    >
                                        {uploading ? '上传中…' : '或上传视频文件拆解'}
                                    </Button>
                                </Upload>
                            </Space>
                        )}
                    </Card>
                )}

                <Form form={form} layout="vertical">
                    <Form.Item
                        name="topic"
                        label="选题 / 主题描述"
                        rules={[{ required: true, message: '请描述这条视频要讲什么' }]}
                    >
                        <Input.TextArea
                            rows={3}
                            placeholder={
                                mode === 'viral_clone'
                                    ? '如：同款结构讲「新手做抖音最容易踩的 3 个坑」'
                                    : '如：分享一个普通人用 AI 一天剪 10 条视频的工作流'
                            }
                        />
                    </Form.Item>
                    <Form.Item name="persona_id" label="使用人设（可选）">
                        <Select
                            allowClear
                            placeholder="选择账号人设（风格样例/音色/定妆照）"
                            options={personas.map((p) => ({ label: p.name, value: p.persona_id }))}
                        />
                    </Form.Item>
                    <Form.Item name="llm_model_id" label="写稿 LLM 模型（可选）">
                        <Select
                            allowClear
                            placeholder="默认使用模型管理中的默认文本模型（限流时可换）"
                            options={[
                                {
                                    label: 'Agnes 直连',
                                    options: llmModels
                                        .filter((m) => m.model_type === 'LLM')
                                        .map((m) => ({
                                            label: `${m.model_name}${
                                                m.default_flag ? '（默认）' : ''
                                            }`,
                                            value: m.model_id,
                                        })),
                                },
                                {
                                    label: '商汤聚合',
                                    options: llmModels
                                        .filter((m) => m.model_type === 'TEXT')
                                        .map((m) => ({
                                            label: m.model_name,
                                            value: m.model_id,
                                        })),
                                },
                            ]}
                        />
                    </Form.Item>
                    <Button type="primary" block loading={creating} onClick={doCreate}>
                        {creating ? '正在生成口播稿与分镜（约 20-60s）…' : '生成脚本与分镜'}
                    </Button>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default ProjectList;
