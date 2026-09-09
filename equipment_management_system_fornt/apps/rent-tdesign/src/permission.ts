import 'nprogress/nprogress.css'; // progress bar style

import NProgress from 'nprogress'; // progress bar
import { MessagePlugin } from 'tdesign-vue-next';

import router from '@/router';
import { getPermissionStore, useUserStore } from '@/store';
import { buildPlatformLoginUrl } from '@/utils/gateway';

NProgress.configure({ showSpinner: false });

const SYSTEM_NAME = 'HONESTTAI 租赁管理';
const PERMISSION_LOAD_TIMEOUT = 15000;

let bootLoading = false;

const resolveTitle = (title: unknown) => {
  if (typeof title === 'string') return title;
  if (title && typeof title === 'object') {
    const localeTitle = title as Record<string, string>;
    return localeTitle.zh_CN || localeTitle.en_US || '';
  }
  return '';
};

const redirectToPlatformLogin = () => {
  window.location.href = buildPlatformLoginUrl(window.location.href);
};

const withTimeout = async <T>(promise: Promise<T>, timeout = PERMISSION_LOAD_TIMEOUT): Promise<T> => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeoutPromise = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => reject(new Error('权限菜单加载超时，请稍后重试')), timeout);
  });
  try {
    return await Promise.race([promise, timeoutPromise]);
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
};

const consumeSsoLogin = (to: any, next: any, userStore: any) => {
  const token = typeof to.query.ssoToken === 'string' ? to.query.ssoToken : '';
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
  delete query.ssoToken;
  delete query.ssoUser;
  next({ path: to.path, query, hash: to.hash, replace: true });
  return true;
};

const resolveFallbackTarget = (fullPath: string, homePath: string) => {
  const resolvedRoute = router.resolve(fullPath);
  if (resolvedRoute.name && resolvedRoute.name !== 'bootFallback') {
    return fullPath;
  }
  return homePath || '/alipay/no-permission';
};

const loadBootRoute = async (to: any, userStore: any, permissionStore: any) => {
  if (bootLoading) return;
  bootLoading = true;
  try {
    const userInfo = await withTimeout(userStore.getUserInfo());
    await withTimeout(permissionStore.initRoutes(userInfo));
    if (to.name === 'bootFallback') {
      router.replace(resolveFallbackTarget(to.fullPath, permissionStore.homePath));
    } else {
      router.replace(permissionStore.homePath || '/alipay/no-permission');
    }
  } catch (error) {
    MessagePlugin.error(error instanceof Error ? error.message : '登录状态已失效');
    redirectToPlatformLogin();
  } finally {
    bootLoading = false;
    NProgress.done();
  }
};

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  const pageTitle = resolveTitle(to.meta?.title);
  document.title = pageTitle ? `${SYSTEM_NAME} - ${pageTitle}` : SYSTEM_NAME;

  const permissionStore = getPermissionStore();
  const userStore = useUserStore();
  const token = localStorage.getItem('token');

  if (consumeSsoLogin(to, next, userStore)) {
    NProgress.done();
    return;
  }

  if (to.path === '/login') {
    await userStore.logout();
    await permissionStore.restoreRoutes();
    redirectToPlatformLogin();
    next(false);
    NProgress.done();
    return;
  }

  if (!token) {
    redirectToPlatformLogin();
    next(false);
    NProgress.done();
    return;
  }

  try {
    if (to.name === 'boot' || to.name === 'bootFallback') {
      next();
      void loadBootRoute(to, userStore, permissionStore);
      return;
    }

    const userInfo = await withTimeout(userStore.getUserInfo());
    await withTimeout(permissionStore.initRoutes(userInfo));
    if (to.name && router.hasRoute(to.name)) {
      next();
      return;
    }
    next(permissionStore.homePath || '/alipay/no-permission');
  } catch (error) {
    MessagePlugin.error(error instanceof Error ? error.message : '登录状态已失效');
    redirectToPlatformLogin();
    next(false);
    NProgress.done();
  }
});

router.afterEach(() => {
  NProgress.done();
});
