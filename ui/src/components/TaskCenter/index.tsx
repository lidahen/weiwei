import { createStyles } from 'antd-style';
import { DeleteOutlined } from '@ant-design/icons';
import { Button, Empty, Popconfirm, Spin, Tooltip } from 'antd';
import React from 'react';

/**
 * 生成中 / 失败任务的状态集合
 * 图片域：queued / pending / retrying / processing
 * 视频域：in_progress 与上侧重叠，这里统一收口
 */
export const TASK_PENDING_STATUSES = ['queued', 'pending', 'retrying', 'processing', 'in_progress'];

const STATUS_TEXT: Record<string, string> = {
    pending: '待处理',
    queued: '排队中',
    retrying: '重试中',
    processing: '处理中',
    in_progress: '处理中',
    failed: '失败',
};

const useStyles = createStyles(({ token, css }) => {
    return {
        wrap: css`
            border: 1px solid ${token.colorBorderSecondary};
            border-radius: ${token.borderRadiusLG}px;
            background: ${token.colorBgContainer};
            padding: 12px 14px;
        `,
        head: css`
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 10px;
        `,
        dot: css`
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: ${token.colorPrimary};
        `,
        title: css`
            font-size: 14px;
            font-weight: 600;
            color: ${token.colorText};
        `,
        count: css`
            font-size: 12px;
            color: ${token.colorTextTertiary};
        `,
        hint: css`
            margin-left: auto;
            font-size: 12px;
            color: ${token.colorTextTertiary};
            @media (max-width: 767px) {
                display: none;
            }
        `,
        grid: css`
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 8px;
        `,
        row: css`
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 8px 10px;
            border: 1px solid ${token.colorBorderSecondary};
            border-radius: ${token.borderRadius}px;
        `,
        rowFailed: css`
            border-color: ${token.colorErrorBorder};
            background: ${token.colorErrorBg};
        `,
        thumb: css`
            width: 36px;
            height: 36px;
            border-radius: 6px;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            background: ${token.colorFillSecondary};
        `,
        thumbFailed: css`
            background: ${token.colorError};
            color: #fff;
            font-size: 16px;
            font-weight: 600;
            line-height: 1;
        `,
        body: css`
            flex: 1;
            min-width: 0;
        `,
        prompt: css`
            font-size: 12px;
            color: ${token.colorText};
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        `,
        errMsg: css`
            font-size: 12px;
            color: ${token.colorError};
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        `,
        sub: css`
            font-size: 12px;
            color: ${token.colorTextSecondary};
            margin-top: 4px;
        `,
        bar: css`
            height: 4px;
            border-radius: 2px;
            background: ${token.colorFillSecondary};
            margin-top: 6px;
            overflow: hidden;
        `,
        barInner: css`
            height: 100%;
            border-radius: 2px;
            background: ${token.colorPrimary};
        `,
    };
});

interface TaskCenterProps {
    /** 进行中 + 失败的任务列表（已由调用方过滤好） */
    tasks: Record<string, any>[];
    /** 视频任务带 progress 字段，图片任务用不确定进度 */
    showProgress?: boolean;
    /** 删除任务（失败任务的唯一操作） */
    onDelete?: (task: Record<string, any>) => void;
}

/**
 * AI 生成页顶部「任务中心」横条：
 * 聚合进行中与失败任务，完成后由调用方刷新列表使其自然归档到作品库。
 * 无任务时不渲染，不占布局空间。
 */
const TaskCenter: React.FC<TaskCenterProps> = ({ tasks, showProgress = false, onDelete }) => {
    const { styles } = useStyles();

    if (!tasks.length) return null;

    const pending = tasks.filter((t) => TASK_PENDING_STATUSES.includes(t.status));
    const failed = tasks.filter((t) => t.status === 'failed');

    const renderRow = (task: Record<string, any>) => {
        const isFailed = task.status === 'failed';
        return (
            <div key={task.taskId} className={`${styles.row} ${isFailed ? styles.rowFailed : ''}`}>
                <div className={`${styles.thumb} ${isFailed ? styles.thumbFailed : ''}`}>
                    {isFailed ? '!' : <Spin size="small" />}
                </div>
                <div className={styles.body}>
                    <Tooltip title={task.prompt} placement="top">
                        <div className={styles.prompt}>{task.prompt || `任务 #${task.taskId}`}</div>
                    </Tooltip>
                    {isFailed ? (
                        <Tooltip title={task.errorMsg || '生成失败'} placement="top">
                            <div className={styles.errMsg}>{task.errorMsg || '生成失败'}</div>
                        </Tooltip>
                    ) : showProgress && task.progress !== undefined && task.progress !== null ? (
                        <>
                            <div className={styles.bar}>
                                <div className={styles.barInner} style={{ width: `${task.progress || 2}%` }} />
                            </div>
                            <div className={styles.sub}>
                                {STATUS_TEXT[task.status] || task.status} · {Math.round(task.progress || 0)}%
                            </div>
                        </>
                    ) : (
                        <div className={styles.sub}>{STATUS_TEXT[task.status] || task.status}中，请稍候…</div>
                    )}
                </div>
                {isFailed && onDelete && (
                    <Popconfirm title="确定删除该失败任务吗" onConfirm={() => onDelete(task)}>
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                )}
            </div>
        );
    };

    return (
        <div className={styles.wrap}>
            <div className={styles.head}>
                <span className={styles.dot} />
                <span className={styles.title}>任务中心</span>
                <span className={styles.count}>
                    {pending.length > 0 && `进行中 ${pending.length}`}
                    {pending.length > 0 && failed.length > 0 && ' · '}
                    {failed.length > 0 && `失败 ${failed.length}`}
                </span>
                <span className={styles.hint}>完成的任务会自动归档到下方作品库</span>
            </div>
            {pending.length === 0 && failed.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无任务" />
            ) : (
                <div className={styles.grid}>
                    {pending.map(renderRow)}
                    {failed.map(renderRow)}
                </div>
            )}
        </div>
    );
};

export default TaskCenter;
