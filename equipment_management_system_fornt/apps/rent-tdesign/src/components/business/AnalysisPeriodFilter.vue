<template>
  <div class="analysis-period-filter">
    <t-radio-group
      :model-value="periodType"
      class="analysis-period-filter__switch"
      variant="default-filled"
      size="small"
      @change="handlePeriodTypeChange"
    >
      <t-radio-button v-for="item in periodOptions" :key="item.value" :value="item.value">
        {{ item.label }}
      </t-radio-button>
    </t-radio-group>
    <t-date-picker
      :key="pickerMode"
      :model-value="targetDate"
      class="analysis-period-filter__picker"
      :mode="pickerMode"
      :placeholder="pickerPlaceholder"
      clearable
      value-type="YYYY-MM-DD"
      @change="handleTargetDateChange"
    />
    <t-button theme="primary" :loading="loading" class="analysis-period-filter__refresh" @click="$emit('refresh')">
      <template #icon><t-icon name="refresh" /></template>
      {{ refreshText }}
    </t-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type PeriodType = 'WEEK' | 'MONTH' | 'YEAR';

const props = withDefaults(
  defineProps<{
    periodType: PeriodType;
    targetDate: string;
    loading?: boolean;
    refreshText?: string;
  }>(),
  {
    loading: false,
    refreshText: '刷新',
  },
);

const emit = defineEmits<{
  (event: 'update:periodType', value: PeriodType): void;
  (event: 'update:targetDate', value: string): void;
  (event: 'period-change', value: PeriodType): void;
  (event: 'date-change', value: string): void;
  (event: 'refresh'): void;
}>();

const periodOptions: Array<{ label: string; value: PeriodType }> = [
  { label: '周', value: 'WEEK' },
  { label: '月', value: 'MONTH' },
  { label: '年', value: 'YEAR' },
];

const pickerMode = computed(() => ({ WEEK: 'week', MONTH: 'month', YEAR: 'year' }[props.periodType]));
const pickerPlaceholder = computed(() => ({ WEEK: '选择目标周', MONTH: '选择目标月', YEAR: '选择目标年' }[props.periodType]));

function formatYmd(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function parseDateText(value: string) {
  const match = String(value || '')
    .trim()
    .match(/^(\d{4})(?:-(\d{1,2}))?(?:-(\d{1,2}))?/);
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2] || 1) - 1, Number(match[3] || 1));
  return Number.isNaN(date.getTime()) ? null : date;
}

function normalizeTargetDate(periodType: PeriodType, value: string) {
  const date = parseDateText(value) || new Date();
  if (periodType === 'WEEK') {
    const day = date.getDay() || 7;
    const start = new Date(date);
    start.setDate(date.getDate() - day + 1);
    return formatYmd(start);
  }
  if (periodType === 'YEAR') return formatYmd(new Date(date.getFullYear(), 0, 1));
  return formatYmd(new Date(date.getFullYear(), date.getMonth(), 1));
}

function handlePeriodTypeChange(value: string | number | boolean) {
  const next = String(value || 'MONTH') as PeriodType;
  const targetDate = normalizeTargetDate(next, props.targetDate);
  emit('update:periodType', next);
  emit('update:targetDate', targetDate);
  emit('period-change', next);
}

function handleTargetDateChange(value: unknown) {
  const next = value ? normalizeTargetDate(props.periodType, String(value)) : '';
  emit('update:targetDate', next);
  emit('date-change', next);
}
</script>

<style scoped lang="less">
.analysis-period-filter {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.analysis-period-filter__picker {
  width: 178px;
  flex: 0 0 178px;
}

.analysis-period-filter__switch {
  position: relative;
  padding: 2px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #f9fafb;
  box-shadow: none;
}

.analysis-period-filter__switch :deep(.t-radio-button) {
  z-index: 1;
  min-width: 40px;
  height: 28px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: transparent !important;
  box-shadow: none !important;
  color: #475467;
  font-weight: 600;
  line-height: 28px;
}

.analysis-period-filter__switch :deep(.t-radio-group__bg-block) {
  border-radius: 6px;
  background: #465fff;
  box-shadow: none;
}

.analysis-period-filter__switch :deep(.t-radio-button.t-is-checked),
.analysis-period-filter__switch :deep(.t-radio-button.t-is-checked:hover) {
  color: #fff;
  background: transparent !important;
  box-shadow: none;
}

.analysis-period-filter__picker :deep(.t-input) {
  background: #fff;
  border-color: #d0d5dd;
  box-shadow: none;
}

.analysis-period-filter__picker :deep(.t-input--focused),
.analysis-period-filter__picker :deep(.t-input:hover) {
  border-color: #465fff;
  box-shadow: 0 0 0 3px rgb(70 95 255 / 12%);
}

.analysis-period-filter__refresh {
  box-shadow: none;
}

@media (max-width: 768px) {
  .analysis-period-filter {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .analysis-period-filter__picker {
    width: min(100%, 220px);
  }
}
</style>
