<template>
  <div class="platform-profile-page">
    <section class="profile-header-panel">
      <div class="profile-identity">
        <div class="profile-avatar">
          {{ avatarText }}
        </div>
        <div class="profile-copy">
          <span>My Profile</span>
          <h1>{{ displayName }}</h1>
          <p>{{ accountLine }}</p>
          <div class="profile-tags">
            <t-tag v-for="role in roleTags" :key="role" variant="light" theme="primary">{{ role }}</t-tag>
            <t-tag v-if="!roleTags.length" variant="light">未配置角色</t-tag>
          </div>
        </div>
      </div>
      <div class="profile-actions">
        <t-button theme="primary" @click="passwordDialogVisible = true">
          <template #icon><t-icon name="lock-on" /></template>
          修改密码
        </t-button>
      </div>
    </section>

    <section class="profile-info-panel">
      <div class="profile-section-title">
        <span>Personal Information</span>
        <strong>个人信息</strong>
      </div>
      <div class="profile-info-grid">
        <div v-for="item in userInfoItems" :key="item.label" class="profile-info-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section class="profile-info-panel">
      <div class="profile-section-title">
        <span>Permission Scope</span>
        <strong>系统权限</strong>
      </div>
      <div class="profile-system-grid">
        <div
          v-for="item in systemCards"
          :key="item.key"
          class="profile-system-card"
          :class="{ 'is-enabled': item.enabled }"
        >
          <div class="profile-system-card__icon">
            <t-icon :name="item.icon" />
          </div>
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.enabled ? item.enabledText : '暂无访问权限' }}</span>
          </div>
          <t-tag :theme="item.enabled ? 'success' : 'default'" variant="light">
            {{ item.enabled ? '已授权' : '未授权' }}
          </t-tag>
        </div>
      </div>
    </section>

    <section class="profile-business-grid">
      <article v-if="hasAlipayPermission" class="profile-business-card">
        <div class="business-card-head">
          <div>
            <span>Alipay Rental</span>
            <strong>支付宝租赁小程序后台台账</strong>
          </div>
          <t-tag variant="light" theme="primary">个人台账</t-tag>
        </div>
        <profile-timeline :rows="alipayLogs" :loading="loading.alipay" empty-text="暂无个人台账记录" />
      </article>

    </section>

    <section v-if="!hasAnyBusinessPermission" class="profile-info-panel profile-empty-panel">
      <t-empty description="当前账号暂无业务系统权限" />
    </section>

    <t-dialog v-model:visible="passwordDialogVisible" header="修改密码" width="520px" @confirm="submitPassword">
      <t-form :data="passwordForm" label-align="right" label-width="96px">
        <t-form-item label="原密码">
          <t-input v-model="passwordForm.oldPassword" type="password" clearable placeholder="请输入当前密码" />
        </t-form-item>
        <t-form-item label="新密码">
          <t-input v-model="passwordForm.newPassword" type="password" clearable placeholder="请输入新密码" />
        </t-form-item>
        <t-form-item label="确认密码">
          <t-input v-model="passwordForm.confirmPassword" type="password" clearable placeholder="请再次输入新密码" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="tsx">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, defineComponent, h, onMounted, reactive, ref, type PropType } from 'vue';
import { useRouter } from 'vue-router';

import {
  changeCurrentUserPassword,
  fetchAlipayPersonalLedgerLogs,
  fetchCurrentRbacPermissions,
  fetchCurrentUserProfile,
} from '@/api/platform';
import { readUserInfo, resolveData, type AnyRecord } from '@/pages/platform/utils';

import '../index.less';

defineOptions({
  name: 'PlatformProfile',
});

type PermissionSnapshot = {
  roleCodes: string[];
  pageCodes: string[];
  buttonCodes: string[];
  userId?: number;
};

type TimelineRow = {
  key: string;
  title: string;
  description: string;
  time: string;
  status: string;
  theme: 'success' | 'danger' | 'warning' | 'primary' | 'default';
};

const router = useRouter();
const profile = ref<AnyRecord>({});
const permission = ref<PermissionSnapshot>({ roleCodes: [], pageCodes: [], buttonCodes: [] });
const alipayLogs = ref<TimelineRow[]>([]);
const loading = reactive({ profile: false, alipay: false });
const passwordDialogVisible = ref(false);
const passwordSubmitting = ref(false);
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

const toArray = (value: unknown): string[] => (Array.isArray(value) ? value.map((item) => String(item)) : []);
const normalizePermission = (value: AnyRecord = {}): PermissionSnapshot => ({
  userId: value.userId,
  roleCodes: toArray(value.roleCodes),
  pageCodes: toArray(value.pageCodes),
  buttonCodes: toArray(value.buttonCodes),
});

const displayName = computed(() => {
  const user = profile.value;
  return String(user.realName || user.nickName || user.name || user.userName || user.username || '未命名用户');
});
const accountName = computed(() => String(profile.value.userName || profile.value.username || profile.value.account || ''));
const accountLine = computed(() => {
  const phone = profile.value.userPhoneNum || profile.value.phone || '-';
  return `账号 ${accountName.value || '-'} · 手机 ${phone || '-'}`;
});
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase());
const roleTags = computed(() => {
  const names = String(profile.value.roleNames || '')
    .split('、')
    .map((item) => item.trim())
    .filter(Boolean);
  return names.length ? names : permission.value.roleCodes;
});

const userInfoItems = computed(() => [
  { label: '账号', value: accountName.value || '-' },
  { label: '姓名', value: displayName.value || '-' },
  { label: '手机号', value: profile.value.userPhoneNum || '-' },
  { label: '性别', value: profile.value.userSex === 0 ? '男' : profile.value.userSex === 1 ? '女' : '-' },
  { label: '创建时间', value: profile.value.creatUserDate || '-' },
  { label: '最近登录', value: profile.value.loginTime || '-' },
  { label: '登录 IP', value: profile.value.loginIp || '-' },
  { label: '登录地点', value: profile.value.loginAddress || '-' },
]);

const hasSystemPermission = (systemCode: string) => {
  const snapshot = permission.value;
  if (snapshot.roleCodes.includes('super_admin')) return true;
  return [...snapshot.pageCodes, ...snapshot.buttonCodes].some((code) => code.startsWith(`${systemCode}:`));
};

const hasAlipayPermission = computed(() => hasSystemPermission('alipay'));
const hasAnyBusinessPermission = computed(() => hasAlipayPermission.value);

const systemCards = computed(() => [
  {
    key: 'alipay',
    title: '支付宝租赁小程序后台',
    icon: 'mobile',
    enabled: hasAlipayPermission.value,
    enabledText: '可查看个人操作台账记录',
  },
]);

const formatDateTime = (value: unknown): string => {
  if (!value) return '-';
  if (typeof value === 'number') {
    const date = new Date(value > 100000000000 ? value : value * 1000);
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false });
  }
  const text = String(value);
  if (/^\d+$/.test(text)) return formatDateTime(Number(text));
  return text.replace('T', ' ').slice(0, 19);
};

const readValue = (row: AnyRecord, keys: string[]) => {
  for (const key of keys) {
    const value = row?.[key];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return '';
};

const compactText = (value: unknown, fallback = '-') => {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  if (!text) return fallback;
  return text.length > 96 ? `${text.slice(0, 96)}...` : text;
};

const resultTheme = (value: unknown): TimelineRow['theme'] => {
  const text = String(value || '').toUpperCase();
  if (text.includes('SUCCESS') || text.includes('成功')) return 'success';
  if (text.includes('FAIL') || text.includes('失败') || text.includes('ERROR')) return 'danger';
  if (text.includes('WARN') || text.includes('异常')) return 'warning';
  return 'default';
};

const normalizeRows = (value: any): AnyRecord[] => {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.records)) return value.records;
  if (Array.isArray(value?.list)) return value.list;
  if (Array.isArray(value?.data)) return value.data;
  return [];
};

const toTimelineRows = (rows: AnyRecord[]): TimelineRow[] =>
  rows.slice(0, 6).map((row, index) => {
    const title = readValue(row, ['operDesc', 'operType', 'operationType']);
    const description = `${readValue(row, ['orderNo']) || '-'} · ${readValue(row, ['statusBefore']) || '-'} -> ${
      readValue(row, ['statusAfter']) || '-'
    }`;
    const status = readValue(row, ['operResult', 'result', 'status']) || '-';
    return {
      key: `alipay-${readValue(row, ['id', 'logId']) || index}`,
      title: compactText(title, '操作记录'),
      description: compactText(description),
      time: formatDateTime(readValue(row, ['operTime', 'dateTime', 'date_time', 'createTime', 'createdAt', 'time'])),
      status: String(status),
      theme: resultTheme(status),
    };
  });

const loadAlipayData = async () => {
  loading.alipay = true;
  try {
    const data = await fetchAlipayPersonalLedgerLogs({
      page: 1,
      limit: 6,
      operator: accountName.value,
    });
    alipayLogs.value = toTimelineRows(normalizeRows(data));
  } finally {
    loading.alipay = false;
  }
};

const loadBusinessData = async () => {
  const tasks: Array<Promise<void>> = [];
  if (hasAlipayPermission.value) tasks.push(loadAlipayData());
  await Promise.allSettled(tasks);
};

const loadProfile = async () => {
  loading.profile = true;
  try {
    const cachedUser = readUserInfo();
    const [profileResult, permissionResult] = await Promise.allSettled([
      fetchCurrentUserProfile(),
      fetchCurrentRbacPermissions(),
    ]);
    profile.value =
      profileResult.status === 'fulfilled'
        ? { ...cachedUser, ...resolveData<AnyRecord>(profileResult.value, {}) }
        : cachedUser;
    permission.value =
      permissionResult.status === 'fulfilled'
        ? normalizePermission(resolveData<AnyRecord>(permissionResult.value, {}))
        : normalizePermission(cachedUser);
    localStorage.setItem('userInfo', JSON.stringify({ ...profile.value, ...permission.value }));
    await loadBusinessData();
  } finally {
    loading.profile = false;
  }
};

const submitPassword = async () => {
  if (passwordSubmitting.value) return;
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    MessagePlugin.warning('请完整填写密码信息');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    MessagePlugin.warning('两次输入的新密码不一致');
    return;
  }
  if (passwordForm.newPassword.length < 6) {
    MessagePlugin.warning('新密码至少 6 位');
    return;
  }
  passwordSubmitting.value = true;
  try {
    await changeCurrentUserPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    });
    MessagePlugin.success('密码已修改，请重新登录');
    passwordDialogVisible.value = false;
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    await router.replace('/login');
  } finally {
    passwordSubmitting.value = false;
  }
};

const ProfileTimeline = defineComponent({
  name: 'ProfileTimeline',
  props: {
    rows: {
      type: Array as PropType<TimelineRow[]>,
      default: (): TimelineRow[] => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
    emptyText: {
      type: String,
      default: '暂无记录',
    },
  },
  setup(props) {
    return () =>
      h(
        'div',
        { class: 'profile-timeline-wrap' },
        props.loading
          ? h('div', { class: 'profile-loading' }, '加载中...')
          : props.rows.length
            ? h(
                'div',
                { class: 'profile-timeline' },
                props.rows.map((item) =>
                  h('div', { key: item.key, class: 'profile-timeline-item' }, [
                    h('span', { class: ['profile-timeline-dot', `profile-timeline-dot--${item.theme}`] }),
                    h('div', { class: 'profile-timeline-content' }, [
                      h('div', { class: 'profile-timeline-content__head' }, [
                        h('strong', item.title),
                        h('span', item.time),
                      ]),
                      h('p', item.description),
                      h('em', { class: `profile-status profile-status--${item.theme}` }, item.status),
                    ]),
                  ]),
                ),
              )
            : h('div', { class: 'profile-empty-text' }, props.emptyText),
      );
  },
});

onMounted(loadProfile);
</script>

<style scoped lang="less">
.platform-profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 88px);
  padding: 20px;
  background: #f7f9fc;
}

.profile-header-panel,
.profile-info-panel,
.profile-business-card {
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(16 24 40 / 4%);
}

.profile-header-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
}

.profile-identity {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.profile-avatar {
  display: flex;
  flex: 0 0 88px;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  border: 1px solid #d6e4ff;
  border-radius: 50%;
  color: #465fff;
  font-size: 32px;
  font-weight: 700;
  background: #eef4ff;
}

.profile-copy {
  min-width: 0;
}

.profile-copy span,
.profile-section-title span,
.business-card-head span {
  display: block;
  margin-bottom: 4px;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.profile-copy h1 {
  margin: 0;
  color: #101828;
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
}

.profile-copy p {
  margin: 6px 0 0;
  color: #344054;
  line-height: 22px;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.profile-actions {
  flex: 0 0 auto;
}

.profile-info-panel {
  padding: 20px 24px;
}

.profile-section-title,
.business-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.profile-section-title strong,
.business-card-head strong {
  color: #101828;
  font-size: 18px;
  line-height: 26px;
}

.profile-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}

.profile-info-item {
  min-width: 0;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.profile-info-item span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
}

.profile-info-item strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-system-grid,
.profile-business-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.profile-system-card {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.profile-system-card.is-enabled {
  border-color: #d6e4ff;
  background: #f9fbff;
}

.profile-system-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  color: #465fff;
  background: #eef4ff;
}

.profile-system-card strong,
.profile-system-card span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-system-card strong {
  color: #101828;
  font-size: 15px;
}

.profile-system-card span {
  margin-top: 4px;
  color: #667085;
  font-size: 13px;
}

.profile-business-card {
  min-width: 0;
  padding: 20px;
}

.profit-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.profit-summary > div {
  min-width: 0;
  padding: 16px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #f8fafc;
}

.profit-summary span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 13px;
}

.profit-summary strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 24px;
  line-height: 32px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}

:deep(.profile-timeline-wrap) {
  min-height: 180px;
}

:deep(.profile-loading),
:deep(.profile-empty-text) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  color: #667085;
  background: #f8fafc;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
}

:deep(.profile-timeline) {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:deep(.profile-timeline-item) {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
}

:deep(.profile-timeline-item:not(:last-child)::before) {
  position: absolute;
  top: 20px;
  bottom: -14px;
  left: 7px;
  width: 1px;
  background: #e4e7ec;
  content: '';
}

:deep(.profile-timeline-dot) {
  z-index: 1;
  width: 15px;
  height: 15px;
  margin-top: 4px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #98a2b3;
  box-shadow: 0 0 0 1px #e4e7ec;
}

:deep(.profile-timeline-dot--success) {
  background: #12b76a;
}

:deep(.profile-timeline-dot--danger) {
  background: #f04438;
}

:deep(.profile-timeline-dot--warning) {
  background: #f79009;
}

:deep(.profile-timeline-dot--primary) {
  background: #465fff;
}

:deep(.profile-timeline-content) {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fff;
}

:deep(.profile-timeline-content__head) {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

:deep(.profile-timeline-content__head strong) {
  min-width: 0;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.profile-timeline-content__head span) {
  flex: 0 0 auto;
  color: #667085;
  font-size: 12px;
}

:deep(.profile-timeline-content p) {
  margin: 8px 0 10px;
  color: #475467;
  font-size: 13px;
  line-height: 20px;
  word-break: break-word;
}

:deep(.profile-status) {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  color: #667085;
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
  background: #f2f4f7;
}

:deep(.profile-status--success) {
  color: #067647;
  background: #ecfdf3;
}

:deep(.profile-status--danger) {
  color: #b42318;
  background: #fef3f2;
}

:deep(.profile-status--warning) {
  color: #b54708;
  background: #fffaeb;
}

:deep(.profile-status--primary) {
  color: #3448d8;
  background: #eef4ff;
}

@media (max-width: 768px) {
  .platform-profile-page {
    padding: 12px;
  }

  .profile-header-panel,
  .profile-identity,
  .profile-section-title,
  .business-card-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-actions {
    width: 100%;
  }

  .profile-actions :deep(.t-button) {
    width: 100%;
  }

  .profile-system-card {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .profile-system-card :deep(.t-tag) {
    grid-column: 1 / -1;
    width: fit-content;
  }

  .profit-summary {
    grid-template-columns: 1fr;
  }
}
</style>
