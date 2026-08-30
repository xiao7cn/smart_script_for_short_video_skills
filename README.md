# 短视频口播文案 Skills

两个可安装到 **Claude Code / Cursor / Codex** 的 Agent Skill：

| Skill | 做什么 |
| --- | --- |
| `short-video-script` | 按「人设配置 + 可变参数」生成**选题 → 标题 → 口播文案**，交付前做一次去 AI 味重写 |
| `video-script-extract` | 把对标视频转成带时间戳的口播稿并做**结构拆解**，产出可原创复用的逻辑框架。支持抖音、快手、小红书、微信视频号 |

两者可独立使用，也可串起来：第一个 skill 的选题来源选「对标爆款拆解」时会自动调用第二个。

三个平台共用同一份 `SKILL.md`（Agent Skills 开放标准），差别只在安装路径。

---

## 快速开始

```bash
git clone <本仓库> && cd smart_script_for_short_video_skills

./install.sh                      # 两个 skill 装到三个平台（个人级）
cp skills/short-video-script/config/persona.example.yaml ./persona.yaml
```

编辑 `persona.yaml` 填上你自己的六项固定参数，重启 Agent 会话，然后直接说：

> 帮我写 5 条短视频口播文案，转化类，爆款元素用荷尔蒙

或者只说「帮我写条短视频文案」，缺的参数会自动随机抽取。

要用对标拆解，再装一个 ASR 后端（三选一，短视频转写几秒到十几秒）：

```bash
pip install faster-whisper        # 本地，推荐
brew install whisper-cpp          # 本地，Apple Silicon 更快
export OPENAI_API_KEY=sk-...      # 云端，免安装

python3 skills/video-script-extract/scripts/transcribe.py --list-backends   # 环境自检
```

---

## 参数分两层

**固定参数**写在 `persona.yaml` 里，一次配好长期复用——人设模型、人设定位、人群画像、用户需求、人设风格、价值定位，外加 25 宫格与输出偏好（字数下限、禁用词、软引流方式）。

**可变参数**每次对话输入，指定哪些就锁定哪些，其余随机：

| 参数 | 取值 |
| --- | --- |
| 选题类型 | 转化类 / 破圈类 / 家长类 |
| 选题来源 | 客户咨询高频提问 / 对标爆款拆解 / 评论私信需求挖掘 / 行业热点借势融合 |
| 25 宫格配对 | 内圈 × 中圈 和/或 外圈 |
| 爆款元素句式 | 成本 / 人群 / 奇葩 / 头牌 / 怀旧 / 反差 / 最差 / 荷尔蒙 |
| 脚本类型 | 痛点科普 / Vlog叙事 / 聊天纪实 / 话题共鸣 |
| 条数 | 默认 1 |

多账号：放多份 `persona.<profile>.yaml`，用「用招聘号那份配置」指定。

配置文件查找顺序：`./persona.yaml` → `./.short-video-script/persona.yaml` → `~/.short-video-script/persona.yaml`。

---

## 工作流

```
0. 加载人设配置（固定参数）
1. 确认可变参数 → pick.py 抽取选题参数卡
2. 25 宫格配对出话题方向 → 套爆款元素句式 → 选题
3. 选题 → 标题（选题 ≠ 标题）  ← 此处暂停，等你确认
4. 按结构公式写 ≥500 字口播正文
5. 去 AI 味独立重写一遍
6. 自检清单 + 朗读 → 交付
```

批量生成时脚本类型严格按 **痛点科普 : Vlog叙事 : 聊天纪实 : 话题共鸣 = 4 : 1 : 3 : 2** 分配。这一步交给 `pick.py` 用最大余数法算，而不是让模型自己随机——模型在小样本下会明显偏离配比。

```bash
cd skills/short-video-script
python3 scripts/pick.py --count 10                          # 抽 10 条
python3 scripts/pick.py --topic-type 转化类 --element 荷尔蒙   # 锁定部分参数
python3 scripts/pick.py --count 5 --seed 42                 # 复现同一批
python3 scripts/pick.py --list                              # 查看全部可选值
```

零依赖（Python 3.8+）。装了 PyYAML 会读配置里自定义的 25 宫格，没装则自动降级解析。

---

## 对标拆解工作流

```
1. 判断输入：链接 / 录屏文件 / 已粘贴的文案
2. 链接 → 尝试取件，失败则给该平台的录屏步骤
3. 文件 → 转写成带时间戳的口播稿
4. 结构拆解：钩子类型、论证骨架、中段钩子秒数、结尾引导、反推 25 宫格坐标
5. 交接 short-video-script 做原创（只复用骨架，禁止改写）
```

```bash
cd skills/video-script-extract
python3 scripts/fetch.py "https://v.douyin.com/xxxxxx/"      # 识别平台并尝试下载
python3 scripts/fetch.py --guide 视频号                        # 查看某平台取件步骤
python3 scripts/transcribe.py --input ~/Downloads/对标.mp4     # 转写
python3 scripts/transcribe.py --list-backends                # 环境自检
```

转写产出三个文件到 `output/transcripts/`：断好行的口播稿、节奏分析（时长/字数/语速/分段时间轴）、原始时间戳 JSON。时间戳用来定位「黄金 3 秒说了什么」「中段钩子在第几秒」，这是拆解的关键输入。

**四个平台的自动化能力不一样**，这点在动手前要知道：

| 平台 | 贴链接自动取件 | 兜底 |
| --- | --- | --- |
| 小红书 | 有时可以（图文笔记的正文直接就是文案） | 录屏 |
| 抖音 | 不稳定 | 录屏 |
| 快手 | 不稳定 | 录屏 |
| 视频号 | **不行**（加密分发，绕过属于规避技术措施） | 只能录屏 |

自动下载完全委托给你自己安装的 `yt-dlp`，本仓库不内置任何平台解析器，所以不会出现「上周还能用、这周报错」。拿不到就走录屏，路径始终可用——拆解只需要声音，一条 60 秒视频录屏不到一分钟。

---

## 安装

```bash
./install.sh                                    # 两个 skill 装到三个平台，个人级
./install.sh cursor --link                      # 只装 Cursor，符号链接（改仓库即时生效）
./install.sh claude --skill short-video-script   # 只装一个 skill
./install.sh claude --project                   # 装到当前项目的 .claude/skills/
./install.sh all --uninstall                    # 移除
./install.sh --dry-run                          # 只看将要做什么
```

| 平台 | 个人级 | 项目级 | 调用方式 |
| --- | --- | --- | --- |
| Claude Code | `~/.claude/skills/` | `.claude/skills/` | `/short-video-script` 或自然语言 |
| Cursor | `~/.cursor/skills/` | `.cursor/skills/` | 自然语言，或 @ 提及 |
| Codex | `~/.agents/skills/` | `.agents/skills/` | `$short-video-script` |

Codex 需要显式开启 skills 功能，在 `~/.codex/config.toml` 加：

```toml
[features]
skills = true
```

安装脚本会自动检查这一项并在缺失时提醒。三个平台的 skill 索引都在**会话启动时**加载，改完要重启会话。

---

## 目录结构

```
.
├── install.sh                            # 跨平台安装/卸载
└── skills/
    ├── short-video-script/
    │   ├── SKILL.md                      # 主入口：七步工作流
    │   ├── config/persona.example.yaml   # 人设配置模板（带字段注释）
    │   ├── scripts/pick.py               # 选题参数抽取 + 4:1:3:2 配比
    │   └── references/
    │       ├── topic-matrix.md           # 选题类型/来源、25 宫格、爆款元素句式全表
    │       ├── script-templates.md       # 四类脚本结构 + 痛点科普范文
    │       ├── writing-craft.md          # 黄金 3 秒钩子、中段钩子、情绪、通病、软引流、自检清单
    │       └── deai-prompt.md            # 去 AI 味提示词 + AI 味症状自查表
    └── video-script-extract/
        ├── SKILL.md                      # 主入口：取件 → 转写 → 拆解 → 交接
        ├── scripts/
        │   ├── fetch.py                  # 平台识别 + 尝试下载 + 取件指引
        │   └── transcribe.py             # 多后端 ASR + 文本清洗 + 节奏分析
        └── references/
            ├── platforms.md              # 四平台能力矩阵、录屏要点、合规边界
            ├── asr-setup.md              # 三个 ASR 后端的安装选型与故障排查
            └── teardown.md               # 结构拆解模板 + 完整示例
```

`SKILL.md` 只放工作流骨架，细则按需读取 `references/`——三个平台都只把 `name` 和 `description` 常驻上下文，正文和参考文件按需加载。

---

## 已知边界

- **视频号只能录屏**，做不到贴链接出文案。它的视频加密分发，取件需要抓包拿配对密钥，绕过属于规避平台技术措施，本仓库不实现，也不建议用第三方在线解析站（要把带你身份信息的分享链接交给对方服务器）。
- **抖音、快手的自动下载不稳定**，小红书需要带 `xsec_token` 的完整链接。失败即降级到录屏，不会卡住。
- **ASR 会出错**，专有名词、数字、岗位名尤其容易错。拆解时影响判断的数字会标注存疑，不当成原文事实。
- **行业热点借势融合**依赖联网检索。检索不可用时会向你索取热点，不会编造新闻和数据。
- 涉及真实用户留言、个人经历、具体数据的内容一律不编造，缺素材就问你。
