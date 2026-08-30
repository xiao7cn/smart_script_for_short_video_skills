#!/usr/bin/env python3
"""抽取短视频选题参数组合（25 宫格配对 + 爆款元素 + 脚本类型）。

批量抽取时脚本类型按 痛点科普:Vlog叙事:聊天纪实:话题共鸣 = 4:1:3:2 分配，
用最大余数法保证总数精确，而不是逐条随机——逐条随机在小样本下会严重偏离配比。

用法:
    python3 pick.py                                  # 抽 1 条，全随机
    python3 pick.py --count 10                       # 抽 10 条，脚本类型严格按 4:1:3:2
    python3 pick.py --topic-type 转化类 --element 荷尔蒙 # 锁定部分参数，其余随机
    python3 pick.py --count 5 --json                 # JSON 输出，便于程序消费
    python3 pick.py --list                           # 查看所有可选值
"""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
from pathlib import Path

TOPIC_TYPES = {
    "转化类": "聚焦 AI 转行前景、岗位解析、培训避坑等刚需话题，直击用户学习；承接流量效果强，快速筛选高意向潜在学员，是账号变现的核心",
    "破圈类": "围绕职场内卷、赛道抉择、成长感悟等普适话题，跳出垂直领域局限依托情绪共鸣快速拉高内容曝光、打破圈层壁垒，持续为账号注入新流量，不断扩容潜在用户池",
    "家长类": "从子女职业规划、核心技能价值、就业稳定性多维切入，精准戳中家长对孩子未来发展的核心顾虑；立足家庭视角建立信任、消解用户防御心理，从决策端深度撬动报名意向",
}

TOPIC_SOURCES = {
    "客户咨询高频提问": "客户高频提问，就是最好的选题方向。直面这些真实诉求，精准击中用户需求，产出既有共鸣又利于转化的内容",
    "对标爆款拆解": "拆解同赛道高赞内容的底层逻辑与用户偏好，通过“换视角、换话术、换案例”，进行差异化二次创作，拒绝简单搬运，打造有新意的爆款平替",
    "评论私信需求挖掘": "深挖评论区、私信中的用户疑问、吐槽与建议。这些反馈是高互动选题的金矿，精准匹配潜在受众的好奇点、焦虑点与求知欲",
    "行业热点借势融合": "紧跟行业新闻、职场趋势与社会热点，结合自身领域输出专业观点与深度解读，借势平台流量风口，让内容自带曝光度与传播力",
}

HOOK_ELEMENTS = {
    "成本": ["便宜又有面子的", "十分之一的时间/金钱", "花大钱干的", "xxx 如何贪小便宜", "xx 如何偷懒"],
    "人群": ["身价十个亿的", "兜里一分钱没有的", "想要（）但不具备条件的", "因为（）导致现在可愁了"],
    "奇葩": ["外行人不知道的", "脑回路有病的", "黑心内幕操作"],
    "头牌": ["xx 电视剧里出现的", "生意最好的", "最贵的/好评最多的", "明星/名人/名校/名企"],
    "怀旧": ["古代的/具体朝代的", "20 年前的", "如果能重来一次", "历史风潮盘点"],
    "反差": ["反向操作", "身份反差", "古今/中外/南北/品牌/穷富"],
    "最差": ["最难吃最难用的", "最没面子的/样子最丑的", "拼多多 9 块 9 的", "差评最多/贬值最多"],
    "荷尔蒙": ["好找对象的", "魅力变强的"],
}

SCRIPT_TYPES = {
    "痛点科普": {
        "formula": "开头抛痛点 + 中间讲干货 + 结尾软引导",
        "goal": "白嫖你",
        "weight": 4,
    },
    "Vlog叙事": {
        "formula": "开场引入 + 片段拼接 + 结尾感悟",
        "goal": "了解你",
        "weight": 1,
    },
    "聊天纪实": {
        "formula": "场景引入 + 对话片段 + 观点总结",
        "goal": "信任你",
        "weight": 3,
    },
    "话题共鸣": {
        "formula": "抛出话题 + 表达观点 + 引导评论",
        "goal": "喜欢你",
        "weight": 2,
    },
}

DEFAULT_MATRIX = {
    "inner": ["AI就业"],
    "middle": ["求职", "面试", "专业", "薪资", "晋升", "岗位/职业", "培训", "AIGC"],
    "outer": [
        "毕业生", "求职者", "待转行", "专/本科生", "机构", "考公/编", "失业/被裁",
        "文/理科生", "不同专业", "普通人", "找对象", "大厂", "投资成本", "发展前景",
        "职场", "保障",
    ],
}

CONFIG_SEARCH_DIRS = [Path.cwd(), Path.cwd() / ".short-video-script", Path.home() / ".short-video-script"]


def find_config(explicit: str | None, profile: str | None) -> Path | None:
    if explicit:
        path = Path(explicit).expanduser()
        return path if path.is_file() else None
    filename = f"persona.{profile}.yaml" if profile else "persona.yaml"
    for directory in CONFIG_SEARCH_DIRS:
        candidate = directory / filename
        if candidate.is_file():
            return candidate
    return None


def load_matrix(config_path: Path | None) -> tuple[dict[str, list[str]], str]:
    """读取配置里的 25 宫格。没有 PyYAML 时退回到极简解析，避免强制依赖。"""
    if config_path is None:
        return DEFAULT_MATRIX, "内置默认值"
    text = config_path.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        data = yaml.safe_load(text) or {}
        matrix = data.get("matrix") or {}
    except ImportError:
        matrix = _parse_matrix_fallback(text)
    merged = {ring: list(matrix.get(ring) or DEFAULT_MATRIX[ring]) for ring in DEFAULT_MATRIX}
    return merged, str(config_path)


def _parse_matrix_fallback(text: str) -> dict[str, list[str]]:
    """只解析 matrix 下的三个字符串列表，不追求通用 YAML 兼容。"""
    matrix: dict[str, list[str]] = {}
    ring = None
    inside_matrix = False
    for raw in text.splitlines():
        line = raw.split(" #")[0].rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        stripped = line.strip()
        if indent == 0:
            inside_matrix = stripped.startswith("matrix:")
            ring = None
            continue
        if not inside_matrix:
            continue
        if match := re.fullmatch(r"(inner|middle|outer):", stripped):
            ring = match.group(1)
            matrix[ring] = []
        elif stripped.startswith("- ") and ring:
            matrix[ring].append(stripped[2:].strip().strip("\"'"))
    return {ring: values for ring, values in matrix.items() if values}


def allocate_script_types(count: int) -> list[str]:
    """按权重分配脚本类型，最大余数法保证总数等于 count。"""
    weights = {name: meta["weight"] for name, meta in SCRIPT_TYPES.items()}
    total_weight = sum(weights.values())
    quotas = {name: count * weight / total_weight for name, weight in weights.items()}
    allocation = {name: int(quota) for name, quota in quotas.items()}
    remainder = count - sum(allocation.values())
    ranked = sorted(quotas, key=lambda name: (quotas[name] - allocation[name], weights[name]), reverse=True)
    for name in ranked[:remainder]:
        allocation[name] += 1
    result = [name for name, times in allocation.items() for _ in range(times)]
    random.shuffle(result)
    return result


def pick_pair(matrix: dict[str, list[str]], args: argparse.Namespace, rng: random.Random) -> dict[str, str | None]:
    inner = args.inner or rng.choice(matrix["inner"])
    mode = args.pair_mode
    if mode == "auto":
        mode = rng.choices(["middle", "outer", "both"], weights=[3, 3, 4])[0]
    if args.middle and args.outer:
        mode = "both"
    elif args.middle:
        mode = "middle"
    elif args.outer:
        mode = "outer"
    middle = args.middle or (rng.choice(matrix["middle"]) if mode in ("middle", "both") else None)
    outer = args.outer or (rng.choice(matrix["outer"]) if mode in ("outer", "both") else None)
    return {"inner": inner, "middle": middle, "outer": outer}


def build_cards(args: argparse.Namespace, matrix: dict[str, list[str]], rng: random.Random) -> list[dict]:
    script_plan = [args.script_type] * args.count if args.script_type else allocate_script_types(args.count)
    cards = []
    for index, script_type in enumerate(script_plan, start=1):
        topic_type = args.topic_type or rng.choice(list(TOPIC_TYPES))
        topic_source = args.topic_source or rng.choice(list(TOPIC_SOURCES))
        element = args.element or rng.choice(list(HOOK_ELEMENTS))
        pair = pick_pair(matrix, args, rng)
        cards.append(
            {
                "序号": index,
                "选题类型": topic_type,
                "选题类型说明": TOPIC_TYPES[topic_type],
                "选题来源": topic_source,
                "选题来源说明": TOPIC_SOURCES[topic_source],
                "25宫格内圈": pair["inner"],
                "25宫格中圈": pair["middle"],
                "25宫格外圈": pair["outer"],
                "爆款元素句式": element,
                "爆款元素参考": HOOK_ELEMENTS[element],
                "脚本类型": script_type,
                "结构公式": SCRIPT_TYPES[script_type]["formula"],
                "脚本目标": SCRIPT_TYPES[script_type]["goal"],
            }
        )
    return cards


def render_text(cards: list[dict], config_label: str, seed: int) -> str:
    lines = [f"配置来源：{config_label}    随机种子：{seed}（复现同一批参数请加 --seed {seed}）", ""]
    for card in cards:
        pair = " × ".join(
            value for value in [card["25宫格内圈"], card["25宫格中圈"], card["25宫格外圈"]] if value
        )
        lines += [
            f"───── 选题参数卡 #{card['序号']} ─────",
            f"选题类型：{card['选题类型']}  |  选题来源：{card['选题来源']}",
            f"25宫格配对：{pair}",
            f"爆款元素句式：{card['爆款元素句式']}（参考：{'；'.join(card['爆款元素参考'])}）",
            f"脚本类型：{card['脚本类型']}（{card['脚本目标']}）",
            f"结构公式：{card['结构公式']}",
            "",
        ]
    if len(cards) > 1:
        tally: dict[str, int] = {}
        for card in cards:
            tally[card["脚本类型"]] = tally.get(card["脚本类型"], 0) + 1
        summary = "  ".join(f"{name}×{times}" for name, times in tally.items())
        lines.append(f"脚本类型配比（目标 痛点科普:Vlog叙事:聊天纪实:话题共鸣 = 4:1:3:2）：{summary}")
    return "\n".join(lines)


def render_list() -> str:
    blocks = ["【选题类型】"] + [f"  - {name}：{desc}" for name, desc in TOPIC_TYPES.items()]
    blocks += ["", "【选题来源】"] + [f"  - {name}：{desc}" for name, desc in TOPIC_SOURCES.items()]
    blocks += ["", "【爆款元素句式】"] + [
        f"  - {name}：{'；'.join(examples)}" for name, examples in HOOK_ELEMENTS.items()
    ]
    blocks += ["", "【脚本类型】"] + [
        f"  - {name}（{meta['goal']}，权重 {meta['weight']}）：{meta['formula']}"
        for name, meta in SCRIPT_TYPES.items()
    ]
    return "\n".join(blocks)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="抽取短视频选题参数组合", formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--count", type=int, default=1, help="抽取条数，默认 1")
    parser.add_argument("--config", help="persona 配置文件路径，默认自动查找")
    parser.add_argument("--profile", help="配置标识，对应 persona.<profile>.yaml")
    parser.add_argument("--topic-type", choices=list(TOPIC_TYPES), help="锁定选题类型")
    parser.add_argument("--topic-source", choices=list(TOPIC_SOURCES), help="锁定选题来源")
    parser.add_argument("--inner", help="锁定 25 宫格内圈")
    parser.add_argument("--middle", help="锁定 25 宫格中圈")
    parser.add_argument("--outer", help="锁定 25 宫格外圈")
    parser.add_argument(
        "--pair-mode",
        choices=["middle", "outer", "both", "auto"],
        default="auto",
        help="内圈与哪一圈配对，默认 auto（随机决定单圈或双圈）",
    )
    parser.add_argument("--element", choices=list(HOOK_ELEMENTS), help="锁定爆款元素句式")
    parser.add_argument("--script-type", choices=list(SCRIPT_TYPES), help="锁定脚本类型（会关闭 4:1:3:2 配比）")
    parser.add_argument("--seed", type=int, help="随机种子，用于复现同一批参数")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    parser.add_argument("--list", action="store_true", help="列出所有可选值后退出")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.list:
        print(render_list())
        return 0
    if args.count < 1:
        print("--count 必须大于 0", file=sys.stderr)
        return 2

    seed = args.seed if args.seed is not None else random.randrange(1, 10**6)
    random.seed(seed)
    rng = random.Random(seed)

    config_path = find_config(args.config, args.profile)
    if args.config and config_path is None:
        print(f"找不到配置文件：{args.config}", file=sys.stderr)
        return 2
    matrix, config_label = load_matrix(config_path)

    cards = build_cards(args, matrix, rng)
    if args.json:
        print(json.dumps({"config": config_label, "seed": seed, "cards": cards}, ensure_ascii=False, indent=2))
    else:
        print(render_text(cards, config_label, seed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
