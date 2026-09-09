<template>
  <div class="report-page tail-dashboard-page">
    <section class="dashboard-header report-dashboard-header">
      <div class="dashboard-header__copy">
        <div class="dashboard-kicker">Report Center</div>
        <h1>报表中心</h1>
        <p>按管理摘要、核心KPI、差异对比、风险预警和行动建议沉淀支付宝租赁周报、月报和年报。</p>
      </div>
      <div class="report-dashboard-header__actions">
        <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
          <template #icon><t-icon name="setting" /></template>
          显示设置
        </t-button>
        <analysis-period-filter
          v-model:period-type="filters.periodType"
          v-model:target-date="filters.targetDate"
          :loading="loading"
          @period-change="searchReports"
          @date-change="searchReports"
          @refresh="fetchReports"
        />
      </div>
    </section>

    <div class="report-module-stack">
    <section
      v-if="isReportModuleVisible('query')"
      class="report-query-card ant-pro-query-card report-module-block"
      :style="reportModuleStyle('query')"
    >
      <div class="report-query-card__title">查询条件</div>
      <t-form layout="inline" :data="filters" label-width="0" class="report-filter" @submit="searchReports">
        <t-form-item>
          <t-select v-model="filters.reportType" class="form-control" clearable :options="reportTypeOptions" placeholder="报表类型" />
        </t-form-item>
        <t-form-item>
          <t-select v-model="filters.generateMode" class="form-control" clearable :options="generateModeOptions" placeholder="生成方式" />
        </t-form-item>
        <t-form-item class="report-filter__actions">
          <AlipayQueryActions @search="searchReports" @reset="resetFilters">
            <t-button theme="primary" variant="outline" @click="openGenerateDialog">补生成</t-button>
          </AlipayQueryActions>
        </t-form-item>
      </t-form>
    </section>

    <div v-if="isReportModuleVisible('summary')" class="summary-grid report-module-block" :style="reportModuleStyle('summary')">
      <t-card v-for="item in statCards" :key="item.label" :bordered="false" class="tail-card stat-card">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value" :class="item.className">{{ item.value }}</div>
        <div class="stat-desc">{{ item.desc }}</div>
      </t-card>
    </div>

    <div v-if="isReportModuleVisible('charts')" class="chart-grid report-module-block" :style="reportModuleStyle('charts')">
      <t-card class="tail-card report-chart-card" :bordered="false">
        <template #title>收入与订单趋势</template>
        <analytics-chart
          :rows="trendRows"
          x-key="periodKey"
          :value-keys="['amount', 'count']"
          :value-labels="{ amount: '总收入', count: '订单量' }"
          height="320px"
        />
      </t-card>
      <t-card class="tail-card report-chart-card" :bordered="false">
        <template #title>履约状态</template>
        <analytics-chart
          type="bar"
          :rows="fulfillmentRows"
          x-key="label"
          :value-keys="['value']"
          :value-labels="{ value: '订单数' }"
          height="320px"
        />
      </t-card>
    </div>

    <t-card
      v-if="isReportModuleVisible('table')"
      class="tail-card report-table-card report-module-block"
      :bordered="false"
      :style="reportModuleStyle('table')"
    >
      <template #title>历史报表</template>
      <t-table
        row-key="id"
        hover
        table-layout="fixed"
        :data="reports"
        :columns="columns"
        :loading="loading"
        :pagination="frontTablePagination(reports)"
        cell-empty-content="-"
      >
        <template #reportType="{ row }">{{ typeLabel(row.reportType) }}</template>
        <template #totalRevenue="{ row }"><span class="money-text">{{ formatMoney(row.totalRevenue) }}</span></template>
        <template #profitAmount="{ row }"><span class="success-text">{{ formatMoney(row.profitAmount) }}</span></template>
        <template #generatedAt="{ row }">{{ formatDateTime(row.generatedAt) }}</template>
        <template #operation="{ row }">
          <t-button variant="text" theme="primary" @click="openDetail(row)">详情</t-button>
        </template>
      </t-table>
    </t-card>
    </div>

    <t-dialog v-model:visible="detailVisible" :header="detail.reportTitle || '报表详情'" width="780px" :footer="false">
      <div class="detail-toolbar">
        <div class="detail-meta">
          <t-tag theme="primary" variant="light">{{ typeLabel(detail.reportType) }}</t-tag>
          <span>{{ detail.periodKey || '-' }}</span>
        </div>
        <t-button theme="primary" variant="outline" :disabled="!detail.summaryCards.length" @click="exportReportDetail">导出</t-button>
      </div>

      <div class="detail-summary-grid">
        <div v-for="card in detail.summaryCards" :key="card.label" class="detail-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value ?? '-' }}</strong>
          <em>{{ card.desc }}</em>
        </div>
      </div>

      <t-card class="tail-card report-detail-panel" :bordered="false">
        <template #title>趋势</template>
        <analytics-chart
          :rows="detail.trendRows"
          x-key="label"
          :value-keys="['amount', 'count']"
          :value-labels="{ amount: '金额', count: '订单数' }"
          height="260px"
        />
      </t-card>

      <t-card v-for="section in detail.keySections" :key="section.title" class="tail-card report-detail-panel" :bordered="false">
        <template #title>
          <div class="report-detail-title">
            <span>{{ section.title }}</span>
            <small>{{ (section.rows || []).length }} 项 · 经营复盘</small>
          </div>
        </template>
        <div class="detail-section-rows">
          <div v-for="row in sectionRows(section)" :key="row.name" class="detail-section-row">
            <div class="detail-section-row__main">
              <span>{{ row.name }}</span>
              <strong>{{ row.value ?? '-' }}</strong>
            </div>
            <p v-if="row.desc">{{ row.desc }}</p>
            <div v-if="hasComparison(row)" class="detail-section-row__compare">
              <span v-if="row.previous !== undefined">上期 {{ row.previous ?? '-' }}</span>
              <span v-if="row.samePeriodLastYear !== undefined">去年同期 {{ row.samePeriodLastYear ?? '-' }}</span>
              <span v-if="row.mom !== undefined">环比 {{ row.mom ?? '-' }}</span>
              <span v-if="row.yoy !== undefined">同比 {{ row.yoy ?? '-' }}</span>
            </div>
            <em v-if="row.insight">{{ row.insight }}</em>
          </div>
        </div>
      </t-card>

      <t-empty
        v-if="!detail.summaryCards.length && !detail.trendRows.length && !detail.keySections.length"
        description="暂无新结构报表详情"
      />
    </t-dialog>

    <t-dialog v-model:visible="generateDialogVisible" header="补生成报表" width="520px" :footer="false">
      <t-form :data="generateForm" label-width="96px" class="generate-dialog-form">
        <t-form-item label="报表类型">
          <t-select v-model="generateForm.reportType" :options="reportTypeOptions" @change="handleGenerateTypeChange" />
        </t-form-item>
        <t-form-item label="目标时间">
          <t-date-picker v-model="generateForm.targetDate" value-type="YYYY-MM-DD" :mode="generatePickerMode" :placeholder="generatePickerPlaceholder" />
        </t-form-item>
      </t-form>
      <div class="dialog-footer">
        <t-button variant="outline" @click="generateDialogVisible = false">取消</t-button>
        <t-button theme="primary" :loading="generating" @click="handleGenerate">生成</t-button>
      </div>
    </t-dialog>
    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="报表中心显示设置"
      :groups="displaySettingGroups"
      :model-value="{ modules: displaySettings.state.modules }"
      @save="saveDisplaySettings"
    />
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { cleanQuery, rentApi, resolveData, type AnyRecord } from '@/api/rent';
import AnalysisPeriodFilter from '@/components/business/AnalysisPeriodFilter.vue';
import AnalyticsChart from '@/pages/alipay/components/AnalyticsChart.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import { formatDateTime } from '@/pages/alipay/shared';
import { downloadExcel } from '@/utils/csv-export';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';
import { useAutoQuery } from '@/utils/useAutoQuery';

type ReportType = 'WEEK' | 'MONTH' | 'YEAR';
type GenerateForm = {
  reportType: ReportType;
  targetDate: string;
};

const reportTypeOptions = [
  { label: '周报', value: 'WEEK' },
  { label: '月报', value: 'MONTH' },
  { label: '年报', value: 'YEAR' },
];
const generateModeOptions = [
  { label: '自动', value: 'AUTO' },
  { label: '手动', value: 'MANUAL' },
];

function currentDateText() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function formatDateOnly(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function resolvePeriodRange(periodType: string, targetDate: string) {
  const date = targetDate ? new Date(targetDate) : new Date();
  const safeDate = Number.isNaN(date.getTime()) ? new Date() : date;
  if (periodType === 'WEEK') {
    const start = new Date(safeDate);
    const day = start.getDay() || 7;
    start.setDate(start.getDate() - day + 1);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return [formatDateOnly(start), formatDateOnly(end)];
  }
  if (periodType === 'YEAR') {
    return [formatDateOnly(new Date(safeDate.getFullYear(), 0, 1)), formatDateOnly(new Date(safeDate.getFullYear(), 11, 31))];
  }
  return [formatDateOnly(new Date(safeDate.getFullYear(), safeDate.getMonth(), 1)), formatDateOnly(new Date(safeDate.getFullYear(), safeDate.getMonth() + 1, 0))];
}

const filters = reactive<AnyRecord>({ reportType: '', generateMode: '', periodType: 'MONTH', targetDate: currentDateText() });
const generateForm = reactive<GenerateForm>({ reportType: 'WEEK', targetDate: '' });
const DEFAULT_TABLE_PAGE_SIZE = 10;
const loading = ref(false);
const reports = ref<AnyRecord[]>([]);
const detailVisible = ref(false);
const detail = ref(normalizeDetail({}));
const generating = ref(false);
const generateDialogVisible = ref(false);
const displaySettingsVisible = ref(false);
const reportModuleDefinitions: DisplaySettingItem[] = [
  { key: 'query', label: '查询条件', group: '筛选' },
  { key: 'summary', label: '核心摘要', group: '概览' },
  { key: 'charts', label: '趋势与履约图表', group: '图表' },
  { key: 'table', label: '历史报表', group: '明细' },
];
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.reports',
  version: 1,
  definitions: { modules: reportModuleDefinitions },
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [{
  kind: 'modules',
  label: '页面模块',
  title: '报表中心模块',
  description: '控制报表中心主要区域的显示和排序。',
  items: reportModuleDefinitions,
}]);
const visibleReportModules = computed(() => displaySettings.visibleItems('modules', reportModuleDefinitions));

const totalRevenue = computed(() => reports.value.reduce((sum, item) => sum + toNumber(item.totalRevenue), 0));
const totalPaid = computed(() => reports.value.reduce((sum, item) => sum + toNumber(item.profitAmount), 0));
const totalSales = computed(() => reports.value.reduce((sum, item) => sum + toNumber(item.salesCount), 0));
const totalActiveRentals = computed(() => reports.value.reduce((sum, item) => sum + toNumber(item.activeRentals), 0));
const totalCancelled = computed(() => reports.value.reduce((sum, item) => sum + toNumber(item.cancelledOrders), 0));
const statCards = computed(() => [
  { label: '总收入', value: formatMoney(totalRevenue.value), desc: '当前筛选周期', className: 'money-text' },
  { label: '实付金额', value: formatMoney(totalPaid.value), desc: '实际入账', className: 'success-text' },
  { label: '订单数', value: totalSales.value, desc: '租赁订单量' },
  { label: '活跃/取消', value: `${totalActiveRentals.value} / ${totalCancelled.value}`, desc: '履约关注' },
]);
const trendRows = computed(() => reports.value.slice().reverse().map((row) => ({
  periodKey: row.periodKey || '-',
  amount: toNumber(row.totalRevenue),
  count: toNumber(row.salesCount),
})));
const fulfillmentRows = computed(() => [
  { label: '活跃租赁', value: totalActiveRentals.value },
  { label: '取消订单', value: totalCancelled.value },
]);
const generatePickerModeMap: Record<ReportType, 'week' | 'month' | 'year'> = { WEEK: 'week', MONTH: 'month', YEAR: 'year' };
const generatePickerPlaceholderMap: Record<ReportType, string> = { WEEK: '选择目标周', MONTH: '选择目标月', YEAR: '选择目标年' };
const generatePickerMode = computed(() => generatePickerModeMap[generateForm.reportType]);
const generatePickerPlaceholder = computed(() => generatePickerPlaceholderMap[generateForm.reportType]);
const columns = [
  { title: '报表标题', colKey: 'reportTitle', minWidth: 260, ellipsis: true },
  { title: '类型', colKey: 'reportType', width: 88 },
  { title: '周期', colKey: 'periodKey', width: 120 },
  { title: '总收入', colKey: 'totalRevenue', width: 130, align: 'right' },
  { title: '实付金额', colKey: 'profitAmount', width: 130, align: 'right' },
  { title: '订单数', colKey: 'salesCount', width: 96, align: 'right' },
  { title: '活跃/取消', colKey: 'fulfillmentText', width: 120 },
  { title: '生成时间', colKey: 'generatedAt', minWidth: 160 },
  { title: '操作', colKey: 'operation', width: 88, fixed: 'right', align: 'center' },
];

function isReportModuleVisible(key: string) {
  return visibleReportModules.value.some((item) => item.key === key);
}

function reportModuleStyle(key: string) {
  const index = visibleReportModules.value.findIndex((item) => item.key === key);
  return { order: index < 0 ? 999 : index };
}

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  displaySettingsVisible.value = false;
}

function typeLabel(type: string) {
  return ({ WEEK: '周报', MONTH: '月报', YEAR: '年报' } as Record<string, string>)[type] || type || '-';
}

function toNumber(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function formatMoney(value: unknown) {
  return toNumber(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function sectionRows(section: AnyRecord) {
  return Array.isArray(section.rows) ? section.rows : [];
}

function hasComparison(row: AnyRecord) {
  return row.previous !== undefined || row.samePeriodLastYear !== undefined || row.mom !== undefined || row.yoy !== undefined;
}

function frontTablePagination(rows: unknown) {
  const total = Array.isArray(rows) ? rows.length : 0;
  return {
    defaultCurrent: 1,
    defaultPageSize: DEFAULT_TABLE_PAGE_SIZE,
    pageSizeOptions: [10, 20, 50, 100],
    total,
  };
}

function buildQueryPayload() {
  const payload: AnyRecord = {
    reportType: filters.reportType,
    generateMode: filters.generateMode,
  };
  const range = resolvePeriodRange(String(filters.periodType || 'MONTH'), String(filters.targetDate || currentDateText()));
  payload.startTime = `${range[0]} 00:00:00`;
  payload.endTime = `${range[1]} 23:59:59`;
  return cleanQuery(payload);
}

async function fetchReports() {
  loading.value = true;
  try {
    const response = await rentApi.fetchReportList(buildQueryPayload());
    reports.value = resolveData<AnyRecord[]>(response, []).map((row) => ({
      ...row,
      fulfillmentText: `${row.activeRentals ?? 0} / ${row.cancelledOrders ?? 0}`,
    }));
  } finally {
    loading.value = false;
  }
}

function searchReports() {
  fetchReports();
}

const { pauseAutoQuery } = useAutoQuery(filters, searchReports);

function resetFilters() {
  pauseAutoQuery(() => {
    filters.reportType = '';
    filters.generateMode = '';
    filters.periodType = 'MONTH';
    filters.targetDate = currentDateText();
    return searchReports();
  });
}

function handleGenerateTypeChange() {
  generateForm.targetDate = '';
}

function openGenerateDialog() {
  generateForm.targetDate = '';
  generateDialogVisible.value = true;
}

function formatGenerateTargetDate(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) return '';
  const text = value.trim();
  if (/^\d{4}$/.test(text)) return `${text}-01-01 00:00:00`;
  if (/^\d{4}-\d{2}$/.test(text)) return `${text}-01 00:00:00`;
  return `${text.slice(0, 10)} 00:00:00`;
}

async function handleGenerate() {
  const targetDate = formatGenerateTargetDate(generateForm.targetDate);
  if (!targetDate) {
    MessagePlugin.warning('请选择目标日期');
    return;
  }
  generating.value = true;
  try {
    await rentApi.generateReport({ reportType: generateForm.reportType, targetDate });
    MessagePlugin.success('补生成成功');
    generateDialogVisible.value = false;
    fetchReports();
  } finally {
    generating.value = false;
  }
}

async function openDetail(row: AnyRecord) {
  const response = await rentApi.fetchReportDetail(row.id);
  detail.value = normalizeDetail(resolveData<AnyRecord>(response, {}));
  detailVisible.value = true;
}

function normalizeDetail(raw: AnyRecord) {
  const summary = raw.summary || {};
  return {
    reportTitle: summary.reportTitle || '报表详情',
    reportType: summary.reportType || '',
    periodKey: summary.periodKey || '',
    summaryCards: Array.isArray(raw.summaryCards) ? raw.summaryCards : [],
    trendRows: Array.isArray(raw.trendRows) ? raw.trendRows : [],
    keySections: Array.isArray(raw.keySections) ? raw.keySections : [],
  };
}

function exportReportDetail() {
  const rows: AnyRecord[] = [];
  detail.value.summaryCards.forEach((item: AnyRecord) => rows.push({ 分区: '核心指标', 指标: item.label, 数值: item.value, 说明: item.desc }));
  detail.value.trendRows.forEach((item: AnyRecord) => rows.push({ 分区: '趋势', 周期: item.label, 金额: item.amount, 数量: item.count }));
  detail.value.keySections.forEach((section: AnyRecord) => {
    (section.rows || []).forEach((row: AnyRecord) =>
      rows.push({
        分区: section.title,
        指标: row.name,
        本期: row.value,
        上期: row.previous,
        去年同期: row.samePeriodLastYear,
        环比: row.mom,
        同比: row.yoy,
        说明: row.desc,
        判断: row.status,
        建议: row.insight,
      }));
  });
  downloadExcel(`${String(detail.value.reportTitle || '支付宝租赁报表详情').replace(/[\\/:*?"<>|]/g, '-')}`, rows);
}

onMounted(fetchReports);
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 88px);
  background: #f8fafc;
}

.report-dashboard-header {
  display: flex;
}

.report-dashboard-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.report-dashboard-header__actions :deep(.t-button) {
  height: 40px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.report-dashboard-header__actions :deep(.t-button--variant-outline) {
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
}

.report-module-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-module-block {
  margin: 0;
}

.report-query-card,
.report-chart-card,
.report-table-card,
.report-detail-panel,
.stat-card,
.detail-card {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}

.report-query-card {
  display: grid;
  gap: 16px;
  padding: 18px 24px;
  background: #fff;
}

.report-query-card__title {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}

.report-filter {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr)) auto;
  gap: 16px 24px;
  align-items: center;
  width: 100%;
  max-width: 100%;
}

.report-filter :deep(.t-form__item) {
  min-width: 0;
  margin: 0;
}

.report-filter :deep(.t-form__label) {
  display: none;
}

.report-filter :deep(.t-form__controls),
.report-filter :deep(.t-form__controls-content) {
  width: 100%;
  min-width: 0;
}

.report-filter__actions :deep(.t-form__controls-content) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.form-control {
  width: 100%;
  min-width: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.generate-dialog-form :deep(.t-select),
.generate-dialog-form :deep(.t-date-picker) {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.stat-card {
  min-height: 112px;
}

.stat-label {
  color: var(--td-text-color-secondary);
}

.stat-value {
  margin-top: 10px;
  color: #101828;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-desc {
  margin-top: 8px;
  color: var(--td-text-color-placeholder);
  font-size: 12px;
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 16px;
}

.report-chart-card,
.report-table-card {
  min-width: 0;
}

.money-text {
  color: var(--td-brand-color);
  font-weight: 700;
}

.success-text {
  color: var(--td-success-color);
  font-weight: 700;
}

.detail-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--td-text-color-secondary);
}

.detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.detail-card {
  min-height: 104px;
  padding: 14px;
  background: var(--td-bg-color-container);
}

.detail-card span,
.detail-card em {
  display: block;
  color: var(--td-text-color-secondary);
  font-size: 12px;
  font-style: normal;
}

.detail-card strong {
  display: block;
  margin: 8px 0;
  color: #101828;
  font-size: 20px;
  line-height: 1.25;
  word-break: break-all;
}

.report-detail-panel {
  margin-bottom: 16px;
}

.report-detail-title {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.report-detail-title span {
  overflow: hidden;
  color: #101828;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-detail-title small {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-section-rows {
  display: grid;
  gap: 12px;
}

.detail-section-row {
  display: grid;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fcfcfd;
  gap: 8px;
}

.detail-section-row__main,
.detail-section-row__compare {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-section-row__main {
  color: #667085;
  font-size: 13px;
}

.detail-section-row__main span,
.detail-section-row__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-section-row__main strong {
  color: #101828;
  font-size: 14px;
}

.detail-section-row p,
.detail-section-row em,
.detail-section-row__compare {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-style: normal;
}

.detail-section-row__compare {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.detail-section-row__compare span {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f2f4f7;
}

@media (max-width: 1180px) {
  .report-filter {
    grid-template-columns: repeat(2, minmax(180px, 240px));
  }

  .summary-grid,
  .chart-grid,
  .detail-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .report-dashboard-header {
    flex-direction: column;
  }

  .report-filter,
  .summary-grid,
  .chart-grid,
  .detail-summary-grid {
    grid-template-columns: 1fr;
  }

  .report-dashboard-header__actions {
    justify-content: flex-start;
  }
}
</style>
