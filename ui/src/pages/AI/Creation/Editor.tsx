import { createStyles } from 'antd-style';
import {
    ArrowLeftOutlined,
    AudioOutlined,
    DownloadOutlined,
    FileTextOutlined,
    PictureOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
    SaveOutlined,
    SettingOutlined,
    SwapOutlined,
    VideoCameraOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Button,
    Card,
    Col,
    Descriptions,
    Empty,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Popover,
    Row,
    Select,
    Space,
    Spin,
    Tag,
    Typography,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { history, useParams } from '@umijs/max';
import { useIsMobile } from '@/hooks/useIsMobile';
import {
    createHookVariants,
    getProject,
    listCreationModels,
    publishPackage,
    renderProject,
    retryShot,
    saveStoryboard,
    AiModelOption,
    AiProject,
    AiShot,
} from './service';

const PROJECT_STATUS: Record<string, { text: string; color: string }> = {
    draft: { text: '草稿', color: 'default' },
    storyboard_ready: { text: '分镜待渲染', color: 'processing' },
    generating: { text: '逐镜生成中', color: 'processing' },
    composing: { text: '合成中', color: 'processing' },
    done: { text: '已完成', color: 'success' },
    failed: { text: '失败', color: 'error' },
};

const SHOT_STATUS: Record<string, { text: string; color: string }> = {
    pending: { text: '待生成', color: 'default' },
    image_done: { text: '图完成', color: 'processing' },
    video_done: { text: '视频完成', color: 'processing' },
    audio_done: { text: '配音完成', color: 'processing' },
    ready: { text: '就绪', color: 'success' },
    failed: { text: '失败', color: 'error' },
};

const ROLE_LABELS: Record<string, string> = {
    hook: '钩子',
    body: '正文',
    proof: '证明',
    cta: '号召',
};

// 轮询健壮性：连续失败上限 / 渲染与镜级重试的总超时（逐镜生成 6 镜可达 15 分钟）
const POLL_MAX_FAILURES = 3;
const RENDER_POLL_TIMEOUT_MS = 30 * 60_000;
const RETRY_POLL_TIMEOUT_MS = 10 * 60_000;
// 镜级重试是后台任务，首轮询可能还没看到 shot_status 翻成 pending，给 15s 观察宽限
const RETRY_GRACE_MS = 15_000;

const useStyles = createStyles(({ token, css }) => ({
    shotPreview: css`
        width: 100%;
        aspect-ratio: 9/16;
        object-fit: cover;
        border-radius: ${token.borderRadiusLG}px;
        background: #000;
    `,
    scriptBox: css`
        background: ${token.colorFillQuaternary};
        border-radius: ${token.borderRadiusLG}px;
        padding: 12px 16px;
        white-space: pre-wrap;
        font-size: 13px;
        line-height: 1.8;
    `,
}));

const CreationEditor: React.FC = () => {
    const { styles } = useStyles();
    const isMobile = useIsMobile();
    const params = useParams();
    const projectId = params.id as string;
    const [project, setProject] = useState<AiProject | null>(null);
    const [shots, setShots] = useState<AiShot[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [reloadTick, setReloadTick] = useState(0);
    const [saving, setSaving] = useState(false);
    const [packaging, setPackaging] = useState(false);
    const [packageData, setPackageData] = useState<AiProject['publish_package'] | null>(null);
    const [variantOpen, setVariantOpen] = useState(false);
    const [variantCount, setVariantCount] = useState(3);
    const [variantLoading, setVariantLoading] = useState(false);
    const [variants, setVariants] = useState<
        { project_id: number; title: string; hook: string; angle: string }[]
    >([]);
    // 渲染模型覆盖（Agnes 限流/不可用时切换商汤聚合模型）
    const [imageModels, setImageModels] = useState<AiModelOption[]>([]);
    const [videoModels, setVideoModels] = useState<AiModelOption[]>([]);
    const [imageModelId, setImageModelId] = useState<number | undefined>();
    const [videoModelId, setVideoModelId] = useState<number | undefined>();
    const pollRef = useRef<any>(null);
    // 用户有未保存编辑时轮询只合并状态类字段，绝不覆盖 text/prompt（受控输入防丢）
    const dirtyRef = useRef(false);

    const load = useCallback(async (): Promise<AiProject | null> => {
        const res = await getProject(projectId);
        if (res?.code === 200 && res.data) {
            setProject(res.data);
            setShots(res.data.shots || []);
            setPackageData(res.data.publish_package || null);
            dirtyRef.current = false;
            return res.data;
        }
        throw new Error(res?.msg || '加载失败');
    }, [projectId]);

    // 合并服务端轮询结果：项目级只取状态类字段，镜级只取产物/状态字段——
    // 台词与 prompt 是受控输入，整包覆盖会把用户未保存的编辑静默打回旧值
    const mergePollResult = useCallback((data: AiProject) => {
        setProject((prev) =>
            prev
                ? {
                      ...prev,
                      status: data.status,
                      error_msg: data.error_msg,
                      final_video_url: data.final_video_url,
                      cover_url: data.cover_url,
                  }
                : data,
        );
        setShots((prevShots) =>
            (data.shots || []).map((srv) => {
                const local = prevShots.find((s) => s.shot_id === srv.shot_id);
                if (!local) {
                    return srv;
                }
                return {
                    ...local,
                    shot_status: srv.shot_status,
                    image_url: srv.image_url,
                    clip_url: srv.clip_url,
                    audio_url: srv.audio_url,
                    audio_duration_ms: srv.audio_duration_ms,
                    error_msg: srv.error_msg,
                };
            }),
        );
    }, []);

    const startPolling = useCallback(
        (mode: 'render' | 'retry' = 'render') => {
            clearInterval(pollRef.current);
            let failures = 0;
            const startedAt = Date.now();
            pollRef.current = setInterval(async () => {
                try {
                    const res = await getProject(projectId);
                    failures = 0;
                    if (res?.code !== 200 || !res.data) {
                        return;
                    }
                    const data = res.data;
                    mergePollResult(data);
                    const shotsActive = (data.shots || []).some(
                        (s) => !['ready', 'failed'].includes(s.shot_status),
                    );
                    const projActive = ['generating', 'composing'].includes(data.status);
                    const timeout =
                        mode === 'render' ? RENDER_POLL_TIMEOUT_MS : RETRY_POLL_TIMEOUT_MS;
                    // 重试模式给后台任务 15s 观察宽限，避免首拍误判「已结束」
                    if (mode === 'retry' && Date.now() - startedAt < RETRY_GRACE_MS) {
                        return;
                    }
                    if (!projActive && !shotsActive) {
                        clearInterval(pollRef.current);
                        const failedShot = (data.shots || []).find((s) => s.shot_status === 'failed');
                        if (mode === 'render') {
                            if (data.status === 'done') {
                                message.success('渲染完成！');
                            } else if (data.status === 'failed') {
                                message.error(data.error_msg || '渲染失败，可镜级重试');
                            }
                        } else if (failedShot) {
                            message.error(`镜级重试失败：${failedShot.error_msg || '详见镜错误信息'}`);
                        } else if (data.status === 'done') {
                            message.success('渲染完成！');
                        } else {
                            message.success('镜级操作完成，改动需重新渲染后进入成片');
                        }
                    } else if (Date.now() - startedAt > timeout) {
                        clearInterval(pollRef.current);
                        message.warning('自动刷新已超时停止，请手动刷新查看最新状态');
                    }
                } catch {
                    failures += 1;
                    if (failures >= POLL_MAX_FAILURES) {
                        clearInterval(pollRef.current);
                        message.error('状态查询连续失败，已停止自动刷新，请手动重试');
                    }
                }
            }, 5000);
        },
        [mergePollResult, projectId],
    );

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setLoadError(null);
        (async () => {
            try {
                const d = await load();
                if (cancelled) {
                    return;
                }
                setLoading(false);
                if (d && ['generating', 'composing'].includes(d.status)) {
                    startPolling('render');
                }
            } catch (e: any) {
                if (cancelled) {
                    return;
                }
                setLoading(false);
                setLoadError(e?.message || '加载失败，请重试');
            }
        })();
        return () => {
            cancelled = true;
            clearInterval(pollRef.current);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projectId, reloadTick]);

    const updateShot = useCallback((shotId: number, patch: Partial<AiShot>) => {
        dirtyRef.current = true;
        setShots((prev) => prev.map((s) => (s.shot_id === shotId ? { ...s, ...patch } : s)));
    }, []);

    const buildStoryboard = useCallback(() => {
        // 项目级 meta/audio/subtitles 保留原值，shots 以当前编辑态覆盖
        const base = project?.storyboard as any;
        return {
            ...base,
            shots: shots.map((s, i) => ({
                index: i + 1,
                role: base?.shots?.[i]?.role || 'body',
                text: s.text,
                mode: s.mode || 'img2vid',
                image_prompt: s.image_prompt,
                video_prompt: s.video_prompt,
                reference_images: base?.shots?.[i]?.reference_images || [],
                transition: base?.shots?.[i]?.transition || 'cut',
            })),
        };
    }, [project, shots]);

    const doSave = useCallback(async (): Promise<boolean> => {
        setSaving(true);
        try {
            const res = await saveStoryboard(projectId, buildStoryboard());
            if (res?.code === 200 && res.data) {
                message.success('分镜已保存');
                setProject(res.data);
                setShots(res.data.shots || []);
                dirtyRef.current = false;
                return true;
            }
            message.error(res?.msg || '保存失败');
            return false;
        } catch {
            message.error('保存失败，请检查网络后重试');
            return false;
        } finally {
            setSaving(false);
        }
    }, [buildStoryboard, projectId]);

    const doRender = useCallback(async () => {
        // 先保存再渲染；保存失败直接中止，否则会用服务端旧分镜渲染
        if (!(await doSave())) {
            return;
        }
        const res = await renderProject(projectId, {
            image_model_id: imageModelId,
            video_model_id: videoModelId,
        });
        if (res?.code === 200) {
            message.info('渲染已启动：逐镜生成 → 合成，请留意状态变化');
            startPolling('render');
        } else {
            message.error(res?.msg || '启动失败');
        }
    }, [doSave, imageModelId, projectId, startPolling, videoModelId]);

    const doRetry = useCallback(
        async (shotId: number, stage: string) => {
            // 重试前先保存：后端配音/生视频按库里已存的台词执行，不保存会按旧词生成
            if (dirtyRef.current && !(await doSave())) {
                return;
            }
            const res = await retryShot(projectId, shotId, stage);
            if (res?.code === 200) {
                message.info(`镜 ${stage} 阶段重试已提交`);
                startPolling('retry');
            } else {
                message.error(res?.msg || '提交失败');
            }
        },
        [doSave, projectId, startPolling],
    );

    // 加载可选的生图/生视频模型（渲染模型选择器）
    useEffect(() => {
        (async () => {
            const [img, vid] = await Promise.all([
                listCreationModels('IMAGE'),
                listCreationModels('VIDEO'),
            ]);
            if (img?.code === 200) {
                setImageModels((img.data?.rows || []).filter((m) => m.model_type === 'IMAGE'));
            }
            if (vid?.code === 200) {
                setVideoModels((vid.data?.rows || []).filter((m) => m.model_type === 'VIDEO'));
            }
        })();
    }, []);

    const modelSelector = (
        <Popover
            trigger="click"
            placement="bottomRight"
            content={
                <Space direction="vertical" style={{ width: 280 }} size={8}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        Agnes 免费档限流/不可用时，切换到商汤聚合模型；选择会保存，单镜重试沿用
                    </Typography.Text>
                    <Select
                        allowClear
                        placeholder="生图模型（默认走模型管理）"
                        style={{ width: '100%' }}
                        value={imageModelId}
                        onChange={setImageModelId}
                        options={imageModels.map((m) => ({
                            label: `${m.model_name}${m.default_flag ? '（默认）' : ''}`,
                            value: m.model_id,
                        }))}
                    />
                    <Select
                        allowClear
                        placeholder="生视频模型（默认走模型管理）"
                        style={{ width: '100%' }}
                        value={videoModelId}
                        onChange={setVideoModelId}
                        options={videoModels.map((m) => ({
                            label: `${m.model_name}${m.default_flag ? '（默认）' : ''}`,
                            value: m.model_id,
                        }))}
                    />
                </Space>
            }
        >
            <Button icon={<SettingOutlined />}>
                生成模型{imageModelId || videoModelId ? ' ·' : ''}
            </Button>
        </Popover>
    );

    const doPackage = useCallback(async () => {
        setPackaging(true);
        try {
            const res = await publishPackage(projectId);
            if (res?.code === 200 && res.data) {
                setPackageData(res.data);
                message.success('发布包已生成');
            } else {
                message.error(res?.msg || '生成失败');
            }
        } catch {
            message.error('发布包生成失败，请重试');
        } finally {
            setPackaging(false);
        }
    }, [projectId]);

    // 一键多变体：克隆当前项目生成 N 版新钩子（正文与各镜产物复用，渲染近乎零成本）
    const doVariants = useCallback(async () => {
        if (dirtyRef.current && !(await doSave())) {
            return;
        }
        setVariantLoading(true);
        setVariants([]);
        try {
            const res = await createHookVariants(projectId, variantCount);
            if (res?.code === 200 && res.data?.variants?.length) {
                setVariants(res.data.variants);
                message.success(`已生成 ${res.data.variants.length} 个钩子变体`);
            } else {
                message.error(res?.msg || '变体生成失败');
            }
        } catch {
            message.error('变体生成失败，请重试');
        } finally {
            setVariantLoading(false);
        }
    }, [doSave, projectId, variantCount]);

    if (loading) {
        return (
            <PageContainer title={false}>
                <Spin style={{ display: 'block', margin: '120px auto' }} />
            </PageContainer>
        );
    }
    if (loadError || !project) {
        return (
            <PageContainer title={false}>
                <Empty description={loadError || '项目不存在'}>
                    <Button icon={<ReloadOutlined />} onClick={() => setReloadTick((t) => t + 1)}>
                        重新加载
                    </Button>
                </Empty>
            </PageContainer>
        );
    }

    const rendering = project.status === 'generating' || project.status === 'composing';
    const st = PROJECT_STATUS[project.status] || { text: project.status, color: 'default' };

    return (
        <PageContainer
            title={false}
            onBack={() => history.push('/ai/creation')}
            extra={[
                modelSelector,
                <Button
                    key="variants"
                    icon={<SwapOutlined />}
                    disabled={rendering}
                    title="克隆本项目生成多个不同钩子的版本（正文不动，渲染近乎零成本）"
                    onClick={() => {
                        setVariants([]);
                        setVariantOpen(true);
                    }}
                >
                    钩子多变体
                </Button>,
                <Button
                    key="save"
                    icon={<SaveOutlined />}
                    loading={saving}
                    disabled={rendering}
                    title={rendering ? '渲染中保存的编辑本次成片不生效' : undefined}
                    onClick={doSave}
                >
                    保存分镜
                </Button>,
                <Button
                    key="render"
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    disabled={rendering}
                    onClick={doRender}
                >
                    {rendering ? '渲染中…' : '保存并渲染'}
                </Button>,
            ]}
        >
            <Card size="small" style={{ marginBottom: 16 }}>
                <Descriptions size="small" column={isMobile ? 1 : 4}>
                    <Descriptions.Item label="作品" span={isMobile ? 1 : 2}>
                        <Space>
                            <Button
                                type="text"
                                size="small"
                                icon={<ArrowLeftOutlined />}
                                onClick={() => history.push('/ai/creation')}
                            />
                            <Typography.Text strong>{project.title}</Typography.Text>
                        </Space>
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                        <Tag color={st.color}>{st.text}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="成片">
                        {project.status === 'done' && project.final_video_url ? (
                            <Button
                                size="small"
                                type="link"
                                icon={<DownloadOutlined />}
                                href={project.final_video_url}
                                target="_blank"
                            >
                                下载/播放
                            </Button>
                        ) : (
                            '-'
                        )}
                    </Descriptions.Item>
                </Descriptions>
                {project.error_msg && (
                    <Typography.Text type="danger">失败原因：{project.error_msg}</Typography.Text>
                )}
            </Card>

            <Row gutter={16}>
                <Col xs={24} md={8}>
                    <Card
                        size="small"
                        title={
                            <>
                                <FileTextOutlined /> 口播稿
                            </>
                        }
                        style={{ marginBottom: 16 }}
                    >
                        <div className={styles.scriptBox}>{project.script_text || '（无）'}</div>
                    </Card>

                    {project.status === 'done' && (
                        <Card size="small" title="成片预览" style={{ marginBottom: 16 }}>
                            {project.final_video_url && (
                                <video
                                    src={project.final_video_url}
                                    controls
                                    poster={project.cover_url}
                                    style={{ width: '100%', borderRadius: 8 }}
                                />
                            )}
                        </Card>
                    )}

                    <Card size="small" title="发布包" style={{ marginBottom: 16 }}>
                        {packageData ? (
                            <Space direction="vertical" style={{ width: '100%' }} size={8}>
                                <Typography.Text strong>{packageData.title}</Typography.Text>
                                <Typography.Paragraph style={{ marginBottom: 0 }}>
                                    {packageData.caption}
                                </Typography.Paragraph>
                                <Space wrap>
                                    {(packageData.tags || []).map((t: string) => (
                                        <Tag key={t}>#{t}</Tag>
                                    ))}
                                </Space>
                                <Typography.Text type="warning" style={{ fontSize: 12 }}>
                                    ⚠ {packageData.ai_declaration || '本视频由 AI 辅助生成'}（发布时请勾选平台声明）
                                </Typography.Text>
                            </Space>
                        ) : (
                            <Button
                                block
                                loading={packaging}
                                onClick={doPackage}
                                disabled={project.status !== 'done'}
                            >
                                生成发布包（标题/文案/标签）
                            </Button>
                        )}
                    </Card>
                </Col>

                <Col xs={24} md={16}>
                    {shots.length === 0 ? (
                        <Empty description="无分镜数据" />
                    ) : (
                        shots.map((s, i) => {
                            const ss = SHOT_STATUS[s.shot_status] || { text: s.shot_status, color: 'default' };
                            return (
                                <Card
                                    key={s.shot_id}
                                    size="small"
                                    style={{ marginBottom: 12 }}
                                    title={
                                        <Space wrap>
                                            <Tag color="blue">镜 {i + 1}</Tag>
                                            <Tag>{ROLE_LABELS[s.role || ''] || s.role || '正文'}</Tag>
                                            <Tag color={ss.color}>{ss.text}</Tag>
                                        </Space>
                                    }
                                    extra={
                                        <Space size={4} wrap>
                                            <Popconfirm
                                                title="重新生成分镜图？"
                                                onConfirm={() => doRetry(s.shot_id, 'image')}
                                                disabled={rendering}
                                            >
                                                <Button size="small" icon={<PictureOutlined />} disabled={rendering}>
                                                    重生图
                                                </Button>
                                            </Popconfirm>
                                            <Popconfirm
                                                title="重新生成分镜视频？"
                                                onConfirm={() => doRetry(s.shot_id, 'video')}
                                                disabled={rendering}
                                            >
                                                <Button size="small" icon={<VideoCameraOutlined />} disabled={rendering}>
                                                    重出片
                                                </Button>
                                            </Popconfirm>
                                            <Popconfirm
                                                title="重新配音？"
                                                onConfirm={() => doRetry(s.shot_id, 'audio')}
                                                disabled={rendering}
                                            >
                                                <Button size="small" icon={<AudioOutlined />} disabled={rendering}>
                                                    重配音
                                                </Button>
                                            </Popconfirm>
                                        </Space>
                                    }
                                >
                                    <Row gutter={16}>
                                        <Col xs={24} sm={6}>
                                            {s.clip_url ? (
                                                <video
                                                    className={styles.shotPreview}
                                                    src={s.clip_url}
                                                    controls
                                                    muted
                                                    loop
                                                />
                                            ) : s.image_url ? (
                                                <img
                                                    className={styles.shotPreview}
                                                    src={s.image_url}
                                                    alt={`镜${i + 1}`}
                                                    loading="lazy"
                                                />
                                            ) : (
                                                <div className={styles.shotPreview} />
                                            )}
                                            {s.audio_url && (
                                                <audio src={s.audio_url} controls style={{ width: '100%', marginTop: 8 }} />
                                            )}
                                        </Col>
                                        <Col xs={24} sm={18}>
                                            <Space direction="vertical" style={{ width: '100%' }} size={8}>
                                                <div>
                                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                                        台词（改动后需保存并重配音）
                                                    </Typography.Text>
                                                    <Input.TextArea
                                                        rows={2}
                                                        value={s.text}
                                                        onChange={(e) => updateShot(s.shot_id, { text: e.target.value })}
                                                    />
                                                </div>
                                                <div>
                                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                                        生图提示词
                                                    </Typography.Text>
                                                    <Input.TextArea
                                                        rows={2}
                                                        value={s.image_prompt}
                                                        onChange={(e) => updateShot(s.shot_id, { image_prompt: e.target.value })}
                                                    />
                                                </div>
                                                <div>
                                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                                        生视频提示词（画面运动描述）
                                                    </Typography.Text>
                                                    <Input.TextArea
                                                        rows={2}
                                                        value={s.video_prompt}
                                                        onChange={(e) => updateShot(s.shot_id, { video_prompt: e.target.value })}
                                                    />
                                                </div>
                                                {s.audio_duration_ms && (
                                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                                        <ReloadOutlined /> 音频时长 {(s.audio_duration_ms / 1000).toFixed(1)}s（时间轴锚点）
                                                    </Typography.Text>
                                                )}
                                                {s.error_msg && (
                                                    <Typography.Text type="danger" style={{ fontSize: 12 }}>
                                                        {s.error_msg}
                                                    </Typography.Text>
                                                )}
                                            </Space>
                                        </Col>
                                    </Row>
                                </Card>
                            );
                        })
                    )}
                </Col>
            </Row>

            <Modal
                title="钩子多变体（正文不动，只换开头）"
                open={variantOpen}
                onCancel={() => setVariantOpen(false)}
                footer={null}
                width={isMobile ? '94%' : 560}
            >
                {variants.length === 0 ? (
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                        <Typography.Text type="secondary">
                            克隆当前项目生成 N 个不同策略钩子的版本；正文分镜与已生成的画面/配音全部复用，
                            变体只需重配首镜台词，渲染成本近乎为零。
                        </Typography.Text>
                        <Space>
                            <span>生成数量：</span>
                            <InputNumber min={1} max={5} value={variantCount} onChange={(v) => setVariantCount(v || 3)} />
                            <Button type="primary" loading={variantLoading} onClick={doVariants}>
                                生成变体
                            </Button>
                        </Space>
                    </Space>
                ) : (
                    <Space direction="vertical" style={{ width: '100%' }} size={8}>
                        {variants.map((v) => (
                            <Card key={v.project_id} size="small">
                                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                                    <Space wrap>
                                        <Tag color="orange">{v.angle || '钩子'}</Tag>
                                        <Typography.Text strong>{v.title}</Typography.Text>
                                    </Space>
                                    <Typography.Text>{v.hook}</Typography.Text>
                                    <Button
                                        size="small"
                                        type="link"
                                        onClick={() => history.push(`/ai/creation/editor/${v.project_id}`)}
                                    >
                                        去编辑/渲染 →
                                    </Button>
                                </Space>
                            </Card>
                        ))}
                    </Space>
                )}
            </Modal>
        </PageContainer>
    );
};

export default CreationEditor;
