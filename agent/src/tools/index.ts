/**
 * Agent 工具集。
 *
 * 工具实现属于 harness 侧，Java 只按场景下发工具名白名单（见 docs/接口设计.md 12.3），
 * 所以换 harness 时 Java 不需要搬运任何 JSON Schema。
 */

import { Type } from "@earendil-works/pi-ai";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { runPython, skillScript } from "./python.js";

const EXTRACT_SKILL = "video-script-extract";
const REWRITE_SKILL = "video-script-rewrite";

function textResult<T>(text: string, details: T) {
  return { content: [{ type: "text" as const, text }], details };
}

const fetchVideoParams = Type.Object({
  url: Type.String({ description: "视频链接，支持 v.douyin.com 短链" }),
  browser: Type.Optional(
    Type.String({ description: "借哪个浏览器的 Cookie：chrome / brave / edge / firefox / safari" }),
  ),
  audioOnly: Type.Optional(Type.Boolean({ description: "只下音频，转写更快", default: true })),
});

/**
 * 取件。失败不重试、不找第三方解析站——直接把该平台的录屏指引交回模型，
 * 这是 Skill 定下的合规底线。
 */
export const fetchVideoTool: AgentTool<typeof fetchVideoParams> = {
  name: "fetch_video",
  label: "取件",
  description:
    "识别短视频平台并尝试下载音视频。支持抖音、快手、小红书；视频号无法自动取件。" +
    "失败时返回该平台的录屏指引，请把指引原样转达用户，不要重试、不要建议第三方解析站。",
  parameters: fetchVideoParams,
  async execute(_id, params, signal) {
    const args = [params.url];
    if (params.browser) args.push("--browser", params.browser);
    if (params.audioOnly !== false) args.push("--audio-only");

    const r = await runPython(skillScript(EXTRACT_SKILL, "fetch.py"), args, {
      timeoutMs: 300_000,
      signal,
    });

    // 退出码 3 = 至少一条需要用户手动提供文件，脚本已把录屏步骤打到 stdout
    if (r.code === 3 || r.timedOut || r.code !== 0) {
      const guide = (r.stdout + "\n" + r.stderr).trim();
      return textResult(
        JSON.stringify({
          ok: false,
          needFile: true,
          guide: guide || "自动取件失败，请按平台录屏后上传文件。",
        }),
        { needFile: true, guide },
      );
    }

    const files = discoverMedia();
    return textResult(
      JSON.stringify({ ok: true, files, log: tail(r.stdout, 2000) }),
      { files },
    );
  },
};

const transcribeParams = Type.Object({
  input: Type.String({ description: "本地音视频文件路径" }),
});

export const transcribeTool: AgentTool<typeof transcribeParams> = {
  name: "transcribe",
  label: "转写",
  description:
    "把音视频转成口播稿。返回三部分：txt（一句一行、不含时间戳）、rhythm（时长/字数/语速/句级时间轴）、" +
    "segments（句级时间戳 JSON）。ASR 会出错，专有名词与数字尤其容易错，" +
    "拆解时对拿不准的地方标注「此处 ASR 可能有误」，不要当成原文事实。",
  parameters: transcribeParams,
  async execute(_id, params, signal) {
    const r = await runPython(
      skillScript(EXTRACT_SKILL, "transcribe.py"),
      ["--input", params.input],
      { timeoutMs: 900_000, signal },
    );

    if (r.code !== 0 || r.timedOut) {
      throw new Error(
        `transcribe failed (code=${r.code}${r.timedOut ? ", timeout" : ""}): ${tail(r.stderr, 1500)}`,
      );
    }

    const base = path.basename(params.input).replace(/\.[^.]+$/, "");
    const dir = path.join(path.dirname(path.dirname(params.input)), "transcripts");
    const txt = readIfExists(path.join(dir, `${base}.txt`));
    const rhythm = readIfExists(path.join(dir, `${base}.rhythm.txt`));
    const segments = readIfExists(path.join(dir, `${base}.segments.json`));

    return textResult(
      JSON.stringify({
        ok: true,
        transcript: txt,
        rhythm,
        segments: segments ? safeJson(segments) : null,
        log: tail(r.stdout, 1000),
      }),
      { transcriptChars: txt?.length ?? 0 },
    );
  },
};

const checkScriptParams = Type.Object({
  rewriteText: Type.String({ description: "洗稿全文（十一段）" }),
  originalText: Type.String({ description: "原视频口播原文" }),
  minWords: Type.Optional(Type.Number()),
  maxWords: Type.Optional(Type.Number()),
  forbidden: Type.Optional(Type.String({ description: "禁用词，英文逗号分隔" })),
});

/**
 * 洗稿机械校验。规则是确定性的（章节、字数、禁用词、≥16 字连续重合、低分项），
 * 交给模型自检不可靠——Skill 里专门写了「改文案，不改分数」。
 */
export const checkScriptTool: AgentTool<typeof checkScriptParams> = {
  name: "check_script",
  label: "洗稿校验",
  description:
    "机械校验洗稿：原视频标题与原文是否齐全、十一段是否齐全、口播字数、禁用词、" +
    "与原文连续重合（≥16 字失败）、自检分是否低于 8、书面腔与口语标记。" +
    "未通过就按报错改第九段再校验，不要改分数。",
  parameters: checkScriptParams,
  async execute(_id, params, signal) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "sc-check-"));
    const rewritePath = path.join(dir, "rewrite.md");
    const originalPath = path.join(dir, "original.txt");
    try {
      fs.writeFileSync(rewritePath, params.rewriteText, "utf8");
      fs.writeFileSync(originalPath, params.originalText, "utf8");

      const args = ["--rewrite", rewritePath, "--original", originalPath];
      if (params.minWords != null) args.push("--min-words", String(params.minWords));
      if (params.maxWords != null) args.push("--max-words", String(params.maxWords));
      if (params.forbidden) args.push("--forbidden", params.forbidden);

      const r = await runPython(skillScript(REWRITE_SKILL, "check.py"), args, {
        timeoutMs: 60_000,
        signal,
      });

      const report = (r.stdout + (r.stderr ? "\n" + r.stderr : "")).trim();
      return textResult(
        JSON.stringify({ passed: r.code === 0, report }),
        { passed: r.code === 0, exitCode: r.code },
      );
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  },
};

export const ALL_TOOLS: Record<string, AgentTool<never>> = {
  fetch_video: fetchVideoTool as unknown as AgentTool<never>,
  transcribe: transcribeTool as unknown as AgentTool<never>,
  check_script: checkScriptTool as unknown as AgentTool<never>,
};

export const TOOL_NAMES = Object.keys(ALL_TOOLS);

/** 按 Java 下发的白名单挑工具，未知名字忽略而不是报错，便于两侧灰度上线 */
export function selectTools(names: string[] | null | undefined): AgentTool<never>[] {
  if (!names || names.length === 0) return [];
  return names.map((n) => ALL_TOOLS[n]).filter((t): t is AgentTool<never> => Boolean(t));
}

/* ---------- 工具内部用的小函数 ---------- */

function readIfExists(p: string): string | null {
  try {
    return fs.readFileSync(p, "utf8");
  } catch {
    return null;
  }
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function tail(text: string, max: number): string {
  return text.length <= max ? text : text.slice(-max);
}

/** fetch.py 把产物落到 skill 的 output/videos 下，这里回捞最近产生的媒体文件 */
function discoverMedia(): string[] {
  const dir = path.join(
    process.env.SKILLS_DIR ?? path.resolve(process.cwd(), "..", "skills"),
    EXTRACT_SKILL,
    "output",
    "videos",
  );
  try {
    return fs
      .readdirSync(dir)
      .filter((f) => /\.(m4a|mp3|wav|mp4|mov)$/i.test(f))
      .map((f) => path.join(dir, f))
      .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)
      .slice(0, 5);
  } catch {
    return [];
  }
}
