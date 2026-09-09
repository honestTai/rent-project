export type AnyRecord = Record<string, any>;

export type AgentRunEventType =
  | 'run.started'
  | 'plan.ready'
  | 'tool.started'
  | 'tool.completed'
  | 'tool.failed'
  | 'answer.delta'
  | 'run.completed'
  | 'run.cancelled'
  | 'run.failed';

export interface AgentRunEvent<T extends AnyRecord = AnyRecord> {
  type: AgentRunEventType;
  runId: string;
  sequence: number;
  timestamp: string;
  data: T;
}

export interface AgentToolCall extends AnyRecord {
  name: string;
  displayName?: string;
  status?: 'planned' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled';
  readonly?: boolean;
  durationMs?: number;
  summary?: string;
  resultSummary?: string;
  reason?: string;
}

export interface AgentQuestion {
  question: string;
  description?: string;
  options?: Array<string | AnyRecord>;
  inferred?: boolean;
}

export interface AgentSource {
  title: string;
  url: string;
  snippet?: string;
  source?: string;
  rank?: number;
}

export interface AgentMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  status?: 'complete' | 'thinking' | 'error' | 'cancelled';
  runId?: string;
  runEvents?: AgentRunEvent[];
  toolCalls?: AgentToolCall[];
  charts?: AnyRecord[];
  sources?: AgentSource[];
  workspace?: AnyRecord;
  questions?: AgentQuestion[];
}

export interface AgentStreamHandlers {
  onEvent: (event: AgentRunEvent) => void;
}

export interface AgentApi {
  sendMessage: (data: AnyRecord) => Promise<AnyRecord>;
  streamMessage?: (data: AnyRecord, handlers: AgentStreamHandlers, signal?: AbortSignal) => Promise<void>;
  cancelRun?: (runId: string) => Promise<boolean>;
  listConversations: (system: string) => Promise<AnyRecord>;
  getConversation: (id: string, system: string) => Promise<AnyRecord>;
  deleteConversation?: (id: string, system: string) => Promise<AnyRecord>;
}
