#!/usr/bin/env python3
"""对抖音视频候选列表做数值解析、阈值筛选、去重与排序，并导出待抽文案的链接清单。

计数解析逻辑与 douyin-benchmark-accounts 里的那份是故意重复的：两个 skill 各自
独立安装，不能跨目录 import，宁可重复也不制造隐式依赖。

输入 JSON（数组，或 {"videos": [...]}）字段：
    url        必填  视频链接（用于去重，支持 /video/<id> 与 v.douyin.com 短链）
    title      选填  视频标题/描述文案（口播稿的一部分，通常不完整）
    likes      选填  点赞数展示值，如 "1.2万"
    comments   选填  评论数展示值
    collects   选填  收藏数
    shares     选填  转发数
    author     选填  作者昵称
    published  选填  发布时间展示值
    duration   选填  时长，如 "1:23"
    keyword    选填  命中的搜索关键词

用法:
    python3 filter_videos.py --input candidates.json --min-likes 300 --min-comments 20 --top 10
    cat candidates.json | python3 filter_videos.py --top 10 --sort engagement
    python3 filter_videos.py --self-test
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from datetime import date, datetime
from pathlib import Path


# ────────────────────────── 解析 ──────────────────────────

def parse_count(raw) -> int | None:
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        return int(raw)
    text = str(raw).strip().replace(",", "").replace("，", "")
    if not text:
        return None
    text = text.rstrip("+")
    match = re.fullmatch(r"(\d+(?:\.\d+)?)\s*([万wW亿kK]?)", text)
    if not match:
        return None
    value, unit = float(match.group(1)), match.group(2)
    multiplier = {"": 1, "w": 10_000, "W": 10_000, "万": 10_000,
                  "亿": 100_000_000, "k": 1_000, "K": 1_000}[unit]
    return int(value * multiplier)


def parse_days_ago(raw, today: date | None = None) -> int | None:
    if raw is None:
        return None
    text = str(raw).strip()
    if not text:
        return None
    today = today or date.today()
    if re.search(r"刚刚|秒前|分钟前|小时前|今天", text):
        return 0
    if "昨天" in text:
        return 1
    if "前天" in text:
        return 2
    if match := re.search(r"(\d+)\s*天前", text):
        return int(match.group(1))
    if match := re.search(r"(\d+)\s*周前", text):
        return int(match.group(1)) * 7
    if match := re.search(r"(\d+)\s*个?月前", text):
        return int(match.group(1)) * 30
    if match := re.search(r"(\d+)\s*年前", text):
        return int(match.group(1)) * 365
    if match := re.search(r"(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})", text):
        year, month, day = (int(g) for g in match.groups())
    elif match := re.search(r"(\d{1,2})[-/月](\d{1,2})", text):
        year, month, day = today.year, int(match.group(1)), int(match.group(2))
    else:
        return None
    try:
        posted = date(year, month, day)
    except ValueError:
        return None
    if posted > today and not re.search(r"\d{4}", text):
        posted = date(year - 1, month, day)
    return (today - posted).days


def parse_duration(raw) -> int | None:
    """把 "1:23" / "01:02:03" 解析成秒。"""
    if raw is None:
        return None
    text = str(raw).strip()
    if not text or not re.fullmatch(r"(\d+:)?\d{1,2}:\d{2}", text):
        return None
    parts = [int(p) for p in text.split(":")]
    while len(parts) < 3:
        parts.insert(0, 0)
    hours, minutes, seconds = parts
    return hours * 3600 + minutes * 60 + seconds


def video_id(url: str) -> str:
    """从链接里取视频 ID 做去重；短链取路径末段。"""
    url = (url or "").strip()
    if match := re.search(r"/video/(\d+)", url):
        return match.group(1)
    if match := re.search(r"modal_id=(\d+)", url):
        return match.group(1)
    if match := re.search(r"v\.douyin\.com/([\w-]+)", url):
        return match.group(1)
    return url.rstrip("/")


# ────────────────────────── 筛选 ──────────────────────────

def normalize(video: dict, today: date | None = None) -> dict:
    likes = parse_count(video.get("likes"))
    comments = parse_count(video.get("comments"))
    return {
        "vid": video_id(video.get("url", "")),
        "url": (video.get("url") or "").strip(),
        "title": (video.get("title") or "").strip(),
        "author": (video.get("author") or "").strip(),
        "likes": likes,
        "likes_raw": video.get("likes"),
        "comments": comments,
        "comments_raw": video.get("comments"),
        "collects": parse_count(video.get("collects")),
        "shares": parse_count(video.get("shares")),
        "days_ago": parse_days_ago(video.get("published"), today),
        "published_raw": video.get("published"),
        "duration_sec": parse_duration(video.get("duration")),
        "duration_raw": video.get("duration"),
        "keyword": (video.get("keyword") or "").strip(),
        "engagement": round(comments / likes, 4) if likes and comments else None,
    }


def dedupe(videos: list[dict]) -> tuple[list[dict], int]:
    seen: dict[str, dict] = {}
    dropped = 0
    for video in videos:
        key = video["vid"]
        if key in seen:
            dropped += 1
            existing = seen[key]
            keywords = {k for k in (existing["keyword"], video["keyword"]) if k}
            existing["keyword"] = " / ".join(sorted(keywords))
            for field in ("likes", "comments", "days_ago", "duration_sec", "title"):
                if not existing.get(field) and video.get(field):
                    existing[field] = video[field]
        else:
            seen[key] = video
    return list(seen.values()), dropped


def judge(video: dict, min_likes: int, min_comments: int) -> str:
    if not video["url"]:
        return "缺视频链接"
    if video["likes"] is None:
        return "点赞数未读到，需人工确认"
    if video["likes"] < min_likes:
        return f"点赞 {video['likes']} < {min_likes}"
    if video["comments"] is None:
        return "评论数未读到，需进详情页确认"
    if video["comments"] < min_comments:
        return f"评论 {video['comments']} < {min_comments}"
    return ""


# ────────────────────────── 输出 ──────────────────────────

def format_count(value: int | None) -> str:
    if value is None:
        return "未知"
    if value >= 10_000:
        return f"{value / 10_000:.1f}".rstrip("0").rstrip(".") + "万"
    return str(value)


def format_duration(seconds: int | None) -> str:
    if seconds is None:
        return "未知"
    return f"{seconds // 60}:{seconds % 60:02d}"


def render_markdown(passed: list[dict], excluded: list[tuple[dict, str]],
                    args: argparse.Namespace, dup: int) -> str:
    total = len(passed) + len(excluded) + dup
    lines = [
        "# 抖音对标视频筛选结果",
        "",
        f"**筛选条件**：点赞 > {args.min_likes}　评论 > {args.min_comments}",
        f"**结果**：候选 {total} 条 → 去重 {dup} 条 → 通过 {len(passed)} 条 → "
        f"取前 {min(args.top, len(passed))} 条",
        "",
        "| # | 标题 | 作者 | 点赞 | 评论 | 互动率 | 时长 | 发布 | 链接 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for index, video in enumerate(passed[: args.top], start=1):
        title = video["title"][:24] + ("…" if len(video["title"]) > 24 else "")
        rate = f"{video['engagement'] * 100:.1f}%" if video["engagement"] else "-"
        published = f"{video['days_ago']}天前" if video["days_ago"] is not None else "未知"
        lines.append(
            f"| {index} | {title or '(无标题)'} | {video['author'] or '-'} | "
            f"{format_count(video['likes'])} | {format_count(video['comments'])} | {rate} | "
            f"{format_duration(video['duration_sec'])} | {published} | {video['url']} |"
        )
    lines += ["", "> 互动率 = 评论数 ÷ 点赞数。这个值明显偏高的，通常是有争议或强共鸣的选题，"
                  "拆解价值比单纯高赞的更大。"]
    if excluded:
        lines += ["", "## 被排除的候选", "", "| 标题 | 点赞 | 评论 | 排除原因 |", "| --- | --- | --- | --- |"]
        for video, reason in excluded:
            title = video["title"][:20] or "(无标题)"
            lines.append(f"| {title} | {video['likes_raw'] or '-'} | {video['comments_raw'] or '-'} | {reason} |")
    pending = [v for v, r in excluded if "需进详情页" in r or "需人工确认" in r]
    if pending:
        lines += ["", f"> 有 {len(pending)} 条因为列表页没显示评论数被排除。抖音搜索结果页通常只给点赞，"
                      "评论数要进详情页才有——这些不代表不合格，值得补一轮。"]
    return "\n".join(lines)


def write_outputs(out_dir: Path, stamp: str, passed: list[dict], report: str, top: int) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    selected = passed[:top]
    (out_dir / f"{stamp}-videos.md").write_text(report + "\n", encoding="utf-8")
    (out_dir / f"{stamp}-videos.json").write_text(
        json.dumps(selected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (out_dir / f"{stamp}-videos.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle)
        writer.writerow(["标题", "作者", "点赞", "评论", "互动率", "时长(秒)", "发布(天前)", "命中关键词", "链接"])
        for video in selected:
            writer.writerow([video["title"], video["author"], video["likes"], video["comments"],
                             video["engagement"], video["duration_sec"], video["days_ago"],
                             video["keyword"], video["url"]])
    # 纯链接清单：供逐条抽文案时按行读取
    (out_dir / f"{stamp}-urls.txt").write_text(
        "\n".join(video["url"] for video in selected) + "\n", encoding="utf-8")
    # 页面已读到的描述文案，作为口播稿的起点
    captions = [f"## {i}. {v['title'] or '(无标题)'}\n\n{v['url']}\n" for i, v in enumerate(selected, 1)]
    (out_dir / f"{stamp}-captions.md").write_text(
        "# 页面文案（视频描述，通常不是完整口播稿）\n\n" + "\n".join(captions), encoding="utf-8")


# ────────────────────────── 自测 ──────────────────────────

def self_test() -> int:
    today = date(2026, 8, 31)
    for raw, expect in {"1234": 1234, "1.2万": 12000, "3.5w": 35000, "999+": 999,
                        "": None, None: None, "赞": None}.items():
        assert parse_count(raw) == expect, f"parse_count({raw!r})"

    for raw, expect in {"3天前": 3, "2周前": 14, "2026-08-15": 16, "刚刚": 0,
                        "": None, "前天": 2}.items():
        assert parse_days_ago(raw, today) == expect, f"parse_days_ago({raw!r})"

    for raw, expect in {"1:23": 83, "0:45": 45, "01:02:03": 3723,
                        "": None, None: None, "abc": None}.items():
        assert parse_duration(raw) == expect, f"parse_duration({raw!r}) = {parse_duration(raw)}"

    ids = {
        "https://www.douyin.com/video/7412345678901234567": "7412345678901234567",
        "https://www.douyin.com/user/MS4w?modal_id=7412345678901234567": "7412345678901234567",
        "https://v.douyin.com/iAbCdEf/": "iAbCdEf",
        "https://www.douyin.com/video/7412345678901234567?q=x": "7412345678901234567",
    }
    for url, expect in ids.items():
        assert video_id(url) == expect, f"video_id({url}) = {video_id(url)}"

    raw_videos = [
        {"url": "https://www.douyin.com/video/711", "likes": "1.2万", "comments": "356",
         "title": "AI就业方向", "keyword": "AI就业", "duration": "1:23", "published": "3天前"},
        {"url": "https://www.douyin.com/user/X?modal_id=711", "likes": "1.2万", "comments": "356",
         "keyword": "AI转行"},
        {"url": "https://www.douyin.com/video/722", "likes": "280", "comments": "40"},
        {"url": "https://www.douyin.com/video/733", "likes": "5000", "comments": "12"},
        {"url": "https://www.douyin.com/video/744", "likes": "8000"},
        {"url": "https://www.douyin.com/video/755", "likes": "900", "comments": "300"},
    ]
    items = [normalize(v, today) for v in raw_videos]
    items, dup = dedupe(items)
    assert dup == 1, dup
    merged = next(v for v in items if v["vid"] == "711")
    assert merged["keyword"] == "AI就业 / AI转行", merged["keyword"]

    verdicts = {v["vid"]: judge(v, 300, 20) for v in items}
    assert verdicts["711"] == ""
    assert "280 < 300" in verdicts["722"]
    assert "12 < 20" in verdicts["733"]
    assert "需进详情页" in verdicts["744"]
    assert verdicts["755"] == ""

    high = next(v for v in items if v["vid"] == "755")
    assert high["engagement"] == round(300 / 900, 4), high["engagement"]
    assert format_duration(83) == "1:23" and format_count(12000) == "1.2万"
    print("self-test 通过：计数 7 例、时间 6 例、时长 6 例、视频ID 4 例、去重与筛选 5 例、互动率 1 例")
    return 0


# ────────────────────────── 主流程 ──────────────────────────

def load_input(path: str | None) -> list[dict]:
    text = Path(path).read_text(encoding="utf-8") if path else sys.stdin.read()
    data = json.loads(text)
    if isinstance(data, dict):
        data = data.get("videos") or data.get("data") or []
    if not isinstance(data, list):
        sys.exit("输入应为 JSON 数组，或含 videos 字段的对象")
    return data


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="抖音对标视频筛选")
    parser.add_argument("--input", help="候选 JSON 文件，省略则读 stdin")
    parser.add_argument("--min-likes", type=int, default=300, help="点赞下限，默认 300")
    parser.add_argument("--min-comments", type=int, default=20, help="评论下限，默认 20")
    parser.add_argument("--top", type=int, default=10, help="输出条数，默认 10")
    parser.add_argument("--sort", choices=["likes", "engagement", "recent"], default="likes",
                        help="排序依据：点赞 / 互动率 / 时间，默认点赞")
    parser.add_argument("--out-dir", default="output/douyin-videos", help="输出目录")
    parser.add_argument("--self-test", action="store_true", help="跑内置自测后退出")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.self_test:
        return self_test()

    items = [normalize(v) for v in load_input(args.input)]
    items, dup = dedupe(items)

    passed, excluded = [], []
    for video in items:
        reason = judge(video, args.min_likes, args.min_comments)
        (passed if not reason else excluded).append(video if not reason else (video, reason))

    if args.sort == "likes":
        passed.sort(key=lambda v: v["likes"] or 0, reverse=True)
    elif args.sort == "engagement":
        passed.sort(key=lambda v: v["engagement"] or 0, reverse=True)
    else:
        passed.sort(key=lambda v: v["days_ago"] if v["days_ago"] is not None else 10**6)

    report = render_markdown(passed, excluded, args, dup)
    stamp = datetime.now().strftime("%Y%m%d-%H%M")
    out_dir = Path(args.out_dir)
    write_outputs(out_dir, stamp, passed, report, args.top)

    print(report)
    print(f"\n已保存：{out_dir}/{stamp}-videos.[md|csv|json]、{stamp}-urls.txt、{stamp}-captions.md")
    print(f"下一步抽口播稿：见 {stamp}-urls.txt，逐条交给 video-script-extract skill")
    if len(passed) < args.top:
        print(f"\n提示：通过筛选的只有 {len(passed)} 条，少于要求的 {args.top} 条。"
              "换关键词再搜一轮，或降低阈值。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
