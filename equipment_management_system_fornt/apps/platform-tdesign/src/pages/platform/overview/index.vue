<template>
  <div class="platform-page platform-home tail-dashboard-page">
    <section class="dashboard-header">
      <div class="dashboard-header__copy">
        <span class="dashboard-kicker">Platform Overview</span>
        <h1>中台首页</h1>
        <p class="page-description">汇总授权业务系统的关键指标、趋势图和快捷入口，统一观察当前经营状态。</p>
      </div>
      <div class="toolbar">
        <analysis-period-filter
          v-model:period-type="periodType"
          v-model:target-date="targetDate"
          :loading="loading"
          @period-change="handlePeriodTypeChange"
          @date-change="handleTargetDateChange"
          @refresh="refreshDashboards"
        />
      </div>
    </section>

    <t-row :gutter="[16, 16]" align="stretch">
      <t-col v-for="section in systemSections" :key="section.code" :xs="12" :lg="4">
        <button
          type="button"
          class="system-select-card tail-card"
          :class="{ 'is-active-system': section.code === activeCode }"
          @click="selectSystem(section.code)"
        >
          <span class="system-select-card__head">
            <span>
              <strong>{{ section.name }}</strong>
              <small>{{ section.code }}</small>
            </span>
            <t-tag variant="light" theme="primary">{{ section.code === activeCode ? '当前' : '切换' }}</t-tag>
          </span>
          <span class="system-select-card__metrics">
            <span v-for="card in section.cards" :key="card.label">
              <em>{{ card.label }}</em>
              <strong>{{ card.value }}</strong>
            </span>
          </span>
        </button>
      </t-col>
    </t-row>

    <section v-if="activeSection" class="system-section tail-card">
      <div class="section-head">
        <div class="section-title">
          <span class="section-mark" />
          <h3>{{ activeSection.name }}</h3>
        </div>
        <t-button variant="text" theme="primary" @click="openSystem(activeSection)">进入系统</t-button>
      </div>

      <t-row :gutter="[16, 16]">
        <t-col v-for="card in activeSection.cards" :key="card.label" :xs="12" :sm="6" :xl="3">
          <t-card :bordered="false" class="tail-card metric-card">
            <div class="metric-card__head">
              <span class="metric-card__icon" :class="`metric-card__icon--${card.tone}`">
                <t-icon :name="card.icon" />
              </span>
              <t-tag size="small" variant="light" :theme="card.theme">{{ card.badge }}</t-tag>
            </div>
            <div class="metric-card__label">{{ card.label }}</div>
            <div class="metric-card__value">{{ card.value }}</div>
            <div class="metric-card__footer">{{ card.desc }}</div>
          </t-card>
        </t-col>
      </t-row>

      <t-row :gutter="[16, 16]" align="stretch">
        <t-col :xs="12" :xl="8">
          <t-card :bordered="false" class="tail-card chart-card">
            <div class="card-title">
              <span>{{ activeSection.trendTitle }}</span>
              <t-tag variant="light" theme="primary">{{ rangeText }}</t-tag>
            </div>
            <div :ref="(el) => setChartRef(`${activeSection.code}-trend`, el)" class="trend-chart" />
          </t-card>
        </t-col>

        <t-col :xs="12" :xl="4">
          <t-card :bordered="false" class="tail-card target-card">
            <div class="card-title">
              <span>{{ activeSection.target.title }}</span>
              <t-icon name="chart-pie" />
            </div>
            <div class="target-ring" :style="{ '--target-progress': `${activeSection.target.percent}%` }">
              <div>
                <strong>{{ activeSection.target.display }}</strong>
                <span>{{ activeSection.target.label }}</span>
              </div>
            </div>
            <div class="target-metrics">
              <div v-for="item in activeSection.target.metrics" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </t-card>
        </t-col>
      </t-row>

      <t-row :gutter="[16, 16]" align="stretch">
        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card mini-chart-card">
            <div class="card-title">
              <span>{{ activeSection.pieTitle }}</span>
            </div>
            <div :ref="(el) => setChartRef(`${activeSection.code}-pie`, el)" class="small-chart" />
          </t-card>
        </t-col>

        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card mini-chart-card">
            <div class="card-title">
              <span>{{ activeSection.barTitle }}</span>
            </div>
            <div :ref="(el) => setChartRef(`${activeSection.code}-bar`, el)" class="small-chart" />
          </t-card>
        </t-col>

        <t-col :xs="12" :lg="4">
          <t-card :bordered="false" class="tail-card mini-chart-card">
            <div class="card-title">
              <span>{{ activeSection.tableTitle }}</span>
            </div>
            <div class="rank-list">
              <div v-for="(item, index) in activeSection.rankRows" :key="`${item.name}-${index}`">
                <span class="rank-badge">{{ index + 1 }}</span>
                <span class="rank-name">{{ item.name }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <t-empty v-if="!activeSection.rankRows.length" size="small" description="暂无数据" />
            </div>
          </t-card>
        </t-col>
      </t-row>
    </section>

    <t-empty v-if="!systemSections.length" description="暂无可展示系统" />
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComponentPublicInstance } from 'vue';

import { fetchSystemDashboard, listSystems } from '@/api/platform';
import AnalysisPeriodFilter from '@/components/business/AnalysisPeriodFilter.vue';
import { resolveData, type AnyRecord } from '@/pages/platform/utils';
import { appendSsoPayload, buildStoredSsoPayload } from '@/utils/sso';

import '../index.less';

defineOptions({
  name: 'PlatformOverview',
});

type MetricTone = 'primary' | 'success' | 'warning' | 'danger' | 'purple';

interface MetricCard {
  label: string;
  value: string;
  desc: string;
  icon: string;
  tone: MetricTone;
  theme: 'primary' | 'success' | 'warning' | 'danger';
  badge: string;
}

interface ChartRow {
  name: string;
  value: number;
  value2?: number;
}

interface RankRow {
  name: string;
  value: string;
  rawValue: number;
}

interface SystemSection extends AnyRecord {
  code: string;
  name: string;
  path?: string;
  tone: MetricTone;
  cards: MetricCard[];
  trendTitle: string;
  trendRows: ChartRow[];
  trendSeriesNames: string[];
  target: {
    title: string;
    label: string;
    display: string;
    percent: number;
    metrics: Array<{ label: string; value: string }>;
  };
  pieTitle: string;
  pieRows: ChartRow[];
  barTitle: string;
  barRows: ChartRow[];
  tableTitle: string;
  rankRows: RankRow[];
}

const chartColors = ['#465fff', '#2e90fa', '#7f56d9', '#f79009', '#667085', '#98a2b3'];

const activeCharts: Record<string, echarts.ECharts> = {};
const chartRefs: Record<string, HTMLElement | null> = {};

const systems = ref<AnyRecord[]>([]);
const dashboards = ref<Record<string, AnyRecord>>({});
const loadingMap = ref<Record<string, boolean>>({});
const errorMap = ref<Record<string, string>>({});
const loading = ref(false);
const activeCode = ref('');
type PeriodType = 'WEEK' | 'MONTH' | 'YEAR';
const periodType = ref<PeriodType>('MONTH');

const now = new Date();
const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0);

const localDevPorts: Record<string, string> = {
  rental: '8081',
  alipay: '8083',
};

const formatYmd = (date: Date) => {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const targetDate = ref(formatYmd(new Date()));
const selectedPeriodRange = computed(() => resolvePeriodRange(periodType.value, targetDate.value));
const rangeText = computed(() => {
  const range = selectedPeriodRange.value;
  return range.length === 2 ? `${range[0]} 至 ${range[1]}` : `${formatYmd(monthStart)} 至 ${formatYmd(monthEnd)}`;
});

const parseDateText = (value: string) => {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const resolvePeriodRange = (type: PeriodType, value: string) => {
  const date = parseDateText(value) || new Date();
  if (type === 'WEEK') {
    const start = new Date(date);
    const day = start.getDay() || 7;
    start.setDate(start.getDate() - day + 1);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return [formatYmd(start), formatYmd(end)];
  }
  if (type === 'YEAR') {
    return [formatYmd(new Date(date.getFullYear(), 0, 1)), formatYmd(new Date(date.getFullYear(), 11, 31))];
  }
  return [formatYmd(new Date(date.getFullYear(), date.getMonth(), 1)), formatYmd(new Date(date.getFullYear(), date.getMonth() + 1, 0))];
};

const toNumber = (value: unknown) => {
  if (typeof value === 'string') {
    const normalized = value.replace(/,/g, '').replace(/[￥¥%]/g, '');
    const number = Number(normalized);
    return Number.isFinite(number) ? number : 0;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

const safeArray = <T = AnyRecord>(value: unknown): T[] => (Array.isArray(value) ? value : []);
const currencyValue = (value: unknown, centsFallback?: unknown) => {
  const hasValue = value !== undefined && value !== null;
  const hasCents = centsFallback !== undefined && centsFallback !== null;
  if (!hasCents) return hasValue ? toNumber(value) : 0;
  const centsValue = toNumber(centsFallback);
  if (!hasValue) return centsValue / 100;
  const numberValue = toNumber(value);
  return numberValue === centsValue ? centsValue / 100 : numberValue;
};
const formatCount = (value: unknown) => toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 1 });
const formatMoney = (value: unknown) =>
  `¥${toNumber(value).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
const formatPercent = (value: unknown) => `${toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 1 })}%`;
const clampPercent = (value: unknown) => Math.max(0, Math.min(100, toNumber(value)));

const labelOf = (row: AnyRecord) =>
  String(row.name || row.label || row.dimensionName || row.channelName || row.categoryName || row.bandName || row.statusLabel || row.status || '-');

const valueOf = (row: AnyRecord) =>
  toNumber(
    row.value ??
      row.count ??
      row.orderCount ??
      row.totalAmount ??
      row.amount ??
      row.salesAmount ??
      row.profitAmount ??
      row.stockAmount ??
      row.totalRentDays,
  );

const card = (
  label: string,
  value: string,
  desc: string,
  icon: string,
  tone: MetricTone,
  theme: 'primary' | 'success' | 'warning' | 'danger',
  badge: string,
): MetricCard => ({ label, value, desc, icon, tone, theme, badge });

const topChartRows = (rows: AnyRecord[], valueKey?: string, labelKey?: string): ChartRow[] =>
  rows
    .map((row) => ({
      name: String((labelKey && row[labelKey]) || labelOf(row)),
      value: toNumber((valueKey && row[valueKey]) ?? valueOf(row)),
    }))
    .filter((row) => row.name && row.name !== '-' && row.value > 0)
    .sort((left, right) => right.value - left.value)
    .slice(0, 8);

const topRankRows = (rows: AnyRecord[], formatter = formatCount, valueKey?: string, labelKey?: string): RankRow[] =>
  topChartRows(rows, valueKey, labelKey)
    .map((row) => ({ ...row, rawValue: row.value, value: formatter(row.value) }))
    .slice(0, 6);

const trendRows = (rows: AnyRecord[], valueKeys: string[], secondKeys: string[] = []): ChartRow[] =>
  rows
    .map((row) => {
      const valueKey = valueKeys.find((key) => row[key] !== undefined && row[key] !== null);
      const secondKey = secondKeys.find((key) => row[key] !== undefined && row[key] !== null);
      return {
        name: String(row.dateLabel || row.dateTime || row.date || row.day || row.month || '').slice(0, 10) || '-',
        value: valueKey ? toNumber(row[valueKey]) : 0,
        value2: secondKey ? toNumber(row[secondKey]) : undefined,
      };
    })
    .filter((row) => row.value > 0 || toNumber(row.value2) > 0)
    .slice(-12);

const createDashboardPayload = (code: string) => {
  const range = selectedPeriodRange.value;
  const startDate = range[0] || formatYmd(monthStart);
  const endDate = range[1] || formatYmd(monthEnd);
  return {
    systemCode: code,
    periodType: periodType.value,
    targetDate: targetDate.value,
    rangeType: 'period',
    timezone: 'Asia/Shanghai',
    topN: 10,
    startDate,
    endDate,
    startTime: startDate,
    endTime: endDate,
    startYmd: startDate,
    endYmd: endDate,
    dateList: [startDate, endDate],
  };
};

const fallbackTrendRows = (cards: MetricCard[]) =>
  cards.map((item) => ({
    name: item.label,
    value: toNumber(item.value),
  }));

const rentalSection = (system: AnyRecord, dashboard: AnyRecord): SystemSection => {
  const summary = dashboard.summary || {};
  const orderAnalysis = dashboard.orderAnalysis || {};
  const orderSummary = orderAnalysis.summary || {};
  const deviceAnalysis = dashboard.deviceAnalysis || {};
  const deviceSummary = deviceAnalysis.summary || {};
  const channelAnalysis = dashboard.channelAnalysis || {};
  const repairAnalysis = dashboard.repairInventoryAnalysis || {};
  const totalDeviceCount = toNumber(deviceSummary.totalDeviceCount ?? summary.deviceCount);
  const freeDeviceCount = toNumber(deviceSummary.freeDeviceCount ?? summary.freeDeviceCount);
  const rentingDeviceCount = toNumber(deviceSummary.rentingDeviceCount ?? summary.rentingDeviceCount);
  const totalOrders = toNumber(orderSummary.totalOrders ?? summary.totalOrders);
  const totalAmount = toNumber(orderSummary.totalAmount ?? summary.totalAmount);
  const abnormalOrderCount = toNumber(orderSummary.abnormalOrderCount ?? summary.abnormalOrderCount);
  const pendingReturnCount = toNumber(orderSummary.pendingReturnCount ?? summary.pendingReturnCount);
  const utilizationRate = clampPercent(deviceSummary.utilizationRate);
  const cards = [
    card('设备总数', formatCount(totalDeviceCount), `空闲 ${formatCount(freeDeviceCount)}，在租 ${formatCount(rentingDeviceCount)}`, 'server', 'primary', 'primary', '资产'),
    card('订单总数', formatCount(totalOrders), `完成率 ${formatPercent(orderSummary.finishedRate)}`, 'root-list', 'success', 'success', '订单'),
    card('租金收入', formatMoney(totalAmount), `客单价 ${formatMoney(orderSummary.averageOrderAmount ?? summary.averageOrderAmount)}`, 'money', 'warning', 'warning', '收入'),
    card('风险待办', formatCount(abnormalOrderCount + pendingReturnCount), `异常 ${formatCount(abnormalOrderCount)} 单`, 'error-circle', 'danger', 'danger', '风险'),
  ];
  const trend = trendRows(safeArray(orderAnalysis.trendRows || dashboard.financialReport?.trendRows || dashboard.trend), ['rentAmount', 'amount', 'nowMoney', 'totalAmount'], ['orderCount', 'nowOrder']);
  const pieRows = topChartRows(
    safeArray(deviceAnalysis.statusRows || repairAnalysis.inventoryStatusRows).length
      ? safeArray(deviceAnalysis.statusRows || repairAnalysis.inventoryStatusRows)
      : [
          { name: '空闲设备', value: freeDeviceCount },
          { name: '在租设备', value: rentingDeviceCount },
          { name: '异常设备', value: toNumber(deviceSummary.abnormalDeviceCount) },
        ],
  );
  return {
    ...system,
    code: 'rental',
    name: system.name || '设备租赁系统',
    tone: 'primary',
    cards,
    trendTitle: '订单与租金趋势',
    trendRows: trend.length ? trend : fallbackTrendRows(cards),
    trendSeriesNames: ['租金', '订单数'],
    target: {
      title: '设备利用率',
      label: '利用率',
      display: formatPercent(utilizationRate),
      percent: utilizationRate,
      metrics: [
        { label: '平均客单价', value: formatMoney(orderSummary.averageOrderAmount ?? summary.averageOrderAmount) },
        { label: '覆盖地域', value: formatCount(dashboard.regionOverview?.summary?.coveredRegionCount) },
        { label: '来源渠道', value: formatCount(channelAnalysis.summary?.sourceCount) },
      ],
    },
    pieTitle: '设备与库存状态',
    pieRows,
    barTitle: '高价值设备排行',
    barRows: topChartRows(safeArray(deviceAnalysis.deviceRows || deviceAnalysis.classRows || channelAnalysis.sourceRows)),
    tableTitle: '订单风险分布',
    rankRows: topRankRows(
      safeArray(orderAnalysis.riskRows).length
        ? safeArray(orderAnalysis.riskRows)
        : [
            { name: '异常订单', value: abnormalOrderCount },
            { name: '待确认归还', value: pendingReturnCount },
            { name: '已完成订单', value: orderSummary.finishedOrders },
            { name: '进行中订单', value: Math.max(totalOrders - toNumber(orderSummary.finishedOrders), 0) },
          ],
    ),
  };
};

const alipaySection = (system: AnyRecord, dashboard: AnyRecord): SystemSection => {
  const overview = dashboard.overview || dashboard.summary || {};
  const trend = dashboard.trend || {};
  const rankings = dashboard.rankings || {};
  const distributions = dashboard.distributions || {};
  const geoDistribution = dashboard.geoDistribution || {};
  const funnel = dashboard.funnel || {};
  const totalOrders = toNumber(overview.totalOrders);
  const totalAmount = currencyValue(overview.totalRevenueYuan ?? overview.totalAmount, overview.totalRevenueCents);
  const activeOrders = toNumber(overview.activeOrders ?? overview.activeRentals);
  const finishedRate = clampPercent(funnel.completionRate ?? overview.finishedRate);
  const cards = [
    card('订单量', formatCount(totalOrders), '筛选范围订单', 'root-list', 'primary', 'primary', '订单'),
    card('租赁收入', formatMoney(totalAmount), `客单价 ${formatMoney(currencyValue(overview.avgOrderValue, overview.avgOrderValueCents))}`, 'money', 'success', 'success', '收入'),
    card('履约中', formatCount(activeOrders), `完结率 ${formatPercent(finishedRate)}`, 'time', 'warning', 'warning', '履约'),
    card('覆盖用户', formatCount(overview.uniqueUsers), `设备 ${formatCount(overview.uniqueDeviceCount)} 款`, 'user', 'purple', 'primary', '用户'),
  ];
  const dailyRows = safeArray(trend.points || trend.orders || dashboard.financialReport?.trendRows);
  return {
    ...system,
    code: 'alipay',
    name: system.name || 'HONESTTAI 租赁管理',
    tone: 'success',
    cards,
    trendTitle: '经营趋势',
    trendRows: trendRows(dailyRows, ['orderAmount', 'amountYuan', 'totalAmount', 'amount', 'revenue'], ['orderCount', 'orders']),
    trendSeriesNames: ['租赁收入', '订单量'],
    target: {
      title: '履约目标',
      label: '完成',
      display: formatPercent(finishedRate),
      percent: finishedRate,
      metrics: [
        { label: '订单金额', value: formatMoney(totalAmount) },
        { label: '累计租期', value: `${formatCount(overview.totalRentDays)} 天` },
        { label: '覆盖用户', value: formatCount(overview.uniqueUsers) },
      ],
    },
    pieTitle: '订单状态分布',
    pieRows: topChartRows(safeArray(distributions.status || distributions.orderStatus)),
    barTitle: '热门设备',
    barRows: topChartRows(safeArray(rankings.devices || rankings.byOrders), 'totalAmount'),
    tableTitle: '地域排行',
    rankRows: topRankRows(safeArray(geoDistribution.provinces || geoDistribution.regions), formatCount, 'orderCount'),
  };
};

const entrySection = (system: AnyRecord): SystemSection => {
  const cards = [
    card('授权范围', system.summaryVisible === false ? '入口' : '总览', system.permissionSummary || '-', 'dashboard', 'primary', 'primary', '权限'),
    card('系统入口', system.name || '-', system.code || '-', 'app', 'success', 'success', '入口'),
    card('分析接口', system.dashboardApi ? '已配置' : '未开放', system.dashboardApi || '-', 'data-base', 'warning', 'warning', '接口'),
    card('入口状态', errorMap.value[system.code] ? '异常' : '正常', errorMap.value[system.code] || '-', 'error-circle', 'danger', 'danger', '状态'),
  ];
  return {
    ...system,
    code: system.code,
    name: system.name || system.code,
    tone: 'primary',
    cards,
    trendTitle: '入口状态',
    trendRows: fallbackTrendRows(cards),
    trendSeriesNames: ['状态'],
    target: {
      title: '授权状态',
      label: '权限',
      display: system.summaryVisible === false ? '入口' : '总览',
      percent: system.summaryVisible === false ? 45 : 100,
      metrics: [
        { label: '系统编码', value: system.code || '-' },
        { label: '数据接口', value: system.dashboardApi ? '已配置' : '未开放' },
        { label: '入口', value: '可用' },
      ],
    },
    pieTitle: '入口信息',
    pieRows: fallbackTrendRows(cards),
    barTitle: '入口信息',
    barRows: fallbackTrendRows(cards),
    tableTitle: '入口信息',
    rankRows: cards.map((item) => ({ name: item.label, value: item.value, rawValue: toNumber(item.value) })),
  };
};

const buildSection = (system: AnyRecord): SystemSection => {
  const dashboard = dashboards.value[system.code] || {};
  if (system.code === 'rental' && system.summaryVisible !== false) return rentalSection(system, dashboard);
  if (system.code === 'alipay' && system.summaryVisible !== false) return alipaySection(system, dashboard);
  return entrySection(system);
};

const systemSections = computed<SystemSection[]>(() => systems.value.map(buildSection));
const activeSection = computed(() => systemSections.value.find((section) => section.code === activeCode.value) || systemSections.value[0] || null);

const selectSystem = (code: string) => {
  activeCode.value = code;
};

const setChartRef = (key: string, el: Element | ComponentPublicInstance | null) => {
  if (el instanceof HTMLElement && activeCharts[key] && activeCharts[key].getDom() !== el) {
    activeCharts[key].dispose();
    delete activeCharts[key];
  }
  chartRefs[key] = el instanceof HTMLElement ? el : null;
};

const baseChartOption = {
  animationDuration: 260,
  textStyle: {
    color: '#475467',
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  },
};

const renderTrendChart = (section: SystemSection) => {
  const el = chartRefs[`${section.code}-trend`];
  if (!el) return;
  activeCharts[`${section.code}-trend`] ||= echarts.init(el);
  const chart = activeCharts[`${section.code}-trend`];
  const rows = section.trendRows.length ? section.trendRows : [{ name: '暂无数据', value: 0, value2: 0 }];
  chart.setOption(
    {
      ...baseChartOption,
      color: ['#465fff', '#2e90fa'],
      tooltip: { trigger: 'axis' },
      legend: { top: 0, right: 0, icon: 'circle' },
      grid: { top: 46, right: 24, bottom: 28, left: 42, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: rows.map((row) => row.name), axisTick: { show: false } },
      yAxis: [
        { type: 'value', splitLine: { lineStyle: { color: '#eef2f6' } } },
        { type: 'value', splitLine: { show: false } },
      ],
      series: [
        {
          name: section.trendSeriesNames[0] || '金额',
          type: 'line',
          smooth: true,
          areaStyle: { opacity: 0.08 },
          data: rows.map((row) => row.value),
        },
        {
          name: section.trendSeriesNames[1] || '数量',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 22,
          itemStyle: { borderRadius: [8, 8, 0, 0] },
          data: rows.map((row) => toNumber(row.value2)),
        },
      ],
    },
    true,
  );
};

const renderPieChart = (section: SystemSection) => {
  const el = chartRefs[`${section.code}-pie`];
  if (!el) return;
  activeCharts[`${section.code}-pie`] ||= echarts.init(el);
  const rows = section.pieRows.length ? section.pieRows : [{ name: '暂无数据', value: 1 }];
  activeCharts[`${section.code}-pie`].setOption(
    {
      ...baseChartOption,
      color: section.pieRows.length ? chartColors : ['#eaecf0'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          name: section.pieTitle,
          type: 'pie',
          radius: ['54%', '76%'],
          center: ['50%', '42%'],
          label: { formatter: '{b}\n{d}%' },
          labelLine: { length: 10, length2: 8 },
          data: rows,
        },
      ],
    },
    true,
  );
};

const renderBarChart = (section: SystemSection) => {
  const el = chartRefs[`${section.code}-bar`];
  if (!el) return;
  activeCharts[`${section.code}-bar`] ||= echarts.init(el);
  const rows = section.barRows.length ? section.barRows.slice(0, 6) : [{ name: '暂无数据', value: 0 }];
  activeCharts[`${section.code}-bar`].setOption(
    {
      ...baseChartOption,
      color: ['#465fff'],
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 8, right: 18, top: 12, bottom: 8, containLabel: true },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f6' } } },
      yAxis: {
        type: 'category',
        inverse: true,
        data: rows.map((row) => row.name.slice(0, 12)),
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [
        {
          name: section.barTitle,
          type: 'bar',
          barMaxWidth: 16,
          itemStyle: { borderRadius: [0, 8, 8, 0] },
          data: rows.map((row) => row.value),
        },
      ],
    },
    true,
  );
};

const renderCharts = () => {
  nextTick(() => {
    if (!activeSection.value) return;
    renderTrendChart(activeSection.value);
    renderPieChart(activeSection.value);
    renderBarChart(activeSection.value);
  });
};

const resizeCharts = () => {
  Object.values(activeCharts).forEach((chart) => chart.resize());
};

const canLoadDashboard = (system: AnyRecord) => !!(system && system.summaryVisible !== false && system.dashboardApi);

const loadDashboard = async (system: AnyRecord) => {
  if (!canLoadDashboard(system)) return;
  loadingMap.value = { ...loadingMap.value, [system.code]: true };
  errorMap.value = { ...errorMap.value, [system.code]: '' };
  try {
    const response = await fetchSystemDashboard(system.dashboardApi, createDashboardPayload(system.code));
    dashboards.value = { ...dashboards.value, [system.code]: resolveData<AnyRecord>(response, {}) };
  } catch (error) {
    errorMap.value = { ...errorMap.value, [system.code]: error instanceof Error ? error.message : '加载失败' };
  } finally {
    loadingMap.value = { ...loadingMap.value, [system.code]: false };
  }
};

const refreshDashboards = async () => {
  loading.value = true;
  try {
    await Promise.all(systems.value.filter(canLoadDashboard).map(loadDashboard));
    renderCharts();
  } finally {
    loading.value = false;
  }
};

const handlePeriodTypeChange = (value: string | number | boolean) => {
  periodType.value = String(value || 'MONTH') as PeriodType;
  if (!targetDate.value) targetDate.value = formatYmd(new Date());
  refreshDashboards();
};

const handleTargetDateChange = (value: unknown) => {
  targetDate.value = String(value || formatYmd(new Date()));
  refreshDashboards();
};

const loadSystems = async () => {
  loading.value = true;
  try {
    const response = await listSystems();
    systems.value = safeArray(resolveData<AnyRecord[]>(response, []));
    if (!activeCode.value && systems.value.length) {
      activeCode.value = systems.value[0].code;
    }
    await refreshDashboards();
  } finally {
    loading.value = false;
  }
};

const resolveSystemUrl = (system: AnyRecord) => {
  const path = String(system?.path || '');
  const code = String(system?.code || '');
  const { protocol, hostname, origin } = window.location;
  const isLocal = ['localhost', '127.0.0.1'].includes(hostname);
  if (isLocal && localDevPorts[code]) {
    return `${protocol}//${hostname}:${localDevPorts[code]}/`;
  }
  if (/^https?:\/\//.test(path)) return path;
  return new URL(path || '/', origin).toString();
};

const openSystem = (system: AnyRecord) => {
  const url = appendSsoPayload(resolveSystemUrl(system), buildStoredSsoPayload());
  window.open(url, '_blank', 'noopener,noreferrer');
};

watch(activeSection, renderCharts, { deep: true });

onMounted(() => {
  loadSystems();
  window.addEventListener('resize', resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  Object.values(activeCharts).forEach((chart) => chart.dispose());
});
</script>

<style lang="less" scoped>
.platform-home {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 88px);
  padding: 20px;
  background: #f7f9fc;
}

.home-toolbar,
.system-select-card,
.system-section,
.tail-card {
  background: #fff;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.home-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
}

.home-toolbar h2 {
  margin: 0;
  color: #101828;
  font-size: 22px;
  font-weight: 700;
}

.home-toolbar > div,
.section-head,
.section-title,
.card-title,
.metric-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.home-toolbar > div {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.system-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.section-head {
  align-items: center;
}

.section-title {
  justify-content: flex-start;
}

.section-title h3 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  font-weight: 700;
}

.section-mark {
  width: 4px;
  height: 22px;
  border-radius: 8px;
  background: #465fff;
}

.tail-card {
  height: 100%;
  overflow: hidden;
}

.platform-home :deep(.t-card__body) {
  padding: 20px;
}

.metric-card {
  min-height: 146px;
}

.metric-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #465fff;
  font-size: 20px;
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

.metric-card__icon--danger {
  color: #d92d20;
  background: #fef3f2;
}

.metric-card__icon--purple {
  color: #7f56d9;
  background: #f4ebff;
}

.metric-card__label,
.metric-card__footer,
.target-metrics span {
  color: #667085;
  font-size: 13px;
}

.metric-card__value {
  margin-top: 14px;
  overflow: hidden;
  color: #101828;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card__footer {
  margin-top: 10px;
}

.system-select-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 218px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.system-select-card:hover,
.system-select-card.is-active-system {
  border-color: rgba(70, 95, 255, 0.5);
  box-shadow: 0 14px 28px rgb(15 23 42 / 7%);
  transform: translateY(-1px);
}

.system-select-card.is-active-system {
  background: #f9fbff;
}

.system-select-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.system-select-card__head strong {
  display: block;
  color: #101828;
  font-size: 17px;
  line-height: 24px;
}

.system-select-card__head small {
  display: block;
  margin-top: 3px;
  color: #667085;
  font-size: 13px;
}

.system-select-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.system-select-card__metrics > span {
  min-width: 0;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
}

.system-select-card__metrics em {
  display: block;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-select-card__metrics strong {
  display: block;
  overflow: hidden;
  margin-top: 6px;
  color: #101828;
  font-size: 20px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-title {
  min-height: 24px;
  margin-bottom: 14px;
  color: #101828;
  font-size: 16px;
  font-weight: 700;
}

.chart-card :deep(.t-card__body),
.target-card :deep(.t-card__body),
.mini-chart-card :deep(.t-card__body) {
  height: 100%;
}

.trend-chart {
  height: 360px;
}

.small-chart {
  height: 285px;
}

.target-card :deep(.t-icon) {
  color: #98a2b3;
  font-size: 22px;
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
    conic-gradient(#465fff var(--target-progress), #eef2f6 0);
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

.target-ring span {
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
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

.rank-list {
  display: grid;
  gap: 10px;
}

.rank-list > div {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) minmax(86px, auto);
  gap: 10px;
  align-items: center;
  min-height: 38px;
  border-bottom: 1px solid #f2f4f7;
}

.rank-list > div:last-child {
  border-bottom: 0;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #465fff;
  font-size: 12px;
  font-weight: 700;
  background: #eef4ff;
  border-radius: 8px;
}

.rank-name,
.rank-list strong {
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-list strong {
  color: #101828;
  text-align: right;
}

.platform-home :deep(.t-button),
.platform-home :deep(.t-tag) {
  border-radius: 8px;
}

@media (max-width: 960px) {
  .home-toolbar,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .home-toolbar > div {
    justify-content: flex-start;
  }

  .target-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .platform-home {
    padding: 12px;
  }

  .system-section {
    padding: 16px;
  }

  .trend-chart {
    height: 300px;
  }
}
</style>
