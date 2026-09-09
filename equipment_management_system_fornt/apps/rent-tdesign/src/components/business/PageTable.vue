<template>
  <section class="page-table">
    <div class="page-table__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <t-space>
        <slot name="actions" />
      </t-space>
    </div>
    <slot name="query" />
    <t-table
      class="page-table__body"
      row-key="id"
      hover
      stripe
      :data="data"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :disable-data-page="true"
      table-layout="fixed"
      cell-empty-content="-"
      @page-change="$emit('page-change', $event)"
    >
      <template v-for="(_, name) in $slots" #[name]="slotProps">
        <slot :name="name" v-bind="slotProps" />
      </template>
    </t-table>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  data: Record<string, any>[];
  columns: Record<string, any>[];
  loading?: boolean;
  pagination?: Record<string, any>;
}>();

defineEmits<{
  'page-change': [pageInfo: Record<string, any>];
}>();
</script>

<style scoped>
.page-table {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-table__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-table__header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-table__header p {
  margin: 6px 0 0;
  color: var(--td-text-color-secondary);
}

.page-table__body {
  background: var(--td-bg-color-container);
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}
</style>
