import type { AnyRecord, AgentMessage, AgentQuestion } from './types';

export const DEFAULT_AGENT_INPUT_PLACEHOLDER = '输入消息，按 Enter 发送…';

function safeFallbackPlaceholder(value: unknown): string {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text || text.toLowerCase() === 'null' || text.toLowerCase() === 'undefined') {
    return DEFAULT_AGENT_INPUT_PLACEHOLDER;
  }
  return text;
}

function inferQuestionsFromText(value: unknown): string[] {
  const text = String(value || '');
  if (!text || !/(请|需要|补充|确认|哪个|是否|吗|？|\?)/.test(text)) return [];

  return text
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*(?:[-*]\s+|\d+[.、]\s*)/, '').trim())
    .filter((line) => line && /[？?]$/.test(line))
    .slice(0, 3);
}

function normalizeQuestion(item: unknown, inferred: boolean): AgentQuestion | null {
  if (typeof item === 'string') {
    const question = item.trim();
    return question ? { question, inferred } : null;
  }
  if (!item || typeof item !== 'object') return null;

  const source = item as AnyRecord;
  const question = [source.question, source.title, source.label, source.content, source.text]
    .map((value) => String(value ?? '').trim())
    .find(Boolean) || '';
  if (!question) return null;

  const normalized: AgentQuestion = {
    question,
    inferred: inferred ? true : source.inferred === true,
  };
  const description = source.description || source.tip || source.reason;
  if (description !== undefined && description !== null && String(description).trim()) {
    normalized.description = String(description).trim();
  }
  if (Array.isArray(source.options)) normalized.options = source.options;
  return normalized;
}

export function extractAgentQuestions(source: AnyRecord): AgentQuestion[] {
  const explicit = [
    source.questions,
    source.clarifyingQuestions,
    source.followUps,
    source.followupQuestions,
    source.metadata?.questions,
    source.extra?.questions,
  ].find(Array.isArray) as unknown[] | undefined;
  const inferred = explicit === undefined;
  const rawQuestions = inferred
    ? inferQuestionsFromText(source.answer || source.content || source.message)
    : explicit;

  return rawQuestions
    .map((item) => normalizeQuestion(item, inferred))
    .filter((question): question is AgentQuestion => question !== null);
}

export function isClarificationMessage(message: AgentMessage): boolean {
  if (message.role !== 'assistant' || message.status === 'error' || message.status === 'cancelled') {
    return false;
  }
  return Boolean(message.questions?.some((question) => question.inferred !== true));
}

export function shouldShowAgentProcess(message: AgentMessage): boolean {
  if (message.role !== 'assistant' || message.status !== 'thinking') return false;
  if (String(message.content || '').trim()) return false;
  return !isClarificationMessage(message);
}

export function clarificationInputPlaceholder(messages: AgentMessage[], fallback: unknown): string {
  const safeFallback = safeFallbackPlaceholder(fallback);
  let latestAssistant: AgentMessage | undefined;
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'assistant') {
      latestAssistant = messages[index];
      break;
    }
  }

  if (!latestAssistant || !isClarificationMessage(latestAssistant)) return safeFallback;
  const hasOptions = latestAssistant.questions?.some(
    (question) => question.inferred !== true && Boolean(question.options?.length),
  );
  return hasOptions ? '选择一个选项，或输入你的回答…' : '输入你的回答…';
}
