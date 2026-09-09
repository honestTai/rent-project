<template>
  <div class="alipay-monitor-page">
    <section class="monitor-header ant-pro-content-card">
      <div class="monitor-header__copy">
        <span>System Monitor</span>
        <h1>系统监控</h1>
        <p>{{ summaryText }}</p>
      </div>
      <div class="monitor-header__actions">
        <t-tag variant="light" theme="primary">支付宝租赁服务</t-tag>
        <t-tag variant="light" :theme="monitorModeTheme">{{ monitorModeText }}</t-tag>
        <t-tag variant="light" theme="primary">更新于 {{ updatedAt || '-' }}</t-tag>
        <t-switch v-model="realtime" label="实时刷新" @change="toggleRealtime" />
        <t-button theme="primary" variant="outline" :loading="loading" @click="loadMonitor">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
      </div>
    </section>

    <t-loading :loading="loading" size="small">
      <div class="monitor-stack">
        <div class="monitor-card-grid">
          <section v-for="card in metricCards" :key="card.key" class="monitor-metric-card" :class="`monitor-metric-card--${card.tone}`">
            <div class="monitor-metric-card__head">
              <span class="monitor-metric-card__icon" :class="`monitor-metric-card__icon--${card.tone}`">
                <t-icon :name="card.icon" />
              </span>
              <t-tag size="small" variant="light" :theme="card.theme">{{ card.badge }}</t-tag>
            </div>
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <em>{{ card.desc }}</em>
          </section>
        </div>

        <div class="monitor-panel-grid">
          <section class="monitor-panel monitor-panel--wide">
            <div class="monitor-panel__head">
              <div>
                <h2>资源使用</h2>
                <p>{{ resourcePanelSubtitle }}</p>
              </div>
              <t-tag :theme="resourceRiskTheme" variant="light">{{ resourceRiskText }}</t-tag>
            </div>
            <div class="resource-list">
              <div v-for="item in resourceItems" :key="item.label" class="resource-row">
                <div>
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
                <i><b :style="{ width: `${item.percent}%`, background: item.color }" /></i>
              </div>
            </div>
          </section>

          <section class="monitor-panel">
            <div class="monitor-panel__head">
              <div>
                <h2>内存结构</h2>
                <p>{{ memorySubtitle }}</p>
              </div>
            </div>
            <div class="memory-list">
              <div v-for="item in memoryRows" :key="item.name" class="memory-row">
                <span>{{ item.name }}</span>
                <strong>{{ formatCount(item.value) }} MB</strong>
              </div>
            </div>
          </section>
        </div>

        <section class="monitor-panel">
          <div class="monitor-panel__head">
            <div>
              <h2>服务实例</h2>
              <p>{{ detailHint }}</p>
            </div>
          </div>
          <t-table
            row-key="code"
            :data="serviceRows"
            :columns="serviceColumns"
            :hover="true"
            size="small"
            table-layout="fixed"
            cell-empty-content="-"
          >
            <template #status="{ row }">
              <t-tag :theme="statusTheme(row.status)" variant="light">{{ statusText(row.status) }}</t-tag>
            </template>
            <template #responseTimeMs="{ row }">
              <span :class="usageToneClass(row.responseTimeMs, 'latency')">{{ formatMs(row.responseTimeMs) }}</span>
            </template>
            <template #cpuUsage="{ row }">
              <span :class="usageToneClass(row.cpuUsage)">{{ formatPercent(row.cpuUsage) }}</span>
            </template>
            <template #memoryUsage="{ row }">
              <span :class="usageToneClass(row.memoryUsage)">{{ formatPercent(row.memoryUsage) }}</span>
            </template>
          </t-table>
        </section>
      </div>
    </t-loading>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { rentApi, resolveData, type AnyRecord } from '@/api/rent';

const chartColors = ['#0052d9', '#12b76a', '#f79009', '#f04438', '#7f56d9', '#2e90fa'];
const ALIPAY_SERVICE_CODE = 'alipay';

const loading = ref(false);
const realtime = ref(false);
const timer = ref<number | null>(null);
const monitor = ref<AnyRecord>({});

const updatedAt = computed(() => String(monitor.value.updatedAt || ''));
const monitorMode = computed(() => String(monitor.value.monitorMode || 'ACTUATOR'));
const isDockerMode = computed(() => monitorMode.value === 'DOCKER');
const summary = computed<AnyRecord>(() => monitor.value.summary || {});
const machine = computed<AnyRecord>(() => monitor.value.machine || {});
const charts = computed<AnyRecord>(() => monitor.value.charts || {});
const services = computed<AnyRecord[]>(() => safeArray(monitor.value.services));
const selectedService = computed(() => services.value.find((item) => item.code === ALIPAY_SERVICE_CODE) || services.value[0] || {});
const serviceRows = computed(() => services.value.filter((item) => !item.code || item.code === ALIPAY_SERVICE_CODE));
const monitorModeText = computed(() => (isDockerMode.value ? 'Docker 监控' : 'Actuator 监控'));
const monitorModeTheme = computed(() => (isDockerMode.value ? 'success' : 'warning'));
const detailHint = computed(() =>
  isDockerMode.value ? 'Docker 容器状态、资源和镜像信息' : '健康端点和 Actuator 指标',
);
const resourcePanelSubtitle = computed(() =>
  isDockerMode.value ? '支付宝租赁容器 CPU、内存、可写层和网络使用率' : '支付宝租赁服务 CPU、JVM 堆和磁盘使用率',
);
const memorySubtitle = computed(() =>
  isDockerMode.value ? '容器内存使用与剩余容量' : '物理内存和 JVM 堆占用对比',
);

const serviceColumns = computed(() => {
  if (isDockerMode.value) {
    return [
      { title: '服务', colKey: 'name', minWidth: 130 },
      { title: '容器', colKey: 'containerName', minWidth: 170, ellipsis: true },
      { title: '状态', colKey: 'status', width: 110 },
      { title: 'CPU', colKey: 'cpuUsage', width: 100, align: 'center' },
      { title: '内存', colKey: 'memoryUsage', width: 100, align: 'center' },
      { title: '说明', colKey: 'message', minWidth: 220, ellipsis: true },
    ];
  }
  return [
    { title: '服务', colKey: 'name', minWidth: 130 },
    { title: '应用名', colKey: 'applicationName', minWidth: 190, ellipsis: true },
    { title: '状态', colKey: 'status', width: 110 },
    { title: '响应', colKey: 'responseTimeMs', width: 110, align: 'center' },
    { title: '节点', colKey: 'host', minWidth: 150, ellipsis: true },
    { title: '说明', colKey: 'message', minWidth: 220, ellipsis: true },
  ];
});

const summaryText = computed(() => {
  if (!Object.keys(monitor.value).length) return '加载后展示支付宝租赁服务健康、CPU、内存、JVM 和磁盘指标。';
  return `当前固定查看 ${summary.value.selectedServiceName || '支付宝租赁服务'}，服务状态 ${statusText(selectedService.value.status)}，CPU ${formatPercent(summary.value.systemCpuUsage)}，内存 ${formatPercent(summary.value.physicalMemoryUsage)}。`;
});

const metricCards = computed(() => [
  {
    key: 'service',
    label: '服务状态',
    value: statusText(selectedService.value.status),
    desc: selectedService.value.message || '健康端点等待返回',
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
      : `进程 CPU ${formatPercent(machine.value.processCpuUsage)}`,
    icon: 'dashboard',
    tone: usageTone(summary.value.systemCpuUsage),
    theme: usageTheme(summary.value.systemCpuUsage),
    badge: 'CPU',
  },
  {
    key: 'memory',
    label: isDockerMode.value ? '容器内存' : '内存使用率',
    value: formatPercent(summary.value.physicalMemoryUsage),
    desc: `${formatCount(machine.value.physicalMemoryUsedMb)} / ${formatCount(machine.value.physicalMemoryTotalMb)} MB`,
    icon: 'chart-bubble',
    tone: usageTone(summary.value.physicalMemoryUsage),
    theme: usageTheme(summary.value.physicalMemoryUsage),
    badge: '内存',
  },
  {
    key: 'jvm',
    label: 'JVM 堆',
    value: formatPercent(summary.value.heapMemoryUsage),
    desc: `${formatCount(machine.value.heapMemoryUsedMb)} / ${formatCount(machine.value.heapMemoryMaxMb)} MB`,
    icon: 'code',
    tone: usageTone(summary.value.heapMemoryUsage),
    theme: usageTheme(summary.value.heapMemoryUsage),
    badge: 'JVM',
  },
  {
    key: 'disk',
    label: isDockerMode.value ? '可写层占用' : '磁盘占用',
    value: formatPercent(summary.value.diskUsage),
    desc: `${formatCount(machine.value.diskUsedGb)} / ${formatCount(machine.value.diskTotalGb)} GB`,
    icon: 'data-base',
    tone: usageTone(summary.value.diskUsage),
    theme: usageTheme(summary.value.diskUsage),
    badge: '磁盘',
  },
]);

const statusRiskTone = computed(() => {
  const status = String(selectedService.value.status || 'DOWN');
  if (status === 'UP') return 'success';
  if (status === 'DEGRADED') return 'warning';
  return 'danger';
});
const statusRiskTheme = computed(() =>
  statusRiskTone.value === 'danger' ? 'danger' : statusRiskTone.value === 'warning' ? 'warning' : 'success',
);
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

const resourceItems = computed(() => {
  const rows = [
    {
      label: 'CPU',
      value: formatPercent(machine.value.systemCpuUsage),
      percent: percentValue(machine.value.systemCpuUsage),
      color: chartColors[0],
    },
    {
      label: isDockerMode.value ? '容器内存' : '内存',
      value: `${formatCount(machine.value.physicalMemoryUsedMb)} / ${formatCount(machine.value.physicalMemoryTotalMb)} MB`,
      percent: percentValue(machine.value.physicalMemoryUsage),
      color: chartColors[1],
    },
    {
      label: 'JVM 堆',
      value: `${formatCount(machine.value.heapMemoryUsedMb)} / ${formatCount(machine.value.heapMemoryMaxMb)} MB`,
      percent: percentValue(machine.value.heapMemoryUsage),
      color: chartColors[2],
    },
    {
      label: isDockerMode.value ? '可写层' : '磁盘',
      value: `${formatCount(machine.value.diskUsedGb)} / ${formatCount(machine.value.diskTotalGb)} GB`,
      percent: percentValue(machine.value.diskUsage),
      color: chartColors[4],
    },
  ];
  if (isDockerMode.value) {
    rows.push({
      label: '网络收发',
      value: `${formatCount(machine.value.networkRxMb)} / ${formatCount(machine.value.networkTxMb)} MB`,
      percent: 0,
      color: chartColors[5],
    });
  }
  return rows;
});
const memoryRows = computed(() => safeArray(charts.value.memoryRows).filter((row) => toNumber(row.value) > 0));

async function loadMonitor() {
  loading.value = true;
  try {
    const response = await rentApi.fetchAlipayMonitorOverview();
    monitor.value = resolveData<AnyRecord>(response, {});
  } finally {
    loading.value = false;
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

function toggleRealtime(enabled: unknown) {
  if (Boolean(enabled)) {
    startRealtime();
  } else {
    stopRealtime();
  }
}

function safeArray<T = AnyRecord>(value: unknown): T[] {
  return Array.isArray(value) ? value : [];
}

function toNumber(value: unknown) {
  const number = Number(String(value ?? '').replace(/,/g, '').replace('%', ''));
  return Number.isFinite(number) ? number : 0;
}

function formatCount(value: unknown) {
  return toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 1 });
}

function formatPercent(value: unknown) {
  return `${formatCount(value)}%`;
}

function formatMs(value: unknown) {
  return `${formatCount(value)} ms`;
}

function percentValue(value: unknown) {
  return Math.max(0, Math.min(100, toNumber(value)));
}

function usageTone(value: unknown) {
  const number = toNumber(value);
  if (number >= 85) return 'danger';
  if (number >= 70) return 'warning';
  return 'success';
}

function usageTheme(value: unknown) {
  const tone = usageTone(value);
  if (tone === 'danger') return 'danger';
  if (tone === 'warning') return 'warning';
  return 'success';
}

function statusTheme(status: unknown) {
  if (status === 'UP') return 'success';
  if (status === 'DEGRADED') return 'warning';
  return 'danger';
}

function statusText(status: unknown) {
  if (status === 'UP') return '正常';
  if (status === 'DEGRADED') return '部分异常';
  if (status === 'DOWN') return '不可用';
  return '等待数据';
}

function usageToneClass(value: unknown, type = 'usage') {
  const number = toNumber(value);
  if (type === 'latency') {
    if (number >= 1000) return 'monitor-tone--danger';
    if (number >= 500) return 'monitor-tone--warning';
    return 'monitor-tone--success';
  }
  const tone = usageTone(value);
  if (tone === 'danger') return 'monitor-tone--danger';
  if (tone === 'warning') return 'monitor-tone--warning';
  return 'monitor-tone--success';
}

onMounted(() => {
  loadMonitor().catch((error) => {
    MessagePlugin.error(error instanceof Error ? error.message : '系统监控加载失败');
  });
});

onBeforeUnmount(() => {
  stopRealtime();
});
</script>

<style scoped>
.alipay-monitor-page {
  display: flex;
  min-height: calc(100vh - 160px);
  flex-direction: column;
  gap: 16px;
}

.monitor-header,
.monitor-panel,
.monitor-metric-card {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  box-shadow: none !important;
}

.monitor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
}

.monitor-header__copy {
  min-width: 240px;
}

.monitor-header__copy span {
  color: #0052d9;
  font-size: 12px;
  font-weight: 700;
}

.monitor-header__copy h1 {
  margin: 4px 0 0;
  color: #101828;
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
}

.monitor-header__copy p {
  max-width: 760px;
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.monitor-header__actions,
.monitor-metric-card__head,
.monitor-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.monitor-header__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.monitor-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.monitor-card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(160px, 1fr));
  gap: 12px;
}

.monitor-metric-card {
  min-width: 0;
  padding: 16px;
}

.monitor-metric-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #079455;
  font-size: 18px;
  border-radius: 6px;
  background: #ecfdf3;
}

.monitor-metric-card__icon--warning {
  color: #dc6803;
  background: #fffaeb;
}

.monitor-metric-card__icon--danger {
  color: #d92d20;
  background: #fef3f2;
}

.monitor-metric-card > span,
.monitor-metric-card > em,
.monitor-panel__head p,
.resource-row span,
.memory-row span {
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.monitor-metric-card > span {
  display: block;
  margin-top: 14px;
}

.monitor-metric-card > strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  color: #101828;
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-metric-card > em {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: 16px;
}

.monitor-panel {
  min-width: 0;
  padding: 18px 20px;
}

.monitor-panel__head {
  align-items: flex-start;
  margin-bottom: 16px;
}

.monitor-panel__head h2 {
  margin: 0;
  color: #101828;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.monitor-panel__head p {
  margin: 4px 0 0;
}

.resource-list {
  display: grid;
  gap: 14px;
}

.resource-row {
  display: grid;
  gap: 8px;
}

.resource-row > div,
.memory-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resource-row strong,
.memory-row strong {
  color: #101828;
  font-size: 13px;
  font-weight: 700;
}

.resource-row i {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #f2f4f7;
}

.resource-row b {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.memory-list {
  display: grid;
  gap: 10px;
}

.memory-row {
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.monitor-tone--success {
  color: #027a48;
}

.monitor-tone--warning {
  color: #b54708;
}

.monitor-tone--danger {
  color: #b42318;
}

.alipay-monitor-page :deep(.t-table),
.alipay-monitor-page :deep(.t-button),
.alipay-monitor-page :deep(.t-card) {
  box-shadow: none !important;
}

@media (max-width: 1280px) {
  .monitor-card-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  .monitor-panel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .monitor-header {
    display: grid;
  }

  .monitor-header__actions {
    justify-content: flex-start;
  }

  .monitor-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
