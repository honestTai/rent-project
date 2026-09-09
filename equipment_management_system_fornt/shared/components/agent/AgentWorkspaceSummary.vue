<template>
  <section v-if="hasContent" class="agent-workspace-v2">
    <header v-if="summary.title || summary.subtitle">
      <div>
        <span>业务分析摘要</span>
        <strong>{{ summary.title }}</strong>
        <p>{{ summary.subtitle }}</p>
      </div>
      <i>{{ summary.statusText || '只读分析' }}</i>
    </header>
    <div v-if="evidence.length" class="agent-workspace-v2__evidence">
      <div v-for="item in evidence" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
    <div v-if="alerts.length" class="agent-workspace-v2__alerts">
      <article v-for="alert in alerts.slice(0, 3)" :key="alert.title" :class="`agent-workspace-v2__alert--${alert.level || 'info'}`">
        <t-icon :name="alert.level === 'error' ? 'close-circle' : alert.level === 'success' ? 'check-circle' : 'info-circle'" />
        <div><strong>{{ alert.title }}</strong><p>{{ alert.detail }}</p></div>
      </article>
    </div>
    <div v-if="nextPrompts.length" class="agent-workspace-v2__next">
      <span>继续追问</span>
      <button v-for="prompt in nextPrompts.slice(0, 5)" :key="prompt.label" type="button" @click="$emit('select', prompt.prompt || prompt.label)">
        {{ prompt.label }} <t-icon name="arrow-right" />
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { AnyRecord } from '../../agent/types';

const props = defineProps<{ workspace?: AnyRecord }>();
defineEmits<{ select: [prompt: string] }>();
const summary = computed(() => (props.workspace?.summary && typeof props.workspace.summary === 'object' ? props.workspace.summary : {}));
const evidence = computed(() => (Array.isArray(props.workspace?.evidence) ? props.workspace.evidence : []));
const alerts = computed(() => (Array.isArray(props.workspace?.alerts) ? props.workspace.alerts : []));
const nextPrompts = computed(() => {
  const value = props.workspace?.nextPrompts || props.workspace?.followUps;
  return Array.isArray(value) ? value : [];
});
const hasContent = computed(() => Boolean(summary.value.title || evidence.value.length || alerts.value.length || nextPrompts.value.length));
</script>

<style scoped>
.agent-workspace-v2 { display: grid; gap: 10px; margin-top: 14px; padding: 14px; border: 1px solid #e2e9f2; border-radius: 12px; background: #fbfcfe; }
.agent-workspace-v2 header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.agent-workspace-v2 header div { display: grid; gap: 3px; }
.agent-workspace-v2 header span, .agent-workspace-v2__next > span { color: #7b8ba1; font-size: 10px; font-weight: 700; letter-spacing: .06em; }
.agent-workspace-v2 header strong { color: #21304a; font-size: 14px; }
.agent-workspace-v2 header p { margin: 0; color: #6e7c90; font-size: 12px; line-height: 18px; }
.agent-workspace-v2 header i { padding: 3px 8px; border-radius: 999px; background: #eaf3ff; color: #1668dc; font-size: 10px; font-style: normal; white-space: nowrap; }
.agent-workspace-v2__evidence { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.agent-workspace-v2__evidence div { display: grid; gap: 2px; padding: 9px 10px; border-radius: 9px; background: #fff; box-shadow: inset 0 0 0 1px #e7edf5; }
.agent-workspace-v2__evidence span { color: #8491a4; font-size: 10px; }
.agent-workspace-v2__evidence strong { overflow: hidden; color: #24334c; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.agent-workspace-v2__alerts { display: grid; gap: 7px; }
.agent-workspace-v2__alerts article { display: grid; grid-template-columns: 18px minmax(0, 1fr); gap: 7px; padding: 8px 9px; border-radius: 8px; background: #fff7ed; color: #b45309; }
.agent-workspace-v2__alerts article.agent-workspace-v2__alert--error { background: #fef2f2; color: #dc2626; }
.agent-workspace-v2__alerts article.agent-workspace-v2__alert--success { background: #f0fdf4; color: #15803d; }
.agent-workspace-v2__alerts strong { color: #344054; font-size: 11px; }
.agent-workspace-v2__alerts p { margin: 2px 0 0; color: #667085; font-size: 11px; line-height: 16px; }
.agent-workspace-v2__next { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; padding-top: 2px; }
.agent-workspace-v2__next button { display: inline-flex; align-items: center; gap: 4px; padding: 5px 8px; border: 1px solid #d8e4f2; border-radius: 999px; background: #fff; color: #315f96; cursor: pointer; font-size: 11px; }
.agent-workspace-v2__next button:hover { border-color: #79b6ff; background: #f4f9ff; }
@media (max-width: 900px) { .agent-workspace-v2__evidence { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
