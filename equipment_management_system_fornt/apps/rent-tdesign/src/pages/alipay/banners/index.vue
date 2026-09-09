<template>
  <div id="miniappBannerList">
    <AlipayCleanListPage
      title="轮播图设置"
      description="维护小程序首页轮播图、上下架状态、排序和跳转配置。"
      :rows="listData"
      row-key="bannerId"
      :loading="loading"
      :pagination="pagination"
      content-title="轮播图列表"
      empty-title="暂无轮播图"
      @page-change="handlePageChange"
    >
      <template #filters>
        <t-input v-model="tableParams.title" placeholder="请输入轮播标题" clearable />
        <t-select
          v-model="tableParams.status"
          :options="statusOptions"
          placeholder="轮播状态"
          clearable
        />
      </template>

      <template #queryActions>
        <AlipayQueryActions @search="search" @reset="resetSearchFilters" />
      </template>

      <template #contentActions>
        <PermissionButton :code="buttonCode('banner', 'create')" theme="primary" variant="outline" @click="openCreate">
          <template #icon><t-icon name="add" /></template>
          新增轮播图
        </PermissionButton>
        <t-button theme="primary" variant="outline" @click="getData">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
      </template>

      <template #record="{ row }">
        <article class="banner-record">
          <div class="banner-record__cover">
            <t-image-viewer v-if="row.image" :images="[resolveUploadAssetPath(row.image)]">
              <template #trigger="{ open }">
                <button class="banner-image-trigger" type="button" @click.stop="open">
                  <t-image :src="resolveUploadAssetPath(row.image)" fit="cover" />
                  <span class="banner-image-trigger__mask">
                    <t-icon name="browse" />
                  </span>
                </button>
              </template>
            </t-image-viewer>
            <div v-else class="banner-record__empty">无图</div>
          </div>
          <div class="banner-record__main">
            <div class="banner-record__summary">
              <div class="banner-record__title">
                <strong>{{ row.title || '-' }}</strong>
                <div class="banner-record__meta">
                  <span>ID：{{ row.bannerId || '-' }}</span>
                  <span>排序：{{ row.sortOrder ?? '-' }}</span>
                  <span>跳转：{{ formatLink(row) }}</span>
                </div>
              </div>
              <div class="banner-record__tags">
                <t-tag :theme="statusTheme(row.status)" variant="light">{{ statusText(row.status) }}</t-tag>
                <t-tag v-if="row.badge" theme="primary" variant="light">{{ row.badge }}</t-tag>
              </div>
            </div>
            <p class="banner-record__desc">{{ row.description || '-' }}</p>
            <div class="banner-record__actions">
              <PermissionButton :code="buttonCode('banner', 'update')" theme="primary" variant="text" size="small" @click="openEdit(row)">编辑</PermissionButton>
              <PermissionButton
                :code="buttonCode('banner', 'update')"
                :theme="Number(row.status || 0) === 1 ? 'warning' : 'success'"
                variant="text"
                size="small"
                @click="toggleStatus(row)"
              >
                {{ Number(row.status || 0) === 1 ? '停用' : '启用' }}
              </PermissionButton>
              <PermissionButton :code="buttonCode('banner', 'update')" theme="warning" variant="text" size="small" @click="sortTop(row)">置顶</PermissionButton>
              <PermissionButton :code="buttonCode('banner', 'delete')" theme="danger" variant="text" size="small" @click="deleteBanner(row)">删除</PermissionButton>
            </div>
          </div>
        </article>
      </template>
    </AlipayCleanListPage>

    <t-drawer
      v-model:visible="editVisible"
      :header="drawerTitle"
      :size="drawerSize"
      placement="right"
      drawer-class-name="banner-drawer"
      destroy-on-close
      :close-on-overlay-click="false"
      :prevent-scroll-through="true"
    >
      <div class="banner-drawer-body">
        <t-form class="banner-form" :data="form" label-align="top">
          <section class="banner-form-section">
            <div class="banner-form-section__header">
              <span>基础信息</span>
            </div>
            <div class="banner-form-grid">
              <t-form-item label="轮播标题">
                <t-input v-model="form.title" placeholder="请输入轮播标题" />
              </t-form-item>
              <t-form-item label="角标">
                <t-input v-model="form.badge" placeholder="例如：热租" />
              </t-form-item>
              <t-form-item label="状态">
                <t-radio-group v-model="form.status">
                  <t-radio :value="1">启用</t-radio>
                  <t-radio :value="0">停用</t-radio>
                </t-radio-group>
              </t-form-item>
              <t-form-item label="排序">
                <t-input-number v-model="form.sortOrder" theme="normal" />
              </t-form-item>
              <t-form-item class="banner-form-item--full" label="描述">
                <t-textarea v-model="form.description" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="请输入描述" />
              </t-form-item>
            </div>
          </section>

          <section class="banner-form-section">
            <div class="banner-form-section__header">
              <span>图片</span>
            </div>
            <div class="banner-image-panel">
              <div class="banner-image-preview">
                <t-image-viewer v-if="form.image" :images="[resolveUploadAssetPath(form.image)]">
                  <template #trigger="{ open }">
                    <button class="banner-image-trigger banner-image-trigger--form" type="button" @click.stop="open">
                      <t-image :src="resolveUploadAssetPath(form.image)" fit="cover" />
                      <span class="banner-image-trigger__mask">
                        <t-icon name="browse" />
                      </span>
                    </button>
                  </template>
                </t-image-viewer>
                <div v-else class="banner-image-empty">
                  <t-icon name="image" />
                  <span>暂无图片</span>
                </div>
              </div>
              <div class="banner-image-upload">
                <t-upload theme="file" :request-method="uploadBannerImage" accept="image/png,image/jpg,image/jpeg,image/webp">
                  <t-button theme="primary" variant="outline">
                    <template #icon><t-icon name="upload" /></template>
                    上传图片
                  </t-button>
                </t-upload>
                <t-input v-model="form.image" placeholder="也可直接填写图片路径或 URL" />
              </div>
            </div>
          </section>

          <section class="banner-form-section">
            <div class="banner-form-section__header">
              <span>跳转配置</span>
            </div>
            <div class="banner-form-grid">
              <t-form-item label="跳转类型">
                <t-select v-model="form.linkType" :options="linkTypeOptions" />
              </t-form-item>
              <t-form-item label="跳转值">
                <t-input v-model="form.linkValue" placeholder="商品ID、小程序路径或 URL" />
              </t-form-item>
            </div>
          </section>
        </t-form>
      </div>
      <template #footer>
        <div class="banner-drawer-footer">
          <t-button theme="default" @click="editVisible = false">取消</t-button>
          <t-button theme="primary" @click="submitBanner">保存</t-button>
        </div>
      </template>
    </t-drawer>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { cleanQuery, rentApi, resolveData, resolvePage, resolveUploadAssetPath, type AnyRecord } from '@/api/rent';
import PermissionButton from '@/components/business/PermissionButton.vue';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import { buttonCode } from '@/pages/alipay/shared';

type PageInfo = { current: number; pageSize: number };

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];
const linkTypeOptions = [
  { value: 'none', label: '不跳转' },
  { value: 'goods', label: '商品详情' },
  { value: 'path', label: '小程序路径' },
  { value: 'url', label: '外部链接' },
];

const tableParams = reactive({
  page: 1,
  limit: 10,
  title: '',
  status: 1 as number | '',
});
const loading = ref(false);
const listData = ref<AnyRecord[]>([]);
const pageTotal = ref(0);
const editVisible = ref(false);
const drawerTitle = ref('新增轮播图');
const form = reactive<AnyRecord>(emptyForm());
const isNarrowDrawerViewport = ref(false);
const drawerSize = computed(() => isNarrowDrawerViewport.value ? '100vw' : 'min(680px, calc(100vw - 96px))');

const pagination = computed(() => ({
  current: tableParams.page,
  pageSize: tableParams.limit,
  total: pageTotal.value,
  pageSizeOptions: [10, 20, 50, 100],
}));

function emptyForm(): AnyRecord {
  return {
    title: '',
    description: '',
    badge: '',
    image: '',
    linkType: 'none',
    linkValue: '',
    sortOrder: null,
    status: 1,
  };
}

function resetForm(row?: AnyRecord) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, emptyForm(), row || {});
  form.status = Number(form.status ?? 1) === 1 ? 1 : 0;
  form.linkType = form.linkType || 'none';
}

async function getData() {
  const requestedPage = tableParams.page;
  const requestedLimit = tableParams.limit;
  loading.value = true;
  try {
    const response = await rentApi.getMiniappBannerList(cleanQuery(tableParams));
    const page = resolvePage<AnyRecord>(response);
    listData.value = page.list;
    pageTotal.value = page.total;
    tableParams.page = requestedPage;
    tableParams.limit = requestedLimit;
  } finally {
    loading.value = false;
  }
}

function search() {
  tableParams.page = 1;
  getData();
}

function resetSearchFilters() {
  tableParams.title = '';
  tableParams.status = 1;
  search();
}

function handlePageChange(pageInfo: PageInfo) {
  tableParams.page = pageInfo.current;
  tableParams.limit = pageInfo.pageSize;
  getData();
}

function openCreate() {
  drawerTitle.value = '新增轮播图';
  resetForm();
  editVisible.value = true;
}

function openEdit(row: AnyRecord) {
  drawerTitle.value = '编辑轮播图';
  resetForm(row);
  editVisible.value = true;
}

async function submitBanner() {
  if (!String(form.title || '').trim()) {
    MessagePlugin.warning('轮播标题不能为空');
    return;
  }
  if (!String(form.image || '').trim()) {
    MessagePlugin.warning('轮播图片不能为空');
    return;
  }
  const payload = {
    bannerId: form.bannerId,
    title: String(form.title || '').trim(),
    description: String(form.description || '').trim(),
    badge: String(form.badge || '').trim(),
    image: String(form.image || '').trim(),
    linkType: form.linkType || 'none',
    linkValue: String(form.linkValue || '').trim(),
    sortOrder: form.sortOrder,
    status: Number(form.status || 0) === 1 ? 1 : 0,
  };
  if (payload.bannerId) {
    await rentApi.updateMiniappBanner(payload);
    MessagePlugin.success('轮播图已保存');
  } else {
    await rentApi.createMiniappBanner(payload);
    MessagePlugin.success('轮播图已新增');
  }
  editVisible.value = false;
  getData();
}

async function toggleStatus(row: AnyRecord) {
  const nextStatus = Number(row.status || 0) === 1 ? 0 : 1;
  await rentApi.updateMiniappBannerStatus({ bannerId: row.bannerId, status: nextStatus });
  MessagePlugin.success(nextStatus === 1 ? '轮播图已启用' : '轮播图已停用');
  getData();
}

async function sortTop(row: AnyRecord) {
  await rentApi.sortMiniappBannerTop({ bannerId: row.bannerId });
  MessagePlugin.success('轮播图已置顶');
  getData();
}

async function deleteBanner(row: AnyRecord) {
  await rentApi.deleteMiniappBanner({ bannerId: row.bannerId });
  MessagePlugin.success('轮播图已删除');
  getData();
}

async function uploadBannerImage(files: any) {
  const file = Array.isArray(files) ? files[0] : files;
  const formData = new FormData();
  formData.append('file', file.raw || file);
  formData.append('path', 'banner/');
  const response = await rentApi.uploadSingle(formData);
  const data = resolveData<any>(response, {});
  const key = data?.path ?? data;
  form.image = key;
  return { status: 'success', response: { url: resolveUploadAssetPath(key) } };
}

function statusText(status: unknown) {
  return Number(status || 0) === 1 ? '启用' : '停用';
}

function statusTheme(status: unknown) {
  return Number(status || 0) === 1 ? 'success' : 'default';
}

function formatLink(row: AnyRecord) {
  const linkTypeText: Record<string, string> = {
    none: '不跳转',
    goods: '商品详情',
    path: '小程序路径',
    url: '外部链接',
  };
  const type = row.linkType || 'none';
  if (type === 'none') return linkTypeText.none;
  return `${linkTypeText[type] || type} ${row.linkValue || '-'}`;
}

function syncDrawerViewport() {
  if (typeof window === 'undefined') return;
  isNarrowDrawerViewport.value = window.innerWidth <= 760;
}

onMounted(() => {
  syncDrawerViewport();
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncDrawerViewport);
  }
  getData();
});

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncDrawerViewport);
  }
});
</script>

<style scoped>
:global(.banner-drawer .t-drawer__body) {
  padding: 0;
  background: #f5f7fa;
}

:global(.banner-drawer .t-drawer__footer) {
  padding: 0;
  border-top: 1px solid #f0f0f0;
}

.banner-record {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 16px;
  align-items: flex-start;
  padding: 14px 16px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.banner-record:hover {
  border-color: #d6e4ff;
  background: #fbfdff;
}

.banner-record__cover,
.banner-record__empty {
  width: 180px;
  height: 96px;
}

.banner-record__empty,
.banner-image-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #98a2b3;
  font-size: 12px;
  background: #f8fafc;
}

.banner-record__main {
  min-width: 0;
}

.banner-record__summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.banner-record__title {
  min-width: 0;
}

.banner-record__title strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-record__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 4px;
}

.banner-record__meta span {
  overflow: hidden;
  max-width: 360px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-record__tags,
.banner-record__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.banner-record__desc {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.banner-record__actions {
  justify-content: flex-start;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f2f4f7;
}

.banner-record__actions :deep(.t-button) {
  min-width: auto;
  min-height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
}

.banner-image-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 0;
  overflow: hidden;
  cursor: zoom-in;
  border: 0;
  border-radius: 0;
  background: #fff;
}

.banner-image-trigger--form {
  width: 240px;
  height: 128px;
}

.banner-image-trigger :deep(.t-image__wrapper),
.banner-image-trigger :deep(.t-image),
.banner-image-trigger :deep(img) {
  width: 100%;
  height: 100%;
  border: 0 !important;
  border-radius: 0 !important;
  background: #fff !important;
}

.banner-image-trigger__mask {
  position: absolute;
  inset: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  opacity: 0;
  background: rgb(0 0 0 / 38%);
  transition: opacity .2s ease;
}

.banner-image-trigger:hover .banner-image-trigger__mask,
.banner-image-trigger:focus-visible .banner-image-trigger__mask {
  opacity: 1;
}

.banner-drawer-body {
  height: 100%;
  padding: 20px 24px 24px;
  overflow: auto;
  background: #f5f7fa;
}

.banner-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.banner-form-section {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}

.banner-form-section__header {
  display: flex;
  align-items: center;
  min-height: 52px;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.banner-form-section__header span {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.banner-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 24px;
  padding: 20px;
}

.banner-form-item--full {
  grid-column: 1 / -1;
}

.banner-form :deep(.t-form__item),
.banner-form :deep(.t-input),
.banner-form :deep(.t-input-number),
.banner-form :deep(.t-select),
.banner-form :deep(.t-textarea) {
  width: 100%;
  margin-bottom: 0;
}

.banner-image-panel {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  padding: 20px;
}

.banner-image-preview {
  width: 240px;
  height: 128px;
  overflow: hidden;
  background: #f8fafc;
}

.banner-image-empty {
  flex-direction: column;
  gap: 6px;
  width: 100%;
  height: 100%;
}

.banner-image-empty :deep(.t-icon) {
  font-size: 22px;
}

.banner-image-upload {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.banner-image-upload :deep(.t-upload) {
  width: fit-content;
}

.banner-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 24px;
  background: #fff;
}

@media (max-width: 760px) {
  .banner-record {
    grid-template-columns: 1fr;
  }

  .banner-record__cover,
  .banner-record__empty {
    width: 100%;
    height: 160px;
  }

  .banner-record__summary,
  .banner-form-grid,
  .banner-image-panel {
    grid-template-columns: 1fr;
  }

  .banner-record__tags {
    justify-content: flex-start;
  }

  .banner-drawer-body {
    padding: 16px;
  }

  .banner-image-preview,
  .banner-image-trigger--form {
    width: 100%;
    height: 168px;
  }
}
</style>
