<template>
  <AlipayCleanListPage
    title="台账管理"
    description="查看订单操作台账、状态变更、请求数据和失败原因，辅助售后追踪。"
    :rows="rows"
    row-key="logId"
    :loading="loading"
    :pagination="pagination"
    content-title="台账列表"
    empty-title="暂无台账记录"
    @page-change="handlePageChange"
  >
    <template #filters>
      <t-input
        v-if="!fixedOrderId"
        v-model="query.orderNo"
        placeholder="请输入订单号搜索"
        clearable
      />
      <t-select
        v-model="query.operType"
        class="type-select"
        placeholder="操作类型"
        clearable
        filterable
        v-bind="searchableSelectProps('logs.operType', operTypeOptions)"
      />
      <t-select
        v-model="query.operResult"
        class="result-select"
        placeholder="操作结果"
        clearable
        filterable
        v-bind="searchableSelectProps('logs.operResult', resultOptions)"
      />
    </template>

    <template #queryActions>
      <AlipayQueryActions @search="loadWithRoute" @reset="resetQuery" />
    </template>

    <template #contentActions>
      <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
        <template #icon><t-icon name="setting" /></template>
        显示设置
      </t-button>
    </template>

    <template #record="{ row }">
      <article class="log-record" :class="{ 'log-record--error': row.operResult === 'FAIL' }">
        <div class="log-record__main">
          <div class="log-record__top">
            <div class="log-record__identity">
              <t-tag :theme="operTypeTheme(row.operType)" variant="light">{{ operTypeLabel(row.operType) }}</t-tag>
              <t-button v-if="!fixedOrderId" variant="text" theme="primary" @click="goToOrder(row)">
                {{ row.orderNo || '-' }}
              </t-button>
              <strong v-else>{{ row.orderNo || '-' }}</strong>
            </div>
            <div class="log-record__tags">
              <t-tag :theme="row.operSource === 'ADMIN' ? 'warning' : 'primary'" variant="light">{{ formatOperSource(row.operSource) }}</t-tag>
              <t-tag :theme="row.operResult === 'SUCCESS' ? 'success' : 'danger'" variant="light">
                {{ formatOperResult(row.operResult) }}
              </t-tag>
            </div>
          </div>
          <div v-if="isLogFieldVisible('operDesc')" class="log-record__desc">{{ row.operDesc || '-' }}</div>
          <div v-if="visibleLogFields.length" class="log-record__meta">
            <span v-for="field in visibleLogFields" :key="field.key">{{ field.label }}：{{ field.value(row) }}</span>
          </div>
          <div v-if="isLogFieldVisible('errorMsg') && row.errorMsg" class="log-record__error">{{ row.errorMsg }}</div>
        </div>
        <div v-if="isLogActionVisible('detail')" class="log-record__actions">
          <t-button variant="text" theme="primary" size="small" @click="showDetail(row)">详情</t-button>
        </div>
      </article>
    </template>

    <template #headerActions>
      <div v-if="fixedOrderId" class="fixed-order-tip">当前按订单筛选</div>
    </template>

    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="台账显示设置"
      :groups="displaySettingGroups"
      :model-value="displaySettings.state"
      @save="saveDisplaySettings"
    />

    <t-dialog v-model:visible="detailVisible" header="台账详情" width="760px" :footer="false">
      <div v-if="currentLog" class="oper-log-detail-sections">
        <div class="detail-section">
          <div class="detail-section-title">请求数据</div>
          <pre v-if="formattedRequestData" class="json-block">{{ formattedRequestData }}</pre>
          <span v-else class="detail-empty">暂无</span>
        </div>
        <div class="detail-section">
          <div class="detail-section-title">返回数据</div>
          <pre v-if="formattedResponseData" class="json-block">{{ formattedResponseData }}</pre>
          <span v-else class="detail-empty">暂无</span>
        </div>
        <div v-if="currentLog.errorMsg" class="detail-section">
          <div class="detail-section-title detail-section-title--error">错误信息</div>
          <pre class="oper-log-stack">{{ currentLog.errorMsg }}</pre>
        </div>
      </div>
    </t-dialog>
  </AlipayCleanListPage>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { createSearchableOptions } from '@shared/utils/search-options';
import { rentApi, type AnyRecord } from '@/api/rent';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import { formatDateTime, formatOperResult, formatOperSource, formatOperType, formatOrderStatus, usePagedList } from '@/pages/alipay/shared';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';
import { useAutoQuery } from '@/utils/useAutoQuery';

const route = useRoute();
const router = useRouter();
const fixedOrderId = computed(() => route.query.orderId || '');
const { searchableSelectProps } = createSearchableOptions();

const { query, rows, loading, pagination, load } = usePagedList(rentApi.getOperLogList, {
  orderNo: String(route.query.orderNo || ''),
  operType: '',
  operResult: '',
});

pagination.value.pageSizeOptions = [10, 20, 50, 100];

const operTypeOptions = [
  { value: 'CREATE_ORDER', label: '创建订单' },
  { value: 'PAY', label: '支付' },
  { value: 'SHIP', label: '发货' },
  { value: 'CONFIRM_RECEIVE', label: '确认收货' },
  { value: 'RETURN', label: '归还' },
  { value: 'REFUND', label: '退款' },
  { value: 'CLOSE', label: '关闭订单' },
  { value: 'COMPLETE', label: '完结订单' },
  { value: 'MERCHANT_CONFIRM', label: '商户审核' },
  { value: 'DEDUCT_DEPOSIT', label: '扣减押金' },
  { value: 'RENT_PAY', label: '租金支付' },
  { value: 'FREEZE', label: '押金冻结' },
  { value: 'CANCEL', label: '取消' },
  { value: 'SYNC', label: '同步' },
  { value: 'REMARK', label: '备注' },
];
const resultOptions = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAIL', label: '失败' },
];

const columns = [
  { title: 'ID', colKey: 'logId', width: 70, align: 'center' },
  { title: '订单号', colKey: 'orderNo', width: 170, align: 'center' },
  { title: '操作类型', colKey: 'operType', width: 140, align: 'center' },
  { title: '操作描述', colKey: 'operDesc', minWidth: 160, ellipsis: true, align: 'center' },
  { title: '操作来源', colKey: 'operSource', width: 110, align: 'center' },
  { title: '结果', colKey: 'operResult', width: 90, align: 'center' },
  { title: '操作前状态', colKey: 'statusBefore', width: 130, ellipsis: true, align: 'center' },
  { title: '操作后状态', colKey: 'statusAfter', width: 130, ellipsis: true, align: 'center' },
  { title: '操作时间', colKey: 'operTime', width: 170, align: 'center' },
  { title: '错误信息', colKey: 'errorMsg', minWidth: 160, ellipsis: true, align: 'center' },
  { title: '操作', colKey: 'operation', width: 100, fixed: 'right', align: 'center' },
];

const currentLog = ref<AnyRecord | null>(null);
const detailVisible = ref(false);
type LogDisplayField = DisplaySettingItem & {
  value: (row: AnyRecord) => string;
};
const logFieldDefinitions: LogDisplayField[] = [
  { key: 'operDesc', label: '操作描述', group: '基础信息', value: (row) => row.operDesc || '-' },
  { key: 'logId', label: 'ID', group: '基础信息', value: (row) => row.logId || '-' },
  { key: 'statusFlow', label: '状态', group: '状态变更', value: (row) => `${formatOrderStatus(row.statusBefore)} -> ${formatOrderStatus(row.statusAfter)}` },
  { key: 'operTime', label: '时间', group: '基础信息', value: (row) => formatDateTime(row.operTime) },
  { key: 'errorMsg', label: '错误信息', group: '异常', value: (row) => row.errorMsg || '-' },
];
const logMetaFieldDefinitions = logFieldDefinitions.filter((field) => !['operDesc', 'errorMsg'].includes(field.key));
const logActionDefinitions: DisplaySettingItem[] = [
  { key: 'detail', label: '详情', group: '操作按钮' },
];
const displaySettingsVisible = ref(false);
const displayDefinitions = computed(() => ({
  fields: logFieldDefinitions,
  actions: logActionDefinitions,
}));
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.ledger',
  version: 1,
  definitions: displayDefinitions,
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [
  {
    kind: 'fields',
    label: '字段',
    title: '台账卡片字段',
    description: '控制台账卡片中的描述、状态、时间和错误字段。',
    items: logFieldDefinitions,
  },
  {
    kind: 'actions',
    label: '操作按钮',
    title: '台账操作按钮',
    description: '控制台账卡片操作按钮的显示。',
    items: logActionDefinitions,
  },
]);
const visibleLogFields = computed(() => displaySettings.visibleItems('fields', logMetaFieldDefinitions));
const isLogFieldVisible = (key: string) => displaySettings.state.fields.includes(key);
const isLogActionVisible = (key: string) => displaySettings.state.actions.includes(key);

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  MessagePlugin.success('显示设置已保存');
}

const formattedRequestData = computed(() => currentLog.value ? formatJson(currentLog.value.requestData) : '');
const formattedResponseData = computed(() => currentLog.value ? formatJson(currentLog.value.responseData) : '');

const buildPayload = () => {
  const payload = { ...query.value };
  if (fixedOrderId.value) {
    payload.orderId = fixedOrderId.value;
    delete payload.orderNo;
  }
  return payload;
};

const loadWithRoute = () => {
  const original = { ...query.value };
  Object.assign(query.value, buildPayload());
  const promise = load();
  Object.assign(query.value, original);
  return promise;
};

const searchLogs = () => {
  pagination.value.current = 1;
  return loadWithRoute();
};

const { pauseAutoQuery } = useAutoQuery(
  () => ({ orderNo: query.value.orderNo, operType: query.value.operType, operResult: query.value.operResult }),
  searchLogs,
);

const resetQuery = () =>
  pauseAutoQuery(() => {
    query.value.orderNo = '';
    query.value.operType = '';
    query.value.operResult = '';
    return searchLogs();
  });

const handlePageChange = (pageInfo: AnyRecord) => {
  pagination.value.current = Number(pageInfo.current || 1);
  pagination.value.pageSize = Number(pageInfo.pageSize || 10);
  return loadWithRoute();
};

const showDetail = (row: AnyRecord) => {
  currentLog.value = row;
  detailVisible.value = true;
};

const goToOrder = (row: AnyRecord) => {
  if (row.orderNo) router.push({ path: '/alipay/orders', query: { searchOrderNo: row.orderNo } });
};

const operTypeLabel = (type: string) => formatOperType(type);
const operTypeTheme = (type: string) => {
  const map: Record<string, string> = {
    PAY: 'success',
    RENT_PAY: 'success',
    SHIP: 'warning',
    FREEZE: 'warning',
    REFUND: 'danger',
    DEDUCT_DEPOSIT: 'danger',
    CLOSE: 'danger',
    COMPLETE: 'success',
    RETURN: 'warning',
  };
  return map[type] || 'default';
};

function formatJson(value: unknown) {
  if (!value) return '';
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, 2);
  } catch {
    return String(value);
  }
}

watch(() => route.query, () => {
  pauseAutoQuery(() => {
    query.value.orderNo = String(route.query.orderNo || '');
    return loadWithRoute();
  });
}, { deep: true });

onMounted(loadWithRoute);
</script>

<style scoped>
.fixed-order-tip {
  padding: 4px 10px;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  color: #0052d9;
  font-size: 12px;
  font-weight: 700;
  background: #f2f7ff;
}

.log-record {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.log-record--error {
  border-color: #ffd8d2;
  background: #fffafa;
}

.log-record:hover {
  border-color: #d6e4ff;
}

.log-record__main {
  min-width: 0;
}

.log-record__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.log-record__identity,
.log-record__tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.log-record__identity :deep(.t-button) {
  max-width: 240px;
  min-height: 24px;
  padding: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-record__identity strong {
  overflow: hidden;
  color: #101828;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-record__desc {
  margin-top: 7px;
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-record__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 5px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.log-record__error {
  display: -webkit-box;
  margin-top: 6px;
  overflow: hidden;
  color: #c2410c;
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.log-record__actions {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  min-width: 54px;
}

.log-record__actions :deep(.t-button) {
  min-height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 760px) {
  .log-record,
  .log-record__top {
    display: grid;
  }

  .log-record__actions {
    justify-content: flex-start;
  }
}

.oper-log-detail-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.detail-section-title--error {
  color: var(--td-error-color);
}

.json-block,
.oper-log-stack {
  max-height: 220px;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  background: var(--td-bg-color-page);
  border-radius: 6px;
}

.detail-empty {
  color: var(--td-text-color-placeholder);
}
</style>
