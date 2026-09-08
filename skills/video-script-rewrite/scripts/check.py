#!/usr/bin/env python3
"""校验洗稿产物：十一段是否齐全、字数、禁用词、与原文的连续重合。

洗稿最容易滑向「换词复述」。本脚本只做机械检查，不代替人工判断，
但连续 16 字与原文相同、缺章节、缺原视频标题/原文、超字数、踩禁用词，都足以判定失败。
书面腔、缺少「我 / 你」或语气词只警告，改第九段后再跑。

用法:
    python3 check.py --rewrite 洗稿.md --original 原文.txt
    python3 check.py --rewrite 洗稿.md --original 原文.txt --min-words 300 --max-words 500
    python3 check.py --rewrite 洗稿.md --forbidden 保证就业,包分配
    python3 check.py --self-test
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass, field

REQUIRED_SECTIONS = [
    ("一", "原文一句话总结"),
    ("二", "原文结构拆解表"),
    ("三", "开头、中段和结尾钩子分析"),
    ("四", "内容、结构、状态、身份、场景分析"),
    ("五", "原创风险与可借鉴内容"),
    ("六", "我的内容补充建议"),
    ("七", "重写方案"),
    ("八", "3个不同类型的新开头"),
    ("九", "完整原创口播文案"),
    ("十", "拍摄时的语气、停顿和重音建议"),
    ("十一", "文案自检评分"),
]

# 连续相同超过这个字数，基本就是在复述原句
COPY_FAIL_N = 16
COPY_WARN_N = 10

HEADING_RE = re.compile(
    r"^(?:#{1,3}\s*)?(?P<num>[一二三四五六七八九十]+)、\s*(?P<title>\S.*?)\s*$",
    re.M,
)
SCORE_RE = re.compile(
    r"(开头吸引力|中段留存能力|内容价值|口语自然度|身份可信度|情绪感染力|原创程度|转化自然度)"
    r"[^\d]{0,8}(\d+(?:\.\d+)?)"
)

# 书面背诵腔，对照 writing-craft 反面教材
WRITTEN_MARKERS = (
    "至关重要",
    "核心竞争力",
    "不可或缺",
    "发展趋势表明",
    "综上所述",
    "值得注意的是",
    "该岗位具备",
)
TONE_RE = re.compile(r"对吧|其实啊|你知道吗|对不对|是吧")
READALOUD_RE = re.compile(r"朗读测试")
TITLE_RE = re.compile(r"^\*\*原视频标题\*\*[：:]\s*\S+", re.M)
ORIG_SCRIPT_HEAD_RE = re.compile(r"^#{1,3}\s*原视频文案\s*$", re.M)
FIRST_SECTION_RE = re.compile(r"^一、", re.M)
MIN_ORIG_SCRIPT = 40


@dataclass
class CheckResult:
    ok: bool
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    missing_sections: list[str] = field(default_factory=list)
    word_count: int = 0
    longest_overlap: int = 0
    overlap_excerpt: str = ""
    forbidden_hits: list[str] = field(default_factory=list)
    written_hits: list[str] = field(default_factory=list)
    scores: dict[str, float] = field(default_factory=dict)
    low_scores: list[str] = field(default_factory=list)


def compact_text(text: str) -> str:
    """去掉空白，只留可用于重合比对的字。"""
    return re.sub(r"\s+", "", text or "")


def count_words(text: str) -> int:
    """口播字数：去掉空白后的字符数（含标点）。"""
    return len(compact_text(text))


def longest_overlap(original: str, rewrite: str) -> tuple[int, str]:
    """求去空白后的最长公共子串长度和片段。"""
    a, b = compact_text(original), compact_text(rewrite)
    if not a or not b:
        return 0, ""
    # 短的做内层，避免超长原文把检查拖死
    if len(a) > len(b):
        a, b = b, a
    best_len = 0
    best = ""
    # 从长到短找，找到 COPY_FAIL_N 就够判定失败；仍求最长便于报告
    start_n = min(len(a), 80)
    for n in range(start_n, COPY_WARN_N - 1, -1):
        seen = {a[i : i + n] for i in range(len(a) - n + 1)}
        for i in range(len(b) - n + 1):
            chunk = b[i : i + n]
            if chunk in seen:
                return n, chunk
    return best_len, best


def extract_sections(markdown: str) -> dict[str, str]:
    """按「一、标题」切开。键是中文序号。"""
    matches = list(HEADING_RE.finditer(markdown or ""))
    sections: dict[str, str] = {}
    for idx, match in enumerate(matches):
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(markdown)
        sections[match.group("num")] = markdown[match.end() : end].strip()
    return sections


def find_forbidden(text: str, words: list[str]) -> list[str]:
    hits = []
    for word in words:
        word = word.strip()
        if word and word in (text or ""):
            hits.append(word)
    return hits


def extract_original_script(markdown: str) -> str:
    """抽出「### 原视频文案」到「一、」之间的正文。"""
    head = ORIG_SCRIPT_HEAD_RE.search(markdown or "")
    if not head:
        return ""
    rest = markdown[head.end() :]
    end = FIRST_SECTION_RE.search(rest)
    return (rest[: end.start()] if end else rest).strip()


def parse_scores(text: str) -> dict[str, float]:
    scores = {}
    for name, raw in SCORE_RE.findall(text or ""):
        scores[name] = float(raw)
    return scores


def check(
    rewrite_md: str,
    original: str = "",
    min_words: int | None = None,
    max_words: int | None = None,
    forbidden: list[str] | None = None,
) -> CheckResult:
    result = CheckResult(ok=True)
    sections = extract_sections(rewrite_md)

    if not TITLE_RE.search(rewrite_md or ""):
        result.errors.append("缺少「原视频标题」，不能用洗稿标题顶替")
    orig_script = extract_original_script(rewrite_md)
    if count_words(orig_script) < MIN_ORIG_SCRIPT:
        result.errors.append("缺少「原视频文案」或原文过短，洗稿文档必须带完整口播原文")

    for num, title in REQUIRED_SECTIONS:
        body = sections.get(num, "").strip()
        if not body:
            result.missing_sections.append(f"{num}、{title}")

    script = sections.get("九", "")
    result.word_count = count_words(script)

    if not script.strip():
        result.errors.append("缺少「九、完整原创口播文案」正文")
    if min_words is not None and result.word_count < min_words:
        result.errors.append(f"口播文案 {result.word_count} 字，低于下限 {min_words}")
    if max_words is not None and result.word_count > max_words:
        result.errors.append(f"口播文案 {result.word_count} 字，超过上限 {max_words}")

    if original and script:
        overlap_n, excerpt = longest_overlap(original, script)
        result.longest_overlap = overlap_n
        result.overlap_excerpt = excerpt
        if overlap_n >= COPY_FAIL_N:
            result.errors.append(
                f"与原文连续重合 {overlap_n} 字（≥{COPY_FAIL_N}）：…{excerpt}…"
            )
        elif overlap_n >= COPY_WARN_N:
            result.warnings.append(
                f"与原文连续重合 {overlap_n} 字：…{excerpt}…"
            )

    result.forbidden_hits = find_forbidden(script, forbidden or [])
    if result.forbidden_hits:
        result.errors.append("口播文案含禁用词：" + "、".join(result.forbidden_hits))

    result.written_hits = [w for w in WRITTEN_MARKERS if w in script]
    if result.written_hits:
        result.warnings.append("口播像书面背诵：" + "、".join(result.written_hits))
    if script.strip():
        if "我" not in script or "你" not in script:
            result.warnings.append("口播缺少「我」或「你」，不像面对面聊天")
        if not TONE_RE.search(script):
            result.warnings.append("口播未见语气词（对吧 / 其实啊 / 你知道吗），朗读时检查是否像背稿")
    if sections.get("十一") and not READALOUD_RE.search(sections["十一"] + sections.get("十", "")):
        result.warnings.append("第十一段未见「朗读测试」通过/不通过")

    score_section = sections.get("十一", "")
    result.scores = parse_scores(score_section)
    for name, value in result.scores.items():
        if value < 8:
            result.low_scores.append(f"{name}={value}")
    if result.low_scores:
        result.errors.append("自检有低于 8 分的项，需要改后再交：" + "、".join(result.low_scores))
    if score_section and len(result.scores) < 8:
        result.warnings.append(f"自检评分只解析到 {len(result.scores)} 项，应有 8 项")

    if result.missing_sections:
        result.errors.append("缺章节：" + "；".join(result.missing_sections))

    result.ok = not result.errors
    return result


def format_report(result: CheckResult) -> str:
    lines = [
        f"{'通过' if result.ok else '未通过'}  口播 {result.word_count} 字"
        + (f"  最长重合 {result.longest_overlap} 字" if result.longest_overlap else "")
    ]
    for err in result.errors:
        lines.append(f"ERROR  {err}")
    for warn in result.warnings:
        lines.append(f"WARN   {warn}")
    if result.ok and not result.warnings:
        lines.append("OK     章节、字数、重合、禁用词、口语标记均通过")
    return "\n".join(lines)


# ────────────────────────── 自测 ──────────────────────────

SAMPLE_ORIGINAL = (
    "计算机专业转 AI，90% 的人不是学不会，是一路踩雷。"
    "今天的这一期纯避雷，干到不能再干。"
    "我直接给你一个三个月学习的路线图。"
)

SAMPLE_SECTIONS = """
**原视频标题**：计算机专业转行学AI，90%的人都会踩的雷！

### 原视频文案

计算机专业转 AI，90% 的人不是学不会，是一路踩雷。
今天的这一期纯避雷，干到不能再干。
我直接给你一个三个月学习的路线图。

一、原文一句话总结
讲转 AI 别乱学。

二、原文结构拆解表
| 原文片段 | 所处位置 | 结构作用 | 使用手法 | 调动的情绪 | 存在的问题 | 可以如何优化 |
|---|---|---|---|---|---|---|
| 开场 | 黄金3秒钩子 | 点名人群 | 反常识 | 焦虑 | 略冲 | 换成具体场景 |

三、开头、中段和结尾钩子分析
开头点名计算机转 AI。中段用路线图悬念。结尾送资料。

四、内容、结构、状态、身份、场景分析
内容是避雷路线。结构按月推进。状态犀利。身份像培训讲师。场景是口播。

五、原创风险与可借鉴内容
1. 可以借鉴：先否错误路径再给顺序。
2. 需要重证：三个月够不够。
3. 不该沿用：90% 这个数字、纯避雷金句。

六、我的内容补充建议
补一个「先确认目标岗位再学」的判断步骤。

七、重写方案
核心观点改成先选方向再学工具。三个差异：顺序、案例、身份。

八、3个不同类型的新开头
A 共情提问
B 反常识
C 实景

九、完整原创口播文案
{script}

十、拍摄时的语气、停顿和重音建议
开头放慢，说到「先选方向」加重。

十一、文案自检评分
- 开头吸引力：9
- 中段留存能力：8
- 内容价值：8
- 口语自然度：9
- 身份可信度：8
- 情绪感染力：8
- 原创程度：9
- 转化自然度：8
- 朗读测试：通过
"""


def _self_test() -> None:
    ok_script = (
        "你知道吗？你要是计算机刚毕业，正犹豫要不要转 AI，先别把课单堆满。"
        "真正耽误人的，不是学得慢，是还没想清楚自己要去哪类岗位，对吧？"
        "我见过不少人三个月把工具学了一圈，简历上全是名词，面试一问场景就空。"
        "所以与其先问学什么，不如先问自己想进应用、工程，还是研究。"
        "想清楚再排学习顺序，比任何路线图都管用。"
        "你要是方向已经有了，又卡在先学哪一块，评论区说一下你的专业和目标，我按这个帮你拆。"
    )
    copied_script = (
        "先说一句，计算机专业转 AI，90% 的人不是学不会，是一路踩雷。"
        "后面我再讲我自己的看法。"
    )

    passed = check(SAMPLE_SECTIONS.format(script=ok_script), SAMPLE_ORIGINAL, min_words=80, max_words=400)
    assert passed.ok, passed.errors
    assert passed.word_count >= 80
    assert passed.longest_overlap < COPY_FAIL_N
    assert not passed.written_hits
    assert not any("书面" in w or "语气词" in w or "我」或「你" in w for w in passed.warnings)

    written = check(
        SAMPLE_SECTIONS.format(script=ok_script + "掌握相关技能至关重要，是核心竞争力。"),
        SAMPLE_ORIGINAL,
    )
    assert written.ok
    assert "至关重要" in written.written_hits

    missing = check("一、原文一句话总结\n只有这一段。\n", SAMPLE_ORIGINAL)
    assert not missing.ok
    assert any("缺章节" in e for e in missing.errors)
    assert any("原视频标题" in e for e in missing.errors)
    assert any("原视频文案" in e for e in missing.errors)

    copied = check(SAMPLE_SECTIONS.format(script=copied_script), SAMPLE_ORIGINAL)
    assert not copied.ok, "整句照搬应失败"
    assert copied.longest_overlap >= COPY_FAIL_N

    forbidden = check(
        SAMPLE_SECTIONS.format(script=ok_script + "保证就业没问题。"),
        SAMPLE_ORIGINAL,
        forbidden=["保证就业"],
    )
    assert not forbidden.ok
    assert "保证就业" in forbidden.forbidden_hits

    low = check(
        SAMPLE_SECTIONS.format(script=ok_script).replace("原创程度：9", "原创程度：6"),
        SAMPLE_ORIGINAL,
    )
    assert not low.ok
    assert low.low_scores

    over = check(SAMPLE_SECTIONS.format(script=ok_script), SAMPLE_ORIGINAL, max_words=10)
    assert not over.ok
    assert any("超过上限" in e for e in over.errors)

    print("self-test: OK")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="校验洗稿产物")
    parser.add_argument("--rewrite", help="洗稿产出的 markdown")
    parser.add_argument("--original", help="原文 txt / md（用来比对重合）")
    parser.add_argument("--min-words", type=int)
    parser.add_argument("--max-words", type=int)
    parser.add_argument("--forbidden", default="", help="逗号分隔的禁用词")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        _self_test()
        return 0

    if not args.rewrite:
        parser.error("需要 --rewrite，或使用 --self-test")

    rewrite_md = open(args.rewrite, encoding="utf-8").read()
    original = open(args.original, encoding="utf-8").read() if args.original else ""
    forbidden = [w for w in args.forbidden.split(",") if w.strip()]
    result = check(rewrite_md, original, args.min_words, args.max_words, forbidden)

    if args.json:
        print(json.dumps(asdict(result), ensure_ascii=False, indent=2))
    else:
        print(format_report(result))
    return 0 if result.ok else 1


if __name__ == "__main__":
    sys.exit(main())
