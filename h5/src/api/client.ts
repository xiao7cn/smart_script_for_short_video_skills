import { session } from "../store/session";

const BASE = "/api";

/** 业务错误码见《接口设计》1.2；-1 保留给网络层异常 */
export class ApiError extends Error {
  code: number;
  requestId?: string;

  constructor(code: number, message: string, requestId?: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.requestId = requestId;
  }
}

type Envelope<T> = { code: number; message: string; data: T; requestId?: string };

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(-1, "网络连接失败，请稍后重试");
  }

  const payload = (await res.json().catch(() => null)) as Envelope<T> | null;

  // 令牌失效：清会话，App 订阅到变化后自动退回登录页
  if (res.status === 401 || payload?.code === 401) {
    session.clear();
    throw new ApiError(401, payload?.message || "登录已过期，请重新登录");
  }
  if (!payload || typeof payload.code !== "number") {
    throw new ApiError(res.status, `服务异常（${res.status}）`);
  }
  if (payload.code !== 0) {
    throw new ApiError(payload.code, payload.message || "请求失败", payload.requestId);
  }
  return payload.data;
}

export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== "")
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join("&")}` : "";
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body ?? {}),
  del: <T>(path: string) => request<T>("DELETE", path),
};

export const errCode = (e: unknown): number => (e instanceof ApiError ? e.code : -1);

export const errMsg = (e: unknown): string =>
  e instanceof ApiError ? e.message : "网络异常，请稍后重试";
