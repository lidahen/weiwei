import { LockOutlined, MobileOutlined, UserOutlined, VerifiedOutlined } from '@ant-design/icons';
import {
    CaptFieldRef,
    ProFormCaptcha,
    ProFormCheckbox,
    ProFormText,
} from '@ant-design/pro-components';
import {
    FormattedMessage,
    Helmet,
    history,
    SelectLang,
    useIntl,
    useModel,
} from '@umijs/max';
import { Alert, Button, Form, Tabs, message } from 'antd';
import { createStyles } from 'antd-style';
import React, { useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import Footer from '@/components/Footer';
import { useGsapReveal } from '@/hooks/useGsapReveal';
import { setAuthority } from '@/utils/twelvet';
import Settings from '../../../config/defaultSettings';
import AnimatedBackground from '@/pages/Login/components/AnimatedBackground';
import BrandHeader from '@/pages/Login/components/BrandHeader';
import LoginCard from '@/pages/Login/components/LoginCard';
import { login, sendSmsCode, smsLogin } from './service';

const useStyles = createStyles(() => ({
    root: {
        position: 'relative',
        zIndex: 10,
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
    },
    main: {
        flex: 1,
        display: 'grid',
        gridTemplateColumns: '1fr',
        '@media (min-width: 1024px)': {
            gridTemplateColumns: '1fr 1fr',
        },
    },
    left: {
        background: '#EBE4D5',
        padding: '48px 32px',
        display: 'flex',
        flexDirection: 'column',
        minHeight: '60vh',
        '@media (min-width: 1024px)': {
            padding: '80px 64px',
            minHeight: 'auto',
        },
    },
    right: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 24px',
        '@media (min-width: 1024px)': {
            padding: '64px 48px',
        },
    },
    rightInner: {
        width: '100%',
        maxWidth: 400,
    },
    sectionHeader: {
        marginBottom: 32,
    },
    sectionEyebrow: {
        fontSize: 11,
        letterSpacing: 4,
        textTransform: 'uppercase',
        color: '#9A9A92',
        marginBottom: 8,
    },
    sectionTitle: {
        fontFamily: "'Cormorant Garamond', 'Source Han Serif SC', 'Noto Serif SC', serif",
        fontSize: 32,
        fontWeight: 400,
        color: '#2D2D2A',
        margin: 0,
        lineHeight: 1.2,
    },
}));

const LoginMessage: React.FC<{
    content: string;
}> = ({ content }) => {
    return (
        <Alert
            style={{
                marginBottom: 24,
            }}
            message={content}
            type="error"
            showIcon
        />
    );
};

const Login: React.FC = () => {
    const { styles } = useStyles();
    const [userLoginState, setUserLoginState] = useState<API.LoginResult>({});
    const [type, setType] = useState<string>('account');
    const [loginLoading, setLoginLoading] = useState(false);
    const { initialState, setInitialState } = useModel('@@initialState');
    const intl = useIntl();
    const captchaRef = useRef<CaptFieldRef | null | undefined>();

    const [loginForm] = Form.useForm();

    // 右栏容器级 stagger 入场 —— 小标题 / 表单 / 按钮 / Demo / OAuth 依次浮现
    const revealRef = useGsapReveal<HTMLDivElement>({ stagger: 0.07, duration: 0.5, y: 20 });

    const fetchUserInfo = async () => {
        const userInfo = await initialState?.fetchUserInfo?.();
        if (userInfo) {
            flushSync(() => {
                setInitialState((s) => ({
                    ...s,
                    currentUser: {
                        user: userInfo.user,
                        menus: userInfo.menus,
                        permissions: userInfo.permissions,
                        roles: userInfo.roles,
                    },
                }));
            });
        }
        return userInfo;
    };

    const handleSubmit = async (values: API.LoginParams) => {
        const fv = values as Record<string, unknown>;
        try {
            setLoginLoading(true);

            const data =
                type === 'account'
                    ? await login({
                          ...(fv.autoLogin ? { autoLogin: true } : {}),
                          username: fv.username,
                          password: fv.password,
                          grantType: 'password',
                          type,
                      } as any)
                    : await smsLogin(fv.mobile as string, fv.code as string);
            if (data.code === 200) {
                setAuthority(data.data);

                const defaultloginSuccessMessage = intl.formatMessage({
                    id: 'pages.login.success',
                    defaultMessage: '登录成功！',
                });

                message.success(defaultloginSuccessMessage);

                setLoginLoading(false);

                const userInfo = await fetchUserInfo();

                const urlParams = new URL(window.location.href).searchParams;
                // 按角色落地：运营/客服进各自工作台首页，其余账号进邻里二手工作台（/home）
                const roles: string[] = userInfo?.roles ?? [];
                const roleHomeMap: Record<string, string> = {
                    ops: '/admin/marketplace/dashboard',
                    cs: '/cs/marketplace/disputes',
                };
                const roleHome = roles.map((role) => roleHomeMap[role]).find(Boolean);
                history.push(urlParams.get('redirect') || roleHome || '/home');
                return;
            }

            message.error(data.msg);
            setLoginLoading(false);
            setUserLoginState(data.msg);
        } catch (error) {
            const defaultloginFailureMessage = intl.formatMessage({
                id: 'pages.login.failure',
                defaultMessage: '登录失败，请重试！',
            });

            message.error(defaultloginFailureMessage);
            setLoginLoading(false);
        }
    };

    const handleLoginSubmit = async () => {
        const values = await loginForm.validateFields();
        await handleSubmit(values as API.LoginParams);
    };

    const { status, type: loginType } = userLoginState;

    return (
        <>
            <AnimatedBackground />
            <div className={`login-bg ${styles.root}`}>
                <Helmet>
                    <title>
                        {intl.formatMessage({
                            id: 'menu.login',
                            defaultMessage: '登录页',
                        })}
                        - {Settings.title}
                    </title>
                </Helmet>
                {SelectLang && (
                    <div
                        data-lang
                        style={{
                            position: 'fixed',
                            top: 16,
                            right: 16,
                            zIndex: 20,
                            width: 42,
                            height: 42,
                            lineHeight: '42px',
                            cursor: 'pointer',
                        }}
                    >
                        <SelectLang />
                    </div>
                )}

                <div className={styles.main}>
                    {/* 左侧品牌区 */}
                    <section className={styles.left}>
                        <BrandHeader />
                    </section>

                    {/* 右侧登录表单区 */}
                    <section className={styles.right}>
                        <LoginCard>
                            <div ref={revealRef} className={styles.rightInner}>
                                <div className={`${styles.sectionHeader} reveal-item`}>
                                    <div className={styles.sectionEyebrow}>
                                        Welcome back
                                    </div>
                                    <h2 className={styles.sectionTitle}>登录</h2>
                                </div>

                                <div className="reveal-item">
                                    <Form
                                        form={loginForm}
                                        initialValues={{
                                            autoLogin: true,
                                        }}
                                    >
                                        <Tabs
                                            activeKey={type}
                                            onChange={setType}
                                            centered
                                            items={[
                                                {
                                                    key: 'account',
                                                    label: intl.formatMessage({
                                                        id: 'pages.login.accountLogin.tab',
                                                        defaultMessage: '账户密码登录',
                                                    }),
                                                },
                                                {
                                                    key: 'mobile',
                                                    label: intl.formatMessage({
                                                        id: 'pages.login.phoneLogin.tab',
                                                        defaultMessage: '手机号登录',
                                                    }),
                                                },
                                            ]}
                                        />

                                        {status === 'error' &&
                                            loginType === 'account' && (
                                                <LoginMessage
                                                    content={intl.formatMessage({
                                                        id: 'pages.login.accountLogin.errorMessage',
                                                        defaultMessage:
                                                            '账户或密码错误',
                                                    })}
                                                />
                                            )}
                                        {type === 'account' && (
                                            <>
                                                <ProFormText
                                                    name="username"
                                                    fieldProps={{
                                                        size: 'large',
                                                        prefix: <UserOutlined />,
                                                    }}
                                                    placeholder={intl.formatMessage({
                                                        id: 'pages.login.username.placeholder',
                                                        defaultMessage: '用户名',
                                                    })}
                                                    rules={[
                                                        {
                                                            required: true,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.username.required"
                                                                    defaultMessage="请输入用户名!"
                                                                />
                                                            ),
                                                        },
                                                    ]}
                                                />
                                                <ProFormText.Password
                                                    name="password"
                                                    fieldProps={{
                                                        size: 'large',
                                                        prefix: <LockOutlined />,
                                                    }}
                                                    placeholder={intl.formatMessage({
                                                        id: 'pages.login.password.placeholder',
                                                        defaultMessage: '密码',
                                                    })}
                                                    rules={[
                                                        {
                                                            required: true,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.password.required"
                                                                    defaultMessage="请输入密码！"
                                                                />
                                                            ),
                                                        },
                                                    ]}
                                                />
                                            </>
                                        )}

                                        {status === 'error' && loginType === 'mobile' && (
                                            <LoginMessage content="验证码错误" />
                                        )}
                                        {type === 'mobile' && (
                                            <>
                                                <ProFormText
                                                    fieldProps={{
                                                        size: 'large',
                                                        prefix: <MobileOutlined />,
                                                    }}
                                                    name="mobile"
                                                    placeholder={intl.formatMessage({
                                                        id: 'pages.login.phoneNumber.placeholder',
                                                        defaultMessage: '手机号',
                                                    })}
                                                    rules={[
                                                        {
                                                            required: true,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.phoneNumber.required"
                                                                    defaultMessage="请输入手机号！"
                                                                />
                                                            ),
                                                        },
                                                        {
                                                            pattern: /^1[3-9]\d{9}$/,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.phoneNumber.invalid"
                                                                    defaultMessage="手机号格式错误！"
                                                                />
                                                            ),
                                                        },
                                                    ]}
                                                />
                                                <ProFormCaptcha
                                                    fieldRef={captchaRef}
                                                    fieldProps={{
                                                        size: 'large',
                                                        prefix: <VerifiedOutlined />,
                                                    }}
                                                    captchaProps={{
                                                        size: 'large',
                                                    }}
                                                    placeholder={intl.formatMessage({
                                                        id: 'pages.login.captcha.placeholder',
                                                        defaultMessage: '请输入验证码',
                                                    })}
                                                    captchaTextRender={(timing, count) => {
                                                        if (timing) {
                                                            return `${count} ${intl.formatMessage(
                                                                {
                                                                    id: 'pages.getCaptchaSecondText',
                                                                    defaultMessage:
                                                                        '获取验证码',
                                                                },
                                                            )}`;
                                                        }
                                                        return intl.formatMessage({
                                                            id: 'pages.login.phoneLogin.getVerificationCode',
                                                            defaultMessage: '获取验证码',
                                                        });
                                                    }}
                                                    phoneName="mobile"
                                                    name="code"
                                                    rules={[
                                                        {
                                                            required: true,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.captcha.required"
                                                                    defaultMessage="请输入验证码！"
                                                                />
                                                            ),
                                                        },
                                                        {
                                                            pattern: /^\d{6}$/,
                                                            message: (
                                                                <FormattedMessage
                                                                    id="pages.login.phoneLogin.errorMessage"
                                                                    defaultMessage="验证码不正确"
                                                                />
                                                            ),
                                                        },
                                                    ]}
                                                    onGetCaptcha={async (phone) => {
                                                        const res: any = await sendSmsCode(phone);
                                                        if (res?.data?.mockCode) {
                                                            loginForm.setFieldValue(
                                                                'code',
                                                                res.data.mockCode,
                                                            );
                                                            message.info(
                                                                `当前为调试模式，验证码：${res.data.mockCode}`,
                                                            );
                                                        } else {
                                                            message.success('验证码已发送');
                                                        }
                                                    }}
                                                />
                                            </>
                                        )}

                                        <div
                                            style={{
                                                marginBottom: 24,
                                            }}
                                        >
                                            <ProFormCheckbox noStyle name="autoLogin">
                                                <FormattedMessage
                                                    id="pages.login.rememberMe"
                                                    defaultMessage="自动登录"
                                                />
                                            </ProFormCheckbox>
                                            <a
                                                style={{
                                                    float: 'right',
                                                }}
                                            >
                                                <FormattedMessage
                                                    id="pages.login.forgotPassword"
                                                    defaultMessage="忘记密码"
                                                />
                                            </a>
                                        </div>

                                        <Button
                                            type="primary"
                                            size="large"
                                            block
                                            loading={loginLoading}
                                            onClick={handleLoginSubmit}
                                            style={{ marginBottom: 24 }}
                                        >
                                            {intl.formatMessage({
                                                id: 'pages.login.submit',
                                                defaultMessage: '登 录',
                                            })}
                                        </Button>
                                    </Form>
                                </div>
                            </div>
                        </LoginCard>
                    </section>
                </div>

                <Footer />
            </div>
        </>
    );
};

export default Login;
