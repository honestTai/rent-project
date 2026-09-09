<template>
  <div class="alipay-query-actions">
    <t-button class="alipay-query-actions__primary" theme="primary" @click="emit('search')">
      {{ searchText }}
    </t-button>
    <t-button class="alipay-query-actions__secondary" variant="outline" @click="emit('reset')">
      {{ resetText }}
    </t-button>
    <slot />
    <t-button
      v-if="expandable"
      class="alipay-query-actions__expand"
      theme="primary"
      variant="text"
      @click="toggleExpanded"
    >
      {{ expanded ? '收起' : '展开' }}
      <template #suffix><t-icon :name="expanded ? 'chevron-up' : 'chevron-down'" /></template>
    </t-button>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  expanded?: boolean;
  expandable?: boolean;
  searchText?: string;
  resetText?: string;
}>(), {
  expanded: false,
  expandable: false,
  searchText: '查询',
  resetText: '重置',
});

const emit = defineEmits<{
  (event: 'search'): void;
  (event: 'reset'): void;
  (event: 'toggle', value: boolean): void;
  (event: 'update:expanded', value: boolean): void;
}>();

function toggleExpanded() {
  const next = !props.expanded;
  emit('update:expanded', next);
  emit('toggle', next);
}
</script>

<style scoped>
.alipay-query-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 36px;
  white-space: nowrap;
}

.alipay-query-actions :deep(.t-button) {
  height: 36px;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
  box-shadow: none !important;
  font-size: 14px;
  font-weight: 500;
}

.alipay-query-actions__primary {
  min-width: 72px;
}

.alipay-query-actions__secondary {
  min-width: 72px;
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
}

.alipay-query-actions__expand {
  min-width: auto;
  padding-right: 2px !important;
  padding-left: 2px !important;
  color: #0052d9;
  background: transparent !important;
  box-shadow: none !important;
}

.alipay-query-actions__expand :deep(.t-button__suffix) {
  margin-left: 4px;
}

@media (max-width: 720px) {
  .alipay-query-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
    white-space: normal;
  }
}
</style>
