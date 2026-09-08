#!/usr/bin/env python3
"""识别短视频链接所属平台，尽力取到本地文件，取不到就给出该平台的手动取件步骤。

设计边界：本脚本不含任何平台协议逆向、签名伪造或加密绕过代码。自动下载完全委托
给用户自行安装的通用工具 yt-dlp。需要登录态时借用用户自己浏览器里的 Cookie
（yt-dlp 原生的 --cookies-from-browser），这和用真实浏览器打开页面是同一件事，
不构造也不伪造任何签名。yt-dlp 拿不到的平台一律走手动取件指引。
微信视频号的视频经过加密分发，不提供自动路径。

用法:
    python3 fetch.py "https://v.douyin.com/xxxxxx/"
    python3 fetch.py URL1 URL2 URL3                       # 批量取件
    python3 fetch.py --from-file urls.txt                 # 从文件读链接，一行一条
    python3 fetch.py --browser chrome "https://..."       # 指定浏览器，跳过探测
    python3 fetch.py --audio-only "https://..."           # 只要音频，转写更省事
    python3 fetch.py --guide 视频号
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

EXIT_NEEDS_MANUAL = 3

PLATFORM_PATTERNS = [
    ("抖音", r"(douyin\.com|iesdouyin\.com)"),
    ("快手", r"(kuaishou\.com|chenzhongtech\.com|gifshow\.com)"),
    ("小红书", r"(xiaohongshu\.com|xhslink\.com)"),
    ("视频号", r"(channels\.weixin\.qq\.com|finder\.video\.qq\.com|weixin\.qq\.com/channels)"),
    ("YouTube", r"(youtube\.com|youtu\.be|youtube-nocookie\.com)"),
]

AUTO_CAPABLE = {"抖音", "快手", "小红书"}

# YouTube 把字幕当公开数据发，不下载视频也不用转写就能出稿，走独立脚本
SUBTITLE_CAPABLE = {"YouTube"}

# 这些平台的 web 接口要求带登录态的新鲜 Cookie，裸请求必定失败，直接从借 Cookie 开始试
NEEDS_COOKIES = {"抖音"}

BROWSER_CANDIDATES = ("chrome", "brave", "edge", "firefox", "safari")

SHORTLINK_PATTERN = r"(v\.douyin\.com|iesdouyin\.com/share|v\.kuaishou\.com|xhslink\.com)"

AWEME_ID_PATTERN = re.compile(r"/(?:video|note|slides)/(\d+)")

USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
)

GUIDES = {
    "抖音": """抖音取件：

  0. 先排查自动下载为什么失败——它本来是能成的，前提是浏览器里有抖音的有效登录态：
     用 Chrome 打开 www.douyin.com 并确认已登录，然后重跑本命令。
     指定浏览器：python3 scripts/fetch.py --browser chrome <链接>
  1. APP 内保存：视频右侧「分享」→「保存到相册」。作者关闭了下载权限时此项灰掉，走第 2 条。
  2. 电脑端录屏：浏览器打开 www.douyin.com 找到该视频，QuickTime（macOS）录屏，
     或按 Shift+Cmd+5 选区域录制。只需要声音，画质无所谓。
  3. 手机录屏：iOS 控制中心「屏幕录制」，Android 通知栏「屏幕录制」。记得开麦克风外的
     「内部音频」，否则录不到声音。

拿到 mp4/mov 后：python3 scripts/transcribe.py --input 文件路径""",

    "快手": """快手取件：

  0. 先试借浏览器 Cookie：用浏览器打开 www.kuaishou.com 登录后重跑本命令
     （python3 scripts/fetch.py --browser chrome <链接>）。
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

    "YouTube": """YouTube 有字幕轨，走 scripts/youtube.py，几秒出稿，不用下载也不用转写：

  python3 scripts/youtube.py "<链接>"
  python3 scripts/youtube.py --list-tracks "<链接>"    # 先看有哪些字幕轨

没有字幕轨时（作者没传，YouTube 也没生成）才回落到 ASR：

  python3 scripts/youtube.py --audio "<链接>"
  python3 scripts/transcribe.py --input <音频文件>""",
}


def detect_platform(url: str) -> str | None:
    for name, pattern in PLATFORM_PATTERNS:
        if re.search(pattern, url, re.IGNORECASE):
            return name
    return None


def resolve_redirects(url: str) -> str:
    """跟随公开重定向拿到最终地址，失败就原样返回。"""
    for method in ("HEAD", "GET"):
        request = urllib.request.Request(url, method=method, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                return response.url
        except Exception:
            continue
    return url


def canonicalize(url: str, platform: str | None) -> str:
    """把分享短链还原成规范的作品地址。

    抖音的 v.douyin.com 短链直接交给 yt-dlp 会报「Fresh cookies are needed」，
    即使带了 Cookie 也一样——重定向链路会把提取器需要的那份 Cookie 冲掉。
    先自己跟完重定向、取出作品 ID 重新拼成 www.douyin.com/video/<id> 就能下成功。
    """
    if not re.search(SHORTLINK_PATTERN, url, re.IGNORECASE):
        return url
    final = resolve_redirects(url)
    if platform == "抖音":
        if match := AWEME_ID_PATTERN.search(final):
            return f"https://www.douyin.com/video/{match.group(1)}"
    return final


def cookie_attempts(platform: str | None, browsers: list[str], use_cookies: bool) -> list[str | None]:
    """返回依次尝试的 Cookie 来源，None 表示不带 Cookie。"""
    if not use_cookies:
        return [None]
    if platform in NEEDS_COOKIES:
        return [*browsers, None]
    return [None, *browsers]


def run_yt_dlp(binary: str, url: str, out_dir: Path, browser: str | None,
               audio_only: bool) -> tuple[Path | None, str]:
    command = [binary, "--no-playlist", "--no-warnings",
               "-o", str(out_dir / "%(id)s.%(ext)s"),
               "--print", "after_move:filepath"]
    if browser:
        command += ["--cookies-from-browser", browser]
    if audio_only:
        command += ["--extract-audio"]
    result = subprocess.run([*command, url], capture_output=True, text=True)
    if result.returncode != 0:
        detail = (result.stderr or "").strip().splitlines()
        return None, detail[-1] if detail else "无错误输出"
    lines = (result.stdout or "").strip().splitlines()
    path = Path(lines[-1]) if lines else None
    return (path, "") if path and path.is_file() else (None, "yt-dlp 没有输出文件路径")


def missing_browser(error: str) -> bool:
    lowered = error.lower()
    return "could not find" in lowered or "unsupported browser" in lowered


def download(url: str, platform: str | None, out_dir: Path, browsers: list[str],
             use_cookies: bool, audio_only: bool) -> Path | None:
    binary = shutil.which("yt-dlp")
    if not binary:
        print("未安装 yt-dlp，跳过自动下载（pip install yt-dlp 或 brew install yt-dlp）\n",
              file=sys.stderr)
        return None
    out_dir.mkdir(parents=True, exist_ok=True)

    last_error = "无错误输出"
    for browser in cookie_attempts(platform, browsers, use_cookies):
        label = f"借用 {browser} 的 Cookie" if browser else "不带 Cookie"
        print(f"尝试 yt-dlp（{label}）……", file=sys.stderr)
        path, error = run_yt_dlp(binary, url, out_dir, browser, audio_only)
        if path:
            return path
        if browser and missing_browser(error):
            continue
        last_error = error

    print(f"yt-dlp 失败：{last_error}", file=sys.stderr)
    if "cookie" in last_error.lower():
        print("这个报错的意思是平台要求带登录态的新鲜 Cookie。请先用浏览器打开并登录该平台，"
              "再重跑本命令；也可以用 --browser 指定到你实际登录的那个浏览器。\n", file=sys.stderr)
    return None


def delegate_to_youtube(url: str, args: argparse.Namespace) -> int:
    """YouTube 交给 youtube.py：它先取字幕，取不到才回落到下音频。

    转交而不是在这里重实现，是因为字幕路径产出的直接是口播稿，
    和本脚本「下文件再转写」的产出根本不是一回事。
    """
    command = [sys.executable, str(Path(__file__).with_name("youtube.py")), url,
               "--video-dir", args.out_dir]
    if args.browser:
        command += ["--browser", args.browser]
    return subprocess.run(command).returncode


def collect_urls(args: argparse.Namespace) -> list[str]:
    urls = list(args.urls)
    if args.from_file:
        text = Path(args.from_file).read_text(encoding="utf-8")
        urls += [line.strip() for line in text.splitlines()
                 if line.strip() and not line.startswith("#")]
    return urls


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="短视频链接取件")
    parser.add_argument("urls", nargs="*", help="分享链接，可给多个")
    parser.add_argument("--from-file", help="从文件读链接，一行一条")
    parser.add_argument("--out-dir", default="output/videos", help="下载目录")
    parser.add_argument("--browser", choices=list(BROWSER_CANDIDATES),
                        help="指定借用哪个浏览器的 Cookie，省略则依次探测")
    parser.add_argument("--no-cookies", action="store_true", help="不借用浏览器 Cookie")
    parser.add_argument("--audio-only", action="store_true", help="只保留音频，转写更快更省空间")
    parser.add_argument("--no-download", action="store_true", help="只识别平台并打印取件指引")
    parser.add_argument("--guide", choices=list(GUIDES), help="直接查看某个平台的取件指引")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    if args.guide:
        print(GUIDES[args.guide])
        return 0

    urls = collect_urls(args)
    if not urls:
        sys.exit("缺少链接。用法：python3 fetch.py <url> [url ...]，"
                 "或 --from-file urls.txt，或 --guide 抖音|快手|小红书|视频号|YouTube")

    browsers = [args.browser] if args.browser else list(BROWSER_CANDIDATES)
    out_dir = Path(args.out_dir)
    done: list[tuple[str, Path]] = []
    direct: list[str] = []          # 走字幕已经出稿的，不需要再转写
    manual: list[tuple[str, str | None]] = []

    for index, url in enumerate(urls, start=1):
        if len(urls) > 1:
            print(f"\n[{index}/{len(urls)}] {url}", file=sys.stderr)
        platform = detect_platform(url)
        if platform is None:
            print("无法识别平台。支持：抖音 / 快手 / 小红书 / 视频号 / YouTube", file=sys.stderr)
            print("如果这是其他站点的直链，装了 yt-dlp 可以直接试：yt-dlp <url>", file=sys.stderr)
            manual.append((url, None))
            continue

        print(f"平台：{platform}", file=sys.stderr)

        if platform in SUBTITLE_CAPABLE:
            if delegate_to_youtube(url, args) == 0:
                direct.append(url)
            else:
                manual.append((url, platform))
            continue

        target = canonicalize(url, platform)
        if target != url:
            print(f"短链已还原：{target}", file=sys.stderr)

        if platform in AUTO_CAPABLE and not args.no_download:
            if path := download(target, platform, out_dir, browsers,
                                not args.no_cookies, args.audio_only):
                print(f"已下载：{path}", file=sys.stderr)
                done.append((url, path))
                continue
        manual.append((url, platform))

    if direct:
        print(f"\n{len(direct)} 条走字幕直接出稿，不用转写，产物路径见上方，可以直接进拆解。")

    if done:
        print(f"\n取到 {len(done)} 个文件：")
        for url, path in done:
            print(f"  {path}")
        print("\n下一步转写：")
        for _, path in done:
            print(f"  python3 scripts/transcribe.py --input {path}")

    if manual:
        print(f"\n还有 {len(manual)} 条需要手动取件：", file=sys.stderr)
        for url, platform in manual:
            print(f"  {url}（{platform or '未识别平台'}）", file=sys.stderr)
        for platform in dict.fromkeys(p for _, p in manual if p):
            print(f"\n{GUIDES[platform]}", file=sys.stderr)
        return EXIT_NEEDS_MANUAL
    return 0


if __name__ == "__main__":
    sys.exit(main())
