/**
 * Harness 执行器。
 *
 * 用 pi-agent-core 的 Agent 跑循环（工具调用 → 结果回灌 → 继续，直到收敛），
 * 用 pi-ai 的 Models 集合做多 provider 归一化。
 * 对外只暴露 types.ts 里那份与 harness 无关的契约。
 */

import { Agent } from "@earendil-works/pi-agent-core";
import type { AgentEvent } from "@earendil-works/pi-agent-core";
import { contentText, type AssistantMessage } from "@earendil-works/pi-ai";
import { buildStreamOptions, getModels, ModelNotFoundError, resolveModel } from "./models.js";
import { selectTools } from "./tools/index.js";
import {
  ZERO_USAGE,
  type HarnessErrorCode,
  type HarnessRunRequest,
  type HarnessRunResult,
  type ToolCallTrace,
  type Usage,
} from "./types.js";

export const HARNESS_NAME = "pi";

/** 与 package.json 的 pi 依赖大版本保持一致，健康检查会上报给管理端 */
export const HARNESS_VERSION = process.env.PI_VERSION ?? "0.85.0";

const inflight = new Map<string, AbortController>();

export function cancel(requestId: string): boolean {
  const controller = inflight.get(requestId);
  if (!controller) return false;
  controller.abort();
  return true;
}

export async function run(req: HarnessRunRequest): Promise<HarnessRunResult> {
  const controller = new AbortController();
  inflight.set(req.requestId, controller);

  const timeoutMs = req.timeoutMs ?? 180_000;
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const usage: Usage = { ...ZERO_USAGE };
  const toolCalls: ToolCallTrace[] = [];
  let steps = 0;
  let lastError: string | undefined;

  try {
    const model = resolveModel(req.model);
    const tools = selectTools(req.tools);
    const models = getModels();

    const agent = new Agent({
      initialState: {
        systemPrompt: req.systemPrompt ?? "",
        model,
        tools,
        thinkingLevel: req.model.thinking && req.model.thinking !== "off" ? req.model.thinking : "off",
      },
      streamFn: (m, context, options) =>
        models.stream(m, context, { ...buildStreamOptions(req.model, controller.signal), ...options }),
    });

    agent.subscribe((event: AgentEvent) => {
      // 步数按助手轮次计，用于观测一次拆解到底绕了几轮
      if (event.type === "message_end") steps += 1;
      if (event.type === "tool_execution_end") {
        const anyEvent = event as unknown as { toolName?: string; isError?: boolean };
        toolCalls.push({ name: anyEvent.toolName ?? "unknown", ok: !anyEvent.isError });
      }
    });

    // 多条 user message 顺序拼给 Agent：契约允许 Java 侧传多轮上下文
    const prompt = req.messages
      .filter((m) => m.role === "user")
      .map((m) => m.content)
      .join("\n\n");

    await agent.prompt(prompt);
    await agent.waitForIdle();

    const collected = collectAssistantText(agent, usage);
    const text = collected.text;
    // 请求失败不会抛异常，错误落在最后一条助手消息的 stopReason/errorMessage 上
    lastError = collected.errorMessage ?? agent.state.errorMessage;

    if (lastError) {
      const aborted = controller.signal.aborted || collected.stopReason === "aborted";
      return fail(
        req,
        aborted ? "TIMEOUT" : classify(lastError),
        lastError,
        usage,
        steps,
        toolCalls,
      );
    }
    if (controller.signal.aborted) {
      return fail(req, "TIMEOUT", `aborted after ${timeoutMs}ms`, usage, steps, toolCalls);
    }
    if (!text.trim()) {
      return fail(req, "PROVIDER_ERROR", "model returned empty text", usage, steps, toolCalls);
    }

    return {
      requestId: req.requestId,
      ok: true,
      text,
      usage,
      steps: Math.max(steps, 1),
      harnessName: HARNESS_NAME,
      harnessVersion: HARNESS_VERSION,
      toolCalls,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const code: HarnessErrorCode =
      err instanceof ModelNotFoundError ? "MODEL_NOT_FOUND" : classify(message);
    return fail(req, code, message, usage, steps, toolCalls);
  } finally {
    clearTimeout(timer);
    inflight.delete(req.requestId);
  }
}

interface Collected {
  text: string;
  stopReason?: string;
  errorMessage?: string;
}

/**
 * 取最终文本并累加用量。
 *
 * 工具调用轮次里的助手消息只有 toolCall 没有正文，所以取最后一条有文本的助手消息；
 * 用量必须把所有轮次加起来，否则一次拆解的成本会被严重低估。
 */
function collectAssistantText(agent: Agent, usage: Usage): Collected {
  const collected: Collected = { text: "" };

  for (const message of agent.state.messages) {
    if (message.role !== "assistant") continue;
    const assistant = message as AssistantMessage;

    if (assistant.usage) {
      usage.inputTokens += assistant.usage.input ?? 0;
      usage.outputTokens += assistant.usage.output ?? 0;
      usage.costUsd += assistant.usage.cost?.total ?? 0;
    }

    collected.stopReason = assistant.stopReason;
    if (assistant.errorMessage) collected.errorMessage = assistant.errorMessage;

    const chunk = contentText(assistant.content).trim();
    if (chunk) collected.text = chunk;
  }
  return collected;
}

function classify(message: string): HarnessErrorCode {
  const m = message.toLowerCase();
  // pi-ai 在密钥缺失时报的是 "Provider is not configured: xxx"，这本质上就是鉴权没配好
  if (
    m.includes("api key") ||
    m.includes("not configured") ||
    m.includes("unauthorized") ||
    m.includes("401") ||
    m.includes("403")
  ) {
    return "PROVIDER_AUTH";
  }
  // 必须排在限流之前判：余额耗尽也返回 429，但重试再多次也不会成功，
  // 归到限流会白白退避 1+4+16 秒，还把失败原因说成了「上游忙」，误导排查
  if (
    m.includes("credit_balance_exhausted") ||
    m.includes("insufficient_quota") ||
    m.includes("no credits remaining") ||
    m.includes("exceeded your current quota") ||
    m.includes("billing")
  ) {
    return "PROVIDER_QUOTA";
  }
  if (m.includes("rate limit") || m.includes("429")) return "PROVIDER_RATE_LIMIT";
  if (m.includes("abort")) return "ABORTED";
  if (m.includes("timeout") || m.includes("etimedout")) return "TIMEOUT";
  if (m.includes("tool")) return "TOOL_ERROR";
  return "PROVIDER_ERROR";
}

function fail(
  req: HarnessRunRequest,
  errorCode: HarnessErrorCode,
  errorMessage: string,
  usage: Usage,
  steps: number,
  toolCalls: ToolCallTrace[],
): HarnessRunResult {
  return {
    requestId: req.requestId,
    ok: false,
    text: null,
    usage,
    steps,
    harnessName: HARNESS_NAME,
    harnessVersion: HARNESS_VERSION,
    toolCalls,
    errorCode,
    errorMessage,
  };
}
