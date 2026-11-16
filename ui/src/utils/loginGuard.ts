import { history } from '@umijs/max';
import { getToken } from '@/utils/twelvet';

export function isLoggedIn(): boolean {
    return !!getToken();
}

/** 需登录动作的守卫：未登录时跳登录页并返回 false */
export function requireLogin(): boolean {
    if (isLoggedIn()) return true;
    history.push('/login');
    return false;
}