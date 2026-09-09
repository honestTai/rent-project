<template>
  <div class="platform-page">
    <platform-page-card>
      <template #actions>
        <t-button theme="primary" @click="openChannel()">
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
        <t-input v-model="query.keyword" class="platform-search" clearable placeholder="搜索通道 / 场景" />
      </template>

      <t-table
        row-key="id"
        :data="pagedChannels"
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
            <platform-table-action icon="edit" tooltip="编辑" @click="openChannel(row)" />
            <platform-table-action icon="delete" theme="danger" tooltip="删除" @click="removeChannel(row)" />
          </div>
        </template>
      </t-table>
    </platform-page-card>

    <platform-form-dialog
      v-model:visible="dialogVisible"
      title="通知通道"
      :model="form"
      label-width="110px"
      @confirm="submitChannel"
    >
      <t-form-item label="系统"><t-input v-model="form.systemCode" /></t-form-item>
      <t-form-item label="场景编码"><t-input v-model="form.sceneCode" /></t-form-item>
      <t-form-item label="通道编码"><t-input v-model="form.channelCode" /></t-form-item>
      <t-form-item label="通道类型"><t-input v-model="form.channelType" /></t-form-item>
      <t-form-item label="AppId"><t-input v-model="form.appId" /></t-form-item>
      <t-form-item label="AppSecret"><t-input v-model="form.appSecret" type="password" /></t-form-item>
      <t-form-item label="接收者类型"><t-input v-model="form.receiveIdType" /></t-form-item>
      <t-form-item label="接收者 ID"><t-input v-model="form.receiveId" /></t-form-item>
      <t-form-item label="启用"><platform-number-switch v-model="form.enabled" /></t-form-item>
      <t-form-item label="备注"><t-input v-model="form.remark" /></t-form-item>
    </platform-form-dialog>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PageInfo, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { deleteChannel, listChannels, saveChannel } from '@/api/platform';
import { systemOptions } from '@/constants/platform';
import PlatformFormDialog from '@/pages/platform/components/PlatformFormDialog.vue';
import PlatformNumberSwitch from '@/pages/platform/components/PlatformNumberSwitch.vue';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import PlatformTableAction from '@/pages/platform/components/PlatformTableAction.vue';
import { resolvePage, type AnyRecord } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

import '../index.less';

defineOptions({
  name: 'PlatformNotify',
});

const columns: PrimaryTableCol<TableRowData>[] = [
  { title: '系统', colKey: 'systemCode', width: 110 },
  { title: '场景', colKey: 'sceneCode', minWidth: 160, ellipsis: true },
  { title: '通道编码', colKey: 'channelCode', minWidth: 160, ellipsis: true },
  { title: '类型', colKey: 'channelType', width: 90 },
  { title: 'AppId', colKey: 'appId', minWidth: 160, ellipsis: true },
  { title: '接收者', colKey: 'receiveId', minWidth: 180, ellipsis: true },
  { title: '启用', colKey: 'enabled', width: 80, align: 'center' },
  { title: '操作', colKey: 'op', width: 104, fixed: 'right', align: 'center' },
];

const channels = ref<AnyRecord[]>([]);
const query = reactive({ systemCode: '', keyword: '' });
const form = ref<AnyRecord>({});
const dialogVisible = ref(false);
const loading = ref(false);
const pagination = ref({ current: 1, pageSize: 10, total: 0, pageSizeOptions: [10, 20, 50, 100] });

const pagedChannels = computed(() => channels.value);

const loadChannels = async () => {
  loading.value = true;
  try {
    const res = await listChannels({
      ...query,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    channels.value = page.list;
    pagination.value.current = page.current;
    pagination.value.pageSize = page.pageSize;
    pagination.value.total = page.total;
  } finally {
    loading.value = false;
  }
};

const searchChannels = () => {
  pagination.value.current = 1;
  loadChannels();
};

const { pauseAutoQuery } = useAutoQuery(query, searchChannels);

const resetQuery = () =>
  pauseAutoQuery(() => {
    query.systemCode = '';
    query.keyword = '';
    return searchChannels();
  });

const onPageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadChannels();
};

const openChannel = (row?: AnyRecord) => {
  form.value = {
    systemCode: 'common',
    sceneCode: 'default',
    channelCode: 'feishu.default',
    channelType: 'feishu',
    receiveIdType: 'chat_id',
    enabled: 1,
    ...(row || {}),
  };
  dialogVisible.value = true;
};

const submitChannel = async () => {
  loading.value = true;
  try {
    await saveChannel(form.value);
    dialogVisible.value = false;
    MessagePlugin.success('保存成功');
    await loadChannels();
  } finally {
    loading.value = false;
  }
};

const removeChannel = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await deleteChannel(row.id);
    MessagePlugin.success('删除成功');
    await loadChannels();
  } finally {
    loading.value = false;
  }
};

onMounted(loadChannels);
</script>
