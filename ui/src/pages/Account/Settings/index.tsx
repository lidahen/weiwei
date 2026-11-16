import React, {useEffect, useMemo, useState} from 'react'

import {PageContainer} from '@ant-design/pro-components'
import {Card, Col, Row, Space, Tabs} from 'antd'
import {
    FieldTimeOutlined,
    ForkOutlined,
    MailOutlined,
    PhoneOutlined,
    TeamOutlined,
    UserOutlined
} from '@ant-design/icons'
import {getUserProfile, updateAvatar} from './service'
import UserInfo from './components/UserInfo'
import RestPassword from './components/RestPassword'
import './styles.less'
import Upload from '@/components/TwelveT/Upload'
import {flushSync} from 'react-dom'
import {getCurrentUser, getRouters} from '@/pages/Login/service'
import {useModel} from '@umijs/max'

/**
 * 个人资料设置
 *
 * 数据源说明：表单回显必须用登录态 currentUser.user（来源 GET /system/user/getInfo，
 * 手机/邮箱是明文且带 sex/avatar）；GET /system/user/profile 是脱敏副本
 * （手机/邮箱带 ****、无 sex/avatar/createTime），只用来补 postGroup/roleGroup 展示字段。
 */
const UserSettings: React.FC = () => {

    const {initialState, setInitialState} = useModel('@@initialState');
    const currentUser = ((initialState as any)?.currentUser?.user || {}) as Record<string, any>;

    // 仅用于"所属部门 / 所属角色"两个展示字段，不参与表单回显
    const [groupInfo, setGroupInfo] = useState<{ postGroup: string; roleGroup: string }>({
        postGroup: '',
        roleGroup: '',
    });

    useEffect(() => {
        getUserProfile()
            .then(({data}: any) => setGroupInfo({
                postGroup: data?.postGroup || '',
                roleGroup: data?.roleGroup || '',
            }))
            .catch(() => {
                // 展示字段失败不阻塞表单回显
            })
    }, [])

    // 头像上传 / 资料保存后刷新登录态，保证回显与右上角信息同步
    const refreshCurrentUser = async () => {
        const {user = {}, roles, permissions} = await getCurrentUser();
        const {data} = await getRouters();
        flushSync(() => {
            setInitialState((s: any) => ({
                ...s,
                currentUser: {user, menus: data, roles, permissions},
            }))
        })
    }

    // useMemo 固定引用：UserInfo 的 useEffect 依赖 props.user，若每次渲染都传新字面量，
    // 父组件任意重渲染都会把表单重置回旧值，表现为"输入被吞掉 / 回显不稳定"。
    const formUser = useMemo(() => ({
        username: currentUser.username,
        nickName: currentUser.nickName,
        sex: currentUser.sex,
        phonenumber: currentUser.phonenumber,
        email: currentUser.email,
    }), [
        currentUser.username,
        currentUser.nickName,
        currentUser.sex,
        currentUser.phonenumber,
        currentUser.email,
    ]);

    return (
        <PageContainer>
            <Row gutter={[20, 20]}>
                <Col xl={{span: 8}} xs={{span: 24}}>
                    <Card title="个人信息">
                        <div className="text-center">
                            <Upload
                                name='avatarFile'
                                // 开启图片剪裁
                                imgCrop={true}
                                title='用户头像'
                                maxCount={1}
                                action={updateAvatar}
                                images={[
                                    currentUser.avatar
                                ]}
                                // 重新渲染个人信息
                                success={() => refreshCurrentUser()}
                            />
                        </div>

                        <ul className="list-group list-group-striped">
                            <li className="list-group-item">
                                <Space>
                                    <UserOutlined/>
                                    用户名称
                                </Space>

                                <div className="pull-right">{currentUser.username}</div>
                            </li>
                            <li className="list-group-item">
                                <Space>
                                    <PhoneOutlined/>
                                    手机号码
                                </Space>
                                <div className="pull-right">{currentUser.phonenumber}</div>
                            </li>
                            <li className="list-group-item">
                                <Space>
                                    <MailOutlined/>
                                    用户邮箱
                                </Space>
                                <div className="pull-right">{currentUser.email}</div>
                            </li>
                            <li className="list-group-item">
                                <Space>
                                    <ForkOutlined/>
                                    所属部门
                                </Space>
                                {
                                    currentUser.dept ? <div
                                        className="pull-right">{currentUser.dept.deptName} / {groupInfo.postGroup}</div> : ''
                                }

                            </li>
                            <li className="list-group-item">
                                <Space>
                                    <TeamOutlined/>
                                    所属角色
                                </Space>
                                <div className="pull-right">{groupInfo.roleGroup}</div>
                            </li>
                            <li className="list-group-item">
                                <Space>
                                    <FieldTimeOutlined/>
                                    创建时间
                                </Space>
                                <div className="pull-right">{currentUser.createTime || '-'}</div>
                            </li>
                        </ul>
                    </Card>
                </Col>

                <Col xl={{span: 16}} xs={{span: 24}}>
                    <Card title="基本资料">
                        <Tabs
                            defaultActiveKey={`basicInformation`}
                            items={[
                                {
                                    key: 'basicInformation',
                                    label: `基本信息`,
                                    children: (
                                        <UserInfo
                                            user={formUser}
                                            onSaved={() => refreshCurrentUser()}
                                        />
                                    ),
                                },
                                {
                                    key: 'changePassword',
                                    label: `修改密码`,
                                    children: <RestPassword/>,
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </PageContainer>
    );
};

export default UserSettings
