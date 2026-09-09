<template>
  <div class="region-map">
    <div class="region-map__canvas">
      <div ref="chartRef" class="region-map__chart" />
      <div v-if="activeRegion" class="region-map__floating">
        <span>{{ activeRegion.name }}</span>
        <strong>{{ activeRegion.orderCount }} 单</strong>
        <em>￥{{ formatAmount(activeRegion.totalAmount) }}</em>
      </div>
    </div>

    <div class="region-map__rank-list">
      <div v-for="item in rankedRegions.slice(0, 4)" :key="item.name" class="region-map__rank" @mouseenter="activeName = item.name">
        <div class="region-map__rank-name">
          <strong>{{ item.name }}</strong>
          <span>{{ item.orderCount }} 单</span>
        </div>
        <div class="region-map__bar">
          <i :style="{ width: `${item.percent}%` }" />
        </div>
        <strong class="region-map__amount">￥{{ formatAmount(item.totalAmount) }}</strong>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import chinaGeoJson from '@/assets/maps/china.json';

type RegionRow = { name?: string; orderCount?: number; totalAmount?: number; orderShare?: string | number };
type NormalizedRegion = { name: string; orderCount: number; totalAmount: number; orderShare: string | number };

const MAP_NAME = 'emsChinaDistribution';

const props = defineProps<{ regions?: RegionRow[] }>();
const chartRef = ref<HTMLDivElement | null>(null);
const activeName = ref('');
let chart: echarts.ECharts | null = null;

const normalizeProvinceName = (name = '') => (
  name
    .replace(/省|市|自治区|壮族|回族|维吾尔|特别行政区/g, '')
    .replace(/^内蒙$/, '内蒙古')
    .trim()
);

const formatAmount = (value?: number) => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 });

const mapFeatures = computed(() => ((chinaGeoJson as any).features || []) as any[]);
const provinceDisplayName = computed(() => {
  const result = new Map<string, string>();
  mapFeatures.value.forEach((feature) => {
    const displayName = feature?.properties?.name || '';
    result.set(normalizeProvinceName(displayName), displayName);
  });
  return result;
});

const normalizedRegions = computed<NormalizedRegion[]>(() => (props.regions || [])
  .map((item) => ({
    name: normalizeProvinceName(item.name || ''),
    orderCount: Number(item.orderCount || 0),
    totalAmount: Number(item.totalAmount || 0),
    orderShare: item.orderShare || '0.0',
  }))
  .filter((item) => item.name));

const rankedRegions = computed(() => {
  const maxAmount = Math.max(...normalizedRegions.value.map((item) => item.totalAmount), 1);
  return [...normalizedRegions.value]
    .sort((left, right) => right.totalAmount - left.totalAmount || right.orderCount - left.orderCount)
    .map((item) => ({
      ...item,
      percent: Math.max(8, Math.min(100, (item.totalAmount / maxAmount) * 100)),
    }));
});

const activeRegion = computed(() => rankedRegions.value.find((item) => item.name === activeName.value) || rankedRegions.value[0]);

const registerMap = () => {
  if (!(echarts as any).getMap(MAP_NAME)) {
    (echarts as any).registerMap(MAP_NAME, chinaGeoJson as any);
  }
};

const findRegionCenter = (regionName: string) => {
  const displayName = provinceDisplayName.value.get(regionName) || regionName;
  const feature = mapFeatures.value.find((item) => item?.properties?.name === displayName);
  return feature?.properties?.centroid || feature?.properties?.center || feature?.properties?.cp;
};

const buildOption = () => {
  const regionMap = new Map(normalizedRegions.value.map((item) => [item.name, item]));
  const maxAmount = Math.max(...normalizedRegions.value.map((item) => item.totalAmount), 1);
  const mapWidth = chartRef.value?.clientWidth || 640;
  const mapHeight = chartRef.value?.clientHeight || 360;
  const layoutSize = Math.round(Math.min(mapWidth * 0.82, mapHeight * 1.48));
  const mapData = mapFeatures.value.map((feature) => {
    const displayName = feature?.properties?.name || '';
    const name = normalizeProvinceName(displayName);
    const matched = regionMap.get(name);
    return {
      name: displayName,
      value: matched?.totalAmount || 0,
      orderCount: matched?.orderCount || 0,
      orderShare: matched?.orderShare || '0.0',
    };
  });
  const scatterData = rankedRegions.value.slice(0, 8).map((item) => {
    const center = findRegionCenter(item.name);
    if (!Array.isArray(center) || center.length < 2) return null;
    return {
      name: provinceDisplayName.value.get(item.name) || item.name,
      value: [center[0], center[1], item.totalAmount],
      orderCount: item.orderCount,
      totalAmount: item.totalAmount,
      orderShare: item.orderShare,
    };
  }).filter(Boolean);

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const displayName = params?.name || params?.data?.name || '';
        const name = normalizeProvinceName(displayName);
        const matched = regionMap.get(name);
        if (!matched) return `${displayName}<br/>暂无订单分布`;
        return [
          displayName,
          `订单数：${matched.orderCount}`,
          `订单金额：￥${formatAmount(matched.totalAmount)}`,
          `占比：${matched.orderShare}%`,
        ].join('<br/>');
      },
    },
    visualMap: {
      show: false,
      min: 0,
      max: maxAmount,
      inRange: {
        color: ['#f3f6fb', '#dce8ff', '#a7c6ff', '#5b7cfa'],
      },
    },
    geo: {
      map: MAP_NAME,
      roam: false,
      zoom: 1.06,
      layoutCenter: ['50%', mapHeight > 420 ? '52%' : '51%'],
      layoutSize,
      label: { show: false },
      itemStyle: {
        areaColor: '#d7dde7',
        borderColor: '#ffffff',
        borderWidth: 1,
      },
      emphasis: {
        label: { show: false },
        itemStyle: {
          areaColor: '#7b95ff',
          borderColor: '#ffffff',
        },
      },
    },
    series: [
      {
        type: 'map',
        map: MAP_NAME,
        geoIndex: 0,
        data: mapData,
        selectedMode: false,
      },
      {
        type: 'effectScatter',
        coordinateSystem: 'geo',
        zlevel: 2,
        rippleEffect: { scale: 3.2, brushType: 'stroke' },
        symbolSize: (value: number[]) => 8 + Math.min((Number(value?.[2] || 0) / maxAmount) * 18, 18),
        itemStyle: {
          color: '#365cff',
          shadowBlur: 12,
          shadowColor: 'rgb(54 92 255 / 30%)',
        },
        data: scatterData,
      },
    ],
  };
};

const render = async () => {
  await nextTick();
  if (!chartRef.value) return;
  registerMap();
  if (!chart) {
    chart = echarts.init(chartRef.value);
    chart.on('mouseover', (params: any) => {
      const name = normalizeProvinceName(params?.name || '');
      if (name && normalizedRegions.value.some((item) => item.name === name)) {
        activeName.value = name;
      }
    });
  }
  chart.setOption(buildOption(), true);
};

const resize = () => {
  chart?.resize();
  render();
};

watch(rankedRegions, (rows) => {
  activeName.value = rows[0]?.name || '';
  render();
}, { immediate: true, deep: true });

onMounted(() => {
  render();
  window.addEventListener('resize', resize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.region-map {
  display: grid;
  gap: 16px;
}

.region-map__canvas {
  position: relative;
  min-height: 360px;
  overflow: hidden;
  border: 0;
  border-radius: 6px;
  background: #fafafa;
}

.region-map__chart {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 360px;
}

.region-map__floating {
  position: absolute;
  right: 16px;
  bottom: 16px;
  display: grid;
  gap: 4px;
  min-width: 132px;
  padding: 12px 14px;
  border: 0;
  border-radius: 6px;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 2px 8px rgb(16 24 40 / 6%);
  backdrop-filter: blur(8px);
}

.region-map__floating span {
  color: var(--td-text-color-secondary);
  font-size: 12px;
}

.region-map__floating strong {
  color: #101828;
  font-size: 16px;
}

.region-map__floating em {
  color: var(--td-brand-color);
  font-style: normal;
  font-weight: 700;
}

.region-map__rank-list {
  display: grid;
  gap: 12px;
}

.region-map__rank {
  display: grid;
  grid-template-columns: 106px minmax(120px, 1fr) 108px;
  align-items: center;
  gap: 14px;
}

.region-map__rank-name {
  display: grid;
  gap: 2px;
}

.region-map__rank-name strong,
.region-map__amount {
  color: #101828;
  font-weight: 700;
}

.region-map__rank-name span {
  color: var(--td-text-color-secondary);
  font-size: 12px;
}

.region-map__bar {
  height: 9px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8edf3;
}

.region-map__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--td-brand-color);
}

@media (max-width: 680px) {
  .region-map__rank {
    grid-template-columns: 92px minmax(80px, 1fr);
  }

  .region-map__amount {
    grid-column: 1 / -1;
  }
}
</style>
