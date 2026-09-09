<template>
  <AlipayCleanListPage
    class="aftersale-page"
    title="售后同步"
    description="核对支付宝租赁售后单与本地扣减台账。"
    :rows="rows"
    row-key="snapshotId"
    :loading="loading"
    :pagination="pagination"
    :content-title="contentTitle"
    empty-title="暂无售后记录"
    @page-change="handlePageChange"
  >
    <template #filters>
      <t-input v-model="query.orderNo" class="aftersale-filter-field" :disabled="orderContextActive" clearable placeholder="本地订单号" />
      <t-select
        v-model="query.alipayStatus"
        class="aftersale-filter-field"
        :disabled="orderContextActive"
        clearable
        filterable
        placeholder="订单状态"
        v-bind="searchableSelectProps('aftersale.alipayStatus', alipayStatusOptions)"
      />
      <t-select
        v-model="query.aftersaleStatus"
        class="aftersale-filter-field"
        clearable
        filterable
        placeholder="售后状态"
        v-bind="searchableSelectProps('aftersale.aftersaleStatus', aftersaleStatusOptions)"
      />
      <t-date-range-picker
        v-if="aftersaleQueryExpanded && !orderContextActive"
        v-model="dateRange"
        class="aftersale-filter-range"
        value-type="time-stamp"
        clearable
        :placeholder="['开始日期', '结束日期']"
      />
    </template>

    <template #queryActions>
      <AlipayQueryActions
        v-model:expanded="aftersaleQueryExpanded"
        :expandable="!orderContextActive"
        @search="search"
        @reset="resetQuery"
      />
    </template>

    <template #contentActions>
      <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
        <template #icon><t-icon name="setting" /></template>
        显示设置
      </t-button>
      <PermissionButton :code="buttonCode('aftersale', 'sync')" theme="primary" variant="outline" @click="load">
        {{ scanButtonText }}
      </PermissionButton>
      <PermissionButton
        :code="buttonCode('aftersale', 'import')"
        theme="primary"
        variant="outline"
        :disabled="pendingSnapshotIds.length === 0"
        @click="importPendingRows"
      >
        批量导入售后
      </PermissionButton>
    </template>

    <template #content>
      <div class="aftersale-content-stack">
        <div v-if="orderContextActive" class="aftersale-context-alert">
          <div class="aftersale-context-alert__main">
            <t-icon name="info-circle" />
            <span>来自订单</span>
            <strong>{{ contextOrderNo || '-' }}</strong>
            <em>当前仅展示该订单在支付宝侧的售后同步结果</em>
          </div>
          <div class="aftersale-context-alert__actions">
            <t-button theme="primary" variant="text" size="small" @click="goToOrder()">返回订单</t-button>
            <t-button theme="primary" variant="text" size="small" @click="goToLedger()">查看台账</t-button>
            <t-button variant="text" size="small" @click="viewAllAftersales">查看全部售后</t-button>
          </div>
        </div>

        <div class="aftersale-summary-grid">
          <div v-for="item in summaryCards" :key="item.key" class="aftersale-summary-card" :class="`aftersale-summary-card--${item.tone}`">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <em>{{ item.description }}</em>
          </div>
        </div>

        <div v-if="rows.length" class="aftersale-record-list">
          <article
            v-for="row in rows"
            :key="recordKey(row)"
            class="aftersale-record"
            :class="{ 'aftersale-record--error': row.matchStatus === 'QUERY_FAILED' || row.importStatus === 'FAILED' }"
          >
            <div class="aftersale-record__header">
              <div class="aftersale-record__title-block">
                <t-button variant="text" theme="primary" @click="goToOrder(row)">
                  {{ row.orderNo || '-' }}
                </t-button>
                <span>支付宝订单：{{ row.rentOrderId || '-' }}</span>
              </div>
              <div class="aftersale-record__tags">
                <t-tag :theme="sourceTypeTheme(row.sourceType)" variant="light">{{ sourceTypeText(row.sourceType) }}</t-tag>
                <t-tag :theme="aftersaleStatusTheme(row.aftersaleStatus)" variant="light">{{ aftersaleStatusText(row.aftersaleStatus) }}</t-tag>
                <t-tag :theme="matchStatusTheme(row.matchStatus)" variant="light">{{ matchStatusText(row.matchStatus) }}</t-tag>
                <t-tag :theme="importStatusTheme(row.importStatus)" variant="light">{{ importStatusText(row.importStatus) }}</t-tag>
              </div>
            </div>

            <div class="aftersale-record__body">
              <div
                v-for="field in visibleAftersaleFields"
                :key="field.key"
                class="aftersale-record__field"
                :class="{ 'aftersale-record__field--wide': field.block }"
              >
                <span>{{ field.label }}</span>
                <strong :class="field.valueClass?.(row)">{{ field.value(row) }}</strong>
              </div>
            </div>

            <div class="aftersale-record__footer">
              <div v-if="row.failReason || row.reasonDescription" class="aftersale-record__note" :class="{ 'aftersale-record__note--error': row.failReason }">
                <div class="aftersale-record__note-summary">
                  <t-icon name="error-circle" />
                  <span>{{ failureSummary(row) }}</span>
                </div>
                <details class="aftersale-record__note-detail">
                  <summary>查看原始原因</summary>
                  <p>{{ failureDetail(row) }}</p>
                </details>
              </div>
              <div class="aftersale-record__actions">
                <PermissionButton
                  v-if="isAftersaleActionVisible('import') && canImport(row)"
                  :code="buttonCode('aftersale', 'import')"
                  theme="success"
                  size="small"
                  @click="importRow(row)"
                >
                  导入台账
                </PermissionButton>
                <PermissionButton
                  v-if="canFinishAftersale(row)"
                  :code="buttonCode('order', 'operate')"
                  theme="success"
                  size="small"
                  @click="finishAftersale(row)"
                >
                  补完结售后
                </PermissionButton>
                <t-button v-if="isAftersaleActionVisible('order')" theme="primary" variant="text" size="small" @click="goToOrder(row)">查看订单</t-button>
                <t-button v-if="isAftersaleActionVisible('ledger')" theme="primary" variant="text" size="small" @click="goToLedger(row)">查看台账</t-button>
              </div>
            </div>
          </article>
        </div>
        <t-empty v-else class="aftersale-empty" title="暂无售后记录" description="调整筛选条件或扫描支付宝售后后查看结果" />
      </div>
    </template>

    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="售后同步显示设置"
      :groups="displaySettingGroups"
      :model-value="displaySettings.state"
      @save="saveDisplaySettings"
    />
  </AlipayCleanListPage>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { createSearchableOptions } from '@shared/utils/search-options';
import { cleanQuery, rentApi, resolveData, type AnyRecord } from '@/api/rent';
import PermissionButton from '@/components/business/PermissionButton.vue';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import { buttonCode, formatDateTime, formatMoney } from '@/pages/alipay/shared';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';

type SelectOption = {
  label: string;
  value: string | number;
};

const DAY_MS = 24 * 60 * 60 * 1000;
const now = new Date();
now.setHours(23, 59, 59, 999);
const start = new Date(now.getTime() - 89 * DAY_MS);
start.setHours(0, 0, 0, 0);

const route = useRoute();
const router = useRouter();
const { searchableSelectProps } = createSearchableOptions();

const query = reactive<AnyRecord>({
  orderNo: '',
  alipayStatus: '',
  aftersaleStatus: '',
});
const dateRange = ref<Array<number | string>>([start.getTime(), now.getTime()]);
const rows = ref<AnyRecord[]>([]);
const loading = ref(false);
const summary = ref<AnyRecord>({});
const currentOrder = ref<AnyRecord>({});
const aftersaleQueryExpanded = ref(false);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  pageSizeOptions: [10, 20, 50, 100],
});

const alipayStatusOptions: SelectOption[] = [
  { value: 'CREATED', label: '用户下单' },
  { value: 'SIGNED', label: '用户已签约' },
  { value: 'APPROVED', label: '商家审核通过' },
  { value: 'PAID', label: '已支付' },
  { value: 'DELIVERED', label: '商家发货' },
  { value: 'RECEIVED', label: '用户确认收货' },
  { value: 'RETURN_DELIVERED', label: '用户寄回' },
  { value: 'RETURN_RECEIVED', label: '商家签收' },
  { value: 'FINISHED', label: '订单完结' },
  { value: 'CLOSED', label: '订单关闭' },
];

const aftersaleStatusOptions: SelectOption[] = [
  { value: 'APPROVING', label: '处理中' },
  { value: 'SUCCESS', label: '已成功' },
  { value: 'FAIL', label: '已失败' },
];

const routeOrderId = computed(() => queryText(route.query.orderId));
const routeOrderNo = computed(() => queryText(route.query.orderNo));
const routeFrom = computed(() => queryText(route.query.from));
const orderContextActive = computed(() => Boolean(routeOrderId.value || (routeFrom.value === 'orders' && routeOrderNo.value)));
const contextOrderId = computed(() => String(currentOrder.value.orderId || routeOrderId.value || ''));
const contextOrderNo = computed(() => String(currentOrder.value.orderNo || routeOrderNo.value || query.orderNo || ''));
const contentTitle = computed(() => (orderContextActive.value ? '当前订单售后' : '售后同步记录'));
const scanButtonText = computed(() => (orderContextActive.value ? '同步当前订单售后' : '扫描支付宝售后'));

const summaryCards = computed(() => [
  { key: 'orders', label: '扫描订单', value: summary.value.scannedOrders || 0, tone: 'primary', description: '本轮扫描范围' },
  { key: 'alipay', label: '支付宝售后', value: summary.value.alipayAftersaleCount || 0, tone: 'info', description: '支付宝返回' },
  { key: 'matched', label: '本地已匹配', value: summary.value.matchedCount || 0, tone: 'success', description: '已关联台账' },
  { key: 'pending', label: '待导入', value: summary.value.pendingImportCount || 0, tone: 'warning', description: '需要入库' },
  { key: 'errors', label: '异常', value: summary.value.errorCount || 0, tone: 'danger', description: '查询或导入失败' },
]);

const pendingSnapshotIds = computed(() => rows.value.filter((row) => canImport(row)).map((row) => row.snapshotId));

type AftersaleDisplayField = DisplaySettingItem & {
  block?: boolean;
  value: (row: AnyRecord) => string;
  valueClass?: (row: AnyRecord) => string;
};

const aftersaleFieldDefinitions: AftersaleDisplayField[] = [
  { key: 'businessAdvice', label: '处理结论', group: '处理建议', block: true, value: (row) => businessAdviceText(row) },
  { key: 'rentOrderId', label: '支付宝订单', group: '订单信息', value: (row) => row.rentOrderId || '-' },
  { key: 'aftersaleNo', label: '支付宝售后单号', group: '售后信息', value: (row) => row.aftersaleNo || '-' },
  { key: 'outAftersaleId', label: '商户售后单号', group: '售后信息', value: (row) => row.outAftersaleId || '-' },
  { key: 'feeType', label: '费用类型', group: '金额信息', value: (row) => feeTypeText(row.feeType) },
  { key: 'deductAmount', label: '售后金额', group: '金额信息', value: (row) => amountText(row.deductAmount), valueClass: () => 'aftersale-record__amount' },
  { key: 'localLedger', label: '本地台账', group: '同步信息', value: (row) => localLedgerText(row) },
  { key: 'syncedAt', label: '最近同步', group: '同步信息', value: (row) => formatDateTime(row.syncedAt) },
  { key: 'nextOperationTypes', label: '支付宝下一步', group: '处理建议', block: true, value: (row) => nextOperationText(row) },
];
const aftersaleActionDefinitions: DisplaySettingItem[] = [
  { key: 'import', label: '导入台账', group: '操作按钮' },
  { key: 'order', label: '查看订单', group: '操作按钮' },
  { key: 'ledger', label: '查看台账', group: '操作按钮' },
];
const displaySettingsVisible = ref(false);
const displayDefinitions = computed(() => ({
  fields: aftersaleFieldDefinitions,
  actions: aftersaleActionDefinitions,
}));
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.aftersales',
  version: 1,
  definitions: displayDefinitions,
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [
  {
    kind: 'fields',
    label: '字段',
    title: '售后卡片字段',
    description: '控制售后同步卡片中的订单、售后、金额和处理字段。',
    items: aftersaleFieldDefinitions,
  },
  {
    kind: 'actions',
    label: '操作按钮',
    title: '售后操作按钮',
    description: '控制售后卡片操作按钮的显示和顺序。',
    items: aftersaleActionDefinitions,
  },
]);
const visibleAftersaleFields = computed(() => displaySettings.visibleItems('fields', aftersaleFieldDefinitions));
const isAftersaleActionVisible = (key: string) => displaySettings.state.actions.includes(key);

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  MessagePlugin.success('显示设置已保存');
}

function queryText(value: unknown) {
  if (Array.isArray(value)) return String(value[0] || '');
  return String(value || '');
}

function recordKey(row: AnyRecord) {
  return row.snapshotId || `${row.orderId || row.orderNo || 'order'}-${row.aftersaleNo || row.rentOrderId || 'empty'}`;
}

function buildScanPayload() {
  const payload: AnyRecord = {
    ...query,
    page: pagination.current,
    limit: pagination.pageSize,
  };
  if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
    payload.start = Number(dateRange.value[0]);
    payload.end = Number(dateRange.value[1]);
  }
  return cleanQuery(payload);
}

function buildPreviewPayload() {
  return cleanQuery({
    orderId: routeOrderId.value || undefined,
    orderNo: routeOrderId.value ? undefined : contextOrderNo.value,
    aftersaleStatus: query.aftersaleStatus,
  });
}

async function load() {
  const requestedPage = pagination.current;
  const requestedSize = pagination.pageSize;
  loading.value = true;
  try {
    if (orderContextActive.value) {
      const response = await rentApi.previewAftersales(buildPreviewPayload());
      const data = resolveData<AnyRecord>(response, {});
      rows.value = data.list || [];
      summary.value = data.summary || {};
      currentOrder.value = data.order || currentOrder.value || {};
      pagination.current = 1;
      pagination.pageSize = requestedSize;
      pagination.total = 0;
      if (!rows.value.length) {
        MessagePlugin.info('支付宝侧暂无售后单');
      }
      return;
    }

    currentOrder.value = {};
    const response = await rentApi.scanAftersales(buildScanPayload());
    const data = resolveData<AnyRecord>(response, {});
    rows.value = data.list || [];
    summary.value = data.summary || {};
    pagination.current = Number(data.current || requestedPage);
    pagination.pageSize = Number(data.pageSize || requestedSize);
    pagination.total = Number(data.total || rows.value.length);
  } finally {
    loading.value = false;
  }
}

function search() {
  pagination.current = 1;
  return load();
}

function resetQuery() {
  query.orderNo = orderContextActive.value ? contextOrderNo.value : '';
  query.alipayStatus = '';
  query.aftersaleStatus = '';
  dateRange.value = [start.getTime(), now.getTime()];
  return search();
}

function handlePageChange(pageInfo: AnyRecord) {
  pagination.current = Number(pageInfo.current || 1);
  pagination.pageSize = Number(pageInfo.pageSize || 10);
  return load();
}

function canImport(row: AnyRecord) {
  return row.snapshotId && row.importStatus !== 'IMPORTED' && row.matchStatus !== 'QUERY_FAILED';
}

async function importRow(row: AnyRecord) {
  loading.value = true;
  try {
    await rentApi.importAftersales(cleanQuery({
      snapshotIds: [row.snapshotId],
      orderId: row.orderId || contextOrderId.value,
    }));
    MessagePlugin.success('售后单已导入本地台账');
    await load();
  } finally {
    loading.value = false;
  }
}

async function importPendingRows() {
  if (!pendingSnapshotIds.value.length) {
    MessagePlugin.info('当前没有可导入的售后单');
    return;
  }
  loading.value = true;
  try {
    const response = await rentApi.importAftersales(cleanQuery({
      snapshotIds: pendingSnapshotIds.value,
      orderId: orderContextActive.value ? contextOrderId.value : undefined,
    }));
    const data = resolveData<AnyRecord>(response, {});
    MessagePlugin.success(`导入完成：成功 ${data.imported || 0} 条，失败 ${data.failed || 0} 条`);
    await load();
  } finally {
    loading.value = false;
  }
}

function canFinishAftersale(row: AnyRecord) {
  return row.matchedDeductRecordId
    && row.localDeductStatus === 'SUCCESS'
    && row.aftersaleStatus !== 'FAIL'
    && !isRemoteAftersaleFinished(row);
}

async function finishAftersale(row: AnyRecord) {
  loading.value = true;
  try {
    MessagePlugin.info('正在请求支付宝售后完结...');
    const response = await rentApi.confirmDeductDeposit({
      recordId: row.matchedDeductRecordId,
      operationType: 'AFTERSALE_FINISH',
    });
    const data = resolveData<AnyRecord>(response, {});
    if (data.aftersaleFinishConfirmed === false) {
      MessagePlugin.warning(data.message || '完结请求已提交，但支付宝查询仍未完结');
    } else {
      MessagePlugin.success(data.message || '支付宝售后已完结');
    }
    await load();
  } catch (error: any) {
    MessagePlugin.error(error?.message || '补完结售后失败');
  } finally {
    loading.value = false;
  }
}

function isRemoteAftersaleFinished(row: AnyRecord) {
  return row.aftersaleStatus === 'SUCCESS' || row.finished === true;
}

function goToOrder(row?: AnyRecord) {
  const orderNo = String(row?.orderNo || contextOrderNo.value || '');
  router.push({
    path: '/alipay/orders',
    query: orderNo ? { searchOrderNo: orderNo } : {},
  });
}

function goToLedger(row?: AnyRecord) {
  const orderId = String(row?.orderId || contextOrderId.value || '');
  const orderNo = String(row?.orderNo || contextOrderNo.value || '');
  router.push({
    path: '/alipay/ledger',
    query: cleanQuery({ orderId, orderNo }),
  });
}

function viewAllAftersales() {
  query.orderNo = '';
  query.alipayStatus = '';
  currentOrder.value = {};
  router.push({ path: '/alipay/aftersales' });
}

function applyRouteContext() {
  if (routeOrderNo.value) {
    query.orderNo = routeOrderNo.value;
  } else if (orderContextActive.value && currentOrder.value.orderNo) {
    query.orderNo = currentOrder.value.orderNo;
  }
  pagination.current = 1;
  return load();
}

function aftersaleStatusText(status: string) {
  if (status === 'APPROVING') return '处理中';
  if (status === 'PROCESSING') return '支付宝处理中';
  if (status === 'SUCCESS') return '已成功';
  if (status === 'FAIL') return '已失败';
  return status || '-';
}

function aftersaleStatusTheme(status: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAIL') return 'danger';
  if (status === 'APPROVING' || status === 'PROCESSING') return 'warning';
  return 'default';
}

function sourceTypeText(sourceType: string) {
  if (sourceType === 'MERCHANT') return '商户发起';
  if (sourceType === 'ZHIMA_RENT') return '芝麻租赁';
  return sourceType || '支付宝';
}

function sourceTypeTheme(sourceType: string) {
  if (sourceType === 'MERCHANT') return 'warning';
  if (sourceType === 'ZHIMA_RENT') return 'primary';
  return 'default';
}

function feeTypeText(type: string) {
  if (type === 'INDEMNITY') return '赔付金';
  if (type === 'LATE_FEE') return '违约金';
  return type || '-';
}

function amountText(value: unknown) {
  if (value == null || value === '') return '-';
  return `￥${formatMoney(value)}`;
}

function localLedgerText(row: AnyRecord) {
  if (row.matchStatus === 'MISSING_LOCAL') return '本地没有对应扣减记录';
  if (row.matchStatus === 'QUERY_FAILED') return '查询支付宝售后失败';
  const status = localDeductStatusText(row.localDeductStatus);
  const aftersaleStatus = aftersaleStatusText(row.localAftersaleStatus);
  return `${status} / 本地售后${aftersaleStatus}`;
}

function localDeductStatusText(status: string) {
  if (status === 'SUCCESS') return '本地记成功';
  if (status === 'PROCESSING') return '本地处理中';
  if (status === 'FAILED') return '本地失败';
  if (status === 'CANCELLED') return '本地已取消';
  return status || '-';
}

function nextOperationText(row: AnyRecord) {
  const value = row.nextOperationTypes;
  if (Array.isArray(value) && value.length) return value.join(' / ');
  if (value) return String(value);
  if (canFinishAftersale(row)) {
    return '已扣款成功，等待补完结支付宝售后';
  }
  if (row.localDeductStatus === 'FAILED' && isRemoteAftersaleProcessing(row)) {
    return '扣款失败待重试或撤销（见订单台账）';
  }
  if (isRemoteAftersaleProcessing(row)) {
    return '支付宝仍在处理中';
  }
  if (row.aftersaleStatus === 'SUCCESS') return '支付宝售后已完结';
  if (row.aftersaleStatus === 'FAIL') return '支付宝售后已失败';
  return '-';
}

function isRemoteAftersaleProcessing(row: AnyRecord) {
  return row.aftersaleStatus === 'PROCESSING' || row.aftersaleStatus === 'APPROVING';
}

function businessAdviceText(row: AnyRecord) {
  if (row.matchStatus === 'MISSING_LOCAL') return '支付宝有售后，本地未入台账；请先导入台账。';
  if (canFinishAftersale(row)) {
    return '已扣款成功，但支付宝售后仍处理中；请点击“补完结售后”。';
  }
  if (row.localDeductStatus === 'SUCCESS' && row.aftersaleStatus === 'SUCCESS') {
    return '支付宝与本地都已成功；这笔售后已经闭环。';
  }
  // 本地扣款失败但支付宝售后仍处理中：这是“押金转支付失败 + 售后未关闭”的卡单态，
  // 必须明确区分，引导到订单台账去“重试扣款”或“撤销售后”，不能让人以为只能干等。
  if (row.localDeductStatus === 'FAILED' && isRemoteAftersaleProcessing(row)) {
    return '本地押金扣款失败，但支付宝售后仍处理中；请到订单台账重试扣款，或撤销售后改走用户主动赔付/线下处理，不要重复发起新售后。';
  }
  if (isRemoteAftersaleProcessing(row)) return '支付宝仍有处理中售后；不要重复发起新的赔付售后。';
  if (row.aftersaleStatus === 'FAIL') return '支付宝售后失败；确认原因后再重新发起。';
  return '请结合本地台账状态处理。';
}

function failureDetail(row: AnyRecord) {
  return String(row.failReason || row.reasonDescription || '').trim();
}

function failureSummary(row: AnyRecord) {
  const reason = failureDetail(row);
  if (!reason) return '同步失败，请查看原始原因';
  if (reason.includes('query_options') && reason.includes('枚举列表')) {
    return '支付宝接口参数不兼容，售后查询选项需要调整';
  }
  if (reason.includes('参数[') || reason.includes('INVALID_PARAMETER')) {
    return '支付宝接口参数校验失败';
  }
  if (reason.includes('超时') || /timeout/i.test(reason)) {
    return '支付宝接口响应超时，请稍后重试';
  }
  if (reason.includes('权限') || /permission|auth/i.test(reason)) {
    return '支付宝接口权限校验失败';
  }
  return reason.length > 42 ? `${reason.slice(0, 42)}...` : reason;
}

function matchStatusText(status: string) {
  if (status === 'MATCHED') return '已匹配';
  if (status === 'MISSING_LOCAL') return '本地缺失';
  if (status === 'QUERY_FAILED') return '查询异常';
  return status || '-';
}

function matchStatusTheme(status: string) {
  if (status === 'MATCHED') return 'success';
  if (status === 'MISSING_LOCAL') return 'warning';
  if (status === 'QUERY_FAILED') return 'danger';
  return 'default';
}

function importStatusText(status: string) {
  if (status === 'IMPORTED') return '已导入';
  if (status === 'PENDING_IMPORT') return '待导入';
  if (status === 'FAILED') return '失败';
  return status || '-';
}

function importStatusTheme(status: string) {
  if (status === 'IMPORTED') return 'success';
  if (status === 'PENDING_IMPORT') return 'warning';
  if (status === 'FAILED') return 'danger';
  return 'default';
}

watch(() => route.fullPath, () => {
  applyRouteContext();
});

onMounted(applyRouteContext);
</script>

<style scoped>
.aftersale-page :deep(.clean-list-page__toolbar) {
  padding: 16px 20px;
}

.aftersale-page :deep(.clean-list-page__toolbar-body) {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
}

.aftersale-page :deep(.clean-list-page__filters) {
  grid-template-columns: repeat(3, minmax(180px, 206px));
  gap: 12px;
  width: max-content;
  max-width: 100%;
}

.aftersale-page :deep(.clean-list-page__actions) {
  align-items: center;
  min-width: 0;
}

.aftersale-filter-range {
  grid-column: span 2;
}

.aftersale-content-stack {
  padding: 12px 16px 16px;
  background: #fff;
}

.aftersale-context-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  background: #f7fbff;
}

.aftersale-context-alert__main {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.aftersale-context-alert__main :deep(.t-icon) {
  color: var(--td-brand-color);
  font-size: 16px;
}

.aftersale-context-alert__main strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.aftersale-context-alert__main em {
  color: #98a2b3;
  font-style: normal;
}

.aftersale-context-alert__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

.aftersale-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(128px, 1fr));
  gap: 8px;
  margin-bottom: 10px;
  background: #fff;
}

.aftersale-summary-card {
  position: relative;
  min-width: 0;
  min-height: 74px;
  padding: 10px 12px 9px;
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.aftersale-summary-card::before {
  position: absolute;
  top: 12px;
  left: 0;
  width: 3px;
  height: 38px;
  border-radius: 0 999px 999px 0;
  background: #2f54eb;
  content: '';
}

.aftersale-summary-card--info::before {
  background: #1677ff;
}

.aftersale-summary-card--success::before {
  background: #00a870;
}

.aftersale-summary-card--warning::before {
  background: #d46b08;
}

.aftersale-summary-card--danger::before {
  background: #d92d20;
}

.aftersale-summary-card span {
  display: block;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.aftersale-summary-card strong {
  display: block;
  margin-top: 3px;
  color: #101828;
  font-size: 20px;
  font-weight: 700;
  line-height: 24px;
  font-variant-numeric: tabular-nums;
}

.aftersale-summary-card em {
  display: block;
  overflow: hidden;
  margin-top: 3px;
  color: #98a2b3;
  font-size: 12px;
  font-style: normal;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aftersale-record-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}

.aftersale-record {
  padding: 12px 14px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.aftersale-record:hover {
  border-color: #d6e4ff;
  background: #fbfdff;
}

.aftersale-record--error {
  background: #fff;
}

.aftersale-record--error:hover {
  border-color: #d6e4ff;
  background: #fbfdff;
}

.aftersale-record__header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.aftersale-record__title-block {
  min-width: 0;
}

.aftersale-record__title-block :deep(.t-button) {
  justify-content: flex-start;
  height: auto;
  max-width: 100%;
  padding: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;
}

.aftersale-record__title-block span {
  display: block;
  overflow: hidden;
  margin-top: 2px;
  color: #667085;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aftersale-record__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.aftersale-record__body {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 14px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #f2f4f7;
}

.aftersale-record__field {
  min-width: 0;
}

.aftersale-record__field span {
  display: block;
  color: #98a2b3;
  font-size: 12px;
  line-height: 16px;
}

.aftersale-record__field strong {
  display: block;
  overflow: hidden;
  margin-top: 3px;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aftersale-record__field--wide {
  grid-column: span 2;
}

.aftersale-record__amount {
  color: #047857 !important;
  font-variant-numeric: tabular-nums;
}

.aftersale-record__footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding-top: 8px;
  margin-top: 10px;
  border-top: 1px solid #f2f4f7;
}

.aftersale-record__note {
  min-width: 0;
  padding: 7px 10px;
  border-radius: 6px;
  background: #f6f8fb;
  color: #475467;
  font-size: 12px;
  line-height: 18px;
}

.aftersale-record__note--error {
  background: #fef3f2;
  color: #b42318;
}

.aftersale-record__note-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.aftersale-record__note-summary :deep(.t-icon) {
  flex: 0 0 auto;
  color: #b42318;
  font-size: 14px;
}

.aftersale-record__note-summary span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aftersale-record__note-detail {
  margin-top: 4px;
  color: #667085;
}

.aftersale-record__note-detail summary {
  width: fit-content;
  color: #667085;
  cursor: pointer;
  font-size: 12px;
  line-height: 18px;
}

.aftersale-record__note-detail p {
  margin: 4px 0 0;
  color: #667085;
  line-height: 18px;
  word-break: break-all;
}

.aftersale-record__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 8px;
  white-space: nowrap;
}

.aftersale-record__actions :deep(.t-button) {
  min-width: auto;
  min-height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
}

.aftersale-empty {
  padding: 40px 16px;
}

@media (max-width: 1280px) {
  .aftersale-page :deep(.clean-list-page__filters) {
    grid-template-columns: repeat(2, minmax(180px, 220px));
  }

  .aftersale-filter-range {
    grid-column: span 2;
  }

  .aftersale-summary-grid {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }

  .aftersale-record__body {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .aftersale-page :deep(.clean-list-page__filters) {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .aftersale-filter-range {
    grid-column: auto;
  }

  .aftersale-context-alert,
  .aftersale-record__header,
  .aftersale-record__footer {
    display: grid;
  }

  .aftersale-context-alert__actions,
  .aftersale-record__tags {
    justify-content: flex-start;
  }

  .aftersale-summary-grid,
  .aftersale-record__body {
    grid-template-columns: 1fr;
  }

  .aftersale-record__field--wide {
    grid-column: auto;
  }

  .aftersale-record__actions {
    justify-content: flex-start;
    white-space: normal;
  }
}
</style>
