#!/usr/bin/env python3
"""识别短视频链接所属平台，尽力取到本地文件，取不到就给出该平台的手动取件步骤。

设计边界：本脚本不含任何平台协议逆向、签名伪造或加密绕过代码。自动下载这一步
完全委托给用户自行安装的通用工具 yt-dlp；它拿不到的平台一律走手动取件指引。
微信视频号的视频经过加密分发，不提供自动路径。

用法:
    python3 fetch.py "https://v.douyin.com/xxxxxx/"
    python3 fetch.py "https://www.xiaohongshu.com/explore/xxx?xsec_token=..."
    python3 fetch.py --guide 视频号
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

EXIT_NEEDS_MANUAL = 3

PLATFORM_PATTERNS = [
    ("抖音", r"(douyin\.com|iesdouyin\.com)"),
    ("快手", r"(kuaishou\.com|chenzhongtech\.com|gifshow\.com)"),
    ("小红书", r"(xiaohongshu\.com|xhslink\.com)"),
    ("视频号", r"(channels\.weixin\.qq\.com|finder\.video\.qq\.com|weixin\.qq\.com/channels)"),
]

AUTO_CAPABLE = {"抖音", "快手", "小红书"}

GUIDES = {
    "抖音": """抖音取件（任选其一，按省事程度排序）：

  1. APP 内保存：视频右侧「分享」→「保存到相册」。作者关闭了下载权限时此项灰掉，走第 2 条。
  2. 电脑端录屏：浏览器打开 www.douyin.com 找到该视频，QuickTime（macOS）录屏，
     或按 Shift+Cmd+5 选区域录制。只需要声音，画质无所谓。
  3. 手机录屏：iOS 控制中心「屏幕录制」，Android 通知栏「屏幕录制」。记得开麦克风外的
     「内部音频」，否则录不到声音。

拿到 mp4/mov 后：python3 scripts/transcribe.py --input 文件路径""",

    "快手": """快手取件（任选其一）：

  1. APP 内保存：「分享」→「保存到相册」（作者允许下载时可用）。
  2. 电脑端录屏：浏览器打开 www.kuaishou.com 播放，QuickTime 或 Shift+Cmd+5 录屏。
  3. 手机录屏：同抖音，注意开内部音频。

拿到文件后：python3 scripts/transcribe.py --input 文件路径""",

    "小红书": """小红书取件（先看是不是图文笔记）：

  1. 图文笔记：正文就是文案，直接复制粘贴给我，不需要转写。
  2. 视频笔记 · APP 内保存：「分享」→「保存到相册」（作者允许时可用）。
  3. 视频笔记 · 录屏：网页版 www.xiaohongshu.com 播放后录屏。

自动下载失败通常是因为小红书强制 xsec_token：链接必须是从搜索结果或 App 分享里
带 ?xsec_token=... 的完整 URL，裸 note id 拿不到内容。

拿到文件后：python3 scripts/transcribe.py --input 文件路径""",

    "视频号": """视频号没有自动路径，必须录屏。

原因：视频号的视频以加密形式分发，取件需要抓包拿到与文件配对的解密密钥。
绕过这层加密属于规避平台技术措施，本 skill 不提供，也不建议你用第三方解析站
（要把带你身份信息的分享链接交给对方服务器）。

录屏步骤：
  1. 电脑：微信 PC 版打开视频号视频 → QuickTime 新建屏幕录制，或 Shift+Cmd+5 选区录制。
  2. 手机：iOS 控制中心「屏幕录制」→ 播放视频；Android 通知栏「屏幕录制」，
     务必开启「内部音频」，否则只录到环境噪音。
  3. 录完从相册导出到电脑。只要声音清楚，画质随便。

拿到文件后：python3 scripts/transcribe.py --input 文件路径""",
}


def detect_platform(url: str) -> str | None:
    for name, pattern in PLATFORM_PATTERNS:
        if re.search(pattern, url, re.IGNORECASE):
            return name
    return None


def try_yt_dlp(url: str, out_dir: Path) -> Path | None:
    binary = shutil.which("yt-dlp")
    if not binary:
        print("未安装 yt-dlp，跳过自动下载（pip install yt-dlp 或 brew install yt-dlp）\n", file=sys.stderr)
        return None
    out_dir.mkdir(parents=True, exist_ok=True)
    template = str(out_dir / "%(id)s.%(ext)s")
    print("尝试用 yt-dlp 自动下载……", file=sys.stderr)
    result = subprocess.run(
        [binary, "--no-playlist", "--no-warnings", "-o", template, "--print", "after_move:filepath", url],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        detail = (result.stderr or "").strip().splitlines()
        tail = detail[-1] if detail else "无错误输出"
        print(f"yt-dlp 失败：{tail}\n", file=sys.stderr)
        return None
    path = Path((result.stdout or "").strip().splitlines()[-1]) if result.stdout.strip() else None
    return path if path and path.is_file() else None


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="短视频链接取件")
    parser.add_argument("url", nargs="?", help="分享链接")
    parser.add_argument("--out-dir", default="output/videos", help="下载目录")
    parser.add_argument("--no-download", action="store_true", help="只识别平台并打印取件指引")
    parser.add_argument("--guide", choices=list(GUIDES), help="直接查看某个平台的取件指引")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    if args.guide:
        print(GUIDES[args.guide])
        return 0
    if not args.url:
        sys.exit("缺少链接。用法：python3 fetch.py <url>，或 --guide 抖音|快手|小红书|视频号")

    platform = detect_platform(args.url)
    if platform is None:
        print("无法识别平台。支持：抖音 / 快手 / 小红书 / 视频号")
        print("如果这是其他站点的直链，装了 yt-dlp 可以直接试：yt-dlp <url>")
        return EXIT_NEEDS_MANUAL

    print(f"平台：{platform}\n")

    if platform in AUTO_CAPABLE and not args.no_download:
        if path := try_yt_dlp(args.url, Path(args.out_dir)):
            print(f"已下载：{path}")
            print(f"\n下一步：python3 scripts/transcribe.py --input {path}")
            return 0

    print(GUIDES[platform])
    return EXIT_NEEDS_MANUAL


if __name__ == "__main__":
    sys.exit(main())
