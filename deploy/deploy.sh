#!/usr/bin/env bash
#
# 更新部署：本地构建 → 上传 → 重启 → 验证。
#
# 只负责「更新」，不负责首次搭建。建库、建数据库用户、写 env.sh、装独立 Node、
# 改 nginx 这些一次性动作见 deploy/README.md，重复执行本脚本不会碰它们。
#
# 用法:
#     ./deploy/deploy.sh            # 后端 + agent 全量更新
#     ./deploy/deploy.sh backend    # 只更新 Java 后端
#     ./deploy/deploy.sh agent      # 只更新 agent（含 skills）
#
set -euo pipefail

HOST=47.97.91.76
USER=root
KEY="$(cd "$(dirname "$0")" && pwd)/xiao7ai.com_ECS.pem"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE=/opt/shanchuang
BASE_URL=https://xiao7ai.com/shanchuang/api

TARGET="${1:-all}"

ssh_run() { ssh -i "$KEY" -o ConnectTimeout=20 "$USER@$HOST" "$@"; }
say() { printf "\n\033[1m▸ %s\033[0m\n" "$1"; }

[ -f "$KEY" ] || { echo "缺少私钥 $KEY"; exit 1; }
chmod 600 "$KEY"

# ---------- 后端 ----------
if [ "$TARGET" = "all" ] || [ "$TARGET" = "backend" ]; then
  say "构建后端 jar"
  (cd "$ROOT/server" && mvn -q package -DskipTests)

  say "上传 jar 并重启"
  # 先传成 .new 再原子替换：直接覆盖运行中的 jar，传输中断会留下半个文件，
  # 服务重启时才发现，那时旧 jar 已经没了
  scp -i "$KEY" "$ROOT/server/target/shanchuang-server-1.0.0.jar" "$USER@$HOST:$REMOTE/backend/app.jar.new"
  ssh_run "mv $REMOTE/backend/app.jar.new $REMOTE/backend/app.jar && systemctl restart shanchuang-backend"
fi

# ---------- agent ----------
if [ "$TARGET" = "all" ] || [ "$TARGET" = "agent" ]; then
  say "编译 agent"
  (cd "$ROOT/agent" && npm run build >/dev/null)

  say "上传 agent 与 skills"
  # macOS 的 tar 会把扩展属性和 BSD 文件标志打进包，Linux 侧解压时每个都 warning 一行，
  # 把真正的输出淹掉。两个都关掉，传的是构建产物，这些元数据没有意义
  TAR_OPTS=(--no-xattrs --no-fflags)
  tar "${TAR_OPTS[@]}" -czf /tmp/sc-agent.tgz -C "$ROOT/agent" dist package.json package-lock.json
  tar "${TAR_OPTS[@]}" -czf /tmp/sc-skills.tgz --exclude=output --exclude=__pycache__ -C "$ROOT" skills
  scp -i "$KEY" /tmp/sc-agent.tgz /tmp/sc-skills.tgz "$USER@$HOST:$REMOTE/"
  rm -f /tmp/sc-agent.tgz /tmp/sc-skills.tgz

  # package-lock 变了才重装依赖，没变就跳过——npm ci 会删掉整个 node_modules 重来，
  # 每次部署都跑一遍要多等一分钟
  ssh_run "set -e
    cd $REMOTE
    md5sum agent/package-lock.json > /tmp/lock.before 2>/dev/null || true
    tar xzf sc-agent.tgz -C agent && tar xzf sc-skills.tgz && rm -f sc-agent.tgz sc-skills.tgz
    if ! md5sum -c /tmp/lock.before >/dev/null 2>&1; then
      echo '  依赖清单有变，重装生产依赖'
      cd agent && $REMOTE/node/bin/npm ci --omit=dev --no-audit --no-fund >/dev/null
    else
      echo '  依赖清单未变，跳过 npm ci'
    fi
    systemctl restart shanchuang-agent"
fi

# ---------- 验证 ----------
say "等待服务就绪"
sleep 12

say "服务状态"
ssh_run "systemctl is-active shanchuang-backend shanchuang-agent | tr '\n' ' '; echo; tail -2 $REMOTE/logs/agent.log"

say "公网接口验证"
code=$(curl -s -m 30 -o /tmp/sc-check.json -w '%{http_code}' "$BASE_URL/options")
if [ "$code" = "200" ]; then
  echo "  GET /options → 200 ✓"
else
  echo "  GET /options → $code ✗"
  head -c 300 /tmp/sc-check.json
  exit 1
fi

say "harness 健康"
ssh_run "curl -s -m 20 http://127.0.0.1:8790/v1/harness/health" |
  python3 -c "import json,sys; d=json.load(sys.stdin); print('  ok=%s  %s v%s' % (d.get('ok'), d.get('harnessName'), d.get('harnessVersion')))"

printf "\n\033[1m部署完成\033[0m  %s\n" "$BASE_URL"
