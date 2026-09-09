<template>
  <t-tag :theme="theme" variant="light">{{ text }}</t-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  value?: string | number | boolean;
  map?: Record<string, string>;
}>();

const successValues = new Set(['SUCCESS', 'FINISHED', 'DELIVERED', 'PAID', 'UP', 'ENABLE', 'true', '1']);
const warningValues = new Set(['WAIT_PAY', 'CREATED', 'PENDING', 'PROCESSING', 'SYNCING']);
const dangerValues = new Set(['FAILED', 'CLOSED', 'REJECTED', 'DOWN', 'DISABLE', 'false', '0']);

const raw = computed(() => String(props.value ?? ''));
const text = computed(() => props.map?.[raw.value] || raw.value || '-');
const theme = computed(() => {
  if (successValues.has(raw.value)) return 'success';
  if (warningValues.has(raw.value)) return 'warning';
  if (dangerValues.has(raw.value)) return 'danger';
  return 'primary';
});
</script>
