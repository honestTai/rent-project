<template>
  <section class="clean-list-page">
    <div class="clean-list-page__header ant-pro-page-header">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <div v-if="$slots.headerActions" class="clean-list-page__header-actions">
        <slot name="headerActions" />
      </div>
    </div>

    <section
      class="clean-list-page__toolbar ant-pro-query-card"
      :class="{ 'clean-list-page__toolbar--expanded': filtersExpanded }"
    >
      <div class="clean-list-page__toolbar-title">查询条件</div>
      <div class="clean-list-page__toolbar-body">
        <div
          class="clean-list-page__filters"
        >
          <slot name="filters" />
        </div>
        <div class="clean-list-page__actions">
          <slot v-if="$slots.queryActions" name="queryActions" />
          <slot v-else name="actions" />
          <t-button
            v-if="filterExpandable"
            class="clean-list-page__expand-trigger"
            theme="primary"
            variant="text"
            @click="toggleFilters"
          >
            {{ filtersExpanded ? '收起' : '展开' }}
            <template #suffix><t-icon :name="filterToggleIcon" /></template>
          </t-button>
        </div>
      </div>
    </section>

    <section class="clean-list-page__content ant-pro-content-card">
      <div class="clean-list-page__content-toolbar">
        <h2>{{ contentTitle }}</h2>
        <div v-if="$slots.contentActions" class="clean-list-page__content-actions">
          <slot name="contentActions" />
        </div>
      </div>

      <t-loading class="clean-list-page__loading" :loading="loading" size="small">
        <slot v-if="$slots.content" name="content" />
        <template v-else>
          <div v-if="rows.length" class="clean-record-list">
            <slot v-for="(row, index) in rows" :key="recordKey(row, index)" name="record" :row="row" :index="index" />
          </div>
          <t-empty v-else :title="emptyTitle" :description="emptyDescription" />
        </template>
      </t-loading>

      <div v-if="pagination.total" class="clean-list-page__pagination">
        <t-pagination
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="pagination.pageSizeOptions || defaultPageSizeOptions"
          @change="handlePageChange"
        />
      </div>
    </section>

    <slot />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import type { AnyRecord } from '@/api/rent';

type PaginationInfo = {
  current: number;
  pageSize: number;
  total: number;
  pageSizeOptions?: number[];
};

const props = withDefaults(defineProps<{
  title: string;
  description: string;
  rows: AnyRecord[];
  rowKey: string;
  loading?: boolean;
  pagination: PaginationInfo;
  contentTitle?: string;
  filterExpandable?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
}>(), {
  loading: false,
  contentTitle: '数据列表',
  filterExpandable: false,
  emptyTitle: '暂无数据',
  emptyDescription: '调整筛选条件后再试',
});

const emit = defineEmits<{
  (event: 'page-change', value: { current: number; pageSize: number }): void;
}>();

const defaultPageSizeOptions = [10, 20, 50, 100];
const filtersExpanded = ref(false);
const filterToggleIcon = computed(() => filtersExpanded.value ? 'chevron-up' : 'chevron-down');

function toggleFilters() {
  filtersExpanded.value = !filtersExpanded.value;
}

function recordKey(row: AnyRecord, index: number) {
  return row?.[props.rowKey] ?? index;
}

function handlePageChange(pageInfo: { current?: number; pageSize?: number }) {
  emit('page-change', {
    current: Number(pageInfo.current || props.pagination.current || 1),
    pageSize: Number(pageInfo.pageSize || props.pagination.pageSize || 10),
  });
}
</script>

<style scoped>
.clean-list-page {
  min-height: calc(100vh - 160px);
}

.clean-list-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.clean-list-page__header h1 {
  margin: 0;
  color: #101828;
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
}

.clean-list-page__header p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.clean-list-page__header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.clean-list-page__toolbar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 24px;
  margin-bottom: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}

.clean-list-page__toolbar-title {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}

.clean-list-page__toolbar-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
}

.clean-list-page__filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 16px 24px;
  align-items: start;
  width: 100%;
  max-width: 100%;
}

.clean-list-page__filters :deep(.t-input),
.clean-list-page__filters :deep(.t-select) {
  width: 100%;
}

.clean-list-page__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 232px;
}

.clean-list-page__actions :deep(.alipay-query-actions) {
  justify-content: flex-end;
}

.clean-list-page__expand-trigger {
  min-width: auto;
  height: 36px;
  min-height: 36px;
  padding: 0 2px !important;
  color: #0052d9;
  font-size: 14px;
  font-weight: 500;
  background: transparent !important;
  box-shadow: none !important;
}

.clean-list-page__content {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}

.clean-list-page__content-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.clean-list-page__content-toolbar h2 {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.clean-list-page__content-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.clean-list-page__content-actions :deep(.t-button) {
  height: 36px;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.clean-list-page__content-actions :deep(.t-button--variant-outline),
.clean-list-page__content-actions :deep(.t-button--variant-base:not(.t-button--theme-danger)) {
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
  box-shadow: none;
}

.clean-list-page__content-actions :deep(.t-button--variant-outline:hover),
.clean-list-page__content-actions :deep(.t-button--variant-base:not(.t-button--theme-danger):hover) {
  color: #0052d9;
  border-color: #b7cdfd;
  background: #f8fbff;
}

.clean-list-page__loading {
  display: block;
}

.clean-record-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px 16px;
  border: 0;
  background: #fff;
}

.clean-list-page__pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

@media (max-width: 1280px) {
  .clean-list-page__filters {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
  }
}

@media (max-width: 720px) {
  .clean-list-page__header {
    display: grid;
    align-items: start;
  }

  .clean-list-page__filters {
    grid-template-columns: 1fr;
  }

  .clean-list-page__toolbar-body {
    grid-template-columns: 1fr;
  }

  .clean-list-page__actions,
  .clean-list-page__content-actions {
    justify-content: flex-start;
    min-width: 0;
  }
}
</style>
