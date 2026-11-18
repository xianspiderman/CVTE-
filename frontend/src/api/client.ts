import axios from "axios";
export const client = axios.create({ baseURL: "/api", timeout: 35000 });
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}
client.interceptors.response.use(
  (r) => r,
  (e) =>
    Promise.reject(
      new ApiError(
        e.response?.data?.message || "服务连接失败，请检查本地服务",
        e.response?.status ?? 0,
      ),
    ),
);
export interface Envelope<T> {
  code: string;
  message: string;
  data: T;
}
export interface Status {
  database: string;
  storedTodoCount: number;
  phase: string;
  aiReady: boolean;
}
export interface Sample {
  id: string;
  businessNo: string;
  title: string;
  status: string;
  owner: string;
}
export async function getStatus() {
  return (await client.get<Envelope<Status>>("/workbench/status")).data.data;
}
export async function getPreview() {
  return (
    await client.get<Envelope<{ data: Sample[] }>>("/data-sources/mock/preview")
  ).data.data.data;
}
