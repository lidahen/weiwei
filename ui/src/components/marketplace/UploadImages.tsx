import { PlusOutlined } from '@ant-design/icons';
import { Image, Upload, message } from 'antd';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';
import React, { useState } from 'react';
import TWT from '@/setting';

interface Props {
    value?: UploadFile[];
    onChange?: (files: UploadFile[]) => void;
    max?: number;
}


const UploadImages: React.FC<Props> = ({ value = [], onChange, max = 9 }) => {
    const [previewOpen, setPreviewOpen] = useState(false);
    const [previewImage, setPreviewImage] = useState('');

    const getBase64 = (file: File): Promise<string> =>
        new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = () => resolve(reader.result as string);
            reader.onerror = (e) => reject(e);
        });

    const handlePreview = async (file: UploadFile) => {
        if (!file.url && !file.preview) {
            file.preview = await getBase64(file.originFileObj as File);
        }
        setPreviewImage(file.url || (file.preview as string));
        setPreviewOpen(true);
    };

    const handleChange: UploadProps['onChange'] = ({ fileList: newFileList }) => {
        onChange?.(newFileList);
    };

    const uploadButton = (
        <div>
            <PlusOutlined />
            <div style={{ marginTop: 8 }}>上传</div>
        </div>
    );

    const local = localStorage.getItem(TWT.accessToken);
    let { access_token } = local ? JSON.parse(local) : { access_token: '' };

    return (
        <>
            <Upload
                listType="picture-card"
                action={`${TWT.requestUri}/dfs/commonUpload`}
                headers={{ Authorization: `Bearer ${access_token}` }}
                fileList={value}
                onPreview={handlePreview}
                onChange={handleChange}
                beforeUpload={(file) => {
                    const isImage = file.type.startsWith('image/');
                    if (!isImage) {
                        message.error('只能上传图片文件');
                        return Upload.LIST_IGNORE;
                    }
                    const isLt10M = file.size / 1024 / 1024 < 10;
                    if (!isLt10M) {
                        message.error('图片大小不能超过 10MB');
                        return Upload.LIST_IGNORE;
                    }
                    return file;
                }}
                maxCount={max}
                multiple
                accept="image/*"
            >
                {value.length >= max ? null : uploadButton}
            </Upload>
            {previewImage && (
                <Image
                    wrapperStyle={{ display: 'none' }}
                    preview={{
                        visible: previewOpen,
                        onVisibleChange: (visible) => setPreviewOpen(visible),
                        afterOpenChange: (visible) => !visible && setPreviewImage(''),
                    }}
                    src={previewImage}
                />
            )}
        </>
    );
};

export default UploadImages;
