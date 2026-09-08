#!/usr/bin/env python3
"""对抖音账号候选列表做数值解析、活跃度判定、筛选与排序。

浏览器上读到的粉丝数是「3.5万」这类展示值，发布时间是「3天前」这类相对值，
都不能直接比较。本脚本把它们归一化成数字再筛选，并把被排除的条目连同原因一起
输出——批量筛选时「为什么只剩 6 个」比「剩下哪 6 个」更需要解释。

输入 JSON（数组，或 {"accounts": [...]}）字段：
    nickname      必填  账号昵称
    url           必填  主页链接（用于去重）
    followers     选填  粉丝数展示值，如 "3.5万"
    latest_post   选填  最近作品时间展示值，如 "3天前" / "2026-08-15"
    works         选填  作品数
    douyin_id     选填  抖音号
    bio           选填  简介
    keyword       选填  命中的搜索关键词

用法:
    python3 filter_accounts.py --input candidates.json --min-followers 3000 --active-within 30 --top 10
    cat candidates.json | python3 filter_accounts.py --top 10
    python3 filter_accounts.py --self-test
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from datetime import date, datetime
from pathlib import Path

UNKNOWN = None


# ────────────────────────── 数值与时间解析 ──────────────────────────

def parse_count(raw) -> int | None:
    """把展示用的计数值解析成整数。无法识别时返回 None，不猜测。"""
    if raw is None:
        return UNKNOWN
    if isinstance(raw, (int, float)):
        return int(raw)
    text = str(raw).strip().replace(",", "").replace("，", "")
    if not text:
        return UNKNOWN
    text = text.rstrip("+")
    match = re.fullmatch(r"(\d+(?:\.\d+)?)\s*([万wW亿kK]?)", text)
    if not match:
        return UNKNOWN
    value, unit = float(match.group(1)), match.group(2)
    multiplier = {"": 1, "w": 10_000, "W": 10_000, "万": 10_000,
                  "亿": 100_000_000, "k": 1_000, "K": 1_000}[unit]
    return int(value * multiplier)


def parse_days_ago(raw, today: date | None = None) -> int | None:
    """把发布时间展示值解析成「距今多少天」。无法识别时返回 None。"""
    if raw is None:
        return UNKNOWN
    text = str(raw).strip()
    if not text:
        return UNKNOWN
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
    # 绝对日期：2026-08-15 / 2026/08/15 / 08-15 / 8月15日
    if match := re.search(r"(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})", text):
        year, month, day = (int(g) for g in match.groups())
    elif match := re.search(r"(\d{1,2})[-/月](\d{1,2})", text):
        year = today.year
        month, day = int(match.group(1)), int(match.group(2))
    else:
        return UNKNOWN
    try:
        posted = date(year, month, day)
    except ValueError:
        return UNKNOWN
    # 只给了月日且算出来是未来，说明是去年的
    if posted > today and not re.search(r"\d{4}", text):
        posted = date(year - 1, month, day)
    return (today - posted).days


# ────────────────────────── 筛选 ──────────────────────────

def normalize(account: dict, today: date | None = None) -> dict:
    followers = parse_count(account.get("followers"))
    days = parse_days_ago(account.get("latest_post"), today)
    return {
        "nickname": (account.get("nickname") or "").strip(),
        "url": (account.get("url") or "").strip(),
        "douyin_id": (account.get("douyin_id") or "").strip(),
        "followers": followers,
        "followers_raw": account.get("followers"),
        "days_ago": days,
        "latest_post_raw": account.get("latest_post"),
        "works": parse_count(account.get("works")),
        "bio": (account.get("bio") or "").strip(),
        "keyword": (account.get("keyword") or "").strip(),
    }


def dedupe(accounts: list[dict]) -> tuple[list[dict], int]:
    """按主页链接去重，同一账号被多个关键词命中时合并关键词。"""
    seen: dict[str, dict] = {}
    dropped = 0
    for account in accounts:
        key = account["url"] or account["douyin_id"] or account["nickname"]
        if key in seen:
            dropped += 1
            existing = seen[key]
            keywords = {k for k in (existing["keyword"], account["keyword"]) if k}
            existing["keyword"] = " / ".join(sorted(keywords))
            # 保留信息更全的一条
            if existing["followers"] is None and account["followers"] is not None:
                existing["followers"] = account["followers"]
                existing["followers_raw"] = account["followers_raw"]
            if existing["days_ago"] is None and account["days_ago"] is not None:
                existing["days_ago"] = account["days_ago"]
                existing["latest_post_raw"] = account["latest_post_raw"]
        else:
            seen[key] = account
    return list(seen.values()), dropped


def judge(account: dict, min_followers: int, active_within: int) -> str:
    """返回空字符串表示通过，否则返回排除原因。"""
    if not account["nickname"] or not account["url"]:
        return "缺昵称或主页链接"
    if account["followers"] is None:
        return "粉丝数未读到，需人工确认"
    if account["followers"] < min_followers:
        return f"粉丝 {account['followers']} < {min_followers}"
    if account["days_ago"] is None:
        return "最近作品时间未读到，需人工确认"
    if account["days_ago"] > active_within:
        return f"最近作品 {account['days_ago']} 天前，超过 {active_within} 天"
    return ""


# ────────────────────────── 输出 ──────────────────────────

def format_count(value: int | None) -> str:
    if value is None:
        return "未知"
    if value >= 10_000:
        return f"{value / 10_000:.1f}".rstrip("0").rstrip(".") + "万"
    return str(value)


def render_markdown(passed: list[dict], excluded: list[tuple[dict, str]],
                    args: argparse.Namespace, dup: int) -> str:
    lines = [
        f"# 抖音对标账号筛选结果",
        "",
        f"**筛选条件**：粉丝 > {args.min_followers}　最近 {args.active_within} 天内有更新",
        f"**结果**：候选 {len(passed) + len(excluded) + dup} 条 → 去重 {dup} 条 → "
        f"通过 {len(passed)} 条 → 取前 {min(args.top, len(passed))} 条",
        "",
        "| # | 账号 | 粉丝 | 最近更新 | 作品数 | 命中关键词 | 主页 |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for index, account in enumerate(passed[: args.top], start=1):
        latest = f"{account['days_ago']} 天前" if account["days_ago"] is not None else "未知"
        lines.append(
            f"| {index} | {account['nickname']} | {format_count(account['followers'])} | "
            f"{latest} | {format_count(account['works'])} | {account['keyword'] or '-'} | "
            f"{account['url']} |"
        )
    if excluded:
        lines += ["", "## 被排除的候选", "", "| 账号 | 粉丝 | 最近更新 | 排除原因 |", "| --- | --- | --- | --- |"]
        for account, reason in excluded:
            lines.append(
                f"| {account['nickname'] or '(无名)'} | {account['followers_raw'] or '-'} | "
                f"{account['latest_post_raw'] or '-'} | {reason} |"
            )
    need_check = [a for a, r in excluded if "需人工确认" in r]
    if need_check:
        lines += ["", f"> 有 {len(need_check)} 个候选因为页面没读到粉丝数或更新时间被排除，"
                      "这类不代表不合格，进主页看一眼就能补上。"]
    return "\n".join(lines)


def write_csv(path: Path, accounts: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle)
        writer.writerow(["账号", "粉丝数", "最近更新(天前)", "作品数", "抖音号", "命中关键词", "主页", "简介"])
        for account in accounts:
            writer.writerow([
                account["nickname"], account["followers"], account["days_ago"],
                account["works"], account["douyin_id"], account["keyword"],
                account["url"], account["bio"],
            ])


# ────────────────────────── 自测 ──────────────────────────

def self_test() -> int:
    today = date(2026, 8, 31)
    cases_count = {
        "1234": 1234, "1,234": 1234, "3.5万": 35000, "3.5w": 35000, "3.5W": 35000,
        "1.2亿": 120_000_000, "12.3k": 12300, "999+": 999, "0": 0,
        "": None, None: None, "暂无": None, "3.5 万": 35000,
    }
    for raw, expect in cases_count.items():
        got = parse_count(raw)
        assert got == expect, f"parse_count({raw!r}) = {got}, 期望 {expect}"

    cases_days = {
        "刚刚": 0, "5分钟前": 0, "3小时前": 0, "今天": 0, "昨天": 1, "前天": 2,
        "3天前": 3, "2周前": 14, "1个月前": 30, "2个月前": 60, "1年前": 365,
        "2026-08-15": 16, "2026/08/15": 16, "2026年8月15日": 16,
        "08-15": 16, "8月15日": 16,
        "12-25": 249,          # 只给月日且是未来 → 判为去年
        "": None, None: None, "不知道": None, "2026-02-30": None,
    }
    for raw, expect in cases_days.items():
        got = parse_days_ago(raw, today)
        assert got == expect, f"parse_days_ago({raw!r}) = {got}, 期望 {expect}"

    # 筛选与去重
    raw_accounts = [
        {"nickname": "A", "url": "u/1", "followers": "3.5万", "latest_post": "3天前", "keyword": "AI就业"},
        {"nickname": "A", "url": "u/1", "followers": "3.5万", "latest_post": "3天前", "keyword": "AI转行"},
        {"nickname": "B", "url": "u/2", "followers": "2000", "latest_post": "1天前"},
        {"nickname": "C", "url": "u/3", "followers": "8万", "latest_post": "3个月前"},
        {"nickname": "D", "url": "u/4", "followers": "5000"},
        {"nickname": "E", "url": "u/5", "followers": "1.2万", "latest_post": "2周前"},
    ]
    items = [normalize(a, today) for a in raw_accounts]
    items, dup = dedupe(items)
    assert dup == 1, dup
    merged = next(a for a in items if a["nickname"] == "A")
    assert merged["keyword"] == "AI就业 / AI转行", merged["keyword"]

    verdicts = {a["nickname"]: judge(a, 3000, 30) for a in items}
    assert verdicts["A"] == ""
    assert "2000 < 3000" in verdicts["B"]
    assert "90 天前" in verdicts["C"]
    assert "需人工确认" in verdicts["D"]
    assert verdicts["E"] == ""

    cases_format = {35000: "3.5万", 10000: "1万", 120_000_000: "12000万",
                    999: "999", 0: "0", None: "未知"}
    for value, expect in cases_format.items():
        got = format_count(value)
        assert got == expect, f"format_count({value}) = {got!r}, 期望 {expect!r}"

    print("self-test 通过：计数解析 14 例、时间解析 19 例、去重与筛选 5 例、展示格式 6 例")
    return 0


# ────────────────────────── 主流程 ──────────────────────────

def load_input(path: str | None) -> list[dict]:
    text = Path(path).read_text(encoding="utf-8") if path else sys.stdin.read()
    data = json.loads(text)
    if isinstance(data, dict):
        data = data.get("accounts") or data.get("data") or []
    if not isinstance(data, list):
        sys.exit("输入应为 JSON 数组，或含 accounts 字段的对象")
    return data


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="抖音对标账号筛选")
    parser.add_argument("--input", help="候选 JSON 文件，省略则读 stdin")
    parser.add_argument("--min-followers", type=int, default=3000, help="粉丝数下限，默认 3000")
    parser.add_argument("--active-within", type=int, default=30, help="活跃判定：最近多少天内有更新，默认 30")
    parser.add_argument("--top", type=int, default=10, help="输出条数，默认 10")
    parser.add_argument("--sort", choices=["followers", "recent"], default="followers", help="排序依据")
    parser.add_argument("--out-dir", default="output/douyin-accounts", help="输出目录")
    parser.add_argument("--self-test", action="store_true", help="跑内置自测后退出")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.self_test:
        return self_test()

    items = [normalize(a) for a in load_input(args.input)]
    items, dup = dedupe(items)

    passed, excluded = [], []
    for account in items:
        reason = judge(account, args.min_followers, args.active_within)
        (passed if not reason else excluded).append(account if not reason else (account, reason))

    if args.sort == "followers":
        passed.sort(key=lambda a: a["followers"] or 0, reverse=True)
    else:
        passed.sort(key=lambda a: a["days_ago"] if a["days_ago"] is not None else 10**6)

    report = render_markdown(passed, excluded, args, dup)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M")
    (out_dir / f"{stamp}-accounts.md").write_text(report + "\n", encoding="utf-8")
    write_csv(out_dir / f"{stamp}-accounts.csv", passed[: args.top])
    (out_dir / f"{stamp}-accounts.json").write_text(
        json.dumps(passed[: args.top], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(report)
    print(f"\n已保存：{out_dir}/{stamp}-accounts.[md|csv|json]")
    if len(passed) < args.top:
        print(f"提示：通过筛选的只有 {len(passed)} 条，少于要求的 {args.top} 条。"
              "可以再换几个关键词补充，或放宽活跃度窗口。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
