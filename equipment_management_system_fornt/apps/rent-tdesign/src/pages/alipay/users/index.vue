<template>
  <AlipayCleanListPage
    title="小程序用户"
    description="查询支付宝用户实名信息、手机号、封禁状态和账号访问权限。"
    :rows="rows"
    row-key="userId"
    :loading="loading"
    :pagination="pagination"
    content-title="用户列表"
    empty-title="暂无用户"
    @page-change="pageChange"
  >
    <template #filters>
      <t-input
        v-model="query.userUuid"
        placeholder="支付宝PID / OpenID"
        clearable
      />
      <t-input v-model="query.realName" placeholder="姓名" clearable />
      <t-input v-model="query.idCard" placeholder="身份证号" clearable />
    </template>

    <template #queryActions>
      <AlipayQueryActions @search="load" @reset="resetQuery" />
    </template>

    <template #contentActions>
      <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
        <template #icon><t-icon name="setting" /></template>
        显示设置
      </t-button>
    </template>

    <template #record="{ row }">
      <article class="user-record" :class="{ 'user-record--blocked': Number(row.isNoRequest || 0) !== 0 }">
        <div class="user-record__avatar">
          <t-image
            v-if="row.userAvatar"
            :src="resolveUploadAssetPath(row.userAvatar)"
            fit="cover"
            shape="round"
          >
            <template #error>
              <div class="avatar-fallback">无</div>
            </template>
          </t-image>
          <div v-else class="avatar-fallback">无</div>
        </div>
        <div class="user-record__main">
          <div class="user-record__top">
            <div class="user-record__title">
              <strong>{{ row.realName || row.userTitle || '-' }}</strong>
              <span class="user-record__id">
                <span>支付宝用户PID：</span>
                <t-tooltip v-if="userPid(row) !== '-'" :content="userPid(row)" placement="top-left" show-arrow>
                  <span class="user-record__id-value">{{ userPid(row) }}</span>
                </t-tooltip>
                <span v-else class="user-record__id-value">-</span>
              </span>
            </div>
            <t-tag :theme="userStatusTheme(row)" variant="light">{{ userStatusText(row) }}</t-tag>
          </div>
          <div v-if="visibleUserFields.length" class="user-record__meta">
            <div v-for="field in visibleUserFields" :key="field.key" class="user-record__meta-item">
              <span>{{ field.label }}</span>
              <t-tooltip v-if="field.value(row) !== '-'" :content="field.value(row)" placement="top-left" show-arrow>
                <strong>{{ field.value(row) }}</strong>
              </t-tooltip>
              <strong v-else>-</strong>
            </div>
          </div>
        </div>
        <div v-if="isUserActionVisible('toggleBlock')" class="user-record__actions">
          <permission-button
            :code="buttonCode('user', 'update')"
            size="small"
            :theme="Number(row.isNoRequest || 0) === 0 ? 'danger' : 'primary'"
            variant="text"
            @click="updateIsNoRequest(row)"
          >
            {{ Number(row.isNoRequest || 0) === 0 ? '封禁' : '解封' }}
          </permission-button>
        </div>
      </article>
    </template>

    <display-settings-dialog
      v-model:visible="displaySettingsVisible"
      title="小程序用户显示设置"
      :groups="displaySettingGroups"
      :model-value="displaySettings.state"
      @save="saveDisplaySettings"
    />
  </AlipayCleanListPage>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, ref } from 'vue';

import { rentApi, resolveUploadAssetPath, type AnyRecord } from '@/api/rent';
import PermissionButton from '@/components/business/PermissionButton.vue';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import { buttonCode, usePagedList } from '@/pages/alipay/shared';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';
import { useAutoQuery } from '@/utils/useAutoQuery';

const { query, rows, loading, pagination, load, search, reset, pageChange } = usePagedList(rentApi.getUserList, {
  role: '0',
  userUuid: '',
  realName: '',
  idCard: '',
});
const { pauseAutoQuery } = useAutoQuery(query, search);
const resetQuery = () => pauseAutoQuery(reset);

pagination.value.pageSizeOptions = [10, 50, 100, 200, 500, 1000];

type UserDisplayField = DisplaySettingItem & {
  value: (row: AnyRecord) => string;
};

const displayValue = (value: unknown) => {
  if (value === null || value === undefined) return '-';
  const text = String(value).trim();
  return text || '-';
};
const userPid = (row: AnyRecord) => displayValue(row.alipayUserId);

const userFieldDefinitions: UserDisplayField[] = [
  { key: 'alipayOpenId', label: '支付宝OpenID', group: '用户信息', value: (row) => displayValue(row.openid) },
  { key: 'localUserId', label: '本地用户ID', group: '用户信息', value: (row) => displayValue(row.userId) },
  { key: 'userTel', label: '手机号', group: '用户信息', value: (row) => displayValue(row.userTel) },
  { key: 'userTitle', label: '昵称', group: '用户信息', value: (row) => displayValue(row.userTitle) },
  { key: 'idCard', label: '身份证号', group: '实名信息', value: (row) => displayValue(row.idCard) },
];
const userActionDefinitions: DisplaySettingItem[] = [
  { key: 'toggleBlock', label: '封禁/解封', group: '操作按钮' },
];
const displaySettingsVisible = ref(false);
const displayDefinitions = computed(() => ({
  fields: userFieldDefinitions,
  actions: userActionDefinitions,
}));
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.users',
  version: 3,
  definitions: displayDefinitions,
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [
  {
    kind: 'fields',
    label: '字段',
    title: '用户列表字段',
    description: '控制用户卡片中的辅助字段显示和顺序。',
    items: userFieldDefinitions,
  },
  {
    kind: 'actions',
    label: '操作按钮',
    title: '用户操作按钮',
    description: '控制用户卡片操作按钮的显示。',
    items: userActionDefinitions,
  },
]);
const visibleUserFields = computed(() => displaySettings.visibleItems('fields', userFieldDefinitions));
const isUserActionVisible = (key: string) => displaySettings.state.actions.includes(key);

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  MessagePlugin.success('显示设置已保存');
}

const columns = [
  { title: '支付宝用户PID', colKey: 'alipayUserId', width: 180, ellipsis: true, align: 'center' },
  { title: '支付宝OpenID', colKey: 'openid', width: 250, ellipsis: true, align: 'center' },
  { title: '本地用户ID', colKey: 'userId', width: 100, align: 'center' },
  { title: '头像', colKey: 'userAvatar', width: 80, align: 'center' },
  { title: '手机号码', colKey: 'userTel', width: 150, align: 'center' },
  { title: '昵称', colKey: 'userTitle', minWidth: 150, ellipsis: true, align: 'center' },
  { title: '姓名', colKey: 'realName', width: 100, align: 'center' },
  { title: '身份证号', colKey: 'idCard', width: 180, ellipsis: true, align: 'center' },
  { title: '是否被封禁', colKey: 'isNoRequest', width: 120, align: 'center' },
  { title: '操作', colKey: 'operation', width: 220, fixed: 'right', align: 'center' },
];

const userStatusText = (row: AnyRecord) => Number(row.isNoRequest || 0) === 0 ? '正常访问' : '已封禁';
const userStatusTheme = (row: AnyRecord) => Number(row.isNoRequest || 0) === 0 ? 'success' : 'danger';

const updateIsNoRequest = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await rentApi.updateUserIsNoRequest({
      userUuid: row.uuid,
      isNoRequest: Number(row.isNoRequest || 0) === 0 ? 1 : 0,
    });
    MessagePlugin.success('用户状态已更新');
    await load();
  } finally {
    loading.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.user-record {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.user-record:hover {
  border-color: #d6e4ff;
  background: #fbfdff;
}

.user-record--blocked {
  border-color: #ffd8d2;
  background: #fffafa;
}

.user-record__avatar,
.avatar-fallback {
  width: 44px;
  height: 44px;
}

.user-record__avatar {
  overflow: hidden;
  border-radius: 50%;
}

.user-record__avatar :deep(.t-image__wrapper),
.user-record__avatar :deep(img) {
  width: 100%;
  height: 100%;
}

.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #667085;
  font-size: 12px;
  background: #f2f4f7;
  border-radius: 50%;
}

.user-record__main {
  min-width: 0;
}

.user-record__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.user-record__title {
  min-width: 0;
}

.user-record__title strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-record__title span {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-record__title .user-record__id {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  word-break: break-all;
}

.user-record__id-value {
  min-width: 0;
  font-weight: 600;
}

.user-record__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.user-record__meta-item {
  min-width: 0;
}

.user-record__meta span {
  display: block;
  color: #98a2b3;
  font-size: 12px;
  line-height: 16px;
}

.user-record__meta strong {
  display: block;
  overflow: visible;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  text-overflow: clip;
  white-space: normal;
  word-break: break-all;
}

.user-record__meta-item :deep(.t-popup__reference) {
  display: block;
  min-width: 0;
}

.user-record__actions {
  display: flex;
  justify-content: flex-end;
  min-width: 56px;
}

.user-record__actions :deep(.t-button) {
  min-height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 760px) {
  .user-record {
    grid-template-columns: 52px minmax(0, 1fr);
  }

  .user-record__actions {
    grid-column: 2 / -1;
    justify-content: flex-start;
  }

  .user-record__top,
  .user-record__meta {
    grid-template-columns: 1fr;
  }

  .user-record__top {
    display: grid;
  }
}
</style>
