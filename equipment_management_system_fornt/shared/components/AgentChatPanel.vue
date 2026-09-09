<template>
  <div
    class="agent-floating"
    :class="[
      `agent-floating--${mode}`,
      { 'agent-floating--open': panelOpen || isPageMode },
    ]"
  >
    <p class="agent-sr-only" aria-live="polite" aria-atomic="true">
      {{ clarificationAnnouncement }}
    </p>
    <button
      v-if="!isPageMode && !panelOpen"
      class="agent-launcher"
      type="button"
      aria-label="打开 AI 助手"
      title="AI 助手"
      @click="openPanel"
    >
      <span class="agent-launcher__icon"><t-icon name="robot-2-filled" /></span>
      <span class="agent-launcher__text">
        <strong>AI 助手</strong>
        <em>{{ primaryTag }}</em>
      </span>
    </button>

    <section v-else class="agent-window" role="dialog" aria-label="AI 助手">
      <aside class="agent-history">
        <div v-if="!isPageMode" class="agent-brand">
          <span class="agent-brand__icon"><t-icon name="robot-2" /></span>
          <div>
            <h2>AI 助手</h2>
            <p>{{ subtitle }}</p>
          </div>
        </div>

        <t-button block theme="primary" variant="outline" @click="startNewConversation">
          <template #icon><t-icon name="add" /></template>
          新建对话
        </t-button>

        <div class="agent-history__head">
          <span>历史对话</span>
          <t-tag size="small" variant="light">{{ conversations.length }}</t-tag>
        </div>

        <div class="agent-conversations">
          <button
            v-if="!conversationId"
            class="agent-conversation agent-conversation--active"
            type="button"
          >
            <span class="agent-conversation__icon"><t-icon name="chat-add" /></span>
            <span class="agent-conversation__content">
              <strong>新对话</strong>
              <em>输入后自动保存</em>
            </span>
          </button>
          <button
            v-for="item in conversations"
            :key="item.id"
            class="agent-conversation"
            :class="{ 'agent-conversation--active': item.id === conversationId }"
            type="button"
            @click="selectConversation(item.id)"
          >
            <span class="agent-conversation__icon"><t-icon name="chat-message" /></span>
            <span class="agent-conversation__content">
              <strong>{{ item.title || '新对话' }}</strong>
              <em>{{ formatConversationTime(item.updatedAt) }}</em>
            </span>
            <span
              v-if="props.api.deleteConversation"
              class="agent-conversation__delete"
              role="button"
              tabindex="0"
              title="删除历史"
              aria-label="删除历史"
              @click.stop="deleteConversation(item.id)"
              @keydown.enter.stop.prevent="deleteConversation(item.id)"
            >
              <t-icon name="delete" />
            </span>
          </button>
          <div v-if="!conversations.length && conversationId" class="agent-empty-side">暂无历史会话</div>
        </div>
      </aside>

      <main class="agent-chat">
        <header class="agent-chat__header">
          <div>
            <span>业务只读助手</span>
            <h1>{{ title }}</h1>
          </div>
          <div class="agent-chat__meta">
            <t-tag variant="light" theme="primary">{{ primaryTag }}</t-tag>
            <t-button v-if="!isPageMode" shape="square" variant="text" aria-label="关闭 AI 助手" @click="closePanel">
              <t-icon name="close" />
            </t-button>
          </div>
        </header>

        <div
          ref="messageListRef"
          class="agent-messages"
          :class="{ 'agent-messages--empty': !messages.length }"
          @scroll="handleMessagesScroll"
        >
          <AgentWelcome
            v-if="!messages.length"
            :title="`你好，我是${title}`"
            :description="emptyText"
            :lanes="starterLanes"
            @select="applyPrompt"
          />

          <article
            v-for="message in messages"
            :key="message.id"
            class="agent-message"
            :class="`agent-message--${message.role}`"
          >
            <div class="agent-message__avatar">
              <t-icon :name="message.role === 'assistant' ? 'robot-2' : 'user-circle'" />
            </div>
            <div class="agent-message__body">
              <div
                v-if="shouldShowAgentProcess(message)"
                class="agent-run"
              >
                <div class="agent-run__head">
                  <span class="agent-run__status">
                    <i class="agent-run__spinner"></i>
                  </span>
                  <div>
                    <strong>思考中</strong>
                    <p>{{ thinkingText }}</p>
                  </div>
                </div>
                <div class="agent-thinking-card">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="agent-chart-skeleton">
                  <span><t-icon name="ai-chart-bar" /></span>
                  <div>
                    <i></i>
                    <i></i>
                    <i></i>
                  </div>
                </div>
              </div>

              <AgentQuestionPrompt
                v-if="isClarificationMessage(message)"
                :questions="message.questions || []"
                @select="applyQuestionOption"
              />

              <template v-else>
                <div
                  v-if="message.content"
                  class="agent-markdown"
                  v-html="renderAgentContent(message.content)"
                ></div>

                <AgentSourceCards v-if="message.sources?.length" :sources="message.sources" />

                <AgentRunTimeline
                  v-if="message.runEvents?.length"
                  :events="message.runEvents"
                  :tools="message.toolCalls"
                  :status="message.status"
                />

                <AgentWorkspaceSummary
                  v-if="message.workspace"
                  :workspace="message.workspace"
                  @select="applyPrompt"
                />

                <AgentToolEvidence
                  v-if="message.toolCalls?.length"
                  :tools="message.toolCalls"
                />

                <div v-if="message.charts?.length" class="agent-charts">
                  <section v-for="chart in message.charts" :key="`${message.id}-${chart.title}`" class="agent-chart-card">
                    <div class="agent-chart-card__head">
                      <h3>{{ chart.title }}</h3>
                      <t-tag size="small" variant="light" theme="primary">{{ isStatChart(chart) ? '结果指标' : '结果图表' }}</t-tag>
                    </div>
                    <p v-if="chart.summary" class="agent-chart-card__summary">{{ chart.summary }}</p>
                    <div v-if="isStatChart(chart)" class="agent-chart__stat">
                      <span>{{ statLabel(chart) }}</span>
                      <strong>{{ statValue(chart) }}</strong>
                    </div>
                    <component
                      :is="chartRenderer"
                      v-else-if="chartRenderer"
                      :type="chart.type"
                      :rows="chart.rows"
                      :x-key="chart.dimensions?.[0] || 'label'"
                      :value-keys="chartValueKeys(chart)"
                      :value-labels="seriesLabels(chart)"
                      :horizontal="shouldUseHorizontalChart(chart)"
                      height="260px"
                    />
                    <div v-else class="agent-chart__fallback">
                      <div v-if="!chartRows(chart).length" class="agent-chart__empty">暂无图表数据</div>
                      <template v-else-if="normalizeChartType(chart) === 'pie'">
                        <div class="agent-chart__pie-wrap">
                          <div class="agent-chart__pie" :style="{ background: pieGradient(chart) }"></div>
                          <div class="agent-chart__legend">
                            <div v-for="item in pieLegendRows(chart)" :key="item.label" class="agent-chart__legend-row">
                              <i :style="{ background: item.color }"></i>
                              <span>{{ item.label }}</span>
                              <strong>{{ item.value }}</strong>
                            </div>
                          </div>
                        </div>
                      </template>
                      <div v-else class="agent-chart__bars">
                        <div v-for="(row, index) in previewRows(chart)" :key="index" class="agent-chart__bar-row">
                          <span class="agent-chart__bar-label">{{ chartLabel(row, chart) }}</span>
                          <div class="agent-chart__bar-track">
                            <i :style="barStyle(row, chart)"></i>
                          </div>
                          <strong>{{ formatValue(row[primaryValueKey(chart)]) }}</strong>
                        </div>
                      </div>

                      <table v-if="chartColumns(chart).length" class="agent-chart__table">
                        <thead>
                          <tr>
                            <th v-for="column in chartColumns(chart)" :key="column.key">{{ column.label }}</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, index) in previewRows(chart)" :key="index">
                            <td v-for="column in chartColumns(chart)" :key="column.key">{{ formatValue(row[column.key]) }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </section>
                </div>
              </template>
            </div>
          </article>
          <div ref="messageEndRef" class="agent-message-end" aria-hidden="true"></div>
        </div>

        <form class="agent-input" @submit.prevent="sendMessage">
          <t-textarea
            v-model="draft"
            class="agent-input__textarea"
            :placeholder="composerPlaceholder"
            :autosize="{ minRows: 2, maxRows: 5 }"
            @keydown="handleTextareaKeydown"
          />
          <div class="agent-input__actions">
            <span>{{ inputTip }}</span>
            <t-button
              class="agent-send-button"
              :class="{ 'agent-send-button--running': loading }"
              shape="circle"
              theme="primary"
              :type="loading ? 'button' : 'submit'"
              :aria-label="loading ? '停止生成' : '发送消息'"
              :disabled="!loading && !draft.trim()"
              @click="handleSendButtonClick"
            >
              <template #icon><t-icon :name="loading ? 'stop-circle-filled' : 'arrow-up'" /></template>
            </t-button>
          </div>
        </form>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { MessagePlugin } from 'tdesign-vue-next';
import type { Component } from 'vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  DEFAULT_AGENT_INPUT_PLACEHOLDER,
  clarificationInputPlaceholder,
  extractAgentQuestions,
  isClarificationMessage,
  shouldShowAgentProcess,
} from '../agent/questions';
import { reduceRunEvent } from '../agent/run-state';
import { starterLanesForSystem } from '../agent/profiles';
import type { AgentApi, AgentMessage, AgentRunEvent, AgentSource, AgentToolCall, AnyRecord } from '../agent/types';
import AgentQuestionPrompt from './agent/AgentQuestionPrompt.vue';
import AgentRunTimeline from './agent/AgentRunTimeline.vue';
import AgentSourceCards from './agent/AgentSourceCards.vue';
import AgentToolEvidence from './agent/AgentToolEvidence.vue';
import AgentWelcome from './agent/AgentWelcome.vue';
import AgentWorkspaceSummary from './agent/AgentWorkspaceSummary.vue';

const props = withDefaults(
  defineProps<{
    system: string;
    title: string;
    subtitle: string;
    primaryTag: string;
    placeholder: string;
    inputTip: string;
    emptyText: string;
    api: AgentApi;
    chartRenderer?: Component;
    mode?: 'floating' | 'page';
  }>(),
  {
    chartRenderer: undefined,
    mode: 'floating',
    placeholder: DEFAULT_AGENT_INPUT_PLACEHOLDER,
  },
);

const loading = ref(false);
const draft = ref('');
const isPageMode = computed(() => props.mode === 'page');
const panelOpen = ref(props.mode === 'page');
const conversationsLoaded = ref(false);
const conversationId = ref('');
const conversations = ref<AnyRecord[]>([]);
const messages = ref<AgentMessage[]>([]);
const composerPlaceholder = computed(() => clarificationInputPlaceholder(messages.value, props.placeholder));
const clarificationAnnouncement = computed(() => {
  let latestAssistant: AgentMessage | undefined;
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (messages.value[index].role === 'assistant') {
      latestAssistant = messages.value[index];
      break;
    }
  }
  if (!latestAssistant || !isClarificationMessage(latestAssistant)) return '';

  const questionText = latestAssistant.questions
    ?.filter((question) => question.inferred !== true)
    .map((question) => question.question.trim())
    .filter(Boolean)
    .join('；');
  return questionText ? `需要补充信息：${questionText}` : '';
});
const messageListRef = ref<HTMLDivElement | null>(null);
const messageEndRef = ref<HTMLDivElement | null>(null);
const scrollTimers: number[] = [];
const shouldStickToBottom = ref(true);
const chartPalette = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2'];
const starterLanes = computed(() => starterLanesForSystem(props.system));
const thinkingStepIndex = ref(0);
const thinkingTexts = [
  '正在理解你的问题和当前业务范围。',
  '正在按只读权限整理可用数据。',
  '正在避免写操作、扣款、同步等高风险动作。',
  '正在把结论整理成可直接处理的建议。',
];
const thinkingText = computed(() => thinkingTexts[thinkingStepIndex.value % thinkingTexts.length]);
const activeAssistantId = ref('');
const activeRunId = ref('');
let activeAbortController: AbortController | null = null;
const cancelledAssistantIds = new Set<string>();
let thinkingTimer: number | null = null;

const agentData = (response: AnyRecord, fallback: AnyRecord = {}) => response?.data?.data ?? response?.data ?? fallback;

function renderAgentContent(content: string) {
  const lines = String(content || '').replace(/\r\n/g, '\n').split('\n');
  const blocks: string[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }

    if (isMarkdownTableStart(lines, index)) {
      const { html, nextIndex } = renderMarkdownTable(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    if (isFenceStart(line)) {
      const { html, nextIndex } = renderFencedCode(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    if (isSqlStart(line)) {
      const { html, nextIndex } = renderSqlBlock(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    const sectionMatch = parseAgentSection(line);
    if (sectionMatch) {
      blocks.push(`<h3>${escapeHtml(sectionMatch.title)}</h3>`);
      if (sectionMatch.content) {
        blocks.push(`<p>${renderInlineMarkdown(sectionMatch.content)}</p>`);
      }
      index += 1;
      continue;
    }

    const headingMatch = line.match(/^(#{1,3})\s+(.+)$/);
    if (headingMatch) {
      const level = Math.min(headingMatch[1].length + 2, 5);
      blocks.push(`<h${level}>${renderInlineMarkdown(headingMatch[2])}</h${level}>`);
      index += 1;
      continue;
    }

    if (/^\s*(?:[-*]\s+|\d+\.\s+)/.test(line)) {
      const { html, nextIndex } = renderMarkdownList(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    const paragraphLines: string[] = [];
    while (
      index < lines.length &&
      lines[index].trim() &&
      !isMarkdownTableStart(lines, index) &&
      !isFenceStart(lines[index]) &&
      !isSqlStart(lines[index]) &&
      !parseAgentSection(lines[index]) &&
      !/^(#{1,3})\s+/.test(lines[index]) &&
      !/^\s*(?:[-*]\s+|\d+\.\s+)/.test(lines[index])
    ) {
      paragraphLines.push(lines[index]);
      index += 1;
    }
    blocks.push(`<p>${paragraphLines.map((item) => renderInlineMarkdown(item)).join('<br />')}</p>`);
  }

  return blocks.join('');
}

function parseAgentSection(line: string) {
  const match = String(line || '').trim().match(/^(结论|证据|判断理由|后续建议|后续规划|数据口径|查询依据|结果|摘要|来源|判断|可能原因)[:：]\s*(.*)$/);
  if (!match) return null;
  return { title: match[1], content: match[2] || '' };
}

function isFenceStart(line: string) {
  return /^```/.test(String(line || '').trim());
}

function renderFencedCode(lines: string[], startIndex: number) {
  const first = String(lines[startIndex] || '').trim();
  const language = first.replace(/^```/, '').trim() || 'text';
  const codeLines: string[] = [];
  let index = startIndex + 1;
  while (index < lines.length && !isFenceStart(lines[index])) {
    codeLines.push(lines[index]);
    index += 1;
  }
  if (index < lines.length) index += 1;
  return {
    html: `<pre class="agent-markdown__code"><code data-language="${escapeHtml(language)}">${escapeHtml(codeLines.join('\n'))}</code></pre>`,
    nextIndex: index,
  };
}

function isSqlStart(line: string) {
  return /^\s*(SELECT|WITH)\b/i.test(String(line || ''));
}

function renderSqlBlock(lines: string[], startIndex: number) {
  const codeLines: string[] = [];
  let index = startIndex;
  while (
    index < lines.length &&
    lines[index].trim() &&
    !parseAgentSection(lines[index]) &&
    !isFenceStart(lines[index])
  ) {
    codeLines.push(lines[index]);
    index += 1;
  }
  return {
    html: `<pre class="agent-markdown__code"><code data-language="sql">${escapeHtml(codeLines.join('\n'))}</code></pre>`,
    nextIndex: index,
  };
}

function isMarkdownTableStart(lines: string[], index: number) {
  const header = lines[index] || '';
  const divider = lines[index + 1] || '';
  const headerCells = splitTableRow(header);
  const dividerCells = splitTableRow(divider);
  return headerCells.length > 1 && dividerCells.length > 1 && dividerCells.every((cell) => /^:?-{3,}:?$/.test(cell.replace(/\s/g, '')));
}

function renderMarkdownTable(lines: string[], startIndex: number) {
  const headers = splitTableRow(lines[startIndex]);
  const rows: string[][] = [];
  let index = startIndex + 2;
  while (index < lines.length) {
    const cells = splitTableRow(lines[index]);
    if (cells.length <= 1) break;
    rows.push(cells);
    index += 1;
  }
  const head = headers.map((cell) => `<th>${renderInlineMarkdown(cell)}</th>`).join('');
  const body = rows
    .map((row) => `<tr>${headers.map((_, cellIndex) => `<td>${renderInlineMarkdown(row[cellIndex] || '')}</td>`).join('')}</tr>`)
    .join('');
  return {
    html: `<div class="agent-markdown__table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`,
    nextIndex: index,
  };
}

function splitTableRow(line: string) {
  const text = String(line || '').trim();
  if (!text.includes('|')) return [];
  return text.replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
}

function renderMarkdownList(lines: string[], startIndex: number) {
  const ordered = /^\s*\d+\.\s+/.test(lines[startIndex]);
  const items: string[] = [];
  let index = startIndex;
  const pattern = ordered ? /^\s*\d+\.\s+(.+)$/ : /^\s*[-*]\s+(.+)$/;
  while (index < lines.length) {
    const match = lines[index].match(pattern);
    if (!match) break;
    items.push(`<li>${renderInlineMarkdown(match[1])}</li>`);
    index += 1;
  }
  const tag = ordered ? 'ol' : 'ul';
  return { html: `<${tag}>${items.join('')}</${tag}>`, nextIndex: index };
}

function renderInlineMarkdown(content: string) {
  let html = escapeHtml(content);
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
  html = html.replace(/(^|[\s>])(https?:\/\/[^\s<]+)/g, '$1<a href="$2" target="_blank" rel="noreferrer">$2</a>');
  return html;
}

function escapeHtml(value: string) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

watch(
  () => [panelOpen.value, messages.value.length],
  () => {
    if (shouldStickToBottom.value) {
      void scrollToBottom();
    }
  },
  { flush: 'post' },
);

onBeforeUnmount(() => {
  activeAbortController?.abort();
  clearScrollTimers();
  stopThinkingLoop();
});

onMounted(() => {
  if (isPageMode.value) {
    void openPanel();
  }
});

async function loadConversations() {
  const data = agentData(await props.api.listConversations(props.system), { items: [] });
  conversations.value = Array.isArray(data.items) ? data.items : [];
  conversationsLoaded.value = true;
}

async function openPanel() {
  panelOpen.value = true;
  if (!conversationsLoaded.value) {
    try {
      await loadConversations();
    } catch (error) {
      conversations.value = [];
      conversationsLoaded.value = true;
    }
  }
  await scrollToBottom();
}

function closePanel() {
  if (isPageMode.value) return;
  panelOpen.value = false;
  stopThinkingLoop();
}

async function selectConversation(id: string) {
  conversationId.value = id;
  const data = agentData(await props.api.getConversation(id, props.system), { messages: [] });
  messages.value = normalizeMessages(data.messages, id);
  shouldStickToBottom.value = true;
  await scrollToBottom();
}

function startNewConversation() {
  conversationId.value = '';
  messages.value = [];
  shouldStickToBottom.value = true;
  stopThinkingLoop();
}

async function deleteConversation(id: string) {
  if (!props.api.deleteConversation) return;
  try {
    const data = agentData(await props.api.deleteConversation(id, props.system), { deleted: false });
    if (!data.deleted) {
      MessagePlugin.warning('历史记录不存在或已删除');
      return;
    }
    conversations.value = conversations.value.filter((item) => item.id !== id);
    if (conversationId.value === id) {
      startNewConversation();
    }
    MessagePlugin.success('历史记录已删除');
  } catch (error) {
    MessagePlugin.error(error instanceof Error ? error.message : '删除历史失败');
  }
}

async function sendMessage() {
  const content = draft.value.trim();
  if (!content || loading.value) return;
  const assistantId = `assistant-${Date.now()}`;
  const request = {
    message: content,
    conversationId: conversationId.value || undefined,
    system: props.system,
  };
  cancelledAssistantIds.delete(assistantId);
  activeAssistantId.value = assistantId;
  activeRunId.value = '';
  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content,
  });
  messages.value.push({
    id: assistantId,
    role: 'assistant',
    content: '',
    status: 'thinking',
    runEvents: [],
    toolCalls: [],
  });
  shouldStickToBottom.value = true;
  draft.value = '';
  startThinkingLoop();
  await scrollToBottom();

  loading.value = true;
  let streamEventCount = 0;
  activeAbortController = new AbortController();
  try {
    if (props.api.streamMessage) {
      await props.api.streamMessage(
        request,
        {
          onEvent: (event) => {
            streamEventCount += 1;
            handleRunEvent(assistantId, event);
          },
        },
        activeAbortController.signal,
      );
      const finalMessage = messages.value.find((message) => message.id === assistantId);
      if (!cancelledAssistantIds.has(assistantId) && finalMessage?.status === 'thinking') {
        throw new Error('流式响应提前结束，未收到完成事件');
      }
    } else {
      await sendSynchronousMessage(assistantId, content, request);
    }
    if (cancelledAssistantIds.has(assistantId)) return;
    await loadConversations();
    await scrollToBottom();
  } catch (error) {
    if (cancelledAssistantIds.has(assistantId)) return;
    if (props.api.streamMessage && streamEventCount === 0 && !isAbortError(error)) {
      try {
        await sendSynchronousMessage(assistantId, content, request);
        await loadConversations();
        await scrollToBottom();
        return;
      } catch (fallbackError) {
        error = fallbackError;
      }
    }
    if (isAbortError(error)) return;
    replaceMessage(assistantId, {
      id: assistantId,
      role: 'assistant',
      status: 'error',
      content: error instanceof Error ? error.message : 'AI 助手请求失败',
    });
    MessagePlugin.error(error instanceof Error ? error.message : 'AI 助手请求失败');
  } finally {
    if (activeAssistantId.value === assistantId) {
      loading.value = false;
      activeAssistantId.value = '';
      activeRunId.value = '';
      activeAbortController = null;
      stopThinkingLoop();
    }
  }
}

async function sendSynchronousMessage(assistantId: string, content: string, request: AnyRecord) {
  const data = agentData(await props.api.sendMessage(request));
  if (cancelledAssistantIds.has(assistantId)) return;
  conversationId.value = data.conversationId || conversationId.value;
  replaceMessage(assistantId, {
    id: assistantId,
    role: 'assistant',
    content: String(data.answer || data.content || data.message || '未返回分析结果'),
    status: 'complete',
    toolCalls: extractToolCalls(data),
    charts: resolveCharts(data, content),
    sources: extractSources(data),
    workspace: extractWorkspace(data),
    questions: extractAgentQuestions(data),
  });
}

function handleRunEvent(assistantId: string, event: AgentRunEvent) {
  if (cancelledAssistantIds.has(assistantId)) return;
  if (event.runId) activeRunId.value = event.runId;
  const current = messages.value.find((message) => message.id === assistantId);
  if (!current) return;
  replaceMessage(assistantId, reduceRunEvent(current, event));
  if (event.type === 'run.completed') {
    conversationId.value = String(event.data.conversationId || conversationId.value);
  }
  shouldStickToBottom.value = true;
  void scrollToBottom();
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
}

function handleSendButtonClick(event: MouseEvent) {
  if (!loading.value) return;
  event.preventDefault();
  void cancelCurrentRun();
}

async function cancelCurrentRun() {
  const id = activeAssistantId.value;
  if (!id) return;
  cancelledAssistantIds.add(id);
  const current = messages.value.find((message) => message.id === id);
  if (current) {
    replaceMessage(id, {
      ...current,
      status: 'cancelled',
      content: '已停止本次运行。你可以补充条件后重新发送。',
    });
  }
  try {
    if (activeRunId.value && props.api.cancelRun) {
      await props.api.cancelRun(activeRunId.value);
    }
  } catch (error) {
    MessagePlugin.warning(error instanceof Error ? error.message : '后端停止请求未确认');
  } finally {
    activeAbortController?.abort();
  }
  loading.value = false;
  activeAssistantId.value = '';
  activeRunId.value = '';
  stopThinkingLoop();
}

function handleTextareaKeydown(_value: unknown, context?: { e?: KeyboardEvent }) {
  const event = context?.e;
  if (!event || event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
  event.preventDefault();
  void sendMessage();
}

function replaceMessage(id: string, next: AgentMessage) {
  const index = messages.value.findIndex((message) => message.id === id);
  if (index >= 0) {
    messages.value.splice(index, 1, next);
    return;
  }
  messages.value.push(next);
}

function startThinkingLoop() {
  stopThinkingLoop();
  thinkingStepIndex.value = 0;
  thinkingTimer = window.setInterval(() => {
    thinkingStepIndex.value = (thinkingStepIndex.value + 1) % 4;
  }, 1100);
}

function stopThinkingLoop() {
  if (thinkingTimer === null) return;
  window.clearInterval(thinkingTimer);
  thinkingTimer = null;
}

function normalizeMessages(value: unknown, idPrefix: string): AgentMessage[] {
  return toRecordArray(value).map((item, index) => ({
    id: `${idPrefix}-${index}`,
    role: item.role === 'assistant' ? 'assistant' as const : 'user' as const,
    content: String(item.content || item.answer || ''),
    status: 'complete' as const,
    toolCalls: extractToolCalls(item),
    charts: resolveCharts(item),
    sources: extractSources(item),
    workspace: extractWorkspace(item),
    questions: extractAgentQuestions(item),
  }));
}

function toRecordArray(value: unknown): AnyRecord[] {
  return Array.isArray(value) ? (value as AnyRecord[]) : [];
}

function extractToolCalls(source: AnyRecord): AgentToolCall[] {
  return toRecordArray(source.toolCalls || source.tool_calls || source.metadata?.toolCalls || source.extra?.toolCalls)
    .map((tool, index) => ({
      ...tool,
      name: String(tool.name || tool.tool || `agent-tool-${index}`),
    }));
}

function extractCharts(source: AnyRecord) {
  return toRecordArray(source.charts || source.metadata?.charts || source.extra?.charts || source.report?.charts);
}

function extractSources(source: AnyRecord): AgentSource[] {
  const rows = toRecordArray(source.sources || source.metadata?.sources || source.extra?.sources);
  return rows
    .map((item, index) => ({
      title: String(item.title || item.name || '网页结果'),
      url: String(item.url || item.link || ''),
      snippet: item.snippet ? String(item.snippet) : undefined,
      source: item.source ? String(item.source) : undefined,
      rank: Number.isFinite(Number(item.rank)) ? Number(item.rank) : index + 1,
    }))
    .filter((item) => item.url.startsWith('https://'));
}

function extractWorkspace(source: AnyRecord) {
  const workspace = source.workspace || source.metadata?.workspace || source.extra?.workspace || {};
  return workspace && typeof workspace === 'object' ? workspace : {};
}

function applyQuestionOption(option: string | AnyRecord) {
  const text = typeof option === 'string'
    ? option
    : String(option.value || option.label || option.title || option.text || '');
  const shouldFocus = typeof window.matchMedia === 'function'
    && window.matchMedia('(pointer: fine)').matches;
  applyPrompt(text, shouldFocus);
}

function applyPrompt(text: unknown, focusTextarea = true) {
  const value = String(text || '').trim();
  if (!value) return;
  draft.value = value;
  shouldStickToBottom.value = true;
  if (!focusTextarea) return;
  void nextTick(() => {
    const textarea = messageListRef.value?.parentElement?.querySelector('textarea');
    if (textarea instanceof HTMLTextAreaElement) textarea.focus();
  });
}

function resolveCharts(source: AnyRecord, userText = '') {
  const charts = extractCharts(source);
  return charts.length ? charts : inferCharts(source, userText);
}

function inferCharts(source: AnyRecord, userText = '') {
  const text = collectChartText(source, userText);
  if (!/图表|趋势|折线|柱状|饼图|可视化|订单数|收入|收益|金额/.test(text)) return [];

  const chartsFromRows = inferChartsFromRows(source);
  if (chartsFromRows.length) return chartsFromRows;

  const overviewRows = inferOverviewRows(text);
  if (!overviewRows.length) return [];

  return [
    {
      title: /今天|今日/.test(text) ? '今日收入概览' : '经营数据概览',
      type: 'bar',
      dimensions: ['label'],
      series: [{ key: 'value', name: '数值' }],
      rows: overviewRows,
      sourceTool: 'agent-summary',
    },
  ];
}

function collectChartText(source: AnyRecord, userText = '') {
  const toolText = extractToolCalls(source)
    .map((tool) => [tool.name, tool.summary, tool.reason].filter(Boolean).join(' '))
    .join(' ');
  return [userText, source.answer, source.content, source.message, toolText].filter(Boolean).join(' ');
}

function inferOverviewRows(text: string) {
  const rows: AnyRecord[] = [];
  const orderCount = matchNumber(text, /订单数\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)/);
  const amount = matchNumber(text, /(?:收益\/金额|收益金额|收入金额|金额|收入|收益)\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)/);

  if (orderCount !== null) rows.push({ label: '订单数', value: orderCount });
  if (amount !== null) rows.push({ label: '收入金额', value: amount });
  return rows;
}

function matchNumber(text: string, pattern: RegExp) {
  const matched = text.match(pattern);
  if (!matched) return null;
  const value = Number(matched[1]);
  return Number.isFinite(value) ? value : null;
}

function inferChartsFromRows(source: AnyRecord) {
  const toolCalls = extractToolCalls(source);
  const charts: AnyRecord[] = [];
  for (const tool of toolCalls) {
    const payload = tool.result || tool.data || tool.output || tool.payload || tool.response;
    const chart = inferChartFromPayload(payload, tool.name);
    if (chart) charts.push(chart);
    if (charts.length >= 2) break;
  }
  return charts;
}

function inferChartFromPayload(payload: unknown, sourceTool = 'agent') {
  if (!payload || typeof payload !== 'object') return null;
  const rowSet = findBestRows(payload as AnyRecord);
  if (!rowSet?.rows.length) return null;
  const columns = Object.keys(rowSet.rows[0] || {});
  const dimension = columns.find((key) => /date|day|time|period|label|name|source|channel/i.test(key)) || columns[0] || 'label';
  const values = columns
    .filter((key) => key !== dimension && rowSet.rows.some((row) => Number.isFinite(Number(row[key]))))
    .slice(0, 3);
  if (!values.length) return null;

  return {
    title: rowSet.title,
    type: /trend|date|day|time|period/i.test(rowSet.key) ? 'line' : 'bar',
    dimensions: [dimension],
    series: values.map((key) => ({ key, name: metricLabel(key) })),
    rows: rowSet.rows,
    sourceTool,
  };
}

function findBestRows(payload: AnyRecord) {
  const matches: Array<{ key: string; rows: AnyRecord[]; title: string; score: number }> = [];

  const walk = (value: unknown, path: string[] = []) => {
    if (Array.isArray(value)) {
      const rows = value.filter((item) => item && typeof item === 'object') as AnyRecord[];
      if (rows.length && Object.values(rows[0]).some((item) => Number.isFinite(Number(item)))) {
        const key = path.join('.');
        matches.push({ key, rows, title: chartTitleFromKey(key), score: chartScore(key, rows) });
      }
      return;
    }
    if (!value || typeof value !== 'object') return;
    Object.entries(value as AnyRecord).forEach(([key, next]) => walk(next, [...path, key]));
  };

  walk(payload);
  matches.sort((left, right) => right.score - left.score);
  return matches[0];
}

function chartScore(key: string, rows: AnyRecord[]) {
  let score = Math.min(rows.length, 12);
  if (/trend|date|day|time|period/i.test(key)) score += 20;
  if (/source|channel|rank|top/i.test(key)) score += 12;
  if (/row|list/i.test(key)) score += 4;
  return score;
}

function chartTitleFromKey(key: string) {
  if (/trend|date|day|time|period/i.test(key)) return '趋势图表';
  if (/source|channel/i.test(key)) return '渠道分布';
  if (/rank|top/i.test(key)) return '排行图表';
  return '数据图表';
}

function metricLabel(key: string) {
  const labels: Record<string, string> = {
    date: '日期',
    day: '日期',
    label: '名称',
    name: '名称',
    deviceName: '设备',
    goodsTitle: '商品',
    amount: '金额',
    totalAmount: '金额',
    revenue: '收入',
    revenueYuan: '收入',
    totalRevenue: '收入',
    totalSellAmount: '销售额',
    income: '收入',
    profit: '利润',
    totalProfit: '利润',
    profitRate: '利润率',
    orderCount: '订单数',
    sellOrderCount: '订单数',
    sell_order_count: '订单数',
    count: '数量',
    metric: '指标',
    metric_value: '指标值',
    value: '数值',
  };
  return labels[key] || key;
}

function seriesLabels(chart: AnyRecord) {
  return (chart.series || []).reduce((labels: Record<string, string>, item: AnyRecord) => {
    labels[item.key] = item.name || item.key;
    return labels;
  }, {});
}

function chartValueKeys(chart: AnyRecord) {
  const keys = (Array.isArray(chart.series) ? chart.series : [])
    .map((item: AnyRecord) => String(item.key || ''))
    .filter(Boolean);
  return keys.length ? keys : ['value'];
}

function chartRows(chart: AnyRecord) {
  return toRecordArray(chart.rows);
}

function chartColumns(chart: AnyRecord) {
  const rows = chartRows(chart);
  const displayColumns = toRecordArray(chart.displayColumns)
    .map((column) => ({
      key: String(column.key || ''),
      label: String(column.label || column.key || ''),
    }))
    .filter((column) => column.key && column.label);
  if (displayColumns.length) return displayColumns.slice(0, 6);
  return Object.keys(rows[0] || {})
    .slice(0, 6)
    .map((key) => ({ key, label: metricLabel(key) }));
}

function previewRows(chart: AnyRecord) {
  return chartRows(chart).slice(0, 6);
}

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function formatConversationTime(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(
    date.getMinutes(),
  ).padStart(2, '0')}`;
}

function normalizeChartType(chart: AnyRecord) {
  const type = String(chart.type || chart.chartType || 'bar').toLowerCase();
  return ['line', 'bar', 'pie', 'stat'].includes(type) ? type : 'bar';
}

function isStatChart(chart: AnyRecord) {
  if (normalizeChartType(chart) === 'stat') return true;
  const rows = chartRows(chart);
  const values = chartValueKeys(chart);
  return rows.length === 1 && values.length === 1 && Number.isFinite(Number(rows[0]?.[values[0]]));
}

function statLabel(chart: AnyRecord) {
  const key = primaryValueKey(chart);
  return chart.series?.[0]?.name || metricLabel(key);
}

function statValue(chart: AnyRecord) {
  const row = chartRows(chart)[0] || {};
  return formatValue(row[primaryValueKey(chart)]);
}

function shouldUseHorizontalChart(chart: AnyRecord) {
  return normalizeChartType(chart) === 'bar' && chartRows(chart).length > 4;
}

function primaryValueKey(chart: AnyRecord) {
  return chartValueKeys(chart)[0] || 'value';
}

function chartLabel(row: AnyRecord, chart: AnyRecord) {
  const dimension = chart.dimensions?.[0] || chart.xKey || 'label';
  return formatValue(row[dimension] || row.label || row.name || row.date || row.periodKey);
}

function numberValue(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function chartMax(chart: AnyRecord) {
  const key = primaryValueKey(chart);
  return Math.max(1, ...chartRows(chart).map((row) => Math.abs(numberValue(row[key]))));
}

function barStyle(row: AnyRecord, chart: AnyRecord) {
  const percent = Math.max(4, Math.min(100, Math.round((Math.abs(numberValue(row[primaryValueKey(chart)])) / chartMax(chart)) * 100)));
  return { width: `${percent}%` };
}

function pieGradient(chart: AnyRecord) {
  const rows = previewRows(chart);
  const key = primaryValueKey(chart);
  const total = rows.reduce((sum, row) => sum + Math.max(0, numberValue(row[key])), 0);
  if (!total) return 'conic-gradient(#e5e7eb 0% 100%)';

  let start = 0;
  const segments = rows.map((row, index) => {
    const value = Math.max(0, numberValue(row[key]));
    const end = start + (value / total) * 100;
    const segment = `${chartPalette[index % chartPalette.length]} ${start}% ${end}%`;
    start = end;
    return segment;
  });
  return `conic-gradient(${segments.join(', ')})`;
}

function pieLegendRows(chart: AnyRecord) {
  const key = primaryValueKey(chart);
  return previewRows(chart).map((row, index) => ({
    label: chartLabel(row, chart),
    value: formatValue(row[key]),
    color: chartPalette[index % chartPalette.length],
  }));
}

function handleMessagesScroll() {
  const el = messageListRef.value;
  if (!el) return;
  shouldStickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}

async function scrollToBottom() {
  await nextTick();
  clearScrollTimers();
  scheduleScrollToBottom();
  scrollTimers.push(window.setTimeout(scheduleScrollToBottom, 80));
  scrollTimers.push(window.setTimeout(scheduleScrollToBottom, 240));
  scrollTimers.push(window.setTimeout(scheduleScrollToBottom, 520));
}

function scheduleScrollToBottom() {
  window.requestAnimationFrame(() => {
    const el = messageListRef.value;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  });
}

function clearScrollTimers() {
  while (scrollTimers.length) {
    const timer = scrollTimers.pop();
    if (timer !== undefined) {
      window.clearTimeout(timer);
    }
  }
}
</script>

<style scoped>
.agent-floating {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3500;
  pointer-events: none;
}

.agent-floating--page {
  position: static;
  z-index: auto;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  pointer-events: auto;
}

.agent-floating--open {
  inset: 72px 24px 24px;
  display: flex;
  align-items: stretch;
  justify-content: center;
}

.agent-floating--page.agent-floating--open {
  inset: auto;
  display: block;
}

.agent-launcher {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 144px;
  padding: 10px 14px 10px 10px;
  border: 1px solid rgb(22 119 255 / 20%);
  border-radius: 8px;
  background: #1677ff;
  box-shadow: 0 16px 34px rgb(22 119 255 / 24%);
  color: #fff;
  cursor: pointer;
  pointer-events: auto;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.agent-launcher:hover {
  background: #4096ff;
  box-shadow: 0 18px 40px rgb(22 119 255 / 30%);
  transform: translateY(-2px);
}

.agent-launcher__icon,
.agent-brand__icon,
.agent-welcome span,
.agent-message__avatar,
.agent-conversation__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.agent-launcher__icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: rgb(255 255 255 / 20%);
  font-size: 20px;
}

.agent-launcher__text {
  display: grid;
  gap: 2px;
  text-align: left;
}

.agent-launcher__text strong {
  font-size: 14px;
  font-weight: 700;
  line-height: 18px;
}

.agent-launcher__text em {
  color: rgb(255 255 255 / 78%);
  font-size: 11px;
  font-style: normal;
  line-height: 14px;
}

.agent-window {
  display: grid;
  width: min(1280px, 100%);
  height: 100%;
  min-height: 0;
  grid-template-columns: 270px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #eef0f4;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 48px rgb(15 23 42 / 14%);
  pointer-events: auto;
}

.agent-floating--page .agent-window {
  width: 100%;
  height: 100%;
  min-height: 0;
  grid-template-columns: 248px minmax(0, 1fr);
  border-color: #dde6f0;
  border-radius: 16px;
  box-shadow: 0 18px 45px rgb(30 64 104 / 9%);
}

.agent-history {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  border-right: 1px solid #eef0f4;
  background: #fff;
}

.agent-floating--page .agent-history {
  gap: 14px;
  padding: 16px 13px;
  background: linear-gradient(180deg, #f8fafc, #f3f6fa);
}

.agent-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
}

.agent-brand__icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 22px;
}

.agent-brand h2,
.agent-chat__header h1,
.agent-welcome h2,
.agent-chart-card__head h3 {
  margin: 0;
  color: #1f2329;
  font-weight: 600;
}

.agent-brand h2 {
  font-size: 18px;
  line-height: 24px;
}

.agent-brand p,
.agent-history__head,
.agent-empty-side,
.agent-chat__header span,
.agent-welcome p,
.agent-input__actions span,
.agent-tool span,
.agent-conversation__content em {
  color: #8c8c8c;
  font-size: 12px;
}

.agent-brand p {
  margin: 2px 0 0;
}

.agent-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px 0;
  font-weight: 600;
}

.agent-conversations {
  display: grid;
  min-height: 0;
  align-content: flex-start;
  gap: 8px;
  overflow: auto;
  padding-right: 2px;
}

.agent-conversation {
  display: grid;
  width: 100%;
  grid-template-columns: 20px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 9px;
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.agent-conversation:hover,
.agent-conversation--active {
  background: #f0f0f0;
}

.agent-conversation__delete {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #a6a6a6;
  opacity: 0;
  transition:
    background 0.18s ease,
    color 0.18s ease,
    opacity 0.18s ease;
}

.agent-conversation:hover .agent-conversation__delete,
.agent-conversation--active .agent-conversation__delete,
.agent-conversation__delete:focus-visible {
  opacity: 1;
}

.agent-conversation__delete:hover {
  background: #fff1f0;
  color: #d54941;
}

.agent-conversation__icon {
  color: #8c8c8c;
  font-size: 16px;
}

.agent-conversation__content {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.agent-conversation strong {
  overflow: hidden;
  color: #262626;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-conversation__content em {
  overflow: hidden;
  font-style: normal;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-empty-side {
  padding: 10px 8px;
}

.agent-chat {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: #fff;
}

.agent-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  clip-path: inset(50%);
  border: 0;
  white-space: nowrap;
}

.agent-floating--page .agent-chat {
  grid-template-rows: minmax(0, 1fr) auto;
  background:
    radial-gradient(circle at 78% 12%, rgb(22 119 255 / 5%), transparent 28%),
    #fff;
}

.agent-chat__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 72px;
  padding: 14px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.agent-floating--page .agent-chat__header {
  display: none;
}

.agent-chat__header span {
  color: #1677ff;
  font-weight: 600;
}

.agent-chat__header h1 {
  margin-top: 4px;
  font-size: 18px;
  line-height: 24px;
}

.agent-chat__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.agent-messages {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 18px;
  overflow-y: auto;
  overflow-x: hidden;
  overflow-anchor: none;
  padding: 24px 30px;
  background: #fff;
}

.agent-floating--page .agent-messages {
  display: grid;
  grid-template-columns: minmax(0, 960px);
  align-content: flex-start;
  justify-content: center;
  gap: 16px;
  padding: 28px 40px 18px;
  background: transparent;
}

.agent-floating--page .agent-messages--empty {
  align-content: center;
}

.agent-floating--page .agent-message,
.agent-floating--page .agent-message-end {
  width: 100%;
}

.agent-floating--page .agent-message--assistant {
  justify-self: stretch;
}

.agent-floating--page .agent-message--user {
  width: min(600px, 68%);
  justify-self: end;
}

.agent-floating--page .agent-message--assistant .agent-message__body {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.agent-floating--page .agent-message__avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 18px;
}

.agent-floating--page .agent-message {
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
}

.agent-floating--page .agent-message--user {
  grid-template-columns: minmax(0, 1fr) 34px;
}

.agent-messages--empty {
  justify-content: center;
}

.agent-message-end {
  width: 1px;
  height: 1px;
  flex: 0 0 auto;
}

.agent-welcome {
  display: grid;
  place-items: center;
  align-self: center;
  max-width: 620px;
  margin: auto;
  padding: 24px;
  color: #344054;
  text-align: center;
}

.agent-floating--page .agent-welcome {
  max-width: 760px;
  padding: 16px 24px 10px;
}

.agent-floating--page .agent-welcome span {
  width: 44px;
  height: 44px;
  margin-bottom: 10px;
  border-radius: 12px;
}

.agent-floating--page .agent-welcome h2 {
  font-size: 28px;
  line-height: 38px;
}

.agent-floating--page .agent-welcome p,
.agent-floating--page .agent-welcome__lanes {
  display: none;
}

.agent-welcome span {
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border-radius: 8px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 28px;
}

.agent-welcome h2 {
  font-size: 30px;
  line-height: 38px;
}

.agent-welcome p {
  max-width: 520px;
  margin: 10px 0 0;
  line-height: 20px;
}

.agent-welcome__lanes {
  display: grid;
  width: min(760px, 100%);
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 22px;
}

.agent-welcome__lanes button {
  display: grid;
  min-width: 0;
  min-height: 112px;
  align-content: flex-start;
  gap: 7px;
  padding: 13px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.agent-welcome__lanes button:hover {
  border-color: #1677ff;
  background: #f8fbff;
}

.agent-welcome__lanes .t-icon {
  color: #1677ff;
  font-size: 18px;
}

.agent-welcome__lanes strong,
.agent-welcome__lanes em {
  display: block;
  min-width: 0;
}

.agent-welcome__lanes strong {
  color: #1f2329;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.agent-welcome__lanes em {
  color: #6b7280;
  font-size: 12px;
  font-style: normal;
  line-height: 17px;
}

.agent-message {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
}

.agent-message--assistant {
  width: min(1080px, 92%);
}

.agent-message--user {
  width: min(360px, 44%);
  grid-template-columns: minmax(0, 1fr) 40px;
  align-self: flex-end;
}

.agent-message--user .agent-message__avatar {
  order: 2;
}

.agent-message--user .agent-message__body {
  order: 1;
}

.agent-message__avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 20px;
}

.agent-message--user .agent-message__avatar {
  color: #16a34a;
  background: #f0fdf4;
}

.agent-message__body {
  min-width: 0;
  padding: 18px 20px;
  border: 1px solid #e7edf5;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.agent-message--user .agent-message__body {
  border-color: #d6e9ff;
  background: #eef7ff;
  box-shadow: none;
}

.agent-markdown {
  margin: 0;
  color: #1f2329;
  font-size: 15px;
  line-height: 24px;
  word-break: break-word;
}

.agent-markdown p {
  margin: 0 0 14px;
}

.agent-markdown p:last-child,
.agent-markdown ul:last-child,
.agent-markdown ol:last-child,
.agent-markdown .agent-markdown__table-wrap:last-child {
  margin-bottom: 0;
}

.agent-markdown h3,
.agent-markdown h4,
.agent-markdown h5 {
  margin: 18px 0 10px;
  color: #111827;
  font-weight: 700;
  line-height: 1.35;
}

.agent-markdown h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}

.agent-markdown h3::before {
  display: block;
  width: 3px;
  height: 14px;
  border-radius: 999px;
  background: #1677ff;
  content: '';
}

.agent-markdown h3:first-child,
.agent-markdown h4:first-child,
.agent-markdown h5:first-child {
  margin-top: 0;
}

.agent-markdown strong {
  color: #111827;
  font-weight: 700;
}

.agent-markdown code {
  padding: 1px 5px;
  border-radius: 5px;
  background: #f3f4f6;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
}

.agent-markdown__code {
  max-width: 100%;
  overflow-x: auto;
  margin: 12px 0 16px;
  padding: 12px 14px;
  border: 1px solid #e5edf8;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 20px;
  white-space: pre;
}

.agent-markdown__code code {
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
}

.agent-markdown a {
  color: #1677ff;
  text-decoration: none;
}

.agent-markdown a:hover {
  text-decoration: underline;
}

.agent-markdown ul,
.agent-markdown ol {
  display: grid;
  gap: 7px;
  margin: 0 0 14px;
  padding-left: 22px;
}

.agent-markdown li {
  padding-left: 2px;
}

.agent-markdown__table-wrap {
  max-width: 100%;
  overflow-x: auto;
  margin: 12px 0 16px;
  border: 1px solid #e5edf8;
  border-radius: 8px;
  background: #fff;
}

.agent-markdown table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
}

.agent-markdown th,
.agent-markdown td {
  padding: 9px 12px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
  font-size: 13px;
  line-height: 20px;
  text-align: left;
  vertical-align: top;
}

.agent-markdown th {
  background: #f8fafc;
  color: #0f172a;
  font-weight: 700;
  white-space: nowrap;
}

.agent-markdown tr:last-child td {
  border-bottom: 0;
}

.agent-workspace {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
}

.agent-workspace__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.agent-workspace__summary span,
.agent-section-title span,
.agent-workspace__evidence span {
  color: #6b7280;
  font-size: 12px;
}

.agent-workspace__summary h3 {
  margin: 3px 0 5px;
  color: #1f2329;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
}

.agent-workspace__summary p {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.agent-workspace__evidence {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.agent-workspace__evidence div {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.agent-workspace__evidence strong {
  color: #1f2329;
  font-size: 16px;
  line-height: 22px;
}

.agent-lanes {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.agent-lane {
  display: grid;
  min-width: 0;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  padding: 11px;
  border: 1px solid #e5e7eb;
  border-left: 3px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.agent-lane:hover {
  border-color: #1677ff;
}

.agent-lane--ready {
  border-left-color: #16a34a;
}

.agent-lane--review,
.agent-lane--partial {
  border-left-color: #f59e0b;
}

.agent-lane--needs_data,
.agent-lane--needs-data,
.agent-lane--blocked {
  border-left-color: #ef4444;
}

.agent-lane__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #1677ff;
  font-size: 16px;
}

.agent-lane__copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.agent-lane__copy strong,
.agent-alert strong,
.agent-task strong,
.agent-action-draft strong,
.agent-section-title strong {
  color: #1f2329;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.agent-lane__copy em,
.agent-action-draft em {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  font-style: normal;
  line-height: 17px;
  text-overflow: ellipsis;
}

.agent-lane :deep(.t-tag) {
  grid-column: 1 / -1;
  justify-self: flex-start;
}

.agent-alerts,
.agent-task-grid,
.agent-action-drafts {
  display: grid;
  gap: 8px;
}

.agent-alert {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.agent-alert--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.agent-alert--warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.agent-alert--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.agent-alert .t-icon {
  margin-top: 2px;
  color: #1677ff;
}

.agent-alert--success .t-icon {
  color: #16a34a;
}

.agent-alert--warning .t-icon {
  color: #d97706;
}

.agent-alert--error .t-icon {
  color: #dc2626;
}

.agent-alert p,
.agent-task p {
  margin: 3px 0 0;
  color: #4b5563;
  font-size: 12px;
  line-height: 18px;
}

.agent-task-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.agent-task {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.agent-task--blocked {
  border-color: #fecaca;
}

.agent-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.agent-action-draft {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.agent-action-draft:hover {
  border-color: #1677ff;
}

.agent-action-draft span {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.agent-next-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-next-prompts button {
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
  color: #1f2329;
  cursor: pointer;
  font-size: 12px;
}

.agent-next-prompts button:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.agent-run {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #d8e9ff;
  border-radius: 8px;
  background: #f8fbff;
}

.agent-run__head {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.agent-run__head strong {
  color: #1f2329;
  font-size: 14px;
  font-weight: 600;
}

.agent-run__head p {
  margin: 3px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.agent-run__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.agent-run__status {
  width: 28px;
  height: 28px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 16px;
}

.agent-run__spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgb(22 119 255 / 20%);
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: agent-spin 0.8s linear infinite;
}

.agent-thinking-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 8px 10px;
  border: 1px solid #e3efff;
  border-radius: 999px;
  background: #fff;
}

.agent-thinking-card span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1677ff;
  animation: agent-thinking-pulse 1.1s ease-in-out infinite;
}

.agent-thinking-card span:nth-child(2) {
  animation-delay: 0.14s;
}

.agent-thinking-card span:nth-child(3) {
  animation-delay: 0.28s;
}

.agent-chart-skeleton {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: end;
  gap: 12px;
  padding: 10px 4px 2px;
}

.agent-chart-skeleton span {
  color: #1677ff;
  font-size: 20px;
  opacity: 0.32;
}

.agent-chart-skeleton div {
  display: flex;
  align-items: end;
  gap: 6px;
  height: 34px;
}

.agent-chart-skeleton i {
  display: block;
  width: 12px;
  border-radius: 4px 4px 0 0;
  background: #dbeafe;
  animation: agent-bar-pulse 1.1s ease-in-out infinite;
}

.agent-chart-skeleton i:nth-child(1) {
  height: 15px;
}

.agent-chart-skeleton i:nth-child(2) {
  height: 27px;
  animation-delay: 0.14s;
}

.agent-chart-skeleton i:nth-child(3) {
  height: 21px;
  animation-delay: 0.28s;
}

.agent-charts {
  margin-top: 12px;
}

.agent-tool {
  display: grid;
  grid-template-columns: auto minmax(120px, auto) minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.agent-tool strong {
  color: #1f2329;
  font-size: 13px;
}

.agent-chart-card {
  padding: 14px;
  border: 1px solid #e5edf8;
  border-radius: 8px;
  background: #fff;
}

.agent-chart-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.agent-chart-card__head h3 {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-chart-card__summary {
  margin: -2px 0 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f5f8ff;
  color: #44546f;
  font-size: 13px;
  line-height: 1.6;
}

.agent-chart__fallback {
  display: grid;
  gap: 12px;
}

.agent-chart__empty {
  padding: 28px 0;
  color: #8c8c8c;
  font-size: 13px;
  text-align: center;
}

.agent-chart__bars {
  display: grid;
  gap: 10px;
}

.agent-chart__bar-row {
  display: grid;
  grid-template-columns: minmax(72px, 118px) minmax(0, 1fr) minmax(56px, auto);
  align-items: center;
  gap: 10px;
  color: #4b5563;
  font-size: 12px;
}

.agent-chart__bar-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-chart__bar-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #f1f5f9;
}

.agent-chart__bar-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1677ff;
}

.agent-chart__bar-row strong,
.agent-chart__legend-row strong {
  color: #1f2329;
  font-size: 12px;
  font-weight: 600;
}

.agent-chart__pie-wrap {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
}

.agent-chart__pie {
  width: 132px;
  height: 132px;
  border: 10px solid #fff;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 4%), 0 8px 18px rgb(15 23 42 / 8%);
}

.agent-chart__legend {
  display: grid;
  gap: 8px;
}

.agent-chart__legend-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 12px;
}

.agent-chart__legend-row i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.agent-chart__table {
  width: 100%;
  overflow: hidden;
  border-collapse: collapse;
}

.agent-chart__table th,
.agent-chart__table td {
  padding: 8px;
  border-bottom: 1px solid #eef2f7;
  color: #4b5563;
  font-size: 12px;
  text-align: left;
}

.agent-input {
  padding: 16px 30px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

.agent-floating--page .agent-input {
  width: min(960px, calc(100% - 80px));
  margin: 0 auto;
  padding: 10px 0 20px;
  border-top: 0;
  background: transparent;
}

.agent-input__textarea {
  width: 100%;
}

.agent-input :deep(.t-textarea__inner) {
  min-height: 118px !important;
  border-color: #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 4px 16px rgb(15 23 42 / 8%);
  font-size: 15px;
}

.agent-floating--page .agent-input :deep(.t-textarea__inner) {
  min-height: 88px !important;
  border-color: #d9e4f1;
  border-radius: 14px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 12px 32px rgb(25 59 101 / 11%);
}

.agent-floating--page .agent-run {
  gap: 0;
  margin-bottom: 10px;
  padding: 13px 15px;
  border-color: #dbe7f5;
  border-radius: 12px;
  background: #f8fbff;
}

.agent-floating--page .agent-thinking-card,
.agent-floating--page .agent-chart-skeleton {
  display: none;
}

.agent-floating--page .run-timeline {
  margin-top: 0;
}

.agent-input__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.agent-input__actions span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-send-button {
  flex: 0 0 auto;
}

.agent-send-button--running :deep(.t-icon) {
  color: #fff;
}

.agent-floating :deep(.t-button),
.agent-floating :deep(.t-card),
.agent-floating :deep(.t-collapse) {
  box-shadow: none !important;
}

.agent-floating :deep(.t-button--theme-primary.t-button--variant-base) {
  background-color: #1677ff;
  border-color: #1677ff;
}

@media (max-width: 960px) {
  .agent-floating {
    right: 12px;
    bottom: 12px;
  }

  .agent-floating--open {
    inset: 12px;
  }

  .agent-floating--page.agent-floating--open {
    inset: auto;
  }

  .agent-window {
    grid-template-columns: 1fr;
  }

  .agent-floating--page .agent-window {
    height: calc(100vh - 132px);
    min-height: 0;
  }

  .agent-history {
    max-height: 230px;
    border-right: 0;
    border-bottom: 1px solid #eef0f4;
  }

  .agent-chat__header {
    display: grid;
    min-height: auto;
  }

  .agent-chat__meta {
    justify-content: flex-start;
  }

  .agent-messages {
    padding: 18px;
  }

  .agent-message--user {
    width: min(100%, 620px);
  }

  .agent-message--assistant {
    width: 100%;
  }

  .agent-welcome h2 {
    font-size: 24px;
    line-height: 32px;
  }

  .agent-welcome__lanes,
  .agent-lanes {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .agent-workspace__evidence,
  .agent-task-grid {
    grid-template-columns: 1fr;
  }

  .agent-chart__pie-wrap {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .agent-input {
    padding: 12px 18px 16px;
  }

  .agent-floating--page .agent-input {
    width: calc(100% - 32px);
    margin-bottom: 20px;
    padding: 0;
  }
}

@keyframes agent-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes agent-bar-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scaleY(0.72);
  }

  50% {
    opacity: 1;
    transform: scaleY(1);
  }
}

@keyframes agent-thinking-pulse {
  0%,
  100% {
    opacity: 0.32;
    transform: translateY(0);
  }

  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
</style>
