import { del, get, post, upload } from '@/utils/platform-request';
import { cancelAgentRun, streamAgentMessage } from '@shared/agent/stream';
import type { AgentStreamHandlers } from '@shared/agent/types';

export type AnyRecord = Record<string, any>;

export const fetchCurrentPermissions = () => get('/api/platform/rbac/current');

export const apiBase = '/api/web';
export const publicApiBase = '/api/rent/v1/miniapp';
let uploadAssetBaseUrl = String(import.meta.env.VITE_UPLOAD_ASSET_BASE_URL || '/uploads').replace(/\/+$/, '');

export const setUploadAssetBaseUrl = (value?: string) => {
  const nextValue = String(value || '').trim().replace(/\/+$/, '');
  if (nextValue) {
    uploadAssetBaseUrl = nextValue;
  }
};

export const getUploadAssetBaseUrl = () => uploadAssetBaseUrl;

const webPost = (path: string, data: AnyRecord = {}) => post(`${apiBase}${path}`, data);
const tablePost = (path: string, data: AnyRecord = {}, options: AnyRecord = {}) =>
  post(`${apiBase}${path}`, data, { skipGlobalLoading: true, ...options });
const platformTablePost = (path: string, data: AnyRecord = {}) => post(path, data, { skipGlobalLoading: true });

export const resolveData = <T>(response: AnyRecord, fallback: T): T => response?.data?.data ?? fallback;

export const resolvePage = <T>(response: AnyRecord, fallback: T[] = []) => {
  const data = resolveData<AnyRecord | T[]>(response, fallback);
  if (Array.isArray(data)) {
    return {
      list: data,
      total: Number(response?.data?.count ?? response?.data?.total ?? data.length),
      current: Number(response?.data?.page ?? response?.data?.pageNum ?? 1),
      pageSize: Number(response?.data?.limit ?? response?.data?.pageSize ?? response?.data?.size ?? 10),
    };
  }
  const list = (data.list || data.records || fallback) as T[];
  return {
    list,
    total: Number(data.total ?? data.count ?? list.length),
    current: Number(data.pageNum ?? data.page ?? data.current ?? 1),
    pageSize: Number(data.pageSize ?? data.limit ?? data.size ?? 10),
  };
};

export const cleanQuery = (query: AnyRecord) => {
  const result: AnyRecord = {};
  Object.keys(query || {}).forEach((key) => {
    const value = query[key];
    if (value !== '' && value !== null && value !== undefined) result[key] = value;
  });
  return result;
};

export const resolveUploadAssetPath = (fileKey: string) => {
  if (!fileKey) return '';
  const key = String(fileKey).trim();
  if (!key) return '';
  if (/^https?:\/\//i.test(key)) {
    return key;
  }
  if (key.startsWith('/uploads/')) {
    return `${uploadAssetBaseUrl}/${key.substring('/uploads/'.length)}`;
  }
  if (key.startsWith('uploads/')) {
    return `${uploadAssetBaseUrl}/${key.substring('uploads/'.length)}`;
  }
  if (key.startsWith('/')) return key;
  return `${uploadAssetBaseUrl}/${key}`;
};

export const loadRuntimeImageConfig = async () => {
  const response = await get(`${publicApiBase}/catalog/config`, {}, { skipGlobalLoading: true });
  const data = resolveData<AnyRecord>(response, {});
  setUploadAssetBaseUrl(data?.imageBaseUrl);
  return data;
};

export const rentApi = {
  fetchAlipayMonitorOverview: () => get('/api/platform/monitor/overview', { serviceCode: 'alipay' }),
  listAlipayLogSources: () => get('/api/platform/system-log/sources'),
  queryAlipaySystemLogs: (data: AnyRecord) =>
    platformTablePost('/api/platform/system-log/query', { ...data, systemCode: 'alipay' }),

  fetchAnalyticsDashboard: (data: AnyRecord) => webPost('/analytics/dashboard', data),
  fetchAnalyticsSubjects: (data: AnyRecord) => webPost('/analytics/subjects', data),
  fetchReportList: (data: AnyRecord) => tablePost('/report-center/page', data),
  fetchReportDetail: (id: string | number) => get(`${apiBase}/report-center/${id}`),
  generateReport: (data: AnyRecord) => webPost('/report-center/generate', data),

  rentOrderList: (data: AnyRecord) => tablePost('/rent-component/page', data),
  queryDeposit: (data: AnyRecord) => tablePost('/rent-component/deposit/query', data),
  deductDeposit: (data: AnyRecord) => tablePost('/rent-component/deposit/deduct', data),
  deductDepositRecords: (data: AnyRecord) => tablePost('/rent-component/deposit/deduct-records', data),
  confirmDeductDeposit: (data: AnyRecord) => tablePost('/rent-component/deposit/deduct/confirm', data),
  previewAftersales: (data: AnyRecord) => tablePost('/rent-component/aftersale/preview', data),
  scanAftersales: (data: AnyRecord) => tablePost('/rent-component/aftersale/scan', data),
  importAftersales: (data: AnyRecord) => tablePost('/rent-component/aftersale/import', data),
  rentComRefund: (data: AnyRecord) => tablePost('/rent-component/refund', data),
  rentOrderMerchantConfirm: (data: AnyRecord) => tablePost('/rent-component/merchant-confirm', data),
  reviewIdentityPhotos: (data: AnyRecord) => tablePost('/rent-component/identity-photo-review', data),
  rentSend: (data: AnyRecord) => tablePost('/rent-component/send', data, data?.riskConfirmed ? { skipMutationConfirm: true } : {}),
  confirmSend: (data: AnyRecord) => tablePost('/rent-component/confirm-send', data),
  rentOrderComplete: (data: AnyRecord) => tablePost('/rent-component/complete', data),
  watchRentOrder: (data: AnyRecord) => tablePost('/rent-component/detail', data),
  queryReturnRecord: (data: AnyRecord) => tablePost('/rent-component/return-record', data),
  rentUserRiskDetail: (data: AnyRecord) => tablePost('/rent-component/risk-detail', data),
  rentOrderSync: (data: AnyRecord) => tablePost('/rent-component/sync', data),
  closeRentOrder: (data: AnyRecord) => tablePost('/rent-component/close', data),
  updateRemark: (data: AnyRecord) => tablePost('/rent-component/remark', data),
  generateContractPdf: (data: AnyRecord) => tablePost('/rent-component/contract/generate', data),
  queryEsignContract: (data: AnyRecord) => tablePost('/rent-component/esign-contract', data),
  startEsignContract: (data: AnyRecord) => tablePost('/rent-component/esign-contract/sign', data),
  syncContractToAlipay: (data: AnyRecord) => tablePost('/rent-component/contract/sync', data),
  queryInstallmentBills: (data: AnyRecord) => tablePost('/rent-component/installment-bills', data),
  startWithholdSign: (data: AnyRecord) => tablePost('/rent-component/withhold/sign', data),

  getDeviceList: (data: AnyRecord) => tablePost('/goods/page', data),
  getClassifyList: () => tablePost('/goods/classifications/list'),
  queryAlipayItemCategories: (data: AnyRecord) => tablePost('/goods/alipay-categories/query', data),
  queryAlipayRentCategories: (data: AnyRecord) => tablePost('/goods/alipay-rent-categories/query', data),
  getPublishedGoods: (data: AnyRecord) => tablePost('/goods/published/page', data),
  getUnpublishedGoods: (data: AnyRecord) => tablePost('/goods/unpublished/page', data),
  fetchUpGood: (data: AnyRecord) => tablePost('/goods/up', data),
  fetchDownGood: (data: AnyRecord) => tablePost('/goods/down', data),
  fetchSortGoodUp: (data: AnyRecord) => tablePost('/goods/sort-top', data),
  fetchDelete: (data: AnyRecord) => tablePost('/goods/delete', data),
  fetchPublicGoods: (data: AnyRecord) => tablePost('/goods/public-status/update', data),
  insertGood: (data: AnyRecord) => tablePost('/goods/create', data),
  updateGood: (data: AnyRecord) => tablePost('/goods/update', data),
  getGoodById: (data: AnyRecord) => tablePost('/goods/detail', data),
  addAttr: (data: AnyRecord) => tablePost('/goods/attrs/create', data),
  updateAttr: (data: AnyRecord) => tablePost('/goods/attrs/update', data),
  fetchDeleteAttr: (data: AnyRecord) => tablePost('/goods/attrs/delete', data),
  syncGood: (data: AnyRecord) => tablePost('/goods/sync', data),
  syncGoodsBidirectional: () => webPost('/goods/sync-bidirectional'),
  getGoodsSyncLogs: (data: AnyRecord) => tablePost('/goods/sync-logs/page', data),
  uploadSingle: (data: FormData) => upload(`${apiBase}/oss/upload`, data),

  getMiniappBannerList: (data: AnyRecord) => tablePost('/miniapp-banners/page', data),
  createMiniappBanner: (data: AnyRecord) => tablePost('/miniapp-banners/create', data),
  updateMiniappBanner: (data: AnyRecord) => tablePost('/miniapp-banners/update', data),
  deleteMiniappBanner: (data: AnyRecord) => tablePost('/miniapp-banners/delete', data),
  updateMiniappBannerStatus: (data: AnyRecord) => tablePost('/miniapp-banners/status/update', data),
  sortMiniappBannerTop: (data: AnyRecord) => tablePost('/miniapp-banners/sort-top', data),
  listCatalogCategories: () => tablePost('/catalog/categories/list'),
  createCatalogCategory: (data: AnyRecord) =>
    tablePost('/catalog/categories/create', data, { skipMutationConfirm: true }),
  updateCatalogCategory: (data: AnyRecord) =>
    tablePost('/catalog/categories/update', data, { skipMutationConfirm: true }),
  getCatalogStatusImpact: (code: string) =>
    tablePost('/catalog/categories/status-impact', { code }, { skipMutationConfirm: true }),
  updateCatalogCategoryStatus: (data: AnyRecord, idempotencyKey?: string) =>
    tablePost('/catalog/categories/status/update', data, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
      skipMutationConfirm: true,
    }),
  deleteCatalogCategory: (code: string) =>
    tablePost('/catalog/categories/delete', { code }, { skipMutationConfirm: true }),
  pageUnclassifiedCatalogGoods: (data: AnyRecord) => tablePost('/catalog/categories/goods/unclassified/page', data),
  pageCategoryGoods: (data: AnyRecord) => tablePost('/catalog/categories/goods/page', data),
  bindCatalogGoods: (data: AnyRecord) =>
    tablePost('/catalog/categories/goods/bind', data, { skipMutationConfirm: true }),
  unbindCatalogGoods: (data: AnyRecord) =>
    tablePost('/catalog/categories/goods/unbind', data, { skipMutationConfirm: true }),

  getUserList: (data: AnyRecord) => tablePost('/users/page', data),
  resolveUserIdentity: (data: AnyRecord) => tablePost('/users/identity/resolve', data),
  getGrantUserList: (data: AnyRecord) => tablePost('/goods/grants/users/page', data),
  fetchGrantGu: (data: AnyRecord) => tablePost('/goods/grants/assign', data),
  fetchCancelGrant: (data: AnyRecord) => tablePost('/goods/grants/revoke', data),
  getRole: () => tablePost('/roles/list'),
  updateUserIsNoRequest: (data: AnyRecord) => tablePost('/users/request-status/update', data),

  getOperLogList: (data: AnyRecord) => tablePost('/order-oper-logs/page', data),
  getOperLogByOrderId: (data: AnyRecord) => tablePost('/order-oper-logs/by-order-id', data),
  getOperLogByOrderNo: (data: AnyRecord) => tablePost('/order-oper-logs/by-order-no', data),

  sendAgentMessage: (data: AnyRecord) => platformTablePost('/api/agent/chat', data),
  streamAgentMessage: (data: AnyRecord, handlers: AgentStreamHandlers, signal?: AbortSignal) =>
    streamAgentMessage(data, handlers, signal),
  cancelAgentMessage: (runId: string) => cancelAgentRun(runId),
  listAgentConversations: (system = 'alipay') => get('/api/agent/conversations', { system }),
  getAgentConversation: (id: string, system = 'alipay') => get(`/api/agent/conversations/${id}`, { system }),
  deleteAgentConversation: (id: string, system = 'alipay') =>
    del(`/api/agent/conversations/${id}?system=${encodeURIComponent(system)}`, { skipGlobalLoading: true }),
  getAgentReport: (id: string) => get(`/api/agent/reports/${id}`),
};
