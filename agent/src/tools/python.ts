/**
 * Python 脚本调用。
 *
 * 取件与 ASR 直接复用 skills/ 下已验证的脚本，不用 Node 重写：
 * 那些脚本里固化了抖音必须带 Cookie、短链要先还原、Whisper 要按句重切分等踩坑经验。
 */

import { spawn } from "node:child_process";
import path from "node:path";

const SKILLS_DIR =
  process.env.SKILLS_DIR ?? path.resolve(process.cwd(), "..", "skills");

const PYTHON = process.env.PYTHON_BIN ?? "python3";

export interface PyResult {
  code: number;
  stdout: string;
  stderr: string;
  timedOut: boolean;
}

export function skillScript(skill: string, script: string): string {
  return path.join(SKILLS_DIR, skill, "scripts", script);
}

export function runPython(
  script: string,
  args: string[],
  options: { timeoutMs?: number; cwd?: string; signal?: AbortSignal } = {},
): Promise<PyResult> {
  const timeoutMs = options.timeoutMs ?? 600_000;

  return new Promise((resolve, reject) => {
    const child = spawn(PYTHON, [script, ...args], {
      cwd: options.cwd ?? path.dirname(path.dirname(script)),
      env: process.env,
    });

    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGKILL");
    }, timeoutMs);

    const onAbort = () => child.kill("SIGKILL");
    options.signal?.addEventListener("abort", onAbort, { once: true });

    child.stdout.on("data", (chunk) => {
      stdout += String(chunk);
      // 单个工具输出上限，避免一次转写把整段字幕塞爆内存
      if (stdout.length > 4_000_000) child.kill("SIGKILL");
    });
    child.stderr.on("data", (chunk) => {
      stderr += String(chunk);
    });

    child.on("error", (err) => {
      clearTimeout(timer);
      options.signal?.removeEventListener("abort", onAbort);
      reject(err);
    });

    child.on("close", (code) => {
      clearTimeout(timer);
      options.signal?.removeEventListener("abort", onAbort);
      resolve({ code: code ?? -1, stdout, stderr, timedOut });
    });
  });
}
