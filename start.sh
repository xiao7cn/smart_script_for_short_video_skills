#!/usr/bin/env bash
# 闪创工厂一键启动：安装依赖、准备数据库、拉起 Agent / Java 后端 / H5。
# 可重复执行：已安装的依赖会跳过，已存在的库表不会重建，重复启动会先停掉本脚本拉起的进程。
# 兼容 macOS 自带 bash 3.2。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$ROOT/.run"
LOG="$RUN/logs"

H5_PORT="${H5_PORT:-5173}"
SERVER_PORT="${SERVER_PORT:-8080}"
AGENT_PORT="${AGENT_PORT:-8790}"

ACTION="start"
RESET_DB=0
SKIP_DEPS=0

usage() {
  cat <<EOF
用法: ./start.sh [命令] [选项]

命令:
  start     安装依赖并启动（默认）
  stop      停止本脚本拉起的 Agent / 后端 / H5
  status    查看端口与进程
  restart   等价于 stop + start（不清库、不重装已有依赖）

选项:
  --reset-db    重建 shanchuang 库表（会清空业务数据）
  --skip-deps   跳过 JDK / Node / Maven / npm / mvn 安装与构建
  -h, --help    显示本帮助

访问:
  H5      http://127.0.0.1:${H5_PORT}
  API     http://127.0.0.1:${SERVER_PORT}/api
  Agent   http://127.0.0.1:${AGENT_PORT}/v1/harness/health

日志: ${LOG}/
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    start|stop|status|restart) ACTION="$1" ;;
    --reset-db) RESET_DB=1 ;;
    --skip-deps) SKIP_DEPS=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m OK\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !!\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31mERR\033[0m %s\n' "$*" >&2; }

need() { command -v "$1" >/dev/null 2>&1; }

apply_db_defaults() {
  DB_HOST="${DB_HOST:-127.0.0.1}"
  DB_PORT="${DB_PORT:-3306}"
  DB_NAME="${DB_NAME:-shanchuang}"
  DB_USER="${DB_USER:-root}"
  DB_PASSWORD="${DB_PASSWORD:-}"
}

# 加载 KEY=VALUE。环境里已有非空值的不覆盖。
load_dotenv() {
  local file="$1"
  [ -f "$file" ] || return 0
  local line key val cur
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
      ''|\#*) continue ;;
    esac
    case "$line" in
      *=*) ;;
      *) continue ;;
    esac
    key="${line%%=*}"
    val="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    val="${val#"${val%%[![:space:]]*}"}"
    val="${val%"${val##*[![:space:]]}"}"
    case "$val" in
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    [ -n "$key" ] || continue
    [ -n "$val" ] || continue
    eval "cur=\"\${$key-}\""
    if [ -n "$cur" ]; then
      continue
    fi
    export "$key=$val"
  done < "$file"
}

load_all_env() {
  if [ ! -f "$ROOT/.env" ] && [ -f "$ROOT/.env.example" ]; then
    cp "$ROOT/.env.example" "$ROOT/.env"
    warn "已生成 $ROOT/.env，如 MySQL 密码不是 root 请先改再启动"
  fi
  if [ ! -f "$ROOT/agent/.env" ] && [ -f "$ROOT/agent/.env.example" ]; then
    cp "$ROOT/agent/.env.example" "$ROOT/agent/.env"
    warn "已生成 agent/.env，请填入模型密钥"
  fi
  load_dotenv "$ROOT/.env"
  load_dotenv "$ROOT/agent/.env"
  apply_db_defaults
  if [ -z "${DB_URL:-}" ]; then
    export DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  fi
  export DB_USER DB_PASSWORD DB_HOST DB_PORT DB_NAME
  export AGENT_PORT
  export HARNESS_ENDPOINT="http://127.0.0.1:${AGENT_PORT}"
  export SKILLS_DIR="$ROOT/skills"
}

java_major() {
  local bin="$1"
  "$bin" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}

# 选出 JDK 21+（优先 21，避开 Homebrew 默认的 25，减少 Lombok/编译器兼容问题）
pick_java_home() {
  local major cand v
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    major="$(java_major "${JAVA_HOME}/bin/java" || true)"
    if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then
      return 0
    fi
    warn "当前 JAVA_HOME 版本过低，改选 JDK 21+"
    unset JAVA_HOME
  fi
  if [ -x /usr/libexec/java_home ]; then
    for v in 21 22 23 24 25; do
      cand="$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)"
      if [ -n "$cand" ] && [ -x "$cand/bin/java" ]; then
        export JAVA_HOME="$cand"
        return 0
      fi
    done
  fi
  if need brew; then
    for v in openjdk@21 openjdk@23 openjdk; do
      cand="$(brew --prefix "$v" 2>/dev/null || true)"
      if [ -n "$cand" ] && [ -x "$cand/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
        export JAVA_HOME="$cand/libexec/openjdk.jdk/Contents/Home"
        major="$(java_major "${JAVA_HOME}/bin/java" || true)"
        if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then
          return 0
        fi
        unset JAVA_HOME
      fi
    done
  fi
  if need java; then
    major="$(java_major java || true)"
    if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then
      cand="$(dirname "$(dirname "$(command -v java)")")"
      export JAVA_HOME="$cand"
      return 0
    fi
  fi
  return 1
}

brew_install() {
  local pkg="$1"
  if brew list --formula "$pkg" >/dev/null 2>&1; then
    return 0
  fi
  info "brew install $pkg"
  brew install "$pkg"
}

ensure_toolchain() {
  [ "$SKIP_DEPS" -eq 0 ] || { info "跳过依赖安装"; return 0; }

  if ! pick_java_home; then
    if need brew; then
      info "未找到 JDK 21+，安装 openjdk@21"
      brew_install openjdk@21
      if ! pick_java_home; then
        err "安装 openjdk@21 后仍找不到 JAVA_HOME"
        exit 1
      fi
    else
      err "需要 JDK 21+。macOS 可先安装 Homebrew，或自行安装 Temurin 21。"
      exit 1
    fi
  fi
  export PATH="${JAVA_HOME}/bin:${PATH}"
  ok "Java $(java_major "${JAVA_HOME}/bin/java")  (${JAVA_HOME})"

  if ! need mvn; then
    if need brew; then
      brew_install maven
    else
      err "需要 Maven。请安装后重试。"
      exit 1
    fi
  fi
  ok "Maven $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')"

  if ! need node || ! need npm; then
    if need brew; then
      brew_install node
    else
      err "需要 Node.js 18+。请安装后重试。"
      exit 1
    fi
  fi
  ok "Node $(node -v) / npm $(npm -v)"

  local node_major
  node_major="$(node -v | sed 's/^v//' | cut -d. -f1)"
  if [ "$node_major" -lt 18 ]; then
    err "Node.js 需要 18+，当前 $(node -v)"
    exit 1
  fi

  if ! need python3; then
    warn "未找到 python3：爆款拆解的取件/转写会不可用，其它功能不受影响"
  else
    ok "Python $(python3 --version 2>&1 | awk '{print $2}')"
  fi

  if ! need mysql; then
    if need brew; then
      brew_install mysql
    else
      err "需要 mysql 客户端。"
      exit 1
    fi
  fi
  ok "MySQL 客户端 $(mysql --version | awk '{print $3}')"
}

ensure_npm_dir() {
  local dir="$1"
  local name="$2"
  [ "$SKIP_DEPS" -eq 0 ] || return 0
  if [ -d "$dir/node_modules" ] \
     && [ ! "$dir/package.json" -nt "$dir/node_modules" ] \
     && { [ ! -f "$dir/package-lock.json" ] || [ ! "$dir/package-lock.json" -nt "$dir/node_modules" ]; }; then
    ok "$name 依赖已就绪"
    return 0
  fi
  info "安装 $name 依赖"
  (cd "$dir" && npm install --no-fund --no-audit)
}

ensure_server_jar() {
  if [ "$SKIP_DEPS" -eq 1 ]; then
    if ls "$ROOT/server/target"/shanchuang-server-*.jar >/dev/null 2>&1; then
      return 0
    fi
    err "--skip-deps 但找不到 server/target/shanchuang-server-*.jar，请先无该参数跑一次"
    exit 1
  fi
  info "构建 Java 后端（跳过测试）"
  mkdir -p "$LOG"
  if ! (cd "$ROOT/server" && mvn -B -DskipTests package >"$LOG/mvn.log" 2>&1); then
    err "Maven 构建失败，末 40 行："
    tail -40 "$LOG/mvn.log" >&2
    exit 1
  fi
  ok "后端 jar 已就绪"
}

mysql_server_up() {
  local out
  out="$(mysqladmin --protocol=tcp -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" ping 2>&1 || true)"
  case "$out" in
    *alive*|*mysqld\ is\ alive*) return 0 ;;
    *"Access denied"*) return 0 ;;
    *) return 1 ;;
  esac
}

mysql_cli() {
  if [ -n "${DB_PASSWORD}" ]; then
    MYSQL_PWD="$DB_PASSWORD" mysql --protocol=tcp -h"$DB_HOST" -P"$DB_PORT" \
      -u"$DB_USER" --default-character-set=utf8mb4 "$@"
  else
    mysql --protocol=tcp -h"$DB_HOST" -P"$DB_PORT" \
      -u"$DB_USER" --default-character-set=utf8mb4 "$@"
  fi
}

ensure_mysql() {
  if ! mysql_server_up; then
    if need brew && brew list --formula mysql >/dev/null 2>&1; then
      info "启动 MySQL（brew services）"
      brew services start mysql >/dev/null
      local i=0
      while [ "$i" -lt 40 ]; do
        mysql_server_up && break
        i=$((i + 1))
        sleep 1
      done
    fi
  fi
  if ! mysql_server_up; then
    err "MySQL 未在 ${DB_HOST}:${DB_PORT} 监听。请先启动 MySQL 后重试。"
    exit 1
  fi

  if ! mysql_cli -e "SELECT 1" >/dev/null 2>&1; then
    err "无法用 ${DB_USER}@${DB_HOST} 登录 MySQL。"
    err "请在 ${ROOT}/.env 填写 DB_USER / DB_PASSWORD 后重新执行 ./start.sh"
    exit 1
  fi
  ok "MySQL 可连接（${DB_USER}@${DB_HOST}:${DB_PORT}）"
}

ensure_schema() {
  local n
  n="$(mysql_cli -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='sv_user'" 2>/dev/null || echo 0)"
  n="$(echo "$n" | tr -d '[:space:]')"
  if [ "$RESET_DB" -eq 1 ]; then
    info "重建数据库 ${DB_NAME}"
    mysql_cli < "$ROOT/deploy/sql/01-schema.sql"
    mysql_cli < "$ROOT/deploy/sql/02-data.sql"
    ok "库表已重建"
    return 0
  fi
  if [ "${n:-0}" = "0" ]; then
    info "导入库表 ${DB_NAME}"
    mysql_cli < "$ROOT/deploy/sql/01-schema.sql"
    mysql_cli < "$ROOT/deploy/sql/02-data.sql"
    ok "库表已导入"
  else
    ok "数据库 ${DB_NAME} 已存在（跳过导入，需要重建请加 --reset-db）"
  fi
}

listen_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1 || true
}

# 双 fork 成独立会话，避免终端/父进程退出时把子进程带走。
launch_daemon() {
  local cwd="$1" log="$2" pidfile="$3"
  shift 3
  mkdir -p "$(dirname "$log")" "$(dirname "$pidfile")"
  : >"$log"
  LAUNCH_CWD="$cwd" python3 - "$log" "$pidfile" "$@" <<'PY'
import os, sys
log, pidfile = sys.argv[1], sys.argv[2]
cmd = sys.argv[3:]
cwd = os.environ["LAUNCH_CWD"]
env = os.environ.copy()
r, w = os.pipe()
pid = os.fork()
if pid > 0:
    os.close(w)
    data = os.read(r, 64)
    os.close(r)
    os.waitpid(pid, 0)
    with open(pidfile, "w", encoding="utf-8") as f:
        f.write(data.decode().strip() + "\n")
    sys.exit(0)
os.close(r)
os.setsid()
if os.fork() > 0:
    sys.exit(0)
os.chdir(cwd)
os.umask(0o022)
devnull = os.open(os.devnull, os.O_RDONLY)
os.dup2(devnull, 0)
os.close(devnull)
logfd = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
os.dup2(logfd, 1)
os.dup2(logfd, 2)
if logfd > 2:
    os.close(logfd)
os.write(w, str(os.getpid()).encode())
os.close(w)
if os.sep in cmd[0]:
    os.execve(cmd[0], cmd, env)
os.execvpe(cmd[0], cmd, env)
PY
}

stop_pidfile() {
  local pf="$1" name="$2"
  if [ ! -f "$pf" ]; then
    return 0
  fi
  local pid
  pid="$(tr -d '[:space:]' < "$pf" || true)"
  rm -f "$pf"
  if [ -z "$pid" ]; then
    return 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  info "停止 $name (pid $pid)"
  kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  local i=0
  while [ "$i" -lt 20 ]; do
    kill -0 "$pid" 2>/dev/null || break
    i=$((i + 1))
    sleep 0.25
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
  fi
}

stop_port_if_ours() {
  local port="$1" pattern="$2" name="$3"
  local pid cmd
  pid="$(listen_pid "$port")"
  [ -n "$pid" ] || return 0
  cmd="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  case "$cmd" in
    *$pattern*)
      info "释放 $name 端口 $port (pid $pid)"
      kill -TERM "$pid" 2>/dev/null || true
      sleep 0.4
      kill -KILL "$pid" 2>/dev/null || true
      ;;
  esac
}

stop_stack() {
  mkdir -p "$RUN"
  stop_pidfile "$RUN/h5.pid" "H5"
  stop_pidfile "$RUN/server.pid" "后端"
  stop_pidfile "$RUN/agent.pid" "Agent"
  stop_port_if_ours "$H5_PORT" "vite" "H5"
  stop_port_if_ours "$SERVER_PORT" "shanchuang-server" "后端"
  stop_port_if_ours "$AGENT_PORT" "tsx" "Agent"
}

wait_http() {
  local url="$1" name="$2" seconds="$3" log="$4"
  local i=0
  while [ "$i" -lt "$seconds" ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      ok "$name 就绪  $url"
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  err "$name 在 ${seconds}s 内没有就绪：$url"
  if [ -f "$log" ]; then
    err "日志末 30 行（$log）："
    tail -30 "$log" >&2
  fi
  return 1
}

assert_port_free_or_ours() {
  local port="$1" pattern="$2" name="$3"
  local pid cmd
  pid="$(listen_pid "$port")"
  [ -z "$pid" ] && return 0
  cmd="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  case "$cmd" in
    *$pattern*) return 0 ;;
  esac
  err "端口 $port 已被其它进程占用（pid $pid）：$cmd"
  err "请先关掉它，或改环境变量里的 ${name} 端口"
  exit 1
}

server_jar() {
  ls -1 "$ROOT/server/target"/shanchuang-server-*.jar 2>/dev/null | grep -v original | head -1
}

cmd_status() {
  local p name port item
  printf '%-8s %-6s %s\n' "服务" "端口" "状态"
  for item in "H5:$H5_PORT" "后端:$SERVER_PORT" "Agent:$AGENT_PORT"; do
    name="${item%%:*}"
    port="${item##*:}"
    p="$(listen_pid "$port")"
    if [ -n "$p" ]; then
      printf '%-8s %-6s 监听中 pid %s\n' "$name" "$port" "$p"
    else
      printf '%-8s %-6s 未启动\n' "$name" "$port"
    fi
  done
  if mysql_server_up 2>/dev/null; then
    printf '%-8s %-6s 运行中\n' "MySQL" "${DB_PORT:-3306}"
  else
    printf '%-8s %-6s 未检测到\n' "MySQL" "${DB_PORT:-3306}"
  fi
}

cmd_start() {
  mkdir -p "$RUN" "$LOG" "$ROOT/server/data/uploads"
  load_all_env
  ensure_toolchain
  ensure_mysql
  ensure_schema
  ensure_npm_dir "$ROOT/agent" "Agent"
  ensure_npm_dir "$ROOT/h5" "H5"
  ensure_server_jar

  if [ -z "${DEEPSEEK_API_KEY:-}" ] && [ -z "${OPENAI_API_KEY:-}" ] \
     && [ -z "${AITECHFLUX_API_KEY:-}" ] && [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    warn "未检测到模型密钥。脚本生成会失败，请在 agent/.env 或仓库根目录 .env 填写。"
  fi

  stop_stack

  assert_port_free_or_ours "$AGENT_PORT" "tsx" "AGENT_PORT"
  assert_port_free_or_ours "$SERVER_PORT" "shanchuang-server" "SERVER_PORT"
  assert_port_free_or_ours "$H5_PORT" "vite" "H5_PORT"

  local jar tsx vite
  jar="$(server_jar)"
  tsx="$ROOT/agent/node_modules/.bin/tsx"
  vite="$ROOT/h5/node_modules/.bin/vite"
  [ -n "$jar" ] && [ -f "$jar" ] || { err "找不到后端 jar"; exit 1; }
  [ -f "$tsx" ] || { err "找不到 $tsx，请去掉 --skip-deps 重跑"; exit 1; }
  [ -f "$vite" ] || { err "找不到 $vite，请去掉 --skip-deps 重跑"; exit 1; }

  info "启动 Agent :${AGENT_PORT}"
  launch_daemon "$ROOT/agent" "$LOG/agent.log" "$RUN/agent.pid" \
    "$tsx" "$ROOT/agent/src/server.ts"

  info "启动后端 :${SERVER_PORT}"
  launch_daemon "$ROOT/server" "$LOG/server.log" "$RUN/server.pid" \
    "${JAVA_HOME}/bin/java" -jar "$jar"

  info "启动 H5 :${H5_PORT}"
  launch_daemon "$ROOT/h5" "$LOG/h5.log" "$RUN/h5.pid" \
    "$vite" --host 0.0.0.0 --port "$H5_PORT"

  wait_http "http://127.0.0.1:${AGENT_PORT}/v1/harness/health" "Agent" 30 "$LOG/agent.log"
  wait_http "http://127.0.0.1:${SERVER_PORT}/api/options" "后端" 90 "$LOG/server.log"
  wait_http "http://127.0.0.1:${H5_PORT}" "H5" 30 "$LOG/h5.log"

  cat <<EOF

闪创工厂已启动
  H5    http://127.0.0.1:${H5_PORT}
  API   http://127.0.0.1:${SERVER_PORT}/api/options
  Agent http://127.0.0.1:${AGENT_PORT}/v1/harness/health

  停止  ./start.sh stop
  状态  ./start.sh status
  日志  tail -f ${LOG}/{agent,server,h5}.log

EOF
}

case "$ACTION" in
  stop)
    stop_stack
    ok "已停止"
    ;;
  status)
    load_dotenv "$ROOT/.env"
    load_dotenv "$ROOT/agent/.env"
    apply_db_defaults
    cmd_status
    ;;
  restart|start)
    cmd_start
    ;;
esac
