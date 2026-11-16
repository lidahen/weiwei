import { feedPublish } from '@/services/marketplace/api';
import { getToken } from '@/utils/twelvet';
import TWT from '@/setting';
import { IMG_FALLBACK, staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Form, Image, Input, Select, Upload, message, Card } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import React, { useState } from 'react';
import { history } from 'umi';
import type { UploadFile } from 'antd/es/upload/interface';

const { TextArea } = Input;
const { Dragger } = Upload;

const FeedPublish: React.FC = () => {
    const [loading, setLoading] = useState(false);
    const [fileList, setFileList] = useState<UploadFile[]>([]);

    /**
     * onChange 时把 commonUpload 响应里的相对路径（{code,msg,data:'path'}）回填到
     * file.url：既让 antd 自带缩略图正常显示，也作为提交时的数据源。
     */
    const handleChange = (info: { fileList: UploadFile[] }) => {
        const next = info.fileList.map((f) => {
            const path = (f as any)?.response?.data;
            if (f.status === 'done' && path && !f.url) {
                return { ...f, url: staticUrl(path) };
            }
            return f;
        });
        setFileList(next);
    };

    const handleSubmit = async (values: any) => {
        const paths = fileList
            .filter((f) => f.status === 'done' && (f as any)?.response?.data)
            .map((f) => (f as any).response.data as string);
        if (paths.length === 0 && !values.content?.trim()) {
            message.warning('请至少输入内容或上传图片');
            return;
        }
        setLoading(true);
        try {
            await feedPublish({
                content: values.content?.trim(),
                images: JSON.stringify(paths),
                relatedProductId: undefined,
                visibility: values.visibility,
            });
            message.success('发布成功，等待审核');
            history.push('/feed/stream');
        } catch {
            message.error('发布失败，请稍后再试');
        } finally {
            setLoading(false);
        }
    };

    return (
        <PageContainer header={{ title: '发布动态', breadcrumb: {} }}>
            <Card style={{ maxWidth: 600, margin: '0 auto', borderRadius: 12 }}>
                <Form onFinish={handleSubmit} layout="vertical">
                    <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
                        <TextArea rows={4} placeholder="分享你的闲置好物..." maxLength={2000} showCount />
                    </Form.Item>

                    <Form.Item label="图片" style={{ marginBottom: 16 }}>
                        <Dragger
                            multiple
                            maxCount={9}
                            listType="picture-card"
                            accept="image/*"
                            fileList={fileList}
                            onChange={handleChange}
                            onRemove={(file) => setFileList((prev) => prev.filter((f) => f.uid !== file.uid))}
                            action={`${TWT.requestUri}/dfs/commonUpload`}
                            headers={{ Authorization: `Bearer ${getToken()}` }}
                            beforeUpload={(file) => {
                                const isImage = file.type.startsWith('image/');
                                if (!isImage) {
                                    message.error('只能上传图片文件！');
                                    return false;
                                }
                                const isLt5M = file.size / 1024 / 1024 < 5;
                                if (!isLt5M) {
                                    message.error('图片大小不能超过 5MB！');
                                    return false;
                                }
                                return isImage && isLt5M;
                            }}
                        >
                            <p className="ant-upload-drag-icon">
                                <InboxOutlined />
                            </p>
                            <p className="ant-upload-text">点击或拖拽图片到此区域(最多9张)</p>
                            <p className="ant-upload-hint">支持 jpg/png 格式，单张不超过 5MB</p>
                        </Dragger>
                        {/* 已上传图片预览（antd 缩略图之外的大图确认） */}
                        {fileList.some((f) => f.url) && (
                            <Image.PreviewGroup>
                                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
                                    {fileList
                                        .filter((f) => f.url)
                                        .map((f) => (
                                            <Image
                                                key={f.uid}
                                                src={f.url}
                                                fallback={IMG_FALLBACK}
                                                width={80}
                                                height={80}
                                                style={{ objectFit: 'cover', borderRadius: 8 }}
                                            />
                                        ))}
                                </div>
                            </Image.PreviewGroup>
                        )}
                    </Form.Item>

                    <Form.Item label="可见范围" name="visibility" initialValue="public" style={{ marginBottom: 24 }}>
                        <Select style={{ width: '100%' }}>
                            <Select.Option value="public">公开 - 所有人都能看到</Select.Option>
                            <Select.Option value="friends">仅粉丝 - 只有关注你的人能看到</Select.Option>
                            <Select.Option value="self">仅自己 - 只有你自己能看到</Select.Option>
                        </Select>
                    </Form.Item>

                    <Form.Item style={{ marginBottom: 0 }}>
                        <Button type="primary" htmlType="submit" block size="large" loading={loading}>
                            发布动态
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </PageContainer>
    );
};

export default FeedPublish;
