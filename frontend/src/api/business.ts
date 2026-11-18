import { client, type Envelope } from "./client";
export async function get<T>(path: string, params?: object): Promise<T> {
  return (await client.get<Envelope<T>>(path, { params })).data.data;
}
export async function post<T>(path: string, body?: unknown): Promise<T> {
  return (await client.post<Envelope<T>>(path, body)).data.data;
}
export async function put<T>(path: string, body: unknown): Promise<T> {
  return (await client.put<Envelope<T>>(path, body)).data.data;
}
export interface Todo {
  id: number;
  sourceId: number;
  sourceRecordId: string;
  businessNo: string;
  title: string;
  description: string;
  businessType: string;
  sourceSystem: string;
  sourcePage: string;
  status: string;
  riskLevel: string;
  priority: string;
  owner: string;
  businessOrg: string;
  createdAt: string;
  dueAt: string;
  sourceUpdatedAt: string;
  completedAt: string | null;
  extraJson: string;
  overdue: boolean;
  overdueMinutes: number;
}
export interface Page {
  items: Todo[];
  total: number;
  now: string;
  timezone: string;
}
export interface Source {
  id: number;
  name: string;
  type: "HTTP" | "FILE";
  endpoint: string;
  requestMethod: string;
  timeoutMs: number;
  refreshInterval: number;
  mappingConfig: Record<string, string>;
  enabled: boolean;
  syncStatus: string;
  lastError: string;
  lastSyncAt: string;
}
export interface Batch {
  id: number;
  source_id: number;
  file_name: string;
  success_count: number;
  failure_count: number;
  duplicate_count: number;
  updated_count: number;
  status: string;
  errors: { line: number; reason: string }[];
}
export interface Item {
  key: string;
  name: string;
  visible: boolean;
  order: number;
  path?: string;
  parent?: string;
  icon?: string;
  aiPrompt?: string;
  color?: string;
  days?: number;
  refreshSeconds?: number;
  metric?: string;
  clickBehavior?: string;
}
export interface Config {
  dueSoonHours: number;
  defaultDays: number;
  defaultOwner: string;
  defaultView: string;
  aiExpanded: boolean;
  navigation: Item[];
  cards: Item[];
  shortcuts: Item[];
}
export interface Charts {
  trend: { date: string; count: number }[];
  business: Record<string, number>;
  risk: Record<string, number>;
  trendMeaning: string;
}
export const businessLabels: Record<string, string> = {
  PROCUREMENT: "采购",
  INVENTORY: "库存",
  SUPPLIER: "供应商",
};
export const riskLabels: Record<string, string> = {
  HIGH: "高风险",
  MEDIUM: "中风险",
  LOW: "低风险",
};
export const statusLabels: Record<string, string> = {
  PENDING: "待处理",
  PENDING_REVIEW: "待审核",
  PENDING_REPLY: "待回复",
  COMPLETED: "已完成",
};
