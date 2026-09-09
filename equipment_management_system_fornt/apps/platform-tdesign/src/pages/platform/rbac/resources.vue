<template>
  <div class="platform-page platform-split platform-rbac-resources">
    <platform-page-card title="系统权限树">
      <template #actions>
        <t-button theme="default" variant="outline" @click="resetResourceFilter">
          <template #icon><t-icon name="filter-clear" /></template>
          全部
        </t-button>
      </template>
      <template #filters>
        <t-input
          v-model="treeKeyword"
          class="platform-search"
          clearable
          placeholder="搜索系统 / 页面 / 按钮"
        />
      </template>
      <t-tree
        v-model:actived="activedKeys"
        :data="filteredTreeNodes"
        activable
        expand-all
        hover
        class="platform-tree-scroll"
        @click="onTreeClick"
      />
    </platform-page-card>

    <platform-page-card :title="detailTitle">
      <template #actions>
        <t-button theme="default" variant="outline" @click="resetResourceFilter">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-input
          v-model="resourceQuery.keyword"
          class="platform-search"
          clearable
          placeholder="搜索权限编码 / 接口"
        />
      </template>
      <t-table
        row-key="id"
        :data="permissionRows"
        :columns="columns"
        :hover="true"
        :loading="loading"
        :pagination="pagination"
        :disable-data-page="true"
        table-layout="fixed"
        @page-change="onPageChange"
      />
    </platform-page-card>
  </div>
</template>

<script setup lang="ts">
import type { PageInfo, PrimaryTableCol, TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { fetchRbacPermissionTree, listRbacPermissionButtons } from '@/api/platform';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import { resolveData, resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';
import { toPermissionTreeNodes } from './rbac-utils';

defineOptions({
  name: 'PlatformRbacResources',
});

const columns: PrimaryTableCol<TableRowData>[] = [
  { title: '系统', colKey: 'systemName', width: 130, fixed: 'left' },
  { title: '子菜单/页面', colKey: 'pageName', width: 160 },
  { title: '按钮', colKey: 'buttonName', width: 140 },
  { title: '权限编码', colKey: 'permissionCode', minWidth: 260, ellipsis: true },
  { title: '方法', colKey: 'apiMethod', width: 80 },
  { title: '接口路径', colKey: 'apiPath', minWidth: 240, ellipsis: true },
];

const treeNodes = ref<AnyRecord[]>([]);
const permissionRows = ref<AnyRecord[]>([]);
const activedKeys = ref<Array<string | number>>([]);
const selectedNode = ref<AnyRecord | null>(null);
const treeKeyword = ref('');
const loading = ref(false);
const resourceQuery = reactive({
  keyword: '',
  systemCode: '',
  pageCode: '',
  buttonId: undefined as number | undefined,
});
const pagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const detailTitle = computed(() => (selectedNode.value ? `权限明细 - ${selectedNode.value.label}` : '权限明细'));

const filteredTreeNodes = computed(() => filterTreeNodes(treeNodes.value, treeKeyword.value.trim()));

const loadPermissionTree = async () => {
  const res = await fetchRbacPermissionTree();
  treeNodes.value = toPermissionTreeNodes(resolveData<AnyRecord[]>(res, []));
};

const loadPermissionRows = async () => {
  loading.value = true;
  try {
    const res = await listRbacPermissionButtons({
      ...resourceQuery,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    permissionRows.value = page.list;
    pagination.value.current = page.current;
    pagination.value.pageSize = page.pageSize;
    pagination.value.total = page.total;
  } finally {
    loading.value = false;
  }
};

const onTreeClick = (context: AnyRecord) => {
  const node = context.node?.data || context.node || context;
  selectedNode.value = node;
  activedKeys.value = node.value ? [node.value] : [];
  resourceQuery.systemCode = node.systemCode || '';
  resourceQuery.pageCode = node.type === 'page' || node.type === 'button' ? node.pageCode || '' : '';
  resourceQuery.buttonId = node.type === 'button' ? Number(node.buttonId) : undefined;
  pagination.value.current = 1;
  loadPermissionRows();
};

const searchResourceKeyword = () => {
  resourceQuery.keyword = treeKeyword.value.trim();
  selectedNode.value = null;
  activedKeys.value = [];
  resourceQuery.systemCode = '';
  resourceQuery.pageCode = '';
  resourceQuery.buttonId = undefined;
  pagination.value.current = 1;
  loadPermissionRows();
};

const searchPermissionRows = () => {
  pagination.value.current = 1;
  loadPermissionRows();
};

const { pauseAutoQuery: pausePermissionAutoQuery } = useAutoQuery(() => resourceQuery.keyword, searchPermissionRows);
const { pauseAutoQuery: pauseTreeAutoQuery } = useAutoQuery(() => treeKeyword.value, () =>
  pausePermissionAutoQuery(searchResourceKeyword),
);

const resetResourceFilter = () =>
  pauseTreeAutoQuery(() =>
    pausePermissionAutoQuery(() => {
      treeKeyword.value = '';
      resourceQuery.keyword = '';
      selectedNode.value = null;
      activedKeys.value = [];
      resourceQuery.systemCode = '';
      resourceQuery.pageCode = '';
      resourceQuery.buttonId = undefined;
      pagination.value.current = 1;
      return loadPermissionRows();
    }),
  );

const onPageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadPermissionRows();
};

const filterTreeNodes = (nodes: AnyRecord[], keyword: string): AnyRecord[] => {
  if (!keyword) return nodes;
  return nodes
    .map((node) => {
      const children = filterTreeNodes(node.children || [], keyword);
      const matched = [node.label, node.systemCode, node.pageCode, node.buttonCode]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword.toLowerCase()));
      return matched || children.length ? { ...node, children } : null;
    })
    .filter(Boolean) as AnyRecord[];
};

onMounted(async () => {
  await loadPermissionTree();
  await loadPermissionRows();
});
</script>
