<template>
  <div class="login-wrapper">
    <login-header />

    <div class="login-container">
      <div class="title-container">
        <h1 class="title margin-no">{{ t('pages.login.loginTitle') }}</h1>
        <h1 class="title">HONESTTAI 产品中台</h1>
        <div class="sub-title">
          <p class="tip">请输入账号密码登录中台管理系统</p>
        </div>
      </div>

      <login />
    </div>

    <app-footer class="login-site-footer" />
  </div>
</template>
<script setup lang="ts">
import { t } from '@/locales';
import AppFooter from '@/layouts/components/Footer.vue';

import LoginHeader from './components/Header.vue';
import Login from './components/Login.vue';

defineOptions({
  name: 'LoginIndex',
});
</script>
<style lang="less" scoped>
@import './index.less';
</style>
