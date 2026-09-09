<template>
  <t-form
    ref="form"
    class="item-container"
    :class="[`login-${type}`]"
    :data="formData"
    :rules="FORM_RULES"
    label-width="0"
    @submit="onSubmit"
  >
    <t-form-item name="account">
      <t-input v-model="formData.account" size="large" :placeholder="t('pages.login.input.account')">
        <template #prefix-icon>
          <t-icon name="user" />
        </template>
      </t-input>
    </t-form-item>

    <t-form-item name="password">
      <t-input
        v-model="formData.password"
        size="large"
        :type="showPsw ? 'text' : 'password'"
        clearable
        :placeholder="t('pages.login.input.password')"
      >
        <template #prefix-icon>
          <t-icon name="lock-on" />
        </template>
        <template #suffix-icon>
          <t-icon :name="showPsw ? 'browse' : 'browse-off'" @click="showPsw = !showPsw" />
        </template>
      </t-input>
    </t-form-item>

    <div class="check-container remember-pwd">
      <t-checkbox v-model="formData.checked">{{ t('pages.login.remember') }}</t-checkbox>
    </div>

    <t-form-item class="btn-container">
      <t-button block size="large" type="submit"> {{ t('pages.login.signIn') }} </t-button>
    </t-form-item>

  </t-form>
</template>
<script setup lang="ts">
import type { FormInstanceFunctions, FormRule, SubmitContext } from 'tdesign-vue-next';
import { MessagePlugin } from 'tdesign-vue-next';
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { t } from '@/locales';
import { useUserStore } from '@/store';
import { redirectWithSsoPayload } from '@/utils/sso';

const userStore = useUserStore();

const INITIAL_DATA = {
  account: '',
  password: '',
  checked: false,
};

const FORM_RULES: Record<string, FormRule[]> = {
  account: [{ required: true, message: t('pages.login.required.account'), type: 'error' }],
  password: [{ required: true, message: t('pages.login.required.password'), type: 'error' }],
};

const type = ref('password');

const form = ref<FormInstanceFunctions>();
const formData = ref({ ...INITIAL_DATA });
const showPsw = ref(false);

const router = useRouter();
const route = useRoute();

const redirectAfterLogin = (redirectUrl: string, payload?: { token?: string; user?: Record<string, unknown> }) => {
  const nextUrl = redirectWithSsoPayload(redirectUrl, payload);
  if (!nextUrl) {
    return;
  }
  router.push(nextUrl);
};

const onSubmit = async (ctx: SubmitContext) => {
  if (ctx.validateResult === true) {
    try {
      const loginData = await userStore.login(formData.value);

      MessagePlugin.success('登录成功');
      const redirect = route.query.redirect as string;
      redirectAfterLogin(redirect || '/platform/overview', loginData);
    } catch (e) {
      console.log(e);
      MessagePlugin.error(e instanceof Error ? e.message : '登录失败');
    }
  }
};
</script>
<style lang="less" scoped>
@import '../index.less';
</style>
