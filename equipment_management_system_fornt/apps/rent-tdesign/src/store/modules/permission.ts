import { defineStore } from 'pinia';
import type { RouteRecordRaw } from 'vue-router';

import router from '@/router';
import { loadRuntimeImageConfig } from '@/api/rent';
import { alipayPages, buildAuthorizedAlipayRoute, flattenAlipayRoutes } from '@/router/modules/alipay';
import { store } from '@/store';
import { hasPagePermission, isSuperAdmin } from '@/utils/rbac';

const DYNAMIC_ROUTE_NAME = 'alipay';
const SYSTEM_CODE = 'alipay';

type PermissionSource = Record<string, unknown>;

const normalizeList = (value: unknown): string[] => (Array.isArray(value) ? value.map((item) => String(item)) : []);

const hasSuperAdminRole = (roleCodes: string[]) => roleCodes.includes('super_admin');

const collectAllPageCodes = () => {
  const result = new Set<string>();
  const walk = (pages: typeof alipayPages) => {
    pages.forEach((page) => {
      result.add(`${SYSTEM_CODE}:${page.pageCode}`);
      if (page.children?.length) {
        walk(page.children as typeof alipayPages);
      }
    });
  };
  walk(alipayPages);
  return [...result];
};

const collectAuthorizedPageCodes = (userInfo: PermissionSource) => {
  if (isSuperAdmin(userInfo)) return collectAllPageCodes();
  const result = new Set<string>();
  const walk = (pages: typeof alipayPages) => {
    pages.forEach((page) => {
      if (hasPagePermission(page.pageCode, userInfo)) {
        result.add(`${SYSTEM_CODE}:${page.pageCode}`);
      }
      if (page.children?.length) {
        walk(page.children as typeof alipayPages);
      }
    });
  };
  walk(alipayPages);
  return [...result];
};

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    whiteListRouters: ['/login'],
    routers: [] as RouteRecordRaw[],
    pageCodes: [] as string[],
    buttonCodes: [] as string[],
    roleCodes: [] as string[],
    homePath: '/alipay/no-permission',
    initialized: false,
  }),
  getters: {
    hasPage: (state) => (pageCode: string) =>
      hasSuperAdminRole(state.roleCodes) || state.pageCodes.includes(`${SYSTEM_CODE}:${pageCode}`),
    hasButton: (state) => (buttonCode: string) => hasSuperAdminRole(state.roleCodes) || state.buttonCodes.includes(buttonCode),
  },
  actions: {
    async initRoutes(userInfo: PermissionSource = {}) {
      this.roleCodes = normalizeList(userInfo.roleCodes);
      this.pageCodes = collectAuthorizedPageCodes(userInfo);
      const runtimeConfig: Record<string, unknown> = await loadRuntimeImageConfig().catch(() => ({}));
      if (runtimeConfig?.demoMode !== true) {
        this.pageCodes = this.pageCodes.filter((code) => code !== `${SYSTEM_CODE}:deliveryShowcase`);
      }
      this.buttonCodes = normalizeList(userInfo.buttonCodes);

      const alipayRoute = buildAuthorizedAlipayRoute(this.pageCodes);
      this.homePath = typeof alipayRoute?.redirect === 'string' ? alipayRoute.redirect : '/alipay/no-permission';
      this.routers = alipayRoute ? [alipayRoute] : [];
      if (router.hasRoute(DYNAMIC_ROUTE_NAME)) {
        router.removeRoute(DYNAMIC_ROUTE_NAME);
      }
      if (alipayRoute) {
        router.addRoute(alipayRoute);
      }
      this.initialized = true;
    },
    async buildAsyncRoutes(userInfo?: PermissionSource) {
      await this.initRoutes(userInfo);
      return flattenAlipayRoutes(this.routers as RouteRecordRaw[]);
    },
    async restoreRoutes() {
      if (router.hasRoute(DYNAMIC_ROUTE_NAME)) {
        router.removeRoute(DYNAMIC_ROUTE_NAME);
      }
      this.roleCodes = [];
      this.pageCodes = [];
      this.buttonCodes = [];
      this.routers = [];
      this.homePath = '/alipay/no-permission';
      this.initialized = false;
    },
  },
});

export function getPermissionStore() {
  return usePermissionStore(store);
}
