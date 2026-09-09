<template>
  <details v-if="tools.length" class="tool-evidence">
    <summary>
      <span><t-icon name="secured" /> 查询证据</span>
      <em>{{ successCount }} 项成功<span v-if="blockedCount">，{{ blockedCount }} 项受阻</span></em>
      <t-icon name="chevron-down" />
    </summary>
    <div class="tool-evidence__list">
      <div v-for="(tool, index) in tools" :key="`${tool.name}-${index}`" class="tool-evidence__item">
        <span class="tool-evidence__state" :class="`tool-evidence__state--${tool.status || 'planned'}`">
          <t-icon :name="toolIcon(tool)" />
        </span>
        <span class="tool-evidence__copy">
          <strong>{{ tool.displayName || '执行只读工具' }}</strong>
          <em>{{ tool.resultSummary || tool.summary || tool.reason || statusText(tool) }}</em>
        </span>
        <span class="tool-evidence__meta">
          <small v-if="tool.durationMs !== undefined">{{ tool.durationMs }}ms</small>
          <i>{{ tool.readonly === false ? '已拦截' : '只读' }}</i>
        </span>
      </div>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { AgentToolCall } from '../../agent/types';

const props = defineProps<{ tools?: AgentToolCall[] }>();
const tools = computed(() => props.tools || []);
const successCount = computed(() => tools.value.filter((tool) => tool.status === 'success').length);
const blockedCount = computed(() => tools.value.filter((tool) => ['failed', 'skipped'].includes(String(tool.status))).length);

function toolIcon(tool: AgentToolCall) {
  if (tool.status === 'success') return 'check';
  if (tool.status === 'failed') return 'close';
  if (tool.status === 'skipped') return 'info-circle';
  if (tool.status === 'running') return 'ellipsis';
  return 'time';
}

function statusText(tool: AgentToolCall) {
  if (tool.status === 'success') return '查询完成';
  if (tool.status === 'failed') return '查询失败';
  if (tool.status === 'skipped') return '当前条件不足，已跳过';
  if (tool.status === 'running') return '查询中';
  return '等待执行';
}
</script>

<style scoped>
.tool-evidence { margin-top: 10px; border-top: 1px solid #edf1f6; }
.tool-evidence summary { display: grid; grid-template-columns: minmax(0, 1fr) auto 18px; align-items: center; gap: 10px; padding: 12px 2px 6px; cursor: pointer; list-style: none; }
.tool-evidence summary::-webkit-details-marker { display: none; }
.tool-evidence summary span { display: inline-flex; align-items: center; gap: 6px; color: #34435c; font-size: 12px; font-weight: 600; }
.tool-evidence summary span .t-icon { color: #1677ff; }
.tool-evidence summary em { color: #8491a5; font-size: 11px; font-style: normal; }
.tool-evidence summary > .t-icon { color: #94a3b8; transition: transform .18s ease; }
.tool-evidence[open] summary > .t-icon { transform: rotate(180deg); }
.tool-evidence__list { display: grid; gap: 7px; padding: 6px 0 2px; }
.tool-evidence__item { display: grid; grid-template-columns: 24px minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 9px 10px; border: 1px solid #e7edf5; border-radius: 9px; background: #fbfcfe; }
.tool-evidence__state { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border-radius: 7px; background: #e8f2ff; color: #1677ff; font-size: 12px; }
.tool-evidence__state--success { background: #dcfce7; color: #15803d; }
.tool-evidence__state--failed { background: #fee2e2; color: #dc2626; }
.tool-evidence__state--skipped { background: #fff7ed; color: #d97706; }
.tool-evidence__copy { display: grid; min-width: 0; gap: 2px; }
.tool-evidence__copy strong { color: #27364f; font-size: 12px; }
.tool-evidence__copy em { overflow: hidden; color: #77859a; font-size: 11px; font-style: normal; line-height: 16px; text-overflow: ellipsis; white-space: nowrap; }
.tool-evidence__meta { display: flex; align-items: center; gap: 6px; }
.tool-evidence__meta small { color: #94a3b8; font-size: 10px; }
.tool-evidence__meta i { padding: 2px 6px; border-radius: 999px; background: #edf8f1; color: #15803d; font-size: 10px; font-style: normal; }
</style>
