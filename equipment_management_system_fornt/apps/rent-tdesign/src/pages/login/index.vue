<template>
  <t-loading text="正在跳转到中台统一登录" fullscreen />
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute } from 'vue-router';

import { buildPlatformLoginUrl, buildRentAppUrl } from '@/utils/gateway';

defineOptions({
  name: 'LoginRedirect',
});

const route = useRoute();

onMounted(() => {
  const redirect = typeof route.query.redirect === 'string' ? decodeURIComponent(route.query.redirect) : window.location.href;
  if (localStorage.getItem('token')) {
    window.location.href = buildRentAppUrl(redirect);
    return;
  }
  window.location.href = buildPlatformLoginUrl(redirect);
});
</script>
