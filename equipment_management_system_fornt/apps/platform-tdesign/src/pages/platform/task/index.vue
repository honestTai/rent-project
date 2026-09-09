<template>
  <div class="platform-page">
    <platform-page-card>
      <template #actions>
        <t-button theme="primary" @click="openTask()">
          <template #icon><t-icon name="add" /></template>
          新增
        </t-button>
        <t-button theme="default" variant="outline" @click="resetQuery">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-select v-model="query.systemCode" class="platform-filter" clearable placeholder="系统">
          <t-option v-for="item in systemOptions" :key="item.value" :label="item.label" :value="item.value" />
        </t-select>
        <t-input v-model="query.keyword" class="platform-search" clearable placeholder="搜索任务编码 / 名称" />
      </template>

      <t-table
        row-key="id"
        :data="pagedTasks"
        :columns="columns"
        :hover="true"
        :loading="loading"
        :pagination="pagination"
        :disable-data-page="true"
        table-layout="fixed"
        @page-change="onPageChange"
      >
        <template #enabled="{ row }">
          <t-tag :theme="row.enabled === 1 ? 'success' : 'default'" variant="light">
            {{ row.enabled === 1 ? '是' : '否' }}
          </t-tag>
        </template>
        <template #op="{ row }">
          <div class="platform-table-actions">
            <platform-table-action icon="edit" tooltip="编辑" @click="openTask(row)" />
            <platform-table-action icon="delete" theme="danger" tooltip="删除" @click="removeTask(row)" />
          </div>
        </template>
      </t-table>
    </platform-page-card>

    <platform-form-dialog v-model:visible="dialogVisible" title="任务配置" :model="form" @confirm="submitTask">
      <t-form-item label="系统"><t-input v-model="form.systemCode" /></t-form-item>
      <t-form-item label="任务编码"><t-input v-model="form.taskCode" /></t-form-item>
      <t-form-item label="任务名称"><t-input v-model="form.taskName" /></t-form-item>
      <t-form-item label="Cron"><t-input v-model="form.cronExpr" /></t-form-item>
      <t-form-item label="执行类型"><t-input v-model="form.executeType" /></t-form-item>
      <t-form-item label="执行目标"><t-input v-model="form.executeTarget" /></t-form-item>
      <t-form-item label="启用"><platform-number-switch v-model="form.enabled" /></t-form-item>
      <t-form-item label="备注"><t-input v-model="form.remark" /></t-form-item>
    </platform-form-dialog>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PageInfo, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { deleteTask, listTasks, saveTask } from '@/api/platform';
import { systemOptions } from '@/constants/platform';
import PlatformFormDialog from '@/pages/platform/components/PlatformFormDialog.vue';
import PlatformNumberSwitch from '@/pages/platform/components/PlatformNumberSwitch.vue';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import PlatformTableAction from '@/pages/platform/components/PlatformTableAction.vue';
import { resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';

defineOptions({
  name: 'PlatformTask',
});

const columns: PrimaryTableCol<TableRowData>[] = [
  { title: '系统', colKey: 'systemCode', width: 110 },
  { title: '任务编码', colKey: 'taskCode', minWidth: 190, ellipsis: true },
  { title: '任务名称', colKey: 'taskName', minWidth: 160, ellipsis: true },
  { title: 'Cron', colKey: 'cronExpr', minWidth: 150 },
  { title: '执行目标', colKey: 'executeTarget', minWidth: 240, ellipsis: true },
  { title: '启用', colKey: 'enabled', width: 80, align: 'center' },
  { title: '操作', colKey: 'op', width: 104, fixed: 'right', align: 'center' },
];

const tasks = ref<AnyRecord[]>([]);
const query = reactive({ systemCode: '', keyword: '' });
const form = ref<AnyRecord>({});
const dialogVisible = ref(false);
const loading = ref(false);
const pagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const pagedTasks = computed(() => tasks.value);

const loadTasks = async () => {
  loading.value = true;
  try {
    const res = await listTasks({
      ...query,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    tasks.value = page.list;
    pagination.value.current = page.current;
    pagination.value.pageSize = page.pageSize;
    pagination.value.total = page.total;
  } finally {
    loading.value = false;
  }
};

const searchTasks = () => {
  pagination.value.current = 1;
  loadTasks();
};

const { pauseAutoQuery } = useAutoQuery(query, searchTasks);

const resetQuery = () =>
  pauseAutoQuery(() => {
    query.systemCode = '';
    query.keyword = '';
    return searchTasks();
  });

const onPageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadTasks();
};

const openTask = (row?: AnyRecord) => {
  form.value = {
    systemCode: 'alipay',
    executeType: 'HTTP',
    enabled: 0,
    ...(row || {}),
  };
  dialogVisible.value = true;
};

const submitTask = async () => {
  loading.value = true;
  try {
    await saveTask(form.value);
    dialogVisible.value = false;
    MessagePlugin.success('保存成功');
    await loadTasks();
  } finally {
    loading.value = false;
  }
};

const removeTask = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await deleteTask(row.id);
    MessagePlugin.success('删除成功');
    await loadTasks();
  } finally {
    loading.value = false;
  }
};

onMounted(loadTasks);
</script>
