<template>
  <div class="query-panel">
    <t-form :data="modelValue" layout="inline" label-width="auto" @submit.prevent>
      <slot />
      <t-form-item>
        <t-space>
          <t-button variant="outline" @click="$emit('reset')">
            <template #icon><refresh-icon /></template>
            重置
          </t-button>
        </t-space>
      </t-form-item>
    </t-form>
  </div>
</template>

<script setup lang="ts">
import { RefreshIcon } from 'tdesign-icons-vue-next';

defineProps<{
  modelValue: Record<string, any>;
}>();

defineEmits<{
  search: [];
  reset: [];
}>();
</script>

<style scoped>
.query-panel {
  padding: 18px 24px;
  background: var(--td-bg-color-container);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  box-shadow: none;
}
</style>
