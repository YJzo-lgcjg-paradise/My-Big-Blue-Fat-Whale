# -*- coding: utf-8 -*-
"""把 CDP 录到的帧拼成带时长的 ffmpeg concat 列表。"""
import json
import os

DIR = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\build\demo"
frames = json.load(open(os.path.join(DIR, "frames.json"), encoding="utf-8"))

lines = []
for i, f in enumerate(frames):
    nxt = frames[i + 1]["ts"] if i + 1 < len(frames) else f["ts"] + 0.5
    dur = max(0.05, min(3.0, nxt - f["ts"]))
    path = f["file"].replace("\\", "/")
    lines.append(f"file '{path}'")
    lines.append(f"duration {dur:.3f}")
if frames:
    lines.append(f"file '{frames[-1]['file'].replace(chr(92), '/')}'")

out = os.path.join(DIR, "frames-concat.txt")
with open(out, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(lines) + "\n")
print("concat list:", out, "frames:", len(frames))
