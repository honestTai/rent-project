<template>
  <t-switch :model-value="normalizedValue" @update:model-value="emitValue" />
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0,
  },
});

const emit = defineEmits<{
  (event: 'update:modelValue', value: number): void;
}>();

const normalizedValue = computed(() => props.modelValue === 1);

const emitValue = (value: number | string | boolean) => {
  emit('update:modelValue', value === true ? 1 : 0);
};
</script>
