import 'nprogress/nprogress.css'; // progress bar style

import NProgress from 'nprogress'; // progress bar

import router from '@/router';
import { getPermissionStore, useUserStore } from '@/store';
import { notifyOnce } from '@/utils/notify';
import { buildStoredSsoPayload, normalizeSsoRedirect, redirectWithSsoPayload } from '@/utils/sso';

NProgress.configure({ showSpinner: false });

const SSO_QUERY_KEYS = ['ssoToken', 'token', 'access_token', 'ssoUser', 'ssoState'];

const resolveTitle = (title: unknown) => {
  if (typeof title === 'string') return title;
  if (title && typeof title === 'object') {
    const localeTitle = title as Record<string, string>;
    return localeTitle.zh_CN || localeTitle.en_US || '';
  }
  return '';
};

const consumeSsoLogin = (to: any, next: any, userStore: any) => {
  const token =
    (typeof to.query.ssoToken === 'string' && to.query.ssoToken) ||
    (typeof to.query.token === 'string' && to.query.token) ||
    (typeof to.query.access_token === 'string' && to.query.access_token) ||
    '';
  if (!token) return false;

  userStore.token = token;
  localStorage.setItem('token', token);
  if (typeof to.query.ssoUser === 'string') {
    try {
      localStorage.setItem('userInfo', to.query.ssoUser);
    } catch (error) {
      localStorage.removeItem('userInfo');
    }
  }

  const query = { ...to.query };
  SSO_QUERY_KEYS.forEach((key) => delete query[key]);
  next({ path: to.path, query, hash: to.hash, replace: true });
  return true;
};

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  const pageTitle = resolveTitle(to.meta?.title);
  document.title = pageTitle ? `HONESTTAI 产品中台 - ${pageTitle}` : 'HONESTTAI 产品中台';

  const permissionStore = getPermissionStore();
  const { whiteListRouters } = permissionStore;

  const userStore = useUserStore();
  const storedToken = localStorage.getItem('token');

  if (consumeSsoLogin(to, next, userStore)) {
    NProgress.done();
    return;
  }

  if (userStore.token && storedToken) {
    if (to.path === '/login') {
      if (typeof to.query.redirect === 'string') {
        const nextUrl = redirectWithSsoPayload(to.query.redirect, buildStoredSsoPayload());
        if (nextUrl) {
          next(normalizeSsoRedirect(nextUrl));
        } else {
          next(false);
        }
        NProgress.done();
        return;
      }
      next(normalizeSsoRedirect('/platform/overview'));
      return;
    }
    try {
      await userStore.getUserInfo();

      await permissionStore.initRoutes();
      const resolvedRoute = router.resolve(to.fullPath);
      if (resolvedRoute.matched.length === 0) {
        const firstPath = permissionStore.firstAccessiblePath();
        next(firstPath === to.path ? '/login' : firstPath);
        return;
      }
      if (!permissionStore.hasRoutePermission(resolvedRoute)) {
        const firstPath = permissionStore.firstAccessiblePath();
        next(firstPath === to.path ? '/login' : firstPath);
        return;
      }
      if (to.matched.length > 0 && to.name && router.hasRoute(to.name)) {
        next();
      } else {
        next({ ...to, replace: true });
      }
    } catch (error) {
      if (!(error instanceof Error && 'silent' in error)) {
        notifyOnce({
          key: 'platform-login-required',
          title: '登录状态已失效',
          content: error instanceof Error ? error.message : '请重新登录后继续操作',
          theme: 'warning',
        });
      }
      next({
        path: '/login',
        query: { redirect: to.fullPath },
      });
      NProgress.done();
    }
  } else {
    if (userStore.token && !storedToken) {
      await userStore.logout();
    }
    /* white list router */
    if (whiteListRouters.includes(to.path)) {
      next();
    } else {
      notifyOnce({
        key: 'platform-login-required',
        title: '登录状态已失效',
        content: '请重新登录后继续操作',
        theme: 'warning',
      });
      next({
        path: '/login',
        query: { redirect: to.fullPath },
      });
    }
    NProgress.done();
  }
});

router.afterEach(() => {
  NProgress.done();
});
