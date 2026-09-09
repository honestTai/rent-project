<template>
  <AlipayCleanListPage
    class="system-log-page"
    title="系统日志"
    description="固定查看支付宝租赁服务运行日志、归档日志和关键错误行。"
    :rows="logLines"
    row-key="index"
    :loading="loading"
    :pagination="emptyPagination"
    content-title="支付宝租赁服务日志"
    empty-title="暂无系统日志"
  >
    <template #filters>
      <t-select v-model="query.level" :options="levelOptions" placeholder="日志级别" @change="handleLevelChange" />
      <t-select
        v-model="query.fileKey"
        class="system-log-file-select"
        placeholder="选择当前或归档日志文件"
        filterable
        v-bind="searchableSelectProps('systemLogs.fileKey', fileOptions)"
        @change="loadLogs"
      />
      <t-input-number
        v-model="query.tail"
        class="system-log-tail"
        :min="50"
        :max="1000"
        :step="50"
        placeholder="读取行数"
      />
      <t-input v-model="query.keyword" clearable placeholder="按关键字过滤日志行" />
    </template>

    <template #queryActions>
      <AlipayQueryActions search-text="查询日志" reset-text="重置" @search="loadLogs" @reset="resetQuery" />
    </template>

    <template #content>
      <div class="system-log-content">
        <t-alert
          v-if="!logResult.exists"
          class="system-log-alert"
          theme="warning"
          message="日志文件不存在"
          :description="logResult.filePath || '请确认平台容器已挂载支付宝租赁服务日志目录。'"
        />
        <t-alert
          v-else
          class="system-log-alert"
          theme="success"
          :message="logResult.archive ? '归档日志文件' : '当前日志文件'"
          :description="logDescription"
        />

        <t-table
          row-key="index"
          :data="logLines"
          :columns="columns"
          :hover="true"
          :loading="loading"
          table-layout="fixed"
          cell-empty-content="-"
        >
          <template #level="{ row }">
            <t-tag :theme="levelTheme(row.level)" variant="light">{{ levelText(row.level) }}</t-tag>
          </template>
          <template #content="{ row }">
            <pre class="system-log-line">{{ row.content }}</pre>
          </template>
        </t-table>
      </div>
    </template>
  </AlipayCleanListPage>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { createSearchableOptions } from '@shared/utils/search-options';
import { rentApi, resolveData, type AnyRecord } from '@/api/rent';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';

type LogSource = {
  systemCode: string;
  systemName: string;
  logDirectory: string;
  levels: string[];
  files: LogFile[];
};

type LogFile = {
  fileKey: string;
  fileName: string;
  level: string;
  archive: boolean;
  sizeBytes: number;
  lastModified: string;
};

type LogLine = {
  index: number;
  content: string;
  level: string;
};

const ALIPAY_SYSTEM_CODE = 'alipay';
const defaultLevels = ['error', 'warn', 'info', 'access'];
const emptyPagination = { current: 1, pageSize: 10, total: 0 };

const loading = ref(false);
const sources = ref<LogSource[]>([]);
const logResult = ref<AnyRecord>({ exists: true, filePath: '', fileName: '', lines: [] });
const { searchableSelectProps } = createSearchableOptions();
const query = reactive({
  level: 'error',
  fileKey: '',
  tail: 300,
  keyword: '',
});

const columns = [
  { title: '序号', colKey: 'index', width: 80, align: 'center' },
  { title: '级别', colKey: 'level', width: 110, align: 'center' },
  { title: '日志内容', colKey: 'content', minWidth: 760 },
];

const currentSource = computed(() => sources.value.find((source) => source.systemCode === ALIPAY_SYSTEM_CODE));
const levelOptions = computed(() => {
  const levels = currentSource.value?.levels?.length ? currentSource.value.levels : defaultLevels;
  return levels.map((level) => ({ label: levelText(level), value: level }));
});
const fileOptions = computed(() =>
  (currentSource.value?.files || [])
    .filter((file) => !query.level || file.level === query.level)
    .map((file) => ({
      label: `${file.fileName}${file.lastModified ? ` · ${file.lastModified}` : ''}`,
      value: file.fileKey,
    })),
);
const logLines = computed<LogLine[]>(() => (Array.isArray(logResult.value.lines) ? logResult.value.lines : []));
const logDescription = computed(() => {
  const fileName = String(logResult.value.fileName || '');
  const filePath = String(logResult.value.filePath || '');
  return [fileName, filePath].filter(Boolean).join(' ');
});

async function loadInitialData() {
  await loadSources();
  await loadLogs();
}

async function loadSources() {
  const response = await rentApi.listAlipayLogSources();
  sources.value = resolveData<LogSource[]>(response, []).filter((source) => source.systemCode === ALIPAY_SYSTEM_CODE);
  applyDefaultFile();
}

async function loadLogs() {
  loading.value = true;
  try {
    const response = await rentApi.queryAlipaySystemLogs({
      level: query.level,
      fileKey: query.fileKey,
      tail: query.tail,
      keyword: query.keyword,
    });
    logResult.value = resolveData<AnyRecord>(response, { exists: false, lines: [] });
  } finally {
    loading.value = false;
  }
}

async function resetQuery() {
  query.level = 'error';
  query.fileKey = '';
  query.tail = 300;
  query.keyword = '';
  applyDefaultFile();
  await loadLogs();
}

function handleLevelChange() {
  applyDefaultFile();
  void loadLogs();
}

function applyDefaultFile() {
  const files = currentSource.value?.files || [];
  const sameLevel = files.filter((file) => file.level === query.level);
  const candidate =
    sameLevel.find((file) => !file.archive) ||
    sameLevel[0] ||
    files.find((file) => !file.archive) ||
    files[0];
  query.fileKey = candidate?.fileKey || '';
  if (candidate?.level) {
    query.level = candidate.level;
  }
}

function levelTheme(level: string) {
  if (level === 'error') return 'danger';
  if (level === 'warn') return 'warning';
  if (level === 'access') return 'success';
  return 'primary';
}

function levelText(level: string) {
  if (level === 'error') return 'ERROR';
  if (level === 'warn') return 'WARN';
  if (level === 'access') return 'ACCESS';
  return 'INFO';
}

onMounted(() => {
  loadInitialData().catch((error) => {
    MessagePlugin.error(error instanceof Error ? error.message : '系统日志加载失败');
  });
});
</script>

<style scoped>
.system-log-content {
  padding: 16px 20px 20px;
  background: #fff;
}

.system-log-alert {
  margin-bottom: 12px;
}

.system-log-file-select {
  width: 100%;
}

.system-log-tail {
  width: 100%;
}

.system-log-line {
  max-height: 120px;
  margin: 0;
  overflow: auto;
  color: #1d2939;
  font-size: 12px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}

.system-log-page :deep(.t-card),
.system-log-page :deep(.t-table) {
  box-shadow: none !important;
}
</style>
