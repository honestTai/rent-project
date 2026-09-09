import { defineStore } from 'pinia';

import { usePermissionStore } from '@/store';
import type { UserInfo } from '@/types/interface';

const InitUserInfo: UserInfo = {
  name: '', // 用户名，用于展示在页面右上角头像处
  roles: [], // 前端权限模型使用 如果使用请配置modules/permission-fe.ts使用
};

const readStoredUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || {};
  } catch (error) {
    return {};
  }
};

const readRbacRoleCodes = (user: Record<string, any>) => {
  return Array.isArray(user.roleCodes) ? user.roleCodes : [];
};

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: { ...InitUserInfo },
  }),
  getters: {
    roles: (state) => {
      return state.userInfo?.roles;
    },
  },
  actions: {
    async login(userInfo: Record<string, unknown>) {
      const { login } = await import('@/api/platform');
      const res = await login({
        userName: userInfo.account,
        userPwd: userInfo.password,
      });
      const body = res.data || {};
      if (Number(body.code) === 0 && body.data) {
        const token = body.data.token;
        const user = body.data.user || body.data;
        this.token = token;
        localStorage.setItem('token', token);
        localStorage.setItem('userInfo', JSON.stringify(user));
        localStorage.setItem('systemindex', '0');
        this.userInfo = {
          ...user,
          name: user.realName || user.nickName || user.username || user.userName || user.name || '',
          roles: readRbacRoleCodes(user),
        };
        return { token, user };
      } else {
        throw new Error(body.msg || '登录失败');
      }
    },
    async getUserInfo() {
      const storedUser = readStoredUserInfo();
      this.userInfo = {
        ...storedUser,
        name: storedUser.realName || storedUser.nickName || storedUser.username || storedUser.userName || storedUser.name || '',
        roles: readRbacRoleCodes(storedUser),
      };
    },
    async logout() {
      this.token = '';
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      this.userInfo = { ...InitUserInfo };
    },
  },
  persist: {
    afterRestore: () => {
      if (!localStorage.getItem('token')) return;
      const permissionStore = usePermissionStore();
      permissionStore.initRoutes();
    },
    key: 'user',
    paths: ['token'],
  },
});
