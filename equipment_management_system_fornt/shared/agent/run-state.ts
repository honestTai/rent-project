import { extractAgentQuestions } from './questions.ts';
import type { AgentMessage, AgentRunEvent, AgentToolCall, AnyRecord } from './types';

export function collapseRunEvents(events: AgentRunEvent[]): AgentRunEvent[] {
  const collapsed: AgentRunEvent[] = [];
  const toolIndexes = new Map<string, number>();

  for (const event of events) {
    if (event.type === 'answer.delta') continue;
    if (!event.type.startsWith('tool.')) {
      collapsed.push(event);
      continue;
    }

    const name = String(event.data.name || '').trim();
    if (!name) {
      collapsed.push(event);
      continue;
    }
    const key = `${event.runId}:${name}`;
    const existingIndex = toolIndexes.get(key);
    if (existingIndex === undefined) {
      toolIndexes.set(key, collapsed.length);
      collapsed.push(event);
    } else {
      collapsed[existingIndex] = event;
    }
  }

  return collapsed;
}

function upsertTool(tools: AgentToolCall[], event: AgentRunEvent): AgentToolCall[] {
  const data = event.data as AnyRecord;
  const name = String(data.name || 'agent-tool');
  const index = tools.findIndex((tool) => tool.name === name);
  const status = event.type === 'tool.started'
    ? 'running'
    : event.type === 'tool.failed'
      ? 'failed'
      : String(data.status || 'success');
  const next: AgentToolCall = {
    ...(index >= 0 ? tools[index] : {}),
    ...data,
    name,
    displayName: String(data.displayName || (index >= 0 ? tools[index].displayName : '') || '执行只读工具'),
    status: status as AgentToolCall['status'],
  };
  if (index < 0) return [...tools, next];
  return tools.map((tool, toolIndex) => (toolIndex === index ? next : tool));
}

export function reduceRunEvent(message: AgentMessage, event: AgentRunEvent): AgentMessage {
  const existingEvents = message.runEvents || [];
  const runEvents = existingEvents.some((item) => item.sequence === event.sequence && item.runId === event.runId)
    ? existingEvents
    : [...existingEvents, event];
  let next: AgentMessage = { ...message, runId: event.runId, runEvents };

  if (event.type === 'run.started' || event.type === 'plan.ready') {
    return { ...next, status: 'thinking' };
  }
  if (event.type === 'tool.started' || event.type === 'tool.completed' || event.type === 'tool.failed') {
    return {
      ...next,
      status: 'thinking',
      toolCalls: upsertTool(message.toolCalls || [], event),
    };
  }
  if (event.type === 'answer.delta') {
    return { ...next, content: String(event.data.content || message.content || '') };
  }
  if (event.type === 'run.completed') {
    const data = event.data as AnyRecord;
    return {
      ...next,
      content: String(data.answer || data.content || message.content || '未返回分析结果'),
      status: 'complete',
      toolCalls: Array.isArray(data.toolCalls) ? data.toolCalls : message.toolCalls,
      charts: Array.isArray(data.charts) ? data.charts : message.charts,
      sources: Array.isArray(data.sources) ? data.sources : message.sources,
      questions: extractAgentQuestions(data),
      workspace: data.workspace && typeof data.workspace === 'object' ? data.workspace : message.workspace,
    };
  }
  if (event.type === 'run.cancelled') {
    return {
      ...next,
      status: 'cancelled',
      content: String(event.data.message || '已停止本次运行。'),
    };
  }
  if (event.type === 'run.failed') {
    return {
      ...next,
      status: 'error',
      content: String(event.data.message || 'AI 助手请求失败'),
    };
  }
  return next;
}
