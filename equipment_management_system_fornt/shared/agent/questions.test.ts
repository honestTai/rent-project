import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DEFAULT_AGENT_INPUT_PLACEHOLDER,
  clarificationInputPlaceholder,
  extractAgentQuestions,
  isClarificationMessage,
  shouldShowAgentProcess,
} from './questions.ts';
import type { AgentMessage, AgentQuestion } from './types.ts';

function assistantMessage(
  questions: AgentQuestion[],
  status: AgentMessage['status'] = 'complete',
): AgentMessage {
  return {
    id: 'assistant-1',
    role: 'assistant',
    content: '',
    status,
    questions,
  };
}

test('extracts explicit questions from every supported array field', () => {
  const sources = [
    { questions: ['问题 1？'] },
    { clarifyingQuestions: ['问题 2？'] },
    { followUps: ['问题 3？'] },
    { followupQuestions: ['问题 4？'] },
    { metadata: { questions: ['问题 5？'] } },
    { extra: { questions: ['问题 6？'] } },
  ];

  assert.deepEqual(
    sources.map((source) => extractAgentQuestions(source)[0]),
    sources.map((_, index) => ({ question: `问题 ${index + 1}？`, inferred: false })),
  );
});

test('uses a later explicit array when an earlier candidate is not an array', () => {
  const questions = extractAgentQuestions({
    questions: { question: '无效结构？' },
    clarifyingQuestions: [{ title: '显式问题？' }],
    answer: '不应该从这里推断吗？',
  });

  assert.deepEqual(questions, [{ question: '显式问题？', inferred: false }]);
});

test('normalizes explicit strings and objects while preserving presentation fields', () => {
  const options = ['订单数', { label: '销售额', value: 'amount' }];
  const questions = extractAgentQuestions({
    questions: [
      '  查询哪个城市？  ',
      { label: '请选择统计口径', description: '用于生成报表', options, inferred: true },
      '   ',
      { question: '' },
    ],
  });

  assert.deepEqual(questions, [
    { question: '查询哪个城市？', inferred: false },
    {
      question: '请选择统计口径',
      description: '用于生成报表',
      options,
      inferred: true,
    },
  ]);
});

test('uses the first non-blank object field as the question text', () => {
  const questions = extractAgentQuestions({
    questions: [{ question: '   ', title: '有效问题？' }],
  });

  assert.deepEqual(questions, [{ question: '有效问题？', inferred: false }]);
});

test('infers question-mark-terminated lines only when no explicit array exists', () => {
  const questions = extractAgentQuestions({
    questions: null,
    content: '这里是普通结论。\n- 还需要查看其他指标吗？\n这行不是问题',
  });

  assert.deepEqual(questions, [{ question: '还需要查看其他指标吗？', inferred: true }]);
  assert.deepEqual(extractAgentQuestions({
    questions: [],
    content: '这个问题不应被推断吗？',
  }), []);
});

test('preserves a numeric prefix that is part of an inferred question', () => {
  const questions = extractAgentQuestions({ content: '2026年是否继续？' });

  assert.deepEqual(questions, [{ question: '2026年是否继续？', inferred: true }]);
});

test('keeps short inferred questions that end with a question mark', () => {
  const questions = extractAgentQuestions({ content: '是否继续？' });

  assert.deepEqual(questions, [{ question: '是否继续？', inferred: true }]);
});

test('explicit API questions enter pure clarification mode', () => {
  const questions = extractAgentQuestions({
    questions: [{ question: '你想查询哪个城市的天气？' }],
  });

  assert.equal(isClarificationMessage(assistantMessage(questions)), true);
});

test('inferred text questions stay in normal result mode', () => {
  const questions = extractAgentQuestions({ content: '还需要查看其他指标吗？' });

  assert.equal(questions[0]?.inferred, true);
  assert.equal(isClarificationMessage(assistantMessage(questions)), false);
});

test('error and cancelled assistant messages never enter clarification mode', () => {
  const questions: AgentQuestion[] = [{ question: '请选择口径', inferred: false }];

  assert.equal(isClarificationMessage(assistantMessage(questions, 'error')), false);
  assert.equal(isClarificationMessage(assistantMessage(questions, 'cancelled')), false);
});

test('uses an option-aware placeholder for explicit questions with options', () => {
  const messages = [assistantMessage([{
    question: '请选择口径',
    options: ['订单数'],
    inferred: false,
  }])];

  assert.equal(
    clarificationInputPlaceholder(messages, '输入消息…'),
    '选择一个选项，或输入你的回答…',
  );
});

test('uses the answer placeholder for explicit questions without options', () => {
  const messages = [assistantMessage([{ question: '你想查询哪个城市？', inferred: false }])];

  assert.equal(
    clarificationInputPlaceholder(messages, '输入消息…'),
    '输入你的回答…',
  );
});

test('keeps the fallback placeholder for ordinary messages without mutating input', () => {
  const messages: AgentMessage[] = [
    assistantMessage([{ question: '还需要其他信息吗？', inferred: true }]),
    { id: 'user-1', role: 'user', content: '不用了', status: 'complete' },
  ];
  const snapshot = structuredClone(messages);

  assert.equal(clarificationInputPlaceholder(messages, '输入消息…'), '输入消息…');
  assert.deepEqual(messages, snapshot);
});

test('replaces null-like fallback placeholders with the safe default', () => {
  const messages: AgentMessage[] = [];

  for (const fallback of [null, undefined, '', '   ', 'null', 'NULL', 'undefined']) {
    assert.equal(
      clarificationInputPlaceholder(messages, fallback),
      DEFAULT_AGENT_INPUT_PLACEHOLDER,
    );
  }
});

test('shows process only while thinking before an answer or clarification exists', () => {
  assert.equal(shouldShowAgentProcess({
    id: 'assistant-thinking',
    role: 'assistant',
    content: '',
    status: 'thinking',
  }), true);
  assert.equal(shouldShowAgentProcess({
    id: 'assistant-answering',
    role: 'assistant',
    content: '正在返回最终回答',
    status: 'thinking',
  }), false);
  assert.equal(shouldShowAgentProcess({
    id: 'assistant-complete',
    role: 'assistant',
    content: '最终回答',
    status: 'complete',
  }), false);
  assert.equal(shouldShowAgentProcess(assistantMessage([
    { question: '你想查询哪个城市？', inferred: false },
  ], 'thinking')), false);
});
