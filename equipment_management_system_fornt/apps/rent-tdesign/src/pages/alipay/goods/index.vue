<template>
  <div id="deviceList">
    <AlipayCleanListPage
      title="租赁商品"
      description="维护本地租赁商品、SKU 和上下架；支付宝状态仅表示发布通道，小程序归类请到独立的“小程序目录”页面维护。"
      :rows="listData"
      row-key="goodId"
      :loading="loading"
      :pagination="pagination"
      content-title="商品列表"
      empty-title="暂无商品"
      @page-change="handlePageChange"
    >
      <template #filters>
        <t-input
          v-model="tableParams.goodTitle"
          placeholder="请输入商品名称"
          clearable
        />
        <t-select
          v-model="tableParams.status"
          class="status-select"
          placeholder="商品状态"
          filterable
          v-bind="searchableSelectProps('goods.status', statusOptions)"
          @change="changeStatus"
        />
      </template>

      <template #queryActions>
        <AlipayQueryActions @search="getData" @reset="resetSearchFilters" />
      </template>

      <template #contentActions>
        <t-button theme="primary" variant="outline" @click="displaySettingsVisible = true">
          <template #icon><t-icon name="setting" /></template>
          显示设置
        </t-button>
        <permission-button :code="buttonCode('goods', 'create')" theme="primary" variant="outline" @click="tapAdd">添加商品</permission-button>
        <permission-button :code="buttonCode('goods', 'delete')" theme="primary" variant="outline" @click="delMore">
          批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span>
        </permission-button>
        <t-button theme="primary" variant="outline" @click="getData">刷新</t-button>
      </template>

      <template #record="{ row }">
        <article class="goods-record">
          <div class="goods-record__check">
            <t-checkbox :checked="isSelected(row)" @change="(checked: boolean) => toggleSelection(row, checked)" />
          </div>
          <div class="goods-record__cover">
            <t-image-viewer v-if="row.goodCover" :images="[resolveUploadAssetPath(row.goodCover)]">
              <template #trigger="{ open }">
                <button class="image-preview-trigger image-preview-trigger--record" type="button" @click.stop="open">
                  <t-image
                    :src="resolveUploadAssetPath(row.goodCover)"
                    fit="contain"
                  />
                  <span class="image-preview-mask">
                    <t-icon name="browse" />
                  </span>
                </button>
              </template>
            </t-image-viewer>
            <div v-else class="goods-record__cover-empty">无图</div>
          </div>
          <div class="goods-record__main">
            <div class="goods-record__summary">
              <div class="goods-record__title">
                <strong>{{ row.goodTitle || '-' }}</strong>
                <div class="goods-record__ids">
                  <span>本地商品ID：{{ row.goodId || '-' }}</span>
                  <span>支付宝商品ID：{{ alipayGoodsId(row) }}</span>
                </div>
              </div>
              <div class="goods-record__tags">
                <t-tag :theme="localStatusTheme(row)" variant="light">{{ localStatusText(row) }}</t-tag>
                <t-tag variant="light">小程序目录：{{ row.categoryCode || '未归类' }}</t-tag>
                <t-tag variant="light" :theme="auditTheme(row.alipayAuditStatus)">{{ formatAlipayAuditStatus(row.alipayAuditStatus) }}</t-tag>
                <t-tag variant="light" :theme="spuTheme(row.alipaySpuStatus)">{{ formatAlipaySpuStatus(row.alipaySpuStatus) }}</t-tag>
              </div>
            </div>
            <p v-if="isGoodsFieldVisible('goodDesc')" class="goods-record__desc">{{ row.goodDesc || '-' }}</p>
            <div v-if="visibleGoodsMetaFields.length" class="goods-record__meta">
              <div v-for="field in visibleGoodsMetaFields" :key="field.key">
                <span>{{ field.label }}</span>
                <strong :class="field.valueClass?.(row)">{{ field.value(row) }}</strong>
              </div>
            </div>
            <div v-if="isGoodsFieldVisible('sku')" class="goods-record__sku">
              <div class="goods-record__sku-head">
                <span>SKU / 规格</span>
                <em>{{ skuSummaryText(row) }}</em>
              </div>
              <div v-if="visibleSkuRows(row).length" class="goods-record__sku-list">
                <div v-for="sku in visibleSkuRows(row)" :key="sku.attrId || sku.apilyGoodSkuId || sku.attrTitle" class="goods-record__sku-item">
                  <strong>{{ sku.attrTitle || '默认规格' }}</strong>
                  <span>本地SKU：{{ sku.attrId || '-' }}</span>
                  <span>支付宝SKU：{{ alipaySkuId(sku) }}</span>
                  <span>{{ installmentText(sku) }}</span>
                  <em>￥{{ fenToYuan(sku.attrAmount) }}/天 · 押金 ￥{{ fenToYuan(sku.attrDeposit) }}</em>
                </div>
                <button
                  v-if="canToggleSkuRows(row)"
                  type="button"
                  class="goods-record__sku-more"
                  @click.stop="toggleSkuRows(row)"
                >
                  {{ skuToggleText(row) }}
                  <t-icon :name="isSkuExpanded(row) ? 'chevron-up' : 'chevron-down'" />
                </button>
              </div>
              <span v-else class="goods-record__sku-empty">{{ skuSummaryLoading ? '规格加载中' : '暂无规格' }}</span>
            </div>
            <div class="goods-record__actions">
              <template v-for="action in visibleGoodsActions(row)" :key="action.key">
                <permission-button
                  v-if="action.code"
                  :code="action.code"
                  :theme="action.theme"
                  variant="text"
                  size="small"
                  @click="action.handler"
                >
                  {{ action.label }}
                </permission-button>
                <t-button v-else :theme="action.theme" variant="text" size="small" @click="action.handler">
                  {{ action.label }}
                </t-button>
              </template>
            </div>
          </div>
        </article>
      </template>

      <display-settings-dialog
        v-model:visible="displaySettingsVisible"
        title="租赁商品显示设置"
        :groups="displaySettingGroups"
        :model-value="displaySettings.state"
        @save="saveDisplaySettings"
      />
    </AlipayCleanListPage>

    <t-drawer
      v-model:visible="editVisible"
      :header="dialogTitle"
      :size="goodsDrawerSize"
      placement="right"
      drawer-class-name="goods-drawer goods-edit-drawer"
      destroy-on-close
      :close-on-overlay-click="false"
      :prevent-scroll-through="true"
    >
      <div class="goods-drawer-body">
        <div class="goods-form-tabs" role="tablist">
          <button
            v-for="item in goodsFormSections"
            :key="item.key"
            type="button"
            class="goods-form-tab"
            :class="{
              'goods-form-tab--active': activeGoodsFormSection === item.key,
              'goods-form-tab--ready': isGoodsFormSectionReady(item.key),
            }"
            @click="openGoodsFormSection(item.key)"
          >
            <span class="goods-form-tab__index">{{ item.index }}</span>
            <span class="goods-form-tab__copy">
              <strong>{{ item.label }}</strong>
              <em>{{ item.description }}</em>
            </span>
          </button>
        </div>

        <t-form class="goods-form pro-form" :data="goodForm" label-align="top">
          <section v-show="activeGoodsFormSection === 'base'" class="pro-form-group">
            <div class="pro-form-group__header">
              <div>
                <span>基础信息</span>
                <p>填写商品标题、状态、价格和活动信息。</p>
              </div>
            </div>
            <div class="pro-form-grid">
              <t-form-item label="商品名称">
                <t-input v-model="goodForm.goodTitle" placeholder="请输入商品名称" />
              </t-form-item>
              <t-form-item label="商品状态">
                <t-radio-group v-model="goodForm.status">
                  <t-radio :value="1">上架</t-radio>
                  <t-radio :value="0">下架</t-radio>
                </t-radio-group>
              </t-form-item>
              <t-form-item class="goods-form-item--full" label="租赁组件类目">
                <div class="alipay-category-field">
                  <t-select
                    v-model="goodForm.alipayRentCategoryId"
                    :options="alipayRentCategoryOptions"
                    :loading="alipayRentCategoryLoading"
                    clearable
                    filterable
                    placeholder="请先选择租赁组件类目"
                    @change="handleRentCategoryChange"
                  />
                  <p v-if="selectedAlipayRentCategoryPath" class="form-tip">{{ selectedAlipayRentCategoryPath }}</p>
                </div>
              </t-form-item>
              <t-form-item class="goods-form-item--full" label="支付宝商品开放类目">
                <div class="alipay-category-field">
                  <t-select
                    v-model="goodForm.alipayCategoryId"
                    :options="alipayCategoryOptions"
                    :loading="alipayCategoryLoading"
                    :disabled="!goodForm.alipayRentCategoryId"
                    clearable
                    filterable
                    placeholder="请先选择租赁组件类目，再选择商品开放类目"
                    @change="handleAlipayCategoryChange"
                  />
                  <p v-if="selectedAlipayCategoryPath" class="form-tip">{{ selectedAlipayCategoryPath }}</p>
                </div>
              </t-form-item>
              <t-form-item label="商品成色">
                <t-select
                  v-model="goodForm.itemFineness"
                  :options="itemFinenessOptions"
                  filterable
                  placeholder="请选择商品成色"
                />
              </t-form-item>
              <t-form-item v-if="goodForm.itemFineness === 'secondHand'" label="成色等级">
                <t-select
                  v-model="goodForm.itemFinenessGrade"
                  :options="itemFinenessGradeOptions"
                  filterable
                  placeholder="请选择成色等级"
                />
              </t-form-item>
              <t-form-item class="goods-form-item--full" label="商品描述">
                <t-input v-model="goodForm.goodDesc" placeholder="请输入商品描述" />
              </t-form-item>
              <t-form-item label="本店活动">
                <t-input v-model="goodForm.goodAct" placeholder="本店活动(15字以内)" />
              </t-form-item>
              <t-form-item label="最低价格/元">
                <t-input-number v-model="goodForm.goodMinamo" theme="normal" placeholder="请输入最低价格(元)（只用作显示）" />
              </t-form-item>
              <t-form-item label="运费（元）">
                <t-input-number v-model="goodForm.freight" theme="normal" placeholder="请输入运费(元)" />
              </t-form-item>
              <t-form-item label="配送方式">
                <t-radio-group v-model="goodForm.offlinePickup">
                  <t-radio :value="1">仅快递</t-radio>
                  <t-radio :value="0">支持自提</t-radio>
                </t-radio-group>
              </t-form-item>
            </div>
          </section>

          <section v-show="activeGoodsFormSection === 'sku'" class="pro-form-group">
            <div class="pro-form-group__header">
              <div>
                <span>SKU / 规格</span>
                <p>新增商品先保存基础信息，再补充 SKU；修改 SKU 会立即生效。</p>
              </div>
              <t-button v-if="goodForm.goodId" theme="primary" variant="outline" size="small" @click="addAttrDialog">
                <template #icon><t-icon name="add" /></template>
                添加 SKU
              </t-button>
            </div>
            <div v-if="goodForm.goodId" class="attr-section">
              <t-table
                row-key="attrId"
                class="attr-table"
                size="small"
                table-layout="fixed"
                :data="attrList"
                :columns="attrColumns"
                cell-empty-content="-"
              >
                <template #attrSlid="{ row }">
                  <t-image-viewer v-if="row.attrSlid" :images="[resolveUploadAssetPath(row.attrSlid)]">
                    <template #trigger="{ open }">
                      <button class="image-preview-trigger image-preview-trigger--thumb" type="button" @click.stop="open">
                        <t-image
                          class="attr-thumb"
                          :src="resolveUploadAssetPath(row.attrSlid)"
                          fit="contain"
                        />
                        <span class="image-preview-mask">
                          <t-icon name="browse" />
                        </span>
                      </button>
                    </template>
                  </t-image-viewer>
                </template>
                <template #attrAmount="{ row }">{{ fenToYuan(row.attrAmount) }}</template>
                <template #attrDeposit="{ row }">{{ fenToYuan(row.attrDeposit) }}</template>
                <template #penalAmount="{ row }">{{ fenToYuan(row.penalAmount) }}</template>
                <template #buyout="{ row }">{{ Number(row.buyout || 0) === 1 ? '允许' : '不允许' }}</template>
                <template #buyoutval="{ row }">{{ fenToYuan(row.buyoutval) }}</template>
                <template #installment="{ row }">{{ installmentText(row) }}</template>
                <template #attrOperation="{ row }">
                  <t-space size="small">
                    <t-button theme="primary" variant="text" size="small" @click="editAttrDialog(row)">修改</t-button>
                    <t-button theme="danger" variant="text" size="small" @click="deleteAttr(row)">删除</t-button>
                  </t-space>
                </template>
              </t-table>
            </div>
            <div v-else class="sku-empty-state">
              <strong>先保存商品基础信息</strong>
              <p>SKU 需要绑定本地商品 ID。保存基础信息后会自动停留在 SKU 分组，可继续添加租期、价格、押金和库存。</p>
              <t-button theme="primary" @click="submitGood">保存基础信息</t-button>
            </div>
          </section>

          <section v-show="activeGoodsFormSection === 'images'" class="pro-form-group">
            <div class="pro-form-group__header">
              <div>
                <span>图片素材</span>
                <p>维护商品封面和轮播图，图片保留原比例展示，点击可查看大图。</p>
              </div>
            </div>
            <div class="pro-form-grid pro-form-grid--single">
              <t-form-item class="goods-form-item--full" label="商品封面图">
                <div class="image-upload-panel image-upload-panel--cover">
                  <div class="image-preview-card image-preview-card--cover">
                    <t-image-viewer v-if="goodForm.goodCover" :images="[resolveUploadAssetPath(goodForm.goodCover)]">
                      <template #trigger="{ open }">
                        <button class="image-preview-trigger image-preview-trigger--cover" type="button" @click.stop="open">
                          <t-image
                            class="cover-preview"
                            :src="resolveUploadAssetPath(goodForm.goodCover)"
                            fit="contain"
                          />
                          <span class="image-preview-mask">
                            <t-icon name="browse" />
                          </span>
                        </button>
                      </template>
                    </t-image-viewer>
                    <div v-else class="image-empty image-empty--cover">
                      <t-icon name="image" />
                      <span>暂无封面</span>
                    </div>
                  </div>
                  <div class="image-upload-content">
                    <t-upload theme="file" :request-method="(files: any) => uploadImage(files, 'goodCover')" accept="image/png,image/jpg,image/jpeg">
                      <t-button theme="primary" variant="outline">
                        <template #icon><t-icon name="upload" /></template>
                        上传封面图
                      </t-button>
                    </t-upload>
                    <span class="image-upload-note">{{ goodForm.goodCover ? '已上传封面图，点击左侧可预览大图' : '上传后自动保存封面图' }}</span>
                  </div>
                </div>
              </t-form-item>
              <t-form-item class="goods-form-item--full" label="商品轮播图">
                <div class="image-upload-panel image-upload-panel--gallery">
                  <div class="slider-preview-list">
                    <div
                      v-for="(url, index) in resolvedSliderPreviewList"
                      :key="`${url}-${index}`"
                      class="slider-preview-item"
                    >
                      <t-image-viewer
                        :images="resolvedSliderPreviewList"
                        :default-index="index"
                      >
                        <template #trigger="{ open }">
                          <button class="image-preview-trigger image-preview-trigger--slider" type="button" @click.stop="open(index)">
                            <t-image
                              class="slider-preview"
                              :src="url"
                              fit="contain"
                            />
                            <span class="image-preview-mask">
                              <t-icon name="browse" />
                            </span>
                          </button>
                        </template>
                      </t-image-viewer>
                      <button
                        class="slider-preview-delete"
                        type="button"
                        title="删除轮播图"
                        @click.stop="removeSliderImage(index)"
                      >
                        <t-icon name="delete" />
                        </button>
                    </div>
                    <div v-if="!sliderPreviewList.length" class="image-empty">
                      <t-icon name="image" />
                      <span>暂无轮播图</span>
                    </div>
                  </div>
                  <div class="image-upload-content">
                    <t-upload theme="file" :request-method="(files: any) => uploadImage(files, 'goodSlid')" accept="image/png,image/jpg,image/jpeg">
                      <t-button theme="primary" variant="outline">
                        <template #icon><t-icon name="upload" /></template>
                        上传轮播图
                      </t-button>
                    </t-upload>
                    <span class="image-upload-note">
                      {{ sliderPreviewList.length ? `已上传 ${sliderPreviewList.length} 张轮播图，点击图片可预览大图，右上角可删除` : '上传后自动追加轮播图' }}
                    </span>
                  </div>
                </div>
              </t-form-item>
            </div>
          </section>

          <section v-show="activeGoodsFormSection === 'detail'" class="pro-form-group">
            <div class="pro-form-group__header">
              <div>
                <span>商品详情</span>
                <p>编辑支付宝商品详情页展示的图文介绍。</p>
              </div>
            </div>
            <div class="pro-form-grid pro-form-grid--single">
              <t-form-item class="goods-form-item--full" label="商品介绍">
                <div class="rich-editor rich-editor--plugin">
                  <Toolbar class="rich-editor__toolbar" :editor="editorRef" :default-config="toolbarConfig" mode="default" />
                  <Editor
                    v-model="goodForm.goodCon"
                    class="rich-editor__content"
                    :default-config="editorConfig"
                    mode="default"
                    @on-created="handleEditorCreated"
                  />
                </div>
              </t-form-item>
            </div>
          </section>
        </t-form>
      </div>
      <template #footer>
        <div class="goods-drawer-footer">
          <t-button theme="default" @click="editVisible = false">取消</t-button>
          <t-button theme="primary" @click="submitGood">保存</t-button>
        </div>
      </template>
    </t-drawer>

    <t-drawer
      v-model:visible="attrVisible"
      :header="attrDialogTitle"
      :size="attrDrawerSize"
      placement="right"
      drawer-class-name="goods-drawer goods-attr-drawer"
      destroy-on-close
      :close-on-overlay-click="false"
      :prevent-scroll-through="true"
    >
      <div class="goods-drawer-body">
        <t-form class="goods-attr-form pro-form" :data="attrForm" label-align="top">
          <section class="pro-form-group">
            <div class="pro-form-group__header">
              <div>
                <span>SKU 信息</span>
                <p>维护租期、价格、押金、免押和库存。</p>
              </div>
            </div>
            <div class="pro-form-grid">
              <t-form-item class="goods-form-item--full" label="SKU 封面图">
                <div class="image-upload-panel image-upload-panel--attr">
                  <div class="image-preview-card image-preview-card--attr">
                    <t-image-viewer v-if="attrForm.attrSlid" :images="[resolveUploadAssetPath(attrForm.attrSlid)]">
                      <template #trigger="{ open }">
                        <button class="image-preview-trigger image-preview-trigger--attr" type="button" @click.stop="open">
                          <t-image
                            class="attr-preview"
                            :src="resolveUploadAssetPath(attrForm.attrSlid)"
                            fit="contain"
                          />
                          <span class="image-preview-mask">
                            <t-icon name="browse" />
                          </span>
                        </button>
                      </template>
                    </t-image-viewer>
                    <div v-else class="image-empty image-empty--attr">
                      <t-icon name="image" />
                      <span>暂无封面</span>
                    </div>
                  </div>
                  <div class="image-upload-content">
                    <t-upload theme="file" :request-method="(files: any) => uploadAttrImage(files)" accept="image/png,image/jpg,image/jpeg">
                      <t-button theme="primary" variant="outline">
                        <template #icon><t-icon name="upload" /></template>
                        上传封面图
                      </t-button>
                    </t-upload>
                    <span class="image-upload-note">{{ attrForm.attrSlid ? '已上传 SKU 封面，点击左侧可预览大图' : '上传后自动保存 SKU 封面' }}</span>
                  </div>
                </div>
              </t-form-item>
              <t-form-item label="SKU 名称"><t-input v-model="attrForm.attrTitle" /></t-form-item>
              <t-form-item label="库存(件)"><t-input-number v-model="attrForm.attrNum" theme="normal" /></t-form-item>
              <t-form-item label="SKU 价格(元/天)"><t-input-number v-model="attrForm.attrAmount" theme="normal" :min="1" :step="1" /></t-form-item>
              <t-form-item label="SKU 押金(元)"><t-input-number v-model="attrForm.attrDeposit" theme="normal" :min="1" :step="1" /></t-form-item>
              <t-form-item label="违约金(元/天)"><t-input-number v-model="attrForm.penalAmount" theme="normal" /></t-form-item>
              <t-form-item label="买断价格(元)">
                <div class="form-control-stack">
                  <t-input-number v-model="attrForm.buyoutval" theme="normal" :min="1" :step="1" @change="handleBuyoutPriceChange" />
                  <div class="form-tip">支付宝租赁下单金额校验使用，关闭买断时也必须大于 0。</div>
                </div>
              </t-form-item>
              <t-form-item label="是否允许买断">
                <t-switch v-model="attrForm.buyout" :custom-value="[1, 0]" :label="['允许', '不允许']" @change="handleBuyoutSwitchChange" />
              </t-form-item>
              <t-form-item label="是否租完即送">
                <t-switch v-model="attrForm.rentToSend" :custom-value="[1, 0]" :label="['是', '否']" />
              </t-form-item>
              <t-form-item label="开启免押">
                <t-switch v-model="attrForm.free" :custom-value="[1, 2]" :label="['开启', '关闭']" @change="handleFreeSwitchChange" />
              </t-form-item>
              <t-form-item label="支持分期">
                <t-switch v-model="attrForm.installmentEnabled" :custom-value="[1, 0]" :label="['开启', '关闭']" />
              </t-form-item>
              <t-form-item label="最少租赁天数"><t-input-number v-model="attrForm.minRent" theme="normal" /></t-form-item>
              <t-form-item class="goods-form-item--full" label="租赁周期">
                <div class="form-control-stack">
                  <t-select v-model="attrForm.attrRentday" multiple :options="rentDayOptions" placeholder="请选择租赁周期" />
                  <div class="form-tip">至少选择一个周期，至多选择三个周期</div>
                </div>
              </t-form-item>
              <t-form-item v-if="Number(attrForm.installmentEnabled || 0) === 1" class="goods-form-item--full" label="可选分期期数">
                <div class="form-control-stack">
                  <t-select v-model="attrForm.installmentPeriods" multiple :options="installmentPeriodOptions" placeholder="请选择分期期数" />
                  <div class="form-tip">1 表示不分期；最多支持 24 期，保存后小程序下单页可选择。</div>
                </div>
              </t-form-item>
            </div>
          </section>
        </t-form>
      </div>
      <template #footer>
        <div class="goods-drawer-footer">
          <t-button theme="default" @click="attrVisible = false">取消</t-button>
          <t-button theme="primary" @click="submitAttr">保存</t-button>
        </div>
      </template>
    </t-drawer>

    <t-dialog v-model:visible="syncLogDialogVisible" :header="syncLogTitle" width="1120px" :footer="false">
      <div class="sync-log-table-card">
        <t-table
          row-key="id"
          :data="syncLogList"
          :columns="syncLogColumns"
          :loading="syncLogLoading"
          :pagination="syncLogPagination"
          :disable-data-page="true"
          @page-change="handleSyncLogPageChange"
        >
          <template #syncStatus="{ row }">
            <t-tag :theme="syncStatusTheme(row.syncStatus)" variant="light">{{ formatSyncStatus(row.syncStatus) }}</t-tag>
          </template>
          <template #syncMode="{ row }">{{ formatSyncMode(row.syncMode) }}</template>
          <template #message="{ row }">{{ syncLogSummary(row) }}</template>
          <template #startedAt="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          <template #finishedAt="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
          <template #syncOperation="{ row }">
            <t-button variant="text" theme="primary" @click="showSyncLogDetail(row)">详情</t-button>
          </template>
        </t-table>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="syncLogDetailVisible" header="商品同步日志详情" width="1100px" :footer="false">
      <div v-if="currentSyncLog" class="sync-log-detail-sections">
        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <t-descriptions bordered :column="2" size="small">
            <t-descriptions-item label="同步状态">{{ formatSyncStatus(currentSyncLog.syncStatus) }}</t-descriptions-item>
            <t-descriptions-item label="任务ID">{{ currentSyncLog.taskId || '-' }}</t-descriptions-item>
            <t-descriptions-item label="同步模式">{{ formatSyncMode(currentSyncLog.syncMode) }}</t-descriptions-item>
            <t-descriptions-item label="支付宝商品ID">{{ currentSyncLog.alipayItemId || '-' }}</t-descriptions-item>
            <t-descriptions-item label="开始时间">{{ formatDateTime(currentSyncLog.startedAt) }}</t-descriptions-item>
            <t-descriptions-item label="完成时间">{{ formatDateTime(currentSyncLog.finishedAt) }}</t-descriptions-item>
          </t-descriptions>
        </div>
        <div class="detail-section">
          <div class="detail-section-title">完整日志</div>
          <pre v-if="currentSyncLog.message" class="sync-log-stack">{{ formatSyncLogText(currentSyncLog.message) }}</pre>
          <span v-else class="detail-empty">暂无</span>
        </div>
        <div v-if="currentSyncLog.detailJson" class="detail-section">
          <div class="detail-section-title">详情 JSON</div>
          <pre class="sync-log-json">{{ formatJson(currentSyncLog.detailJson) }}</pre>
        </div>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import '@wangeditor/editor/dist/css/style.css';

import type { IDomEditor } from '@wangeditor/editor';
import { Editor, Toolbar } from '@wangeditor/editor-for-vue';
import { DialogPlugin, MessagePlugin } from 'tdesign-vue-next';
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';

import { createSearchableOptions } from '@shared/utils/search-options';
import { cleanQuery, rentApi, resolveData, resolvePage, resolveUploadAssetPath, type AnyRecord } from '@/api/rent';
import PermissionButton from '@/components/business/PermissionButton.vue';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import { buttonCode, formatDateTime } from '@/pages/alipay/shared';
import { useLocalDisplaySettings, type DisplaySettingItem, type DisplaySettingState } from '@/utils/display-settings';
import { useAutoQuery } from '@/utils/useAutoQuery';

type PageInfo = { current: number; pageSize: number };
const { searchableSelectProps } = createSearchableOptions();

const statusOptions = [
  { value: 1, label: '上架商品' },
  { value: 0, label: '下架商品' },
];

const rentDayOptions = ['7', '15', '30', '60', '90', '120', '150', '180', '210', '270', '300', '330', '360']
  .map((value) => ({ value, label: value === '30' ? '1个月' : `${value}天` }));
const installmentPeriodOptions = Array.from({ length: 24 }, (_, index) => {
  const value = index + 1;
  return { value, label: value === 1 ? '1期（不分期）' : `${value}期` };
});
const itemFinenessOptions = [
  { value: 'secondHand', label: '二手' },
  { value: 'wholeNew', label: '全新' },
];
const itemFinenessGradeOptions = [
  { value: '99new', label: '99新' },
  { value: '95new', label: '95新' },
  { value: '90new', label: '9成新' },
  { value: '80new', label: '8成新' },
  { value: '70new', label: '7成新' },
];

const tableParams = reactive({
  page: 1,
  limit: 10,
  goodTitle: '',
  status: 1,
  classfyA: '',
  classfyB: '',
  classfyId: 0,
});
const loading = ref(false);
const listData = ref<AnyRecord[]>([]);
const pageTotal = ref(0);
const selectedRows = ref<AnyRecord[]>([]);
const skuSummaryMap = ref<Record<string, AnyRecord[]>>({});
const skuSummaryLoading = ref(false);
const expandedSkuMap = ref<Record<string, boolean>>({});
let skuSummaryRequestId = 0;
const alipayCategoryLoading = ref(false);
const alipayCategories = ref<AnyRecord[]>([]);
const alipayRentCategoryLoading = ref(false);
const alipayRentCategories = ref<AnyRecord[]>([]);
const recommendedRentCategoryId = ref('');
const originalAlipayCategoryId = ref('');

const DEFAULT_VISIBLE_SKU_COUNT = 2;

const pagination = computed(() => ({
  current: tableParams.page,
  pageSize: tableParams.limit,
  total: pageTotal.value,
  pageSizeOptions: [10, 20, 50, 100],
}));

const columns = [
  { title: '', colKey: 'selection', width: 55, align: 'center' },
  { title: 'id', colKey: 'goodId', width: 80, align: 'center' },
  { title: '商品名称', colKey: 'goodTitle', minWidth: 160, ellipsis: true, align: 'center' },
  { title: '商品描述', colKey: 'goodDesc', minWidth: 180, ellipsis: true, align: 'center' },
  { title: '最小价格/元', colKey: 'goodMinamo', width: 120, align: 'center' },
  { title: '商品封面图', colKey: 'goodCover', width: 110, align: 'center' },
  { title: '本店活动', colKey: 'goodAct', minWidth: 120, ellipsis: true, align: 'center' },
  { title: '支付宝审核', colKey: 'alipayAuditStatus', width: 120, align: 'center' },
  { title: '支付宝状态', colKey: 'alipaySpuStatus', width: 120, align: 'center' },
  { title: '支付宝原因', colKey: 'alipayStatusReason', width: 180, ellipsis: true, align: 'center' },
  { title: '操作', colKey: 'operation', width: 360, fixed: 'right', align: 'center' },
];

type GoodsDisplayField = DisplaySettingItem & {
  value: (row: AnyRecord) => string;
  valueClass?: (row: AnyRecord) => string;
};
type GoodsDisplayAction = DisplaySettingItem & {
  code?: string;
  theme: 'primary' | 'success' | 'warning' | 'danger' | 'default';
  visible: (row: AnyRecord) => boolean;
  handler: () => void;
};

const goodsFieldDefinitions: GoodsDisplayField[] = [
  { key: 'goodDesc', label: '商品描述', group: '基础信息', value: (row) => row.goodDesc || '-' },
  { key: 'goodMinamo', label: '最低价', group: '价格信息', value: (row) => `￥${fenToYuan(row.goodMinamo)}`, valueClass: () => 'goods-record__price' },
  { key: 'offlinePickup', label: '配送方式', group: '履约信息', value: (row) => Number(row.offlinePickup) === 0 ? '支持自提' : '仅快递' },
  { key: 'goodAct', label: '本店活动', group: '营销信息', value: (row) => row.goodAct || '-' },
  { key: 'alipayCategoryId', label: '支付宝类目', group: '支付宝同步', value: (row) => row.alipayCategoryId || '-' },
  { key: 'alipayRentCategoryId', label: '支付宝租赁类目', group: '支付宝同步', value: (row) => row.alipayRentCategoryId || '-' },
  { key: 'itemFineness', label: '商品成色', group: '支付宝同步', value: (row) => formatItemFineness(row) },
  { key: 'alipayStatusReason', label: '支付宝原因', group: '支付宝同步', value: (row) => row.alipayStatusReason || '-' },
  { key: 'sku', label: 'SKU / 规格', group: '规格信息', value: (row) => skuSummaryText(row) },
];
const goodsMetaFieldDefinitions = goodsFieldDefinitions.filter((field) => !['goodDesc', 'sku'].includes(field.key));
const goodsActionDefinitions: DisplaySettingItem[] = [
  { key: 'up', label: '上架', group: '操作按钮' },
  { key: 'sync', label: '同步', group: '操作按钮' },
  { key: 'log', label: '日志', group: '操作按钮' },
  { key: 'edit', label: '修改', group: '操作按钮' },
  { key: 'lower', label: '下架', group: '操作按钮' },
  { key: 'sort', label: '置顶', group: '操作按钮' },
];
const displaySettingsVisible = ref(false);
const displayDefinitions = computed(() => ({
  fields: goodsFieldDefinitions,
  actions: goodsActionDefinitions,
}));
const displaySettings = useLocalDisplaySettings({
  scope: 'alipay.goods',
  version: 1,
  definitions: displayDefinitions,
});
const displaySettingGroups = computed<DisplaySettingGroup[]>(() => [
  {
    kind: 'fields',
    label: '字段',
    title: '商品卡片字段',
    description: '控制商品卡片中的描述、价格、活动、同步原因和 SKU 区块。',
    items: goodsFieldDefinitions,
  },
  {
    kind: 'actions',
    label: '操作按钮',
    title: '商品操作按钮',
    description: '控制商品卡片操作按钮的显示和顺序。',
    items: goodsActionDefinitions,
  },
]);
const visibleGoodsMetaFields = computed(() => displaySettings.visibleItems('fields', goodsMetaFieldDefinitions));
const isGoodsFieldVisible = (key: string) => displaySettings.state.fields.includes(key);

function goodsRecordActions(row: AnyRecord): GoodsDisplayAction[] {
  return [
    {
      key: 'up',
      label: '上架',
      group: '操作按钮',
      code: buttonCode('goods', 'update'),
      theme: 'primary',
      visible: (item) => Number(item.status || 0) === 0,
      handler: () => toUp(row),
    },
    {
      key: 'sync',
      label: '同步',
      group: '操作按钮',
      code: buttonCode('goods', 'sync'),
      theme: 'primary',
      visible: (item) => Number(item.status || 0) !== 0,
      handler: () => syncGood(row),
    },
    {
      key: 'log',
      label: '日志',
      group: '操作按钮',
      theme: 'primary',
      visible: (item) => Number(item.status || 0) !== 0,
      handler: () => openSyncLog(row),
    },
    {
      key: 'edit',
      label: '修改',
      group: '操作按钮',
      code: buttonCode('goods', 'update'),
      theme: 'primary',
      visible: (item) => Number(item.status || 0) !== 0,
      handler: () => toEdit(row),
    },
    {
      key: 'lower',
      label: '下架',
      group: '操作按钮',
      code: buttonCode('goods', 'update'),
      theme: 'danger',
      visible: (item) => Number(item.status || 0) !== 0,
      handler: () => toLower(row),
    },
    {
      key: 'sort',
      label: '置顶',
      group: '操作按钮',
      code: buttonCode('goods', 'update'),
      theme: 'warning',
      visible: (item) => Number(item.status || 0) !== 0,
      handler: () => toSort(row),
    },
  ];
}

function visibleGoodsActions(row: AnyRecord) {
  return displaySettings.visibleItems('actions', goodsRecordActions(row)).filter((action) => action.visible(row));
}

function saveDisplaySettings(next: DisplaySettingState) {
  displaySettings.save(next);
  MessagePlugin.success('显示设置已保存');
}

async function getData() {
  const requestedPage = tableParams.page;
  const requestedLimit = tableParams.limit;
  loading.value = true;
  try {
    const response = await rentApi.getDeviceList(cleanQuery(tableParams));
    const page = resolvePage<AnyRecord>(response);
    listData.value = page.list;
    pageTotal.value = page.total;
    tableParams.page = requestedPage;
    tableParams.limit = requestedLimit;
    void loadSkuSummaries(page.list);
  } finally {
    loading.value = false;
  }
}

function search() {
  tableParams.page = 1;
  getData();
}

const { pauseAutoQuery } = useAutoQuery(
  () => ({
    goodTitle: tableParams.goodTitle,
    status: tableParams.status,
    classfyA: tableParams.classfyA,
    classfyB: tableParams.classfyB,
    classfyId: tableParams.classfyId,
  }),
  search,
);

function changeStatus() {
  tableParams.goodTitle = '';
  tableParams.classfyA = '';
  tableParams.classfyB = '';
  tableParams.classfyId = 0;
}

function resetSearchFilters() {
  pauseAutoQuery(() => {
    tableParams.goodTitle = '';
    tableParams.status = 1;
    tableParams.classfyA = '';
    tableParams.classfyB = '';
    tableParams.classfyId = 0;
    return search();
  });
}

function handlePageChange(pageInfo: PageInfo) {
  tableParams.page = pageInfo.current;
  tableParams.limit = pageInfo.pageSize;
  getData();
}

function isSelected(row: AnyRecord) {
  return selectedRows.value.some((item) => item.goodId === row.goodId);
}

function toggleSelection(row: AnyRecord, checked: boolean) {
  if (checked) {
    if (!isSelected(row)) selectedRows.value.push(row);
  } else {
    selectedRows.value = selectedRows.value.filter((item) => item.goodId !== row.goodId);
  }
}

function fenToYuan(value: unknown) {
  return (Number(value || 0) / 100).toFixed(2);
}

function rowSkuKey(row: AnyRecord) {
  return String(row?.goodId ?? '');
}

function alipayGoodsId(row: AnyRecord) {
  return row.alipayGoodsId || row.alipayItemId || row.itemId || '-';
}

function alipaySkuId(row: AnyRecord) {
  return row.apilyGoodSkuId || row.alipaySkuId || row.alipay_sku_id || row.skuId || '-';
}

function parseInstallmentPeriods(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((item) => Number(item))
      .filter((item) => Number.isInteger(item) && item >= 1 && item <= 24);
  }
  return String(value || '1')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item >= 1 && item <= 24);
}

function installmentText(row: AnyRecord) {
  if (Number(row?.installmentEnabled || 0) !== 1) return '不分期';
  const periods = Array.from(new Set(parseInstallmentPeriods(row?.installmentPeriods))).sort((a, b) => a - b);
  return periods.length ? `${periods.join('/')}期` : '不分期';
}

function isAllowedItemFineness(value: unknown) {
  return itemFinenessOptions.some((item) => item.value === String(value || '').trim());
}

function formatItemFineness(value: unknown) {
  const fineness = String((value as AnyRecord)?.itemFineness || value || '').trim();
  const selected = itemFinenessOptions.find((item) => item.value === fineness);
  if (fineness === 'secondHand' || !selected) {
    return `二手 / ${formatItemFinenessGrade((value as AnyRecord)?.itemFinenessGrade)}`;
  }
  return selected.label;
}

function isAllowedItemFinenessGrade(value: unknown) {
  return itemFinenessGradeOptions.some((item) => item.value === String(value || '').trim());
}

function formatItemFinenessGrade(value: unknown) {
  const selected = itemFinenessGradeOptions.find((item) => item.value === String(value || '').trim());
  return selected ? selected.label : '95新';
}

function inlineSkuRows(row: AnyRecord) {
  const candidate = row.attrs || row.attrList || row.skus || row.skuList;
  return Array.isArray(candidate) ? candidate : [];
}

function skuRows(row: AnyRecord) {
  const inlineRows = inlineSkuRows(row);
  if (inlineRows.length) return inlineRows;
  return skuSummaryMap.value[rowSkuKey(row)] || [];
}

function isSkuExpanded(row: AnyRecord) {
  return Boolean(expandedSkuMap.value[rowSkuKey(row)]);
}

function visibleSkuRows(row: AnyRecord) {
  const rows = skuRows(row);
  return isSkuExpanded(row) ? rows : rows.slice(0, DEFAULT_VISIBLE_SKU_COUNT);
}

function hiddenSkuCount(row: AnyRecord) {
  if (isSkuExpanded(row)) return 0;
  return Math.max(skuRows(row).length - DEFAULT_VISIBLE_SKU_COUNT, 0);
}

function canToggleSkuRows(row: AnyRecord) {
  return skuRows(row).length > DEFAULT_VISIBLE_SKU_COUNT;
}

function toggleSkuRows(row: AnyRecord) {
  const key = rowSkuKey(row);
  expandedSkuMap.value = {
    ...expandedSkuMap.value,
    [key]: !expandedSkuMap.value[key],
  };
}

function skuToggleText(row: AnyRecord) {
  return isSkuExpanded(row) ? '收起' : `更多 ${hiddenSkuCount(row)} 个`;
}

function skuSummaryText(row: AnyRecord) {
  const count = skuRows(row).length;
  if (count) return `${count} 个规格`;
  return skuSummaryLoading.value ? '加载中' : '暂无规格';
}

async function loadSkuSummaries(rows: AnyRecord[]) {
  const goodRows = rows.filter((row) => row?.goodId);
  const requestId = ++skuSummaryRequestId;
  if (!goodRows.length) {
    skuSummaryMap.value = {};
    return;
  }

  const nextMap: Record<string, AnyRecord[]> = {};
  goodRows.forEach((row) => {
    const inlineRows = inlineSkuRows(row);
    if (inlineRows.length) nextMap[rowSkuKey(row)] = inlineRows;
  });

  const rowsToFetch = goodRows.filter((row) => !nextMap[rowSkuKey(row)]);
  if (!rowsToFetch.length) {
    skuSummaryMap.value = nextMap;
    return;
  }

  skuSummaryLoading.value = true;
  try {
    const detailResults = await Promise.allSettled(rowsToFetch.map(async (row) => {
      const response = await rentApi.getGoodById({ goodId: row.goodId, userId: 0, page: 1, limit: 20 });
      const data = resolveData<AnyRecord>(response, {});
      return [rowSkuKey(row), Array.isArray(data.attrs) ? data.attrs : []] as const;
    }));
    if (requestId !== skuSummaryRequestId) return;
    detailResults.forEach((result) => {
      if (result.status === 'fulfilled') {
        nextMap[result.value[0]] = result.value[1];
      }
    });
    skuSummaryMap.value = nextMap;
  } finally {
    if (requestId === skuSummaryRequestId) skuSummaryLoading.value = false;
  }
}

function buildGoodPayload(): AnyRecord {
  return {
    ...goodForm,
    freight: Math.trunc(Number(goodForm.freight || 0) * 100),
    goodMaxamo: 1,
    goodMinamo: Math.trunc(Number(goodForm.goodMinamo || 0) * 100),
    offlinePickup: Number(goodForm.offlinePickup) === 0 ? 0 : 1,
  };
}

const editVisible = ref(false);
const dialogTitle = ref('添加商品');
const goodForm = reactive<AnyRecord>(emptyGoodForm());
const attrList = ref<AnyRecord[]>([]);
const sliderPreviewList = computed(() => splitImageKeys(goodForm.goodSlid));
const resolvedSliderPreviewList = computed(() => sliderPreviewList.value.map((url) => resolveUploadAssetPath(url)));
const alipayCategoryOptions = computed(() =>
  filteredAlipayCategories.value.map((item) => ({
    label: `${item.fullPathName || item.categoryName || item.categoryId}（${item.categoryId}）`,
    value: item.categoryId,
  })),
);
const selectedAlipayCategoryPath = computed(() => {
  const selected = alipayCategories.value.find((item) => item.categoryId === goodForm.alipayCategoryId);
  if (selected) return `${selected.categoryId} · ${selected.fullPathName || selected.categoryName}`;
  return goodForm.alipayCategoryId ? `当前类目ID：${goodForm.alipayCategoryId}` : '';
});
const alipayRentCategoryOptions = computed(() =>
  alipayRentCategories.value.map((item) => ({
    label: `${item.categoryName || item.categoryId}（${item.categoryId}）`,
    value: item.categoryId,
  })),
);
const selectedAlipayRentCategoryPath = computed(() => {
  const selected = alipayRentCategories.value.find((item) => item.categoryId === goodForm.alipayRentCategoryId);
  if (selected) return `${selected.categoryId} · ${selected.scene || selected.categoryName}`;
  return goodForm.alipayRentCategoryId ? `当前租赁类目：${goodForm.alipayRentCategoryId}` : '';
});
const filteredAlipayCategories = computed(() => {
  if (!goodForm.alipayRentCategoryId) return [];
  return alipayCategories.value.filter((item) => inferRentCategoryIdFromAlipayCategory(item) === goodForm.alipayRentCategoryId);
});
const isNarrowDrawerViewport = ref(false);
const goodsDrawerSize = computed(() => isNarrowDrawerViewport.value ? '100vw' : 'min(960px, calc(100vw - 96px))');
const attrDrawerSize = computed(() => isNarrowDrawerViewport.value ? '100vw' : 'min(640px, calc(100vw - 96px))');
type GoodsFormSectionKey = 'base' | 'sku' | 'images' | 'detail';
const activeGoodsFormSection = ref<GoodsFormSectionKey>('base');
const goodsFormSections: Array<{ key: GoodsFormSectionKey; index: string; label: string; description: string }> = [
  { key: 'base', index: '01', label: '基础信息', description: '标题与价格' },
  { key: 'sku', index: '02', label: 'SKU / 规格', description: '租期与库存' },
  { key: 'images', index: '03', label: '图片素材', description: '封面与轮播' },
  { key: 'detail', index: '04', label: '商品详情', description: '图文介绍' },
];
const editorRef = shallowRef<IDomEditor>();
const toolbarConfig = {};
const editorConfig = {
  placeholder: '请输入商品介绍，可插入图片和图文说明',
  MENU_CONF: {
    uploadImage: {
      async customUpload(file: File, insertFn: (url: string, alt?: string, href?: string) => void) {
        const key = await uploadImageKey(file);
        const url = resolveUploadAssetPath(key);
        insertFn(url, file.name, url);
      },
    },
  },
};

function handleEditorCreated(editor: IDomEditor) {
  editorRef.value = editor;
}

function syncDrawerViewport() {
  if (typeof window === 'undefined') return;
  isNarrowDrawerViewport.value = window.innerWidth <= 760;
}

function openGoodsFormSection(key: GoodsFormSectionKey) {
  activeGoodsFormSection.value = key;
}

function isGoodsFormSectionReady(key: GoodsFormSectionKey) {
  if (key === 'base') return Boolean(goodForm.goodTitle && goodForm.goodDesc && goodForm.alipayCategoryId && goodForm.alipayRentCategoryId);
  if (key === 'sku') return Boolean(goodForm.goodId && attrList.value.length);
  if (key === 'images') return Boolean(goodForm.goodCover || goodForm.goodSlid);
  if (key === 'detail') return Boolean(String(goodForm.goodCon || '').trim());
  return false;
}

function emptyGoodForm(): AnyRecord {
  return {
    goodTitle: '',
    goodDesc: '',
    status: 1,
    goodAct: '',
    alipayCategoryId: '',
    alipayCategoryName: '',
    alipayCategoryPath: '',
    alipayRentCategoryId: '',
    itemFineness: 'secondHand',
    itemFinenessGrade: '95new',
    goodMinamo: null,
    freight: null,
    offlinePickup: 1,
    goodCon: '',
    goodSpepar: '',
    goodRec: '',
    goodCover: '',
    goodSlid: '',
  };
}

function resetGoodForm(row?: AnyRecord) {
  Object.keys(goodForm).forEach((key) => delete goodForm[key]);
  recommendedRentCategoryId.value = '';
  originalAlipayCategoryId.value = row?.alipayCategoryId || '';
  if (row) {
    Object.assign(goodForm, row, {
      // The editor's model watcher calls setHtml, which requires a string even for an empty database value.
      goodCon: String(row.goodCon ?? ''),
      freight: Number(row.freight || 0) / 100,
      goodMinamo: Number(row.goodMinamo || 0) / 100,
      offlinePickup: Number(row.offlinePickup) === 0 ? 0 : 1,
    });
    if (!isAllowedItemFineness(goodForm.itemFineness)) {
      goodForm.itemFineness = 'secondHand';
    }
    if (!isAllowedItemFinenessGrade(goodForm.itemFinenessGrade)) {
      goodForm.itemFinenessGrade = '95new';
    }
  } else {
    Object.assign(goodForm, emptyGoodForm());
  }
}

function tapAdd() {
  dialogTitle.value = '添加商品';
  activeGoodsFormSection.value = 'base';
  resetGoodForm();
  attrList.value = [];
  editVisible.value = true;
  void loadAlipayCategories();
  void loadAlipayRentCategories();
}

async function toEdit(row: AnyRecord) {
  dialogTitle.value = '修改商品';
  activeGoodsFormSection.value = 'base';
  resetGoodForm(row);
  editVisible.value = true;
  void loadAlipayCategories({ selectedCategoryId: row.alipayCategoryId });
  void loadAlipayRentCategories();
  await getAttrList(row.goodId);
}

async function submitGood() {
  if (!goodForm.goodTitle || !goodForm.goodDesc) {
    MessagePlugin.warning('商品名称和商品描述不能为空');
    return;
  }
  if (!goodForm.alipayRentCategoryId) {
    MessagePlugin.warning('请先选择租赁组件类目');
    return;
  }
  if (!goodForm.alipayCategoryId) {
    MessagePlugin.warning('请选择支付宝商品开放类目');
    return;
  }
  if (!isSelectedAlipayCategoryAllowed()) {
    MessagePlugin.warning('商品开放类目不属于当前租赁组件类目，请重新选择');
    return;
  }
  if (!isAllowedItemFineness(goodForm.itemFineness)) {
    MessagePlugin.warning('请选择商品成色');
    return;
  }
  if (goodForm.itemFineness === 'secondHand' && !isAllowedItemFinenessGrade(goodForm.itemFinenessGrade)) {
    MessagePlugin.warning('请选择成色等级');
    return;
  }
  const payload = buildGoodPayload();
  if (goodForm.alipayGoodsId && originalAlipayCategoryId.value && goodForm.alipayCategoryId !== originalAlipayCategoryId.value) {
    const confirmed = await confirmCategoryDialog({
      header: '确认修改支付宝商品开放类目',
      body: '该商品已同步支付宝，修改类目会触发重新提报/审核，审核期间可能影响商品展示和下单。确认继续？',
      confirmBtn: '确认修改',
      cancelBtn: '取消',
      theme: 'warning',
    });
    if (!confirmed) return;
    payload.confirmAlipayCategoryResubmit = true;
  }
  if (goodForm.goodId) {
    await rentApi.updateGood(payload);
    MessagePlugin.success('商品已修改');
    editVisible.value = false;
    getData();
  } else {
    Object.assign(payload, {
      goodCon: normalizeRichText(payload.goodCon),
      goodRec: `<p>${payload.goodRec || ''}</p>`,
      goodSpepar: `<p>${payload.goodSpepar || ''}</p>`,
    });
    const response = await rentApi.insertGood(payload);
    const created = resolveData<AnyRecord>(response, {});
    MessagePlugin.success('增加成功，商品的属性是必填的，请为此商品添加至少一项属性');
    Object.assign(goodForm, created);
    if (goodForm.goodId) {
      await getAttrList(goodForm.goodId);
    }
    activeGoodsFormSection.value = 'sku';
    getData();
  }
}

async function loadAlipayCategories(options: { selectedCategoryId?: string } = {}) {
  alipayCategoryLoading.value = true;
  try {
    const response = await rentApi.queryAlipayItemCategories({
      keyword: '',
      itemType: '2',
      catStatus: 'AUDIT_PASSED',
      limit: 500,
    });
    const rows = resolveData<AnyRecord[]>(response, []);
    alipayCategories.value = Array.isArray(rows) ? rows : [];
    syncSelectedAlipayCategoryMeta(options.selectedCategoryId || goodForm.alipayCategoryId);
    if (!isSelectedAlipayCategoryAllowed()) {
      goodForm.alipayCategoryId = '';
      goodForm.alipayCategoryName = '';
      goodForm.alipayCategoryPath = '';
      recommendedRentCategoryId.value = '';
    }
    if (alipayCategories.value.length === 0) {
      MessagePlugin.warning('未查询到支付宝商品类目');
    }
  } finally {
    alipayCategoryLoading.value = false;
  }
}

function handleAlipayCategoryChange(value: unknown) {
  syncSelectedAlipayCategoryMeta(String(value || ''));
}

function handleRentCategoryChange() {
  if (!isSelectedAlipayCategoryAllowed()) {
    goodForm.alipayCategoryId = '';
    goodForm.alipayCategoryName = '';
    goodForm.alipayCategoryPath = '';
    recommendedRentCategoryId.value = '';
  }
  if (!alipayCategories.value.length) {
    void loadAlipayCategories();
  }
}

function syncSelectedAlipayCategoryMeta(categoryId?: string) {
  const selectedId = String(categoryId || '').trim();
  const selected = alipayCategories.value.find((item) => item.categoryId === selectedId);
  goodForm.alipayCategoryName = selected?.categoryName || '';
  goodForm.alipayCategoryPath = selected?.fullPathName || selected?.categoryName || '';
  recommendedRentCategoryId.value = inferRentCategoryIdFromAlipayCategory(selected);
  if (selected && recommendedRentCategoryId.value && !goodForm.alipayRentCategoryId) {
    goodForm.alipayRentCategoryId = recommendedRentCategoryId.value;
  }
}

function inferRentCategoryIdFromAlipayCategory(item?: AnyRecord) {
  const text = `${item?.fullPathName || ''} ${item?.categoryName || ''}`.toLowerCase();
  if (text.includes('手机')) return 'RENT_PHONE';
  if (text.includes('电脑') || text.includes('笔记本') || text.includes('平板') || text.includes('显示器')) return 'RENT_COMPUTER';
  if (text.includes('相机') || text.includes('摄像') || text.includes('无人机')) return 'RENT_CAMERA';
  return '';
}

function isSelectedAlipayCategoryAllowed() {
  if (!goodForm.alipayCategoryId) return true;
  const selected = alipayCategories.value.find((item) => item.categoryId === goodForm.alipayCategoryId);
  return Boolean(selected && inferRentCategoryIdFromAlipayCategory(selected) === goodForm.alipayRentCategoryId);
}

function confirmCategoryDialog(options: {
  header: string;
  body: string;
  confirmBtn: string;
  cancelBtn: string;
  theme: 'default' | 'warning' | 'danger';
}) {
  return new Promise<boolean>((resolve) => {
    let settled = false;
    let dialog: ReturnType<typeof DialogPlugin.confirm> | null = null;
    const finish = (confirmed: boolean, hideDialog = true) => {
      if (settled) return;
      settled = true;
      if (hideDialog) dialog?.hide();
      resolve(confirmed);
    };
    dialog = DialogPlugin.confirm({
      header: options.header,
      body: options.body,
      theme: options.theme,
      confirmBtn: options.confirmBtn,
      cancelBtn: options.cancelBtn,
      closeOnOverlayClick: false,
      onConfirm: () => finish(true),
      onCancel: () => finish(false),
      onClose: () => finish(false, false),
    });
  });
}

async function loadAlipayRentCategories() {
  if (alipayRentCategories.value.length > 0) return;
  alipayRentCategoryLoading.value = true;
  try {
    const response = await rentApi.queryAlipayRentCategories({});
    const rows = resolveData<AnyRecord[]>(response, []);
    alipayRentCategories.value = Array.isArray(rows) ? rows : [];
  } finally {
    alipayRentCategoryLoading.value = false;
  }
}

function normalizeRichText(value: unknown) {
  const html = String(value || '').trim();
  if (!html) return '';
  return /<\/?[a-z][\s\S]*>/i.test(html) ? html : `<p>${html}</p>`;
}

async function delMore() {
  if (!selectedRows.value.length) {
    MessagePlugin.warning('请至少选择一条数据！');
    return;
  }
  await rentApi.fetchDelete({ goodIds: selectedRows.value.map((item) => item.goodId).join(',') });
  selectedRows.value = [];
  MessagePlugin.success('删除成功');
  getData();
}

async function runGoodAction(action: () => Promise<AnyRecord>, message: string) {
  loading.value = true;
  try {
    await action();
    MessagePlugin.success(message);
    await getData();
  } finally {
    loading.value = false;
  }
}

function toUp(row: AnyRecord) {
  runGoodAction(() => rentApi.fetchUpGood({ goodId: row.goodId }), '上架成功');
}

function toLower(row: AnyRecord) {
  runGoodAction(() => rentApi.fetchDownGood({ goodId: row.goodId }), '下架成功');
}

function toSort(row: AnyRecord) {
  runGoodAction(() => rentApi.fetchSortGoodUp({ goodId: row.goodId }), '置顶成功');
}

function syncGood(row: AnyRecord) {
  runGoodAction(() => rentApi.syncGood({ goodId: row.goodId }), '已提交支付宝商品同步任务');
}

async function uploadImage(files: any, field: 'goodCover' | 'goodSlid') {
  const key = await uploadImageKey(files);
  if (field === 'goodSlid' && goodForm.goodSlid) {
    goodForm.goodSlid = `${goodForm.goodSlid},${key}`;
  } else {
    goodForm[field] = key;
  }
  return { status: 'success', response: { url: resolveUploadAssetPath(key) } };
}

function removeSliderImage(index: number) {
  const nextList = sliderPreviewList.value.slice();
  if (!Number.isInteger(index) || index < 0 || index >= nextList.length) {
    return;
  }
  nextList.splice(index, 1);
  goodForm.goodSlid = nextList.join(',');
  MessagePlugin.success('轮播图已移除，保存商品后生效');
}

async function uploadImageKey(files: any) {
  const file = Array.isArray(files) ? files[0] : files;
  const formData = new FormData();
  formData.append('file', file.raw || file);
  formData.append('path', 'good/');
  const response = await rentApi.uploadSingle(formData);
  const data = resolveData<any>(response, {});
  return data?.path ?? data;
}

function splitImageKeys(value: unknown) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

const attrColumns = [
  { title: 'SKU 名称', colKey: 'attrTitle', width: 100, align: 'center' },
  { title: 'SKU 图', colKey: 'attrSlid', width: 80, align: 'center' },
  { title: 'SKU 价格(元/天)', colKey: 'attrAmount', width: 170, align: 'center' },
  { title: 'SKU 押金(元)', colKey: 'attrDeposit', width: 150, align: 'center' },
  { title: '违约金(元/天)', colKey: 'penalAmount', width: 150, align: 'center' },
  { title: '是否允许买断', colKey: 'buyout', width: 150, align: 'center' },
  { title: '买断金额(元)', colKey: 'buyoutval', width: 150, align: 'center' },
  { title: '分期', colKey: 'installment', width: 150, align: 'center' },
  { title: '最少租赁天数', colKey: 'minRent', width: 150, align: 'center' },
  { title: '库存', colKey: 'attrNum', width: 100, align: 'center' },
  { title: '租期', colKey: 'attrRentday', width: 150, align: 'center' },
  { title: '操作', colKey: 'attrOperation', width: 150, fixed: 'right', align: 'center' },
];

const attrVisible = ref(false);
const attrDialogTitle = ref('添加 SKU');
const attrForm = reactive<AnyRecord>(emptyAttrForm());
const currentAttr = ref<AnyRecord | null>(null);
const buyoutPriceTouched = ref(false);

function emptyAttrForm(): AnyRecord {
  return {
    attrSlid: '',
    buyout: 0,
    rentToSend: 0,
    attrTitle: '',
    buyoutval: null,
    attrAmount: null,
    attrDeposit: null,
    penalAmount: null,
    free: 2,
    installmentEnabled: 0,
    installmentPeriods: [1],
    minRent: null,
    attrRentday: [],
    attrNum: null,
  };
}

function resetAttrForm(row?: AnyRecord) {
  Object.keys(attrForm).forEach((key) => delete attrForm[key]);
  if (row) {
    Object.assign(attrForm, row, {
      attrAmount: Number(row.attrAmount || 0) / 100,
      attrDeposit: Number(row.attrDeposit || 0) / 100,
      penalAmount: Number(row.penalAmount || 0) / 100,
      buyoutval: Number(row.buyoutval || 0) / 100,
      attrRentday: String(row.attrRentday || '').split(',').filter(Boolean),
      installmentEnabled: Number(row.installmentEnabled || 0),
      installmentPeriods: parseInstallmentPeriods(row.installmentPeriods),
    });
    buyoutPriceTouched.value = Number(row.buyoutval || 0) > 0 && Number(row.buyoutval || 0) !== Number(row.attrDeposit || 0);
  } else {
    Object.assign(attrForm, emptyAttrForm());
    buyoutPriceTouched.value = false;
  }
  syncBuyoutPriceFromDeposit();
}

function handleBuyoutPriceChange() {
  buyoutPriceTouched.value = true;
}

function handleBuyoutSwitchChange() {
  syncBuyoutPriceFromDeposit();
}

function handleFreeSwitchChange() {
  if (Number(attrForm.free || 0) === 1 && !isPositiveYuan(attrForm.attrDeposit)) {
    attrForm.free = 2;
    MessagePlugin.warning('请先填写大于 0 元的 SKU 押金，再开启免押');
  }
}

function isPositiveYuan(value: unknown) {
  return Number(value || 0) > 0;
}

function syncBuyoutPriceFromDeposit() {
  const deposit = Number(attrForm.attrDeposit || 0);
  if (deposit <= 0) {
    return;
  }
  const currentBuyoutPrice = Number(attrForm.buyoutval || 0);
  if (!buyoutPriceTouched.value || currentBuyoutPrice <= 0) {
    // 买断金最小值 1 元，避免传 0 导致支付宝下单报 INVALID_PARAMETER
    attrForm.buyoutval = Math.max(deposit, 1);
  }
}

watch(
  () => [attrForm.buyout, attrForm.attrDeposit],
  () => {
    syncBuyoutPriceFromDeposit();
    if (Number(attrForm.free || 0) === 1 && !isPositiveYuan(attrForm.attrDeposit)) {
      attrForm.free = 2;
    }
  },
);

async function getAttrList(goodId: string | number) {
  const response = await rentApi.getGoodById({ goodId, userId: 0, page: 1, limit: 999 });
  const data = resolveData<AnyRecord>(response, {});
  attrList.value = data.attrs || [];
}

function addAttrDialog() {
  attrDialogTitle.value = '添加 SKU';
  currentAttr.value = null;
  resetAttrForm();
  attrVisible.value = true;
}

function editAttrDialog(row: AnyRecord) {
  attrDialogTitle.value = '修改 SKU';
  currentAttr.value = row;
  resetAttrForm(row);
  attrVisible.value = true;
}

function buildAttrPayload() {
  syncBuyoutPriceFromDeposit();
  return {
    ...currentAttr.value,
    attrAmount: Math.trunc(Number(attrForm.attrAmount || 0) * 100),
    attrDeposit: Math.trunc(Number(attrForm.attrDeposit || 0) * 100),
    penalAmount: Math.trunc(Number(attrForm.penalAmount || 0) * 100),
    free: attrForm.free,
    goodId: goodForm.goodId,
    attrSlid: attrForm.attrSlid,
    buyout: attrForm.buyout,
    rentToSend: attrForm.rentToSend,
    installmentEnabled: Number(attrForm.installmentEnabled || 0),
    installmentPeriods: Array.isArray(attrForm.installmentPeriods) ? attrForm.installmentPeriods.join(',') : attrForm.installmentPeriods,
    buyoutval: Math.trunc(Number(attrForm.buyoutval || 0) * 100),
    attrRentday: Array.isArray(attrForm.attrRentday) ? attrForm.attrRentday.join(',') : attrForm.attrRentday,
    minRent: attrForm.minRent,
    attrTitle: attrForm.attrTitle,
    attrNum: attrForm.attrNum,
  };
}

async function submitAttr() {
  if (!attrForm.attrTitle || !attrForm.attrSlid || !attrForm.attrRentday?.length) {
    MessagePlugin.warning('请填写 SKU 名称、封面图和租赁周期');
    return;
  }
  if (!isPositiveYuan(attrForm.attrAmount)) {
    MessagePlugin.warning('SKU 价格必须大于 0 元');
    return;
  }
  if (!isPositiveYuan(attrForm.attrDeposit)) {
    MessagePlugin.warning('SKU 押金必须大于 0 元');
    return;
  }
  syncBuyoutPriceFromDeposit();
  if (Number(attrForm.installmentEnabled || 0) === 1 && !parseInstallmentPeriods(attrForm.installmentPeriods).length) {
    MessagePlugin.warning('请选择可选分期期数');
    return;
  }
  if (!isPositiveYuan(attrForm.buyoutval)) {
    MessagePlugin.warning('买断价格必须大于 0 元');
    return;
  }
  if (Number(attrForm.free || 0) === 1 && !isPositiveYuan(attrForm.attrDeposit)) {
    MessagePlugin.warning('开启免押前请先填写大于 0 元的 SKU 押金');
    return;
  }
  if (currentAttr.value) {
    await rentApi.updateAttr(buildAttrPayload());
  } else {
    await rentApi.addAttr(buildAttrPayload());
  }
  MessagePlugin.success('规格属性已保存');
  attrVisible.value = false;
  await getAttrList(goodForm.goodId);
}

async function deleteAttr(row: AnyRecord) {
  await rentApi.fetchDeleteAttr({ ids: row.attrId });
  MessagePlugin.success('属性已删除');
  await getAttrList(goodForm.goodId);
}

async function uploadAttrImage(files: any) {
  const file = Array.isArray(files) ? files[0] : files;
  const formData = new FormData();
  formData.append('file', file.raw || file);
  formData.append('path', 'good/');
  const response = await rentApi.uploadSingle(formData);
  const data = resolveData<any>(response, {});
  const key = data?.path ?? data;
  attrForm.attrSlid = key;
  return { status: 'success', response: { url: resolveUploadAssetPath(key) } };
}

const syncLogDialogVisible = ref(false);
const syncLogLoading = ref(false);
const syncLogList = ref<AnyRecord[]>([]);
const syncLogTitle = ref('商品同步日志');
const syncLogParams = reactive({ page: 1, limit: 10, goodId: null as string | number | null });
const syncLogTotal = ref(0);
const syncLogDetailVisible = ref(false);
const currentSyncLog = ref<AnyRecord | null>(null);
const syncLogPagination = computed(() => ({
  current: syncLogParams.page,
  pageSize: syncLogParams.limit,
  total: syncLogTotal.value,
}));

const syncLogColumns = [
  { title: '状态', colKey: 'syncStatus', width: 90, align: 'center' },
  { title: '模式', colKey: 'syncMode', width: 150, align: 'center' },
  { title: '日志', colKey: 'message', minWidth: 280, ellipsis: true },
  { title: '开始时间', colKey: 'startedAt', width: 160, align: 'center' },
  { title: '完成时间', colKey: 'finishedAt', width: 160, align: 'center' },
  { title: '操作', colKey: 'syncOperation', width: 90, fixed: 'right', align: 'center' },
];

function openSyncLog(row: AnyRecord) {
  syncLogTitle.value = `商品同步日志（${row.goodTitle || row.goodId}）`;
  syncLogParams.goodId = row.goodId;
  syncLogParams.page = 1;
  syncLogDialogVisible.value = true;
  loadSyncLogs();
}

async function loadSyncLogs() {
  if (!syncLogParams.goodId) return;
  const requestedPage = syncLogParams.page;
  const requestedLimit = syncLogParams.limit;
  syncLogLoading.value = true;
  try {
    const response = await rentApi.getGoodsSyncLogs(syncLogParams);
    const page = resolvePage<AnyRecord>(response);
    syncLogList.value = page.list;
    syncLogTotal.value = page.total;
    syncLogParams.page = requestedPage;
    syncLogParams.limit = requestedLimit;
  } finally {
    syncLogLoading.value = false;
  }
}

function handleSyncLogPageChange(pageInfo: PageInfo) {
  syncLogParams.page = pageInfo.current;
  syncLogParams.limit = pageInfo.pageSize;
  loadSyncLogs();
}

function showSyncLogDetail(row: AnyRecord) {
  currentSyncLog.value = row;
  syncLogDetailVisible.value = true;
}

function syncLogSummary(row: AnyRecord) {
  if (!row?.message) return '-';
  const firstLine = formatSyncLogText(row.message).split(/\r?\n/)[0];
  return firstLine.length > 120 ? `${firstLine.slice(0, 120)}...` : firstLine;
}

const syncModeTextMap: Record<string, string> = {
  create: '创建商品',
  modify: '普通修改',
  direct_modify: '免审更新',
  direct_modify_skip_no_remote_sku: '免审更新（无远端SKU）',
  direct_delisting: '免审下架',
  delete: '删除远端商品',
  skip: '跳过',
};

function formatSyncMode(mode: string) {
  return syncModeTextMap[mode] || mode || '-';
}

function formatSyncLogText(value: unknown) {
  if (!value) return '';
  return Object.entries(syncModeTextMap)
    .sort((left, right) => right[0].length - left[0].length)
    .reduce((text, [mode, label]) => text.replaceAll(`模式=${mode}`, `模式=${label}`), String(value));
}

function formatJson(value: unknown) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, 2);
  } catch {
    return String(value || '');
  }
}

function formatSyncStatus(status: string) {
  const map: Record<string, string> = {
    PENDING: '未开始',
    QUEUED: '排队中',
    RUNNING: '同步中',
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED: '跳过',
  };
  return map[status] || status || '-';
}

function syncStatusTheme(status: string) {
  const map: Record<string, string> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
  };
  return map[status] || 'default';
}

function formatAlipayAuditStatus(status: string) {
  const map: Record<string, string> = {
    AUDITING: '审核中',
    PASS: '审核通过',
    PASSED: '审核通过',
    REJECT: '审核驳回',
    REJECTED: '审核驳回',
    AUDIT_REJECT: '审核驳回',
    WAIT_AUDIT: '待审核',
    INIT: '未提交',
    NONE: '未提交',
  };
  return map[status] || status || '-';
}

function formatAlipaySpuStatus(status: string) {
  const map: Record<string, string> = {
    AVAILABLE: '可售',
    ONLINE: '已上线',
    AUDITING: '审核中',
    AUDIT_REJECT: '审核驳回',
    DELISTING: '已下架',
    OFFLINE: '已下线',
    FREEZE: '已冻结',
    FROZEN: '已冻结',
    SOLD_OUT: '已售罄',
  };
  return map[status] || status || '-';
}

const localStatusText = (row: AnyRecord) => Number(row.status || 0) === 1 ? '本地上架' : '本地下架';
const localStatusTheme = (row: AnyRecord) => Number(row.status || 0) === 1 ? 'success' : 'default';
const auditTheme = (status: string) => ['PASS', 'PASSED'].includes(status) ? 'success' : (['REJECT', 'REJECTED', 'AUDIT_REJECT'].includes(status) ? 'danger' : 'default');
const spuTheme = (status: string) => ['AVAILABLE', 'ONLINE'].includes(status) ? 'success' : (['FREEZE', 'FROZEN', 'SOLD_OUT', 'OFFLINE'].includes(status) ? 'warning' : 'default');

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
  editorRef.value?.destroy();
});
</script>

<style scoped>
:global(.goods-drawer .t-drawer__body) {
  padding: 0;
  background: #f5f7fa;
}

:global(.goods-drawer .t-drawer__footer) {
  padding: 0;
  border-top: 1px solid #f0f0f0;
}

.goods-record {
  display: grid;
  grid-template-columns: 28px 92px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
  padding: 14px 16px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.goods-record:hover {
  border-color: #d6e4ff;
  background: #fbfdff;
}

.goods-record__check {
  display: flex;
  justify-content: center;
}

.goods-record__cover,
.goods-record__cover-empty {
  width: 92px;
  height: 78px;
}

.goods-record__cover {
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: #fff;
  outline: 0;
  box-shadow: none;
}

.goods-record__cover :deep(.t-image__wrapper),
.goods-record__cover :deep(.t-image),
.goods-record__cover :deep(img) {
  width: 100%;
  height: 100%;
  border: 0 !important;
  border-radius: 0 !important;
  outline: 0 !important;
  background: #fff !important;
  box-shadow: none !important;
}

.goods-record__cover :deep(img) {
  object-fit: contain;
  transform: none;
}

.goods-record__cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #98a2b3;
  font-size: 12px;
}

.goods-record__main {
  min-width: 0;
}

.goods-record__summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.goods-record__title {
  min-width: 0;
}

.goods-record__title strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-record__ids {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 4px;
}

.goods-record__ids span {
  color: #667085;
  font-size: 12px;
  line-height: 16px;
}

.goods-record__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.goods-record__desc {
  display: -webkit-box;
  margin: 4px 0 0;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.goods-record__meta {
  display: grid;
  grid-template-columns: 120px 170px minmax(180px, 1fr);
  gap: 14px;
  margin-top: 10px;
}

.goods-record__meta div {
  min-width: 0;
}

.goods-record__meta span {
  display: block;
  color: #98a2b3;
  font-size: 12px;
  line-height: 16px;
}

.goods-record__meta strong {
  display: block;
  overflow: hidden;
  color: #344054;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-record__price {
  color: #047857 !important;
  font-variant-numeric: tabular-nums;
}

.goods-record__sku {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 8px 12px;
  align-items: start;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f2f4f7;
}

.goods-record__sku-head {
  display: grid;
  gap: 2px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.goods-record__sku-head span {
  color: #344054;
  font-weight: 700;
}

.goods-record__sku-head em {
  color: #98a2b3;
  font-style: normal;
}

.goods-record__sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.goods-record__sku-item,
.goods-record__sku-more,
.goods-record__sku-empty {
  min-height: 36px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
  box-shadow: none;
}

.goods-record__sku-item {
  display: grid;
  grid-template-columns: minmax(96px, 1.2fr) minmax(76px, 0.8fr) minmax(160px, 1.2fr) minmax(92px, 0.8fr) minmax(130px, auto);
  gap: 8px;
  align-items: center;
  width: min(780px, 100%);
  padding: 7px 10px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.goods-record__sku-item strong,
.goods-record__sku-item span,
.goods-record__sku-item em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-record__sku-item strong {
  color: #101828;
  font-weight: 700;
}

.goods-record__sku-item em {
  color: #344054;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.goods-record__sku-more,
.goods-record__sku-empty {
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  color: #667085;
  font-size: 12px;
}

.goods-record__sku-more {
  gap: 4px;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}

.goods-record__sku-more:hover {
  color: var(--td-brand-color);
  border-color: #b8d4ff;
  background: #fff;
  box-shadow: none;
}

.goods-record__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f2f4f7;
}

.goods-record__actions :deep(.t-button) {
  min-width: auto;
  min-height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .goods-record {
    grid-template-columns: 28px 92px minmax(0, 1fr);
  }

  .goods-record__summary,
  .goods-record__meta,
  .goods-record__sku,
  .goods-record__sku-item {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .goods-record {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .goods-record__cover {
    display: none;
  }

  .goods-record__summary,
  .goods-record__meta {
    grid-template-columns: 1fr;
  }

  .goods-record__sku {
    grid-template-columns: 1fr;
  }
}

.attr-section {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}

.attr-section :deep(.t-table) {
  min-width: 1080px;
}

.attr-thumb {
  width: 34px;
  height: 34px;
  overflow: hidden;
  border: 0;
  border-radius: 4px;
  background: transparent;
  outline: 0;
  box-shadow: none;
}

.attr-thumb :deep(.t-image__wrapper),
.attr-thumb :deep(.t-image),
.attr-thumb :deep(img) {
  width: 100%;
  height: 100%;
  border: 0 !important;
  border-radius: 0 !important;
  outline: 0 !important;
  background: #fff !important;
  box-shadow: none !important;
}

.sku-empty-state {
  display: grid;
  gap: 8px;
  justify-items: start;
  padding: 28px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

.sku-empty-state strong {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.sku-empty-state p {
  max-width: 560px;
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.form-tip {
  margin: 8px 0 0;
  color: var(--td-text-color-placeholder);
  font-size: 12px;
  line-height: 20px;
}

.form-tip--warning {
  color: #b7791f;
}

.alipay-category-field {
  display: grid;
  width: 100%;
  gap: 8px;
}

.alipay-category-field__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.form-control-stack {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 100%;
}

.goods-drawer-body {
  height: 100%;
  padding: 20px 24px 24px;
  overflow: auto;
  background: #f5f7fa;
}

.goods-form-tabs {
  position: sticky;
  top: 0;
  z-index: 4;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding-bottom: 16px;
  background: #f5f7fa;
}

.goods-form-tab {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 66px;
  padding: 10px 12px;
  color: #667085;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
}

.goods-form-tab:hover {
  color: var(--td-brand-color);
  border-color: #b8d4ff;
}

.goods-form-tab--active {
  color: var(--td-brand-color);
  border-color: var(--td-brand-color);
  background: #f5f8ff;
}

.goods-form-tab__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  color: #667085;
  font-size: 12px;
  border: 1px solid #e4e7ec;
  border-radius: 50%;
  background: #f8fafc;
}

.goods-form-tab--active .goods-form-tab__index,
.goods-form-tab--ready .goods-form-tab__index {
  color: #fff;
  border-color: var(--td-brand-color);
  background: var(--td-brand-color);
}

.goods-form-tab__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.goods-form-tab__copy strong,
.goods-form-tab__copy em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-form-tab__copy strong {
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.goods-form-tab__copy em {
  color: #98a2b3;
  font-size: 12px;
  font-style: normal;
  line-height: 16px;
}

.goods-form-tab--active .goods-form-tab__copy strong {
  color: var(--td-brand-color);
}

.goods-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 24px;
  background: #fff;
}

.goods-form,
.goods-attr-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pro-form-group {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}

.pro-form-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 52px;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.pro-form-group__header span {
  display: block;
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.pro-form-group__header p {
  margin: 2px 0 0;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 18px;
}

.pro-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 24px;
  padding: 20px;
}

.pro-form-grid--single {
  grid-template-columns: 1fr;
}

.goods-form :deep(.t-form__item),
.goods-attr-form :deep(.t-form__item) {
  margin-bottom: 0;
}

.goods-form :deep(.t-form__controls-content),
.goods-attr-form :deep(.t-form__controls-content),
.goods-form :deep(.t-input),
.goods-attr-form :deep(.t-input),
.goods-form :deep(.t-input-number),
.goods-attr-form :deep(.t-input-number),
.goods-form :deep(.t-select),
.goods-attr-form :deep(.t-select) {
  width: 100%;
}

.goods-form-item--full {
  grid-column: 1 / -1;
}

.image-upload-panel {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  width: 100%;
  padding: 14px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.image-upload-panel--gallery {
  grid-template-columns: minmax(220px, auto) minmax(0, 1fr);
}

.image-upload-content {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.image-upload-content :deep(.t-upload) {
  width: fit-content;
}

.image-upload-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.image-preview-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  color: inherit;
  cursor: zoom-in;
  border: 0 !important;
  border-radius: 0 !important;
  background: #fff;
  outline: 0 !important;
  box-shadow: none !important;
}

.image-preview-trigger:focus,
.image-preview-trigger:focus-visible {
  outline: 0 !important;
  box-shadow: none !important;
}

.image-preview-trigger--record {
  width: 100%;
  height: 100%;
}

.image-preview-trigger--cover {
  width: 116px;
  height: 116px;
}

.image-preview-trigger--slider {
  width: 72px;
  height: 72px;
}

.image-preview-trigger--attr {
  width: 88px;
  height: 88px;
}

.image-preview-trigger--thumb {
  width: 34px;
  height: 34px;
}

.image-preview-trigger :deep(.t-image__wrapper),
.image-preview-trigger :deep(.t-image),
.image-preview-trigger :deep(img) {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  border: 0 !important;
  border-radius: 0 !important;
  outline: 0 !important;
  background: #fff !important;
  box-shadow: none !important;
}

.image-preview-trigger :deep(img) {
  object-fit: contain;
}

.image-preview-mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  opacity: 0;
  background: rgb(0 0 0 / 38%);
  transition: opacity .2s ease;
}

.image-preview-trigger:hover .image-preview-mask,
.image-preview-trigger:focus-visible .image-preview-mask {
  opacity: 1;
}

.image-preview-card,
.image-preview-panel,
.slider-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 96px;
  padding: 8px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  background: #f8fafc;
  box-shadow: none;
}

.image-preview-card:has(.image-preview-trigger),
.slider-preview-list:has(.image-preview-trigger) {
  padding: 0;
  border-color: transparent;
  background: transparent;
}

.slider-preview-item {
  position: relative;
  flex: 0 0 auto;
  width: 72px;
  height: 72px;
}

.slider-preview-delete {
  position: absolute;
  top: -7px;
  right: -7px;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  border: 1px solid #fff;
  border-radius: 50%;
  background: #d92d20;
  box-shadow: 0 2px 8px rgb(16 24 40 / 18%);
}

.slider-preview-delete:hover {
  background: #b42318;
}

.slider-preview-delete :deep(.t-icon) {
  font-size: 13px;
}

.image-preview-card {
  align-items: center;
  justify-content: center;
}

.image-preview-card--cover {
  width: 132px;
  height: 132px;
}

.image-preview-card--attr {
  width: 104px;
  height: 104px;
}

.cover-preview {
  width: 116px;
  height: 116px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: transparent;
  outline: 0;
  box-shadow: none;
}

.slider-preview {
  width: 72px;
  height: 72px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: transparent;
  outline: 0;
  box-shadow: none;
}

.attr-preview {
  width: 88px;
  height: 88px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: transparent;
  outline: 0;
  box-shadow: none;
}

.image-empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  width: 96px;
  height: 72px;
  color: var(--td-text-color-placeholder);
  font-size: 12px;
  background: transparent;
  border-radius: 6px;
}

.image-empty :deep(.t-icon) {
  font-size: 20px;
}

.image-empty--cover {
  width: 116px;
  height: 116px;
}

.image-empty--attr {
  width: 88px;
  height: 88px;
}

.rich-editor {
  width: 100%;
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
}

.rich-editor__toolbar {
  background: #fafafa;
}

.rich-editor__content {
  min-height: 260px;
  overflow: auto;
  color: #1f2937;
  line-height: 1.7;
  background: #fff;
}

.rich-editor__content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}

.sync-log-detail-sections {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sync-log-table-card,
.detail-section {
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.sync-log-table-card {
  padding: 0;
}

.detail-section {
  padding: 14px;
}

.detail-section-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.sync-log-stack,
.sync-log-json {
  max-height: 280px;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  background: var(--td-bg-color-page);
  border: 1px solid #e4e7ec;
  border-radius: 6px;
}

.detail-empty {
  color: var(--td-text-color-placeholder);
}

@media (max-width: 760px) {
  .goods-drawer-body {
    padding: 16px;
  }

  .goods-form-tabs {
    display: flex;
    gap: 8px;
    padding-bottom: 12px;
    overflow-x: auto;
  }

  .goods-form-tab {
    flex: 0 0 168px;
  }

  .goods-drawer-footer {
    padding: 10px 16px;
  }

  .goods-drawer-footer :deep(.t-button) {
    flex: 1;
  }

  .pro-form-group__header {
    display: grid;
    gap: 10px;
    padding: 12px 16px;
  }

  .pro-form-grid {
    grid-template-columns: 1fr;
    padding: 16px;
  }

  .image-upload-panel,
  .image-upload-panel--gallery {
    grid-template-columns: 1fr;
  }

  .image-upload-content :deep(.t-upload) {
    width: 100%;
  }

  .alipay-category-field__search {
    grid-template-columns: 1fr;
  }
}
</style>
