<template>
  <div class="platform-page">
    <platform-page-card title="用户管理">
      <template #actions>
        <t-button v-if="canCreate" theme="primary" @click="openUserDialog()">
          <template #icon><t-icon name="user-add" /></template>
          新增用户
        </t-button>
        <t-button theme="default" variant="outline" @click="resetQuery">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-input v-model="query.userName" class="platform-filter" clearable placeholder="账号" />
        <t-input v-model="query.realName" class="platform-filter" clearable placeholder="姓名" />
        <t-input v-model="query.userPhoneNum" class="platform-filter" clearable placeholder="手机号" />
        <t-select v-model="query.userSex" class="platform-filter" clearable placeholder="性别">
          <t-option label="男" :value="0" />
          <t-option label="女" :value="1" />
        </t-select>
      </template>

      <t-table
        row-key="id"
        :data="users"
        :columns="columns"
        :hover="true"
        :loading="loading"
        :pagination="pagination"
        :disable-data-page="true"
        table-layout="fixed"
        @page-change="onPageChange"
      >
        <template #userSex="{ row }">{{ formatSex(row.userSex) }}</template>
        <template #requestStatus="{ row }">
          <t-tag :theme="row.isNoRequest === 1 ? 'danger' : 'success'" variant="light">
            {{ row.isNoRequest === 1 ? '禁止请求' : '允许请求' }}
          </t-tag>
        </template>
        <template #roleNames="{ row }">
          <span>{{ row.roleNames || '-' }}</span>
        </template>
        <template #op="{ row }">
          <div class="platform-table-actions">
            <platform-table-action v-if="canUpdate" icon="edit" tooltip="编辑" @click="openUserDialog(row)" />
            <platform-table-action v-if="canAuthorize" icon="user-checked" tooltip="授权" @click="openRoleDialog(row)" />
            <platform-table-action
              v-if="canResetPassword"
              icon="key"
              theme="warning"
              tooltip="重置密码"
              @click="openResetDialog(row)"
            />
            <platform-table-action v-if="canDelete" icon="delete" theme="danger" tooltip="删除" @click="removeUser(row)" />
          </div>
        </template>
      </t-table>
    </platform-page-card>

    <platform-form-dialog
      v-model:visible="userDialogVisible"
      :title="userForm.id ? '编辑用户' : '新增用户'"
      width="640px"
      label-width="96px"
      :model="userForm"
      @confirm="submitUser"
    >
      <t-form-item label="账号"><t-input v-model="userForm.userName" :disabled="Boolean(userForm.id)" /></t-form-item>
      <t-form-item v-if="!userForm.id" label="初始密码"><t-input v-model="userForm.userPwd" type="password" /></t-form-item>
      <t-form-item label="姓名"><t-input v-model="userForm.realName" /></t-form-item>
      <t-form-item label="手机号"><t-input v-model="userForm.userPhoneNum" /></t-form-item>
      <t-form-item label="性别">
        <t-radio-group v-model="userForm.userSex">
          <t-radio :value="0">男</t-radio>
          <t-radio :value="1">女</t-radio>
        </t-radio-group>
      </t-form-item>
      <t-form-item label="请求状态">
        <t-radio-group v-model="userForm.isNoRequest">
          <t-radio :value="0">允许请求</t-radio>
          <t-radio :value="1">禁止请求</t-radio>
        </t-radio-group>
      </t-form-item>
      <t-form-item v-if="canAuthorize" label="角色授权">
        <t-checkbox-group
          v-if="allRoles.length"
          v-model="userForm.roleIds"
          class="platform-role-checkboxes platform-role-checkboxes--dialog"
        >
          <t-checkbox v-for="role in allRoles" :key="role.id" :value="role.id" :disabled="role.enabled !== 1">
            {{ role.roleName }} / {{ role.roleCode }}
          </t-checkbox>
        </t-checkbox-group>
        <t-alert v-else theme="warning" message="暂无可分配角色" />
      </t-form-item>
    </platform-form-dialog>

    <platform-form-dialog
      v-model:visible="passwordDialogVisible"
      title="重置密码"
      width="520px"
      label-width="96px"
      :model="passwordForm"
      @confirm="submitPassword"
    >
      <t-form-item label="账号"><t-input v-model="passwordForm.userName" disabled /></t-form-item>
      <t-form-item label="新密码"><t-input v-model="passwordForm.userPwd" type="password" /></t-form-item>
    </platform-form-dialog>

    <platform-form-dialog
      v-model:visible="roleDialogVisible"
      title="用户授权"
      width="640px"
      label-width="96px"
      :model="roleForm"
      @confirm="submitRoleAuth"
    >
      <t-form-item label="账号"><t-input v-model="roleForm.userName" disabled /></t-form-item>
      <t-form-item label="姓名"><t-input v-model="roleForm.realName" disabled /></t-form-item>
      <t-form-item label="角色授权">
        <t-checkbox-group
          v-if="allRoles.length"
          v-model="roleForm.roleIds"
          class="platform-role-checkboxes platform-role-checkboxes--dialog"
        >
          <t-checkbox v-for="role in allRoles" :key="role.id" :value="role.id" :disabled="role.enabled !== 1">
            {{ role.roleName }} / {{ role.roleCode }}
          </t-checkbox>
        </t-checkbox-group>
        <t-alert v-else theme="warning" message="暂无可分配角色" />
      </t-form-item>
    </platform-form-dialog>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PageInfo, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  addPlatformUser,
  deletePlatformUsers,
  fetchPlatformUserRoleIds,
  listPlatformAssignableRoles,
  listPlatformUsers,
  resetPlatformUserPassword,
  savePlatformUserRoles,
  updatePlatformUser,
} from '@/api/platform';
import PlatformFormDialog from '@/pages/platform/components/PlatformFormDialog.vue';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import PlatformTableAction from '@/pages/platform/components/PlatformTableAction.vue';
import { readUserInfo, resolveData, resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';

defineOptions({
  name: 'PlatformUser',
});

const userInfo = ref<AnyRecord>(readUserInfo());
const query = reactive({ userName: '', realName: '', userPhoneNum: '', userSex: undefined as number | undefined });
const users = ref<AnyRecord[]>([]);
const loading = ref(false);
const userForm = ref<AnyRecord>({});
const passwordForm = ref<AnyRecord>({});
const roleForm = ref<AnyRecord>({});
const allRoles = ref<AnyRecord[]>([]);
const userDialogVisible = ref(false);
const passwordDialogVisible = ref(false);
const roleDialogVisible = ref(false);
const roleLoading = ref(false);
const pagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const buttonCodes = computed(() => (Array.isArray(userInfo.value.buttonCodes) ? userInfo.value.buttonCodes : []));
const hasButton = (code: string) => buttonCodes.value.includes(`platform:user:${code}`);
const canCreate = computed(() => hasButton('create'));
const canUpdate = computed(() => hasButton('update'));
const canDelete = computed(() => hasButton('delete'));
const canResetPassword = computed(() => hasButton('reset_password'));
const canAuthorize = computed(() => canUpdate.value);

const columns = computed<PrimaryTableCol<TableRowData>[]>(() => {
  const cols: PrimaryTableCol<TableRowData>[] = [
    { title: '账号', colKey: 'userName', minWidth: 140, fixed: 'left' },
    { title: '姓名', colKey: 'realName', minWidth: 120 },
    { title: '手机号', colKey: 'userPhoneNum', minWidth: 130 },
    { title: '性别', colKey: 'userSex', width: 80, align: 'center' },
    { title: 'RBAC 角色', colKey: 'roleNames', minWidth: 180, ellipsis: true },
    { title: '请求状态', colKey: 'requestStatus', width: 100, align: 'center' },
    { title: '创建时间', colKey: 'creatUserDate', minWidth: 170 },
    { title: '最后登录', colKey: 'loginTime', minWidth: 170 },
    { title: '登录 IP', colKey: 'loginIp', minWidth: 130 },
    { title: '登录地点', colKey: 'loginAddress', minWidth: 180, ellipsis: true },
  ];
  if (canUpdate.value || canDelete.value || canResetPassword.value || canAuthorize.value) {
    cols.push({ title: '操作', colKey: 'op', width: 180, fixed: 'right', align: 'center' });
  }
  return cols;
});

const loadAllRoles = async () => {
  roleLoading.value = true;
  try {
    const res = await listPlatformAssignableRoles({ page: 1, pageSize: 1000 });
    allRoles.value = resolvePage<AnyRecord>(res).list;
  } finally {
    roleLoading.value = false;
  }
};

const ensureRolesLoaded = async () => {
  if (allRoles.value.length) {
    return;
  }
  await loadAllRoles();
};

const loadUserRoleIds = async (userId: string | number) => {
  const res = await fetchPlatformUserRoleIds(userId);
  return normalizeRoleIds(resolveData<Array<string | number>>(res, []));
};

const loadUsers = async () => {
  loading.value = true;
  try {
    const res = await listPlatformUsers({
      ...query,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    users.value = page.list;
    pagination.value.current = page.current;
    pagination.value.pageSize = page.pageSize;
    pagination.value.total = page.total;
  } finally {
    loading.value = false;
  }
};

const searchUsers = () => {
  pagination.value.current = 1;
  loadUsers();
};

const { pauseAutoQuery } = useAutoQuery(query, searchUsers);

const resetQuery = () =>
  pauseAutoQuery(() => {
    query.userName = '';
    query.realName = '';
    query.userPhoneNum = '';
    query.userSex = undefined;
    return searchUsers();
  });

const onPageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadUsers();
};

const openUserDialog = async (row?: AnyRecord) => {
  if (canAuthorize.value) {
    await ensureRolesLoaded();
  }
  const roleIds = row?.id && canAuthorize.value ? await loadUserRoleIds(row.id) : [];
  userForm.value = row
    ? {
        id: row.id,
        userName: row.userName,
        realName: row.realName,
        userPhoneNum: row.userPhoneNum,
        userSex: row.userSex,
        isNoRequest: row.isNoRequest ?? 0,
        uuid: row.uuid || '-1',
        roleIds,
      }
    : { userSex: 0, isNoRequest: 0, uuid: '-1', roleIds: [] };
  userDialogVisible.value = true;
};

const toUserPayload = (form: AnyRecord) => {
  const payload = { ...form };
  delete payload.roleNames;
  delete payload.roleCodes;
  payload.roleIds = canAuthorize.value ? normalizeRoleIds(form.roleIds) : null;
  return payload;
};

const submitUser = async () => {
  if (!userForm.value.userName || (!userForm.value.id && !userForm.value.userPwd)) {
    MessagePlugin.warning('账号和密码不能为空');
    return;
  }
  loading.value = true;
  try {
    if (userForm.value.id) {
      await updatePlatformUser(toUserPayload(userForm.value));
      MessagePlugin.success('用户已更新，相关登录状态已失效');
    } else {
      await addPlatformUser(toUserPayload(userForm.value));
      MessagePlugin.success('用户已新增');
    }
    userDialogVisible.value = false;
    await loadUsers();
  } finally {
    loading.value = false;
  }
};

const openRoleDialog = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await ensureRolesLoaded();
    roleForm.value = {
      id: row.id,
      userName: row.userName,
      realName: row.realName,
      roleIds: await loadUserRoleIds(row.id),
    };
    roleDialogVisible.value = true;
  } finally {
    loading.value = false;
  }
};

const submitRoleAuth = async () => {
  if (!roleForm.value.id) return;
  loading.value = true;
  try {
    await savePlatformUserRoles({
      userId: roleForm.value.id,
      roleIds: normalizeRoleIds(roleForm.value.roleIds),
    });
    roleDialogVisible.value = false;
    MessagePlugin.success('用户授权已保存，相关登录状态已失效');
    await loadUsers();
  } finally {
    loading.value = false;
  }
};

const openResetDialog = (row: AnyRecord) => {
  passwordForm.value = { id: row.id, userName: row.userName, userPwd: '' };
  passwordDialogVisible.value = true;
};

const submitPassword = async () => {
  if (!passwordForm.value.id || !passwordForm.value.userPwd) {
    MessagePlugin.warning('新密码不能为空');
    return;
  }
  loading.value = true;
  try {
    await resetPlatformUserPassword({ id: passwordForm.value.id, userPwd: passwordForm.value.userPwd });
    passwordDialogVisible.value = false;
    MessagePlugin.success('密码已重置，该用户需重新登录');
  } finally {
    loading.value = false;
  }
};

const removeUser = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await deletePlatformUsers({ userIdList: [row.id] });
    MessagePlugin.success('用户已删除，相关登录状态已失效');
    await loadUsers();
  } finally {
    loading.value = false;
  }
};

const formatSex = (value: unknown) => {
  if (value === 0) return '男';
  if (value === 1) return '女';
  return '-';
};

const normalizeRoleIds = (value: unknown) => {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item));
};

onMounted(async () => {
  await Promise.all([loadAllRoles(), loadUsers()]);
});
</script>
