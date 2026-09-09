<template>
  <t-button v-if="visible" v-bind="$attrs">
    <template v-if="$slots.icon" #icon>
      <slot name="icon" />
    </template>
    <slot />
  </t-button>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import { usePermissionStore } from '@/store';

defineOptions({
  name: 'PermissionButton',
  inheritAttrs: false,
});

const props = defineProps<{
  code?: string;
}>();

const permissionStore = usePermissionStore();
const visible = computed(() => !props.code || permissionStore.hasButton(props.code));
</script>
