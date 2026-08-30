#!/usr/bin/env python3
"""把音视频文件转成口播文案，并输出带时间戳的节奏数据。

后端自动探测，优先级：faster-whisper（本地）→ whisper.cpp（本地）→ OpenAI API（云）。
三个后端都没有时打印安装指引后退出，不会静默失败。

用法:
    python3 transcribe.py --input video.mp4
    python3 transcribe.py --input video.mp4 --backend openai
    python3 transcribe.py --list-backends
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path

DEFAULT_MODEL = "large-v3-turbo"
OPENAI_MAX_BYTES = 25 * 1024 * 1024
# 口播语速经验值，用于把字数换算成秒，判断钩子间隔是否达标
CHARS_PER_MINUTE = 300
# Homebrew / 系统 Python 受 PEP 668 保护装不了包，约定一个 venv 位置并自动接管
VENV_PYTHON = Path.home() / ".short-video-script/venv/bin/python"
REEXEC_FLAG = "SVS_TRANSCRIBE_REEXEC"
# Whisper 转中文时会不定期输出繁体，用简体 prompt 把它拉回来
SIMPLIFIED_PROMPT = "以下是一段简体中文的短视频口播文案，内容涉及求职、面试、薪资和职业规划。"


# ────────────────────────── 后端探测 ──────────────────────────

def has_faster_whisper() -> bool:
    try:
        import faster_whisper  # type: ignore # noqa: F401

        return True
    except ImportError:
        return False


def venv_has_faster_whisper() -> bool:
    if not VENV_PYTHON.is_file():
        return False
    result = subprocess.run(
        [str(VENV_PYTHON), "-c", "import faster_whisper"], capture_output=True
    )
    return result.returncode == 0


def maybe_reexec_in_venv() -> None:
    """当前解释器缺 faster-whisper 但约定 venv 里有，就用 venv 的解释器重跑本脚本。"""
    if os.environ.get(REEXEC_FLAG) or has_faster_whisper():
        return
    if not venv_has_faster_whisper():
        return
    print(f"切换到 {VENV_PYTHON}", file=sys.stderr)
    os.environ[REEXEC_FLAG] = "1"
    os.execv(str(VENV_PYTHON), [str(VENV_PYTHON), os.path.abspath(__file__), *sys.argv[1:]])


def whisper_cpp_binary() -> str | None:
    for name in ("whisper-cli", "whisper-cpp", "main"):
        path = shutil.which(name)
        if path and name != "main":
            return path
    return None


def whisper_cpp_model(model: str) -> str | None:
    """按常见安装位置找 ggml 模型文件。"""
    if env := os.environ.get("WHISPER_CPP_MODEL"):
        return env if Path(env).is_file() else None
    candidates = [
        Path.home() / ".cache/whisper.cpp",
        Path.home() / "whisper.cpp/models",
        Path("/opt/homebrew/share/whisper.cpp/models"),
        Path("/usr/local/share/whisper.cpp/models"),
    ]
    for directory in candidates:
        for name in (f"ggml-{model}.bin", f"ggml-{model}-q5_0.bin"):
            if (directory / name).is_file():
                return str(directory / name)
    return None


def has_openai() -> bool:
    return bool(os.environ.get("OPENAI_API_KEY"))


def detect_backend(requested: str, model: str) -> str:
    available = available_backends(model)
    if requested != "auto":
        if requested not in available:
            sys.exit(
                f"后端 {requested} 不可用。当前可用：{', '.join(available) or '无'}\n"
                f"安装指引见 references/asr-setup.md，或运行 scripts/doctor.sh"
            )
        return requested
    if not available:
        sys.exit(
            "没有可用的 ASR 后端。任选一个装上：\n\n"
            "  faster-whisper（推荐，全本地）：\n"
            f"    python3 -m venv {VENV_PYTHON.parent.parent}\n"
            f"    {VENV_PYTHON.parent}/pip install faster-whisper\n"
            "    装好后本脚本会自动使用这个 venv，命令不用改\n\n"
            "  whisper.cpp（Apple Silicon 更快）：brew install whisper-cpp，再下 ggml 模型\n"
            "  OpenAI API（免安装）：export OPENAI_API_KEY=sk-...\n\n"
            "详见 references/asr-setup.md"
        )
    return available[0]


def available_backends(model: str) -> list[str]:
    backends = []
    if has_faster_whisper():
        backends.append("faster-whisper")
    if whisper_cpp_binary() and whisper_cpp_model(model):
        backends.append("whisper-cpp")
    if has_openai():
        backends.append("openai")
    return backends


# ────────────────────────── 各后端转写 ──────────────────────────

def run_faster_whisper(audio: Path, model: str, language: str) -> list[dict]:
    from faster_whisper import WhisperModel  # type: ignore

    whisper = WhisperModel(model, device="auto", compute_type="int8")
    segments, _ = whisper.transcribe(
        str(audio),
        language=language,
        vad_filter=True,
        word_timestamps=True,
        initial_prompt=SIMPLIFIED_PROMPT if language == "zh" else None,
    )
    result = []
    for seg in segments:
        words = [{"start": word.start, "end": word.end, "text": word.word} for word in seg.words or []]
        result.append({"start": seg.start, "end": seg.end, "text": seg.text.strip(), "words": words})
    return result


def run_whisper_cpp(audio: Path, model: str, language: str) -> list[dict]:
    binary = whisper_cpp_binary()
    model_path = whisper_cpp_model(model)
    wav = ensure_wav(audio)
    with tempfile.TemporaryDirectory() as tmp:
        prefix = Path(tmp) / "out"
        command = [binary, "-m", model_path, "-f", str(wav), "-l", language, "-oj", "-of", str(prefix)]
        if language == "zh":
            command += ["--prompt", SIMPLIFIED_PROMPT]
        subprocess.run(command, check=True, capture_output=True)
        data = json.loads(Path(f"{prefix}.json").read_text(encoding="utf-8"))
    return [
        {
            "start": offsets_to_seconds(item["offsets"]["from"]),
            "end": offsets_to_seconds(item["offsets"]["to"]),
            "text": item["text"].strip(),
        }
        for item in data.get("transcription", [])
    ]


def offsets_to_seconds(milliseconds: int) -> float:
    return round(milliseconds / 1000, 2)


def run_openai(audio: Path, language: str) -> list[dict]:
    source = audio
    if source.stat().st_size > OPENAI_MAX_BYTES:
        source = ensure_compressed_audio(audio)
    boundary = uuid.uuid4().hex
    fields = {"model": "whisper-1", "language": language, "response_format": "verbose_json"}
    if language == "zh":
        fields["prompt"] = SIMPLIFIED_PROMPT
    body = bytearray()
    for key, value in fields.items():
        body += (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n{value}\r\n"
        ).encode()
    body += (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
        f"filename=\"{source.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
    ).encode()
    body += source.read_bytes() + f"\r\n--{boundary}--\r\n".encode()

    request = urllib.request.Request(
        "https://api.openai.com/v1/audio/transcriptions",
        data=bytes(body),
        headers={
            "Authorization": f"Bearer {os.environ['OPENAI_API_KEY']}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            data = json.loads(response.read())
    except urllib.error.HTTPError as error:
        sys.exit(f"OpenAI 转写失败 HTTP {error.code}：{error.read().decode(errors='replace')[:300]}")
    return [
        {"start": seg["start"], "end": seg["end"], "text": seg["text"].strip()}
        for seg in data.get("segments", [])
    ] or [{"start": 0.0, "end": 0.0, "text": data.get("text", "").strip()}]


# ────────────────────────── 音频预处理 ──────────────────────────

def require_ffmpeg() -> str:
    binary = shutil.which("ffmpeg")
    if not binary:
        sys.exit("需要 ffmpeg：brew install ffmpeg（或改用 faster-whisper 后端，它自带解码）")
    return binary


def ensure_wav(source: Path) -> Path:
    """whisper.cpp 只吃 16kHz 单声道 WAV。"""
    if source.suffix.lower() == ".wav":
        return source
    target = Path(tempfile.gettempdir()) / f"{source.stem}-16k.wav"
    subprocess.run(
        [require_ffmpeg(), "-y", "-i", str(source), "-vn", "-ar", "16000", "-ac", "1",
         "-c:a", "pcm_s16le", str(target)],
        check=True,
        capture_output=True,
    )
    return target


def ensure_compressed_audio(source: Path) -> Path:
    target = Path(tempfile.gettempdir()) / f"{source.stem}-mono.m4a"
    subprocess.run(
        [require_ffmpeg(), "-y", "-i", str(source), "-vn", "-ac", "1", "-ar", "16000",
         "-b:a", "32k", str(target)],
        check=True,
        capture_output=True,
    )
    return target


# ────────────────────────── 文本清洗 ──────────────────────────

def clean_segments(segments: list[dict]) -> list[dict]:
    cleaned = []
    for seg in segments:
        text = normalize(seg["text"])
        if not text:
            continue
        if cleaned and text == cleaned[-1]["text"]:
            cleaned[-1]["end"] = seg["end"]
            continue
        entry = {"start": seg["start"], "end": seg["end"], "text": text}
        if seg.get("words"):
            entry["words"] = seg["words"]
        cleaned.append(entry)
    return cleaned


def split_into_sentences(segments: list[dict]) -> list[dict]:
    """把 ASR 分段切成句子级分段。

    Whisper 常把十几秒的连续语音作为一个分段返回，这样时间轴只剩一个条目，
    没法定位钩子出现在第几秒。这里按句末标点重切：有词级时间戳就用精确值，
    没有就按字符位置线性插值（匀速口播下误差可接受）。
    """
    sentences = []
    for seg in segments:
        pieces = split_text(seg["text"])
        if len(pieces) <= 1:
            sentences.append({"start": seg["start"], "end": seg["end"], "text": seg["text"]})
            continue
        if words := seg.get("words"):
            sentences += align_with_words(pieces, words, seg)
        else:
            sentences += interpolate(pieces, seg)
    return sentences


def split_text(text: str, max_chars: int = 35) -> list[str]:
    """先按句末标点切，超长的句子再在逗号处切一次，避免出现无法阅读的长行。"""
    rough = [piece for piece in re.split(r"(?<=[。！？])", text) if piece.strip()]
    pieces: list[str] = []
    for piece in rough:
        while len(piece) > max_chars:
            cut = piece.rfind("，", 0, max_chars + 1)
            if cut <= 0:
                break
            pieces.append(piece[: cut + 1])
            piece = piece[cut + 1 :]
        if piece:
            pieces.append(piece)
    return pieces


def align_with_words(pieces: list[str], words: list[dict], seg: dict) -> list[dict]:
    """用词级时间戳给每个句子定位。词文本与句子文本按去标点后的字符逐一对齐。"""
    cursor = 0
    flat = [(word, strip_punctuation(word["text"])) for word in words]
    result = []
    for piece in pieces:
        target = len(strip_punctuation(piece))
        consumed, matched = 0, []
        while cursor < len(flat) and consumed < target:
            word, plain = flat[cursor]
            matched.append(word)
            consumed += len(plain)
            cursor += 1
        if matched:
            result.append({"start": round(matched[0]["start"], 2), "end": round(matched[-1]["end"], 2), "text": piece})
        else:
            result.append({"start": seg["end"], "end": seg["end"], "text": piece})
    return result


def interpolate(pieces: list[str], seg: dict) -> list[dict]:
    total = sum(len(piece) for piece in pieces) or 1
    span = max(seg["end"] - seg["start"], 0.0)
    result, offset = [], 0
    for piece in pieces:
        start = seg["start"] + span * offset / total
        offset += len(piece)
        result.append({"start": round(start, 2), "end": round(seg["start"] + span * offset / total, 2), "text": piece})
    return result


def strip_punctuation(text: str) -> str:
    return re.sub(r"[\s，。！？、；：,.!?;:\"'“”‘’()（）]", "", text)


def normalize(text: str) -> str:
    text = text.strip()
    text = re.sub(r"[ \t]+", "", text)                       # 中文里的空格多为 ASR 噪声
    text = to_fullwidth_punctuation(text)
    text = re.sub(r"([，。！？、；：])\1+", r"\1", text)        # 叠标点
    text = re.sub(r"(.)\1{3,}", r"\1\1", text)               # 同字连续 4 次以上多为幻听
    return text


def to_fullwidth_punctuation(text: str) -> str:
    """ASR 常混用半角标点，不归一会让后续断句失效。数字内的分隔符与小数点保持原样。"""
    text = re.sub(r"(?<!\d)[,，](?!\d)", "，", text)
    text = re.sub(r"(?<!\d)\.(?!\d)", "。", text)
    for half, full in (("?", "？"), ("!", "！"), (";", "；"), (":", "：")):
        text = text.replace(half, full)
    return text


def to_script(sentences: list[dict]) -> str:
    """一句一行，便于朗读比对。"""
    return "\n".join(sentence["text"].strip() for sentence in sentences if sentence["text"].strip())


def rhythm_report(segments: list[dict], script: str) -> str:
    chars = len(re.sub(r"\s", "", script))
    duration = segments[-1]["end"] if segments and segments[-1]["end"] else 0.0
    lines = [
        f"总时长：{duration:.1f} 秒" if duration else "总时长：未知（后端未返回时间戳）",
        f"字数：{chars} 字",
    ]
    if duration > 0:
        lines.append(f"语速：{chars / duration * 60:.0f} 字/分钟（口播经验值 {CHARS_PER_MINUTE} 上下）")
    else:
        lines.append(f"按 {CHARS_PER_MINUTE} 字/分钟估算时长：{chars / CHARS_PER_MINUTE * 60:.0f} 秒")
    lines.append("")
    lines.append("分段时间轴（用于定位黄金 3 秒与中段钩子）：")
    for seg in segments:
        lines.append(f"  [{seg['start']:>6.1f}s] {seg['text']}")
    return "\n".join(lines)


# ────────────────────────── 主流程 ──────────────────────────

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="音视频转口播文案")
    parser.add_argument("--input", help="本地音频或视频文件")
    parser.add_argument(
        "--backend",
        default="auto",
        choices=["auto", "faster-whisper", "whisper-cpp", "openai"],
        help="ASR 后端，默认自动探测",
    )
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"本地后端模型，默认 {DEFAULT_MODEL}")
    parser.add_argument("--language", default="zh", help="语言代码，默认 zh")
    parser.add_argument("--out-dir", default="output/transcripts", help="输出目录")
    parser.add_argument("--list-backends", action="store_true", help="打印可用后端后退出")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    if argv is None:  # 仅真实 CLI 调用时接管解释器，避免干扰以自定义 argv 调用的测试
        maybe_reexec_in_venv()
    args = parse_args(argv)

    if args.list_backends:
        available = available_backends(args.model)
        print("可用后端：" + (", ".join(available) if available else "无"))
        print("\n未就绪的后端：")
        if not has_faster_whisper():
            print("  faster-whisper  →  见 references/asr-setup.md（系统 Python 通常需要建 venv）")
        if not whisper_cpp_binary():
            print("  whisper-cpp     →  brew install whisper-cpp")
        elif not whisper_cpp_model(args.model):
            print(f"  whisper-cpp     →  已装二进制，缺 ggml-{args.model}.bin 模型")
            print("                     下载后设 WHISPER_CPP_MODEL=/path/to/ggml-model.bin")
        if not has_openai():
            print("  openai          →  export OPENAI_API_KEY=sk-...")
        print("\n辅助工具：")
        ffmpeg = "已安装" if shutil.which("ffmpeg") else "未安装（brew install ffmpeg，whisper-cpp 后端必需）"
        yt_dlp = "已安装" if shutil.which("yt-dlp") else "未安装（brew install yt-dlp，用于尝试自动下载）"
        print(f"  ffmpeg   {ffmpeg}")
        print(f"  yt-dlp   {yt_dlp}")
        return 0

    if not args.input:
        sys.exit("缺少 --input，或用 --list-backends 检查环境")
    source = Path(args.input).expanduser()
    if not source.is_file():
        sys.exit(f"找不到文件：{source}")

    backend = detect_backend(args.backend, args.model)
    print(f"后端：{backend}    文件：{source.name}", file=sys.stderr)

    if backend == "faster-whisper":
        raw = run_faster_whisper(source, args.model, args.language)
    elif backend == "whisper-cpp":
        raw = run_whisper_cpp(source, args.model, args.language)
    else:
        raw = run_openai(source, args.language)

    segments = clean_segments(raw)
    if not segments:
        sys.exit("转写结果为空。确认文件里有人说话，或换一个后端重试。")

    sentences = split_into_sentences(segments)
    script = to_script(sentences)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = source.stem
    (out_dir / f"{stem}.txt").write_text(script + "\n", encoding="utf-8")
    (out_dir / f"{stem}.rhythm.txt").write_text(rhythm_report(sentences, script) + "\n", encoding="utf-8")
    (out_dir / f"{stem}.segments.json").write_text(
        json.dumps(sentences, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"\n口播稿：{out_dir / f'{stem}.txt'}")
    print(f"节奏分析：{out_dir / f'{stem}.rhythm.txt'}")
    print(f"时间戳：{out_dir / f'{stem}.segments.json'}\n")
    print(script)
    return 0


if __name__ == "__main__":
    sys.exit(main())
