# 闪创工厂 · 短视频口播文案

H5 + Java 后端的完整应用，以及可安装到 Cursor / Claude / Codex 的 Agent Skills。

## 一键启动应用

```bash
./start.sh            # 自动安装依赖、准备数据库、启动 H5 / 后端 / Agent
./start.sh status     # 查看状态
./start.sh stop       # 停止
./start.sh --reset-db # 重建库表（会清空业务数据）
```

可重复执行：已装好的 JDK / Node / npm 依赖会跳过，已有的 `shanchuang` 库不会覆盖。

首次在新机器上运行前，把 `.env.example` 复制为 `.env`，填入 MySQL 密码；模型密钥写在 `agent/.env`（或同样写进根目录 `.env`）。脚本会加载这两份文件，已有环境变量不会被覆盖。

| 服务 | 地址 |
| --- | --- |
| H5 | http://127.0.0.1:5173 |
| API | http://127.0.0.1:8080/api |
| Agent | http://127.0.0.1:8790/v1/harness/health |

日志在 `.run/logs/`。`install.sh` 仍只用于把 Skills 装到 Cursor / Claude / Codex，与应用启动无关。

## 微信小程序

`mini_program/` 是与 H5 **同一套 `/api` 契约**的原生小程序客户端，布局、色板、字号、交互按 H5 一比一还原（登录 / 工作台 / 文案库 / 我的）。

1. 先 `./start.sh` 让后端在 `:8080` 跑起来
2. 用[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)导入 `mini_program/` 目录（可先用测试号 / touristappid）
3. 详情里关闭「校验合法域名、web-view、TLS 版本以及 HTTPS 证书」

真机预览时把 `mini_program/config.js` 的 `API_BASE` 改成电脑的局域网地址，例如 `http://192.168.1.8:8080/api`。接口路径、字段、错误码与 H5 完全一致，后端零改动。

---

## 短视频口播文案 Skills

五个可安装到 **Claude Code / Cursor / Codex** 的 Agent Skill：

| Skill | 做什么 |
| --- | --- |
| `short-video-script` | 按「人设配置 + 可变参数」生成**选题 → 标题 → 口播文案**，交付前做一次去 AI 味重写 |
| `video-script-extract` | 把对标视频转成口播稿并做**结构拆解**（时间戳只留在节奏文件里）。支持抖音、快手、小红书、微信视频号、YouTube |
| `video-script-rewrite` | 基于原文拆解**重写整篇口播**（洗稿）：换身份、换论证、换案例，不是换同义词 |
| `douyin-benchmark-accounts` | 按关键词批量搜寻**抖音对标账号**，筛出活跃且粉丝达标的清单（默认粉丝 > 3000、30 天内有更新） |
| `douyin-benchmark-videos` | 按关键词批量搜寻**抖音对标视频**，筛出高互动的链接（默认点赞 > 300、评论 > 20）并抽文案 |

五个可独立使用，也能串成一条流水线：

```
douyin-benchmark-accounts  找到对标账号
        ↓
douyin-benchmark-videos    从账号或关键词找到高互动视频 + 链接清单
        ↓
video-script-extract       抽口播稿 → 结构拆解 → 逻辑框架
        ↓
        ├─ video-script-rewrite   同一选题，换成你来讲（洗稿）
        └─ short-video-script     只借骨架，换选题从零原创
```

三个平台共用同一份 `SKILL.md`（Agent Skills 开放标准），差别只在安装路径。

---

## 快速开始

```bash
git clone <本仓库> && cd smart_script_for_short_video_skills

./install.sh                      # 五个 skill 装到三个平台（个人级）
cp skills/short-video-script/config/persona.example.yaml ./persona.yaml
```

编辑 `persona.yaml` 填上你自己的六项固定参数，重启 Agent 会话，然后直接说：

> 帮我写 5 条短视频口播文案，转化类，爆款元素用荷尔蒙

或者只说「帮我写条短视频文案」，缺的参数会自动随机抽取。

要用对标拆解，再装一个 ASR 后端（三选一，短视频转写几秒到十几秒）：

```bash
# macOS/Linux 的系统 Python 多受 PEP 668 保护，建 venv 最省事，脚本会自动发现它
python3 -m venv ~/.short-video-script/venv
~/.short-video-script/venv/bin/pip install faster-whisper   # 本地，推荐

brew install whisper-cpp          # 本地，Apple Silicon 更快
export OPENAI_API_KEY=sk-...      # 云端，免安装

python3 skills/video-script-extract/scripts/transcribe.py --list-backends   # 环境自检
```

要用抖音搜寻（两个 `douyin-benchmark-*`），需要浏览器侧准备：

1. 安装 [Browser MCP](https://browsermcp.io) 扩展，并在 Cursor 的 MCP 配置里启用
2. **每次使用前手动点击扩展图标 → Connect**（这一步无法自动化）
3. 保持抖音已登录

然后直接说「用『AI就业』『AI转行』这两个词找 10 个对标账号」。

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
3. 文件 → 转写出口播稿（正文无时间戳，时间轴在节奏文件里）
4. 结构拆解：钩子类型、论证骨架、中段钩子秒数、结尾引导、反推 25 宫格坐标
5. 一条视频一个 md，存到 output/YYYYMMDD-拆解-<标题前12字>.md
6. 交接原创：同一选题洗稿走 video-script-rewrite；换选题从零写走 short-video-script
```

```bash
cd skills/video-script-extract
python3 scripts/fetch.py "https://v.douyin.com/xxxxxx/"      # 识别平台并尝试下载
python3 scripts/youtube.py "https://youtube.com/watch?v=xx"  # YouTube 取字幕，几秒出稿
python3 scripts/fetch.py --guide 视频号                        # 查看某平台取件步骤
python3 scripts/transcribe.py --input ~/Downloads/对标.mp4     # 转写
python3 scripts/transcribe.py --list-backends                # 环境自检
```

三个脚本产出的文件格式完全一致，都落到 `output/transcripts/`：断好行的口播稿、节奏分析（时长/字数/语速/分段时间轴）、原始时间戳 JSON。时间戳用来定位「黄金 3 秒说了什么」「中段钩子在第几秒」，这是拆解的关键输入。

**五个平台的自动化能力不一样**，这点在动手前要知道：

| 平台 | 贴链接自动取件 | 兜底 |
| --- | --- | --- |
| YouTube | **最省事**（字幕轨是公开数据，不下载不转写） | 无字幕轨时下音频跑 ASR |
| 小红书 | 有时可以（图文笔记的正文直接就是文案） | 录屏 |
| 抖音 | 通常可以（借浏览器 Cookie + 短链还原） | 录屏 |
| 快手 | 不稳定 | 录屏 |
| 视频号 | **不行**（加密分发，绕过属于规避技术措施） | 只能录屏 |

自动下载完全委托给你自己安装的 `yt-dlp`，本仓库不内置任何平台解析器，所以不会出现「上周还能用、这周报错」。拿不到就走录屏，路径始终可用——拆解只需要声音，一条 60 秒视频录屏不到一分钟。

**YouTube 单独一档**：它是唯一不用拿到视频文件就能拿到文案的平台。一条 30 分钟的视频，字幕路径 3 秒出稿，同样内容跑 ASR 要五分钟以上，还会转错专有名词。`fetch.py` 见到 YouTube 链接会自动转交 `youtube.py`，批量列表里混着也不用特殊处理。机翻字幕轨默认不用——用词已被机器洗过一遍，拿它拆「对方怎么措辞」没有意义。

---

## 洗稿工作流

基于原文拆解重写整篇口播。吸收的是选题逻辑、结构和情绪机制，交付的是换成你身份、换论证路径、换案例的新文案——不是换同义词。

```
1. 加载人设配置，补齐拍摄场景、内容目的、字数上限
2. 拿到完整原文（粘贴 / 拆解稿 / 先走 video-script-extract）
3. 先拆：为什么能留人、钩子、五维、原创风险
4. 再出方案：核心观点、与原文的三个差异、新钩子
5. 重写可拍口播 + 3 个备选开头 + 自检评分（低于 8 分自动改）
6. check.py 查章节、字数、禁用词、与原文连续重合
```

```bash
cd skills/video-script-rewrite
python3 scripts/check.py --rewrite ../../output/洗稿.md --original 原文.txt --max-words 500
python3 scripts/check.py --self-test
```

人设仍复用 `persona.yaml`。直接说「按这篇拆解洗稿」或把原文贴过来即可。

---

## 抖音对标搜寻工作流

```
1. 确认关键词、数量与阈值
2. 逐个关键词搜索，读快照收集候选（快照读不到数字就截图看图）
3. 只对已达标的候选补贵字段：账号进主页看更新时间，视频进详情页看评论数
4. 汇总 JSON → 脚本解析「3.5万」「3天前」这类展示值并筛选
5. 交付清单 + 人工判断（哪几个最值得对标、缺口多少）
```

```bash
cd skills/douyin-benchmark-accounts
python3 scripts/filter_accounts.py --input candidates.json --min-followers 3000 --active-within 30 --top 10
python3 scripts/filter_accounts.py --self-test

cd ../douyin-benchmark-videos
python3 scripts/filter_videos.py --input candidates.json --min-likes 300 --min-comments 20 --top 10
python3 scripts/filter_videos.py --input candidates.json --sort engagement   # 按互动率排序
```

零依赖。产物落在 `output/douyin-accounts/` 和 `output/douyin-videos/`，后者额外给一份
`*-urls.txt` 纯链接清单，供逐条抽文案。

**数据从哪来**：全程走你自己的浏览器和登录态，读页面上本来就展示给你的内容。**不实现
`a_bogus`/`msToken` 之类的签名生成**——那既属于规避技术措施，也会随平台更新反复失效。
也不建议用第三方代抓服务，那要把账号 Cookie 交给对方。

**按互动率排序往往比按点赞排序更有用**：高赞可能只是踩了流量，而互动率（评论÷点赞）高说明
选题有争议或强共鸣，拆解价值更大。

---

## 安装

```bash
./install.sh                                    # 五个 skill 装到三个平台，个人级
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
    ├── video-script-extract/
    │   ├── SKILL.md                      # 主入口：取件 → 转写 → 拆解 → 交接
    │   ├── scripts/
    │   │   ├── fetch.py                  # 平台识别 + 尝试下载 + 取件指引
    │   │   ├── youtube.py                # YouTube 字幕轨抽取 + 章节提取
    │   │   └── transcribe.py             # 多后端 ASR + 文本清洗 + 节奏分析
    │   └── references/
    │       ├── platforms.md              # 五平台能力矩阵、录屏要点、合规边界
    │       ├── asr-setup.md              # 三个 ASR 后端的安装选型与故障排查
    │       └── teardown.md               # 结构拆解模板 + 完整示例
    ├── video-script-rewrite/
    │   ├── SKILL.md                      # 主入口：收身份 → 拆原文 → 方案 → 重写 → 校验
    │   ├── scripts/check.py              # 十一段、字数、禁用词、与原文连续重合
    │   └── references/rewrite-prompt.md  # 三阶段合同 + 十一段输出格式
    ├── douyin-benchmark-accounts/
    │   ├── SKILL.md                      # 主入口：搜索 → 补字段 → 筛选 → 交付
    │   ├── scripts/filter_accounts.py    # 粉丝数/时间解析、活跃度判定、去重排序
    │   └── references/browser-recipes.md # 页面字段位置、翻页、反爬、快照降级
    └── douyin-benchmark-videos/
        ├── SKILL.md                      # 主入口：搜索 → 补评论数 → 筛选 → 抽文案
        ├── scripts/filter_videos.py      # 点赞/评论解析、互动率、链接清单导出
        └── references/browser-recipes.md # 页面字段位置、省时间的两轮顺序
```

`SKILL.md` 只放工作流骨架，细则按需读取 `references/`——三个平台都只把 `name` 和 `description` 常驻上下文，正文和参考文件按需加载。

---

## 已知边界

- **视频号只能录屏**，做不到贴链接出文案。它的视频加密分发，取件需要抓包拿配对密钥，绕过属于规避平台技术措施，本仓库不实现，也不建议用第三方在线解析站（要把带你身份信息的分享链接交给对方服务器）。
- **抖音、快手的自动下载不稳定**，小红书需要带 `xsec_token` 的完整链接。失败即降级到录屏，不会卡住。
- **YouTube 没字幕轨就没有快路**：作者没传字幕、YouTube 也没生成自动字幕时（非英语的小频道常见），只能下音频跑 ASR。另外 YouTube 的下载风控时紧时松，脚本会自动换几个 player client 重试，都失败时靠 `--browser` 借登录态或升级 yt-dlp，不做绕过。
- **ASR 会出错**，专有名词、数字、岗位名尤其容易错。拆解时影响判断的数字会标注存疑，不当成原文事实。
- **抖音搜寻做不到无人值守**：browsermcp 扩展需要你手动点 Connect，登录态和验证码也需要人处理。它省的是翻页、记录、换算和筛选，不是省掉你在场。
- **抖音数字只有展示精度**：平台只给到「3.5万」这一档，阈值卡在万级附近时会有误差。读不到的字段一律留空并标为「需人工确认」，不猜。
- **完整口播稿无法批量抽取**：`douyin-benchmark-videos` 批量交付的是链接和页面描述文案（稳定可得），完整口播稿要逐条走 ASR，抖音下载不稳时得录屏。所以只对最值得拆的 2-3 条做。
- **洗稿不是换同义词**。原文的个人经历、标志性金句、未验证数据一律不沿用；也不虚构用户履历和成交数字。缺事实就问，或标「需要我补充」。
- **行业热点借势融合**依赖联网检索。检索不可用时会向你索取热点，不会编造新闻和数据。
- 涉及真实用户留言、个人经历、具体数据的内容一律不编造，缺素材就问你。
