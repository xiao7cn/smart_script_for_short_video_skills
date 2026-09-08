---
name: video-script-extract
description: >-
  抽取短视频口播文案并做结构拆解，支持抖音、快手、小红书、微信视频号、YouTube。
  把链接或录屏文件转成口播稿并做结构拆解（时间戳只留在节奏文件里），再拆出钩子类型、论证骨架、中段钩子位置、结尾引导方式，
  反推 25 宫格坐标与爆款元素，产出可原创复用的逻辑框架。
  YouTube 走字幕轨，贴链接几秒出稿，不用下载也不用转写。
  Use when the user 想拆解对标视频 / 提取视频文案 / 扒口播稿 / 分析爆款结构 / 视频转文字 / 要对标账号的文案 / 抽 YouTube 文案 / 油管视频转文字，
  gives a 抖音·快手·小红书·视频号·YouTube 链接 or a 录屏文件, or when short-video-script 的选题来源是「对标爆款拆解」.
---

# 短视频文案抽取与结构拆解

配合另外两个 skill：这个 skill 负责把对标视频变成口播稿和逻辑框架；`video-script-rewrite` 按同一选题洗稿，`short-video-script` 换选题从零原创。

以下路径均相对于本 `SKILL.md` 所在目录。

---

## 先明确一件事：链接不一定能直接下载

五个平台的自动化能力天差地别，动手前先对号：

| 平台 | 贴链接自动取件 | 说明 |
| --- | --- | --- |
| YouTube | **最省事** | 字幕轨是公开数据，`youtube.py` 直接取，几秒出稿，不下载不转写。没字幕轨才回落 ASR |
| 小红书 | 有时可以 | 图文笔记的正文就是文案，直接复制即可；视频笔记需要带 `xsec_token` 的完整链接 |
| 抖音 | **通常可以** | 前提是借用浏览器 Cookie，且短链要先还原成规范地址——`fetch.py` 已自动做这两件事 |
| 快手 | 不稳定 | 同样会尝试借 Cookie，成功率低于抖音，失败就走录屏 |
| 视频号 | **不行** | 视频加密分发，取件需抓包拿配对密钥。这属于规避平台技术措施，本 skill 不做，只走录屏 |

YouTube 和其余四家根本不是一回事：国内平台要「下文件 → 跑 ASR」，YouTube 直接有现成的文字。
一条 30 分钟的视频，字幕路径 3 秒出稿，ASR 要跑五分钟以上。所以**见到 YouTube 链接先走字幕**，
不要习惯性地去下视频。

抖音那两个前提是踩过的坑，已经固化在 `fetch.py` 里，理解一下省得改坏：

- **必须带登录态 Cookie**。裸请求一定报 `Fresh cookies (not necessarily logged in) are needed`。脚本用 yt-dlp 原生的 `--cookies-from-browser` 读用户自己浏览器里的会话——和用真实浏览器打开页面是同一件事，不构造也不伪造任何签名。用户浏览器里没登录抖音时仍会失败，这时提示他去登录，而不是换招。
- **`v.douyin.com` 短链必须先还原**。直接把短链交给 yt-dlp，即使带了 Cookie 也报同样的错：重定向链路会把提取器需要的那份 Cookie 冲掉。脚本会自己跟完重定向、取出作品 ID，重新拼成 `https://www.douyin.com/video/<id>` 再下载。

录屏依然是兜底路径，且对四个平台一视同仁。自动下载失败时不要反复重试或另寻门路，直接给出该平台的录屏步骤——`scripts/fetch.py` 会自动打印。

同样不要建议用户去第三方在线解析站：那需要把带其身份信息的分享链接交给别人的服务器。

---

## 工作流

| 步骤 | 动作 |
| --- | --- |
| 1 | 判断输入类型：链接 / 本地文件 / 用户已粘贴的文案 |
| 2 | YouTube 链接 → 取字幕，直接出稿，跳到第 4 步 |
| 2' | 其余平台链接 → 尝试取件，失败则给录屏指引并等用户提供文件 |
| 3 | 文件 → 转写出口播稿（正文无时间戳） |
| 4 | 结构拆解，产出拆解表 + 逻辑框架 |
| 5 | 交接给 `short-video-script` 做原创 |

### 步骤 1-2：取件

```bash
python3 scripts/fetch.py "https://v.douyin.com/xxxxxx/"   # 识别平台并尝试下载
python3 scripts/fetch.py URL1 URL2 URL3                   # 批量，一次跑完多条
python3 scripts/fetch.py --from-file urls.txt             # 接 douyin-benchmark-videos 的 *-urls.txt
python3 scripts/fetch.py --browser chrome URL            # 指定借哪个浏览器的 Cookie
python3 scripts/fetch.py --audio-only URL                # 只留音频，转写更快
python3 scripts/fetch.py --guide 视频号                     # 直接查看某平台取件步骤
```

用户一次给多个链接时用批量模式，不要一条一条调。省略 `--browser` 时脚本按
chrome → brave → edge → firefox → safari 依次探测，知道用户用哪个浏览器就直接指定，省几次失败重试。

退出码 3 表示**至少有一条**需要用户手动提供文件。此时把脚本打印的步骤转达给用户，
成功的那些照常继续转写，只对失败的停下等文件路径。

用户直接粘贴了文案原文时跳到步骤 4，不需要转写。小红书图文笔记通常属于这种情况。

### 步骤 2 的 YouTube 分支：取字幕，别下视频

```bash
python3 scripts/youtube.py "https://www.youtube.com/watch?v=xxx"   # 取字幕直接出稿
python3 scripts/youtube.py --list-tracks URL                       # 先看有哪些字幕轨
python3 scripts/youtube.py --lang en URL                           # 指定语言偏好，默认 zh-Hans,zh,en
python3 scripts/youtube.py --allow-translated URL                  # 原语言轨没有时接受机翻
python3 scripts/youtube.py --audio URL                             # 没字幕时下音频，交给 transcribe.py
```

产出与 `transcribe.py` 完全一致（`.txt` / `.rhythm.txt` / `.segments.json`），拿到就能进第 4 步。
`fetch.py` 见到 YouTube 链接会自动转交这个脚本，所以批量列表里混着 YouTube 也不用特殊处理。

三件事值得知道：

- **优先手动字幕，其次原语言自动字幕，机翻轨默认不用**。机翻是从原语言翻过来的，用词习惯已经被洗掉一遍，拿它拆「对方怎么措辞」等于拆机器的措辞。真要用得显式加 `--allow-translated`，并且拆解时只看结构不看用词。
- **自动字幕没有标点**（中文尤其），脚本按词级时间戳的停顿断句，不会替 ASR 臆造句号。所以正文的句读看着可能怪，这不影响拆解——秒数和语义单元是准的。
- **YouTube 有章节**，`.rhythm.txt` 里会列出来。这是作者自己标的结构骨架，国内平台没有，拆长视频时先看它。

退出码 3 表示没有可用字幕轨，此时按提示走 `--audio` + `transcribe.py`。

YouTube 对下载的风控时紧时松，脚本会自动换几个 player client 重试。都失败时先试 `--browser chrome`
借登录态，再不行就 `brew upgrade yt-dlp`——风控变动通常几天内会被跟进，不要自己想办法绕。

### 长视频不是短视频

YouTube 上大量内容是十几分钟起步的长视频，拆解读法和 60 秒短视频不一样：

- 钩子密度那套（10-15 秒一个钩子）不适用，不要硬套着算
- 重点看**开场 30 秒**怎么留人，以及**章节骨架**怎么组织信息
- 拆解表的「结构骨架」直接用章节做一级分段，每章再摘 1-2 句代表性原句
- 超过 5 分钟时 `.rhythm.txt` 会自动提示这一点

### 步骤 3：转写

```bash
python3 scripts/transcribe.py --input ~/Downloads/对标视频.mp4
python3 scripts/transcribe.py --list-backends              # 环境自检
```

后端自动探测，优先级 faster-whisper → whisper.cpp → OpenAI API。一个都没有时脚本会打印安装命令，转达给用户即可，安装选型见 `references/asr-setup.md`。

产出三个文件到 `output/transcripts/`：

- `<name>.txt` — 一句一行的口播稿，**不要带时间戳**
- `<name>.rhythm.txt` — 总时长、字数、语速，以及句子级时间轴
- `<name>.segments.json` — 句子级时间戳，供程序消费

交付拆解稿时，「口播原文」抄 `.txt`，不要把 `.rhythm.txt` 里的 `[0.0]` 一并贴进去。拆钩子用节奏文件和拆解表里的秒数栏。

转写开启了词级时间戳并按句重切分段。Whisper 常把十几秒连续语音作为一整段返回，不重切就只有一个时间条目，没法定位钩子。

**转写不是誊抄**。ASR 会出错，专有名词、数字、岗位名尤其容易错。拆解前先扫一遍明显的错字，拿不准的地方标注「此处 ASR 可能有误」，不要当成原文事实去分析。

### 步骤 4：结构拆解

读 `references/teardown.md`，按模板输出拆解表。核心是拆出**思考逻辑**，不是抄文案：

- 看对方是怎么论证观点、输出内容的
- 挖掘有没有你想不到的角度、观点
- 借鉴这套思考逻辑，完全原创输出属于你自己的内容

时间戳在这一步很关键：钩子在第几秒出现、每两个钩子间隔多少秒、黄金 3 秒实际说了什么，都要落到具体秒数。`.rhythm.txt` 里的语速值还能反推对方的口播节奏。

### 步骤 5：交接原创

拆解完主动问用户要不要接着出原创文案。两条路：

- **同一选题，换成用户来讲**（洗稿）→ `video-script-rewrite`，把口播原文和本拆解一起交过去
- **只借骨架，换选题从零写** → `short-video-script`，走它的步骤 2 起

本 skill 只交拆解，不在这里改写。换词、换语序、换案例都不是本步骤的产出。

---

## 交付格式

**一条视频一个 md 文件**，不要把多条拆解写进同一篇。一次抽 N 条就写 N 个文件。

存到仓库根目录 `output/YYYYMMDD-拆解-<短标题>.md`。文件名用完整短句，宁可短一点，不要截断半个词。

```markdown
## 对标拆解：<标题或来源>

**平台**：<平台>　**时长**：<秒>　**字数**：<字>　**语速**：<字/分钟>

### 口播原文
<断好行的正文，一句一行或按语义分段。不要写 [0.0] 这类时间戳；标注 ASR 存疑处>

### 结构拆解
<按 references/teardown.md 的模板>

### 可原创复用的逻辑框架
1. <骨架第一段的功能，不含具体内容>
2. ...

### 对方想到而我们没想到的角度
- <角度，以及为什么有效>
```

---

## 参考文件

| 文件 | 内容 | 何时读 |
| --- | --- | --- |
| `references/platforms.md` | 五平台能力矩阵、取件详细步骤、合规边界 | 步骤 1-2 |
| `references/asr-setup.md` | 三个 ASR 后端的安装与选型、模型选择、故障排查 | 步骤 3 环境不全时 |
| `references/teardown.md` | 结构拆解模板与示例 | 步骤 4 |
| `scripts/fetch.py` | 平台识别 + 尝试下载 + 取件指引，YouTube 自动转交 | 步骤 1-2 |
| `scripts/youtube.py` | YouTube 字幕轨抽取 + 章节提取，无字幕时下音频 | 步骤 2 的 YouTube 分支 |
| `scripts/transcribe.py` | 多后端 ASR + 文本清洗 + 节奏分析 | 步骤 3 |

---

## 合规底线

- 拆解仅用于学习对方的内容逻辑并产出原创，不做二次分发，不搬运，不冒用对方素材。
- 不实现也不建议任何形式的平台加密绕过、签名伪造、协议逆向。
- 自动下载只调用用户自行安装的通用工具；工具拿不到就老实走录屏。
- 涉及他人肖像、声音的素材不外传，转写产物留在本地 `output/`。
