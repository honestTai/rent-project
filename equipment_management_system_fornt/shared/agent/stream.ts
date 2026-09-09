import type { AgentRunEvent, AgentStreamHandlers, AnyRecord } from './types';

export interface SseParser {
  push: (chunk: string) => void;
  finish: () => void;
}

export function createSseParser(onEvent: (event: AgentRunEvent) => void): SseParser {
  let buffer = '';

  const parseBlock = (block: string) => {
    if (!block.trim()) return;
    const data = block
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n');
    if (!data) return;
    try {
      const event = JSON.parse(data) as AgentRunEvent;
      if (event && event.type && event.runId) onEvent(event);
    } catch {
      // A malformed event is isolated so later events can still be consumed.
    }
  };

  const drain = (flush = false) => {
    buffer = buffer.replace(/\r\n/g, '\n');
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      parseBlock(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf('\n\n');
    }
    if (flush && buffer.trim()) {
      parseBlock(buffer);
      buffer = '';
    }
  };

  return {
    push(chunk: string) {
      buffer += chunk;
      drain();
    },
    finish() {
      drain(true);
    },
  };
}

export async function streamAgentMessage(
  data: AnyRecord,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = typeof localStorage === 'undefined' ? '' : localStorage.getItem('token') || '';
  const response = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-Request-Source': 'admin',
      ...(token ? { token } : {}),
    },
    body: JSON.stringify(data),
    signal,
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `AI 助手流式请求失败（${response.status}）`);
  }
  if (!response.body) throw new Error('浏览器未提供流式响应内容');

  const parser = createSseParser(handlers.onEvent);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      parser.push(decoder.decode(value, { stream: true }));
    }
    parser.push(decoder.decode());
    parser.finish();
  } finally {
    reader.releaseLock();
  }
}

export async function cancelAgentRun(runId: string): Promise<boolean> {
  if (!runId) return false;
  const token = typeof localStorage === 'undefined' ? '' : localStorage.getItem('token') || '';
  const response = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Request-Source': 'admin',
      ...(token ? { token } : {}),
    },
    body: JSON.stringify({ message: '停止', action: 'cancel', runId }),
  });
  if (!response.ok) throw new Error(`停止运行失败（${response.status}）`);
  const body = await response.json();
  return Boolean(body?.data?.cancelled);
}
