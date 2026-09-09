import { defineStore } from 'pinia';

import { fetchCurrentPermissions } from '@/api/rent';
import { usePermissionStore } from '@/store';
import type { UserInfo } from '@/types/interface';

type RentUserInfo = UserInfo & {
  roleCodes: string[];
  pageCodes: string[];
  buttonCodes: string[];
};

const InitUserInfo: RentUserInfo = {
  name: '',
  roles: [],
  roleCodes: [],
  pageCodes: [],
  buttonCodes: [],
};

const readStoredUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || {};
  } catch (error) {
    return {};
  }
};

const normalizeUser = (storedUser: Record<string, unknown>, permission: Record<string, unknown> = {}): RentUserInfo => {
  const roleCodes = (permission.roleCodes || storedUser.roleCodes || []) as string[];
  return {
    ...storedUser,
    ...permission,
    name: String(
      storedUser.realName || storedUser.nickName || storedUser.username || storedUser.userName || storedUser.name || '',
    ),
    roles: roleCodes,
    roleCodes,
    pageCodes: (permission.pageCodes || storedUser.pageCodes || []) as string[],
    buttonCodes: (permission.buttonCodes || storedUser.buttonCodes || []) as string[],
  };
};

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: { ...InitUserInfo },
  }),
  getters: {
    roles: (state) => state.userInfo?.roles,
  },
  actions: {
    async login(userInfo: Record<string, unknown>) {
      const token = String(userInfo.token || localStorage.getItem('token') || '');
      if (!token) throw new Error('请先通过中台统一登录');
      this.token = token;
      localStorage.setItem('token', token);
      await this.getUserInfo();
    },
    async getUserInfo() {
      const storedUser = readStoredUserInfo();
      const res = await fetchCurrentPermissions();
      const permission = (res.data?.data || {}) as Record<string, unknown>;
      this.token = localStorage.getItem('token') || '';
      this.userInfo = normalizeUser(storedUser, permission);
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo));
      localStorage.removeItem('alipayPermissions');
      return this.userInfo;
    },
    async logout() {
      this.token = '';
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      localStorage.removeItem('alipayPermissions');
      this.userInfo = { ...InitUserInfo };
    },
  },
  persist: {
    afterRestore: () => {
      const permissionStore = usePermissionStore();
      permissionStore.initRoutes(readStoredUserInfo());
    },
    key: 'alipay-tdesign-user',
    paths: ['token'],
  },
});
