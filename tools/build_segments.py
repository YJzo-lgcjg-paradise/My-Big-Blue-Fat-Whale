# -*- coding: utf-8 -*-
"""把分段录屏 + 配音 + 字幕合成为 04–09 六段视频，再拼接。"""
import json
import os
import subprocess
import sys

BASE = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\build\video4"
FFMPEG = r"C:\Users\user\AppData\Local\Microsoft\WinGet\Links\ffmpeg.exe"
FONT = "C:/Windows/Fonts/msyh.ttc"

CHAPTERS = [
    ("04", "它有哪些功能", [
        "功能上，它能把 Harness 和网页版的对话批量导进来。",
        "记忆库分三层：事件、对话、回忆。",
        "回忆那一栏，专门收只有你们才懂的梗、特别开心的时刻、郑重的约定。",
        "每轮对话之前，它会先检索记忆，再回答。",
        "图片拖进去，它还能读出图里的字。",
    ]),
    ("05", "怎么开始用", [
        "开始用很简单。",
        "双击这个 exe，窗口自己打开，不用装 Java。",
        "右上角填上 DeepSeek 的 key，保存。",
        "新建一个记忆体，把对话导进来，等整理完就行。",
    ]),
    ("06", "实机演示", [
        "下面实机走一遍。",
        "左边是记忆体，右边是记忆库。上面三个标签：事件、对话、回忆。",
        "点开一个事件，能看到它下面的每一条对话，还有当时发的图。",
        "最后是续命包：按事件组装，几秒钟就生成一段能带走的记忆。",
    ]),
    ("07", "我是谁", [
        "还有一栏叫「我是谁」。",
        "它会读一遍这个记忆体里的事件，写出一段第一人称的自述：",
        "我是他的谁，我怎么对他，我不做什么。",
        "这段话每一轮都会带上，比语气更靠前。",
    ]),
    ("08", "上下文怎么装", [
        "每一轮的上下文是一个池子。",
        "最近十条原文，加上命中事件的整段对话，再加上检索到的旧话，去重之后按预算装。",
        "默认八 k，够日常用；要翻旧账，切到精准优先，会放到二十条。",
        "问很久以前的事，它答不上来的时候会直说记不清，不会编。",
    ]),
    ("09", "接进 Harness", [
        "它还能接进 DeepSeek Harness。",
        "装一个叫记忆桥的插件，设置里会多一页。",
        "选一个记忆体，绑给某个窗口。启用之后，记忆走系统提示词，聊天记录里看不到，但模型知道。",
        "旧的历史可以不再发送，只带记忆和当前这一句。",
    ]),
]


def sh(cmd, cwd=None):
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=cwd)
    if r.returncode != 0:
        print("FFMPEG FAIL:", " ".join(cmd)[:200])
        print(r.stderr[-800:])
        sys.exit(1)


def srt_time(t):
    ms = int(round(t * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def build_chapter(cid, title, lines):
    d = os.path.join(BASE, cid)
    frames = json.load(open(os.path.join(d, "frames.json"), encoding="utf-8"))
    wav = os.path.join(d, "voice.wav")
    if not os.path.exists(wav):
        print("缺配音:", wav)
        sys.exit(1)

    dur = float(subprocess.run(
        [FFMPEG.replace("ffmpeg.exe", "ffprobe.exe"), "-v", "error", "-show_entries", "format=duration",
         "-of", "csv=p=0", wav], capture_output=True, text=True).stdout.strip())
    total = dur + 1.1

    # 帧 → 列表：把真实间隔按比例缩放，让画面对齐配音长度
    gaps = []
    for i, f in enumerate(frames):
        nxt = frames[i + 1]["ts"] if i + 1 < len(frames) else f["ts"] + 0.4
        gaps.append(max(0.05, nxt - f["ts"]))
    raw = sum(gaps)
    target = dur + 0.9
    scale = target / raw if raw > 0 else 1.0
    lst = []
    for i, f in enumerate(frames):
        step = max(0.04, gaps[i] * scale)
        lst.append(f"file '{f['file'].replace(chr(92), '/')}'")
        lst.append(f"duration {step:.3f}")
    lst.append(f"file '{frames[-1]['file'].replace(chr(92), '/')}'")
    listfile = os.path.join(d, "frames.txt")
    open(listfile, "w", encoding="utf-8", newline="\n").write("\n".join(lst) + "\n")

    silent = os.path.join(d, "silent.mp4")
    sh([FFMPEG, "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", "frames.txt",
        "-vf", "fps=30,scale=1280:720", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
        "-pix_fmt", "yuv420p", "silent.mp4"], cwd=d)

    # 字幕：按字数分配时长
    weights = [max(6, len(x)) for x in lines]
    total_w = sum(weights)
    srt, t = [], 0.35
    for idx, (line, w) in enumerate(zip(lines, weights), 1):
        span = (dur - 0.5) * w / total_w
        srt.append(f"{idx}\n{srt_time(t)} --> {srt_time(t + span - 0.08)}\n{line}\n")
        t += span
    srtfile = os.path.join(d, "sub.srt")
    open(srtfile, "w", encoding="utf-8", newline="\n").write("\n".join(srt))

    out = os.path.join(d, "clip.mp4")
    style = ("FontName=Microsoft YaHei,FontSize=12,PrimaryColour=&H00F4F1EA,"
             "BackColour=&H99000000,BorderStyle=3,Outline=0,Shadow=0,"
             "Alignment=2,MarginV=32,MarginL=60,MarginR=60")
    sh([FFMPEG, "-y", "-loglevel", "error",
        "-f", "concat", "-safe", "0", "-i", "frames.txt",
        "-i", "voice.wav",
        "-vf", f"fps=30,scale=1280:720,subtitles=sub.srt:force_style='{style}'",
        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-shortest", "-movflags", "+faststart", "clip.mp4"], cwd=d)
    print(cid, "ok  video≈%.1fs  audio=%.1fs" % (total, dur))
    return out


def main():
    clips = []
    for cid, title, lines in CHAPTERS:
        clips.append(build_chapter(cid, title, lines))
    listfile = os.path.join(BASE, "final.txt")
    open(listfile, "w", encoding="utf-8", newline="\n").write(
        "\n".join(f"file '{c.replace(chr(92), '/')}'" for c in clips) + "\n")
    out = r"C:\Users\user\Desktop\My Big Blue Fat Whale 4.0Pro\4.0 演示片段 04-09.mp4"
    sh([FFMPEG, "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", "final.txt",
        "-c", "copy", "-movflags", "+faststart", out], cwd=BASE)
    print("final:", out)


if __name__ == "__main__":
    main()
