#!/usr/bin/env python3
"""从 YouTube 抽口播文案。有字幕轨就直接取，几秒出稿；没有才回落到 ASR。

这是 YouTube 与国内四平台最大的差别：YouTube 把字幕当公开数据发，
`yt-dlp -J` 返回的元信息里直接带字幕轨地址，不需要下载视频、不需要转写。
一条 20 分钟的视频通常 3 秒内出稿，而 ASR 要跑几分钟。

设计边界同 fetch.py：不含协议逆向、签名伪造、加密绕过。取字幕用 yt-dlp
公开的元信息接口，下载受限时借用户自己浏览器里的 Cookie。

用法:
    python3 youtube.py "https://www.youtube.com/watch?v=xxx"      # 字幕优先，取不到给 ASR 指引
    python3 youtube.py --list-tracks URL                          # 只看有哪些字幕轨
    python3 youtube.py --lang zh-Hans URL                         # 指定字幕语言偏好
    python3 youtube.py --allow-translated URL                     # 原语言没有时接受机翻轨
    python3 youtube.py --audio URL                                # 跳过字幕，直接下音频交给 ASR
    python3 youtube.py --browser chrome URL                       # 借浏览器 Cookie（会员/年龄限制视频）
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from transcribe import (  # noqa: E402
    CHARS_PER_MINUTE,
    normalize,
    split_into_sentences,
    to_script,
)

EXIT_NEEDS_MANUAL = 3

URL_PATTERN = r"(youtube\.com|youtu\.be|youtube-nocookie\.com)"

# YouTube 对不同 player client 的风控强度不一样，且时常变动。默认那套被拦时按序换一个
# 再试——这是 yt-dlp 支持的公开参数，不是绕过手段。android 通常最稳。
PLAYER_CLIENTS = (None, "android", "default,-web_safari", "tv", "ios")

# 自动字幕没有标点，只能按词间静默切句。0.6 秒是口播里一个自然停顿的下限。
SILENCE_GAP = 0.6
MAX_SENTENCE_CHARS = 35
MAX_SENTENCE_WORDS = 14
# 长视频的拆解重点在章节骨架而不是逐句钩子，超过这个长度就在报告里提示换读法
LONG_VIDEO_SECONDS = 300
WORDS_PER_MINUTE = 150

NO_SUBS_GUIDE = """这条视频没有可用字幕轨（作者没上传，YouTube 也没有生成自动字幕）。

字幕这条快路走不通，回落到 ASR：

  python3 scripts/youtube.py --audio <链接>        # 下音频
  python3 scripts/transcribe.py --input <音频文件>  # 转写

音频是完整的 20 分钟也没关系，faster-whisper 在 Apple Silicon 上大约 1:6 实时。"""


# ────────────────────────── 元信息与字幕轨 ──────────────────────────

def is_youtube(url: str) -> bool:
    return bool(re.search(URL_PATTERN, url, re.IGNORECASE))


def yt_dlp_binary() -> str:
    if binary := shutil.which("yt-dlp"):
        return binary
    sys.exit("需要 yt-dlp：brew install yt-dlp（或 pip install yt-dlp）")


def probe(url: str, browser: str | None) -> tuple[dict, str | None]:
    """拿视频元信息。返回 (元信息, 生效的 player client)。

    默认 client 被风控拦下时按 PLAYER_CLIENTS 依次换，全试完才放弃。
    """
    binary = yt_dlp_binary()
    last_error = "无错误输出"
    for client in PLAYER_CLIENTS:
        command = [binary, "-J", "--no-playlist", "--no-warnings", "--skip-download"]
        if client:
            command += ["--extractor-args", f"youtube:player_client={client}"]
        if browser:
            command += ["--cookies-from-browser", browser]
        result = subprocess.run([*command, url], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip():
            try:
                return json.loads(result.stdout), client
            except json.JSONDecodeError:
                last_error = "yt-dlp 返回的不是合法 JSON"
                continue
        detail = (result.stderr or "").strip().splitlines()
        last_error = detail[-1] if detail else last_error
        if client is None:
            print(f"默认 client 失败：{last_error}", file=sys.stderr)
            print("换 player client 重试……", file=sys.stderr)

    print(f"\nyt-dlp 取不到视频信息：{last_error}", file=sys.stderr)
    if "sign in" in last_error.lower() or "cookies" in last_error.lower():
        print("这条报错通常意味着需要登录态。用浏览器打开并登录 YouTube，再加 --browser chrome 重跑。",
              file=sys.stderr)
    sys.exit(EXIT_NEEDS_MANUAL)


def is_translated(formats: list[dict]) -> bool:
    """机翻轨的地址带 tlang 参数，原语言轨不带。"""
    return all("tlang=" in (item.get("url") or "") for item in formats)


def language_score(lang: str, prefer: list[str], native: str | None) -> int:
    """轨道语言与目标的接近程度，越大越优先。

    用户显式指定的偏好排在视频原语言之前——写了 --lang ja 就是想要日文轨，
    不该被"这视频原本是英文的"覆盖掉。原语言只作为偏好都不命中时的兜底。
    """
    base = lang.replace("-orig", "")
    for index, want in enumerate(prefer):
        if base.lower() == want.lower():
            return 90 - index
        if base.split("-")[0].lower() == want.split("-")[0].lower():
            return 80 - index
    return 50 if native and base.lower() == native.lower() else 0


def pick_track(meta: dict, prefer: list[str], allow_translated: bool) -> dict | None:
    """选一条字幕轨。手动字幕 > 原语言自动字幕 > 机翻自动字幕。

    机翻轨是从原语言机器翻译过来的，用来做文案拆解会把对方的用词习惯洗掉，
    默认不选，除非显式 --allow-translated。
    """
    native = meta.get("language")
    candidates = []
    for kind, source in (("手动字幕", meta.get("subtitles")), ("自动字幕", meta.get("automatic_captions"))):
        for lang, formats in (source or {}).items():
            if not formats:
                continue
            translated = kind == "自动字幕" and is_translated(formats)
            if translated and not allow_translated:
                continue
            candidates.append({
                "lang": lang,
                "kind": kind,
                "translated": translated,
                "formats": formats,
                "score": (
                    (200 if kind == "手动字幕" else 0)
                    - (150 if translated else 0)
                    + language_score(lang, prefer, native)
                ),
            })
    if not candidates:
        return None
    return max(candidates, key=lambda item: item["score"])


def list_tracks(meta: dict) -> None:
    native = meta.get("language")
    print(f"标题：{meta.get('title')}")
    print(f"时长：{format_clock(meta.get('duration') or 0)}    原语言：{native or '未标注'}\n")
    if manual := list((meta.get("subtitles") or {}).keys()):
        print(f"手动字幕：{', '.join(manual)}")
    else:
        print("手动字幕：无")

    auto = meta.get("automatic_captions") or {}
    if not auto:
        print("自动字幕：无")
        return
    originals = [lang for lang, formats in auto.items() if formats and not is_translated(formats)]
    print(f"自动字幕：共 {len(auto)} 种")
    print(f"  原语言轨：{', '.join(originals) if originals else '无（全是机翻）'}")
    if others := [lang for lang in auto if lang not in originals]:
        preview = ", ".join(others[:12])
        tail = f" …… 等 {len(others)} 种" if len(others) > 12 else ""
        print(f"  机翻轨：{preview}{tail}")


def fetch_subtitle(track: dict, url: str, client: str | None, browser: str | None) -> list[dict]:
    """取字幕内容。优先直接下 json3（带词级时间戳），拿不到再退回 yt-dlp 写文件。"""
    for item in track["formats"]:
        if item.get("ext") == "json3" and item.get("url"):
            if raw := http_get(item["url"]):
                return parse_json3(raw)
    return parse_json3(download_via_yt_dlp(track, url, client, browser))


def http_get(url: str) -> str | None:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError, OSError):
        return None


def download_via_yt_dlp(track: dict, url: str, client: str | None, browser: str | None) -> str:
    flag = "--write-subs" if track["kind"] == "手动字幕" else "--write-auto-subs"
    with tempfile.TemporaryDirectory() as tmp:
        prefix = Path(tmp) / "sub"
        command = [
            yt_dlp_binary(), "--skip-download", flag,
            "--sub-langs", track["lang"], "--sub-format", "json3",
            "--no-playlist", "--no-warnings", "-o", str(prefix), url,
        ]
        if client:
            command += ["--extractor-args", f"youtube:player_client={client}"]
        if browser:
            command += ["--cookies-from-browser", browser]
        subprocess.run(command, capture_output=True, text=True)
        if files := sorted(Path(tmp).glob("sub*.json3")):
            return files[0].read_text(encoding="utf-8")
    sys.exit("字幕轨存在但下载失败。换 --lang 指定别的语言，或用 --audio 走 ASR。")


# ────────────────────────── json3 解析 ──────────────────────────

def parse_json3(raw: str) -> list[dict]:
    """把 YouTube 的 json3 字幕解析成 transcribe.py 的分段结构。

    要处理三种噪声：窗口定义事件（没有 segs）、滚动字幕的续行事件（aAppend=1，
    内容只是把上一条重复一遍）、以及 [Music] [Applause] 这类音效标注。
    """
    try:
        events = json.loads(raw).get("events") or []
    except json.JSONDecodeError:
        sys.exit("字幕文件解析失败，格式不是预期的 json3。")

    segments = []
    for event in events:
        if event.get("aAppend") or not event.get("segs"):
            continue
        start = event.get("tStartMs", 0) / 1000
        words = []
        for seg in event["segs"]:
            text = (seg.get("utf8") or "").replace("\n", " ")
            if not text.strip():
                continue
            words.append({
                "start": start + seg.get("tOffsetMs", 0) / 1000,
                "end": start + seg.get("tOffsetMs", 0) / 1000,
                "text": text,
            })
        if not words:
            continue
        text = strip_sound_tags("".join(word["text"] for word in words))
        if not text.strip():
            continue
        end = start + event.get("dDurationMs", 0) / 1000
        for index, word in enumerate(words):
            word["end"] = words[index + 1]["start"] if index + 1 < len(words) else end
        # 每行字幕的首词不带前导空格，跨行拼接会把两个英文单词粘成一个。
        # 中文路径的清洗会把空格全删掉，所以在这里统一补是安全的。
        if segments and not words[0]["text"][:1].isspace():
            words[0]["text"] = " " + words[0]["text"]
        segments.append({"start": round(start, 2), "end": round(end, 2),
                         "text": text.strip(), "words": words})
    return merge_overlaps(segments)


def strip_sound_tags(text: str) -> str:
    return re.sub(r"[\[［【]\s*(music|applause|laughter|音乐|掌声|笑声)\s*[\]］】]", "", text, flags=re.IGNORECASE)


def merge_overlaps(segments: list[dict]) -> list[dict]:
    """自动字幕相邻条目的时间区间会互相重叠，末尾时间取下一条的开始更准。"""
    for index in range(len(segments) - 1):
        if segments[index]["end"] > segments[index + 1]["start"]:
            segments[index]["end"] = segments[index + 1]["start"]
    return segments


# ────────────────────────── 断句 ──────────────────────────

def looks_chinese(text: str) -> bool:
    han = len(re.findall(r"[\u4e00-\u9fff]", text))
    return han > len(text) * 0.3


def clean_text(text: str, chinese: bool) -> str:
    """中文走 transcribe 那套（去空格、标点转全角），英文只压空白——
    那套清洗会把英文单词间的空格一并删掉。"""
    return normalize(text) if chinese else re.sub(r"\s+", " ", text).strip()


def to_sentences(segments: list[dict], chinese: bool, rolling: bool) -> list[dict]:
    """自动字幕是滚动流，换行只是显示需要，得按词流重新断句；
    手动字幕的行是作者自己断的，尊重它，只在一行里有多句时再切。"""
    if rolling:
        return split_words([word for seg in segments for word in seg["words"]], chinese)
    return split_cues(segments, chinese)


def split_cues(segments: list[dict], chinese: bool) -> list[dict]:
    sentences = []
    for seg in segments:
        text = clean_text(seg["text"], chinese)
        if not text:
            continue
        entry = {**seg, "text": text}
        sentences += split_into_sentences([entry]) if chinese else split_english_cue(entry)
    return sentences


def split_english_cue(seg: dict) -> list[dict]:
    """一行英文字幕里含多句时按句末标点切开，时间按字符数线性插值。"""
    pieces = [piece.strip() for piece in re.split(r"(?<=[.!?])\s+", seg["text"]) if piece.strip()]
    if len(pieces) <= 1:
        return [{"start": seg["start"], "end": seg["end"], "text": seg["text"]}]
    total = sum(len(piece) for piece in pieces) or 1
    span = max(seg["end"] - seg["start"], 0.0)
    result, offset = [], 0
    for piece in pieces:
        start = seg["start"] + span * offset / total
        offset += len(piece)
        result.append({"start": round(start, 2),
                       "end": round(seg["start"] + span * offset / total, 2), "text": piece})
    return result


def speaking_gap(word: dict, following: dict | None, chinese: bool) -> float:
    """两个词之间的静默时长。

    json3 只给每个词的起点，不给终点，所以直接相减得到的是「起点间隔」，
    里面还含着前一个词自己的发音时间。按语速折算扣掉，剩下的才是停顿。
    """
    if not following:
        return 0.0
    text = word["text"].strip()
    spoken = len(text) * 60 / CHARS_PER_MINUTE if chinese else max(len(text.split()), 1) * 60 / WORDS_PER_MINUTE
    return following["start"] - word["start"] - spoken


def split_words(words: list[dict], chinese: bool) -> list[dict]:
    """按词级时间戳断句：优先句末标点，其次说话停顿，都没有就按长度兜底。

    中文自动字幕通常一个标点都没有，全靠停顿。切完不补标点——ASR 没听出来的
    句读不该由我们臆断，拆解看的是语义单元和秒数，句号在哪不影响结论。
    """
    limit = MAX_SENTENCE_CHARS if chinese else MAX_SENTENCE_WORDS
    sentences, buffer = [], []

    def flush() -> None:
        if not buffer:
            return
        text = clean_text("".join(word["text"] for word in buffer), chinese)
        if text:
            sentences.append({"start": round(buffer[0]["start"], 2),
                              "end": round(buffer[-1]["end"], 2), "text": text})
        buffer.clear()

    for index, word in enumerate(words):
        buffer.append(word)
        joined = "".join(item["text"] for item in buffer)
        size = len(re.sub(r"\s", "", joined)) if chinese else len(joined.split())
        following = words[index + 1] if index + 1 < len(words) else None
        gap = speaking_gap(word, following, chinese)
        if re.search(r"[。！？.!?]$", word["text"].strip()) or size >= limit or gap >= SILENCE_GAP:
            flush()
    flush()
    return sentences


# ────────────────────────── 报告 ──────────────────────────

def format_clock(seconds: float) -> str:
    minutes, rest = divmod(int(seconds), 60)
    return f"{minutes}:{rest:02d}"


def rhythm_report(meta: dict, track: dict, sentences: list[dict], script: str, chinese: bool) -> str:
    duration = meta.get("duration") or (sentences[-1]["end"] if sentences else 0)
    if chinese:
        count = len(re.sub(r"\s", "", script))
        unit, pace = "字", CHARS_PER_MINUTE
    else:
        count = len(script.split())
        unit, pace = "词", WORDS_PER_MINUTE

    lines = [
        f"标题：{meta.get('title')}",
        f"频道：{meta.get('channel') or meta.get('uploader') or '未知'}",
        f"字幕来源：{track['kind']}（{track['lang']}）" + ("，机翻轨，用词非原文" if track["translated"] else ""),
        f"总时长：{duration:.0f} 秒（{format_clock(duration)}）",
        f"{unit}数：{count} {unit}",
    ]
    if duration > 0:
        lines.append(f"语速：{count / duration * 60:.0f} {unit}/分钟（口播经验值 {pace} 上下）")
    if meta.get("view_count"):
        lines.append(f"播放：{meta['view_count']:,}    点赞：{meta.get('like_count') or '未公开'}")

    if chapters := meta.get("chapters"):
        lines += ["", f"作者自己标的章节（{len(chapters)} 段，等于他给出的结构骨架）："]
        for chapter in chapters:
            lines.append(f"  [{format_clock(chapter.get('start_time', 0)):>6}] {chapter.get('title', '')}")

    if duration >= LONG_VIDEO_SECONDS:
        lines += ["", f"注意：这是 {format_clock(duration)} 的长视频，不是短视频。"
                      "拆解重点放在开场钩子和章节骨架上，逐句钩子密度那套指标不适用。"]

    lines += ["", "分段时间轴（用于定位黄金 3 秒与中段钩子）："]
    for seg in sentences:
        lines.append(f"  [{seg['start']:>7.1f}s] {seg['text']}")
    return "\n".join(lines)


def download_audio(url: str, out_dir: Path, browser: str | None) -> Path | None:
    """下音频交给 ASR。YouTube 的风控经常只拦某几个 client，逐个换着试。"""
    binary = yt_dlp_binary()
    out_dir.mkdir(parents=True, exist_ok=True)
    last_error = "无错误输出"
    for client in PLAYER_CLIENTS:
        command = [
            binary, "-f", "bestaudio/best", "--extract-audio", "--audio-format", "m4a",
            "--no-playlist", "--no-warnings", "-o", str(out_dir / "%(id)s.%(ext)s"),
            "--print", "after_move:filepath", url,
        ]
        if client:
            command += ["--extractor-args", f"youtube:player_client={client}"]
        if browser:
            command += ["--cookies-from-browser", browser]
        print(f"下载音频（client={client or '默认'}）……", file=sys.stderr)
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip():
            path = Path(result.stdout.strip().splitlines()[-1])
            if path.is_file():
                return path
        detail = (result.stderr or "").strip().splitlines()
        last_error = detail[-1] if detail else last_error
    print(f"\n音频下载失败：{last_error}", file=sys.stderr)
    print("YouTube 对下载的限制时松时紧。先试 --browser chrome 借登录态；"
          "还不行就升级 yt-dlp（brew upgrade yt-dlp），风控变动通常几天内会被跟进。", file=sys.stderr)
    return None


# ────────────────────────── 主流程 ──────────────────────────

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="YouTube 字幕抽取")
    parser.add_argument("url", nargs="?", help="YouTube 链接")
    parser.add_argument("--lang", default="zh-Hans,zh,en",
                        help="字幕语言偏好，逗号分隔，默认 zh-Hans,zh,en")
    parser.add_argument("--allow-translated", action="store_true",
                        help="原语言字幕没有时接受机翻轨")
    parser.add_argument("--list-tracks", action="store_true", help="只列出可用字幕轨")
    parser.add_argument("--audio", action="store_true", help="跳过字幕，直接下音频给 ASR")
    parser.add_argument("--browser", help="借用哪个浏览器的 Cookie，如 chrome")
    parser.add_argument("--out-dir", default="output/transcripts", help="文稿输出目录")
    parser.add_argument("--video-dir", default="output/videos", help="音频下载目录")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if not args.url:
        sys.exit("缺少链接。用法：python3 youtube.py <YouTube 链接>")
    if not is_youtube(args.url):
        sys.exit("这不是 YouTube 链接。国内平台走 scripts/fetch.py。")

    if args.audio:
        path = download_audio(args.url, Path(args.video_dir), args.browser)
        if not path:
            return EXIT_NEEDS_MANUAL
        print(f"已下载：{path}")
        print(f"\n下一步：python3 scripts/transcribe.py --input {path}")
        return 0

    meta, client = probe(args.url, args.browser)

    if args.list_tracks:
        list_tracks(meta)
        return 0

    prefer = [item.strip() for item in args.lang.split(",") if item.strip()]
    track = pick_track(meta, prefer, args.allow_translated)
    if track is None:
        has_translated = bool(meta.get("automatic_captions"))
        print(f"标题：{meta.get('title')}\n", file=sys.stderr)
        if has_translated and not args.allow_translated:
            print("只有机翻字幕轨，没有原语言轨。机翻会洗掉对方的用词习惯，默认不用。",
                  file=sys.stderr)
            print("确实要用就加 --allow-translated，否则按下面走 ASR。\n", file=sys.stderr)
        print(NO_SUBS_GUIDE, file=sys.stderr)
        return EXIT_NEEDS_MANUAL

    print(f"标题：{meta.get('title')}", file=sys.stderr)
    print(f"字幕轨：{track['kind']}（{track['lang']}）", file=sys.stderr)
    if track["translated"]:
        print("这是机翻轨，用词不是作者原话，只能看结构不能看措辞。", file=sys.stderr)

    segments = fetch_subtitle(track, args.url, client, args.browser)
    if not segments:
        print("字幕轨是空的。", file=sys.stderr)
        print(NO_SUBS_GUIDE, file=sys.stderr)
        return EXIT_NEEDS_MANUAL

    chinese = looks_chinese("".join(seg["text"] for seg in segments))
    sentences = to_sentences(segments, chinese, rolling=track["kind"] == "自动字幕")
    script = to_script(sentences)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = meta.get("id") or "youtube"
    (out_dir / f"{stem}.txt").write_text(script + "\n", encoding="utf-8")
    (out_dir / f"{stem}.rhythm.txt").write_text(
        rhythm_report(meta, track, sentences, script, chinese) + "\n", encoding="utf-8")
    (out_dir / f"{stem}.segments.json").write_text(
        json.dumps(sentences, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"\n口播稿：{out_dir / f'{stem}.txt'}")
    print(f"节奏分析：{out_dir / f'{stem}.rhythm.txt'}")
    print(f"时间戳：{out_dir / f'{stem}.segments.json'}\n")
    print(script)
    return 0


if __name__ == "__main__":
    sys.exit(main())
