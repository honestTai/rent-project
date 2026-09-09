<template>
  <t-dialog
    :visible="visible"
    :header="title"
    :width="width"
    :destroy-on-close="true"
    @update:visible="emit('update:visible', $event)"
    @confirm="emit('confirm')"
  >
    <t-form :data="model" label-align="right" :label-width="labelWidth">
      <slot />
    </t-form>
  </t-dialog>
</template>

<script setup lang="ts">
import type { PropType } from 'vue';

import type { AnyRecord } from '@/pages/platform/utils';

defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    required: true,
  },
  width: {
    type: String,
    default: '720px',
  },
  labelWidth: {
    type: String,
    default: '100px',
  },
  model: {
    type: Object as PropType<AnyRecord>,
    required: true,
  },
});

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'confirm'): void;
}>();
</script>
