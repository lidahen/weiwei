import { PlusOutlined } from '@ant-design/icons';
import { Upload } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React from 'react';

export interface UploadImagesProps {
    value?: UploadFile[];
    onChange?: (files: UploadFile[]) => void;
    max?: number;
}

const UploadImages: React.FC<UploadImagesProps> = ({ value = [], onChange, max = 9 }) => (
    <Upload
        listType="picture-card"
        fileList={value}
        multiple
        beforeUpload={() => false}
        onChange={({ fileList }) => onChange?.(fileList.slice(0, max))}
        maxCount={max}
    >
        {value.length < max && (
            <div>
                <PlusOutlined />
                <div style={{ marginTop: 8 }}>
                    上传 ({value.length}/{max})
                </div>
            </div>
        )}
    </Upload>
);

export default UploadImages;
