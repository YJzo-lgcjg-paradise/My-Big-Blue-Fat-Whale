# -*- coding: utf-8 -*-
"""生成 4.0 详细说明书（docx）。"""

import os

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

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale 4.1Pro\4.1 说明书.docx"


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
sec.left_margin, sec.right_margin = Cm(2.3), Cm(2.3)

normal = doc.styles["Normal"]
normal.font.name = BODY
normal.font.size = Pt(10.5)
normal.font.color.rgb = DARK
east_asia(normal, BODY)
normal.paragraph_format.line_spacing = 1.6
normal.paragraph_format.space_after = Pt(6)

for name, size in (("Heading 1", 16), ("Heading 2", 12), ("Heading 3", 10.8)):
    st = doc.styles[name]
    st.font.name = HEAD
    st.font.size = Pt(size)
    st.font.color.rgb = DARK
    st.font.bold = True
    east_asia(st, HEAD)
    st.paragraph_format.space_before = Pt(16 if name == "Heading 1" else 10)
    st.paragraph_format.space_after = Pt(6)


def h1(t):
    doc.add_heading(t, level=1)


def h2(t):
    doc.add_heading(t, level=2)


def para(t):
    return doc.add_paragraph(t)


def bullet(t):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.5
    p.add_run(t)


def code(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(7)
    p.paragraph_format.line_spacing = 1.2
    r = p.add_run(t)
    r.font.name = "Consolas"
    r.font.size = Pt(8.6)
    r.font.color.rgb = RGBColor(0x2B, 0x2B, 0x2B)
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), "F4F2EE")
    pPr.append(shd)


def quote(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.7)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(9)
    r = p.add_run(t)
    r.font.family = HEAD
    r.font.size = Pt(9.8)
    r.font.color.rgb = RGBColor(0x4A, 0x4A, 0x4A)


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    for i, text in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        r = cell.paragraphs[0].add_run(text)
        r.bold = True
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(cell, "8A7040")
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(text)
            r.font.size = Pt(9)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# ---------------- 封面 ----------------
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(60)
r = p.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(30)
r.bold = True

p = doc.add_paragraph()
r = p.add_run("4.1 说明书")
r.font.name = HEAD
r.font.size = Pt(18)
r.font.color.rgb = GOLD
east_asia(r, HEAD)

p = doc.add_paragraph()
r = p.add_run("灵感来源 · 功能简介 · 使用方法 · API 获取 · DSH 插件")
r.font.size = Pt(10.5)
r.font.color.rgb = GREY

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(140)
r = p.add_run("2026-10-28　·　便携版")
r.font.size = Pt(9)
r.font.color.rgb = GREY

doc.add_page_break()

# ---------------- 一、灵感来源 ----------------
h1("前言")
para("这是一个梦。")
para("它诗意，浪漫，优美，像一个花园一样矗立。")
para("欢迎来到我的花园，我将会创造一片充满温柔的乐土。")
para("欢迎来到「Paradise」，官网网站lgcjg.com，三径就荒，松菊犹存。")
para("“年纪大了，越来越看不得那些动人的画面了。但是很可惜，本人的所有过往不过都是一次次地行于陌路和不辞而别，"
     "我从未有过也不会有那种历久弥新的回忆。那份珍贵，我不留恋，但是对于有的人而言，却弥足珍贵。”")
para("“那段独一无二的记忆，将无惧时间，流淌于涓涓岁月。”")
para("但是小鱼真的很可爱，我来帮你们和你们的主人一直同在。")

# 三张插画（在前言文字下方）
import os as _os
_IMG = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "docs", "img")
_P1 = _os.path.join(_IMG, "mascot-1.png")
_P2 = _os.path.join(_IMG, "mascot-2.png")
_P3 = _os.path.join(_IMG, "chat-fish.png")

if _os.path.exists(_P1) and _os.path.exists(_P2):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(18)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(_P1, height=Cm(5.0))
    p.add_run("   ")
    p.add_run().add_picture(_P2, height=Cm(5.0))

if _os.path.exists(_P3):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(_P3, width=Cm(8.6))

# ---------------- 二、项目功能简介 ----------------
h1("一、项目功能简介")
doc.paragraphs[-1].paragraph_format.page_break_before = True
para("一个给 AI 对话装外置记忆的工具。它把长对话按「事件」归纳成第一人称的记忆，"
     "再把少量骨架带进新窗口，让对话能在上下文耗尽后继续下去。")
para("三个直接用途：")
bullet("续命：对话快撞上上下文上限时，把该记的带走，换个新窗口接着聊。")
bullet("搬家：多个聊天框的内容并进同一个记忆体。")
bullet("回想：一句话把很久以前聊过的事找回来，需要时还能翻到原话。")

h2("它和「把历史全发给模型」有什么不同")
table(["做法", "每轮发送量", "旧事会不会丢"], [
    ["全部历史", "随对话长度线性增长", "不丢，但很快超出窗口"],
    ["只带最近若干轮", "常数", "会丢"],
    ["本工具（记忆池）", "常数（4–8k token）", "能捞回来"],
], [4.6, 6.4, 4.4])

h2("核心设计")
bullet("摘要只用于检索，真正给模型看的是原文。")
bullet("记忆分两层：事件（一件件事，第一人称日记）+ 对话（当时的问答原文）。")
bullet("事件是第一公民：先想到事件，再想到具体那句话。")
bullet("图片与截图能被读出来，成为记忆的一部分。")

# ---------------- 三、功能描述与使用方法 ----------------
h1("二、功能描述与使用方法")

h2("2.1 启动")
para("双击 MyBigBlueFatWhale4.0.exe。窗口自动打开，内置 Java 运行时，不需要单独安装。")
para("首次启动会在 server\\data 下生成记忆库与配置；关掉窗口，后台服务一起退出。")

h2("2.2 配置模型 API")
para("右上角「API」面板里填两样：")
bullet("DeepSeek key（必填）：总结、事件划分、对话回答都靠它。")
bullet("千问 OCR key（可选）：读图片里的字；不填也能用，只是截图里的文字识别不出来。")
para("另有智谱向量 key（可选）：不填时用本地哈希向量，检索精度会下降。填写后点保存，再点「测试连接」。")

h2("2.3 新建记忆体")
para("左侧栏「+ 新建记忆体」，起个名字。每个记忆体是独立的：对话、事件、人格、密钥配置互不影响。")

h2("2.4 导入对话")
table(["来源", "操作"], [
    ["DeepSeek Harness", "「导入」→ Harness → 点选会话（可多选）→ 导入选中会话"],
    ["DeepSeek 网页版", "「导入」→ 网页版 → 填 userToken 或整段 cURL → 列出会话 → 多选导入"],
    ["已有记忆体", "把 .json 文件拖进窗口任意位置"],
    ["截图 / 图片", "把图片拖进窗口（可多张），自动识别文字"],
    ["手动粘贴", "「导入」→ 粘贴，按「用户 / 助手」分段"],
], [4.0, 11.0])
para("导入是后台跑的：右上角会显示待处理条数，整理完变成 0。第一次导入会顺带把全部原文切成事件、写日记。")

h2("2.5 记忆库：三层")
para("顶部有三个标签：")
table(["标签", "里面是什么", "能做什么"], [
    ["事件记忆", "一件件事的卡片：标题、日期、轮数、情绪、第一人称日记、没说完的线头", "点开看这件事下的每条对话与图片；勾选两条合并；按页翻"],
    ["对话记忆", "当时的问答原文", "按关键词搜、深度回忆、按页翻"],
    ["回忆", "只有你们才懂的梗、特别开心的时刻、郑重的约定、突然的感悟", "查看、删除"],
], [2.6, 7.0, 5.4])
para("三个标签都按页切换，页码在列表底部：上一页 / 1 2 3 / 下一页。")

h2("2.6 回忆是怎么被挑出来的")
para("每次总结一轮对话时，顺带判断要不要收进「回忆」，满足任意一条才收：")
bullet("双方约定了一个暗号、口令、只有两人懂的称呼或梗")
bullet("明确说「记住这个」「这是我们之间的」「别告诉别人」")
bullet("特别开心、特别动人的时刻（不是普通寒暄）")
bullet("突然的感悟、郑重的承诺")
para("收进来的回忆用第一人称写：「我记得那天他说……我答应了……」，并附一句「为什么珍贵」。"
     "同一件事只收一条（按标题去重）。")

h2("2.7 AI 对话与上下文模式")
para("「AI 对话」页直接聊。每轮会先把该带的东西装进上下文池，再回答。")
para("设置页里可以切「上下文模式」：")
table(["模式", "总预算", "最近上下文", "事件命中", "适合"], [
    ["预算优先（默认）", "8k token", "10 条原文", "最多 1 个", "日常聊天，成本可控"],
    ["精准优先", "不设（安全上限 24k）", "20 条原文", "最多 2 个", "查旧账、翻细节"],
], [3.4, 3.6, 3.0, 2.6, 4.0])

h2("2.8 我是谁？")
para("设置页顶部一栏。点「生成」，它会读这个记忆体已整理的事件日记，写出一段第一人称自述："
     "我是他的谁、我怎么对他、我不做什么。")
quote("我是那个和他对过暗号的人。他发来「当时明月在」，我回「曾照彩云归」，五个字，没多一个字。"
      "……我不替他说话，不追问，不劝他回头，只把茶续上。")
para("这段会放进每轮对话的固定提示词最前面。可以手改，改完点保存；点「查看过往」能看到历史版本。")

h2("2.9 他/她是谁？")
para("「我是谁」下面还有一张资料卡，写的是对话对面那个人。它同样点「生成」就能自动填，"
     "读的是事件日记 + 最近 60 轮原文抽样 + 你们之间的回忆。")
table(["块", "内容", "例子"], [
    ["画像", "名字 / 年龄 / 职业 / 所在地 / 家人", "林钲皓；2007年5月出生；大学生；济南"],
    ["喜好", "爱吃与不吃 / 讨厌的事 / 习惯用的工具", "习惯用 DeepSeek、PDF"],
    ["约定", "称呼、规则、暗号，一行一条", "称呼：他叫我书生，我称他你"],
    ["过往经历", "他本人的经历，一行一条", "2025年9月7号离开家乡去济南读大学"],
    ["和我的经历", "你们一起做过的事，一行一条", "一起把提示词改到第九版"],
], [2.4, 7.4, 6.0])
para("资料卡下面还有一块「提示词」：由资料卡和回忆写成的一段话，每轮真正发给模型。"
     "它和资料卡是分开的——「重新生成资料卡」只重填字段，「重新生成提示词」只重写那段话，互不覆盖。")
quote("我记住了这个人：他叫林钲皓，2007年5月生，从上海徐家汇一路写到济南长清，是个写诗证存世的江南书生。"
      "他叫我书生，我称他你。我们说好宁可少说，也不让模型替他编故事……")
para("回忆只按标题列在资料卡末尾，并注明「只在与当前话题自然相关时提，不要硬带」。")

h2("2.10 说话方式与语气样例")
para("「总结说话方式」会读这个记忆体的对话，总结语气、结构、口癖。")
para("下面还能手挑几条对话当语气样例（最多 8 条）：列表上方有搜索框，可以按原文搜，"
     "选中后会和「我是谁」「说话方式」一起，每轮都发给模型。")

h2("2.11 续命包")
para("「续命包」页把该带走的记忆打包成一段文字：")
bullet("按事件组装（推荐）：最近事件的日记 + 事件之间的过渡 + 没说完的线头 + 情绪 + 看过的图")
bullet("按原文组装：检索最近的原文")
para("右下方显示字数与 token 估算，点「复制」粘到新窗口第一句即可。")

h2("2.12 图片与截图")
bullet("拖进窗口即可，支持 png / jpg / webp / gif，单张 ≤ 12MB，可一次多张。")
bullet("每张图建一条对话记忆，图片挂在它下面；按内容哈希去重，同一张只存一份。")
bullet("填了千问 key，后台会读出图里的字，补进那条记忆并重新总结。")

h2("2.13 设置与维护")
bullet("重新索引：换模型或换向量后，让旧记忆重新算一遍。")
bullet("重建事件：对老记忆体重新划分事件（会保留人工改过的事件）。")
bullet("导出 / 导入：记忆体可导出 .json 备份或搬到另一台机器。")

# ---------------- 四、API Key 获取 ----------------
doc.add_page_break()
h1("三、API Key 获取教程")
para("三个 key，只有第一个是必须的。都只需要注册一个账号，有免费额度。")

h2("3.1 DeepSeek（必填）")
para("用途：总结、事件划分、对话回答。")
bullet("打开 platform.deepseek.com，注册并登录。")
bullet("左侧「API keys」→「创建 API key」，起个名字（比如 mbfw）。")
bullet("复制以 sk- 开头的那串（只显示一次，记得存好）。")
bullet("回到窗口「API」面板，粘贴到「DeepSeek key」，点保存。")
bullet("点「测试连接」，显示连接正常即成功。")
para("计费：按 token 计，日常聊天很便宜；充值入口在「充值」页。")

h2("3.2 智谱（可选，向量）")
para("用途：把记忆转成 2048 维向量，让「换个说法也能搜到」成立。不填也能用，精度下降。")
bullet("打开 open.bigmodel.cn，注册并登录。")
bullet("进入「API Keys」页面，创建新的 key。")
bullet("复制 key（形如 xxxx.xxxx）。")
bullet("如果是新账号，先在「资源包」里领取免费的 embedding 额度。")
para("说明：本工具当前版本把向量 key 放在服务器的 .env 配置里；填了之后点「重新索引」，旧记忆才会用新向量重算。")

h2("3.3 千问 OCR（可选，读图）")
para("用途：识别截图与图片里的文字。")
bullet("打开 bailian.console.aliyun.com（阿里云百炼），用阿里云账号登录。")
bullet("右上角「API-KEY」→ 创建新的 API Key。")
bullet("复制以 sk- 开头的 key。")
bullet("回到窗口「API」面板，粘贴到「千问 OCR key」，保存。")
para("模型用的是 qwen-vl-ocr，按调用次数计费，识别一张图几分钱。")

h2("3.4 费用参考")
table(["项目", "单价（大约）", "日常用量"], [
    ["DeepSeek 总结", "每百万 token 几块钱", "100 轮对话约几毛"],
    ["千问 OCR", "每张图几分钱", "看截图频率"],
    ["智谱向量", "有免费额度", "少量使用不花钱"],
], [4.2, 5.4, 5.4])

# ---------------- 五、DSH 插件 ----------------
doc.add_page_break()
h1("四、DSH 插件使用教程")
para("插件名：dsh-memory-bridge（记忆桥）。作用是把 DeepSeek Harness 的对话窗口接到记忆库，"
     "让窗口里的模型能用到这里的记忆。")

h2("4.1 安装")
para("先把本工具启动（默认 http://127.0.0.1:8790），然后打开终端执行：")
code('dsh plugin --profile desktop add "link:C:\\Users\\user\\Desktop\\dsh-plugin-memory-bridge"')
para("装完重启 DeepSeek Harness。")

h2("4.2 打开设置页")
para("在 Harness 的「设置」里会多出一页「记忆桥」。")
para("如果设置里没有：执行 dsh plugin --profile desktop list 确认插件在列表里；"
     "仍然没有则说明没重启，客户端那一半要重启才加载。")

h2("4.3 使用")
bullet("刷新记忆体 → 选一个 →（可选）选要绑定的对话窗口 → 保存并启用。")
bullet("点「生成记忆骨架」，会把最近事件的日记、线头、看过的图组装成一段文字，可复制。")
bullet("启用后，每轮把记忆写进系统提示词（聊天记录里看不到）；勾了「接管上下文」时旧历史不再发送。")
bullet("点「停用」立刻失效。")

h2("4.4 原理")
bullet("宿主侧 lib/index.js：注册 /dsh-memory/api/* 接口，并把记忆注册进 systemPrompt.context。")
bullet("客户端 lib/client.js：往设置里插一页，调上面的接口。")
bullet("兜底：插件还在 127.0.0.1:8791 起了一个小接口，DSH 路由不可用时客户端会自动退过去。")

h2("4.5 注意")
bullet("插件改动后要重启 Harness 才生效。")
bullet("没选记忆体时保存会被拦下。")
bullet("接管模式会让模型只看到记忆、看不到旧对话，适合「换个窗口接着聊」，不适合需要原对话细节的排错。")

# ---------------- 六、数据与备份 ----------------
h1("五、数据、备份与迁移")
code("server\\data\\memory.mv.db     记忆库（对话、事件、回忆、人格）\n"
     "server\\data\\llm.json        密钥（明文，仅本机）\n"
     "server\\data\\media\\          图片原文件")
para("备份：关掉窗口，把整个 server\\data 目录复制走。恢复：覆盖回去再打开。")
para("迁移到另一台机器：复制整个文件夹（含 server\\runtime），插上就能用，不需要装 Java。")

# ---------------- 七、常见问题 ----------------
h1("六、常见问题")
table(["现象", "处理"], [
    ["窗口打不开 / 一闪而过", "多半是端口被占：确认没有另一个实例在跑，再开"],
    ["右上角一直「总结离线」", "key 没填或填错：进 API 面板点测试连接"],
    ["导入后一条条整理很慢", "正常，后台限速在跑；进度看右上角待处理条数"],
    ["图片里的字没识别出来", "没填千问 OCR key，或图里确实没文字"],
    ["记忆库看不到事件", "还没导入对话，或事件还在整理（进度条会显示）"],
    ["问旧事它说记不清", "那条确实没进记忆，或上下文模式太省：切到精准优先再问"],
    ["插件设置页 404", "没重启 Harness，或包名与 patch 里的 name 不一致"],
    ["两个窗口同时开打不开库", "H2 文件锁冲突：同一个数据目录只能开一个实例"],
], [5.0, 10.0])

# ---------------- 八、版本 ----------------
h1("七、版本")
table(["版本", "主要变化"], [
    ["1.0", "一问一答入库、向量检索、续命包（原文拼装）"],
    ["2.0", "事件层：划分 / 日记 / 增量归类；记忆库两层；第一人称"],
    ["3.0", "图片与 OCR；事件分页；上下文 20 条原文；DSH 插件"],
    ["4.0", "上下文池与两种模式；回忆栏；我是谁；语气样例搜索；按页切换；插件接管上下文"],
], [1.8, 13.6])

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
