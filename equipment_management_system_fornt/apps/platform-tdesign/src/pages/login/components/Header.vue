<template>
  <header class="login-header">
    <span class="logo">
      <img :src="brandLogoUrl" alt="" />
      HONESTTAI 产品中台
    </span>
  </header>
</template>
<script setup lang="ts">
const brandLogoUrl = `${import.meta.env.BASE_URL}favicon.svg?v=20260712`;
</script>
<style lang="less" scoped>
.login-header {
  padding: 0 var(--td-comp-paddingLR-xl);
  display: flex;
  justify-content: space-between;
  align-items: center;
  backdrop-filter: blur(10px);
  color: var(--td-text-color-primary);
  height: var(--td-comp-size-xxxl);

  .logo {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    color: var(--td-text-color-primary);
    font-size: 20px;
    font-weight: 600;

    img {
      width: 36px;
      height: 36px;
      object-fit: contain;
    }
  }
}
</style>
