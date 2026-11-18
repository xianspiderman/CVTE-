import { client, type Envelope } from "./client";

export interface AiStatus {
  configured: boolean;
  model: string;
}
export interface Proposal {
  id: string;
  changes: { item: string; field: string; before: string; after: string }[];
  expiresAt: string;
}
export interface ToolRun {
  name: string;
  status: string;
  message?: string;
}
export interface AiTodo {
  id: number;
  businessNo: string;
  title: string;
  owner: string;
  status: string;
  riskLevel: string;
  dueAt: string;
  overdue: boolean;
}
export interface Evidence {
  tool: string;
  label: string;
  conditions: Record<string, string | number>;
  result: {
    [key: string]: unknown;
    total?: number;
    counts?: Record<string, number | null>;
    items?: AiTodo[];
    trend?: { date: string; count: number }[];
    business?: Record<string, number>;
    risk?: Record<string, number>;
    trendMeaning?: string;
    unavailable?: string[];
  };
  queriedAt: string;
  sources: {
    name: string;
    status: string;
    lastAttempt: string | null;
    dataUpdatedAt: string | null;
  }[];
  actions: { label: string; url: string }[];
}
export interface AiReply {
  sessionId: string;
  answer: string;
  evidence: Evidence[];
  tools: ToolRun[];
  pending: Proposal | null;
}
export async function chat(sessionId: string | undefined, message: string) {
  const r = await client.post<Envelope<AiReply>>(
    "/ai/chat",
    { sessionId, message },
    { timeout: 115_000 },
  );
  return r.data.data;
}
