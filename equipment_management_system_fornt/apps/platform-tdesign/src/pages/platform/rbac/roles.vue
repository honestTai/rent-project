<template>
  <div class="platform-page platform-split">
    <platform-page-card title="角色列表">
      <template #actions>
        <t-button theme="primary" @click="openRole()">
          <template #icon><t-icon name="add" /></template>
          新增角色
        </t-button>
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
        :data="pagedRoles"
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
        <template #op="{ row }">
          <div class="platform-table-actions">
            <platform-table-action icon="edit" tooltip="编辑" @click="openRole(row)" />
            <platform-table-action
              :disabled="row.builtIn === 1"
              icon="delete"
              theme="danger"
              tooltip="删除"
              @click="removeRole(row)"
            />
          </div>
        </template>
      </t-table>
    </platform-page-card>

    <t-card class="platform-card" :bordered="false">
      <template #header>
        <t-space align="center" style="justify-content: space-between; width: 100%">
          <span>权限授权树</span>
          <t-space>
            <t-button :disabled="!selectedRole" @click="refreshRolePermissions">刷新</t-button>
            <t-button :disabled="!selectedRole" @click="clearRolePermissions">清空</t-button>
            <t-button theme="primary" :disabled="!selectedRole" @click="saveRolePermissions">保存授权</t-button>
          </t-space>
        </t-space>
      </template>

      <div v-if="selectedRole" class="platform-detail-title">
        <div>
          <strong>{{ selectedRole.roleName }}</strong>
          <div style="color: var(--td-text-color-secondary); margin-top: 4px">{{ selectedRole.roleCode }}</div>
        </div>
        <t-tag theme="primary" variant="light">{{ selectedPermissionCount }} 个按钮权限</t-tag>
      </div>
      <t-alert v-else theme="warning" message="请选择左侧角色后维护授权" />
      <t-tree
        v-model="checkedPermissionKeys"
        :data="treeNodes"
        checkable
        expand-all
        value-mode="onlyLeaf"
        style="max-height: calc(100vh - 310px); overflow: auto"
        @change="syncSelectedPermissionCount"
      />
    </t-card>

    <platform-form-dialog
      v-model:visible="roleDialogVisible"
      title="角色"
      width="560px"
      label-width="90px"
      :model="roleForm"
      @confirm="submitRole"
    >
      <t-form-item label="角色编码"><t-input v-model="roleForm.roleCode" /></t-form-item>
      <t-form-item label="角色名称"><t-input v-model="roleForm.roleName" /></t-form-item>
      <t-form-item label="角色等级"><t-input-number v-model="roleForm.roleLevel" :min="1" :max="9999" /></t-form-item>
      <t-form-item label="说明"><t-textarea v-model="roleForm.roleDesc" :autosize="{ minRows: 3, maxRows: 5 }" /></t-form-item>
      <t-form-item label="启用"><platform-number-switch v-model="roleForm.enabled" /></t-form-item>
    </platform-form-dialog>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PageInfo, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  deleteRbacRole,
  fetchRbacPermissionTree,
  fetchRbacRoleButtonIds,
  listRbacRoles,
  saveRbacRole,
  saveRbacRoleButtons,
} from '@/api/platform';
import PlatformFormDialog from '@/pages/platform/components/PlatformFormDialog.vue';
import PlatformNumberSwitch from '@/pages/platform/components/PlatformNumberSwitch.vue';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import PlatformTableAction from '@/pages/platform/components/PlatformTableAction.vue';
import { resolveData, resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';
import { toPermissionTreeNodes } from './rbac-utils';

defineOptions({
  name: 'PlatformRbacRoles',
});

const roleColumns: PrimaryTableCol<TableRowData>[] = [
  { title: '角色', colKey: 'roleName', minWidth: 130, fixed: 'left' },
  { title: '编码', colKey: 'roleCode', minWidth: 130, ellipsis: true },
  { title: '等级', colKey: 'roleLevel', width: 80 },
  { title: '状态', colKey: 'enabled', width: 80, align: 'center' },
  { title: '操作', colKey: 'op', width: 104, fixed: 'right', align: 'center' },
];

const roles = ref<AnyRecord[]>([]);
const roleLoading = ref(false);
const treeNodes = ref<AnyRecord[]>([]);
const selectedRole = ref<AnyRecord | null>(null);
const selectedPermissionCount = ref(0);
const checkedPermissionKeys = ref<Array<string | number>>([]);
const roleQuery = reactive({ keyword: '' });
const roleForm = ref<AnyRecord>({});
const roleDialogVisible = ref(false);
const rolePagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const pagedRoles = computed(() => roles.value);

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
    if (!roles.value.some((role) => role.id === selectedRole.value?.id) && roles.value.length > 0) {
      selectRole(roles.value[0]);
    }
  } finally {
    roleLoading.value = false;
  }
};

const loadPermissionTree = async () => {
  const res = await fetchRbacPermissionTree();
  treeNodes.value = toPermissionTreeNodes(resolveData<AnyRecord[]>(res, []));
};

const selectRole = async (role: AnyRecord) => {
  selectedRole.value = role;
  if (!role) return;
  const res = await fetchRbacRoleButtonIds(role.id);
  const buttonIds = resolveData<Array<string | number>>(res, []);
  checkedPermissionKeys.value = buttonIds.map((id) => `button:${id}`);
  syncSelectedPermissionCount(checkedPermissionKeys.value);
};

const onRoleRowClick = (context: { row: AnyRecord }) => {
  selectRole(context.row);
};

const onRolePageChange = (pageInfo: PageInfo) => {
  rolePagination.value.current = pageInfo.current;
  rolePagination.value.pageSize = pageInfo.pageSize;
  loadRoles();
};

const searchRoles = () => {
  rolePagination.value.current = 1;
  loadRoles();
};

const { pauseAutoQuery } = useAutoQuery(roleQuery, searchRoles);

const resetRoleQuery = () =>
  pauseAutoQuery(() => {
    roleQuery.keyword = '';
    return searchRoles();
  });

const openRole = (row?: AnyRecord) => {
  roleForm.value = { enabled: 1, builtIn: 0, roleLevel: 100, ...(row || {}) };
  roleDialogVisible.value = true;
};

const submitRole = async () => {
  roleLoading.value = true;
  try {
    await saveRbacRole(roleForm.value);
    roleDialogVisible.value = false;
    MessagePlugin.success('保存成功');
    await loadRoles();
  } finally {
    roleLoading.value = false;
  }
};

const removeRole = async (row: AnyRecord) => {
  if (row.builtIn === 1) return;
  roleLoading.value = true;
  try {
    await deleteRbacRole(row.id);
    if (selectedRole.value?.id === row.id) {
      selectedRole.value = null;
    }
    MessagePlugin.success('删除成功');
    await loadRoles();
  } finally {
    roleLoading.value = false;
  }
};

const saveRolePermissions = async () => {
  if (!selectedRole.value) return;
  const buttonIds = checkedPermissionKeys.value
    .filter((key) => String(key).startsWith('button:'))
    .map((key) => Number(String(key).replace('button:', '')));
  await saveRbacRoleButtons({ roleId: selectedRole.value.id, buttonIds });
  MessagePlugin.success('授权已保存，该角色下已登录用户需重新登录');
};

const refreshRolePermissions = () => {
  if (selectedRole.value) {
    selectRole(selectedRole.value);
  }
};

const clearRolePermissions = () => {
  checkedPermissionKeys.value = [];
  syncSelectedPermissionCount(checkedPermissionKeys.value);
};

const syncSelectedPermissionCount = (value: Array<string | number>) => {
  checkedPermissionKeys.value = Array.isArray(value) ? value : checkedPermissionKeys.value;
  selectedPermissionCount.value = checkedPermissionKeys.value.filter((key) => String(key).startsWith('button:')).length;
};

onMounted(() => {
  loadRoles();
  loadPermissionTree();
});
</script>
