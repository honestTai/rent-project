<template>
  <div :class="layoutCls">
    <t-head-menu :class="menuCls" :theme="menuTheme" expand-type="popup" :value="active">
      <template #logo>
        <span v-if="showLogo" class="header-logo-container" @click="handleNav('/platform/overview')">
          <img class="app-brand-logo" :src="brandLogoUrl" alt="" />
          <span class="header-logo-text">HONESTTAI 产品中台</span>
        </span>
        <div v-else class="header-operate-left">
          <t-button theme="default" shape="square" variant="text" @click="changeCollapsed">
            <t-icon class="collapsed-icon" name="view-list" />
          </t-button>
        </div>
      </template>
      <template v-if="layout !== 'side'" #default>
        <menu-content class="header-menu" :nav-data="menu" />
      </template>
      <template #operations>
        <div class="operations-container">
          <t-dropdown :min-column-width="120" trigger="click">
            <template #dropdown>
              <t-dropdown-item class="operations-dropdown-container-item" @click="handleProfile">
                <user-circle-icon />个人中心
              </t-dropdown-item>
              <t-dropdown-item class="operations-dropdown-container-item" @click="handleLogout">
                <poweroff-icon />退出登录
              </t-dropdown-item>
            </template>
            <t-button class="header-user-btn" theme="default" variant="text">
              <template #icon>
                <t-icon class="header-user-avatar" name="user-circle" />
              </template>
              <div class="header-user-account">{{ user.userInfo.name }}</div>
              <template #suffix><chevron-down-icon /></template>
            </t-button>
          </t-dropdown>
        </div>
      </template>
    </t-head-menu>
  </div>
</template>
<script setup lang="ts">
import { ChevronDownIcon, PoweroffIcon, UserCircleIcon } from 'tdesign-icons-vue-next';
import type { PropType } from 'vue';
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { prefix } from '@/config/global';
import { getActive } from '@/router';
import { usePermissionStore, useSettingStore, useUserStore } from '@/store';
import type { MenuRoute, ModeType } from '@/types/interface';

import MenuContent from './MenuContent.vue';

const brandLogoUrl = `${import.meta.env.BASE_URL}favicon.svg?v=20260712`;

const { theme, layout, showLogo, menu, isFixed, isCompact } = defineProps({
  theme: {
    type: String,
    default: 'light',
  },
  layout: {
    type: String,
    default: 'top',
  },
  showLogo: {
    type: Boolean,
    default: true,
  },
  menu: {
    type: Array as PropType<MenuRoute[]>,
    default: () => [],
  },
  isFixed: {
    type: Boolean,
    default: false,
  },
  isCompact: {
    type: Boolean,
    default: false,
  },
  maxLevel: {
    type: Number,
    default: 3,
  },
});

const router = useRouter();
const settingStore = useSettingStore();
const user = useUserStore();
const permissionStore = usePermissionStore();

const active = computed(() => getActive());

const layoutCls = computed(() => [`${prefix}-header-layout`]);

const menuCls = computed(() => {
  return [
    {
      [`${prefix}-header-menu`]: !isFixed,
      [`${prefix}-header-menu-fixed`]: isFixed,
      [`${prefix}-header-menu-fixed-side`]: layout === 'side' && isFixed,
      [`${prefix}-header-menu-fixed-side-compact`]: layout === 'side' && isFixed && isCompact,
    },
  ];
});
const menuTheme = computed(() => theme as ModeType);

const changeCollapsed = () => {
  settingStore.updateConfig({
    isSidebarCompact: !settingStore.isSidebarCompact,
  });
};

const handleNav = (url: string) => {
  router.push(url);
};

const handleProfile = () => {
  router.push('/platform/profile');
};

const handleLogout = () => {
  user.logout();
  permissionStore.restoreRoutes();
  router.push({
    path: '/login',
  });
};
</script>
<style lang="less" scoped>
.@{starter-prefix}-header {
  &-menu-fixed {
    position: fixed;
    top: 0;
    z-index: 1001;

    :deep(.t-head-menu__inner) {
      padding-right: var(--td-comp-margin-xl);
    }

    &-side {
      left: 232px;
      right: 0;
      z-index: 10;
      width: auto;
      transition: all 0.3s;

      &-compact {
        left: 64px;
      }
    }
  }

  &-logo-container {
    cursor: pointer;
    display: inline-flex;
  }
}

.header-menu {
  flex: 1 1 auto;
  display: inline-flex;

  :deep(.t-menu__item) {
    min-width: unset;
  }
}

.operations-container {
  display: flex;
  align-items: center;

  .t-popup__reference {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .t-button {
    margin-left: var(--td-comp-margin-l);
  }
}

.header-operate-left {
  display: flex;
  align-items: normal;
  line-height: 0;
}

.header-logo-container {
  width: 188px;
  min-width: 188px;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: 24px;
  color: var(--td-text-color-primary);

  .t-logo {
    width: 100%;
    height: 100%;

    &:hover {
      cursor: pointer;
    }
  }

  &:hover {
    cursor: pointer;
  }
}

.header-logo-text {
  font-size: 18px;
  font-weight: 600;
  line-height: 26px;
  white-space: nowrap;
}

.header-user-account {
  display: inline-flex;
  align-items: center;
  color: var(--td-text-color-primary);
}

:deep(.t-head-menu__inner) {
  border-bottom: 1px solid var(--td-component-stroke);
}

.t-menu--light {
  .header-user-account {
    color: var(--td-text-color-primary);
  }
}

.t-menu--dark {
  .t-head-menu__inner {
    border-bottom: 1px solid var(--td-gray-color-10);
  }

  .header-user-account {
    color: rgb(255 255 255 / 55%);
  }
}

.operations-dropdown-container-item {
  width: 100%;
  display: flex;
  align-items: center;

  :deep(.t-dropdown__item-text) {
    display: flex;
    align-items: center;
  }

  .t-icon {
    font-size: var(--td-comp-size-xxxs);
    margin-right: var(--td-comp-margin-s);
  }

  :deep(.t-dropdown__item) {
    width: 100%;
    margin-bottom: 0;
  }

  &:last-child {
    :deep(.t-dropdown__item) {
      margin-bottom: 8px;
    }
  }
}
</style>
<!-- eslint-disable-next-line vue-scoped-css/enforce-style-type -->
<style lang="less">
.operations-dropdown-container-item {
  .t-dropdown__item-text {
    display: flex;
    align-items: center;
  }
}
</style>
