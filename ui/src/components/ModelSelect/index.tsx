/** 通用 AI 模型下拉选择器。

按 model_type 实时拉 ai_model 表, default_flag=1 的排第一。
用作受控组件: value=model_id, onChange=model_id。
不传 value 时显示默认模型。

onModelInfo: 把「当前生效的模型」（含 sizes 等能力字段）回调给父组件，
供生成表单按模型能力收敛尺寸/比例选项；不传该 prop 时行为与之前完全一致。
*/
import { listModelsByType, ModelOption } from '@/services/ant-design-pro/aimusic';
import { Select } from 'antd';
import { useEffect, useRef, useState } from 'react';

interface Props {
    modelType: 'IMAGE' | 'VIDEO' | 'MUSIC';
    value?: number;
    onChange?: (val: number) => void;
    placeholder?: string;
    disabled?: boolean;
    /** 当前生效模型变化时回调（未选中时回落到默认模型；无任何模型时回调 null） */
    onModelInfo?: (model: ModelOption | null) => void;
}

const ModelSelect: React.FC<Props> = ({
    modelType,
    value,
    onChange,
    placeholder,
    disabled,
    onModelInfo,
}) => {
    const [options, setOptions] = useState<ModelOption[]>([]);
    const [loading, setLoading] = useState(false);

    // 用 ref 持有回调，避免父组件每次渲染传新函数导致 effect 反复触发
    const cbRef = useRef(onModelInfo);
    cbRef.current = onModelInfo;

    useEffect(() => {
        let mounted = true;
        setLoading(true);
        listModelsByType(modelType)
            .then((res) => {
                if (!mounted) return;
                // listModelsByType 已解开外层 {code,msg,data}，直接是 {total, rows}
                // 兜底链保留，防止将来 service 层改回不解锁
                const raw = res as any;
                const rows = raw?.rows ?? raw?.data?.rows ?? raw?.data?.data?.rows ?? [];
                setOptions(Array.isArray(rows) ? rows : []);
            })
            .catch(() => {
                if (mounted) setOptions([]);
            })
            .finally(() => {
                if (mounted) setLoading(false);
            });
        return () => {
            mounted = false;
        };
    }, [modelType]);

    // 未显式选择时，与后端 _resolve_model 口径保持一致：default_flag=1 → 否则列表首个
    useEffect(() => {
        const current =
            value !== null && value !== undefined
                ? options.find((o) => o.model_id === value)
                : options.find((o) => o.is_default) ?? options[0];
        cbRef.current?.(current ?? null);
    }, [options, value]);

    return (
        <Select
            loading={loading}
            disabled={disabled || loading}
            value={value}
            onChange={onChange}
            placeholder={placeholder || '默认模型'}
            allowClear
            style={{ width: '100%' }}
            options={options.map((o) => ({
                value: o.model_id,
                label: (
                    <span>
                        {o.model_alias || o.model_name}
                        {o.is_default ? (
                            <span style={{ color: '#52c41a', marginLeft: 6 }}>(推荐)</span>
                        ) : null}
                    </span>
                ),
            }))}
        />
    );
};

export default ModelSelect;
