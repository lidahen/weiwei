import { adminReportHandle, adminReportPage } from '@/services/marketplace/api';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { FlagOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
    Image,
    Input,
    message,
    Modal,
    Select,
    Space,
    Tabs,
    Tag,
    Typography,
} from 'antd';
import React, { useRef, useState } from 'react';
import { staticUrl } from '../utils';
import { reportActionOptions, TARGET_TYPE_LABELS } from '@/utils/report';

const { TextArea } = Input;
const { Text } = Typography;

const STATUS_MAP: Record<string, { text: string; color: string }> = {
    pending: { text: '待处理', color: 'orange' },
    done: { text: '已处理', color: 'green' },
};

/** 处理动作的中文名（用于已处理行的「处理信息」列） */
const ACTION_LABELS: Record<string, string> = {
    dismiss: '驳回',
    warn: '警告',
    remove_product: '下架商品',
    remove_feed: '删除动态',
    ban_user: '停用账号',
};

const TARGET_COLORS: Record<string, string> = {
    product: 'blue',
    feed: 'cyan',
    user: 'purple',
};

const TABS = [
    { key: 'pending', label: '待处理' },
    { key: 'done', label: '已处理' },
    { key: 'all', label: '全部' },
];

/** 对象详情页路径（用于「查看对象」链接） */
function targetPath(row: any): string | null {
    if (!row?.targetExists) return null;
    if (row.targetType === 'product') return `/product/detail/${row.targetId}`;
    if (row.targetType === 'feed') return `/feed/detail/${row.targetId}`;
    if (row.targetType === 'user') return `/user/profile/${row.targetId}`;
    return null;
}

/**
 * 列定义在模块作用域构造（不随组件渲染重建）。
 * 唯一的外部依赖是 openHandle，由调用方传进来。
 */
function buildColumns(onOpenHandle: (row: any) => void): ProColumns<any>[] {
    return [
        { title: '举报ID', dataIndex: 'reportId', width: 80, search: false },
        {
            title: '对象',
            dataIndex: 'targetTitle',
            search: false,
            width: 300,
            render: (_, row) => {
                const cover = staticUrl(row.targetCover);
                return (
                    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                        {cover ? (
                            <Image src={cover} width={48} height={48} style={{ borderRadius: 4, flexShrink: 0 }} />
                        ) : (
                            <div
                                style={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 4,
                                    background: '#f5f5f5',
                                    flexShrink: 0,
                                }}
                            />
                        )}
                        <div style={{ minWidth: 0 }}>
                            <div>
                                <Tag color={TARGET_COLORS[row.targetType] || 'default'}>
                                    {TARGET_TYPE_LABELS[row.targetType] || row.targetType}
                                </Tag>
                                <Text ellipsis style={{ maxWidth: 200 }}>
                                    {row.targetTitle || `#${row.targetId}`}
                                </Text>
                                {!row.targetExists && (
                                    <Tag color="default" style={{ marginLeft: 4 }}>
                                        已删除
                                    </Tag>
                                )}
                            </div>
                            <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>
                                {row.targetExtra || '—'}
                            </div>
                        </div>
                    </div>
                );
            },
        },
        { title: '举报人', dataIndex: 'reporterNickName', search: false, width: 100 },
        {
            title: '原因',
            dataIndex: 'reasonType',
            search: false,
            width: 220,
            render: (_, row) => {
                // 后端已把 reason 拆成 reasonType（标签）+ reasonDetail（详情）返回，直接展示
                const typeLabel = row.reasonType || '—';
                const detail = row.reasonDetail || '';
                return (
                    <div>
                        <Tag color="volcano">{typeLabel}</Tag>
                        {detail && <div style={{ fontSize: 12, color: '#666' }}>{detail}</div>}
                    </div>
                );
            },
        },
        {
            title: '证据',
            dataIndex: 'evidenceImages',
            search: false,
            width: 120,
            render: (_, row) => {
                // 证据图在 request 映射阶段已预解析成数组（evidenceList），这里不再 JSON.parse
                const imgs: string[] = Array.isArray(row.evidenceList) ? row.evidenceList : [];
                if (imgs.length === 0) return <span style={{ color: '#999' }}>—</span>;
                return (
                    <Image.PreviewGroup>
                        <Space size={4}>
                            {imgs.map((p, i) => (
                                <Image
                                    key={i}
                                    src={staticUrl(p)}
                                    width={40}
                                    height={40}
                                    style={{ borderRadius: 4, objectFit: 'cover' }}
                                />
                            ))}
                        </Space>
                    </Image.PreviewGroup>
                );
            },
        },
        {
            title: '状态',
            dataIndex: 'status',
            search: false,
            width: 90,
            render: (_, row) => (
                <Tag color={STATUS_MAP[row.status]?.color}>{STATUS_MAP[row.status]?.text || row.status}</Tag>
            ),
        },
        {
            title: '举报时间',
            dataIndex: 'createTime',
            valueType: 'dateTime',
            search: false,
            width: 160,
        },
        {
            title: '处理信息',
            dataIndex: 'handleTime',
            search: false,
            width: 220,
            render: (_, row) => {
                if (row.status !== 'done') return <span style={{ color: '#999' }}>—</span>;
                return (
                    <div style={{ fontSize: 12 }}>
                        <div>
                            {row.handleTime || '—'} · {row.handlerNickName || '—'}
                        </div>
                        <div>
                            <Tag>{ACTION_LABELS[row.action] || row.action || '—'}</Tag>
                        </div>
                        {row.handleNote && <div style={{ color: '#666' }}>{row.handleNote}</div>}
                    </div>
                );
            },
        },
        {
            title: '操作',
            valueType: 'option',
            width: 130,
            fixed: 'right',
            render: (_, row) => (
                <Space>
                    {row.status === 'pending' && <a onClick={() => onOpenHandle(row)}>处理</a>}
                    {targetPath(row) && (
                        <a onClick={() => history.push(targetPath(row)!)}>查看对象</a>
                    )}
                </Space>
            ),
        },
    ];
}

/** 把后端返回的 evidenceImages JSON 字符串预解析成数组，避免每个单元格重复解析 */
function parseEvidence(row: any): string[] {
    if (!row?.evidenceImages) return [];
    if (Array.isArray(row.evidenceImages)) return row.evidenceImages;
    try {
        const parsed = JSON.parse(row.evidenceImages);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

const Report: React.FC = () => {
    const actionRef = useRef<any>();
    const [tab, setTab] = useState('pending');
    const [currentRow, setCurrentRow] = useState<any>(null);
    const [handleOpen, setHandleOpen] = useState(false);
    const [action, setAction] = useState<string>('');
    const [note, setNote] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // useCallback：columns 的 useMemo 依赖它，函数身份必须稳定
    const openHandle = React.useCallback((row: any) => {
        const options = reportActionOptions(row.targetType);
        setCurrentRow(row);
        setAction(options[0]?.value || 'dismiss');
        setNote('');
        setHandleOpen(true);
    }, []);

    const closeHandle = () => {
        setHandleOpen(false);
        setCurrentRow(null);
        setAction('');
        setNote('');
        setSubmitting(false);
    };

    const submitHandle = async () => {
        if (!action) {
            message.warning('请选择处理动作');
            return;
        }
        if (action === 'dismiss' && !note.trim()) {
            message.warning('驳回时请填写理由，会同步通知举报人');
            return;
        }

        const actionLabel = ACTION_LABELS[action] || action;
        Modal.confirm({
            title: `确认「${actionLabel}」？`,
            content:
                action === 'ban_user'
                    ? '该操作将立即生效：账号状态置为停用，并把该用户所有会话踢下线。'
                    : '该操作将立即生效，并给相关用户发送站内通知。',
            okText: '确定',
            cancelText: '取消',
            onOk: async () => {
                setSubmitting(true);
                try {
                    const res: any = await adminReportHandle({
                        reportId: currentRow.reportId,
                        action,
                        note: note.trim(),
                    });
                    message.success(res?.msg || res?.data || '已处理');
                    closeHandle();
                    actionRef.current?.reload();
                } catch (e: any) {
                    const msg = e?.response?.data?.msg || '处理失败，请稍后重试';
                    message.error(msg);
                    // 只有「状态已被他人改变」才需要刷新列表；纯校验失败或网络错误刷新毫无意义，
                    // 反而会触发一次完整重查（count + 分页 + 三条批量 IN）。此时保留弹窗让运营重试。
                    if (msg.includes('已被处理')) {
                        closeHandle();
                        actionRef.current?.reload();
                    } else {
                        setSubmitting(false);
                    }
                }
            },
        });
    };

    // columns 在 useMemo 里构造：每次渲染新建数组会让 ProTable 重跑列生成并重渲染所有单元格。
    // 依赖只有 openHandle（useCallback 稳定），所以在处理备注里打字不会重建整张表。
    const columns = React.useMemo<ProColumns<any>[]>(() => buildColumns(openHandle), [openHandle]);

    const handleOptions = React.useMemo(
        () => reportActionOptions(currentRow?.targetType || ''),
        [currentRow?.targetType],
    );

    return (
        <PageContainer header={{ title: '举报处理', breadcrumb: {} }}>
            <Tabs
                activeKey={tab}
                // 不要在这里 reload()：request 闭包读的是上一次渲染的 tab，
                // 同步 reload 会用旧 status 再查一遍。改由 ProTable 的 params 变化自动重查。
                onChange={(k) => setTab(k)}
                items={TABS.map((t) => ({ key: t.key, label: t.label }))}
            />
            <ProTable
                actionRef={actionRef}
                rowKey="reportId"
                columns={columns}
                search={false}
                options={false}
                pagination={{ defaultPageSize: 20, showSizeChanger: true }}
                // params 变化即触发重查（切 tab / 翻页都走这里，且读到的 tab 一定是最新值）
                params={{ status: tab }}
                request={async (params) => {
                    const r: any = await adminReportPage({
                        status: params.status,
                        current: params.current,
                        pageSize: params.pageSize,
                    });
                    const d = r?.data ?? r;
                    // 证据图在这里预解析一次，单元格渲染时直接读 evidenceList
                    const rows = (d?.records || []).map((row: any) => ({
                        ...row,
                        evidenceList: parseEvidence(row),
                    }));
                    return {
                        data: rows,
                        success: true,
                        total: d?.total || 0,
                    };
                }}
                tableAlertRender={false}
                locale={{ emptyText: '暂无举报' }}
            />

            <Modal
                title={`处理举报 #${currentRow?.reportId ?? ''}`}
                open={handleOpen}
                onCancel={closeHandle}
                confirmLoading={submitting}
                okText="处理"
                cancelText="取消"
                maskClosable={false}
                destroyOnClose
            >
                {currentRow && (
                    <div style={{ marginBottom: 12, fontSize: 13, color: '#666' }}>
                        <FlagOutlined /> {TARGET_TYPE_LABELS[currentRow.targetType] || '对象'}：
                        {currentRow.targetTitle || `#${currentRow.targetId}`}
                        {!currentRow.targetExists && (
                            <Tag color="default" style={{ marginLeft: 8 }}>
                                已删除
                            </Tag>
                        )}
                    </div>
                )}

                <div style={{ marginBottom: 6 }}>处理动作</div>
                <Select
                    style={{ width: '100%' }}
                    value={action}
                    onChange={setAction}
                    options={handleOptions.map((o) => ({ value: o.value, label: o.label }))}
                />

                <div style={{ margin: '12px 0 6px' }}>
                    处理备注
                    {action === 'dismiss' && <span style={{ color: '#ff4d4f' }}>（必填）</span>}
                </div>
                <TextArea
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    rows={3}
                    maxLength={200}
                    showCount
                    placeholder="会随站内通知一起发给用户，驳回时请写清核实结论"
                />

                {currentRow && currentRow.targetType === 'product' && action === 'remove_product' && (
                    <div style={{ marginTop: 8, fontSize: 12, color: '#999' }}>
                        仅「在售 / 待审核」商品可下架；已付款或已售商品请走纠纷处理流程。
                    </div>
                )}
                {currentRow && !currentRow.targetExists && (
                    <div style={{ marginTop: 8, fontSize: 12, color: '#faad14' }}>
                        对象已被删除，处置不会执行，但仍会记为已处理并通知举报人。
                    </div>
                )}

                <div style={{ marginTop: 12, fontSize: 12, color: '#999' }}>
                    举报原因：{currentRow?.reasonType || '—'}
                    {currentRow?.reasonDetail ? ` · ${currentRow.reasonDetail}` : ''}
                </div>
            </Modal>
        </PageContainer>
    );
};

export default Report;
