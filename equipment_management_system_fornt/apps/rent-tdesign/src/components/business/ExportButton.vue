<template>
  <permission-button :code="code" variant="outline" @click="handleExport">
    <template #icon><download-icon /></template>
    导出
  </permission-button>
</template>

<script setup lang="ts">
import { DownloadIcon } from 'tdesign-icons-vue-next';

import PermissionButton from './PermissionButton.vue';

const props = defineProps<{
  code?: string;
  rows: Record<string, any>[];
  filename: string;
}>();

const handleExport = () => {
  const headers = Array.from(new Set(props.rows.flatMap((row) => Object.keys(row))));
  const escape = (value: any) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  const csv = [headers.join(','), ...props.rows.map((row) => headers.map((key) => escape(row[key])).join(','))].join('\n');
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${props.filename}.csv`;
  link.click();
  URL.revokeObjectURL(url);
};
</script>
