/**
 * 模型解析。
 *
 * 依赖 pi-ai 的统一多 provider API：一个 Models 集合里注册了全部内置 provider，
 * 按请求里的 provider + modelId 取出模型即可，换模型/换厂商不需要改代码。
 */

import {
  createProvider,
  envApiKeyAuth,
  type Api,
  type Model,
  type SimpleStreamOptions,
} from "@earendil-works/pi-ai";
import { openAICompletionsApi } from "@earendil-works/pi-ai/api/openai-completions.lazy";
import { builtinModels } from "@earendil-works/pi-ai/providers/all";
import type { ModelSpec, ProviderHealth, ThinkingLevel } from "./types.js";

const models = builtinModels();

/** 已注册过的自定义端点 provider，键是 baseUrl + 密钥变量名 */
const customProviders = new Set<string>();

export function getModels() {
  return models;
}

export class ModelNotFoundError extends Error {}

/**
 * 解析出可用的 Model。
 *
 * modelId 在 pi 的内置目录里查不到时不直接失败：厂商上新模型的速度快于目录更新，
 * 用同 provider 的任一已知模型做模板，仅替换 id，这样运营在后台填一个新模型名就能用。
 */
export function resolveModel(spec: ModelSpec): Model<Api> {
  // 指定了 baseUrl 就是自建端点（Ollama / vLLM / 中转站），走动态注册的 provider。
  // 只改内置模型的 baseUrl 字段是不够的：pi-ai 会先在 provider 层校验
  // 「是否已配置」，内置 openai provider 没有 OPENAI_API_KEY 时直接报
  // Provider is not configured，per-request 的 apiKey 根本来不及生效。
  if (spec.baseUrl) {
    return resolveCustomEndpointModel(spec);
  }

  const provider = models.getProvider(spec.provider);
  if (!provider) {
    throw new ModelNotFoundError(`unknown provider: ${spec.provider}`);
  }

  const model = models.getModel(spec.provider, spec.modelId);
  if (model) {
    return model;
  }

  // 厂商上新模型比 pi 的目录更新快，用同 provider 的已知模型做模板只换 id，
  // 这样运营在后台填一个新模型名就能用，不用等 pi 发版
  const template = models.getModels(spec.provider)[0];
  if (!template) {
    throw new ModelNotFoundError(
      `provider ${spec.provider} exposes no model to use as template for ${spec.modelId}`,
    );
  }
  return { ...template, id: spec.modelId, name: spec.modelId };
}

/**
 * OpenAI 兼容的自建端点。
 *
 * 统一用 openai-completions 协议：`/v1/chat/completions` 是中转站与本地推理服务
 * 覆盖最广的一套，而 `/v1/responses` 很多实现只做了一半。
 * 成本按 0 计——第三方端点的计价我们无从得知，记个假数字比记 0 更误导。
 */
function resolveCustomEndpointModel(spec: ModelSpec): Model<Api> {
  const envVar = spec.apiKeyEnv ?? "CUSTOM_API_KEY";
  const providerId = `custom:${spec.baseUrl}`;
  const key = `${providerId}#${envVar}`;

  const model: Model<"openai-completions"> = {
    id: spec.modelId,
    name: spec.modelId,
    api: "openai-completions",
    provider: providerId,
    baseUrl: spec.baseUrl!,
    reasoning: Boolean(spec.thinking && spec.thinking !== "off"),
    input: ["text"],
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    contextWindow: 128_000,
    maxTokens: spec.maxTokens ?? 8_192,
  };

  if (!customProviders.has(key)) {
    models.setProvider(
      createProvider({
        id: providerId,
        name: spec.baseUrl!,
        baseUrl: spec.baseUrl!,
        auth: { apiKey: envApiKeyAuth(`${spec.baseUrl} API key`, [envVar]) },
        models: [],
        api: openAICompletionsApi(),
      }),
    );
    customProviders.add(key);
  }
  return model;
}

/** 把契约里的采样参数翻译成 pi-ai 的 SimpleStreamOptions */
export function buildStreamOptions(spec: ModelSpec, signal?: AbortSignal): SimpleStreamOptions {
  const options: SimpleStreamOptions = {};

  const apiKey = readApiKey(spec);
  if (apiKey) options.apiKey = apiKey;

  const reasoning = toReasoning(spec.thinking);
  if (reasoning) options.reasoning = reasoning;

  if (spec.maxTokens != null) options.maxTokens = spec.maxTokens;
  if (spec.temperature != null) {
    options.samplingParams = { ...(options.samplingParams ?? {}), temperature: spec.temperature };
  }
  if (signal) options.signal = signal;

  return options;
}

/**
 * 密钥只从环境变量取。Java 侧只传变量名，全程不接触密钥本身。
 * 未显式指定变量名时按 provider 猜默认名（pi-ai 自身也会兜底解析）。
 */
function readApiKey(spec: ModelSpec): string | undefined {
  const names = spec.apiKeyEnv
    ? [spec.apiKeyEnv]
    : [`${spec.provider.toUpperCase().replace(/[^A-Z0-9]/g, "_")}_API_KEY`];
  for (const name of names) {
    const value = process.env[name];
    if (value && value.trim()) return value.trim();
  }
  return undefined;
}

function toReasoning(level: ThinkingLevel | null | undefined): SimpleStreamOptions["reasoning"] {
  if (!level || level === "off") return undefined;
  return level;
}

/** 某个 provider 的密钥是否就绪，供健康检查判断 ready */
export function isProviderReady(providerId: string): boolean {
  const guess = `${providerId.toUpperCase().replace(/[^A-Z0-9]/g, "_")}_API_KEY`;
  if (process.env[guess]?.trim()) return true;
  // Ollama 等本地 provider 不需要密钥
  return providerId === "ollama";
}

export function listProviderHealth(): ProviderHealth[] {
  return models.getProviders().map((provider) => ({
    name: provider.id,
    ready: isProviderReady(provider.id),
    models: models
      .getModels(provider.id)
      .map((m) => m.id)
      .slice(0, 50),
  }));
}
