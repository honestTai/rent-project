import { defineStore } from 'pinia';
import type { RouteRecordRaw } from 'vue-router';

import { fetchCurrentRbacPermissions } from '@/api/platform';
import { fixedRouterList, homepageRouterList, registerDynamicRoutes, resetDynamicRoutes } from '@/router';
import { store } from '@/store';
import type { MenuRoute } from '@/types/interface';

type RbacSnapshot = {
  roleCodes: string[];
  pageCodes: string[];
  buttonCodes: string[];
};

type RouteLike = RouteRecordRaw | {
  matched?: Array<{
    meta?: Record<string, unknown>;
  }>;
  meta?: Record<string, unknown>;
};

const emptySnapshot = (): RbacSnapshot => ({
  roleCodes: [],
  pageCodes: [],
  buttonCodes: [],
});

const resolveData = (response: any) => response?.data?.data || {};

const normalizeList = (value: unknown): string[] => (Array.isArray(value) ? value.map((item) => String(item)) : []);

const isSuperAdmin = (snapshot: RbacSnapshot) => snapshot.roleCodes.includes('super_admin');

const readStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || {};
  } catch (error) {
    return {};
  }
};

const routePageCode = (route: RouteLike) => {
  const matched = 'matched' in route && route.matched ? [...route.matched].reverse() : [];
  for (const record of matched) {
    const code = record.meta?.rbacPageCode;
    if (code) return String(code);
  }
  return route.meta?.rbacPageCode ? String(route.meta.rbacPageCode) : '';
};

const allowBySnapshot = (snapshot: RbacSnapshot, code: string) => {
  if (!code) return true;
  return isSuperAdmin(snapshot) || snapshot.pageCodes.includes(code);
};

const joinPath = (parentPath: string, childPath: string) => {
  if (!childPath) return parentPath || '/';
  if (childPath.startsWith('/')) return childPath;
  return `${parentPath.replace(/\/$/, '')}/${childPath}` || `/${childPath}`;
};

const firstLeafPath = (routes: RouteRecordRaw[], parentPath = ''): string => {
  for (const route of routes) {
    const fullPath = joinPath(parentPath, route.path);
    if (route.children?.length) {
      const childPath = firstLeafPath(route.children, fullPath);
      if (childPath) return childPath;
      continue;
    }
    if (!route.meta?.hidden && fullPath !== '/') {
      return fullPath;
    }
  }
  return '';
};

const filterRoutes = (routes: RouteRecordRaw[], snapshot: RbacSnapshot, parentPath = ''): RouteRecordRaw[] =>
  routes
    .map((route) => {
      const fullPath = joinPath(parentPath, route.path);
      const children = route.children ? filterRoutes(route.children, snapshot, fullPath) : [];
      const code = routePageCode(route);
      const selfAllowed = allowBySnapshot(snapshot, code);
      if (!selfAllowed && children.length === 0) {
        return null;
      }
      const nextRoute: RouteRecordRaw = {
        ...route,
        children,
      };
      if (children.length > 0) {
        nextRoute.redirect = firstLeafPath(children, fullPath) || route.redirect;
      }
      return nextRoute;
    })
    .filter(Boolean) as RouteRecordRaw[];

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    whiteListRouters: ['/login'],
    routers: [] as MenuRoute[],
    snapshot: emptySnapshot(),
  }),
  actions: {
    async loadCurrentPermissions() {
      const response = await fetchCurrentRbacPermissions();
      const current = resolveData(response);
      this.snapshot = {
        roleCodes: normalizeList(current.roleCodes),
        pageCodes: normalizeList(current.pageCodes),
        buttonCodes: normalizeList(current.buttonCodes),
      };
      const storedUser = readStoredUser();
      localStorage.setItem('userInfo', JSON.stringify({ ...storedUser, ...current }));
      return this.snapshot;
    },
    async initRoutes() {
      const snapshot = await this.loadCurrentPermissions();
      this.routers = filterRoutes([...homepageRouterList, ...fixedRouterList], snapshot) as MenuRoute[];
      registerDynamicRoutes(this.routers as RouteRecordRaw[]);
    },
    async buildAsyncRoutes() {
      await this.initRoutes();
      return [];
    },
    async restoreRoutes() {
      this.snapshot = emptySnapshot();
      this.routers = [];
      resetDynamicRoutes();
    },
    hasRoutePermission(route: RouteLike) {
      return allowBySnapshot(this.snapshot, routePageCode(route));
    },
    firstAccessiblePath() {
      return firstLeafPath(this.routers as RouteRecordRaw[]) || '/platform/profile';
    },
  },
});

export function getPermissionStore() {
  return usePermissionStore(store);
}
