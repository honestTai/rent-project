<template>
  <div class="platform-page">
    <platform-page-card>
      <template #actions>
        <t-button theme="primary" @click="openConfig()">
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
        <t-select v-model="query.group" class="platform-filter" clearable placeholder="分组">
          <t-option v-for="item in configGroups" :key="item" :label="item" :value="item" />
        </t-select>
        <t-input v-model="query.keyword" class="platform-search" clearable placeholder="请输入内容搜索" />
      </template>

      <t-table
        row-key="id"
        :data="pagedConfigs"
        :columns="columns"
        :hover="true"
        :loading="loading"
        :pagination="pagination"
        :disable-data-page="true"
        table-layout="fixed"
        @page-change="onPageChange"
      >
        <template #displayName="{ row }">
          <div
            class="config-text-cell"
            :class="{ 'config-text-cell--warning': formatConfigText(row.displayName).mojibake }"
          >
            <span>{{ formatConfigText(row.displayName).text || '-' }}</span>
            <t-tag
              v-if="formatConfigText(row.displayName).mojibake"
              size="small"
              theme="warning"
              variant="light"
              :title="`原始值：${formatConfigText(row.displayName).original}`"
            >
              疑似乱码
            </t-tag>
          </div>
        </template>
        <template #configValue="{ row }">
          <div
            class="config-text-cell"
            :class="{ 'config-text-cell--warning': formatConfigText(row.configValue).mojibake }"
          >
            <span>{{ formatConfigText(row.configValue).text || '-' }}</span>
            <t-tag
              v-if="formatConfigText(row.configValue).mojibake"
              size="small"
              theme="warning"
              variant="light"
              :title="`原始值：${formatConfigText(row.configValue).original}`"
            >
              疑似乱码
            </t-tag>
          </div>
        </template>
        <template #enabled="{ row }">
          <t-tag :theme="row.enabled === 1 ? 'success' : 'default'" variant="light">
            {{ row.enabled === 1 ? '是' : '否' }}
          </t-tag>
        </template>
        <template #op="{ row }">
          <div class="platform-table-actions">
            <platform-table-action icon="edit" tooltip="编辑" @click="openConfig(row)" />
            <platform-table-action icon="delete" theme="danger" tooltip="删除" @click="removeConfig(row)" />
          </div>
        </template>
      </t-table>
    </platform-page-card>

    <platform-form-dialog v-model:visible="dialogVisible" title="配置项" :model="form" @confirm="submitConfig">
      <t-form-item label="系统"><t-input v-model="form.systemCode" /></t-form-item>
      <t-form-item label="分组"><t-input v-model="form.configGroup" /></t-form-item>
      <t-form-item label="配置键"><t-input v-model="form.configKey" /></t-form-item>
      <t-form-item label="名称"><t-input v-model="form.displayName" /></t-form-item>
      <t-form-item label="配置值">
        <div v-if="isAlipayGoodsCategoryForm" class="category-picker">
          <div class="category-picker__search">
            <t-input
              v-model="categoryKeyword"
              clearable
              placeholder="搜索类目名称或ID"
              @enter="loadAlipayCategories()"
            />
            <t-button theme="primary" :loading="categoryLoading" @click="loadAlipayCategories()">
              <template #icon><t-icon name="search" /></template>
              查询
            </t-button>
          </div>
          <t-select
            v-model="form.configValue"
            :options="alipayCategoryOptions"
            :loading="categoryLoading"
            clearable
            filterable
            placeholder="请选择支付宝商品类目"
          />
          <div v-if="selectedCategoryPath" class="category-picker__path">{{ selectedCategoryPath }}</div>
        </div>
        <div v-else-if="isAlipayRentCategoryForm" class="category-picker">
          <t-select
            v-model="form.configValue"
            :options="alipayRentCategoryOptions"
            :loading="rentCategoryLoading"
            clearable
            filterable
            placeholder="请选择支付宝租赁类目"
          />
          <div v-if="selectedRentCategoryPath" class="category-picker__path">{{ selectedRentCategoryPath }}</div>
        </div>
        <div v-else-if="isAlipayItemFinenessForm" class="category-picker">
          <t-select
            v-model="form.configValue"
            :options="alipayItemFinenessOptions"
            filterable
            placeholder="请选择支付宝商品成色"
          />
        </div>
        <div v-else-if="isAlipayItemFinenessGradeForm" class="category-picker">
          <t-select
            v-model="form.configValue"
            :options="alipayItemFinenessGradeOptions"
            filterable
            placeholder="请选择支付宝商品成色等级"
          />
        </div>
        <div v-else-if="isBooleanValueForm" class="boolean-config">
          <t-switch
            :value="String(form.configValue).toLowerCase() === 'true'"
            label="开启"
            @change="onBooleanConfigValueChange"
          />
          <span>{{ String(form.configValue).toLowerCase() === 'true' ? '已开启' : '已关闭' }}</span>
        </div>
        <t-textarea v-else-if="!isSecretForm" v-model="form.configValue" :autosize="{ minRows: 4, maxRows: 6 }" />
        <t-input v-else v-model="form.configValue" type="password" />
      </t-form-item>
      <t-form-item label="类型"><t-input v-model="form.valueType" /></t-form-item>
      <t-form-item label="敏感"><platform-number-switch v-model="form.secretFlag" /></t-form-item>
      <t-form-item label="启用"><platform-number-switch v-model="form.enabled" /></t-form-item>
      <t-form-item label="说明"><t-input v-model="form.remark" /></t-form-item>
    </platform-form-dialog>
  </div>
</template>
<script setup lang="ts">
import '../index.less';

import type { PageInfo, PrimaryTableCol, TableRowData } from 'tdesign-vue-next';
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  deleteConfig,
  listConfigs,
  queryAlipayItemCategories,
  queryAlipayRentCategories,
  saveConfig,
} from '@/api/platform';
import { configGroups, systemOptions } from '@/constants/platform';
import PlatformFormDialog from '@/pages/platform/components/PlatformFormDialog.vue';
import PlatformNumberSwitch from '@/pages/platform/components/PlatformNumberSwitch.vue';
import PlatformPageCard from '@/pages/platform/components/PlatformPageCard.vue';
import PlatformTableAction from '@/pages/platform/components/PlatformTableAction.vue';
import type { AnyRecord } from '@/pages/platform/utils';
import { resolvePage } from '@/pages/platform/utils';
import { useAutoQuery } from '@/utils/useAutoQuery';

defineOptions({
  name: 'PlatformConfig',
});

const columns: PrimaryTableCol<TableRowData>[] = [
  { title: '系统', colKey: 'systemCode', width: 110 },
  { title: '分组', colKey: 'configGroup', width: 110 },
  { title: '配置键', colKey: 'configKey', minWidth: 220, ellipsis: true },
  { title: '名称', colKey: 'displayName', minWidth: 150, ellipsis: true },
  { title: '配置值', colKey: 'configValue', minWidth: 240, ellipsis: true },
  { title: '类型', colKey: 'valueType', width: 90 },
  { title: '启用', colKey: 'enabled', width: 80, align: 'center' },
  { title: '操作', colKey: 'op', width: 104, fixed: 'right', align: 'center' },
];

const configs = ref<AnyRecord[]>([]);
const ALIPAY_GOODS_CATEGORY_CONFIG_KEY = 'alipay.goods.sync.category-id';
const ALIPAY_RENT_CATEGORY_CONFIG_KEY = 'alipay.rent.category-id';
const ALIPAY_ITEM_FINENESS_CONFIG_KEY = 'alipay.item.fineness';
const ALIPAY_ITEM_FINENESS_GRADE_CONFIG_KEY = 'alipay.item.fineness-grade';
const ESIGN_ENABLED_CONFIG_KEY = 'esign.enabled';
const CJK_TEXT_REGEXP = /[\u3400-\u9FFF\uF900-\uFAFF]/;
const MOJIBAKE_HINT_REGEXP = /[ÃÂâãäåæçèéïð€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]/;
const WINDOWS_1252_REVERSE_MAP: Record<string, number> = {
  '€': 0x80,
  '‚': 0x82,
  ƒ: 0x83,
  '„': 0x84,
  '…': 0x85,
  '†': 0x86,
  '‡': 0x87,
  ˆ: 0x88,
  '‰': 0x89,
  Š: 0x8a,
  '‹': 0x8b,
  Œ: 0x8c,
  Ž: 0x8e,
  '‘': 0x91,
  '’': 0x92,
  '“': 0x93,
  '”': 0x94,
  '•': 0x95,
  '–': 0x96,
  '—': 0x97,
  '˜': 0x98,
  '™': 0x99,
  š: 0x9a,
  '›': 0x9b,
  œ: 0x9c,
  ž: 0x9e,
  Ÿ: 0x9f,
};
const alipayItemFinenessOptions = [
  { label: '全新（wholeNew）', value: 'wholeNew' },
  { label: '二手（secondHand）', value: 'secondHand' },
];
const alipayItemFinenessGradeOptions = [
  { label: '99新（99new）', value: '99new' },
  { label: '95新（95new）', value: '95new' },
  { label: '9成新（90new）', value: '90new' },
  { label: '8成新（80new）', value: '80new' },
  { label: '7成新（70new）', value: '70new' },
];
const query = reactive({
  systemCode: '',
  group: '',
  keyword: '',
});
const form = ref<AnyRecord>({});
const categoryKeyword = ref('');
const categoryLoading = ref(false);
const alipayCategories = ref<AnyRecord[]>([]);
const rentCategoryLoading = ref(false);
const alipayRentCategories = ref<AnyRecord[]>([]);
const dialogVisible = ref(false);
const loading = ref(false);
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  pageSizeOptions: [10, 20, 50, 100],
});

const pagedConfigs = computed(() => configs.value);
const isSecretForm = computed(() => form.value.secretFlag === 1 || form.value.valueType === 'secret');
const isAlipayGoodsCategoryForm = computed(() => form.value.configKey === ALIPAY_GOODS_CATEGORY_CONFIG_KEY);
const isAlipayRentCategoryForm = computed(() => form.value.configKey === ALIPAY_RENT_CATEGORY_CONFIG_KEY);
const isAlipayItemFinenessForm = computed(() => form.value.configKey === ALIPAY_ITEM_FINENESS_CONFIG_KEY);
const isAlipayItemFinenessGradeForm = computed(() => form.value.configKey === ALIPAY_ITEM_FINENESS_GRADE_CONFIG_KEY);
const isBooleanValueForm = computed(
  () => form.value.configKey === ESIGN_ENABLED_CONFIG_KEY || form.value.valueType === 'boolean',
);
const alipayCategoryOptions = computed(() =>
  alipayCategories.value.map((item) => ({
    label: item.fullPathName || item.categoryName || item.categoryId,
    value: item.categoryId,
  })),
);
const selectedCategoryPath = computed(() => {
  const selected = alipayCategories.value.find((item) => item.categoryId === form.value.configValue);
  return selected ? `${selected.categoryId} · ${selected.fullPathName || selected.categoryName}` : '';
});
const alipayRentCategoryOptions = computed(() =>
  alipayRentCategories.value.map((item) => ({
    label: `${item.categoryName || item.categoryId}（${item.categoryId}）`,
    value: item.categoryId,
  })),
);
const selectedRentCategoryPath = computed(() => {
  const selected = alipayRentCategories.value.find((item) => item.categoryId === form.value.configValue);
  return selected ? `${selected.categoryId} · ${selected.scene || selected.categoryName}` : '';
});

const decodeUtf8Mojibake = (value: string) => {
  const bytes: number[] = [];
  for (const char of value) {
    const mapped = WINDOWS_1252_REVERSE_MAP[char];
    if (mapped !== undefined) {
      bytes.push(mapped);
      continue;
    }
    const code = char.charCodeAt(0);
    if (code > 0xff) return '';
    bytes.push(code);
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(new Uint8Array(bytes));
  } catch {
    return '';
  }
};

const formatConfigText = (value: unknown) => {
  const original = value === null || value === undefined ? '' : String(value);
  const suspectedMojibake = MOJIBAKE_HINT_REGEXP.test(original);
  if (!original || !suspectedMojibake) {
    return { text: original, mojibake: false, original };
  }

  let decoded = original;
  for (let index = 0; index < 2; index += 1) {
    const next = decodeUtf8Mojibake(decoded);
    if (!next || next === decoded) break;
    decoded = next;
  }

  const hasReadableDecodedText = decoded !== original && CJK_TEXT_REGEXP.test(decoded);
  return { text: hasReadableDecodedText ? decoded : original, mojibake: true, original };
};

const decodeConfigFieldForEdit = (value: unknown) => {
  const formatted = formatConfigText(value);
  return formatted.mojibake ? formatted.text : value;
};

const normalizeConfigForEdit = (row: AnyRecord) => ({
  ...row,
  configValue: decodeConfigFieldForEdit(row.configValue),
  displayName: decodeConfigFieldForEdit(row.displayName),
  remark: decodeConfigFieldForEdit(row.remark),
});

const loadConfigs = async () => {
  loading.value = true;
  try {
    const res = await listConfigs({
      ...query,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize,
    });
    const page = resolvePage<AnyRecord>(res);
    configs.value = page.list;
    pagination.value.current = page.current;
    pagination.value.pageSize = page.pageSize;
    pagination.value.total = page.total;
  } finally {
    loading.value = false;
  }
};

const searchConfigs = () => {
  pagination.value.current = 1;
  loadConfigs();
};

const { pauseAutoQuery } = useAutoQuery(query, searchConfigs);

const resetQuery = () =>
  pauseAutoQuery(() => {
    query.systemCode = '';
    query.group = '';
    query.keyword = '';
    return searchConfigs();
  });

const onPageChange = (pageInfo: PageInfo) => {
  pagination.value.current = pageInfo.current;
  pagination.value.pageSize = pageInfo.pageSize;
  loadConfigs();
};

const isAllowedItemFineness = (value: unknown) =>
  alipayItemFinenessOptions.some((item) => item.value === String(value || '').trim());

const isAllowedItemFinenessGrade = (value: unknown) =>
  alipayItemFinenessGradeOptions.some((item) => item.value === String(value || '').trim());

const openConfig = (row?: AnyRecord) => {
  form.value = {
    systemCode: 'common',
    configGroup: 'business',
    valueType: 'string',
    secretFlag: 0,
    enabled: 1,
    ...(row ? normalizeConfigForEdit(row) : {}),
  };
  if (form.value.configKey === ALIPAY_GOODS_CATEGORY_CONFIG_KEY) {
    categoryKeyword.value = String(form.value.configValue || '').trim();
    alipayCategories.value = [];
    if (categoryKeyword.value) {
      loadAlipayCategories({ silentWhenEmpty: true });
    }
  }
  if (form.value.configKey === ALIPAY_RENT_CATEGORY_CONFIG_KEY) {
    loadAlipayRentCategories();
  }
  if (form.value.configKey === ALIPAY_ITEM_FINENESS_CONFIG_KEY && !isAllowedItemFineness(form.value.configValue)) {
    form.value.configValue = 'secondHand';
  }
  if (
    form.value.configKey === ALIPAY_ITEM_FINENESS_GRADE_CONFIG_KEY &&
    !isAllowedItemFinenessGrade(form.value.configValue)
  ) {
    form.value.configValue = '95new';
  }
  if (isBooleanValueForm.value) {
    form.value.valueType = 'boolean';
    form.value.configValue = String(form.value.configValue).toLowerCase() === 'true' ? 'true' : 'false';
  }
  dialogVisible.value = true;
};

const onBooleanConfigValueChange = (checked: boolean) => {
  form.value.configValue = checked ? 'true' : 'false';
};

const loadAlipayCategories = async (options: { silentWhenEmpty?: boolean } = {}) => {
  const keyword = categoryKeyword.value.trim();
  if (!keyword) {
    if (!options.silentWhenEmpty) MessagePlugin.warning('请输入类目名称或ID');
    return;
  }
  categoryLoading.value = true;
  try {
    const rows = await queryAlipayItemCategories({
      keyword,
      itemType: '2',
      catStatus: 'AUDIT_PASSED',
      limit: 200,
    });
    alipayCategories.value = Array.isArray(rows) ? rows : [];
    if (alipayCategories.value.length === 0) {
      MessagePlugin.warning('未查询到类目');
    }
  } finally {
    categoryLoading.value = false;
  }
};

const loadAlipayRentCategories = async () => {
  if (alipayRentCategories.value.length > 0) return;
  rentCategoryLoading.value = true;
  try {
    const rows = await queryAlipayRentCategories({});
    alipayRentCategories.value = Array.isArray(rows) ? rows : [];
  } finally {
    rentCategoryLoading.value = false;
  }
};

const submitConfig = async () => {
  loading.value = true;
  try {
    await saveConfig(form.value);
    dialogVisible.value = false;
    MessagePlugin.success('保存成功');
    await loadConfigs();
  } finally {
    loading.value = false;
  }
};

const removeConfig = async (row: AnyRecord) => {
  loading.value = true;
  try {
    await deleteConfig(row.id);
    MessagePlugin.success('删除成功');
    await loadConfigs();
  } finally {
    loading.value = false;
  }
};

onMounted(loadConfigs);
</script>
<style scoped lang="less">
.category-picker {
  width: 100%;
}

.category-picker__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-bottom: 8px;
}

.category-picker__path {
  margin-top: 6px;
  color: var(--td-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.boolean-config {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--td-text-color-secondary);
  font-size: 13px;
}

.config-text-cell {
  display: inline-flex;
  max-width: 100%;
  min-width: 0;
  align-items: center;
  gap: 6px;
  vertical-align: middle;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.config-text-cell--warning {
  color: var(--td-warning-color-7);
}
</style>
