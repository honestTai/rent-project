<template>
  <div ref="chartRef" class="analytics-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import * as echarts from 'echarts';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

type ChartType = 'line' | 'bar' | 'pie';
type Row = Record<string, any>;

const props = withDefaults(defineProps<{
  type?: ChartType;
  rows?: Row[];
  xKey?: string;
  valueKeys?: string[];
  valueLabels?: Record<string, string>;
  height?: string;
  horizontal?: boolean;
  zoomable?: boolean;
}>(), {
  type: 'line',
  rows: () => [],
  xKey: 'label',
  valueKeys: () => ['value'],
  valueLabels: () => ({}),
  height: '280px',
  horizontal: false,
  zoomable: true,
});

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let autoZoomTimer: number | null = null;
let autoZoomStart = 0;
let isPointerInside = false;

const toNumber = (value: unknown) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

const buildOption = (): echarts.EChartsOption => {
  const rows = props.rows || [];
  if (props.type === 'pie') {
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        data: rows.map((row) => ({
          name: row[props.xKey] || row.label || row.name || '-',
          value: toNumber(row[props.valueKeys[0]]),
        })),
        label: { formatter: '{b}: {d}%' },
      }],
    };
  }

  const categories = rows.map((row) => row[props.xKey] || row.label || row.name || row.date || '-');
  const zoomEnd = zoomWindowEnd(rows.length);
  const series = props.valueKeys.map((key) => ({
    type: props.type,
    name: props.valueLabels[key] || key,
    smooth: props.type === 'line',
    barMaxWidth: 28,
    data: rows.map((row) => toNumber(row[key])),
    areaStyle: props.type === 'line' ? { opacity: 0.08 } : undefined,
  }));

  return {
    animationDurationUpdate: 800,
    animationEasingUpdate: 'cubicOut' as const,
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    dataZoom: props.zoomable && rows.length > 8
      ? [
        { type: 'inside', start: 0, end: zoomEnd },
        { type: 'slider', start: 0, end: zoomEnd, height: 18, bottom: 6, borderColor: 'transparent', backgroundColor: '#eef2f7', fillerColor: 'rgba(70, 95, 255, 0.16)', handleSize: 14 },
      ]
      : undefined,
    grid: { top: 42, left: props.horizontal ? 90 : 42, right: 24, bottom: props.zoomable && rows.length > 8 ? 58 : 38 },
    xAxis: props.horizontal ? { type: 'value' } : { type: 'category', data: categories, axisLabel: { interval: 0, rotate: categories.length > 6 ? 25 : 0 } },
    yAxis: props.horizontal ? { type: 'category', data: categories, axisLabel: { width: 76, overflow: 'truncate' } } : { type: 'value' },
    series,
  };
};

const isZoomableChart = () => props.type !== 'pie' && props.zoomable && (props.rows || []).length > 8;

const zoomWindowEnd = (length: number) => Math.min(100, Math.max(35, Math.round(800 / length)));

const stopAutoZoom = () => {
  if (!autoZoomTimer) return;
  window.clearInterval(autoZoomTimer);
  autoZoomTimer = null;
};

const startAutoZoom = () => {
  stopAutoZoom();
  if (!chart || !isZoomableChart() || isPointerInside) return;
  const rows = props.rows || [];
  const span = zoomWindowEnd(rows.length);
  const maxStart = Math.max(0, 100 - span);
  const step = Math.max(4, Math.round(span / 4));
  autoZoomTimer = window.setInterval(() => {
    if (!chart || isPointerInside) return;
    autoZoomStart = autoZoomStart >= maxStart ? 0 : Math.min(maxStart, autoZoomStart + step);
    chart.dispatchAction({
      type: 'dataZoom',
      start: autoZoomStart,
      end: Math.min(100, autoZoomStart + span),
    });
  }, 2200);
};

const syncAutoZoom = () => {
  autoZoomStart = 0;
  stopAutoZoom();
  startAutoZoom();
};

const render = async () => {
  await nextTick();
  if (!chartRef.value) return;
  if (!chart) chart = echarts.init(chartRef.value);
  chart.setOption(buildOption(), true);
  resize();
  syncAutoZoom();
};

const resize = () => {
  requestAnimationFrame(() => chart?.resize());
};

onMounted(() => {
  render();
  chartRef.value?.addEventListener('mouseenter', () => {
    isPointerInside = true;
    stopAutoZoom();
  });
  chartRef.value?.addEventListener('mouseleave', () => {
    isPointerInside = false;
    startAutoZoom();
  });
  if (chartRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(chartRef.value);
  }
  window.addEventListener('resize', resize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  stopAutoZoom();
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
});

watch(() => [props.type, props.rows, props.valueKeys, props.xKey, props.horizontal, props.zoomable], render, { deep: true });
</script>

<style scoped>
.analytics-chart {
  width: 100%;
  min-height: 220px;
}
</style>
