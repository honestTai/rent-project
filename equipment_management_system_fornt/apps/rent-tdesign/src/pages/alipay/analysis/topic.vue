<template>
  <div class="analytics-topic-page tail-dashboard-page">
    <section class="analytics-topbar dashboard-header">
      <div class="dashboard-header__copy">
        <div class="dashboard-kicker">专题分析</div>
        <h1>{{ title }}</h1>
        <p>{{ dataRangeText }}<span v-if="lastUpdatedAt"> · 最近刷新 {{ formatDateTime(lastUpdatedAt) }}</span></p>
      </div>
      <div class="toolbar">
        <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
          <template #icon><t-icon name="setting" /></template>
          显示设置
        </t-button>
        <analysis-period-filter
          v-model:period-type="periodType"
          v-model:target-date="targetDate"
          :loading="loading"
          refresh-text="刷新数据"
          @period-change="handlePeriodTypeChange"
          @date-change="handleTargetDateChange"
          @refresh="loadAnalyticsData(true)"
        />
      </div>
    </section>

    <template v-if="hasTopicData">
      <div class="dashboard-module-stack">
      <t-row
        v-if="isTopicModuleVisible('metrics')"
        :gutter="[16, 16]"
        align="stretch"
        class="metric-row dashboard-module-block"
        :style="topicModuleStyle('metrics')"
      >
        <t-col v-for="item in visibleCards" :key="item.label" :xs="12" :sm="6" :xl="3">
          <t-card :bordered="false" class="tail-card metric-card">
            <div class="metric-card__head">
              <div class="metric-card__icon"><t-icon :name="item.icon || 'chart'" /></div>
              <div class="metric-card__compare">
                <span
                  v-for="trend in cardTrendRows(item)"
                  :key="trend.label"
                  :class="`metric-trend metric-trend--${trend.tone}`"
                >
                  <b>{{ trend.label }}</b>
                  <em>{{ trend.value }}</em>
                </span>
              </div>
            </div>
            <div class="metric-card__label">{{ item.label }}</div>
            <div class="metric-card__value">{{ renderCardValue(item) }}</div>
            <div v-if="cardProgressLabel(item)" class="metric-card__footer">
              <span>{{ cardProgressLabel(item) }}</span>
              <span>{{ cardProgress(item) }}%</span>
            </div>
            <div v-if="cardProgressLabel(item)" class="metric-card__track">
              <div class="metric-card__bar" :style="{ width: `${cardProgress(item)}%` }" />
            </div>
            <div v-if="renderCardDesc(item)" class="metric-card__desc">{{ renderCardDesc(item) }}</div>
          </t-card>
        </t-col>
      </t-row>

      <t-row
        v-if="isTopicModuleVisible('charts')"
        :gutter="[16, 16]"
        align="stretch"
        class="dashboard-module-block"
        :style="topicModuleStyle('charts')"
      >
        <t-col :xs="12" :lg="8">
          <t-card :bordered="false" class="tail-card analytics-card chart-card">
            <template #title>
              <div class="card-title">
                <span>{{ topicConfig.primaryTitle }}</span>
                <small>{{ topicConfig.chartHint }}</small>
              </div>
            </template>
            <analytics-chart
              :type="topicChartType"
              :rows="primaryChartRows"
              :x-key="topicConfig.primaryChartLabelKey"
              :value-keys="topicConfig.primaryChartValueKeys"
              :value-labels="topicConfig.primaryChartLabels"
              :horizontal="topicChartType === 'bar'"
              height="360px"
            />
          </t-card>
        </t-col>
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card analytics-card">
            <template #title>
              <div class="card-title">
                <span>{{ topicConfig.secondaryTitle }}</span>
                <small>{{ topicConfig.secondaryHint }}</small>
              </div>
            </template>
            <region-distribution-map v-if="isRegionTopic" :regions="regionMapRows" />
            <analytics-chart
              v-else-if="secondaryRows.length"
              :type="topicChartType"
              :rows="secondaryRows"
              x-key="label"
              :value-keys="['value']"
              :value-labels="{ value: topicConfig.secondaryTitle }"
              :horizontal="topicChartType === 'bar'"
            />
            <div v-else class="empty-block">暂无数据</div>
          </t-card>
        </t-col>
      </t-row>

      <t-row
        v-if="isTopicModuleVisible('table')"
        :gutter="[16, 16]"
        align="stretch"
        class="dashboard-module-block"
        :style="topicModuleStyle('table')"
      >
        <t-col :xs="12">
          <t-card :bordered="false" class="tail-card analytics-card">
            <template #title>
              <div class="card-title">
                <span>{{ topicConfig.primaryTitle }}明细</span>
                <small>用于定位具体用户、区域、设备或日期</small>
              </div>
            </template>
            <t-table
              row-key="__rowKey"
              size="small"
              :data="primaryTableRows"
              :columns="primaryColumns"
              :pagination="frontTablePagination(primaryTableRows)"
              cell-empty-content="-"
            >
              <template #money="{ row, col }">
                <span class="metric-highlight metric-highlight--money">{{ currency(row[col.colKey]) }}</span>
              </template>
              <template #number="{ row, col }">
                <span class="metric-highlight">{{ compactNumber(row[col.colKey]) }}</span>
              </template>
              <template #percent="{ row, col }">
                <span class="metric-highlight metric-highlight--percent">{{ percentText(row[col.colKey]) }}</span>
              </template>
              <template #share="{ row }">
                <div class="share-cell">
                  <div class="share-cell__meta">
                    <span>{{ row.__shareLabel }}</span>
                    <strong>{{ safeNumber(row.__sharePercent).toFixed(1) }}%</strong>
                  </div>
                  <div class="share-cell__track">
                    <div class="share-cell__bar" :style="{ width: `${Math.max(4, safeNumber(row.__sharePercent))}%` }" />
                  </div>
                </div>
              </template>
              <template #tag="{ row, col }">
                <t-tag size="small" variant="light">{{ row[col.colKey] || '普通' }}</t-tag>
              </template>
              <template #auth="{ row }">
                <t-tag size="small" :theme="row.authenticated ? 'success' : 'default'" variant="light">
                  {{ row.authenticated ? '已实名' : '未实名' }}
                </t-tag>
              </template>
            </t-table>
          </t-card>
        </t-col>
      </t-row>

      <t-row
        v-if="additionalSections.length && isTopicModuleVisible('extras')"
        :gutter="[16, 16]"
        class="dashboard-module-block"
        :style="topicModuleStyle('extras')"
      >
        <t-col v-for="section in additionalSections" :key="section.key" :xs="12" :lg="additionalSections.length === 1 ? 12 : 6">
          <t-card :bordered="false" class="tail-card analytics-card section-chart-card">
            <template #title>
              <div class="card-title">
                <span>{{ section.title }}</span>
                <small>{{ section.rows.length }} 条记录 · 图表展示</small>
              </div>
            </template>
            <analytics-chart
              :type="section.chartType"
              :rows="section.chartRows"
              :x-key="section.labelKey"
              :value-keys="[section.valueKey]"
              :value-labels="{ [section.valueKey]: section.valueLabel }"
              :horizontal="section.horizontal"
              height="300px"
            />
            <div class="section-chart-summary">
              <div v-for="item in section.summaryRows" :key="item.key" class="section-chart-summary__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.text }}</strong>
                <i><b :style="{ width: `${item.width}%` }" /></i>
              </div>
            </div>
          </t-card>
        </t-col>
      </t-row>
      </div>
    </template>

    <t-card v-else :bordered="false" class="empty-block">当前范围内暂无{{ title }}数据</t-card>
    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="数据分析显示设置"
      :groups="displaySettingGroups"
      :model-value="{ modules: displaySettings.state.modules }"
      @save="saveDisplaySettings"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { rentApi, resolveData, type AnyRecord } from '@/api/rent';
import AnalysisPeriodFilter from '@/components/business/AnalysisPeriodFilter.vue';
import AnalyticsChart from '@/pages/alipay/components/AnalyticsChart.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import RegionDistributionMap from '@/pages/alipay/components/RegionDistributionMap.vue';
import { formatDateTime } from '@/pages/alipay/shared';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';

type CardField = {
  label: string;
  valueKey: string;
  icon?: string;
  changeKey?: string;
  progressLabel?: string;
  progressKey?: string;
  progressTotalKey?: string;
  progressMax?: number;
  format?: 'number' | 'money' | 'percent' | 'days';
  desc?: string;
  descKey?: string;
  descFormat?: 'number' | 'money' | 'percent' | 'days';
};
type ColumnField = {
  title: string;
  colKey: string;
  width?: number;
  minWidth?: number;
  type?: 'money' | 'number' | 'percent' | 'tag' | 'auth' | 'share';
};

const DEFAULT_TABLE_PAGE_SIZE = 10;
const route = useRoute();
const loading = ref(false);
type PeriodType = 'WEEK' | 'MONTH' | 'YEAR';

const periodType = ref<PeriodType>('MONTH');
const targetDate = ref(formatYmd(new Date()));
const subjectsPayload = ref<AnyRecord | null>(null);
const lastUpdatedAt = ref<string | number>('');
const displaySettingsVisible = ref(false);
const topicModuleDefinitions: DisplaySettingItem[] = [
  { key: 'metrics', label: '核心指标', group: '概览' },
  { key: 'charts', label: '主图表 / 辅助图表', group: '图表' },
  { key: 'table', label: '明细表格', group: '明细' },
  { key: 'extras', label: '扩展分析图表', group: '图表' },
];
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.analysis',
  version: 1,
  definitions: { modules: topicModuleDefinitions },
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [{
  kind: 'modules',
  label: '页面模块',
  title: '数据分析模块',
  description: '控制专题分析页面主要区域的显示和排序。',
  items: topicModuleDefinitions,
}]);
const visibleTopicModules = computed(() => displaySettings.visibleItems('modules', topicModuleDefinitions));
const title = computed(() => String((route.meta.title as AnyRecord)?.zh_CN || route.meta.title || '专题分析'));
const subjectKey = computed(() => subjectKeyMap[String(route.meta.subjectType || '')] || 'userPortrait');
const subjectData = computed(() => (subjectsPayload.value || {})[subjectKey.value] || {});
const summary = computed(() => subjectData.value.summary || {});
const topicConfig = computed(() => topicConfigs[subjectKey.value] || topicConfigs.userPortrait);
const visibleCards = computed(() => topicConfig.value.cards.slice(0, 4));
const primaryRows = computed(() => withRowKey(subjectData.value[topicConfig.value.rowKey] || []));
const primaryTableRows = computed(() => withShareRows(primaryRows.value, topicConfig.value.shareKey));
const primaryChartRows = computed(() => primaryRows.value);
const hasTopicData = computed(() => primaryRows.value.length > 0 || topicConfig.value.cards.some((item) => Boolean(summary.value[item.valueKey])));
const primaryColumns = computed(() => topicConfig.value.columns.map((item) => {
  const column: AnyRecord = {
    title: item.title,
    colKey: item.colKey,
    width: item.width,
    minWidth: item.minWidth,
    ellipsis: true,
  };
  const cell = item.type || inferColumnCell(subjectKey.value, item.colKey);
  if (cell) column.cell = cell;
  return column;
}).concat({
  title: '占比',
  colKey: '__sharePercent',
  minWidth: 220,
  cell: 'share',
}));
const secondaryRows = computed(() => {
  const rows = subjectData.value[topicConfig.value.secondaryRowKey] || [];
  const normalized = rows.map((item: AnyRecord) => ({
    label: String(item.label || item.name || item.date || item.period || item[topicConfig.value.secondaryLabelKey] || '-'),
    value: safeNumber(item.value ?? item.count ?? item.orderCount ?? item.userCount ?? item.totalRevenue ?? item.totalPaid ?? item.totalAmount ?? item.totalRentDays),
  }));
  if (!normalized.length) return secondaryFallbackRows();
  const max = Math.max(...normalized.map((item: AnyRecord) => item.value), 1);
  return normalized.slice(0, 10).map((item: AnyRecord) => ({
    ...item,
    percent: Math.max(4, Math.min(100, Math.round((item.value / max) * 100))),
  }));
});
const additionalSections = computed(() => {
  const skipKeys = new Set(['summary', topicConfig.value.rowKey, topicConfig.value.secondaryRowKey]);
  return topicConfig.value.extraSectionKeys
    .filter((key) => !skipKeys.has(key))
    .map((key) => buildSubjectSection(key, subjectData.value[key]))
    .filter(Boolean) as AnyRecord[];
});
const isRegionTopic = computed(() => subjectKey.value === 'regionAnalysis');
const topicChartType = computed(() => ['orderAnalysis', 'revenueAnalysis', 'rentalAnalysis'].includes(subjectKey.value) ? 'line' : 'bar');
const regionMapRows = computed(() => (primaryRows.value as AnyRecord[]).map((item) => ({
  name: item.name,
  orderCount: item.orderCount,
  totalAmount: item.totalAmount,
})));
const dataRangeText = computed(() => {
  const meta = (subjectsPayload.value || {}).meta || {};
  if (meta.startDate && meta.endDate) {
    return `统计范围：${meta.startDate} 至 ${meta.endDate}，分析订单 ${compactNumber(meta.orderCount || 0)} 单，覆盖用户 ${compactNumber(meta.userCount || 0)} 人`;
  }
  return '统计范围：全部数据';
});

function isTopicModuleVisible(key: string) {
  return visibleTopicModules.value.some((item) => item.key === key);
}

function topicModuleStyle(key: string) {
  const index = visibleTopicModules.value.findIndex((item) => item.key === key);
  return { order: index < 0 ? 999 : index };
}

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  displaySettingsVisible.value = false;
}

function secondaryFallbackRows() {
  if (subjectKey.value === 'rentalAnalysis') {
    return primaryRows.value
      .filter((item: AnyRecord) => safeNumber(item.totalRentDays) > 0 || safeNumber(item.averageRentDays) > 0)
      .slice(-12)
      .map((item: AnyRecord) => ({
        label: String(item.date || item.label || '-'),
        value: safeNumber(item.totalRentDays || item.averageRentDays),
        percent: 100,
      }));
  }
  if (subjectKey.value === 'orderAnalysis') {
    return primaryRows.value
      .filter((item: AnyRecord) => safeNumber(item.orderCount) > 0)
      .slice(-12)
      .map((item: AnyRecord) => ({
        label: String(item.date || item.label || '-'),
        value: safeNumber(item.orderCount),
        percent: 100,
      }));
  }
  if (subjectKey.value === 'revenueAnalysis') {
    return primaryRows.value
      .filter((item: AnyRecord) => safeNumber(item.totalRevenue || item.totalPaid) > 0)
      .slice(-12)
      .map((item: AnyRecord) => ({
        label: String(item.date || item.label || '-'),
        value: safeNumber(item.totalRevenue || item.totalPaid),
        percent: 100,
      }));
  }
  return [];
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

const subjectKeyMap: Record<string, string> = {
  USER_PORTRAIT: 'userPortrait',
  REGION: 'regionAnalysis',
  ORDER: 'orderAnalysis',
  DEVICE: 'deviceAnalysis',
  REVENUE: 'revenueAnalysis',
  RENTAL: 'rentalAnalysis',
};

const topicConfigs: Record<string, {
  cards: CardField[];
  rowKey: string;
  columns: ColumnField[];
  primaryTitle: string;
  secondaryTitle: string;
  secondaryRowKey: string;
  secondaryLabelKey: string;
  primaryChartLabelKey: string;
  primaryChartValueKeys: string[];
  primaryChartLabels: Record<string, string>;
  chartHint: string;
  secondaryHint: string;
  extraSectionKeys: string[];
  shareKey: string;
}> = {
  userPortrait: {
    cards: [
      { label: '累计用户数', valueKey: 'totalUsers', changeKey: 'totalUsersChange', icon: 'user', progressLabel: '实名占比', progressKey: 'authenticatedUsers', progressTotalKey: 'totalUsers', desc: '本期新增', descKey: 'newUsers', descFormat: 'number' },
      { label: '实名认证率', valueKey: 'authenticatedRate', changeKey: 'authenticatedRateChange', icon: 'check-circle', progressLabel: '实名完成率', format: 'percent', desc: '已实名', descKey: 'authenticatedUsers', descFormat: 'number' },
      { label: '下单用户数', valueKey: 'orderingUsers', changeKey: 'orderingUsersChange', icon: 'cart', progressLabel: '下单转化', progressKey: 'orderingUsers', progressTotalKey: 'totalUsers', desc: '复购率', descKey: 'repurchaseRate', descFormat: 'percent' },
      { label: '用户客单价', valueKey: 'avgOrderValue', format: 'money', desc: '平均租期', descKey: 'averageRentDays', descFormat: 'days' },
    ],
    rowKey: 'topCustomerRows',
    primaryTitle: '高价值用户排行',
    secondaryTitle: '用户价值分层',
    secondaryRowKey: 'valueSegmentRows',
    secondaryLabelKey: 'label',
    primaryChartLabelKey: 'userName',
    primaryChartValueKeys: ['valueScore', 'totalAmount'],
    primaryChartLabels: { valueScore: '价值分', totalAmount: '累计金额' },
    chartHint: '按用户价值分与金额综合排序',
    secondaryHint: '用户价值结构',
    extraSectionKeys: ['registerRows', 'provinceRows'],
    shareKey: 'totalAmount',
    columns: [
      { title: '用户', colKey: 'userName', minWidth: 160 },
      { title: '地域', colKey: 'province', minWidth: 100 },
      { title: '实名状态', colKey: 'authenticated', minWidth: 100, type: 'auth' },
      { title: '订单数', colKey: 'orderCount', width: 90 },
      { title: '累计金额', colKey: 'totalAmount', width: 120, type: 'money' },
      { title: '客单价', colKey: 'avgOrderValue', width: 120, type: 'money' },
      { title: '平均租期', colKey: 'avgRentDays', width: 100 },
    ],
  },
  regionAnalysis: {
    cards: [
      { label: '覆盖省份数', valueKey: 'coveredProvinceCount', changeKey: 'coveredProvinceCountChange', icon: 'location', progressLabel: '省份覆盖率', progressKey: 'coveredProvinceCount', progressMax: 34, desc: '覆盖范围' },
      { label: '分析订单数', valueKey: 'totalOrders', changeKey: 'totalOrdersChange', icon: 'root-list', progressLabel: '头部区域占比', progressKey: 'topProvinceOrders', progressTotalKey: 'totalOrders', desc: '地域订单' },
      { label: '累计收入', valueKey: 'totalAmount', changeKey: 'totalAmountChange', icon: 'money', progressLabel: '头部收入贡献', progressKey: 'topProvinceOrders', progressTotalKey: 'totalOrders', format: 'money', desc: '地域收入' },
      { label: '最热区域', valueKey: 'topProvince', changeKey: 'topProvinceOrdersChange', desc: '订单最高' },
    ],
    rowKey: 'provinceRows',
    primaryTitle: '省份明细',
    secondaryTitle: '地域用户热度',
    secondaryRowKey: 'provinceRows',
    secondaryLabelKey: 'name',
    primaryChartLabelKey: 'name',
    primaryChartValueKeys: ['orderCount', 'uniqueUsers'],
    primaryChartLabels: { orderCount: '订单数', uniqueUsers: '用户数' },
    chartHint: '省份订单与用户覆盖',
    secondaryHint: '订单热度地图',
    extraSectionKeys: ['topRevenueRows', 'topOrderRows'],
    shareKey: 'orderCount',
    columns: [
      { title: '省份', colKey: 'name', minWidth: 100 },
      { title: '订单数', colKey: 'orderCount', width: 90 },
      { title: '收入', colKey: 'totalAmount', width: 120, type: 'money' },
      { title: '用户数', colKey: 'uniqueUsers', width: 90 },
      { title: '平均客单价', colKey: 'avgOrderValue', width: 120, type: 'money' },
      { title: '平均租期', colKey: 'averageRentDays', width: 100 },
    ],
  },
  orderAnalysis: {
    cards: [
      { label: '总订单数', valueKey: 'totalOrders', changeKey: 'orderChange', icon: 'root-list', progressLabel: '订单规模', progressKey: 'totalOrders', progressTotalKey: 'totalOrders' },
      { label: '完结率', valueKey: 'finishedRate', changeKey: 'finishedRateChange', icon: 'check-circle', progressLabel: '已完结占比', format: 'percent' },
      { label: '进行中订单', valueKey: 'activeOrders', changeKey: 'activeOrdersChange', icon: 'time', progressLabel: '进行中占比', progressKey: 'activeOrders', progressTotalKey: 'totalOrders' },
      { label: '高峰时段', valueKey: 'peakHourLabel' },
    ],
    rowKey: 'trendRows',
    primaryTitle: '订单趋势明细',
    secondaryTitle: '阶段结构',
    secondaryRowKey: 'statusRows',
    secondaryLabelKey: 'label',
    primaryChartLabelKey: 'date',
    primaryChartValueKeys: ['orderCount', 'totalAmount'],
    primaryChartLabels: { orderCount: '订单数', totalAmount: '金额' },
    chartHint: '周期内订单量与金额走势',
    secondaryHint: '订单状态结构',
    extraSectionKeys: ['hourRows', 'weekdayRows'],
    shareKey: 'orderCount',
    columns: [
      { title: '日期', colKey: 'date', minWidth: 110 },
      { title: '订单数', colKey: 'orderCount', width: 90 },
      { title: '订单金额', colKey: 'totalAmount', width: 120, type: 'money' },
      { title: '总租期', colKey: 'totalRentDays', width: 90 },
      { title: '平均租期', colKey: 'averageRentDays', width: 90 },
    ],
  },
  deviceAnalysis: {
    cards: [
      { label: '覆盖设备数', valueKey: 'coveredDeviceCount', changeKey: 'coveredDeviceCountChange', icon: 'server', progressLabel: '上架覆盖率', progressKey: 'coveredDeviceCount', progressTotalKey: 'listedDeviceCount' },
      { label: '最高价值设备', valueKey: 'topDevice', changeKey: 'topDeviceOrdersChange', icon: 'chart', progressLabel: '头部设备占比', progressKey: 'topDeviceOrders', progressTotalKey: 'totalOrders' },
      { label: '设备收入', valueKey: 'totalRevenue', changeKey: 'totalRevenueChange', icon: 'money', progressLabel: '收入完成度', progressKey: 'totalRevenue', progressTotalKey: 'totalRevenue', format: 'money' },
      { label: '总租赁天数', valueKey: 'totalRentDays' },
    ],
    rowKey: 'deviceRows',
    primaryTitle: '设备价值明细',
    secondaryTitle: '设备收入排行',
    secondaryRowKey: 'deviceRows',
    secondaryLabelKey: 'name',
    primaryChartLabelKey: 'name',
    primaryChartValueKeys: ['orderCount', 'totalAmount'],
    primaryChartLabels: { orderCount: '订单数', totalAmount: '累计收入' },
    chartHint: '设备订单与收入贡献',
    secondaryHint: '收入贡献排行',
    extraSectionKeys: ['topOrderRows', 'topRevenueRows', 'topRentDayRows'],
    shareKey: 'totalAmount',
    columns: [
      { title: '设备名称', colKey: 'name', minWidth: 180 },
      { title: '订单数', colKey: 'orderCount', width: 90 },
      { title: '累计收入', colKey: 'totalAmount', width: 120, type: 'money' },
      { title: '租赁天数', colKey: 'totalRentDays', width: 100 },
      { title: '平均租期', colKey: 'averageRentDays', width: 100 },
    ],
  },
  revenueAnalysis: {
    cards: [
      { label: '总收入', valueKey: 'totalRevenue', changeKey: 'revenueChange', icon: 'money', progressLabel: '收入规模', progressKey: 'totalRevenue', progressTotalKey: 'totalRevenue', format: 'money' },
      { label: '押金规模', valueKey: 'depositAmount', changeKey: 'depositChange', icon: 'wallet', progressLabel: '押金占收入', progressKey: 'depositAmount', progressTotalKey: 'totalRevenue', format: 'money' },
      { label: '实付金额', valueKey: 'paidAmount', changeKey: 'paidChange', icon: 'creditcard', progressLabel: '实付占收入', progressKey: 'paidAmount', progressTotalKey: 'totalRevenue', format: 'money' },
      { label: '客单价', valueKey: 'avgOrderValue', format: 'money' },
    ],
    rowKey: 'trendRows',
    primaryTitle: '收入趋势明细',
    secondaryTitle: '收入结构',
    secondaryRowKey: 'trendRows',
    secondaryLabelKey: 'date',
    primaryChartLabelKey: 'date',
    primaryChartValueKeys: ['totalRevenue', 'totalPaid'],
    primaryChartLabels: { totalRevenue: '总收入', totalPaid: '实付金额' },
    chartHint: '收入、押金与实付趋势',
    secondaryHint: '资金结构走势',
    extraSectionKeys: ['compositionRows'],
    shareKey: 'totalRevenue',
    columns: [
      { title: '日期', colKey: 'date', minWidth: 110 },
      { title: '总收入', colKey: 'totalRevenue', width: 120, type: 'money' },
      { title: '押金规模', colKey: 'depositAmount', width: 120, type: 'money' },
      { title: '实付金额', colKey: 'paidAmount', width: 120, type: 'money' },
    ],
  },
  rentalAnalysis: {
    cards: [
      { label: '总租赁天数', valueKey: 'totalRentDays', changeKey: 'totalRentDaysChange', icon: 'time', progressLabel: '租期规模', progressKey: 'totalRentDays', progressTotalKey: 'totalRentDays' },
      { label: '平均租期', valueKey: 'averageRentDays', changeKey: 'averageRentDaysChange', icon: 'calendar', progressLabel: '30天内占比', progressKey: 'averageRentDays', progressMax: 30, format: 'days' },
      { label: '进行中订单', valueKey: 'activeOrders', changeKey: 'activeOrdersChange', icon: 'root-list', progressLabel: '进行中占比', progressKey: 'activeOrders', progressTotalKey: 'totalOrders' },
      { label: '逾期率', valueKey: 'overdueRate', format: 'percent' },
    ],
    rowKey: 'trendRows',
    primaryTitle: '租期趋势明细',
    secondaryTitle: '租期分布',
    secondaryRowKey: 'durationRows',
    secondaryLabelKey: 'label',
    primaryChartLabelKey: 'date',
    primaryChartValueKeys: ['totalRentDays', 'averageRentDays'],
    primaryChartLabels: { totalRentDays: '总租期', averageRentDays: '平均租期' },
    chartHint: '租赁天数与平均租期走势',
    secondaryHint: '按租赁天数区间统计',
    extraSectionKeys: ['performanceRows'],
    shareKey: 'totalRentDays',
    columns: [
      { title: '日期', colKey: 'date', minWidth: 110 },
      { title: '总租期', colKey: 'totalRentDays', width: 100 },
      { title: '平均租期', colKey: 'averageRentDays', width: 100 },
    ],
  },
};

function buildSubjectsQuery(forceRefresh = false) {
  const query: AnyRecord = {
    periodType: periodType.value,
    targetDate: targetDate.value,
    timezone: 'Asia/Shanghai',
    topN: 20,
  };
  if (forceRefresh) query.forceRefresh = true;
  return query;
}

async function loadAnalyticsData(forceRefresh = false) {
  loading.value = true;
  try {
    const response = await rentApi.fetchAnalyticsSubjects(buildSubjectsQuery(forceRefresh));
    subjectsPayload.value = resolveData<AnyRecord>(response, {});
    lastUpdatedAt.value = subjectsPayload.value?.meta?.generatedAt || Date.now();
  } finally {
    loading.value = false;
  }
}

function formatYmd(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function handlePeriodTypeChange(value: string | number | boolean) {
  periodType.value = String(value) as PeriodType;
  if (!targetDate.value) targetDate.value = formatYmd(new Date());
  loadAnalyticsData();
}

function handleTargetDateChange(value: unknown) {
  targetDate.value = String(value || formatYmd(new Date()));
  loadAnalyticsData();
}

function withRowKey(rows: AnyRecord[]) {
  return rows.map((row, index) => ({ __rowKey: `${row.id || row.date || row.name || index}-${index}`, ...row }));
}

function renderCardValue(item: CardField) {
  return formatByType(summary.value[item.valueKey], item.format);
}

function renderCardDesc(item: CardField) {
  if (!item.desc && !item.descKey) return '';
  const value = item.descKey ? ` ${formatByType(summary.value[item.descKey], item.descFormat)}` : '';
  return `${item.desc || ''}${value}`;
}

function cardTrendRows(item: CardField) {
  return [
    { label: '环比', raw: firstTrendValue(item, 'mom') },
    { label: '同比', raw: firstTrendValue(item, 'yoy') },
  ].map((trend) => ({
    label: trend.label,
    value: trendText(trend.raw),
    tone: trendTone(trend.raw),
  }));
}

function firstTrendValue(item: CardField, type: 'mom' | 'yoy') {
  const suffixes = type === 'mom' ? ['MoM', 'Mom', 'MonthOnMonth'] : ['YoY', 'Yoy', 'YearOnYear'];
  const keys = [
    ...suffixes.map((suffix) => `${item.valueKey}${suffix}`),
    ...(item.changeKey ? suffixes.map((suffix) => `${item.changeKey}${suffix}`) : []),
  ];
  if (type === 'mom' && item.changeKey) keys.unshift(item.changeKey);
  for (const key of keys) {
    const value = summary.value[key];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return null;
}

function trendText(value: unknown) {
  if (value === undefined || value === null || value === '') return '--';
  const number = safeNumber(value);
  if (number === 0) return '持平';
  return `${number > 0 ? '上升' : '下降'} ${Math.abs(number).toFixed(1)}%`;
}

function trendTone(value: unknown) {
  if (value === undefined || value === null || value === '') return 'neutral';
  const number = safeNumber(value);
  if (number > 0) return 'up';
  if (number < 0) return 'down';
  return 'neutral';
}

function cardProgress(item: CardField) {
  if (item.progressKey) {
    const value = safeNumber(summary.value[item.progressKey]);
    if (item.progressTotalKey) {
      const total = safeNumber(summary.value[item.progressTotalKey]);
      return Math.max(4, Math.min(100, Math.round((value / Math.max(total, 1)) * 100)));
    }
    if (item.progressMax) {
      return Math.max(4, Math.min(100, Math.round((value / item.progressMax) * 100)));
    }
  }
  if (item.format === 'percent') return Math.max(4, Math.min(100, Math.round(safeNumber(summary.value[item.valueKey]))));
  return 100;
}

function cardProgressLabel(item: CardField) {
  if (item.progressLabel) return item.progressLabel;
  if (item.format === 'percent') return item.label;
  return '';
}

function withShareRows(rows: AnyRecord[], shareKey: string) {
  const total = rows.reduce((sum, row) => sum + safeNumber(row[shareKey]), 0);
  const max = Math.max(...rows.map((row) => safeNumber(row[shareKey])), 1);
  return rows.map((row) => {
    const value = safeNumber(row[shareKey]);
    return {
      ...row,
      __shareLabel: formatShareValue(value, shareKey),
      __sharePercent: total > 0 ? (value / total) * 100 : (value / max) * 100,
    };
  });
}

function formatShareValue(value: number, shareKey: string) {
  if (['totalAmount', 'totalRevenue', 'totalPaid', 'paidAmount', 'depositAmount', 'avgOrderValue'].includes(shareKey)) {
    return currency(value);
  }
  if (['averageRentDays', 'avgRentDays'].includes(shareKey)) {
    return `${value.toFixed(1)} 天`;
  }
  return compactNumber(value);
}

function formatByType(value: unknown, type?: CardField['format']) {
  if (type === 'money') return currency(value);
  if (type === 'percent') return percentText(value);
  if (type === 'days') return `${safeNumber(value).toFixed(1)} 天`;
  if (type === 'number') return compactNumber(value);
  return value === undefined || value === null || value === '' ? '-' : String(value);
}

function safeNumber(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function currency(value: unknown) {
  return `￥${safeNumber(value).toFixed(2)}`;
}

function percentText(value: unknown) {
  return `${safeNumber(value).toFixed(1)}%`;
}

function compactNumber(value: unknown) {
  const number = safeNumber(value);
  if (number >= 100000000) return `${(number / 100000000).toFixed(1)}亿`;
  if (number >= 10000) return `${(number / 10000).toFixed(1)}万`;
  return String(number);
}

const subjectFieldLabelMap: Record<string, string> = {
  registerRows: '新增用户趋势',
  authRows: '实名认证结构',
  provinceRows: '地域用户热度',
  valueSegmentRows: '用户价值分层',
  trendRows: '趋势明细',
  statusRows: '阶段结构',
  deviceRows: '设备明细',
  hourRows: '时段分布',
  weekdayRows: '星期分布',
  topCustomerRows: '高价值用户排行',
  durationRows: '租期分布',
  performanceRows: '履约表现',
  topOrderRows: '重点订单',
  topRevenueRows: '收入贡献排行',
  topRentDayRows: '租赁天数排行',
  compositionRows: '收入结构',
  summary: '摘要',
  date: '日期',
  label: '名称',
  name: '名称',
  count: '数量',
  value: '数值',
  orderCount: '订单数',
  userCount: '用户数',
  uniqueUsers: '用户数',
  totalAmount: '累计金额',
  authenticatedUsers: '实名用户数',
  orderUserCount: '下单用户数',
  totalRevenue: '总收入',
  totalDeposit: '押金规模',
  totalPaid: '实付金额',
  paidAmount: '实付金额',
  depositAmount: '押金规模',
  avgOrderValue: '平均客单价',
  averageOrderValue: '平均客单价',
  totalRentDays: '总租期',
  averageRentDays: '平均租期',
  avgRentDays: '平均租期',
  authenticated: '实名状态',
  activeUsers: '活跃用户',
  topProvince: '最热区域',
  topProvinceOrders: '最热区域订单',
  finishedRate: '完结率',
  cancelledRate: '取消率',
  activeOrders: '进行中订单',
  overdueRate: '逾期率',
  valueScore: '价值分',
  valueTag: '价值标签',
  valueLevel: '价值等级',
  orderShare: '订单占比',
  factorSummary: '评分说明',
};

function buildSubjectSection(key: string, value: unknown) {
  if (Array.isArray(value)) {
    const rows = withRowKey(value as AnyRecord[]);
    if (!rows.length) return null;
    return buildTableSection(subjectFieldLabelMap[key] || key, key, rows);
  }
  if (value && typeof value === 'object') {
    const rows = withRowKey(Object.keys(value as AnyRecord).map((itemKey) => ({
      name: subjectFieldLabelMap[itemKey] || itemKey,
      value: formatSubjectValue((value as AnyRecord)[itemKey]),
    })));
    if (!rows.length) return null;
    return buildTableSection(subjectFieldLabelMap[key] || key, key, rows, ['name', 'value']);
  }
  return null;
}

function buildTableSection(title: string, sectionKey: string, rows: AnyRecord[], forcedKeys?: string[]) {
  const keys = forcedKeys || resolveSectionColumns(sectionKey, rows);
  const labelKey = resolveSectionLabelKey(keys);
  const rawValueKey = resolveSectionValueKey(sectionKey, keys, labelKey);
  const valueKey = '__chartValue';
  const chartRows = rows.map((row) => ({
    ...row,
    [valueKey]: parseChartNumber(row[rawValueKey]),
  }));
  return {
    key: `${title}-${rows[0]?.__rowKey || rows.length}`,
    title,
    rows,
    chartRows,
    chartType: resolveSectionChartType(sectionKey),
    horizontal: shouldUseHorizontalChart(sectionKey, chartRows, labelKey),
    labelKey,
    valueKey,
    valueLabel: subjectFieldLabelMap[rawValueKey] || subjectFieldLabelMap.value || '数值',
    summaryRows: buildSectionSummaryRows(chartRows, labelKey, valueKey, rawValueKey),
    columns: keys.map((key) => ({
      title: subjectFieldLabelMap[key] || key,
      colKey: key,
      minWidth: 140,
      ellipsis: true,
      cell: forcedKeys ? undefined : inferColumnCell(sectionKey, key),
    })),
  };
}

function resolveSectionLabelKey(keys: string[]) {
  return ['label', 'name', 'date', 'period', 'userName', 'province', 'devName', 'categoryName'].find((key) => keys.includes(key)) || keys[0] || 'label';
}

function resolveSectionValueKey(sectionKey: string, keys: string[], labelKey: string) {
  const preferredMap: Record<string, string[]> = {
    topRevenueRows: ['totalAmount', 'totalRevenue', 'value'],
    topOrderRows: ['orderCount', 'count', 'value'],
    topRentDayRows: ['totalRentDays', 'averageRentDays', 'value'],
    provinceRows: ['totalAmount', 'orderCount', 'userCount', 'value'],
    compositionRows: ['value', 'totalRevenue', 'totalPaid'],
    durationRows: ['value', 'count', 'orderCount'],
    performanceRows: ['value', 'count', 'orderCount'],
    hourRows: ['value', 'count', 'orderCount'],
    weekdayRows: ['value', 'count', 'orderCount'],
    registerRows: ['count', 'value', 'userCount'],
  };
  const preferred = preferredMap[sectionKey] || [];
  return preferred.find((key) => keys.includes(key))
    || keys.find((key) => key !== labelKey && numericSectionKeys.has(key))
    || keys.find((key) => key !== labelKey)
    || labelKey;
}

const numericSectionKeys = new Set([
  'value',
  'count',
  'orderCount',
  'userCount',
  'uniqueUsers',
  'authenticatedUsers',
  'orderUserCount',
  'totalAmount',
  'totalRevenue',
  'totalDeposit',
  'totalPaid',
  'paidAmount',
  'depositAmount',
  'avgOrderValue',
  'averageOrderValue',
  'totalRentDays',
  'averageRentDays',
  'avgRentDays',
  'activeOrders',
]);

function resolveSectionChartType(sectionKey: string) {
  return ['registerRows'].includes(sectionKey) ? 'line' : 'bar';
}

function shouldUseHorizontalChart(sectionKey: string, rows: AnyRecord[], labelKey: string) {
  if (['hourRows', 'weekdayRows', 'registerRows', 'durationRows', 'performanceRows', 'compositionRows'].includes(sectionKey)) return false;
  return rows.length <= 10 && rows.some((row) => String(row[labelKey] || '').length > 5);
}

function parseChartNumber(value: unknown) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0;
  const text = String(value ?? '').replace(/,/g, '').match(/-?\d+(\.\d+)?/);
  return text ? Number(text[0]) : 0;
}

function buildSectionSummaryRows(rows: AnyRecord[], labelKey: string, valueKey: string, rawValueKey: string) {
  const sorted = [...rows]
    .sort((left, right) => safeNumber(right[valueKey]) - safeNumber(left[valueKey]))
    .slice(0, 5);
  const max = Math.max(...sorted.map((row) => safeNumber(row[valueKey])), 1);
  return sorted.map((row, index) => ({
    key: `${row.__rowKey || row[labelKey] || index}-${index}`,
    label: String(row[labelKey] || '-'),
    text: formatShareValue(safeNumber(row[valueKey]), rawValueKey),
    width: Math.max(6, Math.round((safeNumber(row[valueKey]) / max) * 100)),
  }));
}

const preferredSectionColumns: Record<string, string[]> = {
  registerRows: ['date', 'count'],
  provinceRows: ['name', 'userCount', 'authenticatedUsers', 'orderUserCount', 'totalAmount'],
  topOrderRows: ['name', 'orderCount', 'totalAmount', 'uniqueUsers', 'avgOrderValue', 'averageRentDays'],
  topRevenueRows: ['name', 'orderCount', 'totalAmount', 'uniqueUsers', 'avgOrderValue', 'averageRentDays'],
  topRentDayRows: ['name', 'orderCount', 'totalRentDays', 'averageRentDays', 'totalAmount'],
  hourRows: ['label', 'value'],
  weekdayRows: ['label', 'value'],
  compositionRows: ['label', 'value'],
  durationRows: ['label', 'value'],
  performanceRows: ['label', 'value'],
};

function resolveSectionColumns(sectionKey: string, rows: AnyRecord[]) {
  const row = rows[0] || {};
  const preferred = preferredSectionColumns[sectionKey];
  if (preferred) {
    const matched = preferred.filter((key) => key in row);
    if (matched.length) return matched;
  }
  const hidden = new Set(['__rowKey', 'id', 'valueLevel', 'orderShare', 'factorSummary']);
  return Object.keys(row).filter((key) => !hidden.has(key)).slice(0, 6);
}

function inferColumnCell(sectionKey: string, key: string) {
  if (['totalAmount', 'totalRevenue', 'totalDeposit', 'totalPaid', 'paidAmount', 'depositAmount', 'avgOrderValue', 'averageOrderValue'].includes(key)) {
    return 'money';
  }
  if (sectionKey === 'compositionRows' && key === 'value') return 'money';
  if (['finishedRate', 'cancelledRate', 'overdueRate', 'repurchaseRate', 'authenticatedRate'].includes(key)) {
    return 'percent';
  }
  if (['orderCount', 'userCount', 'uniqueUsers', 'authenticatedUsers', 'orderUserCount', 'totalRentDays', 'activeOrders', 'count', 'value'].includes(key)) {
    return 'number';
  }
  if (key === 'authenticated') return 'auth';
  if (key === 'valueTag') return 'tag';
  return undefined;
}

function formatSubjectValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

watch(() => route.fullPath, () => loadAnalyticsData());
onMounted(loadAnalyticsData);
</script>

<style scoped>
.analytics-topic-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 88px);
  background: #f0f2f5;
}

.analytics-card,
.metric-card,
.empty-block {
  height: 100%;
  border-radius: 6px;
}

.analytics-topic-page :deep(.t-card) {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}

.analytics-topic-page :deep(.t-card__header) {
  padding: 18px 20px 0;
  border-bottom: 0;
}

.analytics-topic-page :deep(.t-card__body) {
  padding: 20px;
}

.analytics-topic-page :deep(.t-button) {
  height: 40px;
  border-radius: 8px;
}

.analytics-topic-page :deep(.t-input),
.analytics-topic-page :deep(.t-range-input),
.analytics-topic-page :deep(.t-date-range-picker) {
  min-height: 40px;
  border-radius: 8px;
}

.analytics-topic-page :deep(.t-range-input) {
  background: #fff;
  box-shadow: none !important;
}

.analytics-topic-page :deep(.t-range-input) {
  overflow: hidden;
  padding: 0 14px;
}

.analytics-topic-page :deep(.t-range-input__inner),
.analytics-topic-page :deep(.t-range-input__inner-left),
.analytics-topic-page :deep(.t-range-input__inner-right),
.analytics-topic-page :deep(.t-range-input .t-input),
.analytics-topic-page :deep(.t-range-input .t-input__wrap),
.analytics-topic-page :deep(.t-range-input .t-input__inner),
.analytics-topic-page :deep(.t-range-input input),
.analytics-topic-page :deep(.t-date-range-picker input) {
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.analytics-topic-page :deep(.t-range-input),
.analytics-topic-page :deep(.t-radio-group) {
  border: 1px solid #d0d5dd;
}

.analytics-topic-page :deep(.t-radio-group) {
  min-height: 38px;
  padding: 2px;
  background: #f9fafb;
  border-radius: 8px;
  box-shadow: none;
}

.analytics-topic-page :deep(.t-radio-group__bg-block) {
  border-radius: 6px;
  background: #465fff;
  box-shadow: none;
}

.analytics-topic-page :deep(.t-radio-button),
.analytics-topic-page :deep(.t-radio-button__label) {
  border-radius: 6px;
}

.analytics-topic-page :deep(.t-radio-button) {
  background: transparent !important;
  box-shadow: none !important;
}

.analytics-topic-page :deep(.t-radio-button.t-is-checked),
.analytics-topic-page :deep(.t-radio-button__label.t-is-checked) {
  color: #fff;
  background: transparent !important;
  box-shadow: none;
}

.analytics-topic-page :deep(.t-table) {
  overflow: hidden;
  border-radius: 6px;
}

.analytics-topic-page :deep(.t-table th) {
  color: #475467;
  font-weight: 600;
  background: #fafafa;
}

.analytics-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}

.page-kicker {
  color: #465fff;
  font-size: 12px;
  font-weight: 700;
}

.analytics-topbar h1 {
  margin: 6px 0 0;
  color: #101828;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.analytics-topbar p {
  max-width: 760px;
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar :deep(.t-button) {
  height: 40px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.toolbar :deep(.t-button--variant-outline) {
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
}

.toolbar :deep(.t-radio-group) {
  flex-wrap: wrap;
  max-width: 760px;
}

.toolbar :deep(.t-radio-button) {
  min-width: 48px;
}

.dashboard-module-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-module-block {
  margin: 0 !important;
}

.period-picker {
  width: 220px;
  max-width: 100%;
}

.range-picker {
  width: 352px;
  max-width: 100%;
}

.metric-card {
  min-height: 144px;
}

.metric-row :deep(.t-col) {
  display: flex;
}

.metric-row :deep(.t-card) {
  flex: 1;
}

.metric-card__label,
.metric-card__desc,
.card-title small {
  color: #667085;
}

.metric-card__head,
.metric-card__footer,
.share-cell__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: var(--td-brand-color);
  font-size: 20px;
  background: #eef4ff;
  border-radius: 8px;
}

.metric-card__compare {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
}

.metric-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 112px;
  padding: 3px 7px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  background: #f2f4f7;
  border-radius: 6px;
}

.metric-trend b,
.metric-trend em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-trend b {
  font-weight: 500;
}

.metric-trend em {
  font-style: normal;
  font-weight: 700;
}

.metric-trend--up {
  color: #d92d20;
  background: #fef3f2;
}

.metric-trend--down {
  color: #039855;
  background: #ecfdf3;
}

.metric-card__label {
  margin-top: 12px;
}

.metric-card__value {
  margin-top: 10px;
  color: #101828;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card__desc {
  margin-top: 12px;
  font-size: 12px;
}

.metric-card__footer {
  margin-top: 10px;
  color: #667085;
  font-size: 13px;
}

.metric-card__track,
.share-cell__track {
  height: 8px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 999px;
}

.metric-card__track {
  margin-top: 8px;
}

.metric-card__bar,
.share-cell__bar {
  height: 100%;
  background: linear-gradient(90deg, #465fff 0%, #12b76a 100%);
  border-radius: inherit;
}

.share-cell {
  min-width: 180px;
}

.share-cell__meta {
  margin-bottom: 7px;
  color: #667085;
  font-size: 12px;
}

.share-cell__meta strong {
  color: #101828;
}

.metric-highlight {
  display: inline-flex;
  align-items: center;
  min-width: 52px;
  justify-content: center;
  padding: 3px 9px;
  color: #344054;
  font-weight: 700;
  background: #f2f4f7;
  border-radius: 999px;
}

.metric-highlight--money {
  color: #2f54eb;
  background: #eef4ff;
}

.metric-highlight--percent {
  color: #079455;
  background: #ecfdf3;
}

.card-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.card-title span {
  color: #101828;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.analytics-card {
  min-height: 100%;
}

.chart-card :deep(.analytics-chart) {
  min-height: 330px;
}

.section-chart-card :deep(.t-card__body) {
  display: grid;
  gap: 14px;
}

.section-chart-summary {
  display: grid;
  gap: 10px;
}

.section-chart-summary__item {
  display: grid;
  grid-template-columns: minmax(72px, 1fr) minmax(72px, auto);
  gap: 8px 12px;
  align-items: center;
  color: #667085;
  font-size: 13px;
}

.section-chart-summary__item span,
.section-chart-summary__item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-chart-summary__item strong {
  color: #101828;
}

.section-chart-summary__item i {
  grid-column: 1 / -1;
  height: 8px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 999px;
}

.section-chart-summary__item b {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #465fff 0%, #12b76a 100%);
  border-radius: inherit;
}

.empty-block {
  padding: 28px;
  text-align: center;
  color: var(--td-text-color-secondary);
}

</style>
