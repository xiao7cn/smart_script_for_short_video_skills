#!/usr/bin/env bash
# 把 skills/ 下的 skill 安装到 Claude Code / Cursor / Codex。
# 兼容 bash 3.2（macOS 自带），不使用关联数组。

set -euo pipefail

ALL_SKILLS="short-video-script video-script-extract video-script-rewrite douyin-benchmark-accounts douyin-benchmark-videos"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODE="copy"        # copy | link
SCOPE="user"       # user | project
PROJECT_DIR="$PWD"
ACTION="install"   # install | uninstall
DRY_RUN=0
TARGETS=""
SKILLS=""

usage() {
  cat <<'EOF'
用法: ./install.sh [平台...] [选项]

平台（不填则安装到全部三个）:
  claude    Claude Code
  cursor    Cursor
  codex     Codex CLI / IDE 扩展
  all       全部

Skill（不填则安装全部）:
  --skill short-video-script           短视频口播文案生成
  --skill video-script-extract         对标视频文案抽取与结构拆解
  --skill video-script-rewrite         基于原文拆解重写口播（洗稿）
  --skill douyin-benchmark-accounts    按关键词搜寻抖音对标账号
  --skill douyin-benchmark-videos      按关键词搜寻抖音对标视频并抽文案

安装位置:
  个人级（默认）        claude: ~/.claude/skills/    cursor: ~/.cursor/skills/    codex: ~/.agents/skills/
  项目级 --project     claude: .claude/skills/      cursor: .cursor/skills/      codex: .agents/skills/

选项:
  --project [DIR]   安装为项目级，默认当前目录
  --link            创建符号链接而非复制（改动仓库即时生效，适合自己维护时用）
  --uninstall       移除已安装的 skill
  --dry-run         只打印将要执行的操作
  -h, --help        显示本帮助

示例:
  ./install.sh                                  # 五个 skill 装到三个平台，个人级
  ./install.sh cursor --link                    # 只装 Cursor，符号链接
  ./install.sh claude --skill short-video-script # 只把文案 skill 装到 Claude
  ./install.sh all --uninstall                  # 从三个平台移除
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    claude|cursor|codex) TARGETS="$TARGETS $1" ;;
    all) TARGETS="claude cursor codex" ;;
    --skill)
      if [ $# -lt 2 ]; then echo "--skill 需要一个名称" >&2; exit 2; fi
      if [ ! -d "$REPO_ROOT/skills/$2" ]; then
        echo "未知 skill: $2（可选：${ALL_SKILLS}）" >&2; exit 2
      fi
      SKILLS="$SKILLS $2"; shift
      ;;
    --project)
      SCOPE="project"
      if [ $# -gt 1 ] && [ "${2#-}" = "$2" ] && [ -d "${2:-}" ]; then
        PROJECT_DIR="$(cd "$2" && pwd)"; shift
      fi
      ;;
    --link) MODE="link" ;;
    --uninstall) ACTION="uninstall" ;;
    --dry-run) DRY_RUN=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; echo >&2; usage >&2; exit 2 ;;
  esac
  shift
done

[ -z "$TARGETS" ] && TARGETS="claude cursor codex"
[ -z "$SKILLS" ] && SKILLS="$ALL_SKILLS"

for skill in $SKILLS; do
  if [ ! -f "$REPO_ROOT/skills/$skill/SKILL.md" ]; then
    echo "找不到 $REPO_ROOT/skills/$skill/SKILL.md，请在仓库根目录运行本脚本。" >&2
    exit 1
  fi
done

skills_dir_for() {
  case "$1" in
    claude) [ "$SCOPE" = "user" ] && echo "$HOME/.claude/skills" || echo "$PROJECT_DIR/.claude/skills" ;;
    cursor) [ "$SCOPE" = "user" ] && echo "$HOME/.cursor/skills" || echo "$PROJECT_DIR/.cursor/skills" ;;
    codex)  [ "$SCOPE" = "user" ] && echo "$HOME/.agents/skills" || echo "$PROJECT_DIR/.agents/skills" ;;
  esac
}

run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "    [dry-run] $*"
  else
    "$@"
  fi
}

check_codex_feature() {
  local config="$HOME/.codex/config.toml"
  if [ -f "$config" ] && grep -Eq '^[[:space:]]*skills[[:space:]]*=[[:space:]]*true' "$config"; then
    return 0
  fi
  cat <<EOF

  ⚠️  Codex 需要显式开启 skills 功能，否则不会加载。请在 $config 中加入：

        [features]
        skills = true

      或临时开启：codex --enable skills
EOF
}

changed=0
for target in $TARGETS; do
  dest_root="$(skills_dir_for "$target")"
  echo "── $target: $dest_root"

  for skill in $SKILLS; do
    dest="$dest_root/$skill"
    src="$REPO_ROOT/skills/$skill"

    if [ "$ACTION" = "uninstall" ]; then
      if [ -e "$dest" ] || [ -L "$dest" ]; then
        echo "  移除 $skill"
        run rm -rf "$dest"
        changed=$((changed + 1))
      else
        echo "  跳过 ${skill}（未安装）"
      fi
      continue
    fi

    echo "  安装 $skill"
    run mkdir -p "$dest_root"
    if [ -e "$dest" ] || [ -L "$dest" ]; then
      run rm -rf "$dest"
    fi
    if [ "$MODE" = "link" ]; then
      run ln -s "$src" "$dest"
    else
      run cp -R "$src" "$dest"
    fi
    changed=$((changed + 1))
  done

  if [ "$target" = "codex" ] && [ "$ACTION" = "install" ]; then
    check_codex_feature
  fi
done

echo
if [ "$ACTION" = "uninstall" ]; then
  echo "完成：移除了 $changed 项。"
else
  echo "完成：安装了 $changed 项（${MODE}，${SCOPE}）。"
  cat <<EOF

后续两步：
  1. 复制人设配置并填写你自己的六项固定参数：
       cp $REPO_ROOT/skills/short-video-script/config/persona.example.yaml ./persona.yaml
     也可放到 ~/.short-video-script/persona.yaml 全局共用。
  2. 重启 Claude Code / Cursor / Codex 会话（skill 索引在会话启动时加载）。

想用对标拆解，再装一个 ASR 后端（三选一）：
    python3 -m venv ~/.short-video-script/venv
    ~/.short-video-script/venv/bin/pip install faster-whisper   # 本地，推荐
    brew install whisper-cpp                                    # 本地，Apple Silicon 更快
    export OPENAI_API_KEY=sk-...                                # 云端，免安装
  自检：python3 $REPO_ROOT/skills/video-script-extract/scripts/transcribe.py --list-backends

想用抖音搜寻（douyin-benchmark-*），需要浏览器侧准备：
  1. 安装 Browser MCP 扩展并在 Cursor 的 MCP 配置里启用
  2. 每次使用前，在浏览器工具栏点击扩展图标 → Connect（这一步必须手动）
  3. 保持抖音已登录状态

调用方式：
  Claude Code   /short-video-script  或直接说「帮我写 5 条短视频口播文案」
  Cursor        直接描述需求，或 @ 提及 skill 名
  Codex         \$short-video-script
  洗稿          直接说「按这篇拆解洗稿」或 @video-script-rewrite
EOF
fi
