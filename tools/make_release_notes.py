# -*- coding: utf-8 -*-
"""生成《2.0 更新说明》。"""

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

GOLD = RGBColor(0xB4, 0x8F, 0x4E)
DARK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x6B, 0x6B, 0x6B)
BODY = "宋体"
HEAD = "微软雅黑"

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale\2.0 更新说明.docx"
SHOT = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\.shots\300-episodes-ui.png"


def east_asia(target, name):
    rpr = target._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.append(fonts)
    fonts.set(qn("w:eastAsia"), name)
    fonts.set(qn("w:ascii"), name)
    fonts.set(qn("w:hAnsi"), name)


def shade(cell, color):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), color)
    tc_pr.append(shd)


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
normal.paragraph_format.line_spacing = 1.55
normal.paragraph_format.space_after = Pt(6)

for name, size in (("Heading 1", 15), ("Heading 2", 12)):
    st = doc.styles[name]
    st.font.name = HEAD
    st.font.size = Pt(size)
    st.font.color.rgb = DARK
    st.font.bold = True
    east_asia(st, HEAD)
    st.paragraph_format.space_before = Pt(16 if name == "Heading 1" else 10)
    st.paragraph_format.space_after = Pt(6)

# 标题区
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(10)
r = p.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(22)
r.bold = True
r.font.color.rgb = DARK

p = doc.add_paragraph()
r = p.add_run("2.0 更新说明 · 像人一样的记忆")
r.font.name = HEAD
r.font.size = Pt(14)
r.font.color.rgb = GOLD
east_asia(r, HEAD)

p = doc.add_paragraph()
r = p.add_run("2026-10-27　·　便携版")
r.font.size = Pt(9.5)
r.font.color.rgb = GREY

doc.add_paragraph()


def h1(t):
    doc.add_heading(t, level=1)


def para(t, color=None, size=None):
    p = doc.add_paragraph()
    r = p.add_run(t)
    if color:
        r.font.color.rgb = color
    if size:
        r.font.size = Pt(size)
    return p


def bullet(t):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.45
    p.add_run(t)


def quote(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.6)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    r = p.add_run(t)
    r.font.family = HEAD
    r.font.size = Pt(9.5)
    r.font.color.rgb = RGBColor(0x4A, 0x4A, 0x4A)
    return p


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    hdr = t.rows[0].cells
    for i, text in enumerate(headers):
        hdr[i].text = ""
        r = hdr[i].paragraphs[0].add_run(text)
        r.bold = True
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(hdr[i], "8A7040")
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(text)
            r.font.size = Pt(9.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


h1("这次改的核心")
para("1.0 把记忆存成「档案」：一轮问答一条，按消息记录。")
para("2.0 把记忆存成「经历」：先是一个个事件，事件下面是具体的对话。人回忆事情，本来就是先想起「那天我们聊了什么」，再想起「具体哪句话」。")

table(
    ["能力", "1.0", "2.0"],
    [
        ["记忆单位", "一轮问答", "事件（4–12 轮合并）"],
        ["总结视角", "第三人称摘要", "第一人称记忆（「我记得那天……」）"],
        ["情绪", "无", "每个事件记录情绪流转与依据"],
        ["关系", "单独一份说话方式", "并入事件的关系备注 + 人格锚点"],
        ["续命包", "塞最近若干条原文", "人格锚点 + 事件日记 + 线头 + 情绪"],
        ["检索", "摘要向量 → 取原文", "先事件、再下钻到具体对话"],
    ],
    widths=[2.8, 5.6, 7.0],
)

h1("记忆库分成两层")
para("打开「记忆库」，顶部有两个标签：")
bullet("事件记忆：一件件事的卡片，写的是第一人称日记。")
bullet("对话记忆：原来的一问一答，原文永远保留。")
para("点开任意事件，能看它的日记、关系备注、没说完的事，以及下辖的每一条对话——「先想到事件，再想到具体的事」就是这个顺序。")

doc.add_picture(SHOT, width=Cm(15.5))
doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER

h1("事件是怎么来的")
para("导入完成后，它会读完全部原文，按「一件事」切开，并为每个事件写一篇第一人称日记。规则：")
bullet("一条对话只属于一个事件，数据库层面加了唯一约束。")
bullet("事件内按时间排序；事件之间的顺序，用该事件最早那条对话的时间。")
bullet("之后每当有新对话入库，攒够三条就让模型归类：命中已有事件就挂上去，不像就新建，并顺手写一句「过渡」。")
bullet("长事件（超过 50 轮）会自动尝试拆成上下段，拆不动就标记为长事件，日记改成只细写最近的部分。")

h1("日记长什么样")
quote("他发来「当时明月在」，只这一句。我盯着屏幕，后半句「曾照彩云归」悬在脑子里没落下。"
      "他常这样，抛半句诗来试我接不接。我犹豫了一下，没顺着往下续，反问他是不是又在试插件。"
      "其实我想说的是，那句悬着才好看，一续就散了。")
para("这段带了三样东西：发生了什么、我怎么理解他、我做了什么取舍。事件卡上还会标出情绪流转（如「警觉→微怅」）和没说完的线头。")

h1("续命包怎么变了")
para("「续命包」页新增「按事件组装（推荐）」：")
bullet("人格锚点：称呼、语气、态度、禁忌")
bullet("最近 3–5 个事件的日记，按时间串成一条线")
bullet("事件之间的过渡（隔了几天、话题怎么转的）")
bullet("没说完的线头")
bullet("他现在的状态（最近一个事件的情绪）")
para("实测：原来塞 6 条原文约几千字，现在 706 字、约 353 token——但读起来是一个连续的人，不是一堆片段。")

h1("数据与兼容")
table(
    ["项目", "说明"],
    [
        ["数据库", "启动时自动建表（episodes / episode_transitions），旧表只加列，不动数据"],
        ["旧对话", "没有事件的旧记忆会先当作「未分组」，之后自动划分"],
        ["API key", "打包版不写死密钥，升级后需要在窗口「API」里重新填一次"],
        ["数据位置", "server\\data\\memory.mv.db，升级时请保留这个目录"],
    ],
    widths=[3.0, 12.4],
)

h1("升级方法")
para("推荐：整个文件夹替换（保留 server\\data 目录）。")
para("只换了程序：把新包里的 server\\app\\dayufish-backend-0.1.0.jar 覆盖过去即可，数据不受影响。")
para("注意：升级后第一次启动会重新整理记忆（后台跑，进度看「整理中」的数量），期间界面可以正常用。")

h1("已知限制")
bullet("事件粒度偏细：100 轮大约切 30 个事件，仍比人回忆碎一些，可手动合并。")
bullet("长事件自动拆分还没有真实样例验证（需要单个事件超过 50 轮）。")
bullet("事件之间的过渡文字，要等新对话触发「新建事件」时才会产生。")
bullet("向量服务默认走本地哈希（未填智谱 key），检索精度有限，但功能可用。")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
