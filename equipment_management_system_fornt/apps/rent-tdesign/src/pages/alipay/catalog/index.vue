<template>
  <div class="catalog-page">
    <section class="catalog-hero">
      <div>
        <span class="catalog-kicker">HONESTTAI · DEMO CATALOG</span>
        <h1>租赁商品目录</h1>
        <p>按业务场景维护小程序分类与商品归属，左侧展开目录，右侧完成查看和管理。</p>
      </div>
      <permission-button :code="buttonCode('catalog', 'create')" theme="primary" @click="openCreate()">
        新增分类
      </permission-button>
    </section>

    <div class="summary-grid">
      <div>
        <span>一级目录</span><strong>{{ roots.length }}</strong>
      </div>
      <div>
        <span>全部分类</span><strong>{{ flatCategories.length }}</strong>
      </div>
      <div>
        <span>已启用</span><strong>{{ enabledCount }}</strong>
      </div>
      <div><span>目录内商品</span><strong>{{ catalogGoodsTotal }}</strong></div>
    </div>

    <div class="catalog-workspace">
      <aside class="catalog-tree-panel">
        <div class="panel-heading">
          <div><span>目录结构</span><small>全部分类默认展开</small></div>
          <t-button variant="text" :loading="loading" @click="loadCategories">刷新</t-button>
        </div>
        <div class="catalog-tree">
          <section v-for="root in roots" :key="root.code" class="tree-group">
            <button class="tree-node tree-node--root" :class="{ active: selectedCode === root.code }" @click="selectCategory(root)">
              <span class="tree-node__icon"><component :is="iconComponent(root.icon)" /></span>
              <span class="tree-node__copy"><strong>{{ root.name }}</strong><small>{{ root.children?.length || 0 }} 个子目录</small></span>
              <span class="tree-node__count">{{ root.goodsCount || 0 }}</span>
            </button>
            <div class="tree-children">
              <button v-for="child in root.children || []" :key="child.code" class="tree-node tree-node--child" :class="{ active: selectedCode === child.code }" @click="selectCategory(child)">
                <span class="tree-branch">└</span>
                <span class="tree-node__copy"><strong>{{ child.name }}</strong><small>{{ child.code }}</small></span>
                <span class="tree-node__count">{{ child.goodsCount || 0 }}</span>
              </button>
            </div>
          </section>
        </div>
      </aside>

      <main class="catalog-detail-panel">
        <section v-if="selectedCategory" class="category-overview">
          <div class="category-overview__icon"><component :is="iconComponent(selectedCategory.icon)" /></div>
          <div class="category-overview__copy">
            <span>{{ selectedCategory.parentCode ? '二级目录' : '一级目录' }}</span>
            <h2>{{ selectedCategory.name }}</h2>
            <p>{{ selectedCategory.description || '用于组织和展示租赁商品。' }}</p>
          </div>
          <div class="category-overview__stats"><strong>{{ selectedCategory.goodsCount || 0 }}</strong><span>件商品</span></div>
          <div class="row-actions">
            <permission-button :code="buttonCode('catalog', 'update')" variant="outline" @click="openEdit(selectedCategory)">编辑</permission-button>
            <permission-button :code="buttonCode('catalog', 'update')" variant="outline" :theme="selectedCategory.status === 1 ? 'warning' : 'success'" @click="prepareStatus(selectedCategory)">{{ selectedCategory.status === 1 ? '停用' : '启用' }}</permission-button>
            <permission-button :code="buttonCode('catalog', 'delete')" variant="text" theme="danger" @click="prepareDelete(selectedCategory)">删除</permission-button>
          </div>
        </section>

        <t-card
          :title="selectedCategory ? `添加商品到「${selectedCategory.name}」` : '添加商品到目录'"
          :bordered="false"
          class="catalog-card unclassified-card"
        >
          <template #actions>
            <permission-button
              :code="buttonCode('catalog', 'bind')"
              theme="primary"
              :disabled="!canBindToSelectedCategory || selectedGoodIds.length === 0"
              :loading="saving"
              @click="bindGoods"
            >
              加入当前目录{{ selectedGoodIds.length ? `（${selectedGoodIds.length}）` : '' }}
            </permission-button>
          </template>
          <t-alert
            v-if="!selectedCategory"
            theme="info"
            message="请先从左侧选择一个目录，再选择要加入的商品。"
          />
          <t-alert
            v-else-if="selectedCategory.status !== 1"
            theme="warning"
            message="当前目录已停用，启用后才能添加商品。"
          />
          <t-alert
            v-else-if="selectedCategory.children?.length"
            theme="info"
            message="当前一级目录用于汇总其子目录商品，请从左侧选择一个具体的二级目录后添加。"
          />
          <p v-else class="card-help">
            当前目标目录：<strong>{{ selectedCategory.name }}</strong>。勾选下方商品后，点击“加入当前目录”即可完成绑定。
          </p>
          <div v-if="selectedGoodIds.length" class="selection-status">
            <span>已选 {{ selectedGoodIds.length }} 件商品</span>
            <t-button size="small" variant="text" @click="selectedGoodIds = []">清空选择</t-button>
          </div>
          <t-checkbox-group v-if="unclassifiedGoods.length" v-model="selectedGoodIds" class="goods-list">
            <div v-for="good in unclassifiedGoods" :key="good.goodId" class="goods-item">
              <t-checkbox :value="good.goodId" />
              <span
                ><strong>{{ good.goodTitle || `商品 #${good.goodId}` }}</strong
                ><small>ID {{ good.goodId }}</small></span
              >
              <t-tag :theme="good.status === 1 ? 'success' : 'default'" variant="light">{{
                good.status === 1 ? '上架' : '下架'
              }}</t-tag>
            </div>
          </t-checkbox-group>
          <t-empty v-else description="当前全部商品均已完成目录归类" />
          <t-pagination
            v-if="unclassifiedTotal > unclassifiedPageSize"
            v-model="unclassifiedPage"
            class="goods-pagination"
            :total="unclassifiedTotal"
            :page-size="unclassifiedPageSize"
            :show-page-size="false"
            size="small"
            @current-change="loadUnclassified"
          />
        </t-card>

        <t-card :title="`${selectedCategory?.name || '当前目录'} · 目录内商品`" :bordered="false" class="catalog-card category-goods-card">
          <template #actions>
            <permission-button
              :code="buttonCode('catalog', 'bind')"
              theme="danger"
              variant="outline"
              :disabled="selectedCategoryGoodIds.length === 0"
              @click="unbindSelectedGoods"
            >移出当前目录{{ selectedCategoryGoodIds.length ? `（${selectedCategoryGoodIds.length}）` : '' }}</permission-button>
          </template>
          <p class="card-help">展示当前目录中的商品；一级目录会汇总其全部子目录商品。移出后，商品会回到上方可添加商品区。</p>
          <div v-if="selectedCategoryGoodIds.length" class="selection-status">
            <span>已选 {{ selectedCategoryGoodIds.length }} 件商品</span>
            <t-button size="small" variant="text" @click="selectedCategoryGoodIds = []">清空选择</t-button>
          </div>
          <t-checkbox-group v-if="categoryGoods.length" v-model="selectedCategoryGoodIds" class="goods-list">
            <div v-for="good in categoryGoods" :key="good.goodId" class="goods-item">
              <t-checkbox :value="good.goodId" />
              <span><strong>{{ good.goodTitle || `商品 #${good.goodId}` }}</strong><small>ID {{ good.goodId }} · {{ good.categoryCode }}</small></span>
              <permission-button :code="buttonCode('catalog', 'bind')" size="small" variant="text" theme="danger" @click="unbindGoods([good.goodId])">移出类目</permission-button>
            </div>
          </t-checkbox-group>
          <t-empty v-else description="该目录暂未放入商品" />
          <t-pagination v-if="categoryGoodsTotal > categoryGoodsPageSize" v-model="categoryGoodsPage" class="goods-pagination" :total="categoryGoodsTotal" :page-size="categoryGoodsPageSize" :show-page-size="false" size="small" @current-change="loadCategoryGoods" />
        </t-card>
      </main>
    </div>

    <t-dialog
      v-model:visible="editorVisible"
      :header="editingCode ? '编辑小程序分类' : '新增小程序分类'"
      width="620px"
      :confirm-btn="{ loading: saving }"
      @confirm="saveCategory"
    >
      <t-form label-align="top">
        <div class="form-grid">
          <t-form-item label="分类编码" required
            ><t-input v-model="form.code" :disabled="Boolean(editingCode)" placeholder="如 camera-pro"
          /></t-form-item>
          <t-form-item label="父分类"
            ><t-select v-model="form.parentCode" clearable :options="parentOptions" placeholder="不选择则为一级目录"
          /></t-form-item>
          <t-form-item label="分类名称" required><t-input v-model="form.name" /></t-form-item>
          <t-form-item label="小程序简称" required><t-input v-model="form.shortName" /></t-form-item>
          <t-form-item label="分类图标" required>
            <t-select v-model="form.icon" filterable placeholder="选择 antd-mini Icon">
              <t-option v-for="option in iconOptions" :key="option.value" :value="option.value" :label="option.label">
                <div class="icon-option">
                  <component :is="iconComponent(option.value)" class="icon-preview" />
                  <span>{{ option.label }}</span>
                </div>
              </t-option>
            </t-select>
          </t-form-item>
          <t-form-item label="排序"><t-input-number v-model="form.sortOrder" :min="0" /></t-form-item>
          <t-form-item v-if="!editingCode" label="状态"
            ><t-radio-group v-model="form.status"
              ><t-radio :value="1">启用</t-radio><t-radio :value="0">停用</t-radio></t-radio-group
            ></t-form-item
          >
        </div>
        <t-form-item label="分类说明"><t-textarea v-model="form.description" :maxlength="512" /></t-form-item>
        <t-form-item label="分类封面 HTTPS 地址（可选兼容）"
          ><t-input v-model="form.coverImage" placeholder="https://..."
        /></t-form-item>
      </t-form>
    </t-dialog>

    <t-dialog
      v-model:visible="statusVisible"
      :header="pendingStatus === 0 ? '确认停用目录' : '确认启用目录'"
      width="520px"
      :confirm-btn="{ theme: pendingStatus === 0 ? 'danger' : 'primary', loading: saving }"
      @confirm="applyStatus"
    >
      <div class="impact-box">
        <p>
          分类：<strong>{{ pendingCategory?.name }}</strong>
        </p>
        <p v-if="pendingStatus === 0">
          停用后，小程序将隐藏该分类下 <strong>{{ impact.publicGoodsCount || 0 }}</strong> 个公开商品；共绑定
          {{ impact.goodsCount || 0 }} 个商品。
        </p>
        <p v-else>启用后，该分类及符合公开条件的商品会重新进入小程序目录。</p>
        <t-alert v-if="impact.enabledChildCount" theme="warning" message="该分类仍有启用中的子分类，请先停用子分类。" />
      </div>
    </t-dialog>

    <t-dialog
      v-model:visible="deleteVisible"
      header="删除小程序分类"
      width="480px"
      :confirm-btn="{ theme: 'danger', loading: saving }"
      @confirm="deleteCategory"
    >
      仅未包含子分类且未绑定商品的分类可以删除。确认删除“{{ pendingCategory?.name }}”吗？
    </t-dialog>

  </div>
</template>
<script setup lang="ts">
import {
  AppIcon,
  AudioIcon,
  CameraIcon,
  CheckCircleIcon,
  CompassIcon,
  FlightTakeoffIcon,
  GiftIcon,
  ImageIcon,
  PlayCircleIcon,
  RefreshIcon,
  SettingIcon,
  TagIcon,
  VideoCameraIcon,
  ViewModuleIcon,
} from 'tdesign-icons-vue-next';
import { MessagePlugin } from 'tdesign-vue-next';
import type { Component } from 'vue';
import { computed, onMounted, reactive, ref } from 'vue';

import type { AnyRecord } from '@/api/rent';
import { rentApi, resolveData, resolvePage } from '@/api/rent';
import PermissionButton from '@/components/business/PermissionButton.vue';
import { buttonCode } from '@/pages/alipay/shared';

const loading = ref(false);
const saving = ref(false);
const roots = ref<AnyRecord[]>([]);
const unclassifiedGoods = ref<AnyRecord[]>([]);
const categoryGoods = ref<AnyRecord[]>([]);
const categoryGoodsTotal = ref(0);
const categoryGoodsPage = ref(1);
const categoryGoodsPageSize = 10;
const selectedCategoryGoodIds = ref<Array<number | string>>([]);
const unclassifiedTotal = ref(0);
const unclassifiedPage = ref(1);
const unclassifiedPageSize = 20;
const selectedGoodIds = ref<Array<number | string>>([]);
const editorVisible = ref(false);
const statusVisible = ref(false);
const deleteVisible = ref(false);
const editingCode = ref('');
const pendingCategory = ref<AnyRecord>();
const pendingStatus = ref(0);
const selectedCode = ref('');
const impact = reactive<AnyRecord>({});
const form = reactive({
  code: '',
  parentCode: '',
  name: '',
  shortName: '',
  icon: 'AppOutline',
  description: '',
  coverImage: '',
  sortOrder: 100,
  status: 1,
});

const iconNames = [
  'CompassOutline',
  'VideoOutline',
  'SetOutline',
  'AppOutline',
  'AppstoreOutline',
  'TravelOutline',
  'CameraOutline',
  'PlayOutline',
  'LoopOutline',
  'AudioOutline',
  'CheckShieldOutline',
  'PictureOutline',
  'GiftOutline',
  'TagOutline',
];
const iconOptions = iconNames.map((name) => ({ label: name, value: name }));
const iconComponents: Record<string, Component> = {
  CompassOutline: CompassIcon,
  VideoOutline: VideoCameraIcon,
  SetOutline: SettingIcon,
  AppOutline: AppIcon,
  AppstoreOutline: ViewModuleIcon,
  TravelOutline: FlightTakeoffIcon,
  CameraOutline: CameraIcon,
  PlayOutline: PlayCircleIcon,
  LoopOutline: RefreshIcon,
  AudioOutline: AudioIcon,
  CheckShieldOutline: CheckCircleIcon,
  PictureOutline: ImageIcon,
  GiftOutline: GiftIcon,
  TagOutline: TagIcon,
};
const iconComponent = (name?: string) => iconComponents[name || ''] || AppIcon;

const flatCategories = computed(() =>
  roots.value.flatMap((root) => [
    { ...root, level: 1 },
    ...(root.children || []).map((child: AnyRecord) => ({ ...child, level: 2 })),
  ]),
);
const enabledCount = computed(() => flatCategories.value.filter((item) => item.status === 1).length);
const catalogGoodsTotal = computed(() => flatCategories.value
  .filter((item) => item.parentCode || !(item.children || []).length)
  .reduce((sum, item) => sum + Number(item.goodsCount || 0), 0));
const selectedCategory = computed(() => flatCategories.value.find((item) => item.code === selectedCode.value));
const parentOptions = computed(() =>
  roots.value.filter((item) => item.code !== editingCode.value).map((item) => ({ label: item.name, value: item.code })),
);
const canBindToSelectedCategory = computed(() =>
  Boolean(
    selectedCategory.value
      && selectedCategory.value.status === 1
      && (!selectedCategory.value.children || selectedCategory.value.children.length === 0),
  ),
);

const resetForm = () =>
  Object.assign(form, {
    code: '',
    parentCode: '',
    name: '',
    shortName: '',
    icon: 'AppOutline',
    description: '',
    coverImage: '',
    sortOrder: 100,
    status: 1,
  });

const loadCategories = async () => {
  loading.value = true;
  try {
    roots.value = resolveData<AnyRecord[]>(await rentApi.listCatalogCategories(), []);
    if (!selectedCode.value || !flatCategories.value.some((item) => item.code === selectedCode.value)) {
      selectedCode.value = roots.value[0]?.children?.[0]?.code || roots.value[0]?.code || '';
    }
  } finally {
    loading.value = false;
  }
};

const selectCategory = (category: AnyRecord) => {
  selectedCode.value = category.code;
  categoryGoodsPage.value = 1;
  selectedGoodIds.value = [];
  void loadCategoryGoods();
};

const loadCategoryGoods = async () => {
  if (!selectedCode.value) return;
  const page = resolvePage<AnyRecord>(await rentApi.pageCategoryGoods({
    categoryCode: selectedCode.value,
    page: categoryGoodsPage.value,
    limit: categoryGoodsPageSize,
  }));
  categoryGoods.value = page.list;
  categoryGoodsTotal.value = page.total;
  selectedCategoryGoodIds.value = [];
};

const loadUnclassified = async () => {
  const page = resolvePage<AnyRecord>(
    await rentApi.pageUnclassifiedCatalogGoods({ page: unclassifiedPage.value, limit: unclassifiedPageSize }),
  );
  unclassifiedGoods.value = page.list;
  unclassifiedTotal.value = page.total;
  selectedGoodIds.value = [];
};

const openCreate = (parentCode = '') => {
  editingCode.value = '';
  resetForm();
  form.parentCode = parentCode;
  editorVisible.value = true;
};

const openEdit = (row: AnyRecord) => {
  editingCode.value = row.code;
  Object.assign(form, {
    code: row.code,
    parentCode: row.parentCode || '',
    name: row.name,
    shortName: row.shortName,
    icon: row.icon,
    description: row.description || '',
    coverImage: row.coverImage || '',
    sortOrder: row.sortOrder ?? 100,
    status: row.status,
  });
  editorVisible.value = true;
};

const saveCategory = async () => {
  if (!form.code.trim() || !form.name.trim() || !form.shortName.trim() || !form.icon)
    return MessagePlugin.warning('请填写分类编码、名称、简称并选择分类图标');
  saving.value = true;
  try {
    const payload = { ...form, parentCode: form.parentCode || null, coverImage: form.coverImage || null };
    const { status: _status, ...editPayload } = payload;
    if (editingCode.value) await rentApi.updateCatalogCategory(editPayload);
    else await rentApi.createCatalogCategory(payload);
    MessagePlugin.success(editingCode.value ? '分类已更新' : '分类已创建');
    editorVisible.value = false;
    await Promise.all([loadCategories(), loadUnclassified()]);
  } finally {
    saving.value = false;
  }
};

const prepareStatus = async (row: AnyRecord) => {
  pendingCategory.value = row;
  pendingStatus.value = row.status === 1 ? 0 : 1;
  Object.assign(impact, resolveData<AnyRecord>(await rentApi.getCatalogStatusImpact(row.code), {}));
  statusVisible.value = true;
};

const applyStatus = async () => {
  if (!pendingCategory.value || impact.enabledChildCount) return;
  saving.value = true;
  try {
    const key = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`;
    await rentApi.updateCatalogCategoryStatus(
      { code: pendingCategory.value.code, status: pendingStatus.value, confirmed: true },
      key,
    );
    MessagePlugin.success(pendingStatus.value === 1 ? '分类已启用' : '分类已停用');
    statusVisible.value = false;
    await loadCategories();
  } finally {
    saving.value = false;
  }
};

const prepareDelete = (row: AnyRecord) => {
  pendingCategory.value = row;
  deleteVisible.value = true;
};
const deleteCategory = async () => {
  if (!pendingCategory.value) return;
  saving.value = true;
  try {
    await rentApi.deleteCatalogCategory(pendingCategory.value.code);
    MessagePlugin.success('分类已删除');
    deleteVisible.value = false;
    await loadCategories();
  } finally {
    saving.value = false;
  }
};

const bindGoods = async () => {
  if (!canBindToSelectedCategory.value || !selectedCode.value || selectedGoodIds.value.length === 0) return;
  saving.value = true;
  try {
    const selectedCount = selectedGoodIds.value.length;
    await rentApi.bindCatalogGoods({ categoryCode: selectedCode.value, goodIds: selectedGoodIds.value });
    MessagePlugin.success(`已将 ${selectedCount} 个商品加入“${selectedCategory.value?.name || selectedCode.value}”`);
    await Promise.all([loadCategories(), loadCategoryGoods(), loadUnclassified()]);
  } finally {
    saving.value = false;
  }
};

const unbindGoods = async (goodIds: Array<number | string>) => {
  if (!goodIds.length) return;
  saving.value = true;
  try {
    await rentApi.unbindCatalogGoods({ goodIds });
    MessagePlugin.success(`已将 ${goodIds.length} 个商品移出类目`);
    await Promise.all([loadCategories(), loadCategoryGoods(), loadUnclassified()]);
  } finally {
    saving.value = false;
  }
};

const unbindSelectedGoods = () => unbindGoods([...selectedCategoryGoodIds.value]);

onMounted(async () => {
  await loadCategories();
  await Promise.all([loadCategoryGoods(), loadUnclassified()]);
});
</script>
<style scoped>
.catalog-page {
  min-height: 100%;
  padding: 24px;
  background: #f4f7fb;
  color: #17233d;
}

.catalog-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 18px;
  color: #fff;
  background: linear-gradient(120deg, #123b67, #1767a5);
  box-shadow: 0 16px 36px rgb(20 70 110 / 18%);
}

.catalog-hero h1 {
  margin: 6px 0 8px;
  font-size: 30px;
}

.catalog-hero p {
  max-width: 780px;
  margin: 0;
  line-height: 1.7;
  color: rgb(255 255 255 / 78%);
}

.catalog-kicker {
  font-size: 12px;
  letter-spacing: 0.14em;
  color: #8ed7ff;
}

.boundary-grid,
.summary-grid,
.content-grid {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.boundary-grid {
  grid-template-columns: repeat(2, 1fr);
}

.boundary-grid article {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
  border: 1px solid #dce6f2;
  border-radius: 14px;
  background: #fff;
}

.boundary-grid article span,
.card-help {
  color: #66758c;
  font-size: 13px;
  line-height: 1.6;
}

.boundary-grid__active {
  border-color: #77b8e8 !important;
  background: #f0f8ff !important;
}

.summary-grid {
  grid-template-columns: repeat(4, 1fr);
}

.summary-grid div {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px;
  border-radius: 14px;
  background: #fff;
}

.summary-grid span {
  color: #74839a;
  font-size: 13px;
}

.summary-grid strong {
  font-size: 26px;
}

.content-grid {
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.75fr);
  align-items: start;
}

.catalog-card {
  border-radius: 14px;
  box-shadow: 0 8px 24px rgb(42 68 96 / 7%);
}

.category-name {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.category-name small,
.goods-item small {
  color: #8996a8;
}

.category-name--child {
  padding-left: 24px;
  position: relative;
}

.category-name--child::before {
  position: absolute;
  left: 6px;
  content: '↳';
  color: #8ca0b8;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
}

.goods-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.goods-pagination {
  margin-top: 16px;
}

.selection-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 13px;
}

.icon-value,
.icon-option {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.icon-preview {
  width: 20px;
  height: 20px;
  color: #1767a5;
}

.goods-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e6edf5;
  border-radius: 10px;
}

.goods-item span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.impact-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  line-height: 1.7;
}

.impact-box p {
  margin: 0;
}

.catalog-hero {
  border: 1px solid #dbe7f5;
  color: #15233c;
  background: linear-gradient(135deg, #fff 0%, #f0f6ff 100%);
  box-shadow: 0 14px 34px rgb(44 76 118 / 8%);
}
.catalog-hero p { color: #64748b; }
.catalog-kicker { color: #2563eb; }

.catalog-workspace {
  display: grid;
  grid-template-columns: minmax(300px, .72fr) minmax(460px, 1.28fr);
  gap: 18px;
  margin-top: 18px;
  align-items: start;
}
.catalog-tree-panel, .catalog-detail-panel, .category-overview {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 26px rgb(34 64 104 / 6%);
}
.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid #edf1f6; }
.panel-heading div, .tree-node__copy { display: flex; flex-direction: column; gap: 3px; }
.panel-heading span { font-weight: 600; }
.panel-heading small, .tree-node__copy small { color: #94a3b8; }
.catalog-tree { padding: 10px; }
.tree-group + .tree-group { margin-top: 5px; }
.tree-node { width: 100%; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 11px; padding: 11px 12px; border: 0; border-radius: 11px; color: #334155; background: transparent; text-align: left; cursor: pointer; }
.tree-node:hover { background: #f7faff; }
.tree-node.active { color: #1d4ed8; background: #edf4ff; }
.tree-node__icon { display: flex; width: 24px; color: #3b82f6; }
.tree-node__icon svg { width: 20px; }
.tree-node__count { min-width: 28px; padding: 3px 8px; border-radius: 999px; background: #f1f5f9; text-align: center; font-size: 12px; }
.tree-children { margin-left: 20px; padding-left: 12px; border-left: 1px solid #dbe5f1; }
.tree-node--child { grid-template-columns: 14px 1fr auto; }
.tree-branch { color: #a7b5c7; }
.catalog-detail-panel { padding: 16px; }
.category-overview { display: grid; grid-template-columns: auto 1fr auto; gap: 16px; align-items: center; padding: 22px; background: linear-gradient(135deg, #f8fbff, #fff); }
.category-overview__icon { display: grid; place-items: center; width: 54px; height: 54px; border-radius: 15px; color: #2563eb; background: #eaf2ff; }
.category-overview__icon svg { width: 26px; height: 26px; }
.category-overview__copy span { color: #3b82f6; font-size: 12px; }
.category-overview__copy h2 { margin: 3px 0 5px; }
.category-overview__copy p { margin: 0; color: #64748b; }
.category-overview__stats { display: flex; flex-direction: column; align-items: center; padding: 0 18px; border-left: 1px solid #e2e8f0; }
.category-overview__stats strong { font-size: 28px; }
.category-overview__stats span { color: #94a3b8; font-size: 12px; }
.category-overview .row-actions { grid-column: 2 / -1; gap: 8px; }
.category-goods-card, .unclassified-card { margin-top: 16px; box-shadow: none; border: 1px solid #edf1f6; }

@media (width <= 1100px) {
  .content-grid, .catalog-workspace {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (width <= 680px) {
  .catalog-page {
    padding: 14px;
  }

  .catalog-hero,
  .boundary-grid {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
