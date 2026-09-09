<template>
  <t-dialog v-model:visible="visibleModel" header="财务报表" width="86%" :footer="false" class="financial-dialog">
    <div class="financial-header">
      <div>
        <div class="financial-header__title">资金与履约概览</div>
        <p>{{ rangeText || '当前范围暂无描述' }}</p>
      </div>
      <t-button theme="primary" variant="outline" @click="$emit('export')">导出 Excel</t-button>
    </div>
    <t-row :gutter="[16, 16]" class="financial-summary" align="stretch">
      <t-col v-for="item in summaryCards" :key="item.label" :xs="12" :sm="6" :xl="4">
        <div class="financial-card" :class="`financial-card--${item.tone}`">
          <div class="financial-card__head">
            <span class="financial-card__icon"><t-icon :name="item.icon" /></span>
            <t-tag size="small" variant="light" :theme="item.tone === 'danger' ? 'danger' : item.tone === 'success' ? 'success' : 'primary'">
              {{ item.badge }}
            </t-tag>
          </div>
          <span class="financial-card__label">{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <div class="financial-card__meta">
            <span>{{ item.desc }}</span>
            <span>{{ item.percent }}%</span>
          </div>
          <div class="financial-card__track">
            <div class="financial-card__bar" :style="{ width: `${item.percent}%` }" />
          </div>
        </div>
      </t-col>
    </t-row>
    <t-card :bordered="false" class="financial-panel financial-panel--trend">
      <template #title>
        <div class="financial-panel-title">
          <span>资金趋势</span>
          <small>订单金额与累计资金走势</small>
        </div>
      </template>
      <analytics-chart type="line" :rows="trendRows" x-key="date" :value-keys="['amountYuan', 'cumulativeAmountYuan']" :value-labels="{ amountYuan: '订单金额', cumulativeAmountYuan: '累计资金' }" height="340px" />
    </t-card>
    <t-row :gutter="[16, 16]" align="stretch">
      <t-col :xs="12" :lg="6">
        <t-card :bordered="false" class="financial-panel">
          <template #title>
            <div class="financial-panel-title">
              <span>状态分布</span>
              <small>按订单状态拆解</small>
            </div>
          </template>
          <div class="financial-rank-list">
            <div v-for="item in statusRankRows" :key="item.label" class="financial-rank-row">
              <div class="financial-rank-row__top">
                <strong>{{ item.label }}</strong>
                <span>{{ item.count }} 单 · {{ item.percent }}%</span>
              </div>
              <div class="financial-rank-row__track">
                <div class="financial-rank-row__bar financial-rank-row__bar--status" :style="{ width: `${item.percent}%` }" />
              </div>
            </div>
          </div>
        </t-card>
      </t-col>
      <t-col :xs="12" :lg="6">
        <t-card :bordered="false" class="financial-panel">
          <template #title>
            <div class="financial-panel-title">
              <span>设备财务排行</span>
              <small>按设备收入贡献排序</small>
            </div>
          </template>
          <div class="financial-rank-list">
            <div v-for="item in deviceRankRows" :key="item.name" class="financial-rank-row">
              <div class="financial-rank-row__top">
                <strong>{{ item.name }}</strong>
                <span class="financial-money">{{ money(item.amountYuan) }}</span>
              </div>
              <div class="financial-rank-row__track">
                <div class="financial-rank-row__bar" :style="{ width: `${item.percent}%` }" />
              </div>
            </div>
          </div>
        </t-card>
      </t-col>
    </t-row>
  </t-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import AnalyticsChart from './AnalyticsChart.vue';

type ReportData = Record<string, any>;

const props = defineProps<{
  visible: boolean;
  reportData: ReportData;
  rangeText?: string;
}>();
const emit = defineEmits<{ 'update:visible': [value: boolean]; export: [] }>();

const visibleModel = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const toNumber = (value: unknown) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};
const money = (value: unknown) => `￥${toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const percent = (value: number, total: number) => Math.max(4, Math.min(100, Math.round((value / Math.max(total, 1)) * 100)));
const trendRows = computed(() => props.reportData?.trendRows || []);
const summaryCards = computed(() => {
  const summary = props.reportData?.summary || {};
  const totalRevenue = toNumber(summary.totalRevenueYuan);
  const totalPaid = toNumber(summary.totalPaidYuan);
  const totalDeposit = toNumber(summary.totalDepositYuan);
  const avgOrderValue = toNumber(summary.avgOrderValueYuan);
  const activeRentals = toNumber(summary.activeRentals);
  const totalRentDays = toNumber(summary.totalRentDays);
  return [
    { label: '总收入', value: money(totalRevenue), icon: 'money', tone: 'primary', badge: '收入', desc: '筛选范围收入', percent: 100 },
    { label: '已付款', value: money(totalPaid), icon: 'wallet', tone: 'success', badge: '实付', desc: '占总收入', percent: percent(totalPaid, totalRevenue) },
    { label: '押金规模', value: money(totalDeposit), icon: 'secured', tone: 'warning', badge: '押金', desc: '占总收入', percent: percent(totalDeposit, totalRevenue) },
    { label: '平均客单价', value: money(avgOrderValue), icon: 'chart', tone: 'primary', badge: '客单', desc: '相对总收入', percent: percent(avgOrderValue, totalRevenue) },
    { label: '活跃租赁数', value: `${activeRentals} 单`, icon: 'time', tone: 'success', badge: '履约', desc: '活跃订单占比', percent: percent(activeRentals, activeRentals + 1) },
    { label: '总租赁天数', value: `${totalRentDays} 天`, icon: 'calendar', tone: 'primary', badge: '租期', desc: '租期规模', percent: 100 },
  ];
});
const statusRankRows = computed(() => {
  const rows = props.reportData?.statusRows || [];
  const total = rows.reduce((sum: number, item: ReportData) => sum + toNumber(item.orderCount), 0);
  return rows.map((item: ReportData) => ({
    label: item.statusLabel || '未知状态',
    count: toNumber(item.orderCount),
    percent: percent(toNumber(item.orderCount), total),
  }));
});
const deviceRankRows = computed(() => {
  const rows = props.reportData?.deviceRows || [];
  const max = Math.max(...rows.map((item: ReportData) => toNumber(item.amountYuan)), 1);
  return rows
    .slice()
    .sort((left: ReportData, right: ReportData) => toNumber(right.amountYuan) - toNumber(left.amountYuan))
    .slice(0, 8)
    .map((item: ReportData) => ({
      name: item.name || '未命名设备',
      amountYuan: toNumber(item.amountYuan),
      percent: percent(toNumber(item.amountYuan), max),
    }));
});
</script>

<style scoped>
.financial-dialog :deep(.t-dialog) {
  border-radius: 6px;
}

.financial-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  color: var(--td-text-color-secondary);
}

.financial-header__title {
  color: #101828;
  font-size: 18px;
  font-weight: 700;
}

.financial-header p {
  margin: 8px 0 0;
}

.financial-summary,
.financial-panel {
  margin-bottom: 16px;
}

.financial-card {
  display: grid;
  gap: 10px;
  height: 100%;
  padding: 18px;
  border: 0;
  border-radius: 6px;
  background: #fff;
  box-shadow: none;
}

.financial-card--primary {
  box-shadow: inset 0 0 0 1px #dbe4ff;
}

.financial-card--success {
  box-shadow: inset 0 0 0 1px #d1fadf;
}

.financial-card--warning {
  box-shadow: inset 0 0 0 1px #fedf89;
}

.financial-card__head,
.financial-card__meta,
.financial-rank-row__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.financial-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--td-brand-color);
  background: #eef4ff;
  border-radius: 6px;
}

.financial-card__label,
.financial-card__meta {
  color: #667085;
  font-size: 13px;
}

.financial-card strong {
  color: #101828;
  font-size: 26px;
  line-height: 1.15;
}

.financial-card__track,
.financial-rank-row__track {
  height: 9px;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 999px;
}

.financial-card__bar,
.financial-rank-row__bar {
  height: 100%;
  background: linear-gradient(90deg, #465fff 0%, #12b76a 100%);
  border-radius: inherit;
}

.financial-panel {
  overflow: hidden;
  border-radius: 6px;
  box-shadow: none;
}

.financial-panel-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.financial-panel-title span {
  color: #101828;
  font-weight: 700;
}

.financial-panel-title small {
  color: #667085;
}

.financial-rank-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 300px;
}

.financial-rank-row__top {
  margin-bottom: 8px;
  color: #667085;
}

.financial-rank-row__top strong {
  max-width: 70%;
  overflow: hidden;
  color: #101828;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.financial-rank-row__bar--status {
  background: linear-gradient(90deg, #465fff 0%, #84caff 100%);
}

.financial-money {
  color: #2f54eb;
  font-weight: 700;
}
</style>
