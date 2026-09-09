<template>
  <div class="platform-page platform-log-page">
    <platform-page-card>
      <template #actions>
        <t-button theme="primary" :loading="loading" @click="loadLogs">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
        <t-button theme="default" variant="outline" @click="resetQuery">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
      </template>
      <template #filters>
        <t-select v-model="query.systemCode" class="platform-filter" placeholder="系统" @change="onSystemChange">
          <t-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </t-select>
        <t-select v-model="query.level" class="platform-filter" placeholder="日志级别" @change="onLevelChange">
          <t-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </t-select>
        <t-select
          v-model="query.fileKey"
          class="platform-log-file-select"
          placeholder="选择当前或归档日志文件"
          filterable
          @change="loadLogs"
        >
          <t-option v-for="item in fileOptions" :key="item.value" :label="item.label" :value="item.value" />
        </t-select>
        <t-input-number
          v-model="query.tail"
          class="platform-filter"
          :min="50"
          :max="1000"
          :step="50"
          placeholder="读取行数"
        />
        <t-input v-model="query.keyword" class="platform-search" clearable placeholder="按关键字过滤日志行" />
      </template>

      <t-alert
        v-if="!logResult.exists"
        class="platform-log-alert"
        theme="warning"
        message="日志文件不存在"
        :description="logResult.filePath || '请确认 Docker 部署已把服务日志目录挂载到中台容器 /app/service-logs。'"
      />
      <t-alert
        v-else
        class="platform-log-alert"
        theme="success"
        :message="logResult.archive ? '归档日志文件' : '当前日志文件'"
        :description="`${logResult.fileName || ''} ${logResult.filePath || ''}`"
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
          <pre class="platform-log-line">{{ row.content }}</pre>
        </template>
      </t-table>
    </platform-page-card>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin, type PrimaryTableCol, type TableRowData } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { listLogSources, querySystemLogs } from '@/api/platform';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import { resolveData, type AnyRecord } from '@/pages/platform/utils';

import '../index.less';

defineOptions({
  name: 'PlatformLogs',
});

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

const columns: PrimaryTableCol<TableRowData>[] = [
  { title: '序号', colKey: 'index', width: 80, align: 'center' },
  { title: '级别', colKey: 'level', width: 110, align: 'center' },
  { title: '日志内容', colKey: 'content', minWidth: 760 },
];

const loading = ref(false);
const sources = ref<LogSource[]>([]);
const logResult = ref<AnyRecord>({
  exists: true,
  filePath: '',
  lines: [],
});
const query = reactive({
  systemCode: 'platform',
  level: 'error',
  fileKey: '',
  tail: 300,
  keyword: '',
});

const sourceOptions = computed(() =>
  sources.value.map((source) => ({
    label: `${source.systemName}（${source.logDirectory}）`,
    value: source.systemCode,
  })),
);
const currentSource = computed(() => sources.value.find((source) => source.systemCode === query.systemCode));
const levelOptions = computed(() => {
  const levels = currentSource.value?.levels?.length ? currentSource.value.levels : ['error', 'warn', 'info'];
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

/**
 * 读取日志来源和默认日志内容，保持系统日志页面只访问后端白名单来源。
 */
const loadInitialData = async () => {
  await loadSources();
  await loadLogs();
};

/**
 * 查询后端固定的日志来源，不允许前端自行拼接日志目录。
 */
const loadSources = async () => {
  const response = await listLogSources();
  sources.value = resolveData<LogSource[]>(response, []);
  applyDefaultFile();
};

/**
 * 按当前筛选条件读取 logback 当前日志文件。
 */
const loadLogs = async () => {
  loading.value = true;
  try {
    const response = await querySystemLogs({
      systemCode: query.systemCode,
      level: query.level,
      fileKey: query.fileKey,
      tail: query.tail,
      keyword: query.keyword,
    });
    logResult.value = resolveData<AnyRecord>(response, { exists: false, lines: [] });
  } finally {
    loading.value = false;
  }
};

const resetQuery = async () => {
  query.systemCode = 'platform';
  query.level = 'error';
  query.fileKey = '';
  query.tail = 300;
  query.keyword = '';
  applyDefaultFile();
  await loadLogs();
};

const onSystemChange = () => {
  const source = currentSource.value;
  if (source?.levels?.length && !source.levels.includes(query.level)) {
    query.level = source.levels.includes('error') ? 'error' : source.levels[0];
  }
  applyDefaultFile();
  void loadLogs();
};

const onLevelChange = () => {
  applyDefaultFile();
  void loadLogs();
};

const levelTheme = (level: string) => {
  if (level === 'error') return 'danger';
  if (level === 'warn') return 'warning';
  return 'primary';
};

const levelText = (level: string) => {
  if (level === 'error') return 'ERROR';
  if (level === 'warn') return 'WARN';
  if (level === 'access') return 'ACCESS';
  return 'INFO';
};

const applyDefaultFile = () => {
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
};

onMounted(() => {
  loadInitialData().catch((error) => {
    MessagePlugin.error(error instanceof Error ? error.message : '系统日志加载失败');
  });
});
</script>

<style scoped lang="less">
.platform-log-alert {
  margin-bottom: 12px;
}

.platform-log-file-select {
  width: min(100%, 420px);
}

.platform-log-line {
  max-height: 120px;
  margin: 0;
  overflow: auto;
  color: #1d2939;
  font-size: 12px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
