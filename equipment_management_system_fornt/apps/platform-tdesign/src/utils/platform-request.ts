import axios from 'axios';

import router from '@/router';
import { buildGatewayUrl } from '@/utils/gateway';
import { notifyOnce } from '@/utils/notify';
import {
  confirmMutationRequest,
  forceStopGlobalRequestLoading,
  shouldConfirmMutationRequest,
  startGlobalRequestLoading,
  stopGlobalRequestLoading,
} from '@/utils/request-ux';

const request = axios.create({
  timeout: 300000,
});

type RequestOptions = {
  skipGlobalLoading?: boolean;
};

const shouldSkipGlobalLoading = (config?: unknown) =>
  Boolean((config as { skipGlobalLoading?: boolean; __skipGlobalLoading?: boolean } | undefined)?.skipGlobalLoading ||
    (config as { skipGlobalLoading?: boolean; __skipGlobalLoading?: boolean } | undefined)?.__skipGlobalLoading);

let redirectingToLogin = false;

const silentReject = (message: string) => {
  const error = new Error(message) as Error & { silent?: boolean };
  error.silent = true;
  return Promise.reject(error);
};

const redirectToLogin = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('userInfo');
  notifyOnce({
    key: 'platform-login-required',
    title: '登录状态已失效',
    content: '请重新登录后继续操作',
    theme: 'warning',
  });
  if (redirectingToLogin || router.currentRoute.value.path === '/login') return;
  redirectingToLogin = true;
  router
    .replace({
      path: '/login',
      query: {
        redirect: router.currentRoute.value.fullPath,
      },
    })
    .finally(() => {
      redirectingToLogin = false;
    });
};

request.interceptors.request.use(async (config) => {
  const skipGlobalLoading = shouldSkipGlobalLoading(config);
  (config as { __skipGlobalLoading?: boolean }).__skipGlobalLoading = skipGlobalLoading;
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.token = token;
    if (shouldConfirmMutationRequest(config.method, config.url)) {
      await confirmMutationRequest(config.url);
    }
    if (!skipGlobalLoading) {
      startGlobalRequestLoading();
    }
    return config;
  }
  redirectToLogin();
  return silentReject('NO_TOKEN');
});

request.interceptors.response.use(
  (response) => {
    const skipGlobalLoading = shouldSkipGlobalLoading(response.config);
    const body = response.data || {};
    const code = Number(body.code);
    if (code === 0 || code === 200) {
      if (!skipGlobalLoading) {
        stopGlobalRequestLoading();
      }
      return response;
    }
    if (!skipGlobalLoading) {
      forceStopGlobalRequestLoading();
    }
    if (code === 401 || code === 513) {
      redirectToLogin();
      return silentReject(body.msg || 'UNAUTHORIZED');
    }
    if (code === 403) {
      notifyOnce({
        key: `platform-forbidden-${body.msg || 'default'}`,
        title: '无权限',
        content: body.msg || '当前账号无权执行该操作',
        theme: 'warning',
      });
      return Promise.reject(new Error(body.msg || 'forbidden'));
    }
    notifyOnce({
      key: `platform-request-failed-${code || body.msg || 'default'}`,
      title: '请求未完成',
      content: body.msg || '请稍后重试',
      theme: 'warning',
    });
    return Promise.reject(new Error(body.msg || 'request failed'));
  },
  (error) => {
    if (!shouldSkipGlobalLoading(error?.config)) {
      forceStopGlobalRequestLoading();
    }
    if (error?.name === 'MUTATION_CONFIRM_CANCELLED') {
      return Promise.reject(error);
    }
    if (error?.silent) {
      return Promise.reject(error);
    }
    notifyOnce({
      key: `platform-network-error-${error?.message || 'default'}`,
      title: '网络请求失败',
      content: error.message,
      theme: 'error',
    });
    return Promise.reject(error);
  },
);

export const post = (url: string, data: Record<string, unknown> = {}, options: RequestOptions = {}) =>
  request.post(buildGatewayUrl(url), data, options as any);
export const get = (url: string, params: Record<string, unknown> = {}, options: RequestOptions = {}) =>
  request.get(buildGatewayUrl(url), { params, ...options });
export const del = (url: string, options: RequestOptions = {}) => request.delete(buildGatewayUrl(url), options as any);
