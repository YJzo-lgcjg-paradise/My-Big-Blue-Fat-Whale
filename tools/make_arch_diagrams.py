# -*- coding: utf-8 -*-
"""生成技术文档用的两张示意图。"""
import os

from PIL import Image, ImageDraw, ImageFont

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\docs\img"
os.makedirs(OUT, exist_ok=True)

W, H = 1760, 940
BG = (255, 255, 255)
INK = (34, 34, 34)
MUTED = (110, 110, 110)
GOLD = (176, 140, 74)
GOLD_BG = (252, 248, 240)
GREY_BG = (245, 245, 243)
LINE = (200, 196, 188)

F_T = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 34)
F_H = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 26)
F_B = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 21)
F_S = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 18)


def box(d, x, y, w, h, title, lines, fill=GREY_BG, border=LINE, title_color=INK):
    d.rounded_rectangle([x, y, x + w, y + h], radius=14, fill=fill, outline=border, width=2)
    d.text((x + 22, y + 16), title, font=F_H, fill=title_color)
    ty = y + 58
    for line in lines:
        d.text((x + 22, ty), line, font=F_B, fill=MUTED)
        ty += 32


def arrow(d, x1, y1, x2, y2, label="", color=GOLD):
    d.line([x1, y1, x2, y2], fill=color, width=3)
    # 箭头
    if abs(x2 - x1) >= abs(y2 - y1):
        s = 1 if x2 > x1 else -1
        d.polygon([(x2, y2), (x2 - 14 * s, y2 - 7), (x2 - 14 * s, y2 + 7)], fill=color)
    else:
        s = 1 if y2 > y1 else -1
        d.polygon([(x2, y2), (x2 - 7, y2 - 14 * s), (x2 + 7, y2 - 14 * s)], fill=color)
    if label:
        mx, my = (x1 + x2) // 2, (y1 + y2) // 2
        d.text((mx - 40, my - 30), label, font=F_S, fill=MUTED)


# ---------------- 图 1：系统架构 ----------------
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)
d.text((70, 46), "系统架构", font=F_T, fill=INK)
d.text((70, 96), "桌面壳承载界面，前端只调同源 API，后端把对话变成可检索的记忆", font=F_S, fill=MUTED)

box(d, 70, 160, 360, 190, "桌面外壳", [
    ".NET 8 WinForms + WebView2",
    "选空闲端口 → 拉起 javaw",
    "等 /api/health → 加载界面",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

box(d, 520, 160, 360, 190, "前端", [
    "React 18 + TypeScript",
    "Vite 构建，产物打进 jar",
    "没有路由库，用状态切页",
])

box(d, 970, 160, 380, 190, "后端 API", [
    "Spring Boot 3.3.5 / Java 21",
    "ApiController：60+ 端点",
    "只监听 127.0.0.1",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

box(d, 70, 470, 360, 200, "服务层", [
    "MemoryService：检索 / 上下文池",
    "EpisodeService：事件 / 日记",
    "StyleService：人格 / 语气",
    "MediaService / QwenOcrService",
])

box(d, 520, 470, 360, 200, "后台队列", [
    "Worker：每 2 秒一轮",
    "总结 → 向量 → 事件 → 日记",
    "OCR 队列、事件拆分",
])

box(d, 970, 470, 380, 200, "存储", [
    "H2 单文件 memory.mv.db",
    "data/media：图片按哈希落盘",
    "data/llm.json：密钥（明文）",
])

box(d, 1430, 160, 260, 190, "外部服务", [
    "DeepSeek：对话",
    "智谱：向量（可选）",
    "千问：OCR（可选）",
])

box(d, 70, 760, 1180, 130, "DeepSeek Harness 插件（记忆桥）", [
    "宿主：注册 /dsh-memory/api/*，把记忆写进 systemPrompt.context，pre-step 决定发送哪些消息",
    "客户端：往 Harness 设置里插一页，选记忆体、启用 / 停用",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

arrow(d, 430, 255, 515, 255)
arrow(d, 880, 255, 965, 255)
arrow(d, 1150, 350, 1150, 465)
arrow(d, 250, 350, 250, 465)
arrow(d, 430, 570, 515, 570)
arrow(d, 880, 570, 965, 570)
arrow(d, 1350, 255, 1425, 255, "HTTPS")
arrow(d, 1160, 670, 1160, 755, "", GOLD)

img.save(os.path.join(OUT, "arch-overview.png"))
print("arch-overview.png")

# ---------------- 图 2：一次对话的上下文装配 ----------------
W2, H2 = 1760, 880
img = Image.new("RGB", (W2, H2), BG)
d = ImageDraw.Draw(img)
d.text((70, 46), "一次对话的上下文装配", font=F_T, fill=INK)
d.text((70, 96), "固定层每轮都带；其余三类进池子，按分数和预算装填，最后按时间排序", font=F_S, fill=MUTED)

box(d, 70, 170, 330, 150, "你的问题", [
    "本地分析：关键词 / 过去指向",
    "必要时先改写一次查询",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

box(d, 470, 170, 330, 150, "最近上下文", [
    "预算模式 10 条，精准 20 条",
    "全部给原文，不截句",
])
box(d, 880, 170, 330, 150, "事件命中", [
    "向量 + 词面找事件",
    "命中后带该事件全部对话",
    "单事件上限 40% / 60%",
])
box(d, 1290, 170, 330, 150, "对话检索", [
    "问题去比一轮轮小记",
    "命中后带那一轮原文",
])

box(d, 470, 400, 1150, 140, "上下文池", [
    "候选 = 最近 + 事件 + 检索；按 turn id 去重",
    "按分数与预算装填；装不下就丢分数最低的",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

box(d, 470, 600, 560, 210, "固定层（每轮都带）", [
    "① 我是谁（第一人称自述）",
    "② 说话方式（风格档案）",
    "③ 语气样例（最多 8 条原话）",
    "④ 事件目录（标题清单）",
])

box(d, 1120, 610, 500, 190, "发给模型", [
    "system：固定层 + 池子 + 规则",
    "user：你这一句",
    "规则：没命中就说记不清",
], fill=GOLD_BG, border=GOLD, title_color=GOLD)

arrow(d, 400, 245, 465, 245)
arrow(d, 800, 245, 875, 245)
arrow(d, 1210, 245, 1285, 245)
arrow(d, 635, 325, 700, 395)
arrow(d, 1045, 325, 1045, 395)
arrow(d, 1455, 325, 1400, 395)
arrow(d, 1045, 545, 1045, 600)
arrow(d, 745, 600, 745, 545)
arrow(d, 1030, 700, 1115, 700)

img.save(os.path.join(OUT, "arch-context.png"))
print("arch-context.png")
