/**
 * Harness 契约类型。
 *
 * 这份契约与 harness 实现无关：Java 侧只认这些字段，
 * 所以把 pi 换成 deepseek harness 或自研 harness 时，Java 代码不需要改。
 * 对应文档 docs/接口设计.md 第 12 章。
 */

export type Scene =
  | "TOPIC_TITLE"
  | "SCRIPT_GENERATE"
  | "SCRIPT_DEAI"
  | "SCRIPT_REWRITE"
  | "VIDEO_EXTRACT";

export type ThinkingLevel = "off" | "minimal" | "low" | "medium" | "high";

export interface ModelSpec {
  provider: string;
  modelId: string;
  temperature?: number | null;
  maxTokens?: number | null;
  thinking?: ThinkingLevel | null;
  /** OpenAI 兼容端点，Ollama / vLLM / 自建网关用 */
  baseUrl?: string | null;
  /** 环境变量名，不是密钥本身 */
  apiKeyEnv?: string | null;
}

export interface Msg {
  role: "user" | "assistant";
  content: string;
}

export interface HarnessRunRequest {
  requestId: string;
  scene: Scene;
  model: ModelSpec;
  systemPrompt?: string | null;
  messages: Msg[];
  /** 工具名白名单，不是工具定义。空数组表示纯文本生成 */
  tools?: string[] | null;
  maxSteps?: number | null;
  timeoutMs?: number | null;
}

export interface Usage {
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
}

export interface ToolCallTrace {
  name: string;
  ok: boolean;
  detail?: string;
}

export type HarnessErrorCode =
  | "BAD_REQUEST"
  | "MODEL_NOT_FOUND"
  | "PROVIDER_AUTH"
  /** 余额耗尽或配额用满。和限流都返回 429，但这个重试永远不会成功，必须分开 */
  | "PROVIDER_QUOTA"
  | "PROVIDER_RATE_LIMIT"
  | "PROVIDER_ERROR"
  | "TOOL_ERROR"
  | "TIMEOUT"
  | "ABORTED";

export interface HarnessRunResult {
  requestId: string;
  ok: boolean;
  text: string | null;
  usage: Usage;
  steps: number;
  harnessName: string;
  harnessVersion: string;
  toolCalls?: ToolCallTrace[];
  errorCode?: HarnessErrorCode;
  errorMessage?: string;
}

export interface ProviderHealth {
  name: string;
  ready: boolean;
  models: string[];
}

export interface HarnessHealth {
  ok: boolean;
  harnessName: string;
  harnessVersion: string;
  providers: ProviderHealth[];
  tools: string[];
}

export const ZERO_USAGE: Usage = { inputTokens: 0, outputTokens: 0, costUsd: 0 };
