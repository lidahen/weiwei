import { staticUrl } from './utils';
import { PageContainer } from '@ant-design/pro-components';
import { history, useModel, useRequest } from '@umijs/max';
import { Avatar, Button, Card, Descriptions, Form, Input, Modal, Space, Switch, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';
import { useIsMobile } from '@/hooks/useIsMobile';
import { updateMyProfile, updateMyPassword, userPrivacyGet, userPrivacySave } from '@/services/marketplace/api';
import { getCurrentUser, getRouters } from '@/pages/Login/service';

const userTypeMap: Record<string, string> = {
    '00': '平台运营',
    '01': '平台客服',
    '02': '普通用户',
};

const UserSettings: React.FC = () => {
    const isMobile = useIsMobile();
    const { initialState, setInitialState } = useModel('@@initialState');
    // 回显数据源用登录态 currentUser（GET /system/user/getInfo，手机/邮箱是明文）；
    // GET /system/user/profile 是脱敏副本（135****6788），不能拿来填表单。
    const user = (initialState as any)?.currentUser?.user || {};

    const [editOpen, setEditOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form] = Form.useForm();

    const [pwdOpen, setPwdOpen] = useState(false);
    const [pwdSaving, setPwdSaving] = useState(false);
    const [pwdForm] = Form.useForm();

    const handleNotImpl = () => message.info('此功能开发中,敬请期待');
    // 第三方账号绑定尚未接入 OAuth，先给可执行的客服过渡文案（避免「开发中」死路）
    const handleBind = () => message.info('请联系客服绑定，客服将在 1 个工作日内处理');

    // 隐私三开关：初值读接口（无记录时后端默认全开），保存乐观更新、失败回滚
    const { data: privacyResp, loading: privacyLoading } = useRequest(() => userPrivacyGet());
    const [switches, setSwitches] = useState<Record<string, number>>({
        allowMobileSearch: 1,
        allowFeedByStranger: 1,
        enableNearbyRecommend: 1,
    });

    useEffect(() => {
        const payload = (privacyResp as any)?.data ?? (privacyResp as any);
        if (payload && typeof payload === 'object') {
            setSwitches({
                allowMobileSearch: Number(payload.allowMobileSearch ?? 1),
                allowFeedByStranger: Number(payload.allowFeedByStranger ?? 1),
                enableNearbyRecommend: Number(payload.enableNearbyRecommend ?? 1),
            });
        }
    }, [privacyResp]);

    const togglePrivacy = async (key: string, checked: boolean) => {
        const next = checked ? 1 : 0;
        const prev = switches[key] ?? 1;
        setSwitches((s) => ({ ...s, [key]: next }));
        try {
            await userPrivacySave({ [key]: next });
            message.success('已保存');
        } catch (e: any) {
            setSwitches((s) => ({ ...s, [key]: prev }));
            message.error(e?.response?.data?.msg || '保存失败，请稍后重试');
        }
    };

    // 打开时再灌值，避免页面首次渲染时 initialState 还没加载完导致表单空白
    const openEdit = () => {
        form.setFieldsValue({
            nickName: user.nickName || '',
            phonenumber: user.phonenumber || '',
            email: user.email || '',
            remark: user.remark || '',
        });
        setEditOpen(true);
    };

    const submitEdit = async () => {
        const values = await form.validateFields();
        setSaving(true);
        try {
            const res: any = await updateMyProfile({ username: user.username, ...values });
            if (res?.code !== 200) {
                message.error(res?.msg || '保存失败');
                return;
            }
            message.success(res.msg || '保存成功');
            // 刷新登录态：本页回显与右上角昵称/头像一起同步
            const { user: fresh = {}, roles, permissions } = await getCurrentUser();
            const { data: menus } = await getRouters();
            flushSync(() => {
                setInitialState((s: any) => ({
                    ...s,
                    currentUser: { user: fresh, menus, roles, permissions },
                }));
            });
            setEditOpen(false);
        } catch (e: any) {
            // 后端 TWTException（如"手机号已被占用"）走 500，需把后端文案透出来
            message.error(e?.info?.errorMessage || e?.response?.data?.msg || '保存失败');
        } finally {
            setSaving(false);
        }
    };

    const openPwd = () => {
        pwdForm.resetFields();
        setPwdOpen(true);
    };

    const submitPwd = async () => {
        const values = await pwdForm.validateFields();
        setPwdSaving(true);
        try {
            const res: any = await updateMyPassword(values);
            // 旧密码错误、新旧密码相同等业务失败，后端返回 HTTP 200 + code 500
            if (res?.code !== 200) {
                message.error(res?.msg || '修改失败');
                return;
            }
            message.success(res?.msg || '密码修改成功');
            setPwdOpen(false);
        } catch (e: any) {
            message.error(e?.info?.errorMessage || '修改失败');
        } finally {
            setPwdSaving(false);
        }
    };

    return (
        <PageContainer header={{ title: '账号设置', breadcrumb: {} }}>
            <Card
                title="基础资料"
                style={{ marginBottom: 16 }}
                extra={
                    <a
                        onClick={openEdit}
                        style={isMobile ? { padding: '8px 4px', display: 'inline-block' } : undefined}
                    >
                        编辑
                    </a>
                }
            >
                <Space size={isMobile ? 16 : 24} align="start" style={{ width: '100%' }}>
                    <Avatar src={staticUrl(user.avatar)} size={isMobile ? 64 : 96} />
                    <Descriptions column={1} style={{ flex: 1 }}>
                        <Descriptions.Item label="昵称">{user.nickName || '-'}</Descriptions.Item>
                        <Descriptions.Item label="账号">{user.username || '-'}</Descriptions.Item>
                        <Descriptions.Item label="手机号">
                            {user.phonenumber || '未绑定'}
                        </Descriptions.Item>
                        <Descriptions.Item label="邮箱">{user.email || '未绑定'}</Descriptions.Item>
                        <Descriptions.Item label="角色">
                            <Tag color="orange">{userTypeMap[user.userType] || '未知'}</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="个性签名">
                            {user.remark || '这个人很懒,什么都没留下'}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            <Card title="账号安全" style={{ marginBottom: 16 }}>
                <Descriptions column={1}>
                    <Descriptions.Item label="登录密码">
                        已设置{' '}
                        <a
                            onClick={openPwd}
                            style={isMobile ? { marginLeft: 12, padding: '8px 4px', display: 'inline-block' } : { marginLeft: 12 }}
                        >
                            修改
                        </a>
                    </Descriptions.Item>
                    <Descriptions.Item label="绑定微信">
                        未绑定{' '}
                        <a
                            onClick={handleBind}
                            style={isMobile ? { marginLeft: 12, padding: '8px 4px', display: 'inline-block' } : { marginLeft: 12 }}
                        >
                            去绑定
                        </a>
                    </Descriptions.Item>
                    <Descriptions.Item label="绑定支付宝">
                        未绑定{' '}
                        <a
                            onClick={handleBind}
                            style={isMobile ? { marginLeft: 12, padding: '8px 4px', display: 'inline-block' } : { marginLeft: 12 }}
                        >
                            去绑定
                        </a>
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card title="隐私设置" style={{ marginBottom: 16 }}>
                <Descriptions column={1}>
                    <Descriptions.Item label="允许通过手机号搜到我">
                        <Switch
                            checked={Number(switches.allowMobileSearch ?? 1) === 1}
                            loading={privacyLoading}
                            onChange={(v) => togglePrivacy('allowMobileSearch', v)}
                        />
                    </Descriptions.Item>
                    <Descriptions.Item label="允许陌生人给我发动态评论">
                        <Switch
                            checked={Number(switches.allowFeedByStranger ?? 1) === 1}
                            loading={privacyLoading}
                            onChange={(v) => togglePrivacy('allowFeedByStranger', v)}
                        />
                    </Descriptions.Item>
                    <Descriptions.Item label="开启同城推荐">
                        <Switch
                            checked={Number(switches.enableNearbyRecommend ?? 1) === 1}
                            loading={privacyLoading}
                            onChange={(v) => togglePrivacy('enableNearbyRecommend', v)}
                        />
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card>
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Button block onClick={() => history.push('/user/address')}>
                        管理收货地址
                    </Button>
                    <Button block onClick={() => history.push('/user/follows')}>
                        关注与粉丝
                    </Button>
                    <Button block danger onClick={handleNotImpl}>
                        注销账号
                    </Button>
                </Space>
            </Card>

            <Modal
                title="编辑基础资料"
                open={editOpen}
                onOk={submitEdit}
                confirmLoading={saving}
                onCancel={() => setEditOpen(false)}
                okText="保存"
                cancelText="取消"
                forceRender
            >
                <Form form={form} layout="vertical" preserve={false}>
                    <Form.Item
                        name="nickName"
                        label="昵称"
                        rules={[
                            { required: true, message: '请输入昵称' },
                            { max: 30, message: '昵称最多 30 个字符' },
                        ]}
                    >
                        <Input placeholder="昵称" maxLength={30} />
                    </Form.Item>
                    <Form.Item
                        name="phonenumber"
                        label="手机号"
                        extra="留空表示不修改"
                        rules={[{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}
                    >
                        <Input placeholder="11 位手机号" maxLength={11} />
                    </Form.Item>
                    <Form.Item
                        name="email"
                        label="邮箱"
                        extra="留空表示不修改"
                        rules={[{ type: 'email', message: '邮箱格式不正确' }]}
                    >
                        <Input placeholder="邮箱" maxLength={50} />
                    </Form.Item>
                    <Form.Item
                        name="remark"
                        label="个性签名"
                        rules={[{ max: 200, message: '个性签名最多 200 个字符' }]}
                    >
                        <Input.TextArea rows={3} placeholder="介绍一下自己" showCount maxLength={200} />
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                title="修改登录密码"
                open={pwdOpen}
                onOk={submitPwd}
                confirmLoading={pwdSaving}
                onCancel={() => setPwdOpen(false)}
                okText="确认修改"
                cancelText="取消"
                forceRender
            >
                <Form form={pwdForm} layout="vertical" preserve={false}>
                    <Form.Item
                        name="oldPassword"
                        label="当前密码"
                        rules={[{ required: true, message: '请输入当前密码' }]}
                    >
                        <Input.Password placeholder="当前登录密码" maxLength={32} />
                    </Form.Item>
                    <Form.Item
                        name="newPassword"
                        label="新密码"
                        rules={[
                            { required: true, message: '请输入新密码' },
                            { min: 6, message: '新密码至少 6 位' },
                        ]}
                    >
                        <Input.Password placeholder="至少 6 位" maxLength={32} />
                    </Form.Item>
                    <Form.Item
                        name="confirmPassword"
                        label="确认新密码"
                        dependencies={['newPassword']}
                        rules={[
                            { required: true, message: '请再次输入新密码' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('newPassword') === value) {
                                        return Promise.resolve();
                                    }
                                    return Promise.reject(new Error('两次输入的新密码不一致'));
                                },
                            }),
                        ]}
                    >
                        <Input.Password placeholder="再次输入新密码" maxLength={32} />
                    </Form.Item>
                </Form>
            </Modal>
        </PageContainer>
    );
};

export default UserSettings;
