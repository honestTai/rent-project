<template>
  <t-dialog
    :visible="visible"
    :header="title"
    width="min(920px, calc(100vw - 56px))"
    attach="body"
    :confirm-btn="{ content: '保存设置', theme: 'primary' }"
    :cancel-btn="{ content: '取消', variant: 'base' }"
    @update:visible="emit('update:visible', $event)"
    @confirm="handleConfirm"
  >
    <div class="display-settings">
      <div class="display-settings__tabs" role="tablist" aria-label="显示设置分类">
        <button
          v-for="group in groups"
          :key="group.kind"
          type="button"
          class="display-settings__tab"
          :class="{ 'display-settings__tab--active': activeKind === group.kind }"
          @click="activeKind = group.kind"
        >
          {{ group.label }}
        </button>
      </div>

      <section v-for="group in groups" v-show="activeKind === group.kind" :key="group.kind" class="display-settings__panel">
        <div class="display-settings__header">
          <div>
            <strong>{{ group.title || group.label }}</strong>
            <span>{{ group.description || '勾选需要显示的内容，并通过上下移动调整展示顺序。' }}</span>
          </div>
          <t-button variant="outline" size="small" @click="resetKind(group.kind)">恢复当前默认</t-button>
        </div>

        <t-checkbox-group
          class="display-settings__checks"
          :model-value="draft[group.kind]"
          :options="checkboxOptions(group)"
          @change="handleKindChange(group, $event)"
        />

        <div class="display-settings__order">
          <div class="display-settings__order-title">展示顺序</div>
          <div v-if="orderedVisibleItems(group).length" class="display-settings__order-list">
            <div v-for="(item, index) in orderedVisibleItems(group)" :key="item.key" class="display-settings__order-item">
              <span>
                <em>{{ index + 1 }}</em>
                {{ item.group ? `${item.group} / ` : '' }}{{ item.label }}
              </span>
              <t-space size="small">
                <t-button size="small" variant="text" :disabled="index === 0" @click="moveItem(group.kind, item.key, -1)">上移</t-button>
                <t-button size="small" variant="text" :disabled="index === orderedVisibleItems(group).length - 1" @click="moveItem(group.kind, item.key, 1)">下移</t-button>
              </t-space>
            </div>
          </div>
          <t-empty v-else description="当前没有选中的展示内容" />
        </div>
      </section>
    </div>
  </t-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, unref, watch } from 'vue';

import {
  defaultDisplayKeys,
  normalizeDisplayKeys,
  orderDisplayItems,
  type DisplaySettingItem,
  type DisplaySettingKind,
  type DisplaySettingState,
} from '@/utils/display-settings';

export type DisplaySettingGroup = {
  kind: DisplaySettingKind;
  label: string;
  title?: string;
  description?: string;
  items: DisplaySettingItem[];
};

const props = withDefaults(defineProps<{
  visible: boolean;
  title?: string;
  groups: DisplaySettingGroup[];
  modelValue: DisplaySettingState;
}>(), {
  title: '显示设置',
});

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'save', value: DisplaySettingState): void;
}>();

const activeKind = ref<DisplaySettingKind>('modules');
const draft = reactive<Required<DisplaySettingState>>({
  modules: [],
  fields: [],
  actions: [],
});

const activeGroup = computed(() => props.groups.find((group) => group.kind === activeKind.value) || props.groups[0]);

function syncDraft() {
  props.groups.forEach((group) => {
    draft[group.kind] = normalizeDisplayKeys(
      unref(props.modelValue?.[group.kind]),
      group.items,
      group.items.map((item) => item.key),
    );
  });
  if (!props.groups.some((group) => group.kind === activeKind.value) && props.groups[0]) {
    activeKind.value = props.groups[0].kind;
  }
}

function checkboxOptions(group: DisplaySettingGroup) {
  return group.items.map((item) => ({
    label: `${item.group ? `${item.group} / ` : ''}${item.label}`,
    value: item.key,
    disabled: item.locked,
  }));
}

function orderedVisibleItems(group: DisplaySettingGroup) {
  const visibleSet = new Set(draft[group.kind]);
  return orderDisplayItems(draft[group.kind], group.items).filter((item) => visibleSet.has(item.key));
}

function updateKind(kind: DisplaySettingKind, value: unknown) {
  const group = props.groups.find((item) => item.kind === kind);
  if (!group) return;
  draft[kind] = normalizeDisplayKeys(value, group.items, group.items.map((item) => item.key));
}

function handleKindChange(group: DisplaySettingGroup, value: unknown) {
  updateKind(group.kind, value);
}

function resetKind(kind: DisplaySettingKind) {
  const group = props.groups.find((item) => item.kind === kind);
  if (!group) return;
  draft[kind] = defaultDisplayKeys(group.items);
}

function moveItem(kind: DisplaySettingKind, key: string, offset: number) {
  const next = [...draft[kind]];
  const index = next.indexOf(key);
  const nextIndex = index + offset;
  if (index < 0 || nextIndex < 0 || nextIndex >= next.length) return;
  [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
  draft[kind] = next;
}

function handleConfirm() {
  emit('save', {
    modules: [...draft.modules],
    fields: [...draft.fields],
    actions: [...draft.actions],
  });
  emit('update:visible', false);
}

watch(() => props.visible, (visible) => {
  if (visible) {
    syncDraft();
    if (activeGroup.value) activeKind.value = activeGroup.value.kind;
  }
});

watch(() => props.groups, syncDraft, { deep: true, immediate: true });
</script>

<style scoped>
.display-settings {
  display: grid;
  gap: 16px;
}

.display-settings__tabs {
  display: inline-flex;
  width: fit-content;
  padding: 3px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}

.display-settings__tab {
  min-width: 88px;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 4px;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
  background: transparent;
  cursor: pointer;
}

.display-settings__tab--active {
  color: #fff;
  background: #465fff;
}

.display-settings__panel {
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.display-settings__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.display-settings__header strong {
  display: block;
  color: #101828;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.display-settings__header span {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.display-settings__header :deep(.t-button) {
  width: auto;
  flex: 0 0 auto;
}

.display-settings__checks {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 14px;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fafafa;
}

.display-settings__checks :deep(.t-checkbox) {
  min-width: 0;
  margin-right: 0;
}

.display-settings__order {
  margin-top: 14px;
}

.display-settings__order-title {
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.display-settings__order-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.display-settings__order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;
}

.display-settings__order-item span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.display-settings__order-item em {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: 8px;
  border-radius: 50%;
  color: #667085;
  font-style: normal;
  background: #eef2f6;
}

@media (max-width: 720px) {
  .display-settings__checks,
  .display-settings__order-list {
    grid-template-columns: 1fr;
  }

  .display-settings__header {
    display: grid;
  }

  .display-settings__header :deep(.t-button) {
    justify-self: start;
  }
}
</style>
