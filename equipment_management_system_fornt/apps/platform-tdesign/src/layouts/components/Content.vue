<template>
  <div v-if="!isRefreshing">
    <router-view v-if="!isFramePage" :key="route.fullPath" />
    <frame-page v-else />
  </div>

  <t-loading v-else />
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import FramePage from '@/layouts/frame/index.vue';
import { useTabsRouterStore } from '@/store';

const isRefreshing = computed(() => {
  const tabsRouterStore = useTabsRouterStore();
  const { refreshing } = tabsRouterStore;
  return refreshing;
});

const route = useRoute(); // 这个不能放到computed中，切换页面时会导致被缓存
const isFramePage = computed(() => {
  return !!route.meta?.frameSrc;
});
</script>
<style lang="less" scoped></style>
