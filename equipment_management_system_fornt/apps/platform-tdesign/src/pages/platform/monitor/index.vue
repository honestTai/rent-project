<template>
  <div class="platform-page platform-monitor">
    <section class="monitor-header">
      <div class="monitor-header__copy">
        <span>System Monitor</span>
        <h2>系统监控</h2>
        <p>{{ summaryText }}</p>
      </div>
      <div class="monitor-header__actions">
        <t-select
          v-model="selectedServiceCode"
          class="monitor-service-select"
          placeholder="选择监控服务"
          @change="loadMonitor"
        >
          <t-option v-for="item in serviceSelectOptions" :key="item.value" :label="item.label" :value="item.value" />
        </t-select>
        <t-tag variant="light" :theme="monitorModeTheme">{{ monitorModeText }}</t-tag>
        <t-tag variant="light" theme="primary">更新于 {{ updatedAt || '-' }}</t-tag>
        <t-switch v-model="realtime" label="实时刷新" @change="toggleRealtime" />
        <t-button theme="primary" :loading="loading" @click="loadMonitor">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
      </div>
    </section>

    <t-loading :loading="loading" size="small">
      <div class="monitor-stack">
        <t-row :gutter="[16, 16]">
          <t-col v-for="card in metricCards" :key="card.key" :xs="12" :sm="6" :xl="3">
            <t-card :bordered="false" class="monitor-card" :class="`monitor-card--${card.tone}`">
              <div class="monitor-card__head">
                <span class="monitor-card__icon" :class="`monitor-card__icon--${card.tone}`">
                  <t-icon :name="card.icon" />
                </span>
                <t-tag size="small" variant="light" :theme="card.theme">{{ card.badge }}</t-tag>
              </div>
              <div class="monitor-card__label">{{ card.label }}</div>
              <div class="monitor-card__value">{{ card.value }}</div>
              <div class="monitor-card__footer">{{ card.desc }}</div>
            </t-card>
          </t-col>
        </t-row>

        <t-row :gutter="[16, 16]" align="stretch">
          <t-col :xs="12" :xl="8">
            <t-card :bordered="false" class="platform-card chart-card">
              <div class="section-heading">
                <div>
                  <div class="section-heading__title">资源使用趋势面板</div>
                  <div class="section-heading__subtitle">{{ resourcePanelSubtitle }}</div>
                </div>
                <t-tag :theme="resourceRiskTheme" variant="light">{{ resourceRiskText }}</t-tag>
              </div>
              <div ref="resourceChartRef" class="large-chart" />
            </t-card>
          </t-col>

          <t-col :xs="12" :xl="4">
            <t-card :bordered="false" class="platform-card">
              <div class="section-heading">
                <div>
                  <div class="section-heading__title">服务健康分布</div>
                  <div class="section-heading__subtitle">{{ healthPanelSubtitle }}</div>
                </div>
                <t-icon name="chart-pie" />
              </div>
              <div ref="statusChartRef" class="small-chart" />
            </t-card>
          </t-col>
        </t-row>

        <t-row :gutter="[16, 16]" align="stretch">
          <t-col :xs="12" :lg="5">
            <t-card :bordered="false" class="platform-card">
              <div class="section-heading section-heading--compact">
                <div>
                  <div class="section-heading__title">{{ resourceSectionTitle }}</div>
                  <div class="section-heading__subtitle">{{ machine.hostName || '-' }} / {{ machine.osName || '-' }}</div>
                </div>
              </div>
              <div class="resource-grid">
                <div v-for="item in resourceItems" :key="item.label">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                  <i><b :style="{ width: `${item.percent}%`, background: item.color }" /></i>
                </div>
              </div>
            </t-card>
          </t-col>

          <t-col :xs="12" :lg="7">
            <t-card :bordered="false" class="platform-card">
              <div class="section-heading">
                <div>
                  <div class="section-heading__title">{{ rankingTitle }}</div>
                  <div class="section-heading__subtitle">{{ rankingSubtitle }}</div>
                </div>
                <t-tag variant="light" theme="success">{{ rankingBadge }}</t-tag>
              </div>
              <div ref="latencyChartRef" class="middle-chart" />
            </t-card>
          </t-col>
        </t-row>

        <t-row :gutter="[16, 16]" align="stretch">
          <t-col :xs="12" :xl="8">
            <t-card :bordered="false" class="platform-card">
              <template #header>
                <div class="detail-title">
                  <span>服务实例明细</span>
                  <small>{{ detailHint }}</small>
                </div>
              </template>
              <t-table
                row-key="code"
                :data="services"
                :columns="serviceColumns"
                :hover="true"
                size="small"
                cell-empty-content="-"
                table-layout="fixed"
              >
                <template #status="{ row }">
                  <t-tag :theme="statusTheme(row.status)" variant="light">{{ statusText(row.status) }}</t-tag>
                </template>
                <template #instance="{ row }">
                  <span>{{ row.healthyInstanceCount || 0 }}/{{ row.instanceCount || 0 }}</span>
                </template>
                <template #responseTimeMs="{ row }">
                  <span :class="['latency-text', latencyTone(row.responseTimeMs)]">{{ formatMs(row.responseTimeMs) }}</span>
                </template>
                <template #cpuUsage="{ row }">
                  <span :class="['latency-text', usageToneClass(row.cpuUsage)]">{{ formatPercent(row.cpuUsage) }}</span>
                </template>
                <template #memoryUsage="{ row }">
                  <span :class="['latency-text', usageToneClass(row.memoryUsage)]">{{ formatPercent(row.memoryUsage) }}</span>
                </template>
              </t-table>
            </t-card>
          </t-col>

          <t-col :xs="12" :xl="4">
            <t-card :bordered="false" class="platform-card">
              <div class="section-heading section-heading--compact">
                <div>
                  <div class="section-heading__title">内存结构</div>
                  <div class="section-heading__subtitle">物理内存和 JVM 堆占用对比</div>
                </div>
              </div>
              <div ref="memoryChartRef" class="middle-chart" />
            </t-card>
          </t-col>
        </t-row>
      </div>
    </t-loading>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts';
import type { PrimaryTableCol, TableRowData } from 'tdesign-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { fetchMonitorOverview } from '@/api/platform';
import { resolveData, type AnyRecord } from '@/pages/platform/utils';

import '../index.less';

defineOptions({
  name: 'PlatformMonitor',
});

const chartColors = ['#465fff', '#12b76a', '#f79009', '#f04438', '#7f56d9', '#2e90fa'];

const loading = ref(false);
const realtime = ref(false);
const timer = ref<number | null>(null);
const monitor = ref<AnyRecord>({});
const selectedServiceCode = ref('all');
const resourceChartRef = ref<HTMLElement | null>(null);
const statusChartRef = ref<HTMLElement | null>(null);
const latencyChartRef = ref<HTMLElement | null>(null);
const memoryChartRef = ref<HTMLElement | null>(null);

let resourceChart: echarts.ECharts | null = null;
let statusChart: echarts.ECharts | null = null;
let latencyChart: echarts.ECharts | null = null;
let memoryChart: echarts.ECharts | null = null;

const updatedAt = computed(() => String(monitor.value.updatedAt || ''));
const monitorMode = computed(() => String(monitor.value.monitorMode || 'ACTUATOR'));
const isDockerMode = computed(() => monitorMode.value === 'DOCKER');
const summary = computed<AnyRecord>(() => monitor.value.summary || {});
const machine = computed<AnyRecord>(() => monitor.value.machine || {});
const charts = computed<AnyRecord>(() => monitor.value.charts || {});
const services = computed<AnyRecord[]>(() => safeArray(monitor.value.services));
const serviceOptions = computed<AnyRecord[]>(() => safeArray(monitor.value.serviceOptions));

const serviceSelectOptions = computed(() =>
  serviceOptions.value.map((item) => ({
    label: item.containerName ? `${item.name}（${item.containerName}）` : item.name,
    value: item.code,
  })),
);

const serviceColumns = computed<PrimaryTableCol<TableRowData>[]>(() => {
  if (isDockerMode.value) {
    return [
      { title: '服务', colKey: 'name', minWidth: 120 },
      { title: '容器', colKey: 'containerName', minWidth: 170, ellipsis: true },
      { title: '状态', colKey: 'status', width: 110 },
      { title: 'CPU', colKey: 'cpuUsage', width: 100, align: 'center' },
      { title: '内存', colKey: 'memoryUsage', width: 100, align: 'center' },
      { title: '镜像', colKey: 'image', minWidth: 180, ellipsis: true },
      { title: '说明', colKey: 'message', minWidth: 220, ellipsis: true },
    ];
  }
  return [
    { title: '服务', colKey: 'name', minWidth: 130 },
    { title: '应用名', colKey: 'applicationName', minWidth: 190, ellipsis: true },
    { title: '状态', colKey: 'status', width: 110 },
    { title: '实例', colKey: 'instance', width: 90, align: 'center' },
    { title: '响应', colKey: 'responseTimeMs', width: 110, align: 'center' },
    { title: '节点', colKey: 'host', minWidth: 150, ellipsis: true },
    { title: '说明', colKey: 'message', minWidth: 220, ellipsis: true },
  ];
});

const monitorModeText = computed(() => (isDockerMode.value ? 'Docker 监控' : 'Actuator 监控'));
const monitorModeTheme = computed(() => (isDockerMode.value ? 'success' : 'warning'));
const resourcePanelSubtitle = computed(() =>
  isDockerMode.value ? 'Docker CPU、容器内存和可写层使用率' : 'CPU、物理内存、JVM 堆和磁盘使用率',
);
const healthPanelSubtitle = computed(() =>
  isDockerMode.value ? 'Docker 容器运行状态和 healthcheck 结果' : '注册实例和健康端点探测结果',
);
const resourceSectionTitle = computed(() => (isDockerMode.value ? 'Docker 资源' : '机器资源'));
const rankingTitle = computed(() => (isDockerMode.value ? '容器 CPU 排行' : '服务响应排行'));
const rankingSubtitle = computed(() =>
  isDockerMode.value ? '按 Docker stats CPU 使用率展示，越低越稳定' : '按健康探测响应耗时展示，越低越稳定',
);
const rankingBadge = computed(() => (isDockerMode.value ? 'Docker stats' : 'Actuator'));
const detailHint = computed(() =>
  isDockerMode.value ? 'Docker 容器状态、资源和镜像信息' : '健康端点：/actuator/health',
);

const summaryText = computed(() => {
  if (!services.value.length) return '加载后展示服务健康、接口响应、CPU、内存、JVM 和磁盘指标。';
  const target = summary.value.selectedServiceName || '全部服务';
  return `当前查看 ${target}，已监控 ${formatCount(summary.value.totalServices)} 个服务，正常 ${formatCount(summary.value.healthyServices)} 个，异常 ${formatCount(summary.value.downServices)} 个，当前 CPU ${formatPercent(summary.value.systemCpuUsage)}。`;
});

const metricCards = computed(() => [
  {
    key: 'service',
    label: '服务可用率',
    value: formatPercent(summary.value.availabilityRate),
    desc: `${formatCount(summary.value.healthyServices)} / ${formatCount(summary.value.totalServices)} 个服务正常`,
    icon: 'server',
    tone: statusRiskTone.value,
    theme: statusRiskTheme.value,
    badge: '服务',
  },
  {
    key: 'cpu',
    label: isDockerMode.value ? '容器 CPU' : 'CPU 使用率',
    value: formatPercent(summary.value.systemCpuUsage),
    desc: isDockerMode.value
      ? `网络 ${formatCount(machine.value.networkRxMb)} / ${formatCount(machine.value.networkTxMb)} MB`
      : `进程 CPU ${formatPercent(machine.value.processCpuUsage)}，${formatCount(machine.value.availableProcessors)} 核`,
    icon: 'dashboard',
    tone: usageTone(summary.value.systemCpuUsage),
    theme: usageTheme(summary.value.systemCpuUsage),
    badge: 'CPU',
  },
  {
    key: 'memory',
    label: isDockerMode.value ? '容器内存' : '物理内存',
    value: formatPercent(summary.value.physicalMemoryUsage),
    desc: `${formatCount(machine.value.physicalMemoryUsedMb)} / ${formatCount(machine.value.physicalMemoryTotalMb)} MB`,
    icon: 'chart-bubble',
    tone: usageTone(summary.value.physicalMemoryUsage),
    theme: usageTheme(summary.value.physicalMemoryUsage),
    badge: '内存',
  },
  {
    key: 'disk',
    label: isDockerMode.value ? '可写层占用' : '磁盘占用',
    value: formatPercent(summary.value.diskUsage),
    desc: isDockerMode.value
      ? `${formatCount(machine.value.diskUsedGb)} / ${formatCount(machine.value.diskTotalGb)} GB，可写层`
      : `${formatCount(machine.value.diskUsedGb)} / ${formatCount(machine.value.diskTotalGb)} GB，运行 ${summary.value.uptimeText || '-'}`,
    icon: 'data-base',
    tone: usageTone(summary.value.diskUsage),
    theme: usageTheme(summary.value.diskUsage),
    badge: '磁盘',
  },
]);

const resourceItems = computed(() => {
  const rows = [
    {
      label: 'CPU',
      value: formatPercent(machine.value.systemCpuUsage),
      percent: percentValue(machine.value.systemCpuUsage),
      color: '#465fff',
    },
    {
      label: isDockerMode.value ? '容器内存' : '物理内存',
      value: `${formatCount(machine.value.physicalMemoryUsedMb)} / ${formatCount(machine.value.physicalMemoryTotalMb)} MB`,
      percent: percentValue(machine.value.physicalMemoryUsage),
      color: '#12b76a',
    },
  ];
  if (!isDockerMode.value) {
    rows.push({
      label: 'JVM 堆',
      value: `${formatCount(machine.value.heapMemoryUsedMb)} / ${formatCount(machine.value.heapMemoryMaxMb)} MB`,
      percent: percentValue(machine.value.heapMemoryUsage),
      color: '#f79009',
    });
  }
  rows.push({
    label: isDockerMode.value ? '可写层' : '磁盘',
    value: `${formatCount(machine.value.diskUsedGb)} / ${formatCount(machine.value.diskTotalGb)} GB`,
    percent: percentValue(machine.value.diskUsage),
    color: '#7f56d9',
  });
  if (isDockerMode.value) {
    rows.push({
      label: '网络收发',
      value: `${formatCount(machine.value.networkRxMb)} / ${formatCount(machine.value.networkTxMb)} MB`,
      percent: 0,
      color: '#2e90fa',
    });
  }
  return rows;
});

const statusRiskTone = computed(() => (toNumber(summary.value.downServices) > 0 ? 'danger' : toNumber(summary.value.degradedServices) > 0 ? 'warning' : 'success'));
const statusRiskTheme = computed(() => (statusRiskTone.value === 'danger' ? 'danger' : statusRiskTone.value === 'warning' ? 'warning' : 'success'));
const resourceRiskValue = computed(() =>
  Math.max(
    toNumber(summary.value.systemCpuUsage),
    toNumber(summary.value.physicalMemoryUsage),
    toNumber(summary.value.heapMemoryUsage),
    toNumber(summary.value.diskUsage),
  ),
);
const resourceRiskTheme = computed(() => usageTheme(resourceRiskValue.value));
const resourceRiskText = computed(() => (resourceRiskValue.value >= 85 ? '资源高压' : resourceRiskValue.value >= 70 ? '需要关注' : '资源稳定'));

const safeArray = <T = AnyRecord>(value: unknown): T[] => (Array.isArray(value) ? value : []);
const toNumber = (value: unknown) => {
  const number = Number(String(value ?? '').replace(/,/g, '').replace('%', ''));
  return Number.isFinite(number) ? number : 0;
};
const formatCount = (value: unknown) => toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 1 });
const formatPercent = (value: unknown) => `${formatCount(value)}%`;
const formatMs = (value: unknown) => `${formatCount(value)} ms`;
const percentValue = (value: unknown) => Math.max(0, Math.min(100, toNumber(value)));

function usageTone(value: unknown) {
  const number = toNumber(value);
  if (number >= 85) return 'danger';
  if (number >= 70) return 'warning';
  return 'success';
}

function usageTheme(value: unknown) {
  const tone = usageTone(value);
  return tone === 'danger' ? 'danger' : tone === 'warning' ? 'warning' : 'success';
}

function statusTheme(status: unknown) {
  if (status === 'UP') return 'success';
  if (status === 'DEGRADED') return 'warning';
  return 'danger';
}

function statusText(status: unknown) {
  if (status === 'UP') return '正常';
  if (status === 'DEGRADED') return '部分异常';
  return '不可用';
}

function latencyTone(value: unknown) {
  const number = toNumber(value);
  if (number >= 1000) return 'latency-text--danger';
  if (number >= 500) return 'latency-text--warning';
  return 'latency-text--success';
}

function usageToneClass(value: unknown) {
  const tone = usageTone(value);
  if (tone === 'danger') return 'latency-text--danger';
  if (tone === 'warning') return 'latency-text--warning';
  return 'latency-text--success';
}

async function loadMonitor() {
  loading.value = true;
  try {
    const res = await fetchMonitorOverview({ serviceCode: selectedServiceCode.value });
    monitor.value = resolveData<AnyRecord>(res, {});
    selectedServiceCode.value = String(monitor.value.selectedServiceCode || selectedServiceCode.value || 'all');
  } finally {
    loading.value = false;
    renderCharts();
  }
}

function stopRealtime() {
  if (timer.value) {
    window.clearInterval(timer.value);
    timer.value = null;
  }
  realtime.value = false;
}

function startRealtime() {
  stopRealtime();
  realtime.value = true;
  loadMonitor();
  timer.value = window.setInterval(loadMonitor, 5000);
}

function toggleRealtime(enabled: boolean) {
  if (enabled) {
    startRealtime();
  } else {
    stopRealtime();
  }
}

function renderCharts() {
  nextTick(() => {
    renderResourceChart();
    renderStatusChart();
    renderLatencyChart();
    renderMemoryChart();
  });
}

function renderResourceChart() {
  if (!resourceChartRef.value) return;
  resourceChart ||= echarts.init(resourceChartRef.value);
  const rows = safeArray(charts.value.resourceUsageRows);
  resourceChart.setOption(
    {
      color: chartColors,
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 42, right: 24, top: 30, bottom: 34, containLabel: true },
      xAxis: {
        type: 'category',
        data: rows.map((row) => row.name),
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%' },
        splitLine: { lineStyle: { color: '#eef2f6' } },
      },
      series: [
        {
          name: '使用率',
          type: 'bar',
          barMaxWidth: 42,
          itemStyle: {
            borderRadius: [8, 8, 0, 0],
            color: (params: AnyRecord) => chartColors[params.dataIndex % chartColors.length],
          },
          label: { show: true, position: 'top', formatter: '{c}%' },
          data: rows.map((row) => toNumber(row.value)),
        },
      ],
    },
    true,
  );
}

function renderStatusChart() {
  if (!statusChartRef.value) return;
  statusChart ||= echarts.init(statusChartRef.value);
  const rows = safeArray(charts.value.serviceStatusRows);
  statusChart.setOption(
    {
      color: ['#12b76a', '#f79009', '#f04438'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          name: '服务状态',
          type: 'pie',
          radius: ['54%', '76%'],
          center: ['50%', '42%'],
          label: { formatter: '{b}\n{d}%' },
          labelLine: { length: 10, length2: 8 },
          data: rows.length ? rows : [{ name: '暂无数据', value: 1, itemStyle: { color: '#eaecf0' } }],
        },
      ],
    },
    true,
  );
}

function renderLatencyChart() {
  if (!latencyChartRef.value) return;
  latencyChart ||= echarts.init(latencyChartRef.value);
  const rows = [...safeArray(charts.value.serviceLatencyRows)].sort((left, right) => toNumber(right.value) - toNumber(left.value));
  const unit = isDockerMode.value ? '%' : 'ms';
  latencyChart.setOption(
    {
      color: ['#465fff'],
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 18, right: 28, top: 18, bottom: 10, containLabel: true },
      xAxis: { type: 'value', name: unit, splitLine: { lineStyle: { color: '#eef2f6' } } },
      yAxis: {
        type: 'category',
        inverse: true,
        data: rows.map((row) => String(row.name).replace('服务', '')),
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [
        {
          name: isDockerMode.value ? 'CPU 使用率' : '响应耗时',
          type: 'bar',
          barMaxWidth: 16,
          itemStyle: { borderRadius: [0, 8, 8, 0] },
          data: rows.map((row) => toNumber(row.value)),
        },
      ],
    },
    true,
  );
}

function renderMemoryChart() {
  if (!memoryChartRef.value) return;
  memoryChart ||= echarts.init(memoryChartRef.value);
  const rows = safeArray(charts.value.memoryRows).filter((row) => toNumber(row.value) > 0);
  memoryChart.setOption(
    {
      color: chartColors,
      tooltip: { trigger: 'item', formatter: '{b}: {c} MB' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          name: '内存结构',
          type: 'pie',
          radius: ['50%', '72%'],
          center: ['50%', '42%'],
          label: { formatter: '{b}\n{c} MB' },
          labelLine: { length: 10, length2: 8 },
          data: rows.length ? rows : [{ name: '暂无数据', value: 1, itemStyle: { color: '#eaecf0' } }],
        },
      ],
    },
    true,
  );
}

function resizeCharts() {
  resourceChart?.resize();
  statusChart?.resize();
  latencyChart?.resize();
  memoryChart?.resize();
}

watch(monitor, renderCharts, { deep: true });

onMounted(() => {
  loadMonitor();
  window.addEventListener('resize', resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  stopRealtime();
  resourceChart?.dispose();
  statusChart?.dispose();
  latencyChart?.dispose();
  memoryChart?.dispose();
});
</script>

<style lang="less" scoped>
.platform-monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.monitor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.monitor-header__copy {
  min-width: 240px;
}

.monitor-header__copy span {
  color: #465fff;
  font-size: 12px;
  font-weight: 700;
}

.monitor-header__copy h2 {
  margin: 6px 0 0;
  color: #101828;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.monitor-header__copy p {
  max-width: 760px;
  margin: 8px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.6;
}

.monitor-header__actions,
.monitor-card__head,
.section-heading,
.detail-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.monitor-header__actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.monitor-service-select {
  width: 240px;
}

.monitor-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.monitor-card,
.platform-monitor :deep(.platform-card) {
  height: 100%;
  overflow: hidden;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.monitor-card {
  min-height: 148px;
}

.monitor-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #079455;
  font-size: 20px;
  background: #ecfdf3;
  border-radius: 8px;
}

.monitor-card__icon--warning {
  color: #dc6803;
  background: #fffaeb;
}

.monitor-card__icon--danger {
  color: #d92d20;
  background: #fef3f2;
}

.monitor-card__label,
.monitor-card__footer,
.section-heading__subtitle,
.resource-grid span,
.detail-title small {
  color: #667085;
  font-size: 13px;
}

.monitor-card__value {
  margin-top: 14px;
  overflow: hidden;
  color: #101828;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-card__footer {
  margin-top: 10px;
}

.section-heading {
  align-items: flex-start;
}

.section-heading--compact {
  margin-bottom: 16px;
}

.section-heading__title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.section-heading__subtitle {
  margin-top: 6px;
  line-height: 1.5;
}

.large-chart {
  height: 342px;
  margin-top: 12px;
}

.small-chart {
  height: 342px;
}

.middle-chart {
  height: 300px;
}

.resource-grid {
  display: grid;
  gap: 14px;
}

.resource-grid > div {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
}

.resource-grid strong {
  overflow: hidden;
  color: #101828;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-grid i {
  height: 8px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 8px;
}

.resource-grid b {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.detail-title {
  width: 100%;
}

.detail-title span {
  color: #101828;
  font-weight: 700;
}

.latency-text {
  font-weight: 700;
}

.latency-text--success {
  color: #079455;
}

.latency-text--warning {
  color: #dc6803;
}

.latency-text--danger {
  color: #d92d20;
}

.platform-monitor :deep(.t-card__body) {
  padding: 20px;
}

.platform-monitor :deep(.t-button),
.platform-monitor :deep(.t-switch__label) {
  border-radius: 8px;
}

.platform-monitor :deep(.t-table) {
  overflow: hidden;
  border-radius: 8px;
}

.platform-monitor :deep(.t-table th) {
  color: #475467;
  font-weight: 600;
  background: #f8fafc;
}

@media (max-width: 960px) {
  .monitor-header {
    flex-direction: column;
  }

  .monitor-header__actions {
    justify-content: flex-start;
    width: 100%;
  }

  .monitor-service-select {
    width: min(100%, 280px);
  }
}

@media (max-width: 640px) {
  .monitor-header {
    padding: 16px;
  }

  .monitor-header__copy h2 {
    font-size: 21px;
  }

  .large-chart,
  .small-chart,
  .middle-chart {
    height: 260px;
  }
}
</style>
