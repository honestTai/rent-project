<template>
  <details v-if="visibleEvents.length" class="run-timeline" :open="status === 'thinking'">
    <summary>
      <span class="run-timeline__pulse" :class="`run-timeline__pulse--${tone}`"></span>
      <span class="run-timeline__summary-copy">
        <strong>{{ statusText }}</strong>
        <em>{{ summaryText }}</em>
      </span>
      <span class="run-timeline__count">{{ countText }}</span>
      <t-icon name="chevron-down" />
    </summary>
    <div class="run-timeline__list">
      <div
        v-for="event in visibleEvents"
        :key="`${event.runId}-${event.sequence}`"
        class="run-timeline__item"
        :class="`run-timeline__item--${eventTone(event)}`"
      >
        <span class="run-timeline__dot">
          <i v-if="event.type === 'tool.started'"></i>
          <t-icon v-else :name="eventIcon(event)" />
        </span>
        <span>
          <strong>{{ eventTitle(event) }}</strong>
          <em>{{ eventDetail(event) }}</em>
        </span>
        <small v-if="event.data.durationMs !== undefined">{{ event.data.durationMs }}ms</small>
      </div>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import { collapseRunEvents } from '../../agent/run-state';
import type { AgentRunEvent, AgentToolCall } from '../../agent/types';

const props = defineProps<{
  events?: AgentRunEvent[];
  tools?: AgentToolCall[];
  status?: string;
}>();

const visibleEvents = computed(() => collapseRunEvents(props.events || []));
const queryCount = computed(() => visibleEvents.value.filter((event) => event.type.startsWith('tool.')).length);
const countText = computed(() => queryCount.value ? `${queryCount.value} 项查询` : `${visibleEvents.value.length} 步`);
const blockedCount = computed(() => (props.tools || []).filter((tool) => ['failed', 'skipped'].includes(String(tool.status))).length);
const successCount = computed(() => (props.tools || []).filter((tool) => tool.status === 'success').length);
const tone = computed(() => {
  if (props.status === 'thinking') return 'running';
  if (props.status === 'error' || blockedCount.value) return 'warning';
  if (props.status === 'cancelled') return 'muted';
  return 'success';
});
const statusText = computed(() => {
  if (props.status === 'thinking') return '正在执行只读分析';
  if (props.status === 'cancelled') return '本次运行已停止';
  if (props.status === 'error') return '运行未完成';
  if (blockedCount.value) return '分析完成，部分证据不可用';
  return '分析过程已完成';
});
const summaryText = computed(() => {
  if (props.status === 'thinking') return '工具结果会实时显示，不会修改业务数据';
  if (blockedCount.value) return `${successCount.value} 项成功，${blockedCount.value} 项受阻`;
  return successCount.value ? `${successCount.value} 项只读工具执行成功` : '已结合当前对话生成结果';
});

function eventTone(event: AgentRunEvent) {
  if (event.type === 'tool.failed' || event.type === 'run.failed') return 'error';
  if (event.data.status === 'skipped') return 'warning';
  if (event.type === 'tool.completed' || event.type === 'run.completed') return 'success';
  if (event.type === 'run.cancelled') return 'muted';
  return 'running';
}

function eventIcon(event: AgentRunEvent) {
  if (eventTone(event) === 'success') return 'check';
  if (eventTone(event) === 'error') return 'close';
  if (eventTone(event) === 'warning') return 'info-circle';
  if (eventTone(event) === 'muted') return 'stop-circle';
  return 'ellipsis';
}

function eventTitle(event: AgentRunEvent) {
  if (event.type === 'run.started') return '理解问题与业务范围';
  if (event.type === 'plan.ready') return '生成只读执行计划';
  if (event.type.startsWith('tool.')) return String(event.data.displayName || '执行只读工具');
  if (event.type === 'run.completed') return '整理结论与后续建议';
  if (event.type === 'run.cancelled') return '停止本次运行';
  if (event.type === 'run.failed') return '运行异常';
  return '处理当前问题';
}

function eventDetail(event: AgentRunEvent) {
  if (event.type === 'plan.ready') {
    const tools = Array.isArray(event.data.tools) ? event.data.tools.length : 0;
    return tools ? `已安排 ${tools} 个查询步骤` : '无需调用业务工具';
  }
  if (event.type === 'tool.started') return '正在读取授权范围内的数据';
  if (event.type === 'tool.completed') return String(event.data.summary || '查询成功');
  if (event.type === 'tool.failed') return String(event.data.summary || '当前证据暂不可用');
  return String(event.data.message || '');
}
</script>

<style scoped>
.run-timeline {
  overflow: hidden;
  margin-top: 14px;
  border: 1px solid #dbe7f5;
  border-radius: 12px;
  background: #f8fbff;
}

.run-timeline summary {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto 18px;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  list-style: none;
}

.run-timeline summary::-webkit-details-marker { display: none; }
.run-timeline[open] summary > .t-icon { transform: rotate(180deg); }
.run-timeline summary > .t-icon { color: #7b8ba3; transition: transform 0.18s ease; }

.run-timeline__pulse {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #1677ff;
}
.run-timeline__pulse--running { box-shadow: 0 0 0 5px rgb(22 119 255 / 12%); animation: run-pulse 1.4s infinite; }
.run-timeline__pulse--success { background: #16a34a; }
.run-timeline__pulse--warning { background: #d97706; }
.run-timeline__pulse--muted { background: #94a3b8; }

.run-timeline__summary-copy { display: grid; gap: 2px; min-width: 0; }
.run-timeline__summary-copy strong { color: #13213a; font-size: 13px; }
.run-timeline__summary-copy em { overflow: hidden; color: #66758c; font-size: 12px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.run-timeline__count { padding: 2px 7px; border-radius: 999px; background: #e8f2ff; color: #1668dc; font-size: 11px; }

.run-timeline__list { display: grid; padding: 2px 14px 12px 20px; }
.run-timeline__item { position: relative; display: grid; grid-template-columns: 22px minmax(0, 1fr) auto; gap: 8px; min-height: 38px; padding: 5px 0; }
.run-timeline__item:not(:last-child)::after { position: absolute; top: 25px; bottom: -6px; left: 9px; width: 1px; background: #d8e3f0; content: ''; }
.run-timeline__dot { z-index: 1; display: inline-flex; width: 19px; height: 19px; align-items: center; justify-content: center; border-radius: 50%; background: #e8f2ff; color: #1677ff; font-size: 11px; }
.run-timeline__dot i { width: 7px; height: 7px; border: 2px solid rgb(22 119 255 / 25%); border-top-color: #1677ff; border-radius: 50%; animation: run-spin .8s linear infinite; }
.run-timeline__item--success .run-timeline__dot { background: #dcfce7; color: #15803d; }
.run-timeline__item--error .run-timeline__dot { background: #fee2e2; color: #dc2626; }
.run-timeline__item--warning .run-timeline__dot { background: #fff7ed; color: #d97706; }
.run-timeline__item--muted .run-timeline__dot { background: #e2e8f0; color: #64748b; }
.run-timeline__item > span:nth-child(2) { display: grid; gap: 2px; min-width: 0; }
.run-timeline__item strong { color: #26344d; font-size: 12px; font-weight: 600; }
.run-timeline__item em { overflow: hidden; color: #75839a; font-size: 11px; font-style: normal; line-height: 16px; text-overflow: ellipsis; white-space: nowrap; }
.run-timeline__item small { color: #94a3b8; font-size: 10px; }

@keyframes run-spin { to { transform: rotate(360deg); } }
@keyframes run-pulse { 50% { box-shadow: 0 0 0 2px rgb(22 119 255 / 8%); } }
</style>
