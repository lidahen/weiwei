import { createStyles } from 'antd-style';
import {
    DeleteOutlined,
    DownloadOutlined,
    PlayCircleOutlined,
    PlaySquareOutlined,
    PlusOutlined,
    ReloadOutlined,
    UploadOutlined,
    VideoCameraOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
    Button,
    Card,
    Col,
    Collapse,
    Empty,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Row,
    Segmented,
    Select,
    Space,
    Switch,
    Tooltip,
    Upload,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React, { useEffect, useRef, useState } from 'react';
import { system } from '@/utils/twelvet';
import TWT from '@/setting';
import ModelSelect from '@/components/ModelSelect';
import TaskCenter from '@/components/TaskCenter';
import { delVideoTask, generateVideo, pageQueryVideoTask, queryVideoStatus, uploadVideoImage } from './service';

// 官方约束：num_frames ≤ 441 且满足 8n+1，时长 = 帧数 / 帧率
const DURATION_PRESETS = [
    { label: '3 秒', value: 81 },
    { label: '5 秒', value: 121 },
    { label: '10 秒', value: 241 },
    { label: '18 秒', value: 441 },
];

const FRAME_RATES = [16, 24, 30];

const RESOLUTIONS = [
    { label: '1152×768 · 3:2（推荐）', width: 1152, height: 768 },
    { label: '1280×720 · 16:9 横版', width: 1280, height: 720 },
    { label: '720×1280 · 9:16 竖版', width: 720, height: 1280 },
    { label: '1024×1024 · 1:1 方形', width: 1024, height: 1024 },
];

/**
 * Agnes Video 2.5 Flash 专属参数（见《docs/ai/模型调用文档说明.md》）。
 * 该模型 size 固定为 "720P"，输出尺寸只能靠画幅决定，因此这里按官方
 * 「视频尺寸与画幅」表给出 6 档，并把对应的 720P 像素回填给 width/height
 * （后端会按宽高比归到受支持的画幅）。
 * 商汤等其它厂商仍用上面的 RESOLUTIONS 预设。
 */
const AGNES_ASPECT_RATIOS = [
    { label: '21:9 超宽', width: 1680, height: 720 },
    { label: '16:9 横版', width: 1280, height: 704 },
    { label: '4:3 经典', width: 960, height: 720 },
    { label: '1:1 方形', width: 720, height: 720 },
    { label: '3:4 竖版', width: 720, height: 960 },
    { label: '9:16 竖屏', width: 720, height: 1280 },
];

/** Agnes Video 2.5 Flash 时长范围（秒，官方 4~12） */
const AGNES_SECONDS = [4, 5, 6, 8, 10, 12];

const PENDING_STATUSES = ['queued', 'pending', 'retrying', 'in_progress'];

const GEN_TYPE_LABELS: Record<string, string> = {
    text2vid: '文生视频',
    img2vid: '图生视频',
    multi2vid: '多图合成',
    keyframes: '关键帧动画',
};

const useStyles = createStyles(({ token, css }) => {
    return {
        header: css`
            border-radius: ${token.borderRadiusLG}px;
            background: linear-gradient(120deg, ${token.colorPrimary} 0%, #6d8a77 100%);
            color: #fff;
            padding: 24px 28px;
            margin-bottom: 16px;
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
        galleryCard: css`
            border-radius: ${token.borderRadiusLG}px;
            overflow: hidden;
            background: ${token.colorBgContainer};
            border: 1px solid ${token.colorBorderSecondary};
            position: relative;
            transition: box-shadow 0.2s, transform 0.2s;
            &:hover {
                box-shadow: ${token.boxShadowTertiary};
                transform: translateY(-2px);
            }
        `,
        galleryAction: css`
            position: absolute;
            top: 8px;
            right: 8px;
            z-index: 2;
        `,
    };
});

/**
 * AI 视频生成模块
 */
const VideoPage: React.FC = () => {
    const { styles } = useStyles();
    const [form] = Form.useForm();

    const [loading, setLoading] = useState(false);
    // 生成类型：text2vid / img2vid / multi2vid / keyframes
    const [genType, setGenType] = useState<string>('text2vid');
    // 生成参数
    const [numFrames, setNumFrames] = useState(121);
    const [frameRate, setFrameRate] = useState(24);
    const [resolution, setResolution] = useState(0); // RESOLUTIONS 下标
    // 当前所选模型的厂商：Agnes 与商汤的参数契约不同，表单据此切换控件
    const [modelProvider, setModelProvider] = useState<string>('');
    const isAgnesVideo = modelProvider === 'agnes';
    // Agnes 专属：时长（秒）与画幅下标
    const [seconds, setSeconds] = useState(5);
    const [agnesRatio, setAgnesRatio] = useState(1); // AGNES_ASPECT_RATIOS 下标，默认 16:9
    // 多图上限：Agnes keyframe 只要首尾两帧，reference 最多 5 张；其它厂商按 9 张
    const maxMultiImages = isAgnesVideo ? (genType === 'keyframes' ? 2 : 5) : 9;
    // 完成视频的受控预览弹窗（替代原「生成结果」大卡片）
    const [previewVideoUrl, setPreviewVideoUrl] = useState('');
    const [videoModalOpen, setVideoModalOpen] = useState(false);
    // 任务中心：进行中 + 失败的任务（取最新一页前端过滤）
    const [tasks, setTasks] = useState<Record<string, any>[]>([]);
    // 上传状态
    const [fileList, setFileList] = useState<UploadFile[]>([]);
    const [singleUrl, setSingleUrl] = useState('');
    const [uploading, setUploading] = useState(false);
    // 多图上传：按文件 uid 记录上传后的 URL，避免并发上传完成顺序与选择顺序不一致
    const [multiUrls, setMultiUrls] = useState<Record<string, string>>({});
    // 作品库（仅已完成作品，服务端按 status=completed 过滤）
    const [gallery, setGallery] = useState<Record<string, any>[]>([]);
    const [galleryTotal, setGalleryTotal] = useState(0);
    const [galleryPage, setGalleryPage] = useState(1);
    const galleryPageSize = 6;
    // 作品库按生成类型筛选（'all' = 全部）
    const [genTypeFilter, setGenTypeFilter] = useState<string>('all');
    const pageStateRef = useRef({ page: 1 });
    // 轮询句柄
    const pollRef = useRef<NodeJS.Timeout | null>(null);

    const multiUrlList = fileList.map((f) => multiUrls[f.uid]).filter(Boolean);

    /** 清理轮询 */
    const clearPoll = () => {
        if (pollRef.current) {
            clearInterval(pollRef.current);
            pollRef.current = null;
        }
    };

    useEffect(
        () => () => {
            clearPoll();
        },
        [],
    );

    /** 加载任务中心：取最新一页，非 completed 的（进行中/失败）留在条上 */
    const loadTasks = async () => {
        try {
            const { code, data } = await pageQueryVideoTask({ current: 1, pageSize: 30 });
            if (code !== 200) return;
            setTasks((data?.records || []).filter((t: Record<string, any>) => t.status !== 'completed'));
        } catch (e) {
            system.error(e);
        }
    };

    /** 加载作品库（仅已完成，服务端过滤） */
    const loadGallery = async (page = pageStateRef.current.page) => {
        try {
            // 后端 TableSupport 分页参数为 current / pageSize（PageHelper）
            const params: Record<string, any> = { current: page, pageSize: galleryPageSize, status: 'completed' };
            if (genTypeFilter !== 'all') {
                params.genType = genTypeFilter;
            }
            const { code, data } = await pageQueryVideoTask(params);
            if (code !== 200) return;
            pageStateRef.current.page = page;
            setGalleryPage(page);
            setGallery(data?.records || []);
            setGalleryTotal(data?.total || 0);
        } catch (e) {
            system.error(e);
        }
    };

    const loadAll = (page?: number) => {
        loadTasks();
        loadGallery(page);
    };

    /** 开始轮询当前任务状态 */
    const startPolling = (taskId: string | number) => {
        clearPoll();
        pollRef.current = setInterval(async () => {
            try {
                const { code, data } = await queryVideoStatus(taskId);
                if (code !== 200 || !data) return;
                const taskData = data?.data ?? data;
                if (!taskData) return;
                if (taskData.status === 'completed' || taskData.status === 'failed') {
                    clearPoll();
                    // 完成即自动弹出预览，替代原「生成结果」大卡片
                    if (taskData.status === 'completed' && taskData.resultUrl) {
                        setPreviewVideoUrl(taskData.resultUrl);
                        setVideoModalOpen(true);
                        message.success('视频生成完成');
                    }
                    loadAll();
                }
            } catch (e) {
                system.error(e);
            }
        }, 8000);
    };

    useEffect(() => {
        loadAll(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    useEffect(() => {
        // 切换生成类型筛选后回到第一页
        loadGallery(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [genTypeFilter]);
    useEffect(() => {
        // 存在进行中的任务时定时刷新（任务中心 + 作品库，完成后自然归档）
        const timer = setInterval(() => {
            if (tasks.some((t) => PENDING_STATUSES.includes(t.status))) {
                loadAll();
            }
        }, 8000);
        return () => clearInterval(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tasks]);

    /** 处理图片上传，返回完整 URL */
    const handleUpload = async (file: File) => {
        try {
            setUploading(true);
            const formData = new FormData();
            formData.append('file', file);
            const { code, msg, data } = await uploadVideoImage(formData);
            if (code !== 200) {
                message.error(msg || '上传失败');
                return null;
            }
            return `${TWT.static}/${data}`;
        } catch (e) {
            system.error(e);
            return null;
        } finally {
            setUploading(false);
        }
    };

    /** 提交生成视频 */
    const handleGenerate = async () => {
        const fields = await form.validateFields().catch(() => null);
        if (!fields) return;
        try {
            setLoading(true);

            // Agnes 与商汤的参数契约不同：Agnes Video 2.5 Flash 只认
            // size="720P" + seconds(4~12) + aspect_ratio；商汤沿用帧数/帧率/分辨率。
            const params: Record<string, any> = {
                genType,
                prompt: fields.prompt,
                enhancePrompt: fields.enhancePrompt ?? true,
                ...(isAgnesVideo
                    ? {
                          duration: seconds,
                          width: AGNES_ASPECT_RATIOS[agnesRatio].width,
                          height: AGNES_ASPECT_RATIOS[agnesRatio].height,
                      }
                    : {
                          numFrames,
                          frameRate,
                          width: RESOLUTIONS[resolution].width,
                          height: RESOLUTIONS[resolution].height,
                      }),
            };
            if (fields.negativePrompt) params.negativePrompt = fields.negativePrompt;
            if (fields.seed !== undefined && fields.seed !== null) params.seed = fields.seed;
            // 用户在前端选的模型 id(可空, 不传走 ai_model 表 default_flag=1)
            if (fields.modelId) params.modelId = fields.modelId;

            if (genType === 'img2vid') {
                if (!singleUrl) {
                    message.error('请先上传图片');
                    return;
                }
                params.inputImages = singleUrl;
            } else if (genType === 'multi2vid' || genType === 'keyframes') {
                if (multiUrlList.length < 2) {
                    message.error(genType === 'keyframes' ? '关键帧动画至少需要 2 张关键帧' : '多图合成至少需要 2 张参考图');
                    return;
                }
                if (multiUrlList.length > maxMultiImages) {
                    message.error(
                        isAgnesVideo && genType === 'keyframes'
                            ? 'Agnes 关键帧模式只支持首帧 + 尾帧共 2 张'
                            : `当前模型最多支持 ${maxMultiImages} 张参考图`,
                    );
                    return;
                }
                params.inputImages = JSON.stringify(multiUrlList);
            }

            const { code, msg, data } = await generateVideo(params);
            if (code !== 200) {
                return message.error(msg);
            }
            message.success('任务已提交，请等待生成结果（约 1-3 分钟）');

            // Java 代理 Python 时会产生双层嵌套：{ code:200, data: { task_id, ... } }
            const taskData = data?.data ?? data;
            const taskId = taskData?.taskId ?? taskData?.task_id;
            if (taskId) {
                startPolling(taskId);
            }
            loadAll(1);
        } catch (e) {
            system.error(e);
        } finally {
            setLoading(false);
        }
    };

    /** 删除视频任务（任务中心失败任务 / 作品库卡片共用） */
    const refRemove = async (taskId: string | number | undefined) => {
        try {
            if (!taskId) return true;
            const { code, msg } = await delVideoTask(taskId);
            if (code !== 200) {
                return message.error(msg);
            }
            message.success(msg);
            loadAll();
        } catch (e) {
            system.error(e);
        }
    };

    /** 下载视频 */
    const handleDownload = (url: string) => {
        if (!url) {
            message.warning('暂无可下载视频');
            return;
        }
        const link = document.createElement('a');
        link.href = url;
        link.download = `generated-video-${Date.now()}.mp4`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const renderUploads = () => {
        if (genType === 'img2vid') {
            return (
                <Form.Item label="输入图片" required>
                    <Upload
                        listType="picture-card"
                        maxCount={1}
                        fileList={fileList}
                        beforeUpload={(file) => {
                            if (!file.type.startsWith('image/')) {
                                message.error('只能上传图片文件');
                                return false;
                            }
                            if (file.size / 1024 / 1024 >= 10) {
                                message.error('图片大小不能超过10MB');
                                return false;
                            }
                            setFileList([file as unknown as UploadFile]);
                            handleUpload(file).then((url) => url && setSingleUrl(url));
                            return false;
                        }}
                        onRemove={() => {
                            setFileList([]);
                            setSingleUrl('');
                        }}
                    >
                        {fileList.length === 0 && (
                            <div>
                                <UploadOutlined />
                                <div style={{ marginTop: 8 }}>上传</div>
                            </div>
                        )}
                    </Upload>
                </Form.Item>
            );
        }
        if (genType === 'multi2vid' || genType === 'keyframes') {
            return (
                <Form.Item
                    label={
                        genType === 'keyframes'
                            ? isAgnesVideo
                                ? '关键帧（2 张：首帧 + 尾帧）'
                                : `关键帧（2-${maxMultiImages} 张，按顺序）`
                            : `参考图（2-${maxMultiImages} 张，第一张为主）`
                    }
                    required
                >
                    <Upload
                        listType="picture-card"
                        maxCount={maxMultiImages}
                        fileList={fileList}
                        beforeUpload={(file) => {
                            if (!file.type.startsWith('image/')) {
                                message.error('只能上传图片文件');
                                return false;
                            }
                            if (file.size / 1024 / 1024 >= 10) {
                                message.error('图片大小不能超过10MB');
                                return false;
                            }
                            const uid = `${file.uid}`;
                            setFileList((prev) => [...prev, file as unknown as UploadFile]);
                            handleUpload(file).then((url) => url && setMultiUrls((prev) => ({ ...prev, [uid]: url })));
                            return false;
                        }}
                        onRemove={(removed) => {
                            setFileList((prev) => prev.filter((f) => f.uid !== removed.uid));
                            setMultiUrls((prev) => {
                                const next = { ...prev };
                                delete next[removed.uid];
                                return next;
                            });
                        }}
                    >
                        {fileList.length < 5 && (
                            <div>
                                <PlusOutlined />
                                <div style={{ marginTop: 8 }}>上传</div>
                            </div>
                        )}
                    </Upload>
                </Form.Item>
            );
        }
        return null;
    };

    return (
        <PageContainer header={{ title: null, breadcrumb: {} }}>
            <div className={styles.header}>
                <h2 className={styles.headerTitle}>AI 视频工作台</h2>
                <p className={styles.headerDesc}>
                    文生视频 / 图生视频 / 多图合成 / 关键帧动画，支持 3-18 秒时长与多种分辨率档位
                </p>
            </div>
            <Row gutter={[16, 16]}>
                {/* 左侧：创作面板 */}
                <Col xs={24} xl={8}>
                    <Card title="创作" bordered={false} style={{ borderRadius: 12 }}>
                        <Form form={form} layout="vertical">
                            <Segmented
                                block
                                value={genType}
                                onChange={(v) => {
                                    setGenType(v as string);
                                    setFileList([]);
                                    setSingleUrl('');
                                    setMultiUrls({});
                                }}
                                options={[
                                    { label: '文生视频', value: 'text2vid', icon: <PlaySquareOutlined /> },
                                    { label: '图生视频', value: 'img2vid', icon: <VideoCameraOutlined /> },
                                    { label: '多图', value: 'multi2vid', icon: <PlusOutlined /> },
                                    { label: '关键帧', value: 'keyframes', icon: <PlayCircleOutlined /> },
                                ]}
                            />

                            <div style={{ marginTop: 16 }}>{renderUploads()}</div>

                            <Form.Item
                                label="画面描述"
                                name="prompt"
                                rules={[{ required: true, message: '请输入画面描述' }]}
                            >
                                <Input.TextArea
                                    rows={4}
                                    showCount
                                    maxLength={800}
                                    placeholder={
                                        genType === 'img2vid'
                                            ? '描述图片如何动起来，如：人物缓缓回头看向镜头，保持面部与服装稳定'
                                            : '描述你想要的视频内容，如：沙滩上猫的行走镜头，日落暖光，真实运动感'
                                    }
                                />
                            </Form.Item>

                            <Form.Item
                                label="生成模型"
                                name="modelId"
                                tooltip="不同厂商参数契约不同，下方参数会随所选模型自动切换"
                            >
                                <ModelSelect
                                    modelType="VIDEO"
                                    onModelInfo={(m) => {
                                        const next = m?.model_provider ?? '';
                                        setModelProvider((prev) => (prev === next ? prev : next));
                                    }}
                                />
                            </Form.Item>

                            <Form.Item label="智能提示词增强" name="enhancePrompt" initialValue={true} valuePropName="checked">
                                <Switch checkedChildren="开" unCheckedChildren="关" />
                            </Form.Item>

                            {isAgnesVideo ? (
                                <>
                                    <Form.Item label="视频时长" tooltip="Agnes Video 2.5 Flash 支持 4~12 秒">
                                        <Segmented
                                            block
                                            options={AGNES_SECONDS.map((s) => ({ label: `${s}s`, value: s }))}
                                            value={seconds}
                                            onChange={(v) => setSeconds(v as number)}
                                        />
                                    </Form.Item>

                                    <Form.Item
                                        label="画幅"
                                        tooltip="Agnes 的 size 固定为 720P，输出尺寸由画幅决定"
                                    >
                                        <Select
                                            value={agnesRatio}
                                            onChange={setAgnesRatio}
                                            options={AGNES_ASPECT_RATIOS.map((r, i) => ({
                                                label: `${r.label} · ${r.width}×${r.height}`,
                                                value: i,
                                            }))}
                                        />
                                    </Form.Item>
                                </>
                            ) : (
                                <>
                                    <Form.Item label="视频时长">
                                        <Segmented
                                            block
                                            options={DURATION_PRESETS}
                                            value={numFrames}
                                            onChange={(v) => setNumFrames(v as number)}
                                        />
                                        <div style={{ marginTop: 6, fontSize: 12, color: '#999' }}>
                                            共 {numFrames} 帧 · 约 {Math.round((numFrames / frameRate) * 10) / 10} 秒（时长 = 帧数 ÷ 帧率）
                                        </div>
                                    </Form.Item>

                                    <Form.Item label="帧率">
                                        <Select value={frameRate} onChange={setFrameRate} options={FRAME_RATES.map((r) => ({ label: `${r} fps`, value: r }))} />
                                    </Form.Item>

                                    <Form.Item label="分辨率">
                                        <Select value={resolution} onChange={setResolution} options={RESOLUTIONS.map((r, i) => ({ label: r.label, value: i }))} />
                                    </Form.Item>
                                </>
                            )}

                            <Collapse
                                ghost
                                items={[
                                    {
                                        key: 'advanced',
                                        label: '高级设置',
                                        children: (
                                            <>
                                                <Form.Item label="反向提示词" name="negativePrompt">
                                                    <Input.TextArea rows={2} placeholder="不希望出现的元素，如：blurry, low quality, watermark" />
                                                </Form.Item>
                                                <Form.Item label="Seed（复现结果）" name="seed">
                                                    <InputNumber min={0} max={4294967295} style={{ width: '100%' }} placeholder="留空随机" />
                                                </Form.Item>
                                            </>
                                        ),
                                    },
                                ]}
                            />

                            <Button type="primary" size="large" block loading={loading || uploading} onClick={handleGenerate}>
                                {uploading ? '上传图片中...' : '立即生成'}
                            </Button>
                        </Form>
                    </Card>
                </Col>

                {/* 右侧：任务中心 + 作品库 */}
                <Col xs={24} xl={16}>
                    <TaskCenter
                        tasks={tasks}
                        showProgress
                        onDelete={(task) => refRemove(task.taskId)}
                    />

                    <Card
                        title={`作品库（${galleryTotal}）`}
                        bordered={false}
                        style={{ borderRadius: 12 }}
                        extra={
                            <Space>
                                <Segmented
                                    size="small"
                                    value={genTypeFilter}
                                    onChange={(v) => setGenTypeFilter(v as string)}
                                    options={[
                                        { label: '全部', value: 'all' },
                                        ...Object.entries(GEN_TYPE_LABELS).map(([value, label]) => ({
                                            label,
                                            value,
                                        })),
                                    ]}
                                />
                                <Button icon={<ReloadOutlined />} size="small" onClick={() => loadAll()}>
                                    刷新
                                </Button>
                            </Space>
                        }
                    >
                        {gallery.length === 0 ? (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无作品" />
                        ) : (
                            <Row gutter={[12, 12]}>
                                {gallery.map((task) => {
                                    const url = task.resultUrl;
                                    return (
                                        <Col key={task.taskId} xs={12} sm={8}>
                                            <div className={styles.galleryCard}>
                                                {url && (
                                                    <>
                                                        <Popconfirm
                                                            className={styles.galleryAction}
                                                            title="确定删除吗"
                                                            onConfirm={() => refRemove(task.taskId)}
                                                        >
                                                            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                                                        </Popconfirm>
                                                        <Tooltip title="下载视频">
                                                            <Button
                                                                className={styles.galleryAction}
                                                                style={{ right: 40 }}
                                                                type="text"
                                                                size="small"
                                                                icon={<DownloadOutlined />}
                                                                onClick={() => handleDownload(url)}
                                                            />
                                                        </Tooltip>
                                                    </>
                                                )}
                                                <div style={{ aspectRatio: '16 / 9', background: '#20241f' }}>
                                                    {url && (
                                                        <video
                                                            src={url}
                                                            controls
                                                            preload="metadata"
                                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                        />
                                                    )}
                                                </div>
                                                <div style={{ padding: '8px 10px' }}>
                                                    <Tooltip title={task.prompt} placement="top">
                                                        <div
                                                            style={{
                                                                fontSize: 12,
                                                                color: '#666',
                                                                overflow: 'hidden',
                                                                textOverflow: 'ellipsis',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            {GEN_TYPE_LABELS[task.genType] || task.genType} · {task.prompt || '-'}
                                                        </div>
                                                    </Tooltip>
                                                    <div style={{ fontSize: 12, color: '#aaa', marginTop: 2 }}>
                                                        {[task.videoSize, task.videoSeconds ? `${Math.round(Number(task.videoSeconds) * 10) / 10}s` : '']
                                                            .filter(Boolean)
                                                            .join(' · ')}
                                                    </div>
                                                </div>
                                            </div>
                                        </Col>
                                    );
                                })}
                            </Row>
                        )}
                        {galleryTotal > galleryPageSize && (
                            <div style={{ textAlign: 'right', marginTop: 16 }}>
                                <a
                                    onClick={() => loadGallery(Math.max(1, galleryPage - 1))}
                                    style={{ marginRight: 12, display: galleryPage > 1 ? 'inline' : 'none' }}
                                >
                                    上一页
                                </a>
                                <span style={{ color: '#999', fontSize: 12 }}>
                                    {galleryPage} / {Math.ceil(galleryTotal / galleryPageSize)}
                                </span>
                                <a
                                    onClick={() => loadGallery(galleryPage + 1)}
                                    style={{ marginLeft: 12, display: galleryPage < Math.ceil(galleryTotal / galleryPageSize) ? 'inline' : 'none' }}
                                >
                                    下一页
                                </a>
                            </div>
                        )}
                    </Card>

                    {/* 受控预览：任务完成自动弹出，也可双击作品库卡片回顾 */}
                    <Modal
                        title="视频预览"
                        open={videoModalOpen}
                        onCancel={() => setVideoModalOpen(false)}
                        footer={
                            previewVideoUrl && (
                                <Button type="primary" ghost icon={<DownloadOutlined />} onClick={() => handleDownload(previewVideoUrl)}>
                                    下载视频
                                </Button>
                            )
                        }
                        width={720}
                        centered
                        destroyOnClose
                    >
                        {previewVideoUrl && (
                            <video
                                src={previewVideoUrl}
                                controls
                                autoPlay
                                style={{ width: '100%', maxHeight: 480, borderRadius: 8 }}
                            />
                        )}
                    </Modal>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default VideoPage;
