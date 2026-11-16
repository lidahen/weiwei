import {Space, Tag} from "antd";
import React from "react";

const TagList: React.FC<{
    value?: {
        key: string;
        label: string;
    }[];
    onChange?: (
        value: {
            key: string;
            label: string;
        }[],
    ) => void;
}> = ({value}) => {

    // 原先直接写 {value}：value 是对象数组，非空时 React 会抛
    // "Objects are not valid as a React child"。当前调用方（EditCode/index.tsx:314,329）
    // 未传 value 所以尚未暴露，但组件语义（名称 TagList、props 为 key/label）显然是渲染标签。
    return (
        <Space>
            {value?.map((item) => (
                <Tag key={item.key}>{item.label}</Tag>
            ))}
        </Space>
    );
};

export default TagList;
