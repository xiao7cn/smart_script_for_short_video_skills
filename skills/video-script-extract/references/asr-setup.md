# ASR 后端：装哪个、怎么装

`scripts/transcribe.py` 自动探测后端，装任意一个即可。先跑自检看现状：

```bash
python3 scripts/transcribe.py --list-backends
```

---

## 三个后端怎么选

| 后端 | 装起来 | 隐私 | 成本 | 适合 |
| --- | --- | --- | --- | --- |
| faster-whisper | `pip install faster-whisper` | 全本地 | 免费 | 大多数人的默认选择 |
| whisper.cpp | `brew install whisper-cpp` + 下模型 | 全本地 | 免费 | Apple Silicon，Metal 加速更快 |
| OpenAI API | 设一个环境变量 | 音频上传到 OpenAI | 约 $0.006/分钟 | 不想装东西，或机器太弱 |

短视频只有 30-90 秒，三个后端的速度差异在体感上可以忽略——一条视频都是几秒到十几秒转完。**选择的真正依据是隐私和安装成本**：对标视频是公开内容，走云端没什么顾虑；但如果你要转的是自己的客户咨询录音，选本地。

---

## faster-whisper（推荐）

macOS 的 Homebrew Python 和多数 Linux 发行版的系统 Python 受 PEP 668 保护，`pip install` 会被直接拒绝（报 `externally-managed-environment`）。建一个 venv 是最干净的做法：

```bash
python3 -m venv ~/.short-video-script/venv
~/.short-video-script/venv/bin/pip install faster-whisper
```

**装到这个路径后不需要改任何命令**：`transcribe.py` 会自动发现并切换到这个解释器（切换时会在 stderr 打一行提示）。仍然照常用系统 `python3` 调用即可：

```bash
python3 scripts/transcribe.py --input 视频.mp4
python3 scripts/transcribe.py --input 视频.mp4 --model tiny     # 先用 75MB 小模型跑通链路
```

如果你的 Python 环境本来就能自由装包（conda、pyenv、已激活的 venv），直接 `pip install faster-whisper` 也一样，脚本会优先用当前解释器。

首次运行自动下载模型到 `~/.cache/huggingface`，`large-v3-turbo` 约 1.5 GB。自带 PyAV 解码，不需要 ffmpeg，也自带 VAD（静音切分）。没有 GPU 也能用，脚本用 `device="auto"` + int8 量化，实测 Apple Silicon 纯 CPU 上转一条 17 秒的音频约 8 秒（模型已缓存）。

转写会开启词级时间戳，用来把 Whisper 返回的长分段重切成句子级——否则十几秒的连续语音会挤成一个条目，没法定位钩子出现在第几秒。

## whisper.cpp

```bash
brew install whisper-cpp
```

模型要自己下。装完后把 ggml 模型放到任意位置，脚本会在这些路径找：

- `~/.cache/whisper.cpp/`
- `~/whisper.cpp/models/`
- `/opt/homebrew/share/whisper.cpp/models/`

放别处就指个环境变量：

```bash
export WHISPER_CPP_MODEL=/path/to/ggml-large-v3-turbo.bin
```

这个后端需要 16kHz 单声道 WAV，脚本会自动用 ffmpeg 转（`brew install ffmpeg`）。Apple Silicon 上走 Metal，M2 实测 large-v3-turbo 约 1.5 倍实时速度。

## OpenAI API

```bash
export OPENAI_API_KEY=sk-...
```

不用装任何东西。文件超过 25 MB 时脚本会先用 ffmpeg 压成单声道 32kbps 音频再上传，短视频基本碰不到这个上限。

---

## 模型选择

| 模型 | 大小 | 中文错字率 | 说明 |
| --- | --- | --- | --- |
| `tiny` | 75 MB | 高 | 只用来验证链路通不通 |
| `base` | 142 MB | 偏高 | 人名、岗位名、数字容易错 |
| `small` | 466 MB | 中 | 勉强够用 |
| `large-v3-turbo` | 1.5 GB | 低 | **默认，中文推荐** |

短视频时长短，没有理由为了省几秒去用小模型——错一个薪资数字就够毁掉整个拆解。

---

## 故障排查

**「没有可用的 ASR 后端」**：三个都没装，或装到了脚本找不到的解释器里。跑 `--list-backends` 看现状，按上面任选一个装。

**`pip install` 报 externally-managed-environment**：系统 Python 受 PEP 668 保护，按上面的 venv 方式装。

**输出是繁体字**：Whisper 转中文时会不定期输出繁体。脚本已经传了简体 `initial_prompt` 压制，仍然出现时让 agent 在拆解前转成简体即可，不影响结构分析。

**转写结果为空**：确认文件里真的有人说话（纯音乐、纯字幕的视频没有口播）。也可能是录屏时录的是麦克风而不是内部音频，导出的文件只有环境噪音——用播放器听一下就知道。

**转写结果一堆重复字词**：ASR 幻听，常见于开头结尾的静音段。脚本会清掉连续 4 次以上的同字重复，剩下的手动删。换更大的模型也能缓解。

**专有名词、数字错得多**：这是 ASR 的固有限制，不是配置问题。拆解时凡是影响判断的数字都标注「此处 ASR 可能有误」，别当成原文事实。

**whisper.cpp 报找不到模型**：跑一次 `--list-backends`，它会告诉你是缺二进制还是缺模型文件。
