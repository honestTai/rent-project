import { ref } from 'vue';

import { cleanQuery, resolvePage, type AnyRecord } from '@/api/rent';

export const buttonCode = (pageCode: string, action: string) => `alipay:${pageCode}:${action}`;

export const formatDateTime = (value: unknown) => {
  if (!value) return '-';
  if (typeof value === 'number') return new Date(value).toLocaleString();
  return String(value).replace('T', ' ').slice(0, 19);
};

export const formatMoney = (value: unknown) => {
  const numberValue = Number(value ?? 0);
  if (!Number.isFinite(numberValue)) return '-';
  return (numberValue / 100).toFixed(2);
};

const orderStatusLabels: Record<string, string> = {
  CREATED: '用户下单',
  SIGNED: '用户已签约',
  APPROVED: '商家审核通过',
  PAID: '已支付',
  DELIVERED: '商家发货',
  RECEIVED: '用户确认收货',
  RETURN_DELIVERED: '用户寄回',
  RETURN_RECEIVED: '商家签收',
  FINISHED: '订单完结',
  CLOSED: '订单关闭',
  PENDING_CANCEL: '退款中',
};

const operTypeLabels: Record<string, string> = {
  CREATE_ORDER: '创建订单',
  PAY: '支付',
  SHIP: '发货',
  CONFIRM_RECEIVE: '确认收货',
  RETURN: '归还',
  REFUND: '退款',
  CLOSE: '关闭订单',
  COMPLETE: '完结订单',
  MERCHANT_CONFIRM: '商户审核',
  DEDUCT_DEPOSIT: '扣减押金',
  AFTERSALE_SYNC: '售后同步',
  CONTRACT_SYNC: '租赁合同回传',
  RENT_PAY: '租金支付',
  FREEZE: '押金冻结',
  CANCEL: '取消',
  SYNC: '同步',
  REMARK: '备注',
};

const operSourceLabels: Record<string, string> = {
  ADMIN: '后台管理',
  MINIAPP: '小程序',
  USER: '小程序',
  ALIPAY: '支付宝',
  CALLBACK: '支付宝回调',
  SCHEDULED_TASK: '定时任务',
  SYSTEM: '系统',
};

const operResultLabels: Record<string, string> = {
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败',
};

export const formatOrderStatus = (value: unknown) => orderStatusLabels[String(value || '')] || String(value || '-');
export const formatOperType = (value: unknown) => operTypeLabels[String(value || '')] || String(value || '-');
export const formatOperSource = (value: unknown) => operSourceLabels[String(value || '')] || String(value || '-');
export const formatOperResult = (value: unknown) => operResultLabels[String(value || '')] || String(value || '-');

export const usePagedList = <T extends AnyRecord>(
  loader: (query: AnyRecord) => Promise<AnyRecord>,
  initialQuery: AnyRecord = {},
) => {
  const query = ref<AnyRecord>({ ...initialQuery });
  const rows = ref<T[]>([]);
  const loading = ref(false);
  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    pageSizeOptions: [10, 20, 50, 100],
  });

  const load = async () => {
    const requestedCurrent = pagination.value.current;
    const requestedPageSize = pagination.value.pageSize;
    loading.value = true;
    try {
      const response = await loader(cleanQuery({
        ...query.value,
        page: requestedCurrent,
        limit: requestedPageSize,
      }));
      const page = resolvePage<T>(response);
      rows.value = page.list;
      pagination.value.current = requestedCurrent;
      pagination.value.pageSize = requestedPageSize;
      pagination.value.total = page.total;
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    pagination.value.current = 1;
    return load();
  };

  const reset = () => {
    Object.keys(query.value).forEach((key) => {
      query.value[key] = '';
    });
    return search();
  };

  const pageChange = (pageInfo: AnyRecord) => {
    pagination.value.current = Number(pageInfo.current || 1);
    pagination.value.pageSize = Number(pageInfo.pageSize || 10);
    return load();
  };

  return {
    query,
    rows,
    loading,
    pagination,
    load,
    search,
    reset,
    pageChange,
  };
};
