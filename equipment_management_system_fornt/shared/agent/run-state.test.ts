import assert from 'node:assert/strict';
import test from 'node:test';

import { collapseRunEvents, reduceRunEvent } from './run-state.ts';
import type { AgentMessage, AgentRunEvent } from './types.ts';

const message: AgentMessage = {
  id: 'assistant-1',
  role: 'assistant',
  content: '',
  status: 'thinking',
  runEvents: [],
  toolCalls: [],
};

function event(type: AgentRunEvent['type'], sequence: number, data: Record<string, any> = {}): AgentRunEvent {
  return { type, runId: 'run-1', sequence, timestamp: 'now', data };
}

test('aggregates live tool status and the completed answer', () => {
  let next = reduceRunEvent(message, event('run.started', 1));
  next = reduceRunEvent(next, event('tool.started', 2, {
    name: 'query_logs',
    displayName: '检查服务日志',
    readonly: true,
  }));
  assert.equal(next.runId, 'run-1');
  assert.equal(next.toolCalls?.[0].status, 'running');

  next = reduceRunEvent(next, event('tool.completed', 3, {
    name: 'query_logs',
    displayName: '检查服务日志',
    status: 'success',
    durationMs: 42,
    summary: '未发现新异常',
  }));
  next = reduceRunEvent(next, event('answer.delta', 4, { content: '正在整理结论' }));
  next = reduceRunEvent(next, event('run.completed', 5, {
    answer: '最终结论',
    toolCalls: [{ name: 'query_logs', displayName: '检查服务日志', status: 'success' }],
    charts: [],
    sources: [{
      title: '杭州天气',
      url: 'https://example.com/weather',
      snippet: '今天有阵雨。',
      source: 'example.com',
      rank: 1,
    }],
    questions: [],
    workspace: { readonly: true },
  }));

  assert.equal(next.status, 'complete');
  assert.equal(next.content, '最终结论');
  assert.equal(next.toolCalls?.[0].displayName, '检查服务日志');
  assert.equal(next.sources?.[0].title, '杭州天气');
  assert.deepEqual(next.questions, []);
  assert.equal(next.runEvents?.length, 5);
});

test('normalizes explicit string questions when a streamed run completes', () => {
  const next = reduceRunEvent(message, event('run.completed', 1, {
    answer: '请补充查询条件',
    questions: ['你想查询哪个城市？'],
  }));

  assert.deepEqual(next.questions, [{
    question: '你想查询哪个城市？',
    inferred: false,
  }]);
});

test('normalizes aliased question fields when a streamed run completes', () => {
  const next = reduceRunEvent(message, event('run.completed', 1, {
    answer: '请补充查询条件',
    clarifyingQuestions: [{
      title: '请选择时间范围',
      description: '用于限定查询窗口',
      options: ['今天', '近七天'],
    }],
  }));

  assert.deepEqual(next.questions, [{
    question: '请选择时间范围',
    description: '用于限定查询窗口',
    options: ['今天', '近七天'],
    inferred: false,
  }]);
});

test('an explicit empty question array clears old questions without inferring from the answer', () => {
  const next = reduceRunEvent(
    { ...message, questions: [{ question: '旧问题', inferred: false }] },
    event('run.completed', 1, {
      answer: '还需要补充哪个城市？',
      questions: [],
    }),
  );

  assert.deepEqual(next.questions, []);
});

test('keeps failed tool evidence and marks a cancelled run', () => {
  let next = reduceRunEvent(message, event('tool.failed', 1, {
    name: 'query_order',
    displayName: '查询订单',
    status: 'failed',
    summary: '登录已失效',
  }));
  next = reduceRunEvent(next, event('run.cancelled', 2, { message: '本次运行已停止' }));

  assert.equal(next.toolCalls?.[0].status, 'failed');
  assert.equal(next.status, 'cancelled');
  assert.equal(next.content, '本次运行已停止');
});

test('keeps skipped tools distinct from failed tools', () => {
  const next = reduceRunEvent(message, event('tool.completed', 1, {
    name: 'query_report_detail',
    displayName: '查询报表详情',
    status: 'skipped',
    summary: '缺少报表编号',
  }));

  assert.equal(next.toolCalls?.[0].status, 'skipped');
  assert.equal(next.toolCalls?.[0].summary, '缺少报表编号');
});

test('collapses tool start and completion events into logical query steps', () => {
  const events = [
    event('run.started', 1),
    event('plan.ready', 2, { tools: [{ name: 'query_order' }, { name: 'query_logs' }] }),
    event('tool.started', 3, { name: 'query_order', displayName: '查询订单' }),
    event('tool.completed', 4, {
      name: 'query_order',
      displayName: '查询订单',
      status: 'success',
      durationMs: 42,
    }),
    event('tool.started', 5, { name: 'query_logs', displayName: '查询系统日志' }),
    event('tool.failed', 6, {
      name: 'query_logs',
      displayName: '查询系统日志',
      status: 'failed',
      durationMs: 28,
    }),
    event('answer.delta', 7, { content: '正在整理' }),
    event('run.completed', 8, { answer: '完成' }),
  ];

  const visible = collapseRunEvents(events);

  assert.deepEqual(visible.map((item) => item.type), [
    'run.started',
    'plan.ready',
    'tool.completed',
    'tool.failed',
    'run.completed',
  ]);
  assert.equal(visible[2].data.durationMs, 42);
  assert.equal(visible[3].data.durationMs, 28);
});
