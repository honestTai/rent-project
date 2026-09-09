<template>
  <t-tooltip :content="tooltip">
    <t-button
      :aria-label="tooltip"
      class="platform-table-action"
      :disabled="disabled"
      shape="square"
      :theme="theme"
      variant="text"
      @click="handleClick"
    >
      <template #icon><t-icon :name="icon" /></template>
    </t-button>
  </t-tooltip>
</template>

<script setup lang="ts">
defineOptions({
  name: 'PlatformTableAction',
});

const props = withDefaults(
  defineProps<{
    tooltip: string;
    icon: string;
    theme?: 'primary' | 'danger' | 'warning' | 'default' | 'success';
    disabled?: boolean;
  }>(),
  {
    theme: 'primary',
    disabled: false,
  },
);

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();

const handleClick = (event: MouseEvent) => {
  event.stopPropagation();
  if (props.disabled) return;
  emit('click', event);
};
</script>
