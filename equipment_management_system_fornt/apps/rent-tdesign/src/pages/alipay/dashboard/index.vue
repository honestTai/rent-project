<template>
  <div class="analytics-page tail-dashboard-page">
    <section class="dashboard-header">
      <div class="dashboard-header__copy">
        <span class="dashboard-kicker">Alipay Rental Overview</span>
        <h1>支付宝经营看板</h1>
        <p>{{ dashboardSummaryText }}</p>
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
          @period-change="handlePeriodTypeChange"
          @date-change="handleTargetDateChange"
          @refresh="loadAnalyticsData(true)"
        />
      </div>
    </section>

    <template v-if="hasAnalyticsData">
      <div class="dashboard-module-stack">
      <t-row v-if="isDashboardModuleVisible('metrics')" :gutter="[16, 16]" class="dashboard-module-block" :style="dashboardModuleStyle('metrics')">
        <t-col v-for="item in statCards" :key="item.label" :xs="12" :sm="6" :xl="3">
          <t-card :bordered="false" class="tail-card metric-card" :class="`metric-card--${item.tone}`">
            <div class="metric-card__head">
              <div class="metric-card__icon" :class="`metric-card__icon--${item.tone}`"><t-icon :name="item.icon" /></div>
              <span class="trend-text" :class="trendClass(item.trend)">{{ item.trend }}</span>
            </div>
            <div class="metric-card__label">{{ item.label }}</div>
            <div class="metric-card__value">{{ item.value }}</div>
            <div class="metric-card__footer">{{ item.desc }}</div>
          </t-card>
        </t-col>
      </t-row>

      <t-row v-if="isDashboardModuleVisible('trendTarget')" :gutter="[16, 16]" align="stretch" class="dashboard-module-block" :style="dashboardModuleStyle('trendTarget')">
        <t-col :xs="12" :xl="8">
          <t-card :bordered="false" class="tail-card chart-card">
            <div class="chart-heading">
              <div>
                <div class="tail-card__title">经营趋势</div>
                <div class="tail-card__subtitle">订单量、租赁收入和累计收入走势，{{ comparison.orderDeltaText || dataRangeText }}</div>
              </div>
              <t-radio-group v-model="trendMode" variant="default-filled" size="small">
                <t-radio-button value="order">订单</t-radio-button>
                <t-radio-button value="revenue">收入</t-radio-button>
              </t-radio-group>
            </div>
            <analytics-chart
              type="line"
              :rows="dailyRows"
              x-key="date"
              :value-keys="trendValueKeys"
              :value-labels="{ orderCount: '订单量', orderAmount: '订单金额', runningAmount: '累计收入' }"
              height="376px"
            />
          </t-card>
        </t-col>
        <t-col :xs="12" :xl="4">
          <t-card :bordered="false" class="tail-card target-card">
            <div class="chart-heading">
              <div>
                <div class="tail-card__title">履约目标</div>
                <div class="tail-card__subtitle">完结率 {{ funnelCompletionRate }}%，追踪订单履约进度</div>
              </div>
              <t-tag variant="light" theme="success">{{ dataRangeText }}</t-tag>
            </div>
            <div class="target-ring" :style="{ '--target-progress': `${targetProgress}%` }">
              <div>
                <strong>{{ targetProgress }}%</strong>
                <span>完成</span>
              </div>
            </div>
            <p class="target-copy">
              当前筛选范围内已完结 {{ compactNumber(analytics.finishedOrders) }} 单，履约中 {{ compactNumber(analytics.activeOrders) }} 单，平均租期 {{ safeNumber(analytics.averageRentDays).toFixed(1) }} 天。
            </p>
            <div class="target-metrics">
              <div v-for="item in targetMetrics" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </t-card>
        </t-col>
      </t-row>

      <t-row v-if="isDashboardModuleVisible('distribution')" :gutter="[16, 16]" align="stretch" class="dashboard-module-block" :style="dashboardModuleStyle('distribution')">
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card">
            <div class="tail-card__title">订单状态分布</div>
            <div class="tail-card__subtitle">拆解待履约、履约中、已完结和异常状态占比</div>
            <analytics-chart type="pie" :rows="statusChartRows" x-key="label" :value-keys="['count']" :value-labels="{ count: '订单数' }" height="285px" />
          </t-card>
        </t-col>
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card">
            <div class="tail-card__title">租期结构</div>
            <div class="tail-card__subtitle">按租赁时长区间观察用户选择偏好</div>
            <analytics-chart type="bar" :rows="durationChartRows" x-key="label" :value-keys="['count']" :value-labels="{ count: '订单数' }" height="285px" />
          </t-card>
        </t-col>
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card">
            <div class="tail-card__title">履约漏斗</div>
            <div class="tail-card__subtitle">完结率 {{ funnelCompletionRate }}%，用于判断订单流转健康度</div>
            <div class="funnel-list">
              <div v-for="item in funnelRows" :key="item.label" class="funnel-row">
                <div class="funnel-row__meta">
                  <span>{{ item.label }}</span>
                  <strong>{{ compactNumber(item.count) }}</strong>
                </div>
                <div class="funnel-row__track">
                  <div class="funnel-row__bar" :style="{ width: `${item.rate}%` }" />
                </div>
              </div>
            </div>
          </t-card>
        </t-col>
      </t-row>

      <t-row v-if="isDashboardModuleVisible('deviceInsight')" :gutter="[16, 16]" align="stretch" class="dashboard-module-block" :style="dashboardModuleStyle('deviceInsight')">
        <t-col :xs="12" :lg="7">
          <t-card :bordered="false" class="tail-card">
            <div class="chart-heading">
              <div>
                <div class="tail-card__title">热门设备</div>
                <div class="tail-card__subtitle">按订单、金额或租赁天数查看设备贡献</div>
              </div>
              <t-radio-group v-model="hotMetric" variant="default-filled" size="small">
                <t-radio-button value="orderCount">订单</t-radio-button>
                <t-radio-button value="totalAmount">金额</t-radio-button>
                <t-radio-button value="totalRentDays">天数</t-radio-button>
              </t-radio-group>
            </div>
            <analytics-chart type="bar" horizontal :rows="sortedDeviceRows" x-key="name" :value-keys="[hotMetric]" :value-labels="hotMetricLabels" height="318px" />
            <template #actions>
              <t-button v-if="canOpenRoute('AlipayAnalysisDevice', 'analysis')" variant="text" theme="primary" @click="openPage('/alipay/analysis/device')">设备分析</t-button>
            </template>
          </t-card>
        </t-col>
        <t-col :xs="12" :lg="5">
          <t-card :bordered="false" class="tail-card insight-card">
            <div class="tail-card__title">运营洞察</div>
            <div class="tail-card__subtitle">结合金额、设备、地域和时间分布生成的文本摘要</div>
            <div class="insight-list">
              <div v-for="(item, index) in displayInsightRows" :key="`${item}-${index}`" class="insight-item">
                <span>{{ index + 1 }}</span>
                <p>{{ item }}</p>
              </div>
            </div>
            <div v-if="visibleQuickLinks.length" class="quick-grid">
              <button v-for="item in visibleQuickLinks" :key="item.path" type="button" @click="openPage(item.path)">
                {{ item.title }}
              </button>
            </div>
          </t-card>
        </t-col>
      </t-row>

      <t-row v-if="isDashboardModuleVisible('activityRegion')" :gutter="[16, 16]" align="stretch" class="dashboard-module-block" :style="dashboardModuleStyle('activityRegion')">
        <t-col :xs="12" :lg="8">
          <t-card :bordered="false" class="tail-card">
            <div class="tail-card__title">近期订单</div>
            <div class="tail-card__subtitle">最近一次下单时间：{{ dashboardPayload?.activity?.latestOrderAt || '暂无最新订单' }}</div>
            <t-table
              row-key="__rowKey"
              size="small"
              :data="recentOrderRows"
              :columns="recentOrderColumns"
              :pagination="frontTablePagination(recentOrderRows)"
              cell-empty-content="-"
            >
              <template #deviceName="{ row }">
                <div class="order-device">{{ row.deviceName }}</div>
                <small>{{ row.orderNo }}</small>
              </template>
              <template #statusLabel="{ row }">
                <t-tag size="small" variant="light" :theme="statusTheme(row.status)">{{ row.statusLabel }}</t-tag>
              </template>
              <template #amount="{ row }">
                <span class="dashboard-highlight dashboard-highlight--money">{{ currency(row.amount) }}</span>
              </template>
            </t-table>
          </t-card>
        </t-col>
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card">
            <div class="tail-card__title">地域排行</div>
            <div class="tail-card__subtitle">覆盖 {{ compactNumber(analytics.coveredProvinceCount) }} 个省份，展示订单量最高区域</div>
            <div class="region-rank">
              <div v-for="item in regionRankRows" :key="item.name" class="region-rank__row">
                <div class="region-rank__top">
                  <strong>{{ item.name }}</strong>
                  <span>{{ compactNumber(item.orderCount) }} 单</span>
                </div>
                <div class="region-rank__track">
                  <div class="region-rank__bar" :style="{ width: `${item.percent}%` }" />
                </div>
              </div>
            </div>
          </t-card>
        </t-col>
      </t-row>
      </div>

    </template>

    <t-card v-else :bordered="false" class="analytics-empty">当前范围内暂无可分析订单</t-card>
    <financial-report-dialog
      v-model:visible="financialReportVisible"
      :report-data="financialReportData"
      :range-text="dataRangeText"
      @export="exportFinancialReport"
    />
    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="经营看板显示设置"
      :groups="displaySettingGroups"
      :model-value="{ modules: displaySettings.state.modules }"
      @save="saveDisplaySettings"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { rentApi, resolveData, type AnyRecord } from '@/api/rent';
import AnalysisPeriodFilter from '@/components/business/AnalysisPeriodFilter.vue';
import AnalyticsChart from '@/pages/alipay/components/AnalyticsChart.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import FinancialReportDialog from '@/pages/alipay/components/FinancialReportDialog.vue';
import { usePermissionStore } from '@/store';
import { downloadExcel } from '@/utils/csv-export';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';

type PeriodType = 'WEEK' | 'MONTH' | 'YEAR';

const DEFAULT_TABLE_PAGE_SIZE = 10;
const loading = ref(false);
const periodType = ref<PeriodType>('MONTH');
const targetDate = ref(formatDate(new Date()));
const dashboardPayload = ref<AnyRecord | null>(null);
const hotMetric = ref<'orderCount' | 'totalAmount' | 'totalRentDays'>('orderCount');
const trendMode = ref<'order' | 'revenue'>('order');
const financialReportVisible = ref(false);
const displaySettingsVisible = ref(false);
const hotMetricLabels = { orderCount: '订单数', totalAmount: '金额', totalRentDays: '天数' };
const router = useRouter();
const permissionStore = usePermissionStore();
const dashboardModuleDefinitions: DisplaySettingItem[] = [
  { key: 'metrics', label: '核心指标', group: '概览' },
  { key: 'trendTarget', label: '经营趋势 / 履约目标', group: '图表' },
  { key: 'distribution', label: '状态 / 租期 / 漏斗', group: '图表' },
  { key: 'deviceInsight', label: '热门设备 / 运营洞察', group: '分析' },
  { key: 'activityRegion', label: '近期订单 / 地域排行', group: '分析' },
];
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.dashboard',
  version: 1,
  definitions: { modules: dashboardModuleDefinitions },
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [{
  kind: 'modules',
  label: '页面模块',
  title: '经营看板模块',
  description: '控制经营看板主要区域的显示和排序。',
  items: dashboardModuleDefinitions,
}]);
const visibleDashboardModules = computed(() => displaySettings.visibleItems('modules', dashboardModuleDefinitions));
const analytics = computed(() => normalizeDashboard(dashboardPayload.value || {}));
const comparison = computed(() => dashboardPayload.value?.comparison || {});
const hasAnalyticsData = computed(() => safeNumber(analytics.value.totalOrders) > 0 || analytics.value.deviceRows.length > 0);
const dailyRows = computed(() => analytics.value.dailyRows);
const funnelRows = computed(() => {
  const serverRows = dashboardPayload.value?.funnel?.rows || [];
  if (serverRows.length) {
    return serverRows.map((item: AnyRecord) => ({
      ...item,
      count: safeNumber(item.count),
      rate: safeNumber(item.rate),
    }));
  }
  const statusRows = analytics.value.statusRows;
  if (statusRows.length) {
    const max = Math.max(...statusRows.map((item: AnyRecord) => safeNumber(item.count)), 1);
    return statusRows.slice(0, 4).map((item: AnyRecord) => ({
      label: item.label,
      count: safeNumber(item.count),
      rate: Math.max(6, Math.round((safeNumber(item.count) / max) * 100)),
    }));
  }
  if (analytics.value.totalOrders > 0) {
    const active = safeNumber(analytics.value.activeOrders);
    const finished = safeNumber(analytics.value.finishedOrders);
    return [
      { label: '订单总量', count: analytics.value.totalOrders, rate: 100 },
      { label: '履约中', count: active, rate: Math.max(6, Math.round((active / analytics.value.totalOrders) * 100)) },
      { label: '已完结', count: finished, rate: Math.max(6, Math.round((finished / analytics.value.totalOrders) * 100)) },
    ];
  }
  return [];
});
const sortedDeviceRows = computed(() => {
  const metric = hotMetric.value;
  return [...analytics.value.deviceRows]
    .sort((a, b) => safeNumber(b[metric]) - safeNumber(a[metric]))
    .slice(0, 8);
});
const regionRows = computed(() => analytics.value.regionRows.slice(0, 8));
const regionRankRows = computed(() => {
  const max = Math.max(...regionRows.value.map((item: AnyRecord) => safeNumber(item.orderCount)), 1);
  return regionRows.value.slice(0, 6).map((item: AnyRecord) => ({
    ...item,
    percent: Math.max(6, Math.round((safeNumber(item.orderCount) / max) * 100)),
  }));
});
const funnelCompletionRate = computed(() => dashboardPayload.value?.funnel?.completionRate || analytics.value.finishedRate || '0.0');
const insightRows = computed(() => (dashboardPayload.value?.insights || []).slice(0, 4));
const displayInsightRows = computed(() => {
  if (insightRows.value.length) return insightRows.value;
  const topDevice = analytics.value.topDevice?.name || sortedDeviceRows.value[0]?.name || '暂无热门设备';
  const topRegion = regionRankRows.value[0]?.name || '暂无地域数据';
  return [
    `筛选范围内累计 ${compactNumber(analytics.value.totalOrders)} 单，租赁收入 ${currency(analytics.value.totalAmount)}。`,
    `当前热门设备为 ${topDevice}，可继续下钻设备分析查看贡献结构。`,
    `订单覆盖重点区域为 ${topRegion}，建议结合地域分析观察投放效率。`,
    `履约完结率 ${funnelCompletionRate.value}%，履约中订单 ${compactNumber(analytics.value.activeOrders)} 单。`,
  ];
});
const recentOrderRows = computed(() => withRowKey(dashboardPayload.value?.activity?.recentOrders || []));
const trendValueKeys = computed(() => (trendMode.value === 'order' ? ['orderCount', 'orderAmount'] : ['orderAmount', 'runningAmount']));
const statusChartRows = computed(() => {
  const rows = analytics.value.statusRows.length
    ? analytics.value.statusRows
    : [{ label: '暂无状态', count: analytics.value.totalOrders }];
  return rows.slice(0, 8);
});
const durationChartRows = computed(() => {
  const rows = analytics.value.durationRows.length
    ? analytics.value.durationRows
    : [
        { label: '平均租期', count: Math.round(analytics.value.averageRentDays || 0) },
        { label: '累计租赁天数', count: analytics.value.totalRentDays },
      ];
  return rows.slice(0, 8);
});
const targetProgress = computed(() => {
  const percent = safeNumber(funnelCompletionRate.value);
  return Math.max(0, Math.min(100, Math.round(percent)));
});
const targetMetrics = computed(() => [
  { label: '订单金额', value: currency(analytics.value.totalAmount) },
  { label: '累计租期', value: `${compactNumber(analytics.value.totalRentDays)} 天` },
  { label: '覆盖用户', value: compactNumber(analytics.value.uniqueUsers) },
]);
const dashboardSummaryText = computed(() => {
  const topDevice = analytics.value.topDevice?.name || sortedDeviceRows.value[0]?.name;
  if (!hasAnalyticsData.value) return '选择时间范围后加载支付宝租赁订单、收入、履约和地域数据。';
  return `${dataRangeText.value}，累计 ${compactNumber(analytics.value.totalOrders)} 单，收入 ${currency(analytics.value.totalAmount)}${topDevice ? `，热门设备 ${topDevice}` : ''}。`;
});
const quickLinks = [
  { title: '用户画像', path: '/alipay/analysis/user-portrait', pageCode: 'analysis', routeName: 'AlipayAnalysisUserPortrait' },
  { title: '地域分析', path: '/alipay/analysis/region', pageCode: 'analysis', routeName: 'AlipayAnalysisRegion' },
  { title: '订单分析', path: '/alipay/analysis/order', pageCode: 'analysis', routeName: 'AlipayAnalysisOrder' },
  { title: '设备分析', path: '/alipay/analysis/device', pageCode: 'analysis', routeName: 'AlipayAnalysisDevice' },
  { title: '营收分析', path: '/alipay/analysis/revenue', pageCode: 'analysis', routeName: 'AlipayAnalysisRevenue' },
  { title: '租期履约', path: '/alipay/analysis/rental', pageCode: 'analysis', routeName: 'AlipayAnalysisRental' },
];
const visibleQuickLinks = computed(() => quickLinks.filter((item) => canOpenRoute(item.routeName, item.pageCode)));
const financialReportData = computed(() => {
  const serverReport = dashboardPayload.value?.financialReport || {};
  const summary = serverReport.summary || {};
  const serverTrendRows = Array.isArray(serverReport.trendRows) ? serverReport.trendRows : [];
  const fallbackTrendRows = analytics.value.dailyRows.map((item: AnyRecord) => ({
    date: item.date,
    amountYuan: item.orderAmount,
    cumulativeAmountYuan: item.runningAmount,
  }));
  const serverStatusRows = Array.isArray(serverReport.statusRows) ? serverReport.statusRows : [];
  const serverDeviceRows = Array.isArray(serverReport.deviceRows) ? serverReport.deviceRows : [];
  const serverRegionRows = Array.isArray(serverReport.regionRows) ? serverReport.regionRows : [];
  return {
    summary: {
      totalRevenueYuan: moneyFromReport(summary.totalRevenueYuan, summary.totalRevenueCents, analytics.value.totalAmount),
      totalPaidYuan: moneyFromReport(summary.totalPaidYuan, summary.totalPaidCents, analytics.value.totalPaid),
      totalDepositYuan: moneyFromReport(summary.totalDepositYuan, summary.totalDepositCents, 0),
      avgOrderValueYuan: moneyFromReport(
        summary.avgOrderValueYuan,
        summary.avgOrderValueCents,
        analytics.value.totalOrders ? analytics.value.totalAmount / analytics.value.totalOrders : 0,
      ),
      activeRentals: summary.activeRentals ?? analytics.value.activeOrders,
      totalRentDays: summary.totalRentDays ?? analytics.value.totalRentDays,
    },
    trendRows: serverTrendRows.length
      ? serverTrendRows.map((item: AnyRecord) => ({
        ...item,
        amountYuan: moneyFromReport(item.amountYuan, item.amountCents, item.orderAmount ?? item.totalAmount),
        cumulativeAmountYuan: moneyFromReport(item.cumulativeAmountYuan, item.cumulativeAmountCents, item.runningAmount),
      }))
      : fallbackTrendRows,
    statusRows: serverStatusRows.length
      ? serverStatusRows.map((item: AnyRecord) => ({
        ...item,
        statusLabel: item.statusLabel || item.label || item.status || '未知状态',
        orderCount: safeNumber(item.orderCount ?? item.count),
      }))
      : analytics.value.statusRows.map((item: AnyRecord) => ({
        statusLabel: item.statusLabel || item.label,
        orderCount: item.count,
      })),
    deviceRows: serverDeviceRows.length
      ? serverDeviceRows.map((item: AnyRecord) => ({
        ...item,
        amountYuan: moneyFromReport(item.amountYuan, item.amountCents, item.totalAmount),
      }))
      : analytics.value.deviceRows.map((item: AnyRecord) => ({
        name: item.name,
        orderCount: item.orderCount,
        amountYuan: item.totalAmount,
      })),
    regionRows: serverRegionRows.length
      ? serverRegionRows.map((item: AnyRecord) => ({
        ...item,
        amountYuan: moneyFromReport(item.amountYuan, item.amountCents, item.amount ?? item.totalAmount),
      }))
      : analytics.value.regionRows.map((item: AnyRecord) => ({
        name: item.name,
        orderCount: item.orderCount,
        amountYuan: item.totalAmount,
      })),
  };
});
const financialExportRows = computed(() => {
  const rows: AnyRecord[] = [];
  const summary = financialReportData.value.summary as AnyRecord;
  Object.keys(summary || {}).forEach((key) => rows.push({ section: '摘要', name: key, value: summary[key] }));
  financialReportData.value.trendRows.forEach((item: AnyRecord) => rows.push({ section: '资金趋势', ...item }));
  financialReportData.value.deviceRows.forEach((item: AnyRecord) => rows.push({ section: '设备排行', ...item }));
  financialReportData.value.regionRows.forEach((item: AnyRecord) => rows.push({ section: '地域分布', ...item }));
  financialReportData.value.statusRows.forEach((item: AnyRecord) => rows.push({ section: '状态分布', ...item }));
  return rows;
});
const dataRangeText = computed(() => {
  const meta = (dashboardPayload.value || {}).meta || {};
  const startDate = meta.dataStartDate || meta.startDate;
  const endDate = meta.dataEndDate || meta.endDate;
  if (startDate && endDate) {
    return `${startDate} 至 ${endDate}`;
  }
  const selectedRange = resolvePeriodRange(periodType.value, targetDate.value);
  return selectedRange.length === 2 ? `${selectedRange[0]} 至 ${selectedRange[1]}` : '自定义周期';
});

function frontTablePagination(rows: unknown) {
  const total = Array.isArray(rows) ? rows.length : 0;
  return {
    defaultCurrent: 1,
    defaultPageSize: DEFAULT_TABLE_PAGE_SIZE,
    pageSizeOptions: [10, 20, 50, 100],
    total,
  };
}

const statCards = computed(() => [
  {
    label: '订单量',
    icon: 'root-list',
    tone: 'primary',
    value: compactNumber(analytics.value.totalOrders),
    desc: '筛选范围订单',
    trend: trendPairText(analytics.value.ordersMoM, analytics.value.ordersYoY),
  },
  {
    label: '租赁收入',
    icon: 'money',
    tone: 'success',
    value: currency(analytics.value.totalAmount),
    desc: '订单总金额',
    trend: trendPairText(analytics.value.revenueMoM, analytics.value.revenueYoY),
  },
  {
    label: '履约中',
    icon: 'time',
    tone: 'warning',
    value: compactNumber(analytics.value.activeOrders),
    desc: `完结率 ${analytics.value.finishedRate || '0.0'}%`,
    trend: trendPairText(analytics.value.activeOrdersMoM, analytics.value.activeOrdersYoY),
  },
  {
    label: '覆盖用户',
    icon: 'user',
    tone: 'purple',
    value: compactNumber(analytics.value.uniqueUsers),
    desc: `设备 ${compactNumber(analytics.value.uniqueDeviceCount)} 款`,
    trend: trendPairText(analytics.value.usersMoM, analytics.value.usersYoY),
  },
]);

const recentOrderColumns = [
  { title: '订单', colKey: 'deviceName', minWidth: 180, ellipsis: true },
  { title: '状态', colKey: 'statusLabel', width: 96, align: 'center' },
  { title: '订单金额', colKey: 'amount', width: 100, align: 'center' },
  { title: '时间', colKey: 'createdAt', width: 190 },
];

function isDashboardModuleVisible(key: string) {
  return visibleDashboardModules.value.some((item) => item.key === key);
}

function dashboardModuleStyle(key: string) {
  const index = visibleDashboardModules.value.findIndex((item) => item.key === key);
  return { order: index < 0 ? 999 : index };
}

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  displaySettingsVisible.value = false;
}

function buildDashboardQuery(forceRefresh = false) {
  const query: AnyRecord = {
    periodType: periodType.value,
    targetDate: targetDate.value,
    timezone: 'Asia/Shanghai',
    topN: 10,
  };
  if (forceRefresh) query.forceRefresh = true;
  return query;
}

function handlePeriodTypeChange(value: string | number | boolean) {
  periodType.value = String(value) as PeriodType;
  if (!targetDate.value) targetDate.value = formatDate(new Date());
  loadAnalyticsData();
}

function handleTargetDateChange(value: unknown) {
  targetDate.value = String(value || formatDate(new Date()));
  if (targetDate.value) loadAnalyticsData();
}

function resolvePeriodRange(type: PeriodType, dateText: string) {
  const date = parseDateText(dateText);
  if (!date) return [];
  if (type === 'WEEK') {
    const day = date.getDay() || 7;
    const start = new Date(date);
    start.setDate(date.getDate() - day + 1);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return [formatDate(start), formatDate(end)];
  }
  if (type === 'YEAR') {
    return [formatDate(new Date(date.getFullYear(), 0, 1)), formatDate(new Date(date.getFullYear(), 11, 31))];
  }
  return [formatDate(new Date(date.getFullYear(), date.getMonth(), 1)), formatDate(new Date(date.getFullYear(), date.getMonth() + 1, 0))];
}

function parseDateText(value: string) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value: Date) {
  const year = value.getFullYear();
  const month = `${value.getMonth() + 1}`.padStart(2, '0');
  const day = `${value.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

async function loadAnalyticsData(forceRefresh = false) {
  loading.value = true;
  try {
    const response = await rentApi.fetchAnalyticsDashboard(buildDashboardQuery(forceRefresh));
    dashboardPayload.value = resolveData<AnyRecord>(response, {});
  } finally {
    loading.value = false;
  }
}

function exportFinancialReport() {
  if (!financialExportRows.value.length) return;
  const meta = dashboardPayload.value?.meta || {};
  const selectedRange = resolvePeriodRange(periodType.value, targetDate.value);
  const start = meta.queryStartDate || meta.startDate || selectedRange[0] || '全部';
  const end = meta.queryEndDate || meta.endDate || selectedRange[1] || '数据';
  downloadExcel(`支付宝租赁财务报表-${start}-${end}`, financialExportRows.value);
}

function openPage(path: string) {
  router.push(path);
}

function canOpenRoute(routeName: string, pageCode: string) {
  return permissionStore.hasPage(pageCode) && router.hasRoute(routeName);
}

function normalizeDashboard(payload: AnyRecord) {
  const overview = payload.overview || payload.summary || {};
  const comparison = payload.comparison || {};
  const trend = payload.trend || {};
  const rankings = payload.rankings || {};
  const distributions = payload.distributions || {};
  const geoDistribution = payload.geoDistribution || {};
  const daily = (trend.points || trend.orders || []).map((item: AnyRecord) => ({
    date: item.date,
    orderCount: safeNumber(item.orderCount ?? item.orders),
    orderAmount: centsToYuan(item.revenueCents, item.orderAmount ?? item.totalAmount),
    totalRentDays: safeNumber(item.totalRentDays),
    averageRentDays: safeNumber(item.averageRentDays),
  }));
  let runningAmount = 0;
  daily.forEach((item: AnyRecord) => {
    runningAmount += safeNumber(item.orderAmount);
    item.runningAmount = runningAmount;
  });
  const deviceRows = (rankings.devices || rankings.byOrders || []).map((item: AnyRecord) => {
    const name = item.name || item.goodTitle || '未命名设备';
    return {
      name,
      shortName: shortLabel(name),
      orderCount: safeNumber(item.orderCount ?? item.orders),
      totalAmount: centsToYuan(item.revenueCents, item.totalAmount),
      totalRentDays: safeNumber(item.totalRentDays),
      averageRentDays: safeNumber(item.averageRentDays),
      orderShare: item.orderShare || item.share || '0.0',
    };
  });
  return {
    totalOrders: safeNumber(overview.totalOrders),
    totalAmount: centsToYuan(overview.totalRevenueCents, overview.totalAmount),
    totalPaid: centsToYuan(overview.totalPaidCents, overview.totalPaid ?? overview.paidAmount),
    totalRentDays: safeNumber(overview.totalRentDays),
    activeOrders: safeNumber(overview.activeOrders || overview.activeRentals),
    finishedOrders: safeNumber(overview.finishedOrders),
    finishedRate: overview.finishedRate || '0.0',
    averageRentDays: safeNumber(overview.averageRentDays),
    uniqueUsers: safeNumber(overview.uniqueUsers),
    uniqueDeviceCount: safeNumber(overview.uniqueDeviceCount || deviceRows.length),
    ordersChange: safeNumber(comparison.ordersChange),
    revenueChange: safeNumber(comparison.revenueChange),
    usersChange: safeNumber(comparison.usersChange),
    ordersMoM: safeNumber(comparison.totalOrdersMoM ?? comparison.ordersChange),
    ordersYoY: safeNumber(comparison.totalOrdersYoY),
    revenueMoM: safeNumber(comparison.totalRevenueCentsMoM ?? comparison.revenueChange),
    revenueYoY: safeNumber(comparison.totalRevenueCentsYoY),
    activeOrdersMoM: safeNumber(comparison.activeOrdersMoM ?? comparison.activeOrdersChange),
    activeOrdersYoY: safeNumber(comparison.activeOrdersYoY),
    usersMoM: safeNumber(comparison.uniqueUsersMoM ?? comparison.usersChange),
    usersYoY: safeNumber(comparison.uniqueUsersYoY),
    dailyRows: daily,
    deviceRows,
    statusRows: mapCountRows(distributions.status || distributions.orderStatus, 'status'),
    hourRows: mapCountRows(distributions.hourly || distributions.hours, 'timeSlot'),
    weekdayRows: mapCountRows(distributions.weekday || distributions.weekdays, 'label'),
    durationRows: mapCountRows(distributions.rentalDuration || distributions.durationRows, 'label'),
    regionRows: (geoDistribution.provinces || geoDistribution.regions || []).map((item: AnyRecord) => ({
      name: item.name || item.province || '-',
      orderCount: safeNumber(item.orderCount ?? item.count),
      totalAmount: centsToYuan(item.revenueCents, item.totalAmount),
    })),
    coveredProvinceCount: safeNumber(overview.coveredProvinceCount || (geoDistribution.provinces || []).length),
    topDevice: overview.topDevice ? {
      name: overview.topDevice.name || '未命名设备',
      shortName: shortLabel(overview.topDevice.name || '未命名设备'),
      orderCount: safeNumber(overview.topDevice.orderCount),
      orderShare: overview.topDevice.orderShare || '0.0',
    } : null,
    peakHourLabel: overview.peakHour?.label || '-',
    peakHourRate: overview.peakHour?.rate || '0.0',
    peakWeekdayLabel: overview.peakWeekday?.label || '-',
  };
}

function mapCountRows(rows: AnyRecord[] | AnyRecord = [], labelKey: string) {
  if (!Array.isArray(rows)) {
    return Object.keys(rows || {}).map((key) => ({
      label: key,
      count: safeNumber((rows as AnyRecord)[key]),
    }));
  }
  return rows.map((item) => ({
    label: item.label || item[labelKey] || item.status || '-',
    count: safeNumber(item.orderCount ?? item.count),
  }));
}

function withRowKey(rows: AnyRecord[]) {
  return rows.map((row, index) => ({ __rowKey: `${row.orderId || row.orderNo || row.deviceName || index}-${index}`, ...row }));
}

function statusTheme(status: string) {
  if (['FINISHED', 'RETURN_RECEIVED'].includes(status)) return 'success';
  if (['CLOSED', 'CANCELLED', 'REFUNDED'].includes(status)) return 'danger';
  if (['DELIVERED', 'RECEIVED', 'RETURN_DELIVERED'].includes(status)) return 'warning';
  return 'primary';
}

function safeNumber(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function centsToYuan(cents: unknown, yuanFallback: unknown) {
  return cents !== undefined && cents !== null ? safeNumber(cents) / 100 : safeNumber(yuanFallback);
}

function moneyFromReport(yuanValue: unknown, centsValue: unknown, fallback: unknown = 0) {
  if (yuanValue !== undefined && yuanValue !== null) return safeNumber(yuanValue);
  if (centsValue !== undefined && centsValue !== null) return safeNumber(centsValue) / 100;
  return safeNumber(fallback);
}

function currency(value: unknown) {
  return `￥${safeNumber(value).toFixed(2)}`;
}

function compactNumber(value: unknown) {
  const number = safeNumber(value);
  if (number >= 100000000) return `${(number / 100000000).toFixed(1)}亿`;
  if (number >= 10000) return `${(number / 10000).toFixed(1)}万`;
  return String(number);
}

function trendText(value: unknown) {
  const number = safeNumber(value);
  if (number === 0) return '持平';
  return `${number > 0 ? '上升' : '下降'} ${Math.abs(number).toFixed(1)}%`;
}

function trendPairText(mom: unknown, yoy: unknown) {
  return `环比${trendText(mom)} · 同比${trendText(yoy)}`;
}

function trendClass(value: string) {
  if (value.includes('上升')) return 'trend-text--up';
  if (value.includes('下降')) return 'trend-text--down';
  if (value.includes('持平')) return 'trend-text--flat';
  return 'trend-text--info';
}

function shortLabel(value: string, max = 10) {
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

onMounted(loadAnalyticsData);
</script>

<style scoped>
.analytics-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 88px);
  background: #f0f2f5;
}

.analytics-page :deep(.t-card) {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}

.analytics-page :deep(.t-card__header) {
  padding: 20px 24px 8px;
  border-bottom: 0;
}

.analytics-page :deep(.t-card__body) {
  padding: 20px;
}

.analytics-page :deep(.t-button) {
  height: 38px;
  border-radius: 8px;
}

.analytics-page :deep(.t-input),
.analytics-page :deep(.t-range-input),
.analytics-page :deep(.t-date-range-picker) {
  min-height: 38px;
  border-radius: 8px;
}

.analytics-page :deep(.t-range-input) {
  background: #fff;
  box-shadow: none !important;
}

.analytics-page :deep(.t-range-input) {
  overflow: hidden;
  padding: 0 14px;
}

.analytics-page :deep(.t-range-input__inner),
.analytics-page :deep(.t-range-input__inner-left),
.analytics-page :deep(.t-range-input__inner-right),
.analytics-page :deep(.t-range-input .t-input),
.analytics-page :deep(.t-range-input .t-input__wrap),
.analytics-page :deep(.t-range-input .t-input__inner),
.analytics-page :deep(.t-range-input input),
.analytics-page :deep(.t-date-range-picker input) {
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.analytics-page :deep(.t-range-input),
.analytics-page :deep(.t-radio-group) {
  border: 1px solid #d0d5dd;
}

.analytics-page :deep(.t-radio-group) {
  min-height: 38px;
  padding: 2px;
  background: #f9fafb;
  border-radius: 8px;
  box-shadow: none;
}

.analytics-page :deep(.t-radio-group__bg-block) {
  border-radius: 6px;
  background: #465fff;
  box-shadow: none;
}

.analytics-page :deep(.t-radio-button),
.analytics-page :deep(.t-radio-button__label) {
  border-radius: 6px;
}

.analytics-page :deep(.t-radio-button) {
  background: transparent !important;
  box-shadow: none !important;
}

.analytics-page :deep(.t-radio-button.t-is-checked),
.analytics-page :deep(.t-radio-button__label.t-is-checked) {
  color: #fff;
  background: transparent !important;
  box-shadow: none;
}

.analytics-page :deep(.t-table) {
  overflow: hidden;
  border: 0;
  border-radius: 6px;
}

.analytics-page :deep(.t-table th) {
  color: #475467;
  font-weight: 600;
  background: #fff;
}

.analytics-page :deep(.t-table th),
.analytics-page :deep(.t-table td) {
  border-color: #f2f4f7;
}

.tail-card,
.metric-card,
.analytics-empty {
  height: 100%;
  border-radius: 6px;
}

.dashboard-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}

.dashboard-header__copy {
  min-width: 240px;
}

.dashboard-kicker {
  color: #465fff;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-header h1 {
  margin: 6px 0 0;
  color: #101828;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.dashboard-header p {
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
  flex: 0 0 220px;
  width: 220px;
  max-width: 100%;
}

.range-picker {
  flex: 0 0 424px;
  width: 424px;
  max-width: 100%;
}

.range-picker :deep(.t-range-input) {
  width: 100%;
  min-width: 0;
  padding: 0 12px;
}

.range-picker :deep(.t-range-input__inner) {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  min-width: 0;
}

.range-picker :deep(.t-range-input__inner-left),
.range-picker :deep(.t-range-input__inner-right) {
  flex: 1 1 0;
  min-width: 142px;
}

.range-picker :deep(.t-range-input__separator) {
  flex: 0 0 24px;
  margin: 0 8px;
  text-align: center;
}

.range-picker :deep(input) {
  min-width: 0;
  text-align: center;
  white-space: nowrap;
}

.tail-card__title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.tail-card__subtitle {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
}

.metric-card__footer,
.metric-card__head,
.funnel-row__meta,
.chart-heading,
.region-rank__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-card {
  min-height: 142px;
}

.metric-card--primary {
  box-shadow: none;
}

.metric-card--success {
  box-shadow: none;
}

.metric-card--warning {
  box-shadow: none;
}

.metric-card--purple {
  box-shadow: none;
}

.metric-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: var(--td-brand-color);
  background: #eef4ff;
  border-radius: 8px;
}

.metric-card__icon--success {
  color: #079455;
  background: #ecfdf3;
}

.metric-card__icon--warning {
  color: #dc6803;
  background: #fffaeb;
}

.metric-card__icon--purple {
  color: #7f56d9;
  background: #f4ebff;
}

.metric-card__label,
.metric-card__footer {
  color: #667085;
  font-size: 13px;
}

.metric-card__value {
  margin-top: 14px;
  color: #101828;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.metric-card__footer {
  align-items: flex-end;
  margin-top: 12px;
}

.trend-text {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 4px 9px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.trend-text--up {
  color: var(--td-success-color);
  background: var(--td-success-color-1);
}

.trend-text--down {
  color: var(--td-error-color);
  background: var(--td-error-color-1);
}

.trend-text--flat {
  color: var(--td-text-color-secondary);
  background: var(--td-bg-color-secondarycontainer);
}

.trend-text--info {
  color: var(--td-brand-color);
  background: var(--td-brand-color-light);
}

.chart-card :deep(.analytics-chart) {
  min-height: 360px;
}

.target-card {
  min-height: 100%;
}

.target-ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 188px;
  height: 188px;
  margin: 22px auto 18px;
  background:
    radial-gradient(circle at center, #fff 0 56%, transparent 57%),
    conic-gradient(#12b76a var(--target-progress), #eef2f6 0);
  border-radius: 50%;
}

.target-ring > div {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 126px;
  height: 126px;
  background: #fff;
  border: 1px solid #eaecf0;
  border-radius: 50%;
}

.target-ring strong {
  color: #101828;
  font-size: 32px;
  line-height: 1.1;
}

.target-ring span,
.target-copy,
.target-metrics span {
  color: #667085;
  font-size: 13px;
}

.target-copy {
  margin: 0;
  line-height: 1.7;
  text-align: center;
}

.target-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #f2f4f7;
}

.target-metrics div {
  min-width: 0;
}

.target-metrics strong {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #101828;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.funnel-list,
.insight-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.funnel-row__meta {
  color: #344054;
  font-size: 13px;
}

.funnel-row__meta strong {
  color: #101828;
}

.funnel-row__track {
  height: 10px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 8px;
}

.funnel-row__bar {
  height: 100%;
  background: #465fff;
  border-radius: inherit;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.quick-grid button {
  min-width: 0;
  padding: 11px 10px;
  overflow: hidden;
  color: var(--td-text-color-primary);
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: #fafafa;
  border: 1px solid #eaecf0;
  border-radius: 8px;
}

.order-device {
  max-width: 220px;
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-device + small {
  color: #98a2b3;
}

.dashboard-highlight {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 3px 9px;
  color: #344054;
  font-weight: 700;
  background: #f2f4f7;
  border-radius: 6px;
}

.dashboard-highlight--money {
  color: #2f54eb;
  background: #eef4ff;
}

.insight-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  align-items: start;
}

.insight-item span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--td-brand-color);
  font-size: 12px;
  font-weight: 700;
  background: #eef4ff;
  border-radius: 8px;
}

.insight-item p {
  margin: 2px 0 0;
  color: #344054;
  line-height: 1.6;
}

.region-rank {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 22px;
}

.region-rank__top {
  margin-bottom: 8px;
  color: #667085;
}

.region-rank__top strong {
  color: #111827;
}

.region-rank__track {
  height: 10px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 8px;
}

.region-rank__bar {
  height: 100%;
  background: #465fff;
  border-radius: inherit;
}

.analytics-empty {
  text-align: center;
  color: var(--td-text-color-secondary);
}

@media (max-width: 960px) {
  .dashboard-header {
    flex-direction: column;
  }

  .toolbar {
    justify-content: flex-start;
    width: 100%;
  }
}

@media (max-width: 640px) {
  .analytics-page {
    padding: 12px;
  }

  .dashboard-header {
    padding: 16px;
  }

  .dashboard-header h1 {
    font-size: 21px;
  }

  .target-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
