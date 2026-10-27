# -*- coding: utf-8 -*-
"""生成《My Big Blue Fat Whale · 用户使用手册》DOCX。"""

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

GOLD = RGBColor(0xB4, 0x8F, 0x4E)
DARK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x6B, 0x6B, 0x6B)
BODY_FONT = "宋体"
HEAD_FONT = "微软雅黑"
MONO_FONT = "Consolas"

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale\使用手册.docx"


def set_east_asia(style_or_run, name):
    rpr = style_or_run._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.append(fonts)
    fonts.set(qn("w:eastAsia"), name)
    fonts.set(qn("w:ascii"), name)
    fonts.set(qn("w:hAnsi"), name)


def shade(cell, hex_color):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hex_color)
    tc_pr.append(shd)


def add_page_number(paragraph):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_end)
    run.font.size = Pt(9)
    run.font.color.rgb = GREY


doc = Document()

# ---------- 页面与默认样式 ----------
sec = doc.sections[0]
sec.page_width = Cm(21)
sec.page_height = Cm(29.7)
sec.top_margin = Cm(2.4)
sec.bottom_margin = Cm(2.2)
sec.left_margin = Cm(2.6)
sec.right_margin = Cm(2.6)

normal = doc.styles["Normal"]
normal.font.name = BODY_FONT
normal.font.size = Pt(10.5)
normal.font.color.rgb = DARK
set_east_asia(normal, BODY_FONT)
normal.paragraph_format.line_spacing = 1.55
normal.paragraph_format.space_after = Pt(6)

for name, size in (("Heading 1", 17), ("Heading 2", 13)):
    st = doc.styles[name]
    st.font.name = HEAD_FONT
    st.font.size = Pt(size)
    st.font.color.rgb = DARK
    st.font.bold = True
    set_east_asia(st, HEAD_FONT)
    st.paragraph_format.space_before = Pt(18 if name == "Heading 1" else 12)
    st.paragraph_format.space_after = Pt(8)

# ---------- 封面 ----------
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(120)

logo = doc.add_paragraph()
logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
logo.add_run().add_picture(
    r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\build\logo.png", width=Cm(4.2)
)

title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_before = Pt(24)
r = title.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(28)
r.font.color.rgb = DARK
r.bold = True

sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = sub.add_run("用 户 使 用 手 册")
r.font.name = HEAD_FONT
r.font.size = Pt(15)
r.font.color.rgb = GOLD
set_east_asia(r, HEAD_FONT)

line = doc.add_paragraph()
line.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = line.add_run("— — —")
r.font.color.rgb = GOLD
r.font.size = Pt(11)

tag = doc.add_paragraph()
tag.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = tag.add_run("外置记忆 · 便携版 v0.1.0")
r.font.size = Pt(10.5)
r.font.color.rgb = GREY

doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)

# ---------- 正文辅助 ----------
def h1(text):
    doc.add_heading(text, level=1)


def h2(text):
    doc.add_heading(text, level=2)


def para(text, color=None, size=None, bold=False, space_after=6):
    p = doc.add_paragraph()
    run = p.add_run(text)
    if color:
        run.font.color.rgb = color
    if size:
        run.font.size = Pt(size)
    run.bold = bold
    p.paragraph_format.space_after = Pt(space_after)
    return p


def bullet(text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.45
    p.add_run(text)
    return p


def numbered(text):
    p = doc.add_paragraph(style="List Number")
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.45
    p.add_run(text)
    return p


def note(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    run = p.add_run("※ " + text)
    run.font.size = Pt(9.5)
    run.font.color.rgb = GREY
    set_east_asia(run, BODY_FONT)
    return p


def code(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    r = p.add_run(text)
    r.font.name = MONO_FONT
    r.font.size = Pt(9)
    r.font.color.rgb = RGBColor(0x2B, 0x2B, 0x2B)
    return p


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, text in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        r = p.add_run(text)
        r.bold = True
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(hdr[i], "8A7040")
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            r = p.add_run(text)
            r.font.size = Pt(9.5)
            p.paragraph_format.space_after = Pt(2)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


# ---------- 正文 ----------
h1("1. 这是什么")
para(
    "一句话：给你的 AI 对话装一个外置记忆。它把长对话切成一问一答，逐条总结、存成向量；"
    "下次提问时先检索相关记忆，再把原文取出来交给模型回答。"
)
para("它解决三件事：")
bullet("续命 —— 对话快撞上上下文上限时，把该记的留下来，换个新窗口接着聊。")
bullet("搬家 —— 把多个聊天框的内容整合进同一个记忆体，不再各聊各的。")
bullet("回想 —— 用一句话把很久以前聊过的事找回来，甚至能翻出原话。")

h2("它大致是怎么运转的")
table(
    ["环节", "做法"],
    [
        ["切分", "一轮问答 = 一条记忆，问题与回答都保留原文"],
        ["总结", "每条记忆生成一段摘要，摘要是「检索用的索引」，不是给模型看的答案"],
        ["检索", "提问先被改写成检索语句，按向量 + 词面 + 时间 + 强度打分，取前 6 条"],
        ["回答", "把命中的原文（问 300 字 / 答 900 字）交给模型，让它基于原文作答"],
        ["回收", "这一轮的问答，按同样规则写回当前记忆体"],
    ],
    widths=[3.0, 11.6],
)

h1("2. 三分钟上手")
numbered("双击文件夹里的 MyBigBlueFatWhale.exe。首次启动约 5–10 秒。")
numbered("会弹出一个独立窗口（不是浏览器），里面就是界面。窗口左上角和任务栏都是那只鲸鱼的图标。")
numbered("右上角点「API」，填入你的 DeepSeek key，点「测试连接」。")
numbered("左侧「导入」，把一段对话搬进来，等后台把记忆整理完。")
numbered("回「记忆库」翻看，或直接在「AI 对话」里开聊。")
note("程序是绿色的：不写注册表、不装服务，关掉窗口就退出。整个文件夹可以拷到 U 盘。")

h1("3. 配置模型 API")
para(
    "所有需要大模型的地方（对话、总结、提问改写、说话方式）都用你填的这个 key。"
    "key 只存在本机的 data\\llm.json，不会上传到任何地方。"
)
h2("拿到 key")
para("打开 platform.deepseek.com → API keys → 创建，复制以 sk- 开头的那串。")
h2("填进去")
para("点右上角的「API」，粘贴 key → 保存 → 测试连接。看到「连接正常」就成了。")
note("按钮上的小圆点：金色=已配置，灰色=没有 key（此时总结会退化为截断式兜底，聊天会提示先配 key）。")
para("想清除：同一面板点「清除」，会退回环境变量里的配置（便携版没有，等于清空）。")

h1("4. 把对话搬进来")
para("四个来源，按省事程度排：")

h2("4.1 DeepSeek Harness（最省事）")
para("本机装过 Harness 的话，这里会直接列出会话，选一个点导入即可，不用复制任何东西。")

h2("4.2 DeepSeek 网页版（用 userToken）")
para(
    "网页版接口认的不是 Cookie，而是 localStorage 里的 userToken。"
    "在浏览器里登录 chat.deepseek.com，按 F12："
)
code("Application → Local Storage → https://chat.deepseek.com → 复制 userToken 的值")
para("粘到「DeepSeek 凭证」框里，点「连接」，下面会列出会话，选一个导入。")
note(
    "更省事的做法：在 Network 面板右键那条 fetch_page 请求 → Copy → Copy as cURL，"
    "整段粘进来。bash 和 cmd 两种格式都能识别。"
)
para("常见报错：")
table(
    ["提示", "含义与处理"],
    [
        ["40003 INVALID_TOKEN", "key 填成了 Cookie。要的是 userToken，或整段 cURL。"],
        ["连上了，但没有会话", "token 被截断或已过期，重新复制一次；也可能是账号里确实没有会话。"],
        ["接口有变动", "网页版改版了，把报错发我，按新结构修解析。"],
    ],
    widths=[4.6, 10.0],
)

h2("4.3 已有记忆体（.json）")
para(
    "把记忆体导出成 .json 之后，可以直接拖到窗口任意位置——整屏都是投放区，"
    "松手就新建一个记忆体。也可以点「选择 .json 文件」。"
)
para("如果只是想把另一个记忆体并进来，不用导出：同一张卡片下面列出已有记忆体，点「复制过来」。")

h2("4.4 手动粘贴")
para("「DeepSeek 网页版」卡片里有个折叠的「手动粘贴正文」，支持这些格式：")
code("用户：… / 助手：…\nUser: … / Assistant: …\n问：… / 答：…")

h1("5. 记忆库")
para("默认按时间倒序，一次 30 条，底部「再加载 30 条」继续。")
bullet("搜索框问一句 → 按相关度回忆（勾「深度回忆」会全库扫，数量上限放宽）。")
bullet("点开任意一条 → 看原文、改摘要、调重要度。")
bullet("卡片右下角的小柱子 = 记忆强度，越久没被想起越弱。")

h2("为什么会“忘”")
para(
    "打分公式：相似度 0.68 + 新近度 0.16 + 强度 0.09 + 重要度 0.07，半衰期 30 天。"
    "所以旧记忆会慢慢排到后面——这是故意的。但原文一直在库里，勾「深度回忆」就能捞回来。"
)

h1("6. AI 对话")
para("左侧栏最上面「AI 对话」，点进去右侧整页都是对话区，右上角可切记忆体。")
h2("每一轮发生什么")
numbered("你提问。")
numbered("LLM 把问题改写成检索语句（补全指代、同义词、专有名词）。")
numbered("按向量 + 词面匹配，取前 6 条记忆的原文。")
numbered("模型基于原文作答，回答下方会标出：带了几条记忆、检索词是什么。")
numbered("这一轮问答写回当前记忆体，后台总结成新记忆。")
h2("上下文")
para(
    "每个记忆体各有一条独立对话线，切页面、关浏览器都不会丢（存在浏览器本地）。"
    "发给模型的最近 20 条消息会一起带上，所以「继续」「刚才那个」能接得住。"
    "标题栏「清空」只清聊天记录，不动记忆库。"
)

h1("7. 说话方式")
para("设置页 →「说话方式」。它会翻这个记忆体里的双方对话，总结出：")
bullet("语气：冷暖、克制还是外放、有没有玩笑")
bullet("称呼：它怎么叫你、你怎么叫它")
bullet("态度：朋友、同行、服务者、师徒……以及亲疏")
bullet("风格：句长、节奏、结构、开场收尾、口癖、标点")
bullet("要避开的腔调：这段对话里明显没有的（客服腔、AI 腔、过度抒情）")
para("生成后，每次对话都会带上这份档案，回答贴着这个语气来。")
h2("样例对话")
para("面板下方可以勾选最近 30 条记忆中的最多 8 条，作为「说话方式参考」拼进提示词——比抽象描述更能锁住语感。")
h2("重写历史总结")
para(
    "点「按这个语气重写全部总结」，整库会重置为待整理，后台逐条按新语气重写。"
    "注意：摘要现在只用于匹配，太风格化可能降低命中率，效果变差就停用它。"
)

h1("8. 续命包 · 搬家 · 整合")
para("这三个是同一条流水线的三种用法：从记忆体里挑出相关片段，拼成一段可以直接粘进新对话的文本。")
table(
    ["场景", "怎么做"],
    [
        ["续命", "写上「接下来要聊什么」，生成后复制，粘到新窗口的第一句。"],
        ["搬家", "留空，整段搬过去，新窗口从零开始但记得过去。"],
        ["整合", "多个记忆体先「复制过来」并成一个，再生成一次续命包。"],
    ],
    widths=[3.4, 11.2],
)

h1("9. 设置与维护")
table(
    ["功能", "说明"],
    [
        ["重建本记忆体向量", "换过模型后跑一次，检索才会对齐。"],
        ["刷新状态", "重新拉一次统计。"],
        ["删除当前记忆体", "连记忆一起删，不可恢复。"],
        ["说话方式", "生成 / 重新总结 / 挑样例 / 重写总结。"],
    ],
    widths=[4.4, 10.2],
)

h1("10. 数据在哪、怎么备份")
para("全部在程序文件夹的 data 目录里：")
table(
    ["文件", "内容"],
    [
        ["server\\data\\memory.mv.db", "所有记忆（H2 数据库，就一个文件）"],
        ["server\\data\\llm.json", "你的 API key"],
    ],
    widths=[4.6, 10.0],
)
para("备份 = 复制 server\\data 文件夹。换电脑 = 把整个文件夹拷过去（或只拷 server\\data 覆盖）。")
note("删除 server\\data 文件夹 = 回到全新状态，不可恢复，删前先备份。")

h1("11. 常见问题")
h2("双击没反应 / 一闪而过")
para("先看有没有杀毒软件拦截；再确认文件夹里 app 和 runtime 两个目录都在（缺一个都起不来）。")
h2("窗口一直空白")
para("等 5–10 秒；仍空白说明 WebView2 运行时缺失，装一下 Microsoft Edge WebView2 Runtime 即可。")
h2("端口被占用")
para("桌面版会自己从 8790 往后找一个空闲端口，不需要你操心；地址栏里的端口就是它实际用的。开发版固定 8787，两者可以同时开。")
h2("总结一直是空的")
para("看「整理中」的数量。后台每 2 秒处理一条，导入 100 条要三分钟左右。")
h2("回答还是没想起某件事")
para("先确认那条记忆确实在这个记忆体里（搜索框搜关键词）。如果确实有却没命中，把问题发我——通常是检索改写或切分的问题。")
h2("界面还是老样子")
para("浏览器缓存。按 Ctrl + F5 硬刷新。")

h1("12. 隐私与安全")
bullet("只监听 127.0.0.1，局域网和公网都访问不到。")
bullet("对话和记忆只存在你本机的 data 目录，不上传任何服务器。")
bullet("发出去的内容只有：你的提问、检索到的原文、说话方式档案——都直接发给 DeepSeek 官方接口。")
bullet("API key 明文存在 data\\llm.json。公用电脑上建议用完点「清除」。")
bullet("网页版 userToken 只存在后端内存里，重启即失效，不落盘、不写日志。")

doc.add_paragraph()
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

# ---------- 页脚页码 ----------
footer = sec.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = footer.add_run("My Big Blue Fat Whale · 使用手册　")
run.font.size = Pt(9)
run.font.color.rgb = GREY
add_page_number(footer)

doc.save(OUT)
print("saved", OUT)
