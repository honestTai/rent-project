export type AnyRecord = Record<string, any>;

export const resolveData = <T>(response: AnyRecord, fallback: T): T => {
  return response?.data?.data ?? fallback;
};

export const resolvePage = <T>(response: AnyRecord, fallback: T[] = []) => {
  const data = resolveData<AnyRecord | T[]>(response, fallback);
  if (Array.isArray(data)) {
    return {
      list: data,
      total: Number(response?.data?.total ?? response?.data?.count ?? data.length),
      current: Number(response?.data?.page ?? response?.data?.pageNum ?? response?.data?.current ?? 1),
      pageSize: Number(response?.data?.pageSize ?? response?.data?.limit ?? response?.data?.size ?? 10),
    };
  }
  const list = (data.list || data.records || fallback) as T[];
  return {
    list,
    total: Number(data.total ?? list.length),
    current: Number(data.pageNum ?? data.page ?? data.current ?? 1),
    pageSize: Number(data.pageSize ?? data.size ?? 10),
  };
};

export const readUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || {};
  } catch (error) {
    return {};
  }
};

export const formatPermissionText = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '-';
  }
  return value ? String(value) : '-';
};
