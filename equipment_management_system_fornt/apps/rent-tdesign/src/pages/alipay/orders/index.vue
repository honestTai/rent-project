<template>
  <div id="orderListPage" class="order-page">
    <AlipayCleanListPage
      title="订单列表"
      description="集中查看支付宝租赁订单、押金、履约物流、用户身份和订单操作。"
      :rows="orderRows"
      row-key="orderId"
      :loading="loading"
      :pagination="orderPagination"
      content-title="订单列表"
      empty-title="暂无订单"
      @page-change="handlePageChange"
    >
      <template #filters>
        <t-select
          v-model="tableParams.type"
          class="handle-select"
          placeholder="搜索类型"
          clearable
          filterable
          v-bind="searchableSelectProps('orders.type', typeOptions)"
          @change="changeType"
          @clear="changeType"
        />
        <t-input
          v-model="tableParams.userName"
          class="handle-input"
          placeholder="请输入关键信息搜索"
          clearable
        />
        <t-select
          v-model="tableParams.alipayStatus"
          class="handle-select"
          placeholder="订单状态"
          clearable
          filterable
          v-bind="searchableSelectProps('orders.alipayStatus', alipayStatusOptions)"
        />
        <template v-if="orderQueryExpanded">
          <t-select
            v-model="tableParams.chooseTime"
            class="handle-select"
            placeholder="时间选择"
            clearable
            filterable
            v-bind="searchableSelectProps('orders.chooseTime', dateOptions)"
            @change="changeTime"
            @clear="changeTime"
          />
          <t-date-range-picker
            v-model="tableParams.chooseDate"
            class="date-range"
            value-type="time-stamp"
            clearable
            :placeholder="['开始日期', '结束日期']"
            @change="handleSearchDate"
          />
          <t-date-picker v-model="selectedDate" class="bill-date" value-type="YYYY-MM-DD" placeholder="账单日期" />
        </template>
      </template>

      <template #queryActions>
        <AlipayQueryActions
          v-model:expanded="orderQueryExpanded"
          expandable
          @search="getListOrder()"
          @reset="resetSearchFilters"
        />
      </template>

      <template #contentActions>
        <t-button theme="primary" variant="outline" @click="openOrderFieldSetting">
          <template #icon><t-icon name="setting" /></template>
          显示设置
        </t-button>
        <t-button theme="primary" variant="outline" @click="downloadOrder">
          <template #icon><t-icon name="download" /></template>
          账单下载
        </t-button>
      </template>

      <template #record="{ row }">
        <article class="record-card">
              <div class="record-card__header">
                <div class="record-card__heading">
                  <t-tag v-if="row.alipayStatus" :theme="statusTheme(row)" variant="light">
                    {{ dealStatus(row) }}
                  </t-tag>
                  <div class="record-card__title-block">
                    <strong>{{ row.orderNo || '-' }}</strong>
                    <span>{{ orderCustomerName(row) }} / {{ row.goodTitle || '-' }}</span>
                  </div>
                </div>

                <div v-if="visibleOrderAmountFields.length" class="record-card__amounts">
                  <div v-for="field in visibleOrderAmountFields" :key="field.key">
                    <span>{{ field.label }}</span>
                    <strong class="amount-highlight" :class="field.valueClass?.(row)">{{ field.value(row) }}</strong>
                  </div>
                </div>
              </div>

              <div v-if="visibleOrderSummaryFields.length" class="record-card__meta-line">
                <span v-for="field in visibleOrderSummaryFields" :key="field.key" :class="field.valueClass?.(row)">{{ field.label }}：{{ field.value(row) }}</span>
              </div>

              <div v-if="visibleOrderCardGroups.length" class="record-card__body">
                <div v-for="group in visibleOrderCardGroups" :key="group.key" class="record-info-group">
                  <div class="record-info-group__title">{{ group.title }}</div>
                  <div
                    v-for="field in group.fields"
                    :key="field.key"
                    class="record-field"
                    :class="{ 'record-field--block': field.block, 'record-field--wrap': field.wrap }"
                  >
                    <span>{{ field.label }}</span>
                    <t-tooltip v-if="field.wrap && field.value(row) !== '-'" :content="field.value(row)" placement="top-left" show-arrow>
                      <strong :class="field.valueClass?.(row)">{{ field.value(row) }}</strong>
                    </t-tooltip>
                    <strong v-else :class="field.valueClass?.(row)">{{ field.value(row) }}</strong>
                  </div>
                </div>
              </div>

              <div v-if="idCardPhotoList(row).length" class="identity-photo-list record-identity-photo-list">
                <div v-for="photo in idCardPhotoList(row)" :key="photo.url" class="identity-photo-item">
                  <t-image
                    class="identity-photo-thumb"
                    :src="photo.previewUrl"
                    fit="cover"
                    @click="previewIdentityPhoto(photo.previewUrl)"
                  />
                  <span>{{ photo.label }}</span>
                </div>
              </div>

              <div v-if="displayedRecordActions(row).length" class="record-actions">
                <span class="record-actions__label">操作</span>
                <div class="record-actions__buttons">
                  <t-button
                    v-for="action in recordInlineActions(row)"
                    :key="action.key"
                    :theme="action.theme"
                    :variant="action.variant || 'text'"
                    size="small"
                    @click="action.handler"
                  >
                    {{ action.label }}
                  </t-button>
                  <t-dropdown v-if="recordOverflowActions(row).length" trigger="click" :min-column-width="132" placement="bottom-right">
                    <template #dropdown>
                      <t-dropdown-item
                        v-for="action in recordOverflowActions(row)"
                        :key="action.key"
                        :class="`record-actions__dropdown-item--${action.theme || 'primary'}`"
                        @click="action.handler"
                      >
                        {{ action.label }}
                      </t-dropdown-item>
                    </template>
                    <t-button theme="primary" variant="text" size="small" class="record-actions__more">
                      更多
                      <template #suffix><t-icon name="chevron-down" /></template>
                    </t-button>
                  </t-dropdown>
                </div>
              </div>
        </article>
      </template>
    </AlipayCleanListPage>

    <t-drawer
      v-model:visible="orderDetailDrawerVisible"
      :header="false"
      :footer="false"
      :size="orderDetailDrawerSize"
      placement="right"
      drawer-class-name="order-detail-drawer"
      :prevent-scroll-through="true"
      destroy-on-close
    >
      <div class="order-detail-drawer-body">
        <header class="order-detail-drawer-header">
          <div class="order-detail-drawer-header__main">
            <t-tag v-if="currentObj.alipayStatus" :theme="statusTheme(currentObj)" variant="light">
              {{ dealStatus(currentObj) }}
            </t-tag>
            <div class="order-detail-drawer-header__title">
              <strong>{{ currentObj.orderNo || '-' }}</strong>
              <span>{{ orderCustomerName(currentObj) }} / {{ currentObj.goodTitle || '-' }}</span>
            </div>
          </div>
          <t-button theme="default" variant="text" shape="square" @click="orderDetailDrawerVisible = false">
            <template #icon><t-icon name="close" /></template>
          </t-button>
        </header>

        <div class="order-detail-summary-grid">
          <div v-for="item in drawerSummaryCards" :key="item.key" class="order-detail-summary-card">
            <span>{{ item.label }}</span>
            <strong :class="item.className">{{ item.value }}</strong>
          </div>
        </div>

        <div v-if="orderDetailDrawerMode === 'full' && visibleOrderDrawerSections.length > 1" class="order-detail-tabs" role="tablist" aria-label="订单详情分段导航">
          <button
            v-for="section in visibleOrderDrawerSections"
            :key="section.key"
            type="button"
            class="order-detail-tabs__item"
            :class="{ 'order-detail-tabs__item--active': activeOrderDrawerSection === section.key }"
            role="tab"
            :aria-selected="activeOrderDrawerSection === section.key"
            @click="openOrderDrawerSection(section.key)"
          >
            {{ section.label }}
          </button>
        </div>

        <div class="order-detail-drawer-content">
          <section v-if="activeOrderDrawerSection === 'overview'" class="drawer-section">
            <div class="drawer-section__header">
              <div>
                <strong>订单概览</strong>
                <span>当前列表已加载的订单、用户、身份和履约摘要。</span>
              </div>
            </div>
            <div class="drawer-overview-panel">
              <section v-for="group in drawerOverviewGroups" :key="group.key" class="drawer-overview-group">
                <h3>{{ group.title }}</h3>
                <dl class="drawer-overview-list">
                  <div
                    v-for="field in group.fields"
                    :key="field.key"
                    class="drawer-overview-row"
                    :class="{ 'drawer-overview-row--block': field.block }"
                  >
                    <dt>{{ field.label }}</dt>
                    <dd :class="field.valueClass?.(currentObj)">{{ field.value(currentObj) }}</dd>
                  </div>
                </dl>
              </section>
            </div>
            <div v-if="idCardPhotoList(currentObj).length" class="drawer-section">
              <div class="drawer-section__header">
                <div>
                  <strong>身份证照片</strong>
                  <span>点击图片可查看大图。</span>
                </div>
              </div>
              <div class="identity-photo-list">
                <div v-for="photo in idCardPhotoList(currentObj)" :key="photo.url" class="identity-photo-item">
                  <t-image class="identity-photo-thumb" :src="photo.previewUrl" fit="cover" @click="previewIdentityPhoto(photo.previewUrl)" />
                  <span>{{ photo.label }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'order'" class="drawer-section drawer-section--order-deposit">
            <t-loading :loading="orderDetailLoading">
              <template v-if="rentOrderDetail.orderId || rentOrderDetail.title">
                <div class="drawer-section__header">
                  <div>
                    <strong>订单 / 押金</strong>
                    <span>支付宝租赁订单、地址、价格、账单和押金预授权。</span>
                  </div>
                </div>
                <t-descriptions class="drawer-descriptions" :column="2" size="small">
                  <t-descriptions-item label="订单标题">{{ rentOrderDetail.title || '-' }}</t-descriptions-item>
                  <t-descriptions-item label="订单号">{{ rentOrderDetail.orderId || '-' }}</t-descriptions-item>
                  <t-descriptions-item label="订单状态">
                    <t-tag :theme="rentOrderDetail.status === 'FINISHED' ? 'success' : 'default'" variant="light">
                      {{ rentOrderDetail.status || '-' }}
                    </t-tag>
                  </t-descriptions-item>
                  <t-descriptions-item label="创建时间">{{ formatDate(rentOrderDetail.orderCreateTime) }}</t-descriptions-item>
                </t-descriptions>
                <div class="amount-highlight-grid drawer-amount-grid">
                  <div class="amount-highlight-card amount-highlight-card--primary">
                    <span>订单价格</span>
                    <strong>￥{{ rentOrderDetail.priceInfo?.orderPrice || '0.00' }}</strong>
                  </div>
                  <div class="amount-highlight-card amount-highlight-card--danger">
                    <span>商品押金</span>
                    <strong>￥{{ rentOrderDetail.priceInfo?.depositPrice || '0.00' }}</strong>
                  </div>
                  <div class="amount-highlight-card">
                    <span>运费</span>
                    <strong>￥{{ rentOrderDetail.priceInfo?.freight || '0.00' }}</strong>
                  </div>
                  <div class="amount-highlight-card">
                    <span>附加费用</span>
                    <strong>￥{{ rentOrderDetail.priceInfo?.additionalPrice || '0.00' }}</strong>
                  </div>
                </div>
                <div class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>收货地址</strong>
                      <span>{{ rentOrderDetail.addressInfo?.receiverName || '-' }} / {{ rentOrderDetail.addressInfo?.telNumber || '-' }}</span>
                    </div>
                  </div>
                  <t-descriptions class="drawer-descriptions" :column="1" size="small">
                    <t-descriptions-item label="详细地址">{{ rentOrderDetail.addressInfo?.detailedAddress || '-' }}</t-descriptions-item>
                  </t-descriptions>
                </div>
                <div class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>账单时间轴</strong>
                      <span>按计划支付时间或分期号排序。</span>
                    </div>
                  </div>
                  <t-timeline v-if="combinedStatementTimelineItems.length" mode="same" theme="dot">
                    <t-timeline-item
                      v-for="item in combinedStatementTimelineItems"
                      :key="item.key"
                      :label="item.label"
                      :dot-color="item.dotColor"
                    >
                      <div class="drawer-timeline-card">
                        <div class="drawer-timeline-card__title">
                          <strong>{{ item.title }}</strong>
                          <t-tag :theme="item.tagTheme" variant="light">{{ item.status }}</t-tag>
                        </div>
                        <div class="drawer-mini-grid">
                          <span v-for="field in item.fields" :key="field.label">{{ field.label }}：{{ field.value }}</span>
                        </div>
                      </div>
                    </t-timeline-item>
                  </t-timeline>
                  <t-empty v-else description="暂无账单记录" />
                </div>
                <div class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>租赁商品</strong>
                      <span>商品字段较多，保留表格便于对比。</span>
                    </div>
                  </div>
                  <t-table row-key="outItemId" size="small" :data="rentOrderDetail.itemInfos || []" :columns="rentItemColumns" cell-empty-content="-" />
                </div>
                <div class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>押金 / 预授权</strong>
                      <span>{{ depositLoading ? '正在查询押金和预授权信息' : depositSummaryHint }}</span>
                    </div>
                  </div>
                  <t-loading :loading="depositLoading">
                    <t-alert v-if="depositData.success === false" theme="error" :message="`查询失败：${depositData.subMsg || depositData.errorMsg || depositData.subCode || '未知错误'}`" />
                    <template v-else-if="depositData.orderNo">
                      <div class="amount-highlight-grid drawer-amount-grid">
                        <div class="amount-highlight-card amount-highlight-card--primary">
                          <span>订单押金</span>
                          <strong>￥{{ fenToYuan(depositData.orderDeposit) }}</strong>
                        </div>
                        <div class="amount-highlight-card amount-highlight-card--danger">
                          <span>剩余冻结押金</span>
                          <strong>￥{{ fenToYuan(depositData.remainingDeposit) }}</strong>
                        </div>
                        <div class="amount-highlight-card amount-highlight-card--muted">
                          <span>租金支付交易号</span>
                          <strong>{{ depositData.paymentTradeNo || '-' }}</strong>
                        </div>
                      </div>
                      <div v-if="fundAuthTimelineItems.length" class="drawer-section">
                        <div class="drawer-section__header">
                          <div>
                            <strong>资金授权时间轴</strong>
                            <span>支付宝资金授权返回字段。</span>
                          </div>
                        </div>
                        <t-timeline mode="same" theme="dot">
                          <t-timeline-item
                            v-for="item in fundAuthTimelineItems"
                            :key="item.key"
                            :label="item.label"
                            dot-color="primary"
                          >
                            <div class="drawer-timeline-card">
                              <div class="drawer-timeline-card__title">
                                <strong>{{ item.title }}</strong>
                              </div>
                              <div class="drawer-mini-grid">
                                <span v-for="field in item.fields" :key="field.label">{{ field.label }}：{{ field.value }}</span>
                              </div>
                            </div>
                          </t-timeline-item>
                        </t-timeline>
                      </div>
                      <div v-if="priceInfoList.length" class="drawer-section">
                        <div class="drawer-section__header">
                          <div>
                            <strong>支付宝价格信息</strong>
                            <span>金额字段保留分组描述。</span>
                          </div>
                        </div>
                        <t-descriptions class="drawer-descriptions" :column="1" size="small">
                          <t-descriptions-item v-for="item in priceInfoList" :key="item.key" :label="item.label">{{ item.value }}</t-descriptions-item>
                        </t-descriptions>
                      </div>
                    </template>
                    <t-empty v-else description="暂无押金数据" />
                  </t-loading>
                </div>
              </template>
              <t-empty v-else description="暂无订单详情" />
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'deduct'" class="drawer-section">
            <t-loading :loading="deductRecordLoading">
              <div class="drawer-section__header">
                <div>
                  <strong>扣减记录时间轴</strong>
                  <span>按创建时间展示扣款、售后和商家处理状态。</span>
                </div>
              </div>
              <t-timeline v-if="deductRecordTimelineItems.length" mode="same" theme="dot">
                <t-timeline-item
                  v-for="item in deductRecordTimelineItems"
                  :key="item.key"
                  :label="item.label"
                  :dot-color="item.dotColor"
                >
                  <div class="drawer-timeline-card">
                    <div class="drawer-timeline-card__title">
                      <strong>{{ item.title }}</strong>
                      <t-tag :theme="item.tagTheme" variant="light">{{ item.status }}</t-tag>
                    </div>
                    <div class="drawer-mini-grid">
                      <span v-for="field in item.fields" :key="field.label">{{ field.label }}：{{ field.value }}</span>
                    </div>
                    <t-space v-if="canOperateDeductRecord(item.row)" size="small" class="drawer-timeline-actions">
                      <t-button v-if="canApproveWithUserPay(item.row)" size="small" theme="primary" @click="confirmDeductRecord(item.row, 'APPROVE_WITH_USER_PAY')">发起赔付</t-button>
                      <t-button v-if="canRejectDeductRecord(item.row)" size="small" theme="warning" @click="confirmDeductRecord(item.row, 'MERCHANT_REJECT')">拒绝售后</t-button>
                      <t-button v-if="canFinishDeductRecord(item.row)" size="small" theme="success" @click="confirmDeductRecord(item.row, 'PAY_COMPENSATION')">{{ finishDeductButtonText(item.row) }}</t-button>
                      <t-button v-if="canFinishAftersaleRecord(item.row)" size="small" theme="success" @click.stop="confirmDeductRecord(item.row, 'AFTERSALE_FINISH')">补完结售后</t-button>
                      <t-button v-if="canCancelDeductRecord(item.row)" size="small" theme="danger" @click="confirmDeductRecord(item.row, 'USER_CANCEL_APPLY')">撤销售后</t-button>
                    </t-space>
                  </div>
                </t-timeline-item>
              </t-timeline>
              <t-empty v-else description="暂无扣减记录" />
              <div v-if="deductRecordPage.total" class="drawer-pagination">
                <t-pagination
                  :current="deductRecordPagination.current"
                  :page-size="deductRecordPagination.pageSize"
                  :total="deductRecordPagination.total"
                  :page-size-options="deductRecordPagination.pageSizeOptions"
                  size="small"
                  @change="handleDeductRecordPageChange"
                />
              </div>
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'fulfillment'" class="drawer-section">
            <t-loading :loading="returnRecordLoading">
              <div class="drawer-section__header">
                <div>
                  <strong>履约 / 寄回时间轴</strong>
                  <span>从订单与用户寄回记录推导履约节点。</span>
                </div>
              </div>
              <t-timeline v-if="fulfillmentTimelineItems.length" mode="same" theme="dot">
                <t-timeline-item
                  v-for="item in fulfillmentTimelineItems"
                  :key="item.key"
                  :label="item.label"
                  :dot-color="item.dotColor"
                >
                  <div class="drawer-timeline-card">
                    <div class="drawer-timeline-card__title">
                      <strong>{{ item.title }}</strong>
                      <t-tag v-if="item.status" :theme="item.tagTheme" variant="light">{{ item.status }}</t-tag>
                    </div>
                    <p v-if="item.description">{{ item.description }}</p>
                    <div v-if="item.fields.length" class="drawer-mini-grid">
                      <span v-for="field in item.fields" :key="field.label">{{ field.label }}：{{ field.value }}</span>
                    </div>
                  </div>
                </t-timeline-item>
              </t-timeline>
              <t-empty v-else description="暂无履约记录" />
              <div v-if="returnPhotoList.length" class="drawer-section">
                <div class="drawer-section__header">
                  <div>
                    <strong>寄回照片</strong>
                    <span>用户提交的图片凭证。</span>
                  </div>
                </div>
                <div class="return-record-images">
                  <t-image
                    v-for="(url, index) in returnPhotoList"
                    :key="index"
                    class="return-record-image"
                    :src="resolveUploadAssetPath(url)"
                    fit="cover"
                  />
                </div>
              </div>
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'ledger'" class="drawer-section">
            <t-loading :loading="operLogLoading">
              <div class="drawer-section__header">
                <div>
                  <strong>操作台账时间轴</strong>
                  <span>台账为分页数据，切页继续请求对应分页。</span>
                </div>
              </div>
              <t-timeline v-if="operLogTimelineItems.length" mode="same" theme="dot">
                <t-timeline-item
                  v-for="item in operLogTimelineItems"
                  :key="item.key"
                  :label="item.label"
                  :dot-color="item.dotColor"
                >
                  <div class="drawer-timeline-card">
                    <div class="drawer-timeline-card__title">
                      <strong>{{ item.title }}</strong>
                      <t-tag :theme="item.tagTheme" variant="light">{{ item.status }}</t-tag>
                    </div>
                    <p v-if="item.description">{{ item.description }}</p>
                    <div class="drawer-mini-grid">
                      <span v-for="field in item.fields" :key="field.label">{{ field.label }}：{{ field.value }}</span>
                    </div>
                    <details v-if="item.requestText || item.responseText || item.errorText" class="drawer-detail-box">
                      <summary>
                        <span>请求 / 响应数据</span>
                        <small>{{ item.payloadSummary }}</small>
                      </summary>
                      <div class="drawer-detail-box__body">
                        <div class="drawer-detail-box__section">
                          <div class="drawer-detail-box__title">请求参数</div>
                          <pre v-if="item.requestText" class="json-block drawer-json-block">{{ item.requestText }}</pre>
                          <span v-else class="drawer-detail-box__empty">暂无</span>
                        </div>
                        <div class="drawer-detail-box__section">
                          <div class="drawer-detail-box__title">返回数据</div>
                          <pre v-if="item.responseText" class="json-block drawer-json-block">{{ item.responseText }}</pre>
                          <span v-else class="drawer-detail-box__empty">暂无</span>
                        </div>
                        <div v-if="item.errorText" class="drawer-detail-box__section">
                          <div class="drawer-detail-box__title drawer-detail-box__title--error">错误信息</div>
                          <pre class="json-block drawer-json-block drawer-json-block--error">{{ item.errorText }}</pre>
                        </div>
                      </div>
                    </details>
                  </div>
                </t-timeline-item>
              </t-timeline>
              <t-empty v-else description="暂无台账记录" />
              <div v-if="operLogPage.total" class="drawer-pagination">
                <t-pagination
                  :current="operLogPagination.current"
                  :page-size="operLogPagination.pageSize"
                  :total="operLogPagination.total"
                  :page-size-options="operLogPagination.pageSizeOptions"
                  size="small"
                  @change="handleOperLogPageChange"
                />
              </div>
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'risk'" class="drawer-section">
            <t-loading :loading="riskInfoLoading">
              <template v-if="canViewRiskInfo(currentObj)">
                <div class="drawer-section__header">
                  <div>
                    <strong>风险信息</strong>
                    <span>本地与支付宝风险模型，支持任意状态订单查看。</span>
                  </div>
                </div>
                <div v-if="riskInfoDetail.localRisk" class="local-risk-panel">
                  <div class="local-risk-panel__summary">
                    <div>
                      <span>本地模型评分</span>
                      <strong>{{ localRiskScoreText(riskInfoDetail.localRisk) }}</strong>
                      <p>{{ riskInfoDetail.localRisk.summary || '暂无本地风险命中。' }}</p>
                    </div>
                    <t-tag :theme="riskLevelTheme(riskInfoDetail.localRisk.level)" variant="light">
                      {{ localRiskLevelText(riskInfoDetail.localRisk.level) }}
                    </t-tag>
                  </div>
                  <div class="local-risk-metrics">
                    <div>
                      <span>历史订单</span>
                      <strong>{{ riskInfoDetail.localRisk.orderStats?.totalOrders || 0 }} 单</strong>
                    </div>
                    <div>
                      <span>进行中订单</span>
                      <strong>{{ riskInfoDetail.localRisk.orderStats?.activeOrders || 0 }} 单</strong>
                    </div>
                    <div>
                      <span>成功扣减</span>
                      <strong>{{ riskInfoDetail.localRisk.aftersaleStats?.successRecords || 0 }} 次</strong>
                    </div>
                    <div>
                      <span>累计扣减</span>
                      <strong>￥{{ fenToYuan(riskInfoDetail.localRisk.aftersaleStats?.successAmount) }}</strong>
                    </div>
                  </div>
                  <div v-if="riskInfoDetail.localRisk.hitRules?.length" class="local-risk-rules">
                    <div
                      v-for="rule in riskInfoDetail.localRisk.hitRules"
                      :key="rule.code"
                      class="local-risk-rule"
                    >
                      <div class="local-risk-rule__title">
                        <strong>{{ rule.title || rule.code }}</strong>
                        <t-tag :theme="riskLevelTheme(rule.level)" variant="light">{{ localRiskLevelText(rule.level) }}</t-tag>
                      </div>
                      <p>{{ rule.evidence || '-' }}</p>
                      <span>{{ rule.suggestion || '-' }}</span>
                    </div>
                  </div>
                  <div v-else class="local-risk-empty">本地规则暂无命中。</div>
                  <div class="local-risk-history-grid">
                    <div class="local-risk-history">
                      <div class="local-risk-history__title">最近历史订单</div>
                      <div v-if="riskInfoDetail.localRisk.recentOrders?.length" class="local-risk-history__list">
                        <div v-for="item in riskInfoDetail.localRisk.recentOrders" :key="item.orderId || item.orderNo" class="local-risk-history__row">
                          <div>
                            <strong>{{ item.orderNo || '-' }}</strong>
                            <span>{{ item.goodTitle || '-' }}</span>
                          </div>
                          <div>
                            <t-tag :theme="successStatuses.includes(item.alipayStatus) ? 'success' : 'default'" variant="light">
                              {{ formatOrderStatus(item.alipayStatus) }}
                            </t-tag>
                            <small>{{ formatDateTime(item.createdAt) }}</small>
                          </div>
                        </div>
                      </div>
                      <div v-else class="local-risk-empty">暂无历史订单。</div>
                    </div>
                    <div class="local-risk-history">
                      <div class="local-risk-history__title">最近售后扣减</div>
                      <div v-if="riskInfoDetail.localRisk.recentDeductRecords?.length" class="local-risk-history__list">
                        <div
                          v-for="item in riskInfoDetail.localRisk.recentDeductRecords"
                          :key="item.id || item.orderNo"
                          class="local-risk-history__row"
                        >
                          <div>
                            <strong>{{ fenToYuan(item.deductAmount) }} 元</strong>
                            <span>{{ item.reasonCode || item.remark || '-' }}</span>
                          </div>
                          <div>
                            <t-tag :theme="recordStatusTheme(item.status)" variant="light">
                              {{ localDeductStatusText(item.status) }}
                            </t-tag>
                            <small>{{ formatDateTime(item.createTime) }}</small>
                          </div>
                        </div>
                      </div>
                      <div v-else class="local-risk-empty">暂无售后扣减记录。</div>
                    </div>
                  </div>
                </div>
                <div v-if="riskInfoDetail.cloudRentRisk" class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>云智能租赁风控</strong>
                      <span>支付宝云智能租赁风控返回的履约、多头和逾期风险。</span>
                    </div>
                  </div>
                  <t-alert
                    v-if="riskInfoDetail.cloudRentRisk.available === false"
                    theme="warning"
                    :message="`云风控查询失败：${riskInfoDetail.cloudRentRisk.errorMessage || '暂未返回原因'}`"
                  />
                  <div v-else class="risk-list drawer-risk-list">
                    <div class="risk-card">
                      <div class="risk-card-title risk-card-title--inline">
                        <span>综合判断</span>
                        <t-tag :theme="riskLevelTheme(riskInfoDetail.cloudRentRisk.riskRank)" variant="light">
                          {{ riskRankText(riskInfoDetail.cloudRentRisk.riskRank) }}
                        </t-tag>
                      </div>
                      <div class="risk-item">风险名称：{{ riskInfoDetail.cloudRentRisk.riskName || '-' }}</div>
                      <div class="risk-item">风险描述：{{ riskInfoDetail.cloudRentRisk.riskDesc || '-' }}</div>
                      <div v-if="riskInfoDetail.cloudRentRisk.recordId" class="risk-item">记录ID：{{ riskInfoDetail.cloudRentRisk.recordId }}</div>
                    </div>
                    <div v-for="item in cloudRentRiskItems(riskInfoDetail.cloudRentRisk)" :key="item.key" class="risk-card">
                      <div class="risk-card-title risk-card-title--inline">
                        <span>{{ item.title }}</span>
                        <t-tag :theme="riskLevelTheme(item.riskRank)" variant="light">{{ riskRankText(item.riskRank) }}</t-tag>
                      </div>
                      <div class="risk-item">风险名称：{{ item.riskName || '-' }}</div>
                      <div v-if="item.riskDesc" class="risk-item">风险描述：{{ item.riskDesc }}</div>
                    </div>
                  </div>
                </div>
                <t-alert
                  v-if="!riskInfoDetail.cloudRentRisk && riskInfoDetail.alipayRisk && riskInfoDetail.alipayRisk.available === false"
                  theme="warning"
                  :message="`${riskProviderDisplayName(riskInfoDetail)}查询失败：${riskInfoDetail.alipayRisk.errorMessage || '暂未返回原因'}，本地模型结果仍可作为审核参考。`"
                />
                <t-descriptions v-if="!riskInfoDetail.cloudRentRisk" class="drawer-descriptions" :column="2" size="small">
                  <t-descriptions-item label="产品版本">
                    <t-tag :theme="riskInfoDetail.productEdition === 'PRO' ? 'success' : 'default'" variant="light">
                      {{ productEditionText(riskInfoDetail.productEdition) }}
                    </t-tag>
                  </t-descriptions-item>
                  <t-descriptions-item label="联营订单分组">{{ vamGroupText(riskInfoDetail.vamGroup) }}</t-descriptions-item>
                  <t-descriptions-item label="风险策略值">{{ riskInfoDetail.riskBasicInfo?.riskPolicyValue || '-' }}</t-descriptions-item>
                </t-descriptions>
                <div v-if="riskInfoDetail.comprehensiveRiskModels" class="risk-card">
                  <div class="risk-card-title risk-card-title--inline">
                    <span>综合风险模型</span>
                    <t-tag :theme="riskLevelTheme(riskInfoDetail.comprehensiveRiskModels.riskLevel)" variant="light">
                      {{ riskRankText(riskInfoDetail.comprehensiveRiskModels.riskLevel) }}
                    </t-tag>
                  </div>
                  <div class="risk-item">风险级别类型：{{ riskLevelTypeText(riskInfoDetail.comprehensiveRiskModels.riskLevelType) }}</div>
                  <div v-if="riskInfoDetail.comprehensiveRiskModels.desc" class="risk-item">描述：{{ riskInfoDetail.comprehensiveRiskModels.desc }}</div>
                </div>
                <div v-if="riskInfoDetail.highRiskModels" class="risk-card">
                  <div class="risk-card-title risk-card-title--inline">
                    <span>高风险模型</span>
                    <t-tag :theme="riskLevelTheme(riskInfoDetail.highRiskModels.riskLevel)" variant="light">
                      {{ riskRankText(riskInfoDetail.highRiskModels.riskLevel) }}
                    </t-tag>
                  </div>
                  <div class="risk-item">风险级别类型：{{ riskLevelTypeText(riskInfoDetail.highRiskModels.riskLevelType) }}</div>
                  <div v-if="riskInfoDetail.highRiskModels.desc" class="risk-item">描述：{{ riskInfoDetail.highRiskModels.desc }}</div>
                </div>
                <div v-if="riskInfoDetail.shipGoodsRiskModels?.length" class="risk-list drawer-risk-list">
                  <div v-for="(item, index) in riskInfoDetail.shipGoodsRiskModels" :key="index" class="risk-card">
                    <div class="risk-card-title">发货前风险</div>
                    <div class="risk-item">风险项：{{ riskCodeText(item.riskCode) }}</div>
                    <div v-if="riskCodeDescription(item.riskCode)" class="risk-item">说明：{{ riskCodeDescription(item.riskCode) }}</div>
                    <div class="risk-item">发货建议：{{ shipRiskDecisionText(item) }}</div>
                  </div>
                </div>
                <div v-if="riskInfoDetail.riskInfos?.length" class="risk-list drawer-risk-list">
                  <div v-for="(riskInfo, index) in riskInfoDetail.riskInfos" :key="index" class="risk-card">
                    <div class="risk-card-title">风险类型：{{ riskTypeText(riskInfo.riskType) }}</div>
                    <div v-for="(item, itemIndex) in riskInfo.riskItemList || []" :key="itemIndex" class="risk-item">
                      <div>风险项：{{ riskCodeText(item.riskCode) }}</div>
                      <div v-if="riskCodeDescription(item.riskCode)">说明：{{ riskCodeDescription(item.riskCode) }}</div>
                      <div>
                        风险级别：
                        <t-tag :theme="riskLevelTheme(item.riskLevel)" variant="light">{{ riskRankText(item.riskLevel) }}</t-tag>
                      </div>
                      <div v-if="item.hitDetail">命中详情：{{ item.hitDetail }}</div>
                    </div>
                  </div>
                </div>
                <t-empty v-if="!riskInfoDetail.cloudRentRisk && !riskInfoDetail.riskInfos?.length && !riskInfoDetail.comprehensiveRiskModels && !riskInfoDetail.highRiskModels && !riskInfoDetail.shipGoodsRiskModels?.length" description="暂无租安盾风险明细" />
                <div v-if="canOperateRiskReview(currentObj)" class="drawer-section">
                  <div class="drawer-section__header">
                    <div>
                      <strong>后续流程要求</strong>
                      <span>可按本次审核结果要求用户补充身份证照片或完成 e签宝电子合同。</span>
                    </div>
                  </div>
                  <div class="risk-option-row">
                    <span>要求上传身份证照片</span>
                    <t-switch v-model="riskRequireIdCardPhoto" />
                  </div>
                  <div class="risk-option-row">
                    <span>要求完成 e签宝电子合同</span>
                    <t-switch v-model="riskRequireEsign" />
                  </div>
                  <t-space class="drawer-risk-actions">
                    <t-button theme="success" @click="handleRiskApprove">同意</t-button>
                    <t-button theme="danger" @click="handleRiskNoApprove">不同意</t-button>
                  </t-space>
                </div>
              </template>
              <t-empty v-else description="当前订单暂无可查看的风险信息" />
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'contract'" class="drawer-section">
            <t-loading :loading="contractLoading">
              <div class="drawer-section__header">
                <div>
                  <strong>{{ contractPanelTitle(esignContractDetail) }}</strong>
                  <span>{{ contractPanelDescription(esignContractDetail) }}</span>
                </div>
              </div>
              <div v-if="isEsignEnabled(esignContractDetail)" class="contract-panel">
                <t-tag :theme="esignContractStatusTheme(esignContractDetail.contract?.status)" variant="light">
                  {{ esignContractDetail.contract?.statusText || '待生成' }}
                </t-tag>
                <div class="contract-panel__meta">
                  <span>订单号：{{ currentObj.orderNo || '-' }}</span>
                  <span>合同编号：{{ esignContractDetail.contract?.contractNo || '-' }}</span>
                  <span>流程ID：{{ esignContractDetail.contract?.flowId || '-' }}</span>
                  <span>签署人ID：{{ esignContractDetail.contract?.signerId || '-' }}</span>
                  <span>回传状态：{{ contractAlipaySyncStatusText(currentObj) }}</span>
                  <span v-if="currentObj.contractAlipayFileId">支付宝 file_id：{{ currentObj.contractAlipayFileId }}</span>
                  <span v-if="currentObj.contractAlipaySyncError" class="contract-panel__error">
                    失败原因：{{ currentObj.contractAlipaySyncError }}
                  </span>
                </div>
                <t-space>
                  <t-button v-if="!isEsignCompleted(esignContractDetail)" theme="primary" @click="startEsignContract">
                    发起/继续签署
                  </t-button>
                  <t-button
                    v-if="esignContractSignUrl(esignContractDetail)"
                    theme="primary"
                    variant="outline"
                    @click="openExternalUrl(esignContractSignUrl(esignContractDetail))"
                  >
                    打开签署入口
                  </t-button>
                  <t-button
                    v-if="esignSignedFileUrl(esignContractDetail)"
                    theme="success"
                    @click="openExternalUrl(esignSignedFileUrl(esignContractDetail))"
                  >
                    查看签署文件
                  </t-button>
                  <t-button v-if="hasContractPdf(currentObj)" theme="default" @click="openContractPdf(currentObj)">打开原协议PDF</t-button>
                  <t-button v-if="canGenerateContractPdf(currentObj)" theme="warning" @click="generateContractPdf(currentObj)">
                    {{ generateContractPdfButtonText(currentObj) }}
                  </t-button>
                  <t-button
                    v-if="canSyncContractToAlipay(currentObj)"
                    theme="success"
                    :loading="contractSyncLoading"
                    @click="syncContractToAlipay(currentObj)"
                  >
                    手动回传支付宝
                  </t-button>
                </t-space>
                <div v-if="contractPreviewUrl(esignContractDetail, currentObj)" class="contract-panel__preview">
                  <div class="contract-panel__preview-header">
                    <strong>{{ contractPreviewTitle(esignContractDetail) }}</strong>
                    <span>{{ contractPreviewHint(esignContractDetail) }}</span>
                  </div>
                  <iframe class="contract-panel__frame" :src="contractPreviewUrl(esignContractDetail, currentObj)" title="协议PDF预览"></iframe>
                </div>
              </div>
              <div v-else class="contract-panel">
                <t-tag theme="default" variant="light">默认 PDF 协议</t-tag>
                <div class="contract-panel__meta">
                  <span>订单号：{{ currentObj.orderNo || '-' }}</span>
                  <span>合同编号：{{ currentObj.contractNo || '-' }}</span>
                  <span>协议地址：{{ currentObj.contractPdfUrl || '暂未生成' }}</span>
                  <span>回传状态：{{ contractAlipaySyncStatusText(currentObj) }}</span>
                  <span v-if="currentObj.contractAlipayFileId">支付宝 file_id：{{ currentObj.contractAlipayFileId }}</span>
                  <span v-if="currentObj.contractAlipaySyncError" class="contract-panel__error">
                    失败原因：{{ currentObj.contractAlipaySyncError }}
                  </span>
                </div>
                <t-space>
                  <t-button v-if="hasContractPdf(currentObj)" theme="primary" @click="openContractPdf(currentObj)">打开协议PDF</t-button>
                  <t-button v-if="canGenerateContractPdf(currentObj)" theme="warning" @click="generateContractPdf(currentObj)">
                    {{ generateContractPdfButtonText(currentObj) }}
                  </t-button>
                  <t-button
                    v-if="canSyncContractToAlipay(currentObj)"
                    theme="success"
                    :loading="contractSyncLoading"
                    @click="syncContractToAlipay(currentObj)"
                  >
                    手动回传支付宝
                  </t-button>
                </t-space>
                <div v-if="hasContractPdf(currentObj)" class="contract-panel__preview">
                  <div class="contract-panel__preview-header">
                    <strong>协议预览</strong>
                    <span>如未刷新，请重新生成 PDF</span>
                  </div>
                  <iframe class="contract-panel__frame" :src="contractPdfPreviewUrl(currentObj)" title="协议PDF预览"></iframe>
                </div>
              </div>
            </t-loading>
          </section>

          <section v-else-if="activeOrderDrawerSection === 'billing'" class="drawer-section">
            <t-loading :loading="billingLoading">
              <div class="drawer-section__header">
                <div>
                  <strong>分期账单</strong>
                  <span>查看账单计划和支付状态，用户可在小程序逐期主动支付。</span>
                </div>
              </div>
              <div class="billing-layout">
                <div class="billing-summary-strip">
                  <div class="billing-summary-item">
                    <span>账单期数</span>
                    <strong>{{ installmentBillDetail.plan?.periodTotal || 0 }} 期</strong>
                  </div>
                  <div class="billing-summary-item">
                    <span>账单总额</span>
                    <strong>￥{{ fenToYuan(installmentBillDetail.plan?.totalAmount) }}</strong>
                  </div>
                  <div class="billing-summary-item billing-summary-item--danger">
                    <span>待收金额</span>
                    <strong>￥{{ fenToYuan(installmentBillDetail.plan?.unpaidAmount) }}</strong>
                  </div>
                </div>

                <div class="billing-withhold-panel">
                  <div class="billing-withhold-panel__columns">
                    <div class="billing-withhold-panel__main">
                      <div class="billing-withhold-panel__title">
                        <strong>代扣签约</strong>
                        <t-tag :theme="withholdStatusTheme(installmentBillDetail.withholdSign?.status)" variant="light">
                          {{ installmentBillDetail.withholdSign?.statusText || '未签约' }}
                        </t-tag>
                      </div>
                      <div class="billing-withhold-panel__meta">
                        <span>协议号：{{ installmentBillDetail.withholdSign?.agreementNo || '-' }}</span>
                        <span>签约时间：{{ formatBillingDateTime(installmentBillDetail.withholdSign?.signedAt) }}</span>
                      </div>
                      <p class="billing-withhold-panel__hint">{{ withholdActionHint }}</p>
                    </div>

                    <div class="billing-withhold-panel__main billing-withhold-panel__main--deduct">
                      <div class="billing-withhold-panel__title">
                        <strong>分期收款</strong>
                        <t-tag :theme="hasUnpaidInstallmentBill ? 'warning' : 'success'" variant="light">
                          {{ hasUnpaidInstallmentBill ? '待支付' : '无待收' }}
                        </t-tag>
                      </div>
                      <div class="billing-withhold-panel__meta">
                        <span>最近账单：{{ formatBillingDateTime(installmentBillDetail.plan?.nextDueDate) }}</span>
                        <span>待收金额：￥{{ fenToYuan(installmentBillDetail.plan?.unpaidAmount) }}</span>
                        <span>支付方式：小程序逐期支付</span>
                      </div>
                    </div>
                  </div>
                  <div v-if="withholdSignEntryEnabled" class="billing-withhold-panel__actions">
                    <t-button theme="primary" :disabled="!canStartWithholdSign" @click="startWithholdSign">
                      {{ withholdActionText }}
                    </t-button>
                  </div>
                </div>

                <div class="billing-table-panel">
                  <div class="billing-table-panel__header">
                    <div>
                      <strong>账单明细</strong>
                      <span>{{ (installmentBillDetail.bills || []).length }} 条</span>
                    </div>
                    <t-button theme="default" variant="outline" size="small" @click="loadInstallmentBills(true)">刷新账单</t-button>
                  </div>
                  <t-table
                    row-key="billId"
                    size="small"
                    :data="installmentBillDetail.bills || []"
                    :columns="installmentBillColumns"
                    cell-empty-content="-"
                  >
                    <template #amount="{ row }">￥{{ fenToYuan(row.amount) }}</template>
                    <template #paidAmount="{ row }">￥{{ fenToYuan(row.paidAmount) }}</template>
                    <template #dueDate="{ row }">{{ formatBillingDateTime(row.dueDate) }}</template>
                    <template #paidAt="{ row }">{{ formatBillingDateTime(row.paidAt) }}</template>
                    <template #status="{ row }">
                      <t-tag :theme="billStatusTheme(row.status)" variant="light">{{ row.statusText || row.status }}</t-tag>
                    </template>
                  </t-table>
                </div>
              </div>
            </t-loading>
          </section>
        </div>
      </div>
    </t-drawer>

    <display-settings-dialog
      v-model:visible="orderFieldSettingVisible"
      title="订单列表显示设置"
      :groups="orderDisplaySettingGroups"
      :model-value="orderDisplaySettingState"
      @save="saveOrderDisplaySettings"
    />

    <t-dialog
      v-model:visible="deliverVisible"
      :header="dialogTitle"
      width="720px"
      :close-on-overlay-click="!deliverBusy"
      :confirm-btn="{ content: deliverSubmitButtonText, loading: deliverBusy, disabled: deliverBusy }"
      :cancel-btn="{ content: '取消', disabled: deliverBusy }"
      @confirm="submitRentComDeliver"
    >
      <div class="tail-dialog-body">
        <div class="tail-form-section">
          <div class="tail-form-section__header">
            <div>
              <div class="tail-form-section__title">履约物流</div>
              <div class="tail-form-section__desc">填写发货或归还物流信息，快递公司可选择后自动带出名称。</div>
            </div>
          </div>
          <t-form class="tail-form tail-form--two" :data="rentComDeliverForm" label-align="top">
            <t-form-item label="快递公司">
              <t-select
                v-model="rentComDeliverForm.courCode"
                :disabled="deliverBusy"
                filterable
                clearable
                placeholder="请选择快递公司"
                v-bind="searchableSelectProps('orders.courCode', expressOptions)"
                @change="handleExpressCompanyChange"
              />
            </t-form-item>
            <t-form-item label="快递单号">
              <t-input v-model="rentComDeliverForm.courno" :disabled="deliverBusy" placeholder="请输入快递单号" />
            </t-form-item>
            <t-form-item label="快递公司名称">
              <t-input v-model="rentComDeliverForm.courName" :disabled="deliverBusy" placeholder="自动带出或手动填写" />
            </t-form-item>
          </t-form>
        </div>
      </div>
    </t-dialog>

    <t-dialog
      v-model:visible="shipRiskConfirmVisible"
      class="ship-risk-confirm-dialog"
      header="发货前租安盾风险确认"
      width="640px"
      :close-on-overlay-click="false"
      :confirm-btn="{
        content: shipRiskConfirm.danger ? '已知晓风险，继续发货' : '已查看，继续发货',
        theme: shipRiskConfirm.danger ? 'danger' : 'primary',
        loading: deliverSubmitting,
        disabled: deliverSubmitting,
      }"
      :cancel-btn="{ content: '取消发货', disabled: deliverSubmitting }"
      @confirm="finishShipRiskConfirm(true)"
      @cancel="finishShipRiskConfirm(false)"
      @close="finishShipRiskConfirm(false)"
    >
      <div class="ship-risk-confirm">
        <div class="ship-risk-confirm__meta">
          <div>
            <span>订单号</span>
            <strong>{{ shipRiskConfirm.orderNo }}</strong>
          </div>
          <div>
            <span>风险服务</span>
            <strong>{{ shipRiskConfirm.provider }}</strong>
          </div>
          <div>
            <span>产品版本</span>
            <strong>{{ shipRiskConfirm.productEdition }}</strong>
          </div>
        </div>

        <div
          class="ship-risk-confirm__summary"
          :class="shipRiskConfirm.danger ? 'ship-risk-confirm__summary--danger' : 'ship-risk-confirm__summary--safe'"
        >
          <t-icon :name="shipRiskConfirm.danger ? 'error-circle-filled' : 'check-circle-filled'" />
          <div>
            <strong>{{ shipRiskConfirm.summaryTitle }}</strong>
            <p>{{ shipRiskConfirm.summaryText }}</p>
          </div>
        </div>

        <div class="ship-risk-confirm__list">
          <div v-for="(item, index) in shipRiskConfirm.items" :key="`${item.label}-${index}`" class="ship-risk-confirm__item">
            <div class="ship-risk-confirm__item-head">
              <span>{{ item.label }}</span>
              <t-tag v-if="item.badge" :theme="item.theme" variant="light">{{ item.badge }}</t-tag>
            </div>
            <p>{{ item.value }}</p>
            <small v-if="item.description">{{ item.description }}</small>
          </div>
        </div>

        <div class="ship-risk-confirm__notice">继续发货表示已查看并确认承担本次履约风险。</div>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="finishVisible" header="选择完结类型" width="620px" @confirm="submitRentOrderFinish">
      <div class="tail-dialog-body">
        <div class="tail-form-section">
          <div class="tail-form-section__header">
            <div>
              <div class="tail-form-section__title">订单完结</div>
              <div class="tail-form-section__desc">选择与支付宝租赁状态匹配的完结场景。</div>
            </div>
          </div>
          <t-form class="tail-form" :data="rentOrderFinishForm" label-align="top">
            <t-form-item label="完结类型">
              <t-select v-model="rentOrderFinishForm.finishType" :options="finishTypeOptions" />
            </t-form-item>
          </t-form>
        </div>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="deductVisible" header="扣除押金" width="760px" @confirm="submitDepositDeduct">
      <div class="tail-dialog-body">
        <div class="tail-form-section tail-form-section--accent">
          <div class="tail-form-section__header">
            <div>
              <div class="tail-form-section__title">押金扣减概览</div>
              <div class="tail-form-section__desc">扣减前请核对订单押金和剩余冻结金额。</div>
            </div>
          </div>
          <div class="amount-highlight-grid">
            <div class="amount-highlight-card amount-highlight-card--muted">
              <span>订单号</span>
              <strong>{{ currentObj.orderNo || '-' }}</strong>
            </div>
            <div class="amount-highlight-card">
              <span>订单押金</span>
              <strong>￥{{ fenToYuan(currentObj.orderDeposit) }}</strong>
            </div>
            <div class="amount-highlight-card amount-highlight-card--danger">
              <span>剩余冻结</span>
              <strong>￥{{ fenToYuan(currentObj.orderRestDeposit) }}</strong>
            </div>
          </div>
        </div>
        <div class="tail-form-section">
          <div class="tail-form-section__header">
            <div>
              <div class="tail-form-section__title">扣减信息</div>
              <div class="tail-form-section__desc">按支付宝售后原因填写扣减类型、原因码与本次扣减金额。</div>
            </div>
          </div>
          <t-form class="tail-form tail-form--two" :data="depositDeductForm" label-align="top">
            <t-form-item label="费用类型">
              <t-select v-model="depositDeductForm.feeType" :options="feeTypeOptions" @change="handleDepositFeeTypeChange" />
            </t-form-item>
            <t-form-item label="原因码">
              <t-select v-model="depositDeductForm.reasonCode" :options="reasonCodeOptions" />
            </t-form-item>
            <t-form-item class="tail-form-item--money" label="扣减金额">
              <div class="money-input">
                <span class="money-input__prefix">￥</span>
                <t-input v-model="depositDeductForm.deductAmount" placeholder="请输入金额" />
                <span class="money-input__suffix">元</span>
              </div>
            </t-form-item>
            <t-form-item class="tail-form-item--wide" label="备注">
              <t-textarea v-model="depositDeductForm.remark" placeholder="可选，填写本次扣减说明" :maxlength="200" />
            </t-form-item>
          </t-form>
        </div>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="remarkVisible" header="添加备注" width="620px" @confirm="submitRemark">
      <div class="tail-dialog-body">
        <div class="tail-form-section">
          <div class="tail-form-section__header">
            <div>
              <div class="tail-form-section__title">订单备注</div>
              <div class="tail-form-section__desc">备注会展示在订单列表的备注状态区域。</div>
            </div>
          </div>
          <t-form class="tail-form" label-align="top">
            <t-form-item label="备注内容">
              <t-textarea v-model="remarkText" placeholder="请输入备注内容" :maxlength="200" />
            </t-form-item>
          </t-form>
        </div>
      </div>
    </t-dialog>

    <t-dialog v-model:visible="rentOrderDetailVisible" header="租赁订单详情" width="70%" :footer="false">
      <div class="dialog-section">
        <t-descriptions bordered :column="2" size="small">
          <t-descriptions-item label="订单标题">{{ rentOrderDetail.title || '-' }}</t-descriptions-item>
          <t-descriptions-item label="订单号">{{ rentOrderDetail.orderId || '-' }}</t-descriptions-item>
          <t-descriptions-item label="订单状态">
            <t-tag :theme="rentOrderDetail.status === 'FINISHED' ? 'success' : 'default'" variant="light">
              {{ rentOrderDetail.status || '-' }}
            </t-tag>
          </t-descriptions-item>
          <t-descriptions-item label="创建时间">{{ formatDate(rentOrderDetail.orderCreateTime) }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div class="dialog-section">
        <div class="section-title">收货地址</div>
        <t-descriptions bordered :column="1" size="small">
          <t-descriptions-item label="收货人">{{ rentOrderDetail.addressInfo?.receiverName || '-' }}</t-descriptions-item>
          <t-descriptions-item label="联系电话">{{ rentOrderDetail.addressInfo?.telNumber || '-' }}</t-descriptions-item>
          <t-descriptions-item label="详细地址">{{ rentOrderDetail.addressInfo?.detailedAddress || '-' }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div class="dialog-section">
        <div class="section-title">价格信息</div>
        <div class="amount-highlight-grid">
          <div class="amount-highlight-card amount-highlight-card--primary">
            <span>订单价格</span>
            <strong>￥{{ rentOrderDetail.priceInfo?.orderPrice || '0.00' }}</strong>
          </div>
          <div class="amount-highlight-card amount-highlight-card--danger">
            <span>商品押金</span>
            <strong>￥{{ rentOrderDetail.priceInfo?.depositPrice || '0.00' }}</strong>
          </div>
          <div class="amount-highlight-card">
            <span>运费</span>
            <strong>￥{{ rentOrderDetail.priceInfo?.freight || '0.00' }}</strong>
          </div>
          <div class="amount-highlight-card">
            <span>附加费用</span>
            <strong>￥{{ rentOrderDetail.priceInfo?.additionalPrice || '0.00' }}</strong>
          </div>
        </div>
      </div>
      <div class="dialog-section">
        <div class="section-title">支付状态</div>
        <t-table row-key="installmentNo" size="small" :data="rentOrderDetail.rentStatementInfos || []" :columns="rentStatementColumns" cell-empty-content="-">
          <template #statementStatus="{ row }">
            <t-tag :theme="row.statementStatus === 'PAID' ? 'success' : 'warning'" variant="light">
              {{ row.statementStatus === 'PAID' ? '已支付' : row.statementStatus || '未支付' }}
            </t-tag>
          </template>
        </t-table>
      </div>
      <div class="dialog-section">
        <div class="section-title">租赁商品</div>
        <t-table row-key="outItemId" size="small" :data="rentOrderDetail.itemInfos || []" :columns="rentItemColumns" cell-empty-content="-" />
      </div>
    </t-dialog>

    <t-dialog v-model:visible="riskInfoVisible" header="风险信息详情" width="70%">
      <div v-if="riskInfoDetail.cloudRentRisk" class="dialog-section">
        <div class="section-title">云智能租赁风控</div>
        <t-alert
          v-if="riskInfoDetail.cloudRentRisk.available === false"
          theme="warning"
          :message="`云风控查询失败：${riskInfoDetail.cloudRentRisk.errorMessage || '暂未返回原因'}`"
        />
        <div v-else class="risk-list">
          <div class="risk-card">
            <div class="risk-card-title risk-card-title--inline">
              <span>综合判断</span>
              <t-tag :theme="riskLevelTheme(riskInfoDetail.cloudRentRisk.riskRank)" variant="light">
                {{ riskRankText(riskInfoDetail.cloudRentRisk.riskRank) }}
              </t-tag>
            </div>
            <div class="risk-item">风险名称：{{ riskInfoDetail.cloudRentRisk.riskName || '-' }}</div>
            <div class="risk-item">风险描述：{{ riskInfoDetail.cloudRentRisk.riskDesc || '-' }}</div>
          </div>
          <div v-for="item in cloudRentRiskItems(riskInfoDetail.cloudRentRisk)" :key="item.key" class="risk-card">
            <div class="risk-card-title risk-card-title--inline">
              <span>{{ item.title }}</span>
              <t-tag :theme="riskLevelTheme(item.riskRank)" variant="light">{{ riskRankText(item.riskRank) }}</t-tag>
            </div>
            <div class="risk-item">风险名称：{{ item.riskName || '-' }}</div>
            <div v-if="item.riskDesc" class="risk-item">风险描述：{{ item.riskDesc }}</div>
          </div>
        </div>
      </div>
      <div v-if="!riskInfoDetail.cloudRentRisk" class="dialog-section">
        <t-alert
          v-if="riskInfoDetail.alipayRisk && riskInfoDetail.alipayRisk.available === false"
          theme="warning"
          :message="`${riskProviderDisplayName(riskInfoDetail)}查询失败：${riskInfoDetail.alipayRisk.errorMessage || '暂未返回原因'}`"
        />
        <t-descriptions bordered :column="2" size="small">
          <t-descriptions-item label="产品版本">
            <t-tag :theme="riskInfoDetail.productEdition === 'PRO' ? 'success' : 'default'" variant="light">
              {{ productEditionText(riskInfoDetail.productEdition) }}
            </t-tag>
          </t-descriptions-item>
          <t-descriptions-item label="联营订单分组">{{ vamGroupText(riskInfoDetail.vamGroup) }}</t-descriptions-item>
          <t-descriptions-item label="风险策略值">{{ riskInfoDetail.riskBasicInfo?.riskPolicyValue || '-' }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div v-if="!riskInfoDetail.cloudRentRisk" class="dialog-section">
        <div class="section-title">风险信息列表</div>
        <div v-if="riskInfoDetail.riskInfos?.length" class="risk-list">
          <div v-for="(riskInfo, index) in riskInfoDetail.riskInfos" :key="index" class="risk-card">
            <div class="risk-card-title">风险类型：{{ riskTypeText(riskInfo.riskType) }}</div>
            <div v-for="(item, itemIndex) in riskInfo.riskItemList || []" :key="itemIndex" class="risk-item">
              <div>风险项：{{ riskCodeText(item.riskCode) }}</div>
              <div v-if="riskCodeDescription(item.riskCode)">说明：{{ riskCodeDescription(item.riskCode) }}</div>
              <div>
                风险级别：
                <t-tag :theme="riskLevelTheme(item.riskLevel)" variant="light">{{ riskRankText(item.riskLevel) }}</t-tag>
              </div>
              <div v-if="item.hitDetail">命中详情：{{ item.hitDetail }}</div>
            </div>
          </div>
        </div>
        <div v-else class="empty-dialog">暂无风险信息</div>
      </div>
      <div v-if="riskInfoDetail.comprehensiveRiskModels" class="dialog-section">
        <div class="section-title">综合风险模型</div>
        <t-descriptions bordered :column="1" size="small">
          <t-descriptions-item label="风险级别">{{ riskRankText(riskInfoDetail.comprehensiveRiskModels.riskLevel) }}</t-descriptions-item>
          <t-descriptions-item label="风险级别类型">{{ riskLevelTypeText(riskInfoDetail.comprehensiveRiskModels.riskLevelType) }}</t-descriptions-item>
          <t-descriptions-item label="描述">{{ riskInfoDetail.comprehensiveRiskModels.desc || '-' }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div v-if="riskInfoDetail.extremelyLowRiskModels" class="dialog-section">
        <div class="section-title">极低风险模型</div>
        <t-descriptions bordered :column="1" size="small">
          <t-descriptions-item label="风险级别">{{ riskRankText(riskInfoDetail.extremelyLowRiskModels.riskLevel) }}</t-descriptions-item>
          <t-descriptions-item label="风险级别类型">{{ riskLevelTypeText(riskInfoDetail.extremelyLowRiskModels.riskLevelType) }}</t-descriptions-item>
          <t-descriptions-item label="描述">{{ riskInfoDetail.extremelyLowRiskModels.desc || '-' }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div v-if="riskInfoDetail.highRiskModels" class="dialog-section">
        <div class="section-title">高风险模型</div>
        <t-descriptions bordered :column="1" size="small">
          <t-descriptions-item label="风险级别">{{ riskRankText(riskInfoDetail.highRiskModels.riskLevel) }}</t-descriptions-item>
          <t-descriptions-item label="风险级别类型">{{ riskLevelTypeText(riskInfoDetail.highRiskModels.riskLevelType) }}</t-descriptions-item>
          <t-descriptions-item label="描述">{{ riskInfoDetail.highRiskModels.desc || '-' }}</t-descriptions-item>
        </t-descriptions>
      </div>
      <div v-if="riskInfoDetail.shipGoodsRiskModels?.length" class="dialog-section">
        <div class="section-title">发货风险模型</div>
        <div class="risk-list">
          <div v-for="(item, index) in riskInfoDetail.shipGoodsRiskModels" :key="index" class="risk-card">
            <div class="risk-item">风险项：{{ riskCodeText(item.riskCode) }}</div>
            <div v-if="riskCodeDescription(item.riskCode)" class="risk-item">说明：{{ riskCodeDescription(item.riskCode) }}</div>
            <div class="risk-item">发货建议：{{ shipRiskDecisionText(item) }}</div>
          </div>
        </div>
      </div>
      <div v-if="canOperateRiskReview(currentRiskOrder)" class="dialog-section">
        <div class="section-title">后续流程要求</div>
        <t-alert theme="info" message="身份证照片会阻断支付，需后台二次审核通过；e签宝会在用户确认收货前校验签署完成。" />
        <div class="risk-option-row">
          <span>要求上传身份证照片</span>
          <t-switch v-model="riskRequireIdCardPhoto" />
        </div>
        <div class="risk-option-row">
          <span>要求完成 e签宝电子合同</span>
          <t-switch v-model="riskRequireEsign" />
        </div>
      </div>
      <template #footer>
        <t-space v-if="canOperateRiskReview(currentRiskOrder)">
          <t-button theme="success" @click="handleRiskApprove">同意</t-button>
          <t-button theme="danger" @click="handleRiskNoApprove">不同意</t-button>
          <t-button @click="riskInfoVisible = false">取消</t-button>
        </t-space>
        <t-button v-else @click="riskInfoVisible = false">关闭</t-button>
      </template>
    </t-dialog>

    <t-dialog v-model:visible="operLogVisible" header="台账记录" width="80%" :footer="false">
      <t-table
        row-key="logId"
        hover
        size="small"
        :data="operLogRecords"
        :columns="operLogColumns"
        :loading="operLogLoading"
        :pagination="operLogPagination"
        :disable-data-page="true"
        cell-empty-content="-"
        @page-change="handleOperLogPageChange"
      >
        <template #operType="{ row }"><t-tag variant="light">{{ formatOperType(row.operType) }}</t-tag></template>
        <template #operSource="{ row }"><t-tag variant="light">{{ formatOperSource(row.operSource) }}</t-tag></template>
        <template #operResult="{ row }">
          <t-tag :theme="row.operResult === 'SUCCESS' ? 'success' : 'danger'" variant="light">{{ formatOperResult(row.operResult) }}</t-tag>
        </template>
        <template #statusBefore="{ row }">{{ formatOrderStatus(row.statusBefore) }}</template>
        <template #statusAfter="{ row }">{{ formatOrderStatus(row.statusAfter) }}</template>
        <template #operTime="{ row }">{{ formatDate(row.operTime) }}</template>
      </t-table>
    </t-dialog>

    <t-dialog v-model:visible="returnRecordVisible" header="用户寄回记录" width="720px" :footer="false">
      <t-loading :loading="returnRecordLoading">
        <t-empty v-if="!hasReturnRecord" description="暂无寄回记录" />
        <div v-else class="tail-dialog-body">
          <div class="tail-form-section">
            <div class="tail-form-section__header">
              <div>
                <div class="tail-form-section__title">寄回物流</div>
                <div class="tail-form-section__desc">用户提交的寄回状态、物流单号和图片凭证。</div>
              </div>
            </div>
            <t-form class="tail-form tail-form--two tail-form--readonly" :data="returnRecordData" label-align="top">
              <t-form-item label="订单号">{{ returnRecordData.orderNo || '-' }}</t-form-item>
              <t-form-item label="寄回状态">
                <t-tag :theme="returnStatusTheme(returnRecordData.status)" variant="light">{{ returnStatusText(returnRecordData.status) }}</t-tag>
                <span v-if="returnRecordData.failReason" class="fail-text">{{ returnRecordData.failReason }}</span>
              </t-form-item>
              <t-form-item label="寄回方式">{{ returnRecordData.returnType || '-' }}</t-form-item>
              <t-form-item label="快递公司">
                {{ returnRecordData.expressCompany || '-' }}<span v-if="returnRecordData.expressCode">（{{ returnRecordData.expressCode }}）</span>
              </t-form-item>
              <t-form-item label="寄回单号">{{ returnRecordData.expressNo || '-' }}</t-form-item>
              <t-form-item label="提交时间">{{ formatDate(returnRecordData.submittedAt) }}</t-form-item>
              <t-form-item class="tail-form-item--wide" label="寄回说明">{{ returnRecordData.detailRemark || '-' }}</t-form-item>
              <t-form-item class="tail-form-item--wide" label="寄回照片">
                <div v-if="returnPhotoList.length" class="return-record-images">
                  <t-image
                    v-for="(url, index) in returnPhotoList"
                    :key="index"
                    class="return-record-image"
                    :src="resolveUploadAssetPath(url)"
                    fit="cover"
                  />
                </div>
                <span v-else>-</span>
              </t-form-item>
            </t-form>
          </div>
        </div>
      </t-loading>
    </t-dialog>

    <t-dialog v-model:visible="depositVisible" header="押金/预授权支付情况" width="800px" :footer="false">
      <t-loading :loading="depositLoading">
        <t-alert v-if="depositData.success === false" theme="error" :message="`查询失败：${depositData.subMsg || depositData.errorMsg || depositData.subCode || '未知错误'}`" />
        <template v-else-if="depositData.orderNo">
          <div class="dialog-section">
            <div class="section-title">基础信息</div>
            <div class="amount-highlight-grid">
              <div class="amount-highlight-card amount-highlight-card--primary">
                <span>订单押金</span>
                <strong>￥{{ fenToYuan(depositData.orderDeposit) }}</strong>
              </div>
              <div class="amount-highlight-card amount-highlight-card--danger">
                <span>剩余冻结押金</span>
                <strong>￥{{ fenToYuan(depositData.remainingDeposit) }}</strong>
              </div>
              <div class="amount-highlight-card amount-highlight-card--muted">
                <span>当前说明</span>
                <strong>{{ depositSummaryHint }}</strong>
              </div>
            </div>
            <t-descriptions bordered :column="1" size="small">
              <t-descriptions-item label="订单号">{{ depositData.orderNo || '-' }}</t-descriptions-item>
              <t-descriptions-item label="租赁单号">{{ depositData.rentOrderId || '-' }}</t-descriptions-item>
              <t-descriptions-item label="支付宝授权号">{{ depositData.authNo || '-' }}</t-descriptions-item>
              <t-descriptions-item label="租金支付交易号">{{ depositData.paymentTradeNo || '-' }}</t-descriptions-item>
              <t-descriptions-item label="内部订单ID">{{ depositData.orderId || '-' }}</t-descriptions-item>
              <t-descriptions-item label="支付宝订单状态"><t-tag variant="light">{{ depositData.status || '-' }}</t-tag></t-descriptions-item>
              <t-descriptions-item label="本地记录状态"><t-tag variant="light">{{ depositData.alipayStatus || '-' }}</t-tag></t-descriptions-item>
            </t-descriptions>
          </div>
          <div v-if="depositData.depositTypeText || depositData.depositTypeMsg" class="dialog-section">
            <div class="section-title">押金类型（支付宝）</div>
            <t-descriptions bordered :column="1" size="small">
              <t-descriptions-item v-if="depositData.depositTypeText" label="押金扣除类型">{{ depositData.depositTypeText }}</t-descriptions-item>
              <t-descriptions-item v-if="depositData.depositTypeMsg" label="说明">{{ depositData.depositTypeMsg }}</t-descriptions-item>
            </t-descriptions>
          </div>
          <div v-if="fundAuthDetailList.length" class="dialog-section">
            <div class="section-title">资金授权详情（支付宝）</div>
            <t-descriptions bordered :column="1" size="small">
              <t-descriptions-item v-for="item in fundAuthDetailList" :key="item.key" :label="item.label">{{ item.value }}</t-descriptions-item>
            </t-descriptions>
          </div>
          <div v-if="priceInfoList.length" class="dialog-section">
            <div class="section-title">支付宝价格信息</div>
            <t-descriptions bordered :column="1" size="small">
              <t-descriptions-item v-for="item in priceInfoList" :key="item.key" :label="item.label">{{ item.value }}</t-descriptions-item>
            </t-descriptions>
          </div>
          <div v-if="depositData.rentStatementInfos?.length" class="dialog-section">
            <div class="section-title">分期/账单列表（支付宝）</div>
            <t-table row-key="installmentNo" size="small" :data="depositData.rentStatementInfos" :columns="depositStatementColumns" cell-empty-content="-">
              <template #statementStatus="{ row }">
                <t-tag :theme="row.statementStatus === 'PAID' ? 'success' : 'default'" variant="light">{{ row.statementStatus || '-' }}</t-tag>
              </template>
              <template #planPayTime="{ row }">{{ formatDate(row.planPayTime) }}</template>
            </t-table>
          </div>
        </template>
        <t-empty v-else description="暂无数据" />
      </t-loading>
    </t-dialog>

    <t-dialog v-model:visible="deductRecordVisible" header="扣除记录" width="92%" :footer="false">
      <t-table
        row-key="id"
        hover
        size="small"
        :data="deductRecords"
        :columns="deductRecordColumns"
        :loading="deductRecordLoading"
        :pagination="deductRecordPagination"
        :disable-data-page="true"
        cell-empty-content="-"
        @page-change="handleDeductRecordPageChange"
      >
        <template #createTime="{ row }">{{ formatTimestamp(row.createTime) }}</template>
        <template #feeType="{ row }">{{ feeTypeText(row.feeType) }}</template>
        <template #deductAmount="{ row }">{{ fenToYuan(row.deductAmount) }} 元</template>
        <template #status="{ row }"><t-tag :theme="recordStatusTheme(row.status)" variant="light">{{ row.status || '-' }}</t-tag></template>
        <template #aftersaleStatus="{ row }"><t-tag :theme="aftersaleStatusTheme(row.aftersaleStatus)" variant="light">{{ row.aftersaleStatus || '-' }}</t-tag></template>
        <template #deductResult="{ row }"><t-tag :theme="deductResultTheme(row)" variant="light">{{ deductResultText(row) }}</t-tag></template>
        <template #needOperation="{ row }">{{ needOperationText(row.needOperation) }}</template>
        <template #beforeRemainingDeposit="{ row }">{{ fenToYuan(row.beforeRemainingDeposit) }} 元</template>
        <template #afterRemainingDeposit="{ row }">{{ nullableFenToYuan(row.afterRemainingDeposit) }}</template>
        <template #alipaySubMsg="{ row }">
          <span v-if="deductErrorBrief(row)" class="deduct-error-cell" :title="deductErrorFullText(row)">
            <span class="deduct-error-cell__summary">{{ deductErrorBrief(row) }}</span>
            <span v-if="row.alipaySubCode" class="deduct-error-cell__code">{{ row.alipaySubCode }}</span>
          </span>
          <span v-else>-</span>
        </template>
        <template #recordOperation="{ row }">
          <t-space v-if="canOperateDeductRecord(row)" size="small">
            <t-button v-if="canApproveWithUserPay(row)" size="small" theme="primary" @click="confirmDeductRecord(row, 'APPROVE_WITH_USER_PAY')">发起赔付</t-button>
            <t-button v-if="canRejectDeductRecord(row)" size="small" theme="warning" @click="confirmDeductRecord(row, 'MERCHANT_REJECT')">拒绝售后</t-button>
            <t-button v-if="canFinishDeductRecord(row)" size="small" theme="success" @click="confirmDeductRecord(row, 'PAY_COMPENSATION')">{{ finishDeductButtonText(row) }}</t-button>
            <t-button v-if="canFinishAftersaleRecord(row)" size="small" theme="success" @click.stop="confirmDeductRecord(row, 'AFTERSALE_FINISH')">补完结售后</t-button>
            <t-button v-if="canCancelDeductRecord(row)" size="small" theme="danger" @click="confirmDeductRecord(row, 'USER_CANCEL_APPLY')">撤销售后</t-button>
          </t-space>
          <span v-else>-</span>
        </template>
      </t-table>
    </t-dialog>

    <t-dialog v-model:visible="jsonVisible" :header="dialogTitle" width="920px" :footer="false">
      <pre class="json-block">{{ dialogText }}</pre>
    </t-dialog>

    <t-dialog v-model:visible="identityPhotoPreviewVisible" header="身份证照片" width="720px" :footer="false">
      <div class="identity-photo-preview">
        <t-image v-if="identityPhotoPreviewUrl" :src="identityPhotoPreviewUrl" fit="contain" />
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { createSearchableOptions } from '@shared/utils/search-options';
import { apiBase, cleanQuery, rentApi, resolveData, resolvePage, resolveUploadAssetPath, type AnyRecord } from '@/api/rent';
import AlipayCleanListPage from '@/pages/alipay/components/AlipayCleanListPage.vue';
import AlipayQueryActions from '@/pages/alipay/components/AlipayQueryActions.vue';
import DisplaySettingsDialog, { type DisplaySettingGroup } from '@/pages/alipay/components/DisplaySettingsDialog.vue';
import {
  buttonCode,
  formatDateTime,
  formatMoney,
  formatOperResult,
  formatOperSource,
  formatOperType,
  formatOrderStatus,
} from '@/pages/alipay/shared';
import { usePermissionStore } from '@/store';
import {
  displaySettingStorageKey,
  orderDisplayItems,
  type DisplaySettingItem,
  type DisplaySettingState,
} from '@/utils/display-settings';
import { buildGatewayUrl } from '@/utils/gateway';
import { useAutoQuery } from '@/utils/useAutoQuery';

type SelectOption = {
  label: string;
  value: string | number;
};

type PageInfo = {
  current: number;
  pageSize: number;
};

type ListLoadOptions = {
  silent?: boolean;
};

type ShipRiskConfirmItem = {
  label: string;
  value: string;
  badge?: string;
  theme?: 'primary' | 'success' | 'warning' | 'danger' | 'default';
  description?: string;
};

type ShipRiskConfirmPayload = {
  orderNo: string;
  provider: string;
  productEdition: string;
  danger: boolean;
  summaryTitle: string;
  summaryText: string;
  items: ShipRiskConfirmItem[];
};

const typeOptions: SelectOption[] = [
  { value: 'goodTitle', label: '商品名称' },
  { value: 'userTitle', label: '收货人' },
  { value: 'tel', label: '手机号码' },
  { value: 'orderNo', label: '订单号' },
  { value: 'idCard', label: '身份证信息' },
];

const alipayStatusOptions: SelectOption[] = [
  { value: -1, label: '全部' },
  { value: 'CREATED', label: '用户下单' },
  { value: 'SIGNED', label: '用户已签约' },
  { value: 'APPROVED', label: '商家审核通过' },
  { value: 'DELIVERED', label: '商家发货' },
  { value: 'RECEIVED', label: '用户确认收货' },
  { value: 'RETURN_DELIVERED', label: '用户寄回' },
  { value: 'RETURN_RECEIVED', label: '商家签收' },
  { value: 'FINISHED', label: '订单完结' },
  { value: 'CLOSED', label: '订单关闭' },
  { value: 'PAID', label: '已支付' },
  { value: 'PENDING_CANCEL', label: '退款中' },
];

const statusMap: Record<string, string> = Object.fromEntries(alipayStatusOptions.map((item) => [String(item.value), item.label]));
const successStatuses = ['PAID', 'APPROVED', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'];
const contractReadyStatuses = ['SIGNED', 'APPROVED', 'PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED', 'CLOSED'];
const contractSyncReadyStatuses = ['RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'];
const MERCHANT_DELIVERY_SEND = 'MERCHANT_DELIVERY_SEND';
const USER_DELIVERY_SEND = 'USER_DELIVERY_SEND';

const dateOptions: SelectOption[] = [
  { value: '', label: '全部' },
  { value: 7, label: '7天内订单' },
  { value: 15, label: '15天内订单' },
  { value: 30, label: '30天内订单' },
];

const finishTypeOptions: SelectOption[] = [
  { value: 'USER_RETURNED', label: '用户租赁到期，已经归还商品' },
  { value: 'OTHER', label: '其他场景下完结订单（无需归还）' },
  { value: 'USER_RETURNED_IN_ADVANCE', label: '用户提前完成归还' },
];

const feeTypeOptions: SelectOption[] = [
  { value: 'INDEMNITY', label: '赔付金' },
  { value: 'LATE_FEE', label: '违约金' },
];

const reasonCodeOptionsMap: Record<string, SelectOption[]> = {
  INDEMNITY: [
    { value: 'ITEM_DAMAGED', label: '物品损坏赔付' },
    { value: 'ITEM_REPAIR', label: '物品维修赔付' },
    { value: 'ITEM_LOST', label: '物品丢失赔付' },
    { value: 'ITEM_DEPRECIATION', label: '物品折旧赔付' },
  ],
  LATE_FEE: [
    { value: 'RETURN_EARLY', label: '用户提前归还违约' },
    { value: 'RETURN_OVERDUE', label: '用户逾期归还违约' },
  ],
};

const expressCompanyList = [
  { code: 'SF', name: '顺丰速运' },
  { code: 'YTO', name: '圆通速递' },
  { code: 'YUNDA', name: '韵达快递' },
  { code: 'STO', name: '申通快递' },
  { code: 'ZTO', name: '中通速递' },
  { code: 'HTKY', name: '百世快递' },
  { code: 'JD', name: '京东' },
  { code: 'EMS', name: 'EMS' },
  { code: 'SS', name: '闪送' },
  { code: 'DADA', name: '达达' },
];
const expressOptions = expressCompanyList.map((item) => ({ label: item.name, value: item.code }));

const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 1);
const route = useRoute();
const router = useRouter();
const { searchableSelectProps } = createSearchableOptions();
const permissionStore = usePermissionStore();
const selectedDate = ref(formatDateOnly(yesterday));
const orderQueryExpanded = ref(false);
const loading = ref(false);
const contractSyncLoading = ref(false);
const orderList = ref<AnyRecord[]>([]);
const pageTotal = ref(0);

const tableParams = reactive({
  page: 1,
  limit: 10,
  type: '',
  chooseTime: '' as string | number,
  chooseDate: [] as Array<string | number>,
  userName: '',
  alipayStatus: '' as string | number,
  start: '' as string | number,
  end: '' as string | number,
});

const orderPagination = computed(() => ({
  current: tableParams.page,
  pageSize: tableParams.limit,
  total: pageTotal.value,
  pageSizeOptions: [10, 20, 50, 100],
}));
const orderDetailDrawerSize = computed(() => isNarrowOrderDrawerViewport.value ? '100vw' : 'min(1040px, calc(100vw - 96px))');

const currentObj = ref<AnyRecord>({});
const dialogTitle = ref('');
const jsonVisible = ref(false);
const jsonData = ref<unknown>({});
const dialogText = computed(() => JSON.stringify(jsonData.value, null, 2));
const identityPhotoPreviewVisible = ref(false);
const identityPhotoPreviewUrl = ref('');
const createEmptyShipRiskConfirm = (): ShipRiskConfirmPayload => ({
  orderNo: '-',
  provider: '租安盾',
  productEdition: '-',
  danger: false,
  summaryTitle: '风险结果待确认',
  summaryText: '请先完成风险查询。',
  items: [],
});

type OrderDrawerSectionKey = 'overview' | 'order' | 'deposit' | 'deduct' | 'fulfillment' | 'ledger' | 'risk' | 'contract' | 'billing';
type OrderDetailDrawerMode = 'full' | 'single';
type TimelineDotColor = string;
type TimelineTagTheme = 'primary' | 'success' | 'warning' | 'danger' | 'default';
type DetailTimelineField = {
  label: string;
  value: string;
};
type DetailTimelineItem = {
  key: string;
  label: string;
  title: string;
  status?: string;
  description?: string;
  dotColor: TimelineDotColor;
  tagTheme: TimelineTagTheme;
  fields: DetailTimelineField[];
  row?: AnyRecord;
  requestText?: string;
  responseText?: string;
  errorText?: string;
  payloadSummary?: string;
};

const orderDetailDrawerVisible = ref(false);
const orderDetailDrawerMode = ref<OrderDetailDrawerMode>('full');
const activeOrderDrawerSection = ref<OrderDrawerSectionKey>('overview');
const isNarrowOrderDrawerViewport = ref(false);
const drawerLoadedSections = reactive<Record<OrderDrawerSectionKey, boolean>>({
  overview: true,
  order: false,
  deposit: false,
  deduct: false,
  fulfillment: false,
  ledger: false,
  risk: false,
  contract: false,
  billing: false,
});
const orderDetailLoading = ref(false);
const riskInfoLoading = ref(false);
const contractLoading = ref(false);
const billingLoading = ref(false);
const contractPdfPreviewVersion = ref(Date.now());

const deliverVisible = ref(false);
const deliverRiskLoading = ref(false);
const deliverSubmitting = ref(false);
const deliverBusy = computed(() => deliverRiskLoading.value || deliverSubmitting.value);
const deliverSubmitButtonText = computed(() => {
  if (deliverRiskLoading.value) return '查询风险中';
  if (deliverSubmitting.value) return '提交中';
  return '确认';
});
const finishVisible = ref(false);
const deductVisible = ref(false);
const remarkVisible = ref(false);
const rentOrderDetailVisible = ref(false);
const riskInfoVisible = ref(false);
const shipRiskConfirmVisible = ref(false);
const shipRiskConfirm = ref<ShipRiskConfirmPayload>(createEmptyShipRiskConfirm());
let shipRiskConfirmResolver: ((confirmed: boolean) => void) | null = null;
const operLogVisible = ref(false);
const operLogLoading = ref(false);
const returnRecordVisible = ref(false);
const returnRecordLoading = ref(false);
const depositVisible = ref(false);
const depositLoading = ref(false);
const deductRecordVisible = ref(false);
const deductRecordLoading = ref(false);
const orderFieldSettingVisible = ref(false);
const remarkText = ref('');
const rentOrderDetail = ref<AnyRecord>({});
const riskInfoDetail = ref<AnyRecord>({});
const esignContractDetail = ref<AnyRecord>({});
const signedContractPreviewUrl = ref('');
const installmentBillDetail = ref<AnyRecord>({});
const currentRiskOrder = ref<AnyRecord | null>(null);
const riskRequireIdCardPhoto = ref(false);
const riskRequireEsign = ref(false);
const operLogRecords = ref<AnyRecord[]>([]);
const operLogPage = reactive({
  current: 1,
  pageSize: 5,
  total: 0,
});
const deductRecordPage = reactive({
  current: 1,
  pageSize: 5,
  total: 0,
});
const returnRecordData = ref<AnyRecord>({});
const depositData = ref<AnyRecord>({});
const deductRecords = ref<AnyRecord[]>([]);

const withholdSignEntryEnabled = false;
const withholdStatus = computed(() => String(installmentBillDetail.value.withholdSign?.status || 'UNSIGNED').toUpperCase());
const withholdSigned = computed(() => withholdStatus.value === 'SIGNED');
const withholdSignableOrderStatuses = ['APPROVED', 'PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED'];
const paidOrderStatuses = ['PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'];
const hasUnpaidInstallmentBill = computed(() => Number(installmentBillDetail.value.plan?.unpaidAmount || 0) > 0);
const canStartWithholdSign = computed(
  () =>
    withholdSignEntryEnabled
    && withholdSignableOrderStatuses.includes(String(currentObj.value?.alipayStatus || ''))
    && hasUnpaidInstallmentBill.value
    && !withholdSigned.value,
);
const withholdActionText = computed(() => (withholdSigned.value ? '已签约' : '代扣暂未启用'));
const withholdActionHint = computed(() => {
  if (withholdSigned.value) return '用户已完成自动扣款签约，后续账单可按计划扣款。';
  if (!withholdSignEntryEnabled) return '自动代扣签约入口暂未启用；请让用户通过小程序分期账单逐期主动支付。';
  if (currentObj.value?.alipayStatus === 'CREATED') return '用户完成订单签约、商家审核通过后，再生成自动代扣签约链接。';
  if (!withholdSignableOrderStatuses.includes(String(currentObj.value?.alipayStatus || ''))) {
    return '当前订单暂不支持发起代扣签约，请先确认订单状态。';
  }
  if (!hasUnpaidInstallmentBill.value) return '当前订单没有待收账单，不需要发起自动扣款签约。';
  return '将生成支付宝代扣签约入口，需要用户在支付宝侧完成授权。';
});

const rentComDeliverForm = reactive({
  courCode: '',
  courno: '',
  courName: '',
  status: '',
});

const rentOrderFinishForm = reactive({
  finishType: 'USER_RETURNED',
});

const depositDeductForm = reactive({
  feeType: 'INDEMNITY',
  reasonCode: 'ITEM_DAMAGED',
  deductAmount: '',
  remark: '',
});

const reasonCodeOptions = computed(() => reasonCodeOptionsMap[depositDeductForm.feeType] || []);

// 保留 main 分支旧订单页的数据形态：父行展示汇总，明细区默认展开同一条订单。
const orderRows = computed<AnyRecord[]>(() => orderList.value.map((item) => ({ ...item, detail: [{ ...item }] })));

type OrderFieldArea = 'amount' | 'summary' | 'order' | 'user' | 'identity' | 'fulfillment';

type OrderCardField = {
  key: string;
  label: string;
  groupTitle: string;
  area: OrderFieldArea;
  block?: boolean;
  wrap?: boolean;
  defaultVisible?: boolean;
  value: (row: AnyRecord) => string;
  valueClass?: (row: AnyRecord) => string;
};

type VisibleOrderFieldGroup = {
  key: OrderFieldArea;
  title: string;
  fields: OrderCardField[];
};

const orderFieldSettingStorageKey = displaySettingStorageKey('alipay.orders', 1);
const orderFieldGroupTitleMap: Record<OrderFieldArea, string> = {
  amount: '金额',
  summary: '概要',
  order: '订单信息',
  user: '用户信息',
  identity: '身份信息',
  fulfillment: '履约备注',
};
const orderCardGroupAreas: OrderFieldArea[] = ['order', 'user', 'identity', 'fulfillment'];
const orderFieldDefinitions: OrderCardField[] = [
  { key: 'orderTotal', label: '订单总额', groupTitle: '金额', area: 'amount', value: (row) => `￥${fenToYuan(row.orderTotal)}`, valueClass: () => 'amount-highlight--income' },
  { key: 'orderDeposit', label: '商品押金', groupTitle: '金额', area: 'amount', value: (row) => `￥${fenToYuan(row.orderDeposit)}`, valueClass: () => 'amount-highlight--deposit' },
  { key: 'recordId', label: 'ID', groupTitle: '概要', area: 'summary', value: (row) => orderRecordId(row) },
  { key: 'source', label: '来源', groupTitle: '概要', area: 'summary', value: (row) => orderSourceText(row) },
  { key: 'createdAt', label: '创建', groupTitle: '概要', area: 'summary', value: (row) => orderCreateTime(row) },
  { key: 'rentPeriod', label: '租期', groupTitle: '概要', area: 'summary', value: (row) => `${dealTime(row, 'orderStart')} - ${dealTime(row, 'orderEnd')}` },
  { key: 'rentOrderId', label: '支付宝租赁单号', groupTitle: '概要', area: 'summary', value: (row) => alipayRentOrderNo(row) },
  { key: 'paymentTradeNo', label: '租金交易号', groupTitle: '概要', area: 'summary', defaultVisible: false, value: (row) => paymentTradeNo(row) },
  { key: 'orderAuthNo', label: '授权号', groupTitle: '概要', area: 'summary', defaultVisible: false, value: (row) => orderAuthNo(row) },
  { key: 'aplyOrderNo', label: '支付宝订单号', groupTitle: '概要', area: 'summary', defaultVisible: false, value: (row) => alipayOrderNo(row) },
  { key: 'deviceName', label: '设备名称', groupTitle: '订单信息', area: 'order', value: (row) => row.goodTitle || '-' },
  { key: 'duration', label: '使用时长', groupTitle: '订单信息', area: 'order', value: (row) => `${row.orderKeep || 0} 天` },
  { key: 'deliverDate', label: '寄出日期', groupTitle: '履约备注', area: 'fulfillment', value: (row) => orderDeliverDate(row), valueClass: (row) => deliveryToneClass(row) },
  { key: 'userId', label: '用户ID', groupTitle: '用户信息', area: 'user', wrap: true, value: (row) => row.userUuid || '-' },
  { key: 'contactPhone', label: '电话', groupTitle: '用户信息', area: 'user', value: (row) => orderPhone(row) },
  { key: 'alipayAccountPhone', label: '支付宝账号', groupTitle: '用户信息', area: 'user', value: (row) => alipayAccountPhone(row) },
  { key: 'address', label: '地址', groupTitle: '用户信息', area: 'user', block: true, value: (row) => orderAddress(row) },
  { key: 'realName', label: '实名信息', groupTitle: '身份信息', area: 'identity', value: (row) => realNameInfo(row), valueClass: (row) => realNameToneClass(row) },
  { key: 'idCardPhoto', label: '身份证照片', groupTitle: '身份信息', area: 'identity', value: (row) => idCardPhotoStatus(row), valueClass: (row) => idCardPhotoToneClass(row) },
  { key: 'remark', label: '备注信息', groupTitle: '履约备注', area: 'fulfillment', block: true, value: (row) => orderRemarkText(row), valueClass: (row) => remarkToneClass(row) },
];
const defaultOrderFieldKeys = orderFieldDefinitions.filter((field) => field.defaultVisible !== false).map((field) => field.key);
const visibleOrderFieldKeys = ref<string[]>([...defaultOrderFieldKeys]);
const fieldsByKeys = <T extends { key: string }>(keys: string[], fields: T[]) => {
  const fieldMap = new Map(fields.map((field) => [field.key, field]));
  return keys.map((key) => fieldMap.get(key)).filter(Boolean) as T[];
};
const visibleOrderFields = computed(() => fieldsByKeys(visibleOrderFieldKeys.value, orderFieldDefinitions));
const visibleOrderAmountFields = computed(() => visibleOrderFields.value.filter((field) => field.area === 'amount'));
const visibleOrderSummaryFields = computed(() => visibleOrderFields.value.filter((field) => field.area === 'summary'));
const visibleOrderCardGroups = computed<VisibleOrderFieldGroup[]>(() => orderCardGroupAreas
  .map((area) => ({
    key: area,
    title: orderFieldGroupTitleMap[area],
    fields: visibleOrderFields.value.filter((field) => field.area === area),
  }))
  .filter((group) => group.fields.length));
const drawerOverviewGroups = computed<VisibleOrderFieldGroup[]>(() => orderCardGroupAreas
  .map((area) => ({
    key: area,
    title: orderFieldGroupTitleMap[area],
    fields: orderFieldDefinitions.filter((field) => field.area === area),
  }))
  .filter((group) => group.fields.length));
const drawerSummaryCards = computed(() => [
  { key: 'orderTotal', label: '订单总额', value: `￥${fenToYuan(currentObj.value.orderTotal)}`, className: 'amount-highlight--income' },
  { key: 'orderDeposit', label: '商品押金', value: `￥${fenToYuan(currentObj.value.orderDeposit)}`, className: 'amount-highlight--deposit' },
  { key: 'rentPeriod', label: '租期', value: `${dealTime(currentObj.value, 'orderStart')} - ${dealTime(currentObj.value, 'orderEnd')}`, className: '' },
  { key: 'source', label: '来源', value: orderSourceText(currentObj.value), className: '' },
]);
const orderDrawerSections: Array<{
  key: OrderDrawerSectionKey;
  label: string;
  group: string;
  code?: string;
  defaultVisible?: boolean;
  locked?: boolean;
  visible?: (row: AnyRecord) => boolean;
}> = [
  { key: 'overview', label: '概览', group: '基础信息', locked: true },
  { key: 'order', label: '订单/押金', group: '订单资料', code: buttonCode('order', 'view') },
  { key: 'deduct', label: '扣减记录', group: '售后押金', code: buttonCode('order', 'view') },
  { key: 'fulfillment', label: '履约/寄回', group: '履约链路', code: buttonCode('order', 'view') },
  { key: 'ledger', label: '台账', group: '操作记录', code: buttonCode('ledger', 'view') },
  { key: 'risk', label: '风险', group: '审核资料', code: buttonCode('order', 'view'), visible: (row) => canViewRiskInfo(row) },
  { key: 'contract', label: '协议', group: '订单资料', code: buttonCode('order', 'view') },
  { key: 'billing', label: '账单/代扣', group: '订单资料', code: buttonCode('order', 'view') },
];
const defaultOrderDrawerSectionKeys = orderDrawerSections
  .filter((section) => section.locked || section.defaultVisible !== false)
  .map((section) => section.key);
const visibleOrderDrawerSectionKeys = ref<OrderDrawerSectionKey[]>([...defaultOrderDrawerSectionKeys]);
const orderDrawerSectionDisplayKeys = computed(() => normalizeOrderDrawerSectionKeys(visibleOrderDrawerSectionKeys.value));

function availableOrderDrawerSections(row: AnyRecord) {
  return orderDrawerSections
    .filter((section) => !section.code || permissionStore.hasButton(section.code))
    .filter((section) => !section.visible || section.visible(row));
}

const visibleOrderDrawerSections = computed(() => {
  const selectedKeys = new Set(orderDrawerSectionDisplayKeys.value);
  return orderDisplayItems(orderDrawerSectionDisplayKeys.value, availableOrderDrawerSections(currentObj.value))
    .filter((section) => selectedKeys.has(section.key));
});

const hasReturnRecord = computed(() => Boolean(returnRecordData.value?.id || returnRecordData.value?.orderId || returnRecordData.value?.expressNo));
const returnPhotoList = computed(() => {
  const list = Array.isArray(returnRecordData.value?.photoUrlList) ? returnRecordData.value.photoUrlList : [];
  return list.filter(Boolean);
});
const priceInfoList = computed(() => objectToLabelValueList(depositData.value?.priceInfo, {
  orderPrice: '订单总金额（元）',
  depositPrice: '商品押金（元）',
  freight: '运费（元）',
  additionalPrice: '附加费用（元）',
  buyoutPrice: '买断金（元）',
  totalRent: '总租金（元）',
  total_amount: '总金额（元）',
  deposit_price: '押金（元）',
  total_rent: '总租金（元）',
  additional_price: '附加费（元）',
}));
const fundAuthDetailList = computed(() => objectToLabelValueList(depositData.value?.fundAuthDetail, {
  authNo: '支付宝授权号',
  operationType: '操作类型',
  operationId: '操作流水号',
  amount: '操作金额（元）',
  creditAmount: '信用金额（元）',
  fundAmount: '资金金额（元）',
  totalFreezeAmount: '累计冻结（元）',
  totalPayAmount: '累计支付（元）',
  restAmount: '剩余冻结（元）',
  payerLogonId: '付款人',
  gmtCreate: '创建时间',
  gmtTrans: '账务处理时间',
  preAuthType: '预授权类型',
}));
const depositSummaryHint = computed(() => {
  if (depositData.value.success === false) return '查询失败，请检查订单号或支付宝返回信息';
  const orderDeposit = Number(depositData.value.orderDeposit || 0);
  const remainingDeposit = Number(depositData.value.remainingDeposit || 0);
  if (!orderDeposit) return '当前订单没有可识别的押金金额';
  if (remainingDeposit < orderDeposit) return '剩余押金已发生变化，请结合扣除记录查看是否已经完成赔付扣款';
  return '剩余押金未发生变化，如需确认售后扣款结果，请查看扣除记录';
});
const routeOrderNo = computed(() => String(route.query.searchOrderNo || route.query.orderNo || ''));
const operLogPagination = computed(() => ({
  current: operLogPage.current,
  pageSize: operLogPage.pageSize,
  total: operLogPage.total,
  pageSizeOptions: [5, 10, 20, 50],
}));
const deductRecordPagination = computed(() => ({
  current: deductRecordPage.current,
  pageSize: deductRecordPage.pageSize,
  total: deductRecordPage.total,
  pageSizeOptions: [5, 10, 20, 50],
}));
const rentStatementTimelineItems = computed(() => buildStatementTimelineItems(rentOrderDetail.value.rentStatementInfos || [], 'rent'));
const depositStatementTimelineItems = computed(() => buildStatementTimelineItems(depositData.value.rentStatementInfos || [], 'deposit'));
const combinedStatementTimelineItems = computed(() =>
  rentStatementTimelineItems.value.length ? rentStatementTimelineItems.value : depositStatementTimelineItems.value);
const fundAuthTimelineItems = computed<DetailTimelineItem[]>(() => {
  if (!fundAuthDetailList.value.length) return [];
  const detail = depositData.value?.fundAuthDetail || {};
  return [{
    key: 'fund-auth',
    label: timelineDateLabel(detail.gmtTrans || detail.gmtCreate || depositData.value.createTime),
    title: '资金授权详情',
    dotColor: 'primary',
    tagTheme: 'primary',
    fields: fundAuthDetailList.value.map((item) => ({ label: item.label, value: item.value })),
  }];
});
const deductRecordTimelineItems = computed(() => deductRecords.value
  .slice()
  .sort((a, b) => compareTimelineValue(b.createTime, a.createTime))
  .map((row, index) => ({
    key: String(row.id || row.aftersaleNo || index),
    label: timelineDateLabel(row.createTime),
    title: `${feeTypeText(row.feeType)} / ${fenToYuan(row.deductAmount)} 元`,
    status: deductResultText(row),
    dotColor: recordStatusDotColor(row.status),
    tagTheme: deductResultTheme(row),
    fields: compactFields([
      ['原因码', row.reasonCode],
      ['本地状态', row.status],
      ['售后状态', row.aftersaleStatus],
      ['待商家操作', needOperationText(row.needOperation)],
      ['最近动作', buildDeductActionText(row.lastOperationType)],
      ['支付宝售后单号', row.aftersaleNo],
      ['商户售后单号', row.outAftersaleId],
      ['扣前剩余押金', `${fenToYuan(row.beforeRemainingDeposit)} 元`],
      ['扣后剩余押金', nullableFenToYuan(row.afterRemainingDeposit)],
      ['支付宝交易号', row.tradeNo],
      ['错误码', row.alipaySubCode],
      ['错误提示', deductErrorBrief(row)],
    ]),
    row,
  })));
const fulfillmentTimelineItems = computed(() => buildFulfillmentTimelineItems(
  currentObj.value,
  returnRecordData.value,
  installmentBillDetail.value?.plan || {},
));
const operLogTimelineItems = computed<DetailTimelineItem[]>(() => operLogRecords.value.map((row, index) => {
  const requestText = formatJsonPayload(row.requestData);
  const responseText = formatJsonPayload(row.responseData ?? row.resultBody ?? row.responseCode);
  const errorText = row.errorMsg ? String(row.errorMsg) : '';
  return {
    key: String(row.logId || index),
    label: timelineDateLabel(row.operTime),
    title: formatOperType(row.operType),
    status: formatOperResult(row.operResult),
    description: row.operDesc || '',
    dotColor: row.operResult === 'SUCCESS' ? 'success' : 'error',
    tagTheme: row.operResult === 'SUCCESS' ? 'success' : 'danger',
    fields: compactFields([
      ['来源', formatOperSource(row.operSource)],
      ['操作前', formatOrderStatus(row.statusBefore)],
      ['操作后', formatOrderStatus(row.statusAfter)],
      ['订单号', row.orderNo],
    ]),
    row,
    requestText,
    responseText,
    errorText,
    payloadSummary: buildOperLogPayloadSummary(requestText, responseText, errorText),
  };
}));

const rentStatementColumns = [
  { title: '分期号', colKey: 'installmentNo', width: 90, align: 'center' },
  { title: '状态', colKey: 'statementStatus', width: 120, align: 'center' },
  { title: '账单金额', colKey: 'amount', width: 120, align: 'right' },
  { title: '已付金额', colKey: 'paidAmount', width: 120, align: 'right' },
  { title: '类型', colKey: 'statementType', width: 120, align: 'center' },
];
const installmentBillColumns = [
  { title: '期数', colKey: 'periodNo', width: 80, align: 'center' },
  { title: '账单号', colKey: 'billNo', minWidth: 160, ellipsis: true },
  { title: '金额', colKey: 'amount', width: 120, align: 'right' },
  { title: '已付', colKey: 'paidAmount', width: 120, align: 'right' },
  { title: '到期日', colKey: 'dueDate', width: 170 },
  { title: '支付时间', colKey: 'paidAt', width: 170 },
  { title: '状态', colKey: 'status', width: 120, align: 'center' },
];
const rentItemColumns = [
  { title: '商品名称', colKey: 'itemName', minWidth: 160, ellipsis: true },
  { title: '商品数量', colKey: 'itemCnt', width: 100 },
  { title: '商品价值', colKey: 'itemValue', width: 120 },
  { title: '商品成色', colKey: 'itemFineness', width: 120 },
  { title: '成色等级', colKey: 'itemFinenessGrade', width: 120 },
  { title: '销售价格', colKey: 'salePrice', width: 120 },
  { title: '商品ID', colKey: 'outItemId', minWidth: 160, ellipsis: true },
  { title: 'SKU ID', colKey: 'outSkuId', minWidth: 160, ellipsis: true },
];
const operLogColumns = [
  { title: '日志ID', colKey: 'logId', width: 90 },
  { title: '订单号', colKey: 'orderNo', minWidth: 180, ellipsis: true },
  { title: '操作类型', colKey: 'operType', width: 130 },
  { title: '操作描述', colKey: 'operDesc', minWidth: 180, ellipsis: true },
  { title: '来源', colKey: 'operSource', width: 110 },
  { title: '结果', colKey: 'operResult', width: 110 },
  { title: '操作前', colKey: 'statusBefore', width: 130 },
  { title: '操作后', colKey: 'statusAfter', width: 130 },
  { title: '操作时间', colKey: 'operTime', width: 170 },
  { title: '错误信息', colKey: 'errorMsg', minWidth: 180, ellipsis: true },
];
const depositStatementColumns = [
  { title: '分期号', colKey: 'installmentNo', width: 80, align: 'center' },
  { title: '状态', colKey: 'statementStatus', width: 100, align: 'center' },
  { title: '账单金额', colKey: 'amount', minWidth: 100, align: 'right' },
  { title: '已付金额', colKey: 'paidAmount', minWidth: 100, align: 'right' },
  { title: '类型', colKey: 'statementType', width: 100, align: 'center' },
  { title: '计划支付时间', colKey: 'planPayTime', minWidth: 160, align: 'center' },
];
const deductRecordColumns = [
  { title: '创建时间', colKey: 'createTime', minWidth: 160, align: 'center' },
  { title: '费用类型', colKey: 'feeType', width: 110, align: 'center' },
  { title: '原因码', colKey: 'reasonCode', minWidth: 150, align: 'center' },
  { title: '扣减金额', colKey: 'deductAmount', width: 120, align: 'right' },
  { title: '本地状态', colKey: 'status', width: 110, align: 'center' },
  { title: '售后状态', colKey: 'aftersaleStatus', width: 110, align: 'center' },
  { title: '扣款结果', colKey: 'deductResult', minWidth: 150, align: 'center' },
  { title: '待商家操作', colKey: 'needOperation', width: 110, align: 'center' },
  { title: '最近动作', colKey: 'lastOperationType', minWidth: 150, align: 'center' },
  { title: '支付宝售后单号', colKey: 'aftersaleNo', minWidth: 180 },
  { title: '商户售后单号', colKey: 'outAftersaleId', minWidth: 180 },
  { title: '扣前剩余押金', colKey: 'beforeRemainingDeposit', width: 130, align: 'right' },
  { title: '扣后剩余押金', colKey: 'afterRemainingDeposit', width: 130, align: 'right' },
  { title: '赔付支付请求号', colKey: 'outTradeNo', minWidth: 180 },
  { title: '支付宝交易号', colKey: 'tradeNo', minWidth: 180 },
  { title: '最近通知ID', colKey: 'lastNotifyId', minWidth: 160 },
  { title: '处理提示', colKey: 'alipaySubMsg', width: 240 },
  { title: '操作', colKey: 'recordOperation', width: 340, fixed: 'right', align: 'center' },
];
function formatDateOnly(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getTimeByDay(offset = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function buildListParams(extra: AnyRecord = {}) {
  const params: AnyRecord = {
    page: tableParams.page,
    limit: tableParams.limit,
    alipayStatus: tableParams.alipayStatus,
    pickupWay: '',
    start: tableParams.start,
    end: tableParams.end,
    ...extra,
  };
  if (tableParams.type && tableParams.userName) {
    params[tableParams.type] = tableParams.userName;
  }
  if (params.alipayStatus === -1) delete params.alipayStatus;
  return cleanQuery(params);
}

async function getListOrder(extra: AnyRecord = {}, options: ListLoadOptions = {}) {
  const requestedPage = tableParams.page;
  const requestedLimit = tableParams.limit;
  if (!options.silent) loading.value = true;
  try {
    const response = await rentApi.rentOrderList(buildListParams(extra));
    const page = resolvePage<AnyRecord>(response);
    orderList.value = page.list;
    pageTotal.value = page.total;
    tableParams.page = requestedPage;
    tableParams.limit = requestedLimit;
  } finally {
    if (!options.silent) loading.value = false;
  }
}

function search() {
  tableParams.page = 1;
  return getListOrder();
}

const { pauseAutoQuery } = useAutoQuery(
  () => ({
    chooseTime: tableParams.chooseTime,
    chooseDate: Array.isArray(tableParams.chooseDate) ? [...tableParams.chooseDate] : tableParams.chooseDate,
    userName: tableParams.userName,
    alipayStatus: tableParams.alipayStatus,
    start: tableParams.start,
    end: tableParams.end,
  }),
  search,
);

function resetSearchFilters() {
  pauseAutoQuery(() => {
    tableParams.type = '';
    tableParams.chooseTime = '';
    tableParams.chooseDate = [];
    tableParams.userName = '';
    tableParams.alipayStatus = '';
    tableParams.start = '';
    tableParams.end = '';
    return search();
  });
}

function changeType() {
  if (!tableParams.type) {
    tableParams.userName = '';
    return;
  }
  tableParams.chooseTime = '';
  tableParams.chooseDate = [];
  tableParams.start = '';
  tableParams.end = '';
}

function changeTime() {
  tableParams.page = 1;
  if (!tableParams.chooseTime) {
    tableParams.chooseDate = [];
    tableParams.start = '';
    tableParams.end = '';
  } else {
    const value = Number(tableParams.chooseTime) - 1;
    tableParams.start = getTimeByDay(-value);
    tableParams.end = getTimeByDay();
    tableParams.chooseDate = [];
  }
}

function handleSearchDate(value: Array<string | number>) {
  tableParams.type = '';
  tableParams.userName = '';
  tableParams.chooseTime = '';
  if (value && value.length === 2) {
    tableParams.start = Number(value[0]);
    tableParams.end = Number(value[1]);
  } else {
    tableParams.start = '';
    tableParams.end = '';
  }
}

function handlePageChange(pageInfo: PageInfo) {
  tableParams.page = pageInfo.current;
  tableParams.limit = pageInfo.pageSize;
  getListOrder();
}

function dealStatus(row: AnyRecord) {
  return statusMap[String(row.alipayStatus)] || row.statusName || formatOrderStatus(row.alipayStatus);
}

function statusTheme(row: AnyRecord) {
  return successStatuses.includes(row.alipayStatus) ? 'success' : 'danger';
}

function esignContractStatusTheme(status?: string) {
  if (status === 'COMPLETED' || status === 'SIGNED') return 'success';
  if (status === 'SIGNING' || status === 'INIT') return 'warning';
  if (status === 'FAILED' || status === 'EXPIRED' || status === 'VOIDED') return 'danger';
  return 'default';
}

function resolveContractPayload(payload: AnyRecord) {
  const data = payload?.data?.data ?? payload?.data ?? payload ?? {};
  return data && typeof data === 'object' ? data : {};
}

function isEsignEnabled(payload: AnyRecord) {
  const data = resolveContractPayload(payload);
  if (!data || data.enabled === false || data.mode === 'pdf') return false;
  return data.enabled === true || data.mode === 'esign' || !!data.contract;
}

function contractPanelTitle(payload: AnyRecord) {
  return isEsignEnabled(payload) ? '电子合同' : '租赁协议';
}

function contractPanelDescription(payload: AnyRecord) {
  return isEsignEnabled(payload) ? '查看签署流程、签署入口和已生成协议。' : '当前使用系统默认 PDF 协议。';
}

function isEsignCompleted(payload: AnyRecord) {
  const status = resolveContractPayload(payload)?.contract?.status;
  return status === 'COMPLETED' || status === 'SIGNED';
}

function billStatusTheme(status?: string) {
  if (status === 'PAID') return 'success';
  if (status === 'WAIT_PAY' || status === 'PAYING') return 'warning';
  if (status === 'OVERDUE') return 'danger';
  return 'default';
}

function withholdStatusTheme(status?: string) {
  if (status === 'SIGNED') return 'success';
  if (status === 'SIGNING') return 'warning';
  if (status === 'FAILED' || status === 'CLOSED') return 'danger';
  return 'default';
}

function esignContractSignUrl(payload: AnyRecord) {
  const data = resolveContractPayload(payload);
  const contract = data?.contract || {};
  return compactText(data?.signUrl) || compactText(contract.signUrl);
}

function esignSignedFileUrl(payload: AnyRecord) {
  const data = resolveContractPayload(payload);
  const contract = data?.contract || {};
  if (!isEsignCompleted(data)) return '';
  return compactText(contract.downloadUrl) || compactText(contract.viewUrl) || compactText(contract.pdfUrl);
}

function openExternalUrl(url: string) {
  if (!url) {
    MessagePlugin.warning('暂无可打开的入口');
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}

function formatDate(value: unknown) {
  return formatDateTime(value);
}

function formatBillingDateTime(value: unknown) {
  const text = formatDateTime(value);
  return text === '-' ? '-' : text.replace(/:\d{2}$/, '');
}

function formatTimestamp(value: unknown) {
  if (!value) return '-';
  const number = Number(value);
  return formatDate(Number.isFinite(number) ? number : value);
}

function isMeaningful(value: unknown) {
  return value !== null && value !== undefined && value !== '' && value !== '-';
}

function compactFields(fields: Array<[string, unknown]>): DetailTimelineField[] {
  return fields
    .filter(([, value]) => isMeaningful(value))
    .map(([label, value]) => ({ label, value: String(value) }));
}

function compactText(value: unknown) {
  return isMeaningful(value) ? String(value).trim() : '';
}

function includesAny(text: string, keywords: string[]) {
  const normalized = text.toUpperCase();
  return keywords.some((keyword) => normalized.includes(keyword.toUpperCase()));
}

function limitText(text: string, maxLength = 34) {
  if (text.length <= maxLength) return text;
  return `${text.slice(0, maxLength)}...`;
}

function deductErrorFullText(row: AnyRecord) {
  const message = compactText(row?.alipaySubMsg);
  const code = compactText(row?.alipaySubCode);
  if (message && code && !message.includes(code)) return `${message} 错误码：${code}`;
  return message || code;
}

function deductErrorBrief(row: AnyRecord) {
  const text = deductErrorFullText(row);
  if (!text) return '';
  if (includesAny(text, ['NO_PAYMENT_INSTRUMENTS_AVAILABLE', '没有可用的支付工具', '没有可用支付工具', '无可用支付工具'])) {
    return '支付工具不可用，联系用户补卡/充值';
  }
  if (includesAny(text, ['BUYER_BALANCE_NOT_ENOUGH', 'BUYER_BANKCARD_BALANCE_NOT_ENOUGH', '余额不足', '银行卡余额不足'])) {
    return '余额不足，联系用户充值或换卡';
  }
  if (includesAny(text, ['AUTH_AMOUNT_NOT_ENOUGH', '授权资金不足', '预授权金额不足', '可用授权金额不足'])) {
    return '预授权金额不足，改主动赔付或线下处理';
  }
  if (includesAny(text, ['AUTH_ORDER_HAS_CLOSED', 'AUTH_ORDER_CLOSED', '授权订单已关闭', '预授权已关闭', '预授权已解冻', '冻结已解冻'])) {
    return '预授权已关闭，改主动赔付或线下处理';
  }
  if (includesAny(text, ['OUT_TRADE_NO', '外部交易号已存在', '重复提交', 'DUPLICATE'])) {
    return '支付请求号重复，刷新后重试';
  }
  if (includesAny(text, ['RISK', '风控', '账户限制', '支付受限'])) {
    return '支付宝风控/账户限制，联系用户处理';
  }
  return limitText(text);
}

function formatJsonPayload(value: unknown) {
  if (!isMeaningful(value)) return '';
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, 2);
  } catch {
    return String(value);
  }
}

function buildOperLogPayloadSummary(requestText: string, responseText: string, errorText: string) {
  const parts = [];
  if (requestText) parts.push('请求参数');
  if (responseText) parts.push('返回数据');
  if (errorText) parts.push('错误信息');
  return parts.join(' / ') || '暂无明细';
}

function firstMeaningful(source: AnyRecord, keys: string[]) {
  for (const key of keys) {
    if (isMeaningful(source?.[key])) return source[key];
  }
  return '';
}

function timelineDateLabel(value: unknown) {
  const formatted = formatTimestamp(value);
  return formatted === '-' ? '时间未记录' : formatted;
}

function timelineTimeValue(value: unknown) {
  if (!isMeaningful(value)) return 0;
  const number = Number(value);
  if (Number.isFinite(number) && number > 0) return number;
  const parsed = new Date(String(value).replace(/-/g, '/')).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function compareTimelineValue(a: unknown, b: unknown) {
  return timelineTimeValue(a) - timelineTimeValue(b);
}

function statementStatusText(status: string) {
  if (status === 'PAID') return '已支付';
  if (status === 'UNPAID') return '未支付';
  return status || '未支付';
}

function statementStatusTheme(status: string): TimelineTagTheme {
  if (status === 'PAID') return 'success';
  if (status === 'OVERDUE') return 'danger';
  return 'warning';
}

function statementDotColor(status: string): TimelineDotColor {
  if (status === 'PAID') return '#00a870';
  if (status === 'OVERDUE') return 'error';
  return 'warning';
}

function recordStatusDotColor(status: string): TimelineDotColor {
  if (status === 'SUCCESS') return '#00a870';
  if (status === 'FAILED') return 'error';
  if (status === 'CANCELLED') return 'default';
  return 'warning';
}

function buildStatementTimelineItems(records: AnyRecord[], prefix: string): DetailTimelineItem[] {
  return records
    .slice()
    .sort((a, b) => {
      const timeDiff = compareTimelineValue(a.planPayTime, b.planPayTime);
      if (timeDiff) return timeDiff;
      return Number(a.installmentNo || 0) - Number(b.installmentNo || 0);
    })
    .map((row, index) => {
      const status = String(row.statementStatus || '');
      return {
        key: `${prefix}-${row.installmentNo || index}`,
        label: statementTimeLabel(row),
        title: statementTitle(row, index),
        status: statementStatusText(status),
        dotColor: statementDotColor(status),
        tagTheme: statementStatusTheme(status),
        fields: compactFields([
          ['账单金额', row.amount],
          ['已付金额', row.paidAmount],
          ['账单类型', row.statementType],
        ]),
        row,
      };
    });
}

function statementTimeLabel(row: AnyRecord) {
  const time = firstMeaningful(row, ['payTime', 'paidTime', 'paymentTime', 'gmtPayment', 'gmtPay', 'planPayTime']);
  if (isMeaningful(time)) return timelineDateLabel(time);
  if (row.statementType === 'INDEMNITY') return '赔付时间未返回';
  if (row.statementStatus === 'PAID') return '支付时间未返回';
  return '计划时间未返回';
}

function statementTitle(row: AnyRecord, index: number) {
  if (row.statementType === 'INDEMNITY') return '赔付账单';
  if (row.statementType === 'LATE_FEE') return '违约金账单';
  if (row.statementType === 'BUYOUT') return '买断账单';
  return `第 ${row.installmentNo || index + 1} 期账单`;
}

function dealTime(row: AnyRecord, key: string) {
  const value = formatDateTime(row[key]);
  return value === '-' ? '-' : value.split(' ')[0];
}

function fenToYuan(value: unknown) {
  return formatMoney(value);
}

function nullableFenToYuan(value: unknown) {
  if (value === null || value === undefined || value === '') return '-';
  return `${fenToYuan(value)} 元`;
}

function intText(value: unknown) {
  return Math.trunc(Number(value || 0));
}

function centValue(value: unknown) {
  const numberValue = Number(value ?? 0);
  return Number.isFinite(numberValue) ? Math.trunc(numberValue) : 0;
}

function rentPaidAmount(row: AnyRecord, billingPlan: AnyRecord = {}) {
  const planPaidAmount = centValue(billingPlan.paidAmount);
  if (planPaidAmount > 0) return planPaidAmount;
  if (isMeaningful(billingPlan.paidAmount) && Number(billingPlan.periodTotal || 0) > 0) return 0;
  return centValue(firstMeaningful(row, [
    'orderFinalpay',
    'paidAmount',
    'paidRentAmount',
    'rentPaidAmount',
    'totalPaidCents',
  ]));
}

function rentPaidAmountText(row: AnyRecord, billingPlan: AnyRecord = {}) {
  const amount = rentPaidAmount(row, billingPlan);
  return amount > 0 ? `￥${fenToYuan(amount)}` : '';
}

function hasRentPaid(row: AnyRecord, paidAt?: unknown, billingPlan: AnyRecord = {}) {
  return rentPaidAmount(row, billingPlan) > 0 || isMeaningful(paidAt) || paidOrderStatuses.includes(row.alipayStatus);
}

function rentPaymentStatus(row: AnyRecord, paidAt?: unknown, billingPlan: AnyRecord = {}) {
  if (hasRentPaid(row, paidAt, billingPlan)) return '已支付';
  return paymentTradeNo(row) === '-' ? '待支付' : '交易号已生成';
}

function hasSource(row: AnyRecord) {
  return row.sourceId !== null && row.sourceId !== undefined;
}

function hasContractPdf(row: AnyRecord) {
  return typeof row.contractPdfUrl === 'string' && row.contractPdfUrl.trim().length > 0;
}

function canGenerateContractPdf(row: AnyRecord) {
  return contractReadyStatuses.includes(row.alipayStatus);
}

function generateContractPdfButtonText(row: AnyRecord) {
  return hasContractPdf(row) ? '重新生成PDF' : '补生成PDF';
}

function canSyncContractToAlipay(row: AnyRecord) {
  return hasContractPdf(row)
    && contractSyncReadyStatuses.includes(row.alipayStatus)
    && !(row.contractAlipaySyncStatus === 'SUCCESS' && row.contractAlipayFileId);
}

function contractAlipaySyncStatusText(row: AnyRecord) {
  if (row.contractAlipaySyncStatus === 'SUCCESS') {
    return '已回传';
  }
  if (row.contractAlipaySyncStatus === 'FAILED') {
    return row.contractAlipaySyncError ? `回传失败：${row.contractAlipaySyncError}` : '回传失败';
  }
  if (!hasContractPdf(row)) return '协议未生成';
  if (!contractSyncReadyStatuses.includes(row.alipayStatus)) return '确认收货后可回传';
  return '未回传';
}

function contractPdfPreviewUrl(row: AnyRecord) {
  if (!hasContractPdf(row)) return '';
  return pdfPreviewUrl(row.contractPdfUrl, true);
}

function contractPreviewSource(payload: AnyRecord, row: AnyRecord) {
  const signedUrl = esignSignedFileUrl(payload);
  if (signedUrl) {
    return { url: signedContractPreviewUrl.value, signed: true };
  }
  if (hasContractPdf(row)) {
    return { url: row.contractPdfUrl, signed: false };
  }
  return { url: '', signed: false };
}

function contractPreviewUrl(payload: AnyRecord, row: AnyRecord) {
  const source = contractPreviewSource(payload, row);
  if (!source.url) return '';
  return pdfPreviewUrl(source.url, !source.signed);
}

function contractPreviewTitle(payload: AnyRecord) {
  return esignSignedFileUrl(payload) ? '签署文件预览' : '协议预览';
}

function contractPreviewHint(payload: AnyRecord) {
  return esignSignedFileUrl(payload) ? '已签署文件来自 e签宝' : '如未刷新，请重新生成 PDF';
}

function pdfPreviewUrl(url: string, withCacheBust: boolean) {
  const [base, hash] = String(url || '').split('#', 2);
  let next = base;
  if (withCacheBust) {
    const separator = next.includes('?') ? '&' : '?';
    next = `${next}${separator}_preview=${contractPdfPreviewVersion.value}`;
  }
  return `${next}#${hash || 'toolbar=1&navpanes=0'}`;
}

function safeJsonParse<T>(value: unknown, fallback: T): T {
  if (!value) return fallback;
  if (typeof value === 'object') return value as T;
  try {
    return JSON.parse(String(value)) as T;
  } catch {
    return fallback;
  }
}

function latestReturn(row: AnyRecord) {
  return safeJsonParse<AnyRecord>(row.latestReturnRecord, row.latestReturnRecord || {});
}

function avatarObjectJson(data: unknown) {
  return typeof data === 'string' && data.trim().startsWith('{') && data.trim().endsWith('}');
}

function avatarObject(data: unknown) {
  return safeJsonParse<AnyRecord | null>(data, null);
}

function identityName(row: AnyRecord) {
  if (avatarObjectJson(row.avatar)) return avatarObject(row.avatar)?.data?.name?.data || '无';
  return row.avatar || '无';
}

function identityNo(row: AnyRecord) {
  if (avatarObjectJson(row.avatar)) return avatarObject(row.avatar)?.data?.num?.data || '无';
  return row.em || '无';
}

function realNameInfo(row: AnyRecord) {
  const name = row.accountRealName || identityName(row);
  const idCard = row.accountIdCard || identityNo(row);
  if ((name && name !== '无') || (idCard && idCard !== '无')) {
    return `${name && name !== '无' ? name : '-'} / ${idCard && idCard !== '无' ? idCard : '-'}`;
  }
  return '-';
}

function idCardPhotoList(row: AnyRecord) {
  const urls = Array.isArray(row.idCardPhotoUrls) ? row.idCardPhotoUrls.filter(Boolean) : [];
  return urls.map((url: string, index: number) => ({
    url,
    previewUrl: resolveUploadAssetPath(url),
    label: index === 0 ? '身份证正面' : index === 1 ? '身份证反面' : `照片${index + 1}`,
  }));
}

function identityPhotoReviewStatus(row: AnyRecord) {
  if (!row.idCardPhotoRequired) return 'NOT_REQUIRED';
  const status = String(row.idCardPhotoReviewStatus || '').trim().toUpperCase();
  if (status) return status;
  return idCardPhotoList(row).length >= 2 ? 'PENDING_REVIEW' : 'PENDING_UPLOAD';
}

function idCardPhotoStatus(row: AnyRecord) {
  const reviewStatus = identityPhotoReviewStatus(row);
  if (reviewStatus === 'APPROVED') return '身份证审核通过';
  if (reviewStatus === 'REJECTED') return `审核不通过${row.idCardPhotoReviewRemark ? `：${row.idCardPhotoReviewRemark}` : ''}`;
  if (reviewStatus === 'PENDING_REVIEW') return '已上传，待后台审核';
  const uploadedCount = idCardPhotoList(row).length;
  if (uploadedCount >= 2) return '已上传正反面';
  if (uploadedCount > 0) return `已上传 ${uploadedCount} 张，缺少${uploadedCount === 1 ? '反面' : '照片'}`;
  return row.idCardPhotoRequired ? '要求上传，用户未上传' : '无需上传';
}

function canReviewIdentityPhotos(row: AnyRecord) {
  return row.alipayStatus === 'SIGNED'
    && row.idCardPhotoRequired
    && identityPhotoReviewStatus(row) === 'PENDING_REVIEW'
    && idCardPhotoList(row).length >= 2;
}

function previewIdentityPhoto(url: string) {
  identityPhotoPreviewUrl.value = url;
  identityPhotoPreviewVisible.value = true;
}

function canDirectRefund(row: AnyRecord) {
  return ['PAID', 'APPROVED', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED'].includes(row.alipayStatus);
}

function canViewRiskInfo(row?: AnyRecord | null) {
  return Boolean(row?.orderId || row?.orderNo || row?.id);
}

function canOperateRiskReview(row?: AnyRecord | null) {
  return Boolean(row && row.alipayStatus === 'SIGNED' && !row.idCardPhotoRequired);
}

function addTimelineNode(items: DetailTimelineItem[], node: {
  key: string;
  date?: unknown;
  fallbackLabel?: string;
  title: string;
  status?: string;
  description?: string;
  dotColor?: TimelineDotColor;
  tagTheme?: TimelineTagTheme;
  fields?: DetailTimelineField[];
}) {
  const label = node.date ? timelineDateLabel(node.date) : node.fallbackLabel || '时间未记录';
  items.push({
    key: node.key,
    label,
    title: node.title,
    status: node.status,
    description: node.description,
    dotColor: node.dotColor || 'primary',
    tagTheme: node.tagTheme || 'primary',
    fields: node.fields || [],
  });
}

function buildFulfillmentTimelineItems(row: AnyRecord, returnRecord: AnyRecord, billingPlan: AnyRecord = {}): DetailTimelineItem[] {
  const items: DetailTimelineItem[] = [];
  addTimelineNode(items, {
    key: 'created',
    date: row.createtime || row.createTime,
    title: '用户下单',
    status: dealStatus(row),
    tagTheme: statusTheme(row),
    dotColor: 'primary',
    fields: compactFields([
      ['订单号', row.orderNo],
      ['租期', `${dealTime(row, 'orderStart')} - ${dealTime(row, 'orderEnd')}`],
      ['来源', orderSourceText(row)],
    ]),
  });
  const signedAt = firstMeaningful(row, ['contractAgreedAt', 'signedTime', 'signTime', 'orderSignTime', 'authTime']);
  if (signedAt || ['SIGNED', 'APPROVED', 'PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'signed',
      date: signedAt,
      fallbackLabel: signedAt ? undefined : '签约时间未记录',
      title: '用户签约',
      status: '已签约',
      dotColor: '#00a870',
      tagTheme: 'success',
      fields: compactFields([
        ['支付宝租赁单号', alipayRentOrderNo(row)],
        ['授权号', orderAuthNo(row)],
      ]),
    });
  }
  const paidAt = firstMeaningful(row, ['orderLastpay', 'lastPaidAt', 'paymentTime', 'payTime', 'paidTime', 'gmtPayment', 'gmtPay']);
  if (paidAt || ['PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'paid',
      date: paidAt,
      fallbackLabel: paidAt ? undefined : '支付时间未记录',
      title: '租金支付',
      status: rentPaymentStatus(row, paidAt, billingPlan),
      dotColor: hasRentPaid(row, paidAt, billingPlan) ? '#00a870' : 'warning',
      tagTheme: hasRentPaid(row, paidAt, billingPlan) ? 'success' : 'warning',
      fields: compactFields([
        ['租金交易号', paymentTradeNo(row)],
        ['已付租金', rentPaidAmountText(row, billingPlan)],
      ]),
    });
  }
  const deliveredAt = firstMeaningful(row, ['deliveryTime', 'deliverTime', 'orderDeliverTime', 'sendTime']);
  if (deliveredAt || row.courno || ['DELIVERED', 'RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'deliver',
      date: deliveredAt,
      fallbackLabel: deliveredAt ? undefined : '发货时间未记录',
      title: '商家发货',
      status: row.courno ? '物流已提交' : '已发货',
      dotColor: '#00a870',
      tagTheme: 'success',
      fields: compactFields([
        ['快递公司', row.courName],
        ['快递单号', row.courno],
        ['运费', `￥${row.offlinePickup !== 0 ? fenToYuan(row.freight) : '0.00'}`],
      ]),
    });
  }
  const receivedAt = firstMeaningful(row, ['receiveTime', 'receivedTime', 'confirmReceiveTime']);
  if (receivedAt || ['RECEIVED', 'RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'receive',
      date: receivedAt,
      fallbackLabel: receivedAt ? undefined : '收货时间未记录',
      title: '用户确认收货',
      status: '已收货',
      dotColor: '#00a870',
      tagTheme: 'success',
    });
  }
  const latest = latestReturn(row);
  const returnSubmittedAt = returnRecord.submittedAt || latest.submittedAt || latest.createTime;
  if (returnSubmittedAt || returnRecord.expressNo || latest.expressNo || ['RETURN_DELIVERED', 'RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'return-send',
      date: returnSubmittedAt,
      fallbackLabel: returnSubmittedAt ? undefined : '寄回时间未记录',
      title: '用户寄回',
      status: returnStatusText(returnRecord.status || latest.status),
      dotColor: returnStatusTheme(returnRecord.status || latest.status) === 'danger' ? 'error' : '#00a870',
      tagTheme: returnStatusTheme(returnRecord.status || latest.status),
      description: returnRecord.detailRemark || latest.detailRemark || '',
      fields: compactFields([
        ['寄回方式', returnRecord.returnType || latest.returnType],
        ['快递公司', returnRecord.expressCompany || latest.expressCompany],
        ['快递单号', returnRecord.expressNo || latest.expressNo],
        ['失败原因', returnRecord.failReason || latest.failReason],
      ]),
    });
  }
  const returnReceivedAt = firstMeaningful(row, ['returnReceiveTime', 'returnReceivedTime']);
  if (returnReceivedAt || ['RETURN_RECEIVED', 'FINISHED'].includes(row.alipayStatus)) {
    addTimelineNode(items, {
      key: 'return-receive',
      date: returnReceivedAt,
      fallbackLabel: returnReceivedAt ? undefined : '签收时间未记录',
      title: '商家签收寄回',
      status: '已签收',
      dotColor: '#00a870',
      tagTheme: 'success',
    });
  }
  const finishedAt = firstMeaningful(row, ['finishTime', 'finishedTime', 'completeTime']);
  if (finishedAt || row.alipayStatus === 'FINISHED') {
    addTimelineNode(items, {
      key: 'finish',
      date: finishedAt,
      fallbackLabel: finishedAt ? undefined : '完结时间未记录',
      title: '订单完结',
      status: '已完结',
      dotColor: '#00a870',
      tagTheme: 'success',
    });
  }
  return items;
}

type RecordActionTheme = 'primary' | 'success' | 'warning' | 'danger' | 'default';
type RecordActionVariant = 'base' | 'outline' | 'dashed' | 'text';
type RecordActionGroupKey = 'flow' | 'after' | 'view';

type RecordActionItem = {
  key: string;
  label: string;
  code: string;
  theme?: RecordActionTheme;
  variant?: RecordActionVariant;
  group: RecordActionGroupKey;
  handler: () => void;
};

const recordActionSettingDefinitions: Array<Pick<RecordActionItem, 'key' | 'label' | 'group' | 'theme'>> = [
  { key: 'detail', label: '详情', group: 'view', theme: 'primary' },
  { key: 'close', label: '订单关闭', group: 'flow', theme: 'danger' },
  { key: 'deliver', label: '商品发货', group: 'flow', theme: 'danger' },
  { key: 'deliverByUser', label: '自提发货', group: 'flow', theme: 'danger' },
  { key: 'confirmReceive', label: '确认收货', group: 'flow', theme: 'danger' },
  { key: 'returnDeliver', label: '归还发货', group: 'flow', theme: 'danger' },
  { key: 'returnDeliverByUser', label: '线下归还', group: 'flow', theme: 'danger' },
  { key: 'returnReceive', label: '确认归还收货', group: 'flow', theme: 'danger' },
  { key: 'finish', label: '订单完结', group: 'flow', theme: 'danger' },
  { key: 'deduct', label: '扣除押金', group: 'after', theme: 'danger' },
  { key: 'refund', label: '退款', group: 'after', theme: 'danger' },
  { key: 'agreeRefund', label: '同意退款', group: 'after', theme: 'danger' },
  { key: 'rejectRefund', label: '拒绝退款', group: 'after', theme: 'danger' },
  { key: 'syncAftersales', label: '同步售后', group: 'after', theme: 'warning' },
  { key: 'generateContract', label: '生成协议PDF', group: 'view', theme: 'warning' },
  { key: 'syncContract', label: '回传协议', group: 'view', theme: 'success' },
  { key: 'identityApprove', label: '身份通过', group: 'view', theme: 'success' },
  { key: 'identityReject', label: '身份驳回', group: 'view', theme: 'danger' },
  { key: 'remark', label: '添加备注', group: 'view', theme: 'success' },
];
const defaultRecordActionKeys = recordActionSettingDefinitions.map((action) => action.key);
const visibleRecordActionKeys = ref<string[]>([...defaultRecordActionKeys]);
const recordActionDisplayKeys = computed(() => normalizeRecordActionKeys(visibleRecordActionKeys.value));
const recordActionGroupTitleMap: Record<RecordActionGroupKey, string> = {
  view: '查看与备注',
  flow: '履约流程',
  after: '售后押金',
};
const orderDisplaySettingGroups = computed<DisplaySettingGroup[]>(() => [
  {
    kind: 'modules',
    label: '详情抽屉',
    title: '大抽屉内容与顺序',
    description: '勾选进入订单详情大抽屉；未勾选的内容会放到订单卡片操作区，点击后打开只含该内容的右侧抽屉。',
    items: orderDrawerSections.map<DisplaySettingItem>((section) => ({
      key: section.key,
      label: section.label,
      group: section.group,
      defaultVisible: section.defaultVisible,
      locked: section.locked,
    })),
  },
  {
    kind: 'fields',
    label: '订单卡片',
    title: '订单卡片展示字段',
    description: '控制订单列表卡片中的字段显示和顺序，金额与核心订单字段默认展示。',
    items: orderFieldDefinitions.map<DisplaySettingItem>((field) => ({
      key: field.key,
      label: field.label,
      group: field.groupTitle,
      defaultVisible: field.defaultVisible,
    })),
  },
  {
    kind: 'actions',
    label: '操作按钮',
    title: '操作按钮显示顺序',
    description: '本地设置只控制已授权按钮的展示，权限系统仍优先生效。',
    items: recordActionSettingDefinitions.map<DisplaySettingItem>((action) => ({
      key: action.key,
      label: action.label,
      group: recordActionGroupTitleMap[action.group],
      defaultVisible: true,
    })),
  },
]);
const orderDisplaySettingState = computed<DisplaySettingState>(() => ({
  modules: visibleOrderDrawerSectionKeys.value,
  fields: visibleOrderFieldKeys.value,
  actions: visibleRecordActionKeys.value,
}));

function orderRecordId(row: AnyRecord) {
  return row.orderId || row.id || row.sourceId || '-';
}

function orderCustomerName(row: AnyRecord) {
  return row.accountRealName || row.userName || identityName(row) || '-';
}

function orderPhone(row: AnyRecord) {
  return row.userTel || row.tel || '-';
}

function alipayAccountPhone(row: AnyRecord) {
  return row.accountUserTel || '-';
}

function orderAddress(row: AnyRecord) {
  return row.addr || '-';
}

function orderCreateTime(row: AnyRecord) {
  return formatDate(row.createtime || row.createTime);
}

function orderDeliverDate(row: AnyRecord) {
  return formatDate(row.deliveryTime || row.deliverTime || row.orderDeliverTime || row.sendTime || row.orderLastpay);
}

function alipayRentOrderNo(row: AnyRecord) {
  return row.rentOrderId || row.alipayOrderId || row.aplyOrderNo || '-';
}

function orderAuthNo(row: AnyRecord) {
  return row.orderAuthNo || row.aplyOrderAuthNo || '-';
}

function paymentTradeNo(row: AnyRecord) {
  return row.paymentTradeNo || row.tradeNo || '-';
}

function alipayOrderNo(row: AnyRecord) {
  return row.aplyOrderNo || row.rentOrderId || '-';
}

function deliveryToneClass(row: AnyRecord) {
  const value = orderDeliverDate(row);
  if (value !== '-') return '';
  return ['PAID', 'DELIVERED', 'RECEIVED', 'RETURN_DELIVERED'].includes(row.alipayStatus) ? 'field-tone--warning' : '';
}

function orderSourceText(row: AnyRecord) {
  return row.sourceName || row.sourceTitle || row.sourceType || (hasSource(row) ? '支付宝租赁' : '信用租赁');
}

function orderRemarkText(row: AnyRecord) {
  return row.orderLeave || row.remark || '-';
}

function remarkToneClass(row: AnyRecord) {
  return orderRemarkText(row) === '-' ? '' : 'field-tone--warning';
}

function realNameToneClass(row: AnyRecord) {
  return realNameInfo(row) === '-' ? 'field-tone--warning' : '';
}

function idCardPhotoToneClass(row: AnyRecord) {
  const reviewStatus = identityPhotoReviewStatus(row);
  if (reviewStatus === 'REJECTED' || reviewStatus === 'PENDING_UPLOAD') return 'field-tone--danger';
  if (reviewStatus === 'PENDING_REVIEW') return 'field-tone--warning';
  return '';
}

function knownKeySet(keys: unknown, fallback: string[]) {
  return new Set((Array.isArray(keys) ? keys.map(String) : fallback).filter(Boolean));
}

function normalizeOrderFieldKeys(keys: unknown, knownKeys?: unknown) {
  const allowed = new Set(orderFieldDefinitions.map((field) => field.key));
  const sourceKeys = Array.isArray(keys) ? keys.map(String) : defaultOrderFieldKeys;
  const normalized = Array.from(new Set(sourceKeys.filter((key) => allowed.has(key))));
  if (Array.isArray(knownKeys)) {
    const known = knownKeySet(knownKeys, sourceKeys);
    orderFieldDefinitions.forEach((field) => {
      if (field.defaultVisible === false || known.has(field.key) || normalized.includes(field.key)) return;
      normalized.push(field.key);
    });
  }
  return normalized;
}

function normalizeRecordActionKeys(keys: unknown, knownKeys?: unknown) {
  const allowed = new Set(defaultRecordActionKeys);
  const sourceKeys = Array.isArray(keys) ? keys.map(String) : defaultRecordActionKeys;
  const normalized = Array.from(new Set(sourceKeys.filter((key) => allowed.has(key))));
  if (Array.isArray(knownKeys)) {
    const known = knownKeySet(knownKeys, sourceKeys);
    defaultRecordActionKeys.forEach((key) => {
      if (known.has(key) || normalized.includes(key)) return;
      normalized.push(key);
    });
  }
  return normalized;
}

function normalizeOrderDrawerSectionKeys(keys: unknown, knownKeys?: unknown) {
  const allowed = new Set<string>(orderDrawerSections.map((section) => section.key));
  const lockedKeys = orderDrawerSections.filter((section) => section.locked).map((section) => section.key);
  const sourceKeys = Array.isArray(keys) ? keys.map(String) : defaultOrderDrawerSectionKeys;
  const normalized = Array.from(new Set([...sourceKeys, ...lockedKeys].filter((key) => allowed.has(key)))) as OrderDrawerSectionKey[];
  if (Array.isArray(knownKeys)) {
    const known = knownKeySet(knownKeys, sourceKeys);
    defaultOrderDrawerSectionKeys.forEach((key) => {
      if (known.has(key) || normalized.includes(key)) return;
      normalized.push(key);
    });
  }
  return normalized;
}

function loadOrderFieldSettings() {
  if (typeof window === 'undefined') return;
  try {
    const stored = JSON.parse(window.localStorage.getItem(orderFieldSettingStorageKey) || '{}');
    visibleOrderDrawerSectionKeys.value = normalizeOrderDrawerSectionKeys(stored.modules, stored.knownKeys?.modules);
    visibleOrderFieldKeys.value = normalizeOrderFieldKeys(stored.fields, stored.knownKeys?.fields);
    visibleRecordActionKeys.value = normalizeRecordActionKeys(stored.actions, stored.knownKeys?.actions);
  } catch {
    visibleOrderDrawerSectionKeys.value = [...defaultOrderDrawerSectionKeys];
    visibleOrderFieldKeys.value = [...defaultOrderFieldKeys];
    visibleRecordActionKeys.value = [...defaultRecordActionKeys];
  }
}

function openOrderFieldSetting() {
  loadOrderFieldSettings();
  orderFieldSettingVisible.value = true;
}

function saveOrderFieldSettings() {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(orderFieldSettingStorageKey, JSON.stringify({
      modules: visibleOrderDrawerSectionKeys.value,
      fields: visibleOrderFieldKeys.value,
      actions: visibleRecordActionKeys.value,
      knownKeys: {
        modules: orderDrawerSections.map((section) => section.key),
        fields: orderFieldDefinitions.map((field) => field.key),
        actions: defaultRecordActionKeys,
      },
    }));
  }
  orderFieldSettingVisible.value = false;
  MessagePlugin.success('显示设置已保存');
}

function saveOrderDisplaySettings(next: DisplaySettingState) {
  visibleOrderDrawerSectionKeys.value = normalizeOrderDrawerSectionKeys(next.modules);
  visibleOrderFieldKeys.value = normalizeOrderFieldKeys(next.fields);
  visibleRecordActionKeys.value = normalizeRecordActionKeys(next.actions);
  saveOrderFieldSettings();
}

function recordActions(row: AnyRecord): RecordActionItem[] {
  if (!hasSource(row)) return [];
  const actions: RecordActionItem[] = [];
  const add = (
    visible: boolean,
    group: RecordActionGroupKey,
    key: string,
    label: string,
    code: string,
    theme: RecordActionTheme,
    handler: () => void,
  ) => {
    if (visible) actions.push({ key, label, code, theme, group, handler });
  };

  add(true, 'view', 'detail', '详情', buttonCode('order', 'view'), 'primary', () => openOrderDetailDrawer(row));
  add(['CREATED', 'SIGNED', 'APPROVED', 'DELIVERED'].includes(row.alipayStatus), 'flow', 'close', '订单关闭', buttonCode('order', 'operate'), 'danger', () => closeRentOrder(row));
  add(row.alipayStatus === 'PAID' && !isStorePickupOrder(row), 'flow', 'deliver', '商品发货', buttonCode('order', 'operate'), 'danger', () => rentComDeliver(row));
  add(row.alipayStatus === 'PAID' && isStorePickupOrder(row), 'flow', 'deliverByUser', '自提发货', buttonCode('order', 'operate'), 'danger', () => rentComDeliverByUser(row));
  add(row.alipayStatus === 'DELIVERED', 'flow', 'confirmReceive', '确认收货', buttonCode('order', 'operate'), 'danger', () => confirmBtnNew(row));
  add(row.alipayStatus === 'RECEIVED', 'flow', 'returnDeliver', '归还发货', buttonCode('order', 'operate'), 'danger', () => rentRturn(row));
  add(row.alipayStatus === 'RECEIVED', 'flow', 'returnDeliverByUser', '线下归还', buttonCode('order', 'operate'), 'danger', () => rentRturnByUser(row));
  add(row.alipayStatus === 'RETURN_DELIVERED', 'flow', 'returnReceive', '确认归还收货', buttonCode('order', 'operate'), 'danger', () => rentComReceive(row));
  add(row.alipayStatus === 'RETURN_RECEIVED', 'flow', 'finish', '订单完结', buttonCode('order', 'operate'), 'danger', () => confirmRentOrder(row));
  add(Number(row.orderRestDeposit || 0) > 0 && !['FINISHED', 'CLOSED'].includes(row.alipayStatus), 'after', 'deduct', '扣除押金', buttonCode('order', 'operate'), 'danger', () => deductDes(row));
  add(canDirectRefund(row), 'after', 'refund', '退款', buttonCode('order', 'operate'), 'danger', () => rentComRefund(row));
  add(row.alipayStatus === 'PENDING_CANCEL', 'after', 'agreeRefund', '同意退款', buttonCode('order', 'operate'), 'danger', () => rentComRefund(row));
  add(row.alipayStatus === 'PENDING_CANCEL', 'after', 'rejectRefund', '不同意退款', buttonCode('order', 'operate'), 'danger', () => cancelRentComRefund(row));
  add(true, 'after', 'syncAftersales', '同步售后', buttonCode('aftersale', 'sync'), 'warning', () => syncAftersales(row));
  add(canGenerateContractPdf(row), 'view', 'generateContract', generateContractPdfButtonText(row), buttonCode('order', 'operate'), 'warning', () => generateContractPdf(row));
  add(canSyncContractToAlipay(row), 'view', 'syncContract', '回传协议', buttonCode('order', 'operate'), 'success', () => syncContractToAlipay(row));
  add(canReviewIdentityPhotos(row), 'view', 'identityApprove', '身份通过', buttonCode('order', 'operate'), 'success', () => reviewIdentityPhotos(row, true));
  add(canReviewIdentityPhotos(row), 'view', 'identityReject', '身份驳回', buttonCode('order', 'operate'), 'danger', () => reviewIdentityPhotos(row, false));
  add(true, 'view', 'remark', '添加备注', buttonCode('order', 'operate'), 'success', () => insertRemark(row));
  return actions;
}

function visibleRecordActions(row: AnyRecord) {
  const actionMap = new Map(recordActions(row)
    .filter((action) => !action.code || permissionStore.hasButton(action.code))
    .map((action) => [action.key, action]));
  return recordActionDisplayKeys.value.map((key) => actionMap.get(key)).filter(Boolean) as RecordActionItem[];
}

function externalOrderDrawerActions(row: AnyRecord): RecordActionItem[] {
  if (!hasSource(row)) return [];
  const selectedKeys = new Set(orderDrawerSectionDisplayKeys.value);
  return availableOrderDrawerSections(row)
    .filter((section) => !section.locked && !selectedKeys.has(section.key))
    .map((section) => ({
      key: `drawer-${section.key}`,
      label: section.label,
      code: section.code || '',
      theme: 'primary' as RecordActionTheme,
      group: 'view' as RecordActionGroupKey,
      handler: () => {
        void openOrderDetailDrawer(row, section.key, 'single');
      },
    }));
}

function displayedRecordActions(row: AnyRecord) {
  const configuredActions = visibleRecordActions(row);
  const detailActions = configuredActions.filter((action) => action.key === 'detail');
  const otherActions = configuredActions.filter((action) => action.key !== 'detail');
  return [
    ...detailActions,
    ...externalOrderDrawerActions(row),
    ...otherActions,
  ];
}

function recordInlineActions(row: AnyRecord) {
  return displayedRecordActions(row).slice(0, 6);
}

function recordOverflowActions(row: AnyRecord) {
  return displayedRecordActions(row).slice(6);
}

function objectToLabelValueList(source: AnyRecord | undefined, labelMap: Record<string, string>) {
  if (!source || typeof source !== 'object') return [];
  return Object.keys(source)
    .filter((key) => !/^\d+$/.test(key) && source[key] !== null && source[key] !== undefined && source[key] !== '')
    .map((key) => ({
      key,
      label: labelMap[key] || key,
      value: typeof source[key] === 'number' ? Number(source[key]).toFixed(2) : String(source[key]),
    }));
}

type RiskLabel = {
  label: string;
  description?: string;
};

const riskTypeLabelMap: Record<string, RiskLabel> = {
  CONCURRENT_RENT: {
    label: '多头租赁风险',
    description: '用户可能同时申请或持有多笔租赁订单。',
  },
  ORDER_CONCURRENT_RENT_RISK: {
    label: '订单多头租赁风险',
    description: '订单存在同时租赁或短期频繁申请的风险。',
  },
  ACCOUNT_RISK: { label: '账号风险' },
  ORDER_RISK: { label: '订单风险' },
  CREDIT_RISK: { label: '信用风险' },
  PERFORMANCE_RISK: { label: '履约风险' },
  OVERDUE_RISK: { label: '逾期风险' },
};

const riskCodeLabelMap: Record<string, RiskLabel> = {
  ORDER_CONCURRENT_RENT_RISK: {
    label: '订单多头租赁风险',
    description: '提示该用户可能已有并行租赁或短期频繁申请，发货前建议核对履约能力。',
  },
  ACCOUNT_RENT_APPLY_CNT_TO: {
    label: '账号当天租赁申请次数',
    description: '统计该支付宝账号当天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T0: {
    label: '账号当天租赁申请次数',
    description: '统计该支付宝账号当天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T1: {
    label: '账号近 1 天租赁申请次数',
    description: '统计该支付宝账号近 1 天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T3: {
    label: '账号近 3 天租赁申请次数',
    description: '统计该支付宝账号近 3 天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T7: {
    label: '账号近 7 天租赁申请次数',
    description: '统计该支付宝账号近 7 天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T15: {
    label: '账号近 15 天租赁申请次数',
    description: '统计该支付宝账号近 15 天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T30: {
    label: '账号近 30 天租赁申请次数',
    description: '统计该支付宝账号近 30 天提交租赁申请的次数。',
  },
  ACCOUNT_RENT_APPLY_CNT_T90: {
    label: '账号近 90 天租赁申请次数',
    description: '统计该支付宝账号近 90 天提交租赁申请的次数。',
  },
};

const riskTokenLabelMap: Record<string, string> = {
  ACCOUNT: '账号',
  USER: '用户',
  ORDER: '订单',
  RENT: '租赁',
  APPLY: '申请',
  CNT: '次数',
  COUNT: '次数',
  CONCURRENT: '多头',
  MULTI: '多头',
  RISK: '风险',
  CREDIT: '信用',
  OVERDUE: '逾期',
  PERFORMANCE: '履约',
  DEVICE: '设备',
  MOBILE: '手机号',
  PHONE: '手机号',
  IDENTITY: '身份',
  CERT: '证件',
  BLACK: '黑名单',
  LIST: '名单',
  HISTORY: '历史',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败',
  AMT: '金额',
  AMOUNT: '金额',
};

const riskPeriodTokenLabelMap: Record<string, string> = {
  TO: '当天',
  T0: '当天',
  T1: '近 1 天',
  T3: '近 3 天',
  T7: '近 7 天',
  T15: '近 15 天',
  T30: '近 30 天',
  T60: '近 60 天',
  T90: '近 90 天',
  T180: '近 180 天',
  T365: '近 365 天',
};

const riskLevelTextMap: Record<string, string> = {
  '0': '未命中风险',
  '1': '低风险',
  '2': '中风险',
  '3': '高风险',
  '4': '高风险',
  '5': '高风险',
  NOR: '正常',
  NORMAL: '正常',
  NONE: '未命中风险',
  PASS: '通过',
  SAFE: '低风险',
  LOW: '低风险',
  L: '低风险',
  GREEN: '低风险',
  MEDIUM: '中风险',
  M: '中风险',
  YELLOW: '中风险',
  HIGH: '高风险',
  H: '高风险',
  RED: '高风险',
  REJECT: '不建议通过',
};

function normalizeRiskKey(value: unknown) {
  return String(value ?? '').trim().toUpperCase();
}

function readableRiskFallback(value: unknown, fallbackPrefix: string) {
  const raw = String(value ?? '').trim();
  if (!raw) return '-';
  const normalized = normalizeRiskKey(raw);
  const tokens = normalized.split(/[_\s-]+/).filter(Boolean);
  const period = tokens.find((token) => riskPeriodTokenLabelMap[token]);
  const words = tokens
    .filter((token) => !riskPeriodTokenLabelMap[token])
    .map((token) => riskTokenLabelMap[token] || token)
    .filter(Boolean);
  const label = words.join('');
  if (label && label !== normalized) {
    return period ? `${label}（${riskPeriodTokenLabelMap[period]}）` : label;
  }
  return `${fallbackPrefix}（${raw}）`;
}

function riskTypeText(value: unknown) {
  const normalized = normalizeRiskKey(value);
  return riskTypeLabelMap[normalized]?.label || readableRiskFallback(value, '未知风险类型');
}

function riskCodeText(value: unknown) {
  const normalized = normalizeRiskKey(value);
  return riskCodeLabelMap[normalized]?.label || riskTypeLabelMap[normalized]?.label || readableRiskFallback(value, '未知风险项');
}

function riskCodeDescription(value: unknown) {
  const normalized = normalizeRiskKey(value);
  return riskCodeLabelMap[normalized]?.description || riskTypeLabelMap[normalized]?.description || '';
}

function riskLevelTypeText(value: unknown) {
  const normalized = normalizeRiskKey(value);
  return riskLevelTextMap[normalized] || readableRiskFallback(value, '未知级别类型');
}

function shipRiskDecisionText(item: AnyRecord) {
  if (item?.canShipFlag === false) return '不建议发货，请先人工核实或联系用户补充资料';
  if (item?.canShipFlag === true) return '可以发货';
  return '支付宝未返回明确发货结论，请人工核实';
}

function productEditionText(value: string) {
  if (value === 'PRO') return '专业版';
  if (value === 'BASIC') return '基础版';
  return value || '-';
}

function vamGroupText(value: string) {
  if (value === 'experimental') return '实验组';
  if (value === 'control') return '对照组';
  return value || '-';
}

function riskLevelTheme(value: unknown) {
  const normalized = normalizeRiskKey(value);
  if (['HIGH', 'H', 'RED', '3', '4', '5', 'REJECT'].includes(normalized)) return 'danger';
  if (['MEDIUM', 'M', 'YELLOW', '2'].includes(normalized)) return 'warning';
  if (['LOW', 'L', 'GREEN', 'SAFE', 'PASS', 'NORMAL', 'NOR', 'NONE', '0', '1'].includes(normalized)) return 'success';
  return 'default';
}

function riskRankText(value: unknown) {
  const normalized = normalizeRiskKey(value);
  return riskLevelTextMap[normalized] || readableRiskFallback(value, '未知风险级别');
}

function cloudRentRiskItems(risk: AnyRecord) {
  const sub = risk?.subRentRiskResult || {};
  const fixedItems = [
    { key: 'basePerformanceRisk', title: '基础履约风险', ...(sub.basePerformanceRisk || {}) },
    { key: 'multiRentRisk', title: '多头租赁风险', ...(sub.multiRentRisk || {}) },
    { key: 'overdueRisk', title: '逾期风险', ...(sub.overdueRisk || {}) },
  ];
  const dynamicItems = Array.isArray(risk?.subRiskResultList)
    ? risk.subRiskResultList.map((item: AnyRecord, index: number) => ({
      key: `subRisk-${index}`,
      title: item.riskName || `子风险 ${index + 1}`,
      ...item,
    }))
    : [];
  return [...fixedItems, ...dynamicItems].filter((item) => item.riskName || item.riskRank || item.riskDesc);
}

function riskProviderDisplayName(detail: AnyRecord) {
  if (detail?.riskProviderName) return detail.riskProviderName;
  if (detail?.riskProvider === 'cloud-rent-risk') return '云风控';
  return '租安盾';
}

function normalizeRiskDetail(data: AnyRecord = {}) {
  return {
    riskProvider: data.riskProvider || 'rent-shield',
    riskProviderName: data.riskProviderName || '',
    productEdition: data.productEdition || '',
    riskInfos: data.riskInfos || [],
    riskBasicInfo: data.riskBasicInfo || null,
    comprehensiveRiskModels: data.comprehensiveRiskModels || null,
    extremelyLowRiskModels: data.extremelyLowRiskModels || null,
    highRiskModels: data.highRiskModels || null,
    shipGoodsRiskModels: data.shipGoodsRiskModels || [],
    vamGroup: data.vamGroup || null,
    alipayRisk: data.alipayRisk || null,
    rentShieldRisk: data.rentShieldRisk || null,
    cloudRentRisk: data.cloudRentRisk || (data.riskProvider === 'cloud-rent-risk' ? data.rentShieldRisk : null),
    localRisk: data.localRisk || null,
  };
}

function riskQueryUnavailableReason(detail: AnyRecord) {
  const candidates = [
    detail?.alipayRisk,
    detail?.rentShieldRisk,
    detail?.cloudRentRisk,
    detail?.risk,
  ];
  const unavailable = candidates.find((item) => item && item.available === false);
  return unavailable?.errorMessage || unavailable?.subMsg || unavailable?.msg || '';
}

function hasBlockedShipRisk(detail: AnyRecord) {
  return Array.isArray(detail?.shipGoodsRiskModels)
    && detail.shipGoodsRiskModels.some((item: AnyRecord) => item?.canShipFlag === false);
}

function shipRiskTheme(value: unknown): ShipRiskConfirmItem['theme'] {
  const theme = riskLevelTheme(value);
  return (['primary', 'success', 'warning', 'danger', 'default'].includes(theme) ? theme : 'default') as ShipRiskConfirmItem['theme'];
}

function buildShipRiskConfirmPayload(row: AnyRecord, detail: AnyRecord, queryError = ''): ShipRiskConfirmPayload {
  const unavailableReason = queryError || riskQueryUnavailableReason(detail);
  const blocked = hasBlockedShipRisk(detail);
  const items: ShipRiskConfirmItem[] = [];

  if (unavailableReason) {
    items.push({
      label: '风险查询',
      value: unavailableReason,
      badge: '查询失败',
      theme: 'warning',
    });
  }

  if (detail?.vamGroup) {
    items.push({
      label: '联营订单分组',
      value: vamGroupText(detail.vamGroup),
      badge: '订单分组',
      theme: 'default',
    });
  }

  if (detail?.comprehensiveRiskModels) {
    items.push({
      label: '综合风险',
      value: detail.comprehensiveRiskModels.desc || '未返回补充描述',
      badge: riskRankText(detail.comprehensiveRiskModels.riskLevel),
      theme: shipRiskTheme(detail.comprehensiveRiskModels.riskLevel),
    });
  }

  if (detail?.highRiskModels) {
    items.push({
      label: '高风险模型',
      value: detail.highRiskModels.desc || '未返回补充描述',
      badge: riskRankText(detail.highRiskModels.riskLevel),
      theme: shipRiskTheme(detail.highRiskModels.riskLevel),
    });
  }

  const shipGoodsRiskModels = Array.isArray(detail?.shipGoodsRiskModels) ? detail.shipGoodsRiskModels : [];
  shipGoodsRiskModels.slice(0, 3).forEach((item: AnyRecord) => {
    const blockedItem = item?.canShipFlag === false;
    items.push({
      label: riskCodeText(item.riskCode),
      value: shipRiskDecisionText(item),
      badge: blockedItem ? '不建议发货' : '发货判断',
      theme: blockedItem ? 'danger' : 'success',
      description: riskCodeDescription(item.riskCode),
    });
  });

  const riskInfos = Array.isArray(detail?.riskInfos) ? detail.riskInfos : [];
  riskInfos.slice(0, 3).forEach((riskInfo: AnyRecord) => {
    const riskItems = Array.isArray(riskInfo?.riskItemList) ? riskInfo.riskItemList : [];
    const firstRiskItem = riskItems[0] || {};
    const itemSummary = riskItems.length
      ? riskItems.slice(0, 3).map((item: AnyRecord) => `${riskCodeText(item.riskCode)}（${riskRankText(item.riskLevel)}）`).join('、')
      : '未返回具体风险项';
    items.push({
      label: riskTypeText(riskInfo?.riskType),
      value: itemSummary,
      badge: riskItems.length ? riskRankText(firstRiskItem.riskLevel) : '风险明细',
      theme: riskItems.length ? shipRiskTheme(firstRiskItem.riskLevel) : 'default',
    });
  });

  if (detail?.localRisk) {
    items.push({
      label: '本地规则',
      value: detail.localRisk.summary || '本地规则未返回命中说明',
      badge: localRiskLevelText(detail.localRisk.level),
      theme: detail.localRisk.level === 'HIGH' ? 'danger' : detail.localRisk.level === 'MEDIUM' ? 'warning' : 'success',
    });
  }

  if (!items.length) {
    items.push({
      label: '风险明细',
      value: '本次未返回明确风险明细',
      badge: '无阻断项',
      theme: 'default',
    });
  }

  const danger = Boolean(unavailableReason || blocked);
  return {
    orderNo: row?.orderNo || row?.orderId || '-',
    provider: riskProviderDisplayName(detail),
    productEdition: productEditionText(detail?.productEdition),
    danger,
    summaryTitle: unavailableReason ? '风险查询未正常返回' : blocked ? '存在不建议发货项' : '未发现阻断发货项',
    summaryText: unavailableReason
      ? '风险服务未返回完整结果，继续发货前请人工确认用户和订单信息。'
      : blocked
        ? '当前订单存在不建议发货的风险项，继续发货前请确认已完成线下核实。'
        : '当前风险结果未提示禁止发货，可在确认无误后继续提交物流信息。',
    items,
  };
}

function finishShipRiskConfirm(confirmed: boolean) {
  if (!shipRiskConfirmResolver) {
    shipRiskConfirmVisible.value = false;
    return;
  }
  const resolve = shipRiskConfirmResolver;
  shipRiskConfirmResolver = null;
  shipRiskConfirmVisible.value = false;
  resolve(confirmed);
}

function confirmShipRiskDialog(payload: ShipRiskConfirmPayload) {
  if (shipRiskConfirmResolver) {
    shipRiskConfirmResolver(false);
    shipRiskConfirmResolver = null;
  }
  shipRiskConfirm.value = payload;
  shipRiskConfirmVisible.value = true;
  return new Promise<boolean>((resolve) => {
    shipRiskConfirmResolver = resolve;
  });
}

async function confirmMerchantShipRisk(row: AnyRecord) {
  let detail: AnyRecord = normalizeRiskDetail({});
  let queryError = '';
  deliverRiskLoading.value = true;
  try {
    const response = await rentApi.rentUserRiskDetail(row);
    detail = normalizeRiskDetail(resolveData<AnyRecord>(response, {}));
  } catch (error: any) {
    queryError = error?.message || '风险查询请求失败';
  } finally {
    deliverRiskLoading.value = false;
  }
  return confirmShipRiskDialog(buildShipRiskConfirmPayload(row, detail, queryError));
}

function localRiskLevelText(value: string) {
  if (value === 'HIGH') return '高风险';
  if (value === 'MEDIUM') return '中风险';
  if (value === 'LOW') return '低风险';
  return value || '-';
}

function localRiskScoreText(localRisk: AnyRecord) {
  const score = Number(localRisk?.score ?? 0);
  return `${Number.isFinite(score) ? score : 0} 分`;
}

function localDeductStatusText(status: string) {
  if (status === 'SUCCESS') return '成功';
  if (status === 'PROCESSING') return '处理中';
  if (status === 'FAILED') return '失败';
  if (status === 'CANCELLED') return '已取消';
  return status || '-';
}

function returnStatusText(status: string) {
  const map: Record<string, string> = {
    SUBMITTED: '已提交',
    SEND_SUCCESS: '履约成功',
    SEND_FAILED: '履约失败',
  };
  return map[status] || status || '-';
}

function returnStatusTheme(status: string) {
  if (status === 'SEND_SUCCESS') return 'success';
  if (status === 'SEND_FAILED') return 'danger';
  return 'default';
}

function feeTypeText(type: string) {
  if (type === 'INDEMNITY') return '赔付金';
  if (type === 'LATE_FEE') return '违约金';
  return type || '-';
}

function recordStatusTheme(status: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'CANCELLED') return 'default';
  return 'warning';
}

function aftersaleStatusTheme(status: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAIL') return 'danger';
  if (status === 'APPROVING' || status === 'PROCESSING') return 'warning';
  return 'default';
}

function needOperationText(value: string) {
  if (value === 'true' || value === 'Y') return '是';
  if (value === 'false' || value === 'N') return '否';
  return '-';
}

function isMerchantSource(row: AnyRecord) {
  return !row || !row.sourceType || row.sourceType === 'MERCHANT';
}

function hasDeductAftersaleReference(row: AnyRecord) {
  return Boolean(row?.aftersaleNo || row?.outAftersaleId);
}

// 公域（芝麻租赁）售后的可操作基线：处理中或可补完结，且有售后单号。
// 单独抽出来避免和 canOperateDeductRecord（渲染总开关）互相递归。
function hasOperableAftersaleBase(row: AnyRecord) {
  return hasDeductAftersaleReference(row)
    && (row.status === 'PROCESSING' || canFinishAftersaleRecord(row));
}

function canOperateDeductRecord(row: AnyRecord) {
  // 这个总开关只负责决定“是否渲染操作区”，具体显示哪个按钮由各 canXxx 决定。
  // 必须把扣款失败但售后未终态（可重试/可撤销）的情况也算进来，否则 FAILED 记录会整组按钮被隐藏，导致卡死。
  return hasDeductAftersaleReference(row)
    && (canApproveWithUserPay(row)
      || canRejectDeductRecord(row)
      || canFinishDeductRecord(row)
      || canFinishAftersaleRecord(row)
      || canCancelDeductRecord(row));
}

function canApproveWithUserPay(row: AnyRecord) {
  return !isMerchantSource(row) && hasOperableAftersaleBase(row) && row.aftersaleStatus !== 'SUCCESS' && row.aftersaleStatus !== 'FAIL';
}

function canRejectDeductRecord(row: AnyRecord) {
  return !isMerchantSource(row) && hasOperableAftersaleBase(row) && row.aftersaleStatus !== 'SUCCESS' && row.aftersaleStatus !== 'FAIL';
}

function canFinishDeductRecord(row: AnyRecord) {
  if (!isMerchantSource(row) || !hasDeductAftersaleReference(row) || row.aftersaleStatus === 'FAIL') {
    return false;
  }
  // 已经发起押金转支付（有 tradeNo）的记录不再重复发起，等支付回调。
  if (row.lastOperationType === 'PAY_COMPENSATION' && row.tradeNo) {
    return false;
  }
  // 处理中：首次发起扣款；
  // 失败且没有 tradeNo：扣款发起失败（如 PAYMENT_FAIL），允许换新外部单号重试，不能卡死。
  if (row.status === 'PROCESSING') {
    return true;
  }
  return row.status === 'FAILED'
    && row.lastOperationType === 'PAY_COMPENSATION'
    && !row.tradeNo
    && row.aftersaleStatus !== 'SUCCESS';
}

function isRetryDeductRecord(row: AnyRecord) {
  return row.status === 'FAILED' && row.lastOperationType === 'PAY_COMPENSATION' && !row.tradeNo;
}

function finishDeductButtonText(row: AnyRecord) {
  return isRetryDeductRecord(row) ? '重试扣款' : '确认售后并扣款';
}

function canFinishAftersaleRecord(row: AnyRecord) {
  return isMerchantSource(row)
    && hasDeductAftersaleReference(row)
    && row.aftersaleStatus !== 'FAIL'
    && !isRemoteAftersaleFinished(row)
    && (row.status === 'SUCCESS' || row.status === 'PROCESSING');
}

function canCancelDeductRecord(row: AnyRecord) {
  // 已发起押金转支付（有 tradeNo）的不允许直接撤销，避免与在途支付冲突。
  if (row.tradeNo) {
    return false;
  }
  // 处理中或扣款失败、但支付宝售后仍未终态（APPROVING）的私域售后，允许撤销，转用户主动赔付/线下处理。
  return isMerchantSource(row)
    && (row.status === 'PROCESSING' || row.status === 'FAILED')
    && hasDeductAftersaleReference(row)
    && row.aftersaleStatus !== 'SUCCESS'
    && row.aftersaleStatus !== 'FAIL';
}

function deductResultText(row: AnyRecord) {
  if (row.status === 'SUCCESS') return '已扣款成功';
  if (isMerchantSource(row) && row.status === 'FAILED' && row.lastOperationType === 'PAY_COMPENSATION') return '售后已确认，扣款发起失败，可重试';
  if (row.status === 'FAILED') return '扣款失败';
  if (row.status === 'CANCELLED') return '已取消';
  if (isMerchantSource(row) && row.tradeNo) return '已发起押金转支付，待支付回调';
  if (isMerchantSource(row) && row.aftersaleStatus === 'SUCCESS') return '私域售后已完结，待发起押金转支付';
  if (row.aftersaleStatus === 'SUCCESS') return '售后成功，待支付回调确认';
  if (row.lastOperationType === 'PAY_COMPENSATION') return '私域售后已确认，正在发起扣款';
  if (row.lastOperationType === 'AFTERSALE_FINISH') return isMerchantSource(row) ? '私域售后已完结' : '售后终态已同步';
  if (row.lastOperationType === 'APPROVE_WITH_USER_PAY') return '已发起赔付，待支付回调';
  return '处理中';
}

function deductResultTheme(row: AnyRecord) {
  if (row.status === 'SUCCESS') return 'success';
  if (row.status === 'FAILED') return 'danger';
  if (row.status === 'CANCELLED') return 'default';
  return 'warning';
}

function buildDeductActionText(operationType: string) {
  if (operationType === 'APPROVE_WITH_USER_PAY') return '发起赔付';
  if (operationType === 'MERCHANT_REJECT') return '拒绝售后';
  if (operationType === 'PAY_COMPENSATION') return '确认售后并扣款';
  if (operationType === 'AFTERSALE_FINISH') return '同步售后状态';
  if (operationType === 'USER_CANCEL_APPLY') return '撤销售后';
  return '处理售后扣减';
}

async function confirmDeductRecord(record: AnyRecord, operationType: string) {
  const actionText = buildDeductActionText(operationType);
  if (!record?.id) {
    MessagePlugin.error('扣减记录缺少ID，无法提交');
    return;
  }
  let rejectReasonCode = '';
  if (operationType === 'MERCHANT_REJECT') {
    rejectReasonCode = window.prompt('请输入拒绝原因码：GOODS_DELIVERED / BUYER_AGREED / OTHER', 'OTHER') || '';
    if (!['GOODS_DELIVERED', 'BUYER_AGREED', 'OTHER'].includes(rejectReasonCode)) {
      MessagePlugin.warning('拒绝原因码不合法');
      return;
    }
  }
  if (operationType === 'USER_CANCEL_APPLY'
    && !window.confirm('撤销后这笔赔付售后将关闭，押金不再自动扣款，需改走用户主动赔付或线下处理。确认撤销？')) {
    return;
  }
  depositLoading.value = true;
  loading.value = true;
  try {
    if (operationType === 'AFTERSALE_FINISH') {
      MessagePlugin.info('正在请求支付宝售后完结...');
    }
    const response = await rentApi.confirmDeductDeposit({
      recordId: record.id,
      operationType,
      reasonCode: rejectReasonCode,
    });
    const data = resolveData<AnyRecord>(response, {});
    if (operationType === 'AFTERSALE_FINISH' && data.aftersaleFinishConfirmed === false) {
      MessagePlugin.warning(data.message || '完结请求已提交，但支付宝查询仍未完结');
    } else {
      MessagePlugin.success(data.message || `${actionText}成功`);
    }
    await loadDeductRecords(true);
    await getListOrder();
  } catch (error: any) {
    MessagePlugin.error(error?.message || `${actionText}失败`);
  } finally {
    depositLoading.value = false;
    loading.value = false;
  }
}

function isRemoteAftersaleFinished(row: AnyRecord) {
  return row.finished === true
    || row.remoteAftersaleFinished === true
    || (row.aftersaleStatus === 'SUCCESS' && row.lastOperationType === 'AFTERSALE_FINISH');
}

function downloadOrder() {
  MessagePlugin.info(`账单下载暂未开放，选择日期：${selectedDate.value || '-'}`);
}

async function runAction(action: () => Promise<AnyRecord>, message: string) {
  loading.value = true;
  try {
    await action();
    MessagePlugin.success(message);
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

async function confirmAction(_message: string, action: () => Promise<AnyRecord>, successMessage: string) {
  loading.value = true;
  try {
    await action();
    MessagePlugin.success(successMessage);
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

function deductDes(row: AnyRecord) {
  currentObj.value = row;
  depositDeductForm.feeType = 'INDEMNITY';
  depositDeductForm.reasonCode = 'ITEM_DAMAGED';
  depositDeductForm.deductAmount = '';
  depositDeductForm.remark = '';
  deductVisible.value = true;
}

async function submitDepositDeduct() {
  const amount = Number(depositDeductForm.deductAmount);
  const remain = Number(currentObj.value.orderRestDeposit || 0) / 100;
  if (!Number.isFinite(amount) || amount <= 0) {
    MessagePlugin.warning('请输入正确的扣减金额');
    return;
  }
  if (remain > 0 && amount > remain) {
    MessagePlugin.warning('扣减金额不能超过剩余押金');
    return;
  }
  loading.value = true;
  try {
    await rentApi.deductDeposit({
      orderId: currentObj.value.orderId,
      ...depositDeductForm,
    });
    MessagePlugin.success('私域赔付售后单已创建，请到售后扣减记录中确认售后并扣款');
    deductVisible.value = false;
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

function handleDepositFeeTypeChange() {
  depositDeductForm.reasonCode = reasonCodeOptions.value[0]?.value as string;
}

function insertRemark(row: AnyRecord) {
  currentObj.value = row;
  remarkText.value = row.remark || '';
  remarkVisible.value = true;
}

async function submitRemark() {
  loading.value = true;
  try {
    await rentApi.updateRemark({ orderId: currentObj.value.orderId, remark: remarkText.value });
    MessagePlugin.success('备注添加成功');
    remarkVisible.value = false;
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

function resetDrawerLoadedSections() {
  (Object.keys(drawerLoadedSections) as OrderDrawerSectionKey[]).forEach((key) => {
    drawerLoadedSections[key] = key === 'overview';
  });
}

function resetDrawerData() {
  clearSignedContractPreview();
  rentOrderDetail.value = {};
  riskInfoDetail.value = {};
  esignContractDetail.value = {};
  installmentBillDetail.value = {};
  currentRiskOrder.value = null;
  riskRequireIdCardPhoto.value = false;
  riskRequireEsign.value = false;
  operLogRecords.value = [];
  operLogPage.current = 1;
  operLogPage.pageSize = 5;
  operLogPage.total = 0;
  deductRecordPage.current = 1;
  deductRecordPage.pageSize = 5;
  deductRecordPage.total = 0;
  returnRecordData.value = {};
  depositData.value = {};
  deductRecords.value = [];
  resetDrawerLoadedSections();
}

async function openOrderDetailDrawer(row: AnyRecord, section: OrderDrawerSectionKey = 'overview', mode: OrderDetailDrawerMode = 'full') {
  currentObj.value = row;
  activeOrderDrawerSection.value = 'overview';
  resetDrawerData();
  orderDetailDrawerMode.value = mode;
  orderDetailDrawerVisible.value = true;
  await openOrderDrawerSection(section);
}

async function openOrderDrawerSection(section: OrderDrawerSectionKey) {
  if (section === 'deposit') {
    await openOrderDrawerSection('order');
    return;
  }
  activeOrderDrawerSection.value = section;
  if (section === 'order') {
    await Promise.allSettled([loadRentOrderDetail(), loadDepositDetail()]);
    return;
  }
  if (drawerLoadedSections[section]) return;
  if (section === 'deduct') await loadDeductRecords();
  if (section === 'fulfillment') await loadReturnRecordDetail();
  if (section === 'ledger') {
    operLogPage.current = 1;
    await loadOperLogs();
  }
  if (section === 'risk') await loadRiskDetail();
  if (section === 'contract') await loadEsignContract();
  if (section === 'billing') await loadInstallmentBills();
  drawerLoadedSections[section] = true;
}

async function loadRentOrderDetail(force = false) {
  if (!force && drawerLoadedSections.order) return;
  orderDetailLoading.value = true;
  try {
    const response = await rentApi.watchRentOrder(currentObj.value);
    rentOrderDetail.value = resolveData(response, {});
    drawerLoadedSections.order = true;
  } finally {
    orderDetailLoading.value = false;
  }
}

async function watchRentOrder(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'order');
}

function openContractPdf(row: AnyRecord) {
  if (!hasContractPdf(row)) {
    MessagePlugin.warning('协议尚未生成');
    return;
  }
  window.open(row.contractPdfUrl, '_blank', 'noopener,noreferrer');
}

async function generateContractPdf(row: AnyRecord) {
  loading.value = true;
  try {
    const response = await rentApi.generateContractPdf({ orderId: row.orderId });
    const data = resolveData<AnyRecord>(response, {});
    if (data.contractPdfUrl) {
      currentObj.value = { ...currentObj.value, ...data };
      contractPdfPreviewVersion.value = Date.now();
    }
    MessagePlugin.success(data.contractPdfUrl ? '协议已生成' : '协议生成已触发');
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

async function loadEsignContract(force = false) {
  if (!force && drawerLoadedSections.contract) return;
  contractLoading.value = true;
  try {
    const response = await rentApi.queryEsignContract({ orderId: currentObj.value.orderId });
    esignContractDetail.value = resolveContractPayload(response);
    await loadSignedContractPreview();
    drawerLoadedSections.contract = true;
  } finally {
    contractLoading.value = false;
  }
}

async function startEsignContract() {
  if (!isEsignEnabled(esignContractDetail.value)) {
    MessagePlugin.warning('当前未开启电子合同');
    return;
  }
  contractLoading.value = true;
  try {
    const response = await rentApi.startEsignContract({ orderId: currentObj.value.orderId });
    esignContractDetail.value = resolveContractPayload(response);
    await loadSignedContractPreview();
    MessagePlugin.success('电子合同签署流程已发起');
  } finally {
    contractLoading.value = false;
  }
}

async function loadInstallmentBills(force = false) {
  if (!force && drawerLoadedSections.billing) return;
  billingLoading.value = true;
  try {
    const response = await rentApi.queryInstallmentBills({ orderId: currentObj.value.orderId });
    installmentBillDetail.value = resolveData<AnyRecord>(response, {});
    drawerLoadedSections.billing = true;
  } finally {
    billingLoading.value = false;
  }
}

async function startWithholdSign() {
  if (!canStartWithholdSign.value) {
    MessagePlugin.warning(withholdActionHint.value);
    return;
  }
  billingLoading.value = true;
  try {
    const response = await rentApi.startWithholdSign({ orderId: currentObj.value.orderId });
    const data = resolveData<AnyRecord>(response, {});
    MessagePlugin.success('支付宝代扣签约链接已生成');
    if (data.signUrl) {
      window.open(data.signUrl, '_blank', 'noopener,noreferrer');
    }
    await loadInstallmentBills(true);
  } finally {
    billingLoading.value = false;
  }
}

async function syncContractToAlipay(row: AnyRecord) {
  contractSyncLoading.value = true;
  loading.value = true;
  try {
    const response = await rentApi.syncContractToAlipay({ orderId: row.orderId });
    const data = resolveData<AnyRecord>(response, {});
    currentObj.value = { ...currentObj.value, ...data };
    if (data.contractAlipaySyncStatus === 'SUCCESS') {
      MessagePlugin.success(data.message || '协议已回传支付宝');
    } else if (data.contractAlipaySyncStatus === 'FAILED') {
      MessagePlugin.warning(data.message || '协议回传失败，请查看失败原因');
    } else {
      MessagePlugin.info(data.message || '协议回传已触发');
    }
    await getListOrder();
  } finally {
    contractSyncLoading.value = false;
    loading.value = false;
  }
}

async function loadRiskDetail(force = false) {
  if (!force && drawerLoadedSections.risk) return;
  if (!canViewRiskInfo(currentObj.value)) {
    drawerLoadedSections.risk = true;
    return;
  }
  riskInfoLoading.value = true;
  try {
    const response = await rentApi.rentUserRiskDetail(currentObj.value);
    const data = resolveData<AnyRecord>(response, {});
    riskInfoDetail.value = normalizeRiskDetail(data);
    currentRiskOrder.value = currentObj.value;
    riskRequireIdCardPhoto.value = Boolean(currentObj.value?.idCardPhotoRequired);
    riskRequireEsign.value = Boolean(currentObj.value?.esignRequired);
    drawerLoadedSections.risk = true;
  } finally {
    riskInfoLoading.value = false;
  }
}

async function rentUserRiskDetail(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'risk');
}

function buildRiskApproveConfirmText(requirePhoto: boolean, requireEsign: boolean) {
  const requirements: string[] = [];
  if (requirePhoto) requirements.push('用户先上传身份证照片并通过二次审核后才能支付');
  if (requireEsign) requirements.push('用户确认收货前完成 e签宝电子合同签署');
  return requirements.length ? `是否审核通过，并要求${requirements.join('、')}？` : '是否确认审核通过？';
}

function buildRiskApproveSuccessText(requirePhoto: boolean, requireEsign: boolean) {
  if (requirePhoto && requireEsign) return '已通知用户上传身份证照片，并要求确认收货前完成 e签宝签署';
  if (requirePhoto) return '已通知用户上传身份证照片';
  if (requireEsign) return '审核通过，已要求用户确认收货前完成 e签宝签署';
  return '审核通过成功';
}

function handleRiskApprove() {
  if (!currentRiskOrder.value) return;
  riskInfoVisible.value = false;
  orderDetailDrawerVisible.value = false;
  const requirePhoto = riskRequireIdCardPhoto.value;
  const requireEsign = riskRequireEsign.value;
  confirmAction(
    buildRiskApproveConfirmText(requirePhoto, requireEsign),
    () => rentApi.rentOrderMerchantConfirm({
      ...currentRiskOrder.value,
      isAgree: true,
      idCardPhotoRequired: requirePhoto,
      esignRequired: requireEsign,
    }),
    buildRiskApproveSuccessText(requirePhoto, requireEsign),
  );
}

function handleRiskNoApprove() {
  if (!currentRiskOrder.value) return;
  riskInfoVisible.value = false;
  orderDetailDrawerVisible.value = false;
  confirmAction(
    '是否确认审核不通过？',
    () => rentApi.rentOrderMerchantConfirm({ ...currentRiskOrder.value, isAgree: false }),
    '审核不通过成功',
  );
}

function reviewIdentityPhotos(row: AnyRecord, isAgree: boolean) {
  confirmAction(
    isAgree ? '是否确认身份证照片审核通过？' : '是否确认身份证照片审核不通过？',
    () => rentApi.reviewIdentityPhotos({
      orderId: row.orderId,
      isAgree,
      remark: isAgree ? '' : '身份证照片审核不通过，请重新上传清晰的正反面照片',
    }),
    isAgree ? '身份证照片审核通过' : '已驳回，用户可重新上传',
  );
}

function closeRentOrder(row: AnyRecord) {
  confirmAction('是否关闭租赁订单？', () => rentApi.closeRentOrder(row), '订单已关闭');
}

function rentComDeliver(row: AnyRecord) {
  currentObj.value = row;
  dialogTitle.value = '填写发货信息';
  rentComDeliverForm.courCode = '';
  rentComDeliverForm.courno = '';
  rentComDeliverForm.courName = '';
  rentComDeliverForm.status = MERCHANT_DELIVERY_SEND;
  deliverVisible.value = true;
}

function rentRturn(row: AnyRecord) {
  currentObj.value = row;
  dialogTitle.value = '填写发货信息';
  rentComDeliverForm.courCode = '';
  rentComDeliverForm.courno = '';
  rentComDeliverForm.courName = '';
  rentComDeliverForm.status = USER_DELIVERY_SEND;
  deliverVisible.value = true;
}

function handleExpressCompanyChange(value: string) {
  rentComDeliverForm.courName = expressCompanyList.find((item) => item.code === value)?.name || '';
}

async function submitRentComDeliver() {
  if (deliverBusy.value) return;
  if (!rentComDeliverForm.courCode || !rentComDeliverForm.courno) {
    MessagePlugin.warning('请填写完整物流信息');
    return;
  }
  const isMerchantDelivery = rentComDeliverForm.status === MERCHANT_DELIVERY_SEND;
  if (isMerchantDelivery) {
    const confirmed = await confirmMerchantShipRisk(currentObj.value);
    if (!confirmed) return;
  }
  deliverSubmitting.value = true;
  try {
    await rentApi.rentSend({
      orderId: currentObj.value.orderId,
      courCode: rentComDeliverForm.courCode,
      courno: rentComDeliverForm.courno,
      courName: rentComDeliverForm.courName,
      rentSendStatus: rentComDeliverForm.status,
      riskConfirmed: isMerchantDelivery,
      riskConfirmScene: isMerchantDelivery ? 'SHIP_BEFORE_SEND' : '',
    });
    MessagePlugin.success('物流信息已提交');
    deliverVisible.value = false;
    await getListOrder({}, { silent: true });
  } finally {
    deliverSubmitting.value = false;
  }
}

async function rentComDeliverByUser(row: AnyRecord) {
  if (!isStorePickupOrder(row)) {
    MessagePlugin.warning('该订单未选择自提，请走商品发货');
    return;
  }
  if (deliverBusy.value) return;
  const confirmed = await confirmMerchantShipRisk(row);
  if (!confirmed) return;
  deliverSubmitting.value = true;
  try {
    await rentApi.rentSend({
      orderId: row.orderId,
      courCode: 'NEW_TAOBAO',
      courno: `${row.orderNo}00`,
      courName: '用户自提',
      rentSendStatus: MERCHANT_DELIVERY_SEND,
      riskConfirmed: true,
      riskConfirmScene: 'SHIP_BEFORE_SEND',
    });
    MessagePlugin.success('自提发货成功');
    await getListOrder({}, { silent: true });
  } finally {
    deliverSubmitting.value = false;
  }
}

function isStorePickupOrder(row: AnyRecord) {
  return Number(row?.offlinePickup) === 0;
}

function rentRturnByUser(row: AnyRecord) {
  runAction(
    () => rentApi.rentSend({
      orderId: row.orderId,
      courCode: 'NEW_TAOBAO',
      courno: `${row.orderNo}01`,
      courName: '线下归还',
      rentSendStatus: USER_DELIVERY_SEND,
    }),
    '线下归还发货成功',
  );
}

function confirmBtnNew(row: AnyRecord) {
  confirmAction(
    '是否确认收货？',
    async () => {
      const ready = await ensureEsignReadyBeforeReceive(row);
      if (!ready) {
        throw new Error('请先完成电子合同签署');
      }
      return rentApi.confirmSend({ ...row, rentSendStatus: 'MERCHANT_DELIVERY_RECEIVED' });
    },
    '确认收货成功',
  );
}

async function ensureEsignReadyBeforeReceive(row: AnyRecord) {
  const response = await rentApi.queryEsignContract({ orderId: row.orderId });
  const data = resolveContractPayload(response);
  if (!isEsignEnabled(data) || isEsignCompleted(data)) {
    return true;
  }
  await openOrderDetailDrawer(row, 'contract');
  esignContractDetail.value = data;
  await loadSignedContractPreview();
  drawerLoadedSections.contract = true;
  MessagePlugin.warning('请先完成电子合同签署后再确认收货');
  return false;
}

function clearSignedContractPreview() {
  if (signedContractPreviewUrl.value && signedContractPreviewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(signedContractPreviewUrl.value);
  }
  signedContractPreviewUrl.value = '';
}

async function loadSignedContractPreview() {
  clearSignedContractPreview();
  if (!esignSignedFileUrl(esignContractDetail.value) || !isMeaningful(currentObj.value?.orderId)) {
    return;
  }
  const token = localStorage.getItem('token') || '';
  const url = buildGatewayUrl(
    `${apiBase}/rent-component/esign-contract/signed-file?orderId=${encodeURIComponent(String(currentObj.value.orderId))}&_preview=${contractPdfPreviewVersion.value}`,
  );
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        ...(token ? { token } : {}),
        'X-Request-Source': 'admin',
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const contentType = response.headers.get('content-type') || '';
    const blob = await response.blob();
    if (!blob.size || !contentType.toLowerCase().includes('application/pdf')) {
      throw new Error('签署文件不是 PDF 响应');
    }
    signedContractPreviewUrl.value = URL.createObjectURL(blob);
  } catch (error) {
    console.warn('签署文件预览加载失败', error);
    MessagePlugin.warning('签署文件预览加载失败，可点击“查看签署文件”打开');
  }
}

function rentComReceive(row: AnyRecord) {
  confirmAction(
    '是否确认收货？',
    () => rentApi.confirmSend({ ...row, rentSendStatus: 'USER_DELIVERY_RECEIVED' }),
    '归还收货成功',
  );
}

function confirmRentOrder(row: AnyRecord) {
  currentObj.value = row;
  rentOrderFinishForm.finishType = 'USER_RETURNED';
  finishVisible.value = true;
}

async function submitRentOrderFinish() {
  loading.value = true;
  try {
    await rentApi.rentOrderComplete({
      orderId: currentObj.value.orderId,
      rentSendStatus: rentOrderFinishForm.finishType,
    });
    MessagePlugin.success('订单已完结');
    finishVisible.value = false;
    await getListOrder();
  } finally {
    loading.value = false;
  }
}

function rentComRefund(row: AnyRecord) {
  confirmAction('是否确认退款？', () => rentApi.rentComRefund({ ...row, isAgree: true }), '退款处理成功');
}

function cancelRentComRefund(row: AnyRecord) {
  confirmAction('是否确认不同意退款？', () => rentApi.rentComRefund({ ...row, isAgree: false }), '退款处理成功');
}

async function loadOperLogs() {
  const requestedPage = operLogPage.current;
  const requestedLimit = operLogPage.pageSize;
  const payload: AnyRecord = {
    page: requestedPage,
    limit: requestedLimit,
  };
  if (currentObj.value.orderId) payload.orderId = currentObj.value.orderId;
  else if (currentObj.value.orderNo) payload.orderNo = currentObj.value.orderNo;
  operLogLoading.value = true;
  try {
    const response = await rentApi.getOperLogList(payload);
    const page = resolvePage<AnyRecord>(response);
    operLogRecords.value = page.list;
    operLogPage.current = requestedPage;
    operLogPage.pageSize = requestedLimit;
    operLogPage.total = page.total;
    drawerLoadedSections.ledger = true;
  } finally {
    operLogLoading.value = false;
  }
}

function handleOperLogPageChange(pageInfo: PageInfo) {
  operLogPage.current = pageInfo.current;
  operLogPage.pageSize = pageInfo.pageSize;
  loadOperLogs();
}

function handleDeductRecordPageChange(pageInfo: PageInfo) {
  deductRecordPage.current = pageInfo.current;
  deductRecordPage.pageSize = pageInfo.pageSize;
  loadDeductRecords(true);
}

async function viewOperLog(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'ledger');
}

async function loadReturnRecordDetail(force = false) {
  if (!force && drawerLoadedSections.fulfillment) return;
  returnRecordLoading.value = true;
  try {
    const response = await rentApi.queryReturnRecord({ orderId: currentObj.value.orderId });
    returnRecordData.value = resolveData<AnyRecord>(response, {});
    drawerLoadedSections.fulfillment = true;
  } finally {
    returnRecordLoading.value = false;
  }
}

async function viewReturnRecord(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'fulfillment');
}

async function loadDepositDetail(force = false) {
  if (!force && drawerLoadedSections.deposit) return;
  depositLoading.value = true;
  try {
    const response = await rentApi.queryDeposit({ orderId: currentObj.value.orderId });
    depositData.value = resolveData<AnyRecord>(response, {});
    drawerLoadedSections.deposit = true;
  } finally {
    depositLoading.value = false;
  }
}

async function viewDeposit(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'order');
}

async function loadDeductRecords(force = false) {
  if (!force && drawerLoadedSections.deduct) return;
  const requestedPage = deductRecordPage.current;
  const requestedLimit = deductRecordPage.pageSize;
  deductRecordLoading.value = true;
  try {
    const response = await rentApi.deductDepositRecords({
      orderId: currentObj.value.orderId,
      page: requestedPage,
      limit: requestedLimit,
    });
    const page = resolvePage<AnyRecord>(response);
    deductRecords.value = page.list;
    deductRecordPage.current = requestedPage;
    deductRecordPage.pageSize = requestedLimit;
    deductRecordPage.total = page.total;
    drawerLoadedSections.deduct = true;
  } finally {
    deductRecordLoading.value = false;
  }
}

async function viewDeductRecords(row: AnyRecord) {
  await openOrderDetailDrawer(row, 'deduct');
}

function syncAftersales(row: AnyRecord) {
  currentObj.value = row;
  router.push({
    path: '/alipay/aftersales',
    query: cleanQuery({
      orderId: row.orderId,
      orderNo: row.orderNo,
      from: 'orders',
      autoScan: 1,
    }),
  });
}

function applyRouteOrderQuery() {
  const orderNo = routeOrderNo.value.trim();
  if (!orderNo) return false;
  tableParams.type = 'orderNo';
  tableParams.userName = orderNo;
  tableParams.page = 1;
  tableParams.chooseTime = '';
  tableParams.chooseDate = [];
  tableParams.start = '';
  tableParams.end = '';
  getListOrder({ orderNo });
  return true;
}

watch(routeOrderNo, (orderNo) => {
  if (orderNo) applyRouteOrderQuery();
});

function syncOrderDrawerViewport() {
  if (typeof window === 'undefined') return;
  isNarrowOrderDrawerViewport.value = window.innerWidth <= 760;
}

onMounted(() => {
  loadOrderFieldSettings();
  syncOrderDrawerViewport();
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncOrderDrawerViewport);
  }
  if (!applyRouteOrderQuery()) getListOrder();
});

onUnmounted(() => {
  clearSignedContractPreview();
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncOrderDrawerViewport);
  }
});
</script>

<style scoped>
.order-page {
  min-width: 0;
}

.handle-select {
  width: 100%;
}

.handle-input {
  width: 100%;
}

.date-range {
  width: 100%;
}

.bill-date {
  width: 100%;
}

.record-card {
  border: 1px solid #e7ebf3;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.record-card__header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 8px 14px;
  border-bottom: 1px solid #edf1f7;
}

.record-card__heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.record-card__title-block {
  min-width: 0;
}

.record-card__title-block strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-card__title-block span {
  display: block;
  margin-top: 1px;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-card__amounts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.record-card__amounts div {
  display: grid;
  gap: 1px;
  justify-items: end;
  min-width: 88px;
}

.record-card__amounts span {
  color: #98a2b3;
  font-size: 12px;
}

.record-card__meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 5px 14px;
  border-bottom: 1px solid #f2f4f7;
  color: #667085;
  font-size: 12px;
  line-height: 17px;
}

.record-card__body {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 8px 14px;
}

.record-info-group {
  min-width: 0;
}

.record-info-group__title {
  margin-bottom: 5px;
  color: #344054;
  font-size: 12px;
  font-weight: 800;
}

.record-field {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 6px;
  min-height: 20px;
  align-items: start;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.record-field span {
  color: #98a2b3;
}

.record-field strong {
  min-width: 0;
  overflow: hidden;
  color: #101828;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-field--block strong {
  display: -webkit-box;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.record-field--wrap :deep(.t-popup__reference) {
  display: block;
  min-width: 0;
}

.record-field--wrap strong {
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  word-break: break-all;
}

.field-tone--warning {
  color: #b45309 !important;
}

.field-tone--danger {
  color: #c2410c !important;
}

.field-tone--muted {
  color: #98a2b3 !important;
}

.record-field strong.field-tone--warning,
.record-field strong.field-tone--danger {
  display: inline-flex;
  align-items: center;
  width: max-content;
  max-width: 100%;
  padding: 0 6px;
  border-radius: 4px;
  line-height: 18px;
}

.record-field strong.field-tone--warning {
  background: #fffbeb;
}

.record-field strong.field-tone--danger {
  background: #fff1f0;
}

.amount-highlight {
  display: inline-flex !important;
  align-items: center;
  max-width: 100%;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
}

.amount-highlight--deposit {
  color: #a16207 !important;
  background: #fefce8;
}

.amount-highlight--income {
  color: #047857 !important;
  background: #ecfdf5;
}

.record-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 14px;
  border-top: 1px solid #f2f4f7;
  background: #fcfcfd;
}

.record-action-group {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.record-actions__label {
  flex: 0 0 auto;
  width: 42px;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  line-height: 24px;
}

.record-action-group__buttons,
.record-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
  min-width: 0;
}

.record-actions :deep(.t-button) {
  min-width: auto;
  height: 24px;
  min-height: 24px;
  padding-right: 6px;
  padding-left: 6px;
  border-radius: 4px !important;
  font-size: 12px;
  line-height: 22px;
  vertical-align: middle;
}

.record-actions :deep(.t-button__text) {
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.record-actions :deep(.t-button--variant-text) {
  border: 1px solid transparent;
  background: transparent;
}

.record-actions :deep(.t-button--variant-text:hover) {
  border-color: #d6e4ff;
  background: #f2f7ff;
}

.record-actions__more {
  color: #667085;
}

:global(.record-actions__dropdown-item--danger .t-dropdown__item-text) {
  color: var(--td-error-color);
}

:global(.record-actions__dropdown-item--warning .t-dropdown__item-text) {
  color: var(--td-warning-color);
}

:global(.record-actions__dropdown-item--success .t-dropdown__item-text) {
  color: var(--td-success-color);
}

.record-identity-photo-list {
  margin: 0;
  padding: 6px 14px;
  border-top: 1px solid #f2f4f7;
}

:global(.order-detail-drawer .t-drawer__body) {
  padding: 0;
  background: #f6f8fb;
}

.order-detail-drawer-body {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background: #f6f8fb;
}

.order-detail-drawer-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 88px;
  padding: 18px 24px;
  border-bottom: 1px solid #e4e7ec;
  background: #fff;
  box-shadow: none;
}

.order-detail-drawer-header__main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.order-detail-drawer-header__title {
  min-width: 0;
}

.order-detail-drawer-header__title strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 20px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-detail-drawer-header__title span {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  color: #667085;
  font-size: 14px;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin: 16px 24px 0;
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.order-detail-summary-card {
  min-width: 0;
  padding: 18px 20px;
  border-right: 1px solid #edf1f7;
}

.order-detail-summary-card:last-child {
  border-right: 0;
}

.order-detail-summary-card span {
  display: block;
  margin-bottom: 10px;
  color: #98a2b3;
  font-size: 13px;
  font-weight: 700;
}

.order-detail-summary-card strong {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 24px;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-detail-summary-card strong.amount-highlight--income,
.order-detail-summary-card strong.amount-highlight--deposit {
  padding: 2px 8px;
  border-radius: 4px;
}

.order-detail-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 16px 24px 0;
  padding: 4px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
}

.order-detail-tabs__item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 4px;
  color: #667085;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.order-detail-tabs__item:hover,
.order-detail-tabs__item--active {
  background: #eef5ff;
  color: #0052d9;
}

.order-detail-drawer-content {
  display: grid;
  gap: 16px;
  padding: 16px 24px 28px;
}

.drawer-section {
  display: grid;
  gap: 18px;
  min-width: 0;
  padding: 20px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.drawer-section .drawer-section {
  gap: 16px;
  padding: 22px 0 0;
  border: 0;
  border-top: 1px solid #eef2f6;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.drawer-section__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.drawer-section__header strong {
  display: block;
  color: #101828;
  font-size: 16px;
  font-weight: 800;
  line-height: 24px;
}

.drawer-section__header span {
  display: block;
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.drawer-overview-panel {
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
}

.drawer-overview-group {
  min-width: 0;
  padding: 14px 18px 16px;
}

.drawer-overview-group + .drawer-overview-group {
  border-top: 1px solid #edf1f7;
}

.drawer-overview-group h3 {
  margin: 0 0 12px;
  color: #344054;
  font-size: 13px;
  font-weight: 800;
  line-height: 20px;
}

.drawer-overview-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 28px;
  margin: 0;
}

.drawer-overview-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  color: #667085;
  font-size: 13px;
  line-height: 22px;
}

.drawer-overview-row--block {
  grid-column: 1 / -1;
  align-items: start;
}

.drawer-overview-row dt {
  margin: 0;
  color: #98a2b3;
  font-weight: 500;
}

.drawer-overview-row dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: #101828;
  font-weight: 700;
}

.drawer-overview-row dd.amount-highlight--income,
.drawer-overview-row dd.amount-highlight--deposit {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  padding: 0 8px;
  border-radius: 4px;
}

.drawer-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.drawer-info-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
}

.drawer-info-card__title {
  margin-bottom: 14px;
  color: #344054;
  font-size: 13px;
  font-weight: 800;
}

.drawer-info-row {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 12px;
  min-height: 28px;
  color: #667085;
  font-size: 13px;
  line-height: 22px;
}

.drawer-info-row span {
  color: #98a2b3;
}

.drawer-info-row strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #101828;
  font-weight: 700;
}

.drawer-amount-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
  margin-bottom: 0;
}

.drawer-amount-grid .amount-highlight-card {
  padding: 18px 20px;
  border-color: #edf1f7;
  background: #f8fafc;
}

.drawer-amount-grid .amount-highlight-card--primary {
  border-color: #c7ddff;
  background: #f7fbff;
}

.drawer-amount-grid .amount-highlight-card--danger {
  border-color: #ffd6d6;
  background: #fffafa;
}

.drawer-descriptions :deep(.t-descriptions__body) {
  background: transparent;
}

.drawer-descriptions :deep(.t-descriptions__label) {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.drawer-descriptions :deep(.t-descriptions__content) {
  color: #101828;
  font-size: 13px;
  line-height: 22px;
}

.drawer-timeline-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
  box-shadow: none;
}

.drawer-timeline-card__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.drawer-timeline-card__title strong {
  min-width: 0;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
}

.drawer-timeline-card p {
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 22px;
}

.drawer-mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 24px;
}

.drawer-mini-grid span {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.drawer-timeline-actions {
  flex-wrap: wrap;
}

.drawer-detail-box {
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #f8fafc;
}

.drawer-detail-box summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.drawer-detail-box summary::-webkit-details-marker {
  display: none;
}

.drawer-detail-box summary::after {
  flex: 0 0 auto;
  color: var(--td-brand-color);
  font-size: 12px;
  font-weight: 700;
  content: "展开";
}

.drawer-detail-box[open] summary::after {
  content: "收起";
}

.drawer-detail-box summary small {
  min-width: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.drawer-detail-box__body {
  display: grid;
  gap: 10px;
  padding: 0 12px 12px;
}

.drawer-detail-box__section {
  display: grid;
  gap: 6px;
}

.drawer-detail-box__title {
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}

.drawer-detail-box__title--error {
  color: #c2410c;
}

.drawer-detail-box__empty {
  color: #98a2b3;
  font-size: 12px;
}

.drawer-json-block {
  max-height: 240px;
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.drawer-json-block--error {
  border-color: #fed7aa;
  background: #fff7ed;
}

.drawer-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.drawer-risk-list {
  margin-top: 2px;
}

.drawer-risk-actions {
  justify-content: flex-end;
}

.local-risk-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #f8fafc;
}

.local-risk-panel__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.local-risk-panel__summary span,
.local-risk-metrics span,
.local-risk-history__row span {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.local-risk-panel__summary strong {
  display: block;
  margin-top: 4px;
  color: #101828;
  font-size: 26px;
  font-weight: 800;
  line-height: 34px;
}

.local-risk-panel__summary p {
  margin: 6px 0 0;
  color: #475467;
  font-size: 13px;
  line-height: 22px;
}

.local-risk-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.local-risk-metrics div {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.local-risk-metrics strong {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
  color: #101828;
  font-size: 15px;
  font-weight: 800;
  line-height: 22px;
}

.local-risk-rules {
  display: grid;
  gap: 10px;
}

.local-risk-rule {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.local-risk-rule__title,
.local-risk-history__row,
.local-risk-history__row > div:last-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.local-risk-rule__title strong,
.local-risk-history__title,
.local-risk-history__row strong {
  min-width: 0;
  color: #101828;
  font-size: 13px;
  font-weight: 800;
  line-height: 20px;
}

.local-risk-rule p,
.local-risk-rule span {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 21px;
}

.local-risk-history-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.local-risk-history {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.local-risk-history__title {
  margin-bottom: 10px;
}

.local-risk-history__list {
  display: grid;
  gap: 10px;
}

.local-risk-history__row {
  align-items: flex-start;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf1f7;
}

.local-risk-history__row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.local-risk-history__row > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.local-risk-history__row > div:last-child {
  flex: 0 0 auto;
  justify-content: flex-end;
}

.local-risk-history__row small {
  color: #98a2b3;
  font-size: 12px;
  line-height: 18px;
  text-align: right;
}

.local-risk-empty {
  color: #98a2b3;
  font-size: 13px;
  line-height: 22px;
}

.contract-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.contract-panel__meta {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #f8fafc;
}

.contract-panel__meta span {
  overflow-wrap: anywhere;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.contract-panel__meta .contract-panel__error {
  color: #b42318;
}

.contract-panel__preview {
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
}

.contract-panel__preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid #edf1f7;
  background: #fcfcfd;
}

.contract-panel__preview-header strong {
  color: #101828;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.contract-panel__preview-header span {
  color: #98a2b3;
  font-size: 12px;
  line-height: 18px;
}

.contract-panel__frame {
  display: block;
  width: 100%;
  height: min(680px, calc(100vh - 360px));
  min-height: 460px;
  border: 0;
  background: #f8fafc;
}

.billing-layout {
  display: grid;
  gap: 18px;
}

.billing-summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.billing-summary-item {
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid #edf1f7;
}

.billing-summary-item:last-child {
  border-right: 0;
}

.billing-summary-item span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.billing-summary-item strong {
  display: block;
  color: #101828;
  font-size: 20px;
  font-weight: 800;
  line-height: 28px;
}

.billing-summary-item--danger strong {
  color: #b42318;
}

.billing-withhold-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.billing-withhold-panel__columns {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  min-width: 0;
}

.billing-withhold-panel__main {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.billing-withhold-panel__main--deduct {
  padding-left: 18px;
  border-left: 1px solid #e4e7ec;
}

.billing-withhold-panel__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.billing-withhold-panel__title strong {
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 22px;
}

.billing-withhold-panel__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: #475467;
  font-size: 12px;
  line-height: 18px;
}

.billing-withhold-panel__meta span {
  overflow-wrap: anywhere;
}

.billing-withhold-panel__hint {
  margin: 0;
  color: #98a2b3;
  font-size: 12px;
  line-height: 18px;
}

.billing-withhold-panel__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.billing-table-panel {
  overflow: hidden;
  margin-top: 2px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.billing-table-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 12px 16px;
  border-bottom: 1px solid #edf1f7;
  background: #fcfcfd;
}

.billing-table-panel__header > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.billing-table-panel__header strong {
  display: block;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 22px;
}

.billing-table-panel__header span {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.order-detail-drawer-content :deep(.t-timeline) {
  padding-top: 4px;
}

.order-detail-drawer-content :deep(.t-timeline-item) {
  padding-bottom: 22px;
}

.order-detail-drawer-content :deep(.t-timeline-item__content) {
  min-width: 0;
}

.dialog-section {
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.dialog-section :deep(.t-descriptions__body--border .t-descriptions__label),
.dialog-section :deep(.t-descriptions__body--border .t-descriptions__label:hover) {
  background-color: #fff;
}

.dialog-section :deep(.t-descriptions__body .t-descriptions__label) {
  color: #667085;
}

.deduct-error-cell {
  display: grid;
  max-width: 220px;
  min-width: 0;
  gap: 4px;
  cursor: help;
}

.deduct-error-cell__summary,
.deduct-error-cell__code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deduct-error-cell__summary {
  color: #b42318;
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
}

.deduct-error-cell__code {
  color: #667085;
  font-size: 12px;
  line-height: 16px;
}

.section-title {
  margin-bottom: 14px;
  color: #101828;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
}

.tail-dialog-body {
  display: grid;
  gap: 16px;
}

.tail-form-section {
  padding: 20px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.tail-form-section--accent {
  border-color: #fed7aa;
  background: #fff;
}

.tail-form-section__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.tail-form-section__title {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
}

.tail-form-section__desc {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.tail-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}

.tail-form--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.tail-form-item--wide {
  grid-column: 1 / -1;
}

.tail-form :deep(.t-form__item) {
  margin-bottom: 0;
}

.tail-form :deep(.t-form__label) {
  padding-bottom: 6px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.tail-form :deep(.t-input),
.tail-form :deep(.t-select__wrap),
.tail-form :deep(.t-textarea__inner) {
  border-color: #d0d5dd;
  border-radius: 8px;
  box-shadow: none;
}

.tail-form :deep(.t-input),
.tail-form :deep(.t-select__wrap) {
  min-height: 40px;
}

.tail-form :deep(.t-textarea__inner) {
  min-height: 104px;
  resize: vertical;
}

.tail-form--readonly :deep(.t-form__controls-content) {
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  color: #101828;
  line-height: 22px;
}

.amount-highlight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.amount-highlight-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.amount-highlight-card span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.amount-highlight-card strong {
  display: block;
  overflow-wrap: anywhere;
  color: #101828;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.35;
}

.amount-highlight-card--primary {
  border-color: #bfdbfe;
}

.amount-highlight-card--danger {
  border-color: #fecaca;
}

.amount-highlight-card--danger strong {
  color: #b42318;
}

.amount-highlight-card--muted strong {
  font-size: 13px;
  font-weight: 700;
}

.money-input {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  min-height: 42px;
  overflow: hidden;
  border: 1px solid #fb923c;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.money-input__prefix,
.money-input__suffix {
  padding: 0 12px;
  color: #c2410c;
  font-size: 13px;
  font-weight: 800;
}

.money-input :deep(.t-input) {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.money-input :deep(.t-input:focus-within) {
  box-shadow: none;
}

@media (max-width: 720px) {
  .tail-form--two,
  .amount-highlight-grid,
  .billing-summary-strip,
  .order-detail-summary-grid,
  .drawer-overview-panel,
  .drawer-info-grid,
  .drawer-mini-grid,
  .local-risk-metrics,
  .local-risk-history-grid {
    grid-template-columns: 1fr;
  }

  .order-detail-drawer-header {
    min-height: auto;
    padding: 16px;
  }

  .order-detail-summary-grid {
    margin: 16px 16px 0;
  }

  .order-detail-summary-card {
    border-right: 0;
    border-bottom: 1px solid #edf1f7;
  }

  .order-detail-summary-card:last-child {
    border-bottom: 0;
  }

  .billing-summary-item {
    border-right: 0;
    border-bottom: 1px solid #edf1f7;
  }

  .billing-summary-item:last-child {
    border-bottom: 0;
  }

  .billing-withhold-panel,
  .billing-withhold-panel__actions {
    flex-direction: column;
  }

  .billing-withhold-panel__columns {
    grid-template-columns: 1fr;
    gap: 14px;
    width: 100%;
  }

  .billing-withhold-panel__main--deduct {
    padding-top: 14px;
    padding-left: 0;
    border-top: 1px solid #e4e7ec;
    border-left: 0;
  }

  .billing-withhold-panel__actions {
    width: 100%;
  }

  .drawer-overview-group,
  .drawer-overview-group:nth-child(odd) {
    border-right: 0;
  }

  .drawer-overview-group + .drawer-overview-group {
    border-top: 1px solid #edf1f7;
  }

  .order-detail-tabs {
    margin: 16px 16px 0;
  }

  .order-detail-drawer-content {
    gap: 16px;
    padding: 16px;
  }

  .drawer-section {
    gap: 18px;
    padding: 18px;
  }

  .drawer-section .drawer-section {
    padding-top: 18px;
  }

  .drawer-overview-row,
  .drawer-info-row {
    grid-template-columns: 1fr;
    gap: 2px;
  }

  .record-action-group {
    grid-template-columns: 1fr;
  }

}

:global(.ship-risk-confirm-dialog .t-dialog__body) {
  padding-top: 12px;
}

.ship-risk-confirm {
  display: grid;
  gap: 14px;
}

.ship-risk-confirm__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
}

.ship-risk-confirm__meta > div {
  min-width: 0;
  padding: 12px 14px;
  border-right: 1px solid #edf1f7;
}

.ship-risk-confirm__meta > div:last-child {
  border-right: 0;
}

.ship-risk-confirm__meta span {
  display: block;
  margin-bottom: 6px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.ship-risk-confirm__meta strong {
  display: block;
  overflow: hidden;
  color: #101828;
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ship-risk-confirm__summary {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #f8fafc;
}

.ship-risk-confirm__summary :deep(.t-icon) {
  flex: 0 0 auto;
  margin-top: 2px;
  font-size: 20px;
}

.ship-risk-confirm__summary strong {
  display: block;
  color: #101828;
  font-size: 15px;
  font-weight: 800;
  line-height: 22px;
}

.ship-risk-confirm__summary p {
  margin: 4px 0 0;
  color: #475467;
  font-size: 13px;
  line-height: 21px;
}

.ship-risk-confirm__summary--danger {
  border-color: #ffd6d6;
  background: #fff7f7;
}

.ship-risk-confirm__summary--danger :deep(.t-icon) {
  color: var(--td-error-color);
}

.ship-risk-confirm__summary--safe {
  border-color: #c7ead9;
  background: #f6fffa;
}

.ship-risk-confirm__summary--safe :deep(.t-icon) {
  color: var(--td-success-color);
}

.ship-risk-confirm__list {
  display: grid;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
  padding-right: 2px;
}

.ship-risk-confirm__item {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
}

.ship-risk-confirm__item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ship-risk-confirm__item-head span {
  min-width: 0;
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  font-weight: 800;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ship-risk-confirm__item p {
  margin: 8px 0 0;
  color: #101828;
  font-size: 14px;
  line-height: 22px;
}

.ship-risk-confirm__item small {
  display: block;
  margin-top: 6px;
  color: #667085;
  font-size: 12px;
  line-height: 19px;
}

.ship-risk-confirm__notice {
  padding-top: 2px;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 768px) {
  .ship-risk-confirm__meta {
    grid-template-columns: 1fr;
  }

  .ship-risk-confirm__meta > div {
    border-right: 0;
    border-bottom: 1px solid #edf1f7;
  }

  .ship-risk-confirm__meta > div:last-child {
    border-bottom: 0;
  }
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-card {
  padding: 12px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.risk-card-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.risk-card-title--inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.risk-item {
  margin-left: 16px;
  margin-bottom: 8px;
  line-height: 26px;
}

.empty-dialog {
  padding: 24px 0;
  color: var(--td-text-color-placeholder);
  text-align: center;
}

.fail-text {
  margin-left: 8px;
  color: var(--td-error-color);
}

.return-record-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.return-record-image {
  width: 96px;
  height: 96px;
  border-radius: 6px;
}

.identity-photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.identity-photo-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  color: #667085;
  font-size: 12px;
}

.identity-photo-thumb {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  cursor: pointer;
}

.risk-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
  color: var(--td-text-color-primary);
  font-weight: 600;
}

.identity-photo-preview {
  display: flex;
  justify-content: center;
  max-height: 70vh;
}

.identity-photo-preview :deep(.t-image__wrapper) {
  max-width: 100%;
  max-height: 70vh;
}

.json-block {
  max-height: 60vh;
  padding: 12px;
  overflow: auto;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}
</style>
