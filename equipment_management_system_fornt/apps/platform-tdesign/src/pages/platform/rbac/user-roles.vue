<template>
  <div class="platform-page platform-user-role-layout">
    <platform-page-card title="角色列表">
      <template #actions>
        <t-button theme="default" variant="outline" @click="resetRoleQuery">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-input v-model="roleQuery.keyword" class="platform-search" clearable placeholder="搜索角色" />
      </template>

      <t-table
        row-key="id"
        :data="roles"
        :columns="roleColumns"
        :hover="true"
        :loading="roleLoading"
        :active-row-keys="selectedRole ? [selectedRole.id] : []"
        active-row-type="single"
        :pagination="rolePagination"
        :disable-data-page="true"
        table-layout="fixed"
        @row-click="onRoleRowClick"
        @page-change="onRolePageChange"
      >
        <template #enabled="{ row }">
          <t-tag :theme="row.enabled === 1 ? 'success' : 'default'" variant="light">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </t-tag>
        </template>
      </t-table>
    </platform-page-card>

    <platform-page-card :title="userListTitle">
      <template #actions>
        <t-button theme="default" variant="outline" @click="resetUserQuery">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-input v-model="userQuery.userName" class="platform-filter" clearable placeholder="账号" />
        <t-input v-model="userQuery.realName" class="platform-filter" clearable placeholder="姓名" />
        <t-input v-model="userQuery.loginAddress" class="platform-filter" clearable placeholder="最后登录地点" />
      </template>

      <t-table
        row-key="id"
        :data="users"
        :columns="userColumns"
        :hover="true"
        :loading="userLoading"
        :active-row-keys="selectedUser ? [selectedUser.id] : []"
        active-row-type="single"
        :pagination="userPagination"
        :disable-data-page="true"
        table-layout="fixed"
        @row-click="onUserRowClick"
        @page-change="onUserPageChange"
      >
        <template #userSex="{ row }">{{ formatSex(row.userSex) }}</template>
        <template #roleNames="{ row }">
          <t-tag v-if="row.roleNames" theme="primary" variant="light">{{ row.roleNames }}</t-tag>
          <span v-else>-</span>
        </template>
      </t-table>

      <div v-if="selectedUser" class="platform-user-detail">
        <div class="platform-detail-title">
          <div>
            <strong>{{ selectedUser.userName }}</strong>
            <div class="platform-muted">{{ selectedUser.realName || '未维护姓名' }}</div>
          </div>
          <t-button theme="primary" @click="saveUserRoles">保存角色</t-button>
        </div>

        <div class="platform-user-info-grid">
          <div><span>用户 ID</span><strong>{{ selectedUser.id }}</strong></div>
          <div><span>手机号</span><strong>{{ selectedUser.userPhoneNum || '-' }}</strong></div>
          <div><span>性别</span><strong>{{ formatSex(selectedUser.userSex) }}</strong></div>
          <div><span>创建时间</span><strong>{{ selectedUser.creatUserDate || '-' }}</strong></div>
          <div><span>最后登录</span><strong>{{ selectedUser.loginTime || '-' }}</strong></div>
          <div><span>登录 IP</span><strong>{{ selectedUser.loginIp || '-' }}</strong></div>
          <div><span>登录地点</span><strong>{{ selectedUser.loginAddress || '-' }}</strong></div>
          <div><span>UUID</span><strong>{{ selectedUser.uuid || '-' }}</strong></div>
          <div><span>请求状态</span><strong>{{ selectedUser.isNoRequest === 1 ? '禁止请求' : '允许请求' }}</strong></div>
        </div>

        <t-checkbox-group v-model="selectedUserRoleIds" class="platform-role-checkboxes">
          <t-checkbox v-for="role in allRoles" :key="role.id" :value="role.id" :disabled="role.enabled !== 1">
            {{ role.roleName }} / {{ role.roleCode }}
          </t-checkbox>
        </t-checkbox-group>
      </div>
      <t-alert v-else theme="warning" message="请选择角色下的用户后维护角色绑定" />
    </platform-page-card>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PageInfo, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { fetchRbacUserRoleIds, listPlatformUsers, listRbacRoles, saveRbacUserRoles } from '@/api/platform';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import { resolveData, resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';

defineOptions({
  name: 'PlatformRbacUserRoles',
});

const roleColumns: PrimaryTableCol<TableRowData>[] = [
  { title: '角色', colKey: 'roleName', minWidth: 130, fixed: 'left' },
  { title: '编码', colKey: 'roleCode', minWidth: 130, ellipsis: true },
  { title: '状态', colKey: 'enabled', width: 80, align: 'center' },
];

const userColumns: PrimaryTableCol<TableRowData>[] = [
  { title: '账号', colKey: 'userName', minWidth: 140, fixed: 'left' },
  { title: '姓名', colKey: 'realName', minWidth: 120 },
  { title: '手机号', colKey: 'userPhoneNum', minWidth: 130 },
  { title: '性别', colKey: 'userSex', width: 80, align: 'center' },
  { title: 'RBAC 角色', colKey: 'roleNames', minWidth: 180, ellipsis: true },
  { title: '创建时间', colKey: 'creatUserDate', minWidth: 170 },
  { title: '最后登录', colKey: 'loginTime', minWidth: 170 },
  { title: '登录 IP', colKey: 'loginIp', minWidth: 130 },
  { title: '登录地点', colKey: 'loginAddress', minWidth: 180, ellipsis: true },
];

const roles = ref<AnyRecord[]>([]);
const allRoles = ref<AnyRecord[]>([]);
const users = ref<AnyRecord[]>([]);
const roleLoading = ref(false);
const userLoading = ref(false);
const selectedRole = ref<AnyRecord | null>(null);
const selectedUser = ref<AnyRecord | null>(null);
const selectedUserRoleIds = ref<Array<string | number>>([]);
const roleQuery = reactive({ keyword: '' });
const userQuery = reactive({ userName: '', realName: '', loginAddress: '' });
const rolePagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });
const userPagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const userListTitle = computed(() => (selectedRole.value ? `${selectedRole.value.roleName} - 用户列表` : '用户列表'));

const loadAllRoles = async () => {
  const res = await listRbacRoles({ page: 1, pageSize: 1000 });
  allRoles.value = resolvePage<AnyRecord>(res).list;
};

const loadRoles = async () => {
  roleLoading.value = true;
  try {
    const res = await listRbacRoles({
      ...roleQuery,
      page: rolePagination.value.current,
      pageSize: rolePagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    roles.value = page.list;
    rolePagination.value.current = page.current;
    rolePagination.value.pageSize = page.pageSize;
    rolePagination.value.total = page.total;
    if (!roles.value.some((role) => role.id === selectedRole.value?.id)) {
      await selectRole(roles.value[0] || null);
    }
  } finally {
    roleLoading.value = false;
  }
};

const loadUsers = async () => {
  userLoading.value = true;
  try {
    const res = await listPlatformUsers({
      ...userQuery,
      roleId: selectedRole.value?.id,
      page: userPagination.value.current,
      pageSize: userPagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    users.value = page.list;
    userPagination.value.current = page.current;
    userPagination.value.pageSize = page.pageSize;
    userPagination.value.total = page.total;
    if (!users.value.some((user) => user.id === selectedUser.value?.id)) {
      await selectUser(users.value[0] || null);
    }
  } finally {
    userLoading.value = false;
  }
};

const selectRole = async (role: AnyRecord | null) => {
  selectedRole.value = role;
  selectedUser.value = null;
  selectedUserRoleIds.value = [];
  userPagination.value.current = 1;
  await loadUsers();
};

const selectUser = async (user: AnyRecord | null) => {
  selectedUser.value = user;
  selectedUserRoleIds.value = [];
  if (!user) return;
  const res = await fetchRbacUserRoleIds(user.id);
  selectedUserRoleIds.value = resolveData<Array<string | number>>(res, []);
};

const onRoleRowClick = (context: { row: AnyRecord }) => {
  selectRole(context.row);
};

const onUserRowClick = (context: { row: AnyRecord }) => {
  selectUser(context.row);
};

const onRolePageChange = (pageInfo: PageInfo) => {
  rolePagination.value.current = pageInfo.current;
  rolePagination.value.pageSize = pageInfo.pageSize;
  loadRoles();
};

const onUserPageChange = (pageInfo: PageInfo) => {
  userPagination.value.current = pageInfo.current;
  userPagination.value.pageSize = pageInfo.pageSize;
  loadUsers();
};

const searchRoles = () => {
  rolePagination.value.current = 1;
  loadRoles();
};

const searchUsers = () => {
  userPagination.value.current = 1;
  loadUsers();
};

const { pauseAutoQuery: pauseRoleAutoQuery } = useAutoQuery(roleQuery, searchRoles);
const { pauseAutoQuery: pauseUserAutoQuery } = useAutoQuery(userQuery, searchUsers);

const resetRoleQuery = () =>
  pauseRoleAutoQuery(() => {
    roleQuery.keyword = '';
    return searchRoles();
  });

const resetUserQuery = () =>
  pauseUserAutoQuery(() => {
    userQuery.userName = '';
    userQuery.realName = '';
    userQuery.loginAddress = '';
    return searchUsers();
  });

const saveUserRoles = async () => {
  if (!selectedUser.value) return;
  userLoading.value = true;
  try {
    await saveRbacUserRoles({
      userId: selectedUser.value.id,
      roleIds: selectedUserRoleIds.value,
    });
    MessagePlugin.success('用户角色已保存');
    await loadAllRoles();
    await loadUsers();
  } finally {
    userLoading.value = false;
  }
};

const formatSex = (value: unknown) => {
  if (value === 0) return '男';
  if (value === 1) return '女';
  return '-';
};

onMounted(async () => {
  await loadAllRoles();
  await loadRoles();
});
</script>
