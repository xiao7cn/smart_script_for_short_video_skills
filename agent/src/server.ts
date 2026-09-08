/**
 * Agent 运行时的 HTTP 入口。
 *
 * 只监听回环地址：这个服务能读环境变量里的全部模型密钥，也能起 Python 子进程，
 * 不该对公网暴露。Java 与它同机或走内网。
 */

// 必须是第一个 import：它会在其余模块求值前把 .env 里的密钥注入 process.env
import "./env.js";

import express from "express";
import { listProviderHealth } from "./models.js";
import { cancel, HARNESS_NAME, HARNESS_VERSION, run } from "./runner.js";
import { TOOL_NAMES } from "./tools/index.js";
import type { HarnessHealth, HarnessRunRequest } from "./types.js";

const PORT = Number(process.env.AGENT_PORT ?? 8790);
const HOST = process.env.AGENT_HOST ?? "127.0.0.1";

const app = express();
app.use(express.json({ limit: "8mb" }));

app.get("/v1/harness/health", (_req, res) => {
  const providers = listProviderHealth();
  const health: HarnessHealth = {
    ok: true,
    harnessName: HARNESS_NAME,
    harnessVersion: HARNESS_VERSION,
    providers,
    tools: TOOL_NAMES,
  };
  res.json(health);
});

app.post("/v1/harness/run", async (req, res) => {
  const body = req.body as Partial<HarnessRunRequest>;
  const invalid = validate(body);
  if (invalid) {
    res.json({
      requestId: body.requestId ?? "",
      ok: false,
      text: null,
      usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 },
      steps: 0,
      harnessName: HARNESS_NAME,
      harnessVersion: HARNESS_VERSION,
      errorCode: "BAD_REQUEST",
      errorMessage: invalid,
    });
    return;
  }

  const request = body as HarnessRunRequest;
  const startedAt = Date.now();
  const result = await run(request);
  console.log(
    `[${request.scene}] ${request.model.provider}/${request.model.modelId} ` +
      `ok=${result.ok} steps=${result.steps} ${Date.now() - startedAt}ms ` +
      `in=${result.usage.inputTokens} out=${result.usage.outputTokens}` +
      (result.errorCode ? ` err=${result.errorCode}` : ""),
  );
  res.json(result);
});

app.post("/v1/harness/cancel", (req, res) => {
  const requestId = (req.body as { requestId?: string })?.requestId;
  res.json({ cancelled: requestId ? cancel(requestId) : false });
});

function validate(body: Partial<HarnessRunRequest>): string | null {
  if (!body || typeof body !== "object") return "body must be a JSON object";
  if (!body.requestId) return "requestId is required";
  if (!body.scene) return "scene is required";
  if (!body.model?.provider) return "model.provider is required";
  if (!body.model?.modelId) return "model.modelId is required";
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return "messages must be a non-empty array";
  }
  if (!body.messages.some((m) => m?.role === "user" && m?.content?.trim())) {
    return "at least one non-empty user message is required";
  }
  return null;
}

app.listen(PORT, HOST, () => {
  console.log(`shanchuang-agent (harness=${HARNESS_NAME} v${HARNESS_VERSION}) on http://${HOST}:${PORT}`);
  const ready = listProviderHealth().filter((p) => p.ready).map((p) => p.name);
  console.log(`providers ready: ${ready.length ? ready.join(", ") : "(none — 请设置对应 *_API_KEY)"}`);
});
