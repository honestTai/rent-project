import axios from 'axios';

import { get, post, del } from '@/utils/platform-request';
import { buildGatewayUrl } from '@/utils/gateway';
import { forceStopGlobalRequestLoading, startGlobalRequestLoading, stopGlobalRequestLoading } from '@/utils/request-ux';

const tableOptions = { skipGlobalLoading: true };
type AnyRecord = Record<string, any>;

const tokenHeaders = () => {
  const token = localStorage.getItem('token');
  return token ? { token } : {};
};

const unwrapGatewayPayload = (payload: any) => {
  if (payload && typeof payload === 'object' && 'code' in payload) {
    const code = Number(payload.code);
    if (code === 0 || code === 200) {
      return payload.data ?? [];
    }
    throw new Error(payload.msg || '请求未完成');
  }
  return payload;
};

const rawGatewayPost = async (url: string, data: AnyRecord = {}) => {
  const response = await axios.post(buildGatewayUrl(url), data, {
    timeout: 300000,
    headers: tokenHeaders(),
  });
  return unwrapGatewayPayload(response.data);
};

export const login = async (data: Record<string, unknown>) => {
  startGlobalRequestLoading();
  try {
    const response = await axios.post(buildGatewayUrl('/api/login/login'), data, {
      timeout: 300000,
      headers: {
        'X-Login-System': 'platform',
      },
    });
    stopGlobalRequestLoading();
    return response;
  } catch (error) {
    forceStopGlobalRequestLoading();
    throw error;
  }
};

export const listConfigs = (data: Record<string, unknown>) => post('/api/platform/config/list', data, tableOptions);
export const saveConfig = (data: Record<string, unknown>) => post('/api/platform/config/save', data, tableOptions);
export const deleteConfig = (id: string | number) => del(`/api/platform/config/${id}`, tableOptions);
export const queryAlipayItemCategories = (data: Record<string, unknown>) =>
  rawGatewayPost('/api/web/goods/alipay-categories/query', data);
export const queryAlipayRentCategories = (data: Record<string, unknown>) =>
  rawGatewayPost('/api/web/goods/alipay-rent-categories/query', data);

export const listChannels = (data: Record<string, unknown>) =>
  post('/api/platform/notify-channel/list', data, tableOptions);
export const saveChannel = (data: Record<string, unknown>) =>
  post('/api/platform/notify-channel/save', data, tableOptions);
export const deleteChannel = (id: string | number) => del(`/api/platform/notify-channel/${id}`, tableOptions);

export const listTasks = (data: Record<string, unknown>) =>
  post('/api/platform/schedule-task/list', data, tableOptions);
export const saveTask = (data: Record<string, unknown>) =>
  post('/api/platform/schedule-task/save', data, tableOptions);
export const deleteTask = (id: string | number) => del(`/api/platform/schedule-task/${id}`, tableOptions);

export const listSystems = () => get('/api/platform/overview/systems');
export const fetchSystemDashboard = (url: string, data: Record<string, unknown>) => post(url, data);
export const fetchMonitorOverview = (params: Record<string, unknown> = {}) =>
  get('/api/platform/monitor/overview', params);

export const listLogSources = () => get('/api/platform/system-log/sources');
export const querySystemLogs = (data: Record<string, unknown>) =>
  post('/api/platform/system-log/query', data, tableOptions);

export const fetchCurrentRbacPermissions = () => get('/api/platform/rbac/current');
export const fetchCurrentUserProfile = () => get('/api/user/current');
export const changeCurrentUserPassword = (data: Record<string, unknown>) =>
  post('/api/user/changePassword', data, tableOptions);
export const listRbacRoles = (params: Record<string, unknown>) =>
  get('/api/platform/rbac/roles', params, tableOptions);
export const saveRbacRole = (data: Record<string, unknown>) =>
  post('/api/platform/rbac/roles/save', data, tableOptions);
export const deleteRbacRole = (id: string | number) => del(`/api/platform/rbac/roles/${id}`, tableOptions);
export const fetchRbacPermissionTree = () => get('/api/platform/rbac/permission-tree');
export const listRbacPermissionButtons = (params: Record<string, unknown>) =>
  get('/api/platform/rbac/permission-buttons', params, tableOptions);
export const fetchRbacRoleButtonIds = (roleId: string | number) =>
  get(`/api/platform/rbac/roles/${roleId}/button-ids`);
export const saveRbacRoleButtons = (data: Record<string, unknown>) =>
  post('/api/platform/rbac/roles/permissions/save', data, tableOptions);
export const fetchRbacUserRoleIds = (userId: string | number) => get(`/api/platform/rbac/users/${userId}/role-ids`);
export const saveRbacUserRoles = (data: Record<string, unknown>) =>
  post('/api/platform/rbac/users/roles/save', data, tableOptions);
export const listPlatformUsers = (data: Record<string, unknown>) => post('/api/user/userList', data, tableOptions);
export const addPlatformUser = (data: Record<string, unknown>) => post('/api/user/addUser', data, tableOptions);
export const updatePlatformUser = (data: Record<string, unknown>) => post('/api/user/updateUser', data, tableOptions);
export const deletePlatformUsers = (data: Record<string, unknown>) => post('/api/user/deleteUser', data, tableOptions);
export const resetPlatformUserPassword = (data: Record<string, unknown>) =>
  post('/api/user/resetPassword', data, tableOptions);
export const listPlatformAssignableRoles = (params: Record<string, unknown>) =>
  get('/api/user/roles', params, tableOptions);
export const fetchPlatformUserRoleIds = (userId: string | number) => get(`/api/user/roles/${userId}/role-ids`);
export const savePlatformUserRoles = (data: Record<string, unknown>) => post('/api/user/roles/save', data, tableOptions);

export const fetchAlipayPersonalLedgerLogs = (data: AnyRecord) => rawGatewayPost('/api/web/order-oper-logs/page', data);
