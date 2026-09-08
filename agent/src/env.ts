/**
 * 加载 agent/.env。
 *
 * 自己读而不是依赖 `node --env-file`：那个标志得由启动命令带上，
 * 换个跑法（tsx / node dist / pm2 / systemd）就容易漏，
 * 漏了的表现是「健康检查说 provider 未就绪」，排查起来绕。
 * 也不引 dotenv——这点逻辑不值得多一个依赖。
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

function loadDotEnv(fileName = ".env"): void {
  const here = path.dirname(fileURLToPath(import.meta.url));
  // src/ 下跑（tsx）和 dist/ 下跑（编译后）都要能找到工程根的 .env
  const candidates = [
    path.resolve(here, "..", fileName),
    path.resolve(here, "..", "..", fileName),
    path.resolve(process.cwd(), fileName),
  ];

  const file = candidates.find((p) => fs.existsSync(p));
  if (!file) return;

  for (const rawLine of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const eq = line.indexOf("=");
    if (eq <= 0) continue;

    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    // 真实环境变量优先：生产上密钥由编排平台注入，不该被仓库里的文件盖掉
    if (process.env[key] === undefined && value) {
      process.env[key] = value;
    }
  }
  console.log(`loaded env from ${file}`);
}

// 模块级副作用：ESM 按 import 声明顺序深度优先求值，
// 所以 server.ts 里把 `import "./env.js"` 放第一行，就能保证密钥先于 models.js 就位。
loadDotEnv();
