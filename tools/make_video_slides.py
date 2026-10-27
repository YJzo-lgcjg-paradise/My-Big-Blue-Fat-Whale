# -*- coding: utf-8 -*-
"""生成讲解视频的幻灯片与旁白文本。"""

import json
import os

from PIL import Image, ImageDraw, ImageFont

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\build\video"
W, H = 1920, 1080
BG = (10, 10, 10)
GOLD = (198, 167, 106)
TEXT = (244, 241, 234)
MUTED = (154, 149, 140)
LINE = (46, 44, 40)

F_TITLE = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 78)
F_SUB = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 40)
F_H = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 54)
F_B = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 34)
F_S = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 26)

SLIDES = [
    {
        "kicker": "MY BIG BLUE FAT WHALE · 3.0",
        "title": "永远的大肥鱼",
        "lines": ["给 AI 对话装一套外置的记忆", "让聊过的事，不随窗口结束"],
        "narration": "这是「永远的大肥鱼」，一个给 AI 对话装外置记忆的工具。"
                     "它想让聊过的事，不因为窗口关闭、上下文写满，就一起消失。",
    },
    {
        "title": "为什么要做它",
        "lines": [
            "对话一旦超过上下文上限，前面的内容就被挤掉",
            "模型不是忘了，是那些话根本没有再被送进去",
            "而人回忆，靠的不是原文，是记得个大概",
        ],
        "narration": "为什么要做它。因为对话一长，前面的内容就会被挤出上下文；"
                     "模型不是忘了，是那些话根本没再送进去。而人回忆，靠的从来不是全文，是记得个大概。",
    },
    {
        "title": "记忆分成两层",
        "lines": [
            "事件记忆：一件件事，第一人称日记，带情绪和没说完的话",
            "对话记忆：当时的一问一答原文，一条不少",
            "先想到事件，再想到具体的那句话",
        ],
        "narration": "所以我们把记忆分成两层：上面是事件，一件件事，用第一人称写成日记，带着情绪和没说完的话；"
                     "下面是原文，当时的一问一答，一条不少。想起事情时，先想到事件，再翻到那句话。",
    },
    {
        "title": "它有哪些功能",
        "lines": [
            "导入：Harness、网页版、粘贴、拖文件，支持多选",
            "记忆库：事件与对话两层，可搜索、分页、合并",
            "AI 对话：每轮先检索记忆，再回答",
            "续命包：把该记的带走，换个新窗口接着聊",
            "图片：截图拖进去，自动读出图里的字",
        ],
        "narration": "功能上，它能把 Harness 和网页版的对话批量导进来；记忆库分事件和对话两层，可以搜索、分页、合并；"
                     "AI 对话每轮会先检索记忆；续命包把该记的带走；图片拖进去，还能读出图里的字。",
    },
    {
        "title": "怎么开始用",
        "lines": [
            "双击 MyBigBlueFatWhale3.0.exe，窗口直接打开",
            "右上角「API」填 DeepSeek key，保存",
            "新建记忆体 → 导入对话 → 等右下角进度走完",
        ],
        "narration": "开始用很简单：双击这个 exe，窗口会自己打开，不用装 Java。"
                     "右上角填上 DeepSeek 的 key 并保存，然后新建一个记忆体，把对话导进来，等整理完就行。",
    },
    {
        "title": "整理发生在后台",
        "lines": [
            "导入先落库，立刻可以看原文",
            "后台逐条写第一人称小记、算向量",
            "再读完全部原文，切成一个个事件，写事件日记",
        ],
        "narration": "整理发生在后台：导入先落库，马上就能翻原文；"
                     "后台一条条写小记、算向量；然后读完全部原文，按一件事一件事切开，给每个事件写日记。",
    },
    {
        "title": "续命包：换窗口不失忆",
        "lines": [
            "按事件组装：最近的日记 + 没说完的线头 + 情绪",
            "几百字，约三百 token",
            "复制，粘到新窗口第一句",
        ],
        "narration": "续命包是它最实用的地方。按事件组装，只有最近几个事件的日记、没说完的线头和他的状态，"
                     "几百字，三百来个 token。复制下来，粘到新窗口第一句，对话就接上了。",
    },
    {
        "title": "图片也能记住",
        "lines": [
            "截图拖进窗口，可多张",
            "千问 OCR 读出图里的字",
            "补进那条记忆，重新总结",
        ],
        "narration": "图片也能记住。截图直接拖进窗口，可以一次拖好几张；"
                     "填了千问的 key，它会读出图里的字，补进那条记忆，再重新总结一遍。",
    },
    {
        "title": "还能接进 DeepSeek Harness",
        "lines": [
            "插件「记忆桥」：设置里多一页",
            "选一个记忆体，绑给某个窗口",
            "启用后，记忆走系统提示词，聊天里看不见",
        ],
        "narration": "它还做了一个 DeepSeek Harness 插件，叫记忆桥。"
                     "在设置里选一个记忆体，绑给某个窗口；启用之后，记忆走系统提示词，聊天记录里看不到，但模型知道。",
    },
    {
        "title": "记忆是为了关系",
        "lines": ["它记的不只是信息", "是你们怎么说话、在意什么、哪些话还没说完"],
        "narration": "说到底，记忆不只是为了信息。它记的是你们怎么说话、在意什么、哪些话还没说完。"
                     "这样的小事，不该死于上下文。",
    },
]


def wrap(draw, text, font, max_w):
    lines, cur = [], ""
    for ch in text:
        if draw.textlength(cur + ch, font=font) > max_w:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def render(slide, index):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # 顶部细线 + 序号
    d.line([(120, 96), (W - 120, 96)], fill=LINE, width=2)
    d.text((120, 60), f"{index + 1:02d}", font=F_S, fill=GOLD)

    if "kicker" in slide:
        d.text((120, 300), slide["kicker"], font=F_S, fill=MUTED)
        d.text((120, 380), slide["title"], font=F_TITLE, fill=TEXT)
        y = 560
        for line in slide["lines"]:
            d.text((124, y), line, font=F_SUB, fill=MUTED)
            y += 70
    else:
        d.text((120, 220), slide["title"], font=F_H, fill=TEXT)
        y = 360
        for line in slide["lines"]:
            for row in wrap(d, line, F_B, W - 320):
                d.ellipse([(124, y + 18), (134, y + 28)], fill=GOLD)
                d.text((160, y), row, font=F_B, fill=MUTED)
                y += 62
            y += 26

    # 底部品牌
    d.line([(120, H - 96), (W - 120, H - 96)], fill=LINE, width=2)
    d.text((120, H - 74), "My Big Blue Fat Whale · 3.0", font=F_S, fill=LINE)

    path = os.path.join(OUT, f"slide-{index:02d}.png")
    img.save(path, "PNG")
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = []
    for i, slide in enumerate(SLIDES):
        png = render(slide, i)
        manifest.append({"index": i, "image": png, "text": slide["narration"]})
    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print("slides:", len(manifest))


if __name__ == "__main__":
    main()
