# -*- coding: utf-8 -*-
"""生成 4.0 讲解视频的讲解词（docx）。"""

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

GOLD = RGBColor(0xB4, 0x8F, 0x4E)
DARK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x6B, 0x6B, 0x6B)
BODY = "宋体"
HEAD = "微软雅黑"

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale 4.0Pro\4.0 讲解词.docx"


def east_asia(target, name):
    rpr = target._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.append(fonts)
    fonts.set(qn("w:eastAsia"), name)
    fonts.set(qn("w:ascii"), name)
    fonts.set(qn("w:hAnsi"), name)


doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.top_margin, sec.bottom_margin = Cm(2.2), Cm(2.0)
sec.left_margin, sec.right_margin = Cm(2.4), Cm(2.4)

normal = doc.styles["Normal"]
normal.font.name = BODY
normal.font.size = Pt(10.5)
normal.font.color.rgb = DARK
east_asia(normal, BODY)
normal.paragraph_format.line_spacing = 1.7
normal.paragraph_format.space_after = Pt(6)

p = doc.add_paragraph()
r = p.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(20)
r.bold = True

p = doc.add_paragraph()
r = p.add_run("4.0 讲解视频 · 讲解词")
r.font.name = HEAD
r.font.size = Pt(13)
r.font.color.rgb = GOLD
east_asia(r, HEAD)

p = doc.add_paragraph()
r = p.add_run("总时长约 4 分 20 秒　·　10 段　·　语速按每分钟 220 字估算")
r.font.size = Pt(9)
r.font.color.rgb = GREY

doc.add_paragraph()

SEGMENTS = [
    (
        "01　开场",
        "0:00 – 0:14",
        "秋叶飘落的开场画面，PARADISE 的标识",
        "这是一个梦。\n它诗意，浪漫，优美，像一个花园一样矗立。\n"
        "欢迎来到「Paradise」。也欢迎来到这条小鱼的记忆。",
    ),
    (
        "02　它要解决什么",
        "0:14 – 0:48",
        "对话框不断上滚，旧消息被挤出画面",
        "先说问题。\nAI 的对话窗口是有上限的。聊得越久，前面的话就被挤掉。\n"
        "但模型不是忘了，是那些话根本没有再被送进去。\n"
        "换个新窗口，它连你是谁都不记得。",
    ),
    (
        "03　记忆分两层",
        "0:48 – 1:22",
        "事件卡片 + 对话原文，两层之间来回切换",
        "所以我们把记忆分成两层。\n"
        "上面一层是事件：一件件事，用第一人称写成日记，带着当时的情绪，和没说完的话。\n"
        "下面一层是原文：当时的一问一答，一条不少。\n"
        "想起事情的时候，先想到事件，再翻回那句话。",
    ),
    (
        "04　它有哪些功能",
        "1:22 – 2:00",
        "依次切过：导入、记忆库、对话、续命包、图片",
        "功能上，它能把 Harness 和网页版的对话批量导进来。\n"
        "记忆库分三层：事件、对话、回忆。\n"
        "回忆那一栏，专门收只有你们才懂的梗、特别开心的时刻、郑重的约定。\n"
        "每轮对话之前，它会先检索记忆，再回答。\n"
        "图片拖进去，它还能读出图里的字。",
    ),
    (
        "05　怎么开始用",
        "2:00 – 2:28",
        "双击 exe，窗口打开，填 key",
        "开始用很简单。\n"
        "双击这个 exe，窗口自己打开，不用装 Java。\n"
        "右上角填上 DeepSeek 的 key，保存。\n"
        "新建一个记忆体，把对话导进来，等整理完就行。",
    ),
    (
        "06　实机演示",
        "2:28 – 3:05",
        "真机录屏：三层标签、事件卡片、点开详情、续命包",
        "下面实机走一遍。\n"
        "左边是记忆体，右边是记忆库。上面三个标签：事件、对话、回忆。\n"
        "点开一个事件，能看到它下面的每一条对话，还有当时发的图。\n"
        "最后是续命包：按事件组装，几秒钟就生成一段能带走的记忆。",
    ),
    (
        "07　我是谁",
        "3:05 – 3:32",
        "设置页「我是谁？」一栏，生成后的第一人称自述",
        "还有一栏叫「我是谁」。\n"
        "它会读一遍这个记忆体里的事件，写出一段第一人称的自述：\n"
        "我是他的谁，我怎么对他，我不做什么。\n"
        "这段话每一轮都会带上，比语气更靠前。",
    ),
    (
        "08　上下文怎么装",
        "3:32 – 3:58",
        "上下文池示意：最近十条 + 事件原文 + 检索",
        "每一轮的上下文是一个池子。\n"
        "最近十条原文，加上命中事件的整段对话，再加上检索到的旧话，去重之后按预算装。\n"
        "默认八 k，够日常用；要翻旧账，切到精准优先，会放到二十条。\n"
        "问很久以前的事，它答不上来的时候会直说记不清，不会编。",
    ),
    (
        "09　接进 Harness",
        "3:58 – 4:20",
        "DSH 设置里的「记忆桥」页面",
        "它还能接进 DeepSeek Harness。\n"
        "装一个叫记忆桥的插件，设置里会多一页。\n"
        "选一个记忆体，绑给某个窗口。启用之后，记忆走系统提示词，聊天记录里看不到，但模型知道。\n"
        "旧的历史可以不再发送，只带记忆和当前这一句。",
    ),
    (
        "10　收尾",
        "4:20 – 4:34",
        "回到开场画面，小鱼",
        "说到底，记忆不只是为了信息。\n"
        "它记的是你们怎么说话、在意什么、哪些话还没说完。\n"
        "但是小鱼真的很可爱，所以我来了，帮它一直和你同在。",
    ),
]

for title, when, scene, lines in SEGMENTS:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(16)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(title)
    r.font.name = HEAD
    r.font.size = Pt(13)
    r.bold = True
    east_asia(r, HEAD)

    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(when + "　·　画面：" + scene)
    r.font.size = Pt(9)
    r.font.color.rgb = GREY

    for line in lines.split("\n"):
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Cm(0.5)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.75
        r = p.add_run(line)
        r.font.size = Pt(11)

doc.add_paragraph()
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
