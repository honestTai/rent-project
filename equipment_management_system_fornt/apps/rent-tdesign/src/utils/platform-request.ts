import axios from 'axios';
import { NotifyPlugin } from 'tdesign-vue-next';

import router from '@/router';
import { buildGatewayUrl, buildPlatformLoginUrl } from '@/utils/gateway';
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
  skipMutationConfirm?: boolean;
};

const shouldSkipGlobalLoading = (config?: unknown) =>
  Boolean((config as { skipGlobalLoading?: boolean; __skipGlobalLoading?: boolean } | undefined)?.skipGlobalLoading ||
    (config as { skipGlobalLoading?: boolean; __skipGlobalLoading?: boolean } | undefined)?.__skipGlobalLoading);

const shouldSkipMutationConfirm = (config?: unknown) =>
  Boolean((config as { skipMutationConfirm?: boolean; __skipMutationConfirm?: boolean } | undefined)?.skipMutationConfirm ||
    (config as { skipMutationConfirm?: boolean; __skipMutationConfirm?: boolean } | undefined)?.__skipMutationConfirm);

request.interceptors.request.use(async (config) => {
  const skipGlobalLoading = shouldSkipGlobalLoading(config);
  const skipMutationConfirm = shouldSkipMutationConfirm(config);
  (config as { __skipGlobalLoading?: boolean }).__skipGlobalLoading = skipGlobalLoading;
  (config as { __skipMutationConfirm?: boolean }).__skipMutationConfirm = skipMutationConfirm;
  if (!skipMutationConfirm && shouldConfirmMutationRequest(config.method, config.url)) {
    await confirmMutationRequest(config.url);
  }
  if (!skipGlobalLoading) {
    startGlobalRequestLoading();
  }
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.token = token;
  }
  config.headers['X-Request-Source'] = 'admin';
  return config;
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
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      window.location.href = buildPlatformLoginUrl(window.location.href);
    }
    if (code === 403) {
      NotifyPlugin.warning({
        title: '无权限',
        content: body.msg || '当前账号无权执行该操作',
      });
      return Promise.reject(new Error(body.msg || 'forbidden'));
    }
    NotifyPlugin.warning({
      title: '请求未完成',
      content: body.msg || '请稍后重试',
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
    NotifyPlugin.error({
      title: '网络请求失败',
      content: error.message,
    });
    return Promise.reject(error);
  },
);

export const post = (url: string, data: Record<string, unknown> = {}, options: RequestOptions = {}) =>
  request.post(buildGatewayUrl(url), data, options as any);
export const get = (url: string, params: Record<string, unknown> = {}, options: RequestOptions = {}) =>
  request.get(buildGatewayUrl(url), { params, ...options });
export const del = (url: string, options: RequestOptions = {}) => request.delete(buildGatewayUrl(url), options as any);
export const upload = (url: string, data: FormData, options: RequestOptions = {}) => request.post(buildGatewayUrl(url), data, {
  headers: {
    'Content-Type': 'multipart/form-data',
  },
  ...options,
} as any);
