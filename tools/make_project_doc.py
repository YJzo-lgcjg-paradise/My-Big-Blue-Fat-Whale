# -*- coding: utf-8 -*-
"""生成《项目说明：技术栈 / 功能 / 实现方式》。"""

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

OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\docs\项目说明.docx"


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
normal.font.size = Pt(10)
normal.font.color.rgb = DARK
east_asia(normal, BODY)
normal.paragraph_format.line_spacing = 1.5
normal.paragraph_format.space_after = Pt(5)

for name, size in (("Heading 1", 15), ("Heading 2", 11.5)):
    st = doc.styles[name]
    st.font.name = HEAD
    st.font.size = Pt(size)
    st.font.color.rgb = DARK
    st.font.bold = True
    east_asia(st, HEAD)
    st.paragraph_format.space_before = Pt(15 if name == "Heading 1" else 9)
    st.paragraph_format.space_after = Pt(6)


def h1(t):
    doc.add_heading(t, level=1)


def h2(t):
    doc.add_heading(t, level=2)


def para(t):
    return doc.add_paragraph(t)


def bullet(t):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.42
    p.add_run(t)


def code(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(t)
    r.font.name = "Consolas"
    r.font.size = Pt(8.5)
    r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    hdr = t.rows[0].cells
    for i, text in enumerate(headers):
        hdr[i].text = ""
        r = hdr[i].paragraphs[0].add_run(text)
        r.bold = True
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(hdr[i], "8A7040")
    for row in rows:
        cells = t.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(text)
            r.font.size = Pt(8.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# 标题
p = doc.add_paragraph()
r = p.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(22)
r.bold = True
p = doc.add_paragraph()
r = p.add_run("项目说明 · 技术栈 / 功能 / 实现方式")
r.font.name = HEAD
r.font.size = Pt(13)
r.font.color.rgb = GOLD
east_asia(r, HEAD)
p = doc.add_paragraph()
r = p.add_run("版本 2.0　·　2026-10-27")
r.font.size = Pt(9)
r.font.color.rgb = GREY

h1("一、这是什么")
para("一个给 AI 对话装外置记忆的工具。它把长对话按「事件」归纳成第一人称的记忆，"
     "再把少量骨架带进新窗口，让对话能在上下文耗尽后继续下去。")
para("三个直接用途：")
bullet("续命：对话快撞上上下文上限时，把该记的带走，换个新窗口接着聊。")
bullet("搬家：多个聊天框的内容并进同一个记忆体。")
bullet("回想：一句话把很久以前聊过的事找回来，需要时还能翻到原话。")

h1("二、技术栈")

h2("后端")
table(
    ["项", "选型", "说明"],
    [
        ["语言", "Java 21", "与既有项目对齐，用 record、switch 表达式等"],
        ["框架", "Spring Boot 3.3.5", "Web / Data JPA / 定时任务"],
        ["数据库", "H2（文件模式）", "单文件 memory.mv.db，免安装；留了 MySQL 配置开关"],
        ["ORM", "Hibernate 6", "ddl-auto=update，加表加列不动旧数据"],
        ["HTTP", "RestClient", "调 DeepSeek / 智谱 / DeepSeek 网页版"],
        ["构建", "Maven", "boot 插件打可执行 jar"],
    ],
    widths=[2.2, 4.2, 9.2],
)

h2("前端")
table(
    ["项", "选型", "说明"],
    [
        ["框架", "React 18.3 + TypeScript", "无 UI 库，手写 CSS 变量体系"],
        ["构建", "Vite 6", "产物打进后端 static，同源访问"],
        ["样式", "原生 CSS", "黑金配色，无 Tailwind"],
    ],
    widths=[2.2, 5.0, 8.4],
)

h2("桌面外壳与打包")
table(
    ["项", "选型", "说明"],
    [
        ["窗口", ".NET 8 WinForms + WebView2", "原生窗口，内嵌 Edge 内核，不是浏览器标签页"],
        ["图标", "多尺寸 .ico", "exe / 窗口 / 任务栏共用一张"],
        ["运行时", "jlink 裁剪 JRE 21", "约 87 MB，用户不用装 Java"],
        ["发布", "dotnet publish 单文件", "自包含，约 67 MB"],
    ],
    widths=[2.2, 5.6, 7.8],
)

h2("外部服务")
table(
    ["用途", "服务", "可替换性"],
    [
        ["对话 / 总结 / 事件划分", "DeepSeek chat（deepseek-chat）", "填任意兼容 OpenAI 协议的 key"],
        ["向量", "智谱 embedding-3（2048 维）", "缺 key 时退化为本地哈希向量（512 维）"],
        ["网页版导入", "DeepSeek 网页版 v0 接口", "用 userToken / cURL，只读"],
    ],
    widths=[4.0, 5.6, 6.0],
)

h1("三、功能清单")

h2("记忆体")
bullet("多记忆体，各自独立的对话、事件、人格锚点与统计。")
bullet("新建、重命名、删除；导出为 .json，或从 .json 导入。")

h2("导入")
table(
    ["来源", "做法"],
    [
        ["DeepSeek Harness", "读本机 ~/.dsh/sessions，列出会话直接导入"],
        ["DeepSeek 网页版", "填 userToken 或整段 cURL，列出会话后导入"],
        ["已有记忆体", "拖 .json 到窗口任意位置，或从现有记忆体复制"],
        ["手动粘贴", "用户/助手、User/Assistant、问/答 等格式"],
    ],
    widths=[4.2, 11.2],
)

h2("记忆库（两层）")
bullet("事件记忆：一件件事的卡片，第一人称日记 + 情绪 + 关系备注 + 未完成线头。")
bullet("对话记忆：一问一答原文，分页、关键词搜索、深度回忆。")
bullet("事件可点开看下辖对话；可勾选两条合并；长事件自动尝试拆分。")

h2("AI 对话")
bullet("每轮先检索当前记忆体，再把命中内容拼进 system prompt。")
bullet("问题先经 LLM 改写（补指代、同义词）再检索。")
bullet("每个记忆体一条独立对话线，存在本地，关窗不丢。")
bullet("聊完的问答写回记忆体，后台继续总结、归类。")

h2("说话方式 / 人格锚点")
bullet("从双方对话里总结语气、称呼、态度、格式习惯、禁忌。")
bullet("可手选最多 8 条对话作为语气样例。")
bullet("可按当前语气重写全部总结（事实不变，只改腔调）。")

h2("续命包")
bullet("两种组装：按原文（旧版）或按事件（推荐）。")
bullet("按事件组装 = 人格锚点 + 最近事件日记 + 事件间过渡 + 线头 + 情绪。")
bullet("显示字数与 token 估算，附复制按钮。")

h1("四、实现方式")

h2("1. 记忆流水线")
code("导入/对话 → 切分（一轮问答=一条 memories）→ 状态 pending\n"
     "→ Worker 每 2 秒取 10 条 → 调模型写第一人称小记 + 关键词 + 重要度\n"
     "→ 调向量接口 → 写 embedding → 状态 ready")
para("Worker 是 @Scheduled 定时任务，导入接口立即返回，进度由 /api/health 的 pending 暴露。")

h2("2. 检索打分")
code("query → LLM 改写 → 向量相似度 + 词面匹配 + 新近度 + 记忆强度 + 重要度")
para("权重：相似度 0.68、新近度 0.16、强度 0.09、重要度 0.07、词面 0.06；半衰期 30 天。")
para("词面匹配会扫正文（问题、答案、摘要、关键词），并对 4 字以上的原样命中直接加分——"
     "诗句、报错码、函数名这类罕见词靠它兜住。")

h2("3. 事件层")
para("严格遵循三条规则：一条对话只属于一个事件；事件时间取该事件最早对话的时间；"
     "事件内按时间排序，事件之间也按时间排序。")
code("首次导入 → 全量划分：按 30 轮分块，LLM 切块内事件，落库后逐事件写日记\n"
     "之后新对话 → 攒 3 条归类：候选=最近 15 个事件，LLM 返回 event_id 或 new_title\n"
     "失败兜底 → 归到时间最近的既有事件，标 fallback")
para("日记由三样东西构成：发生了什么、我怎么理解他、我做了什么取舍；"
     "同时产出情绪（从→到、强度、依据）、关系备注、未完成线头、事件级向量。")

h2("4. 长事件与接缝")
bullet("单个事件超过 50 轮：让模型试拆 2–3 段；拆不动就标记 long_running，日记改滚动式。")
bullet("事件之间用 episode_transitions 记录过渡（隔了几天 / 话题怎么转的），"
       "归类时同一次调用顺带产出，不额外花钱。")

h2("5. 桌面壳")
code("exe 启动 → 从 8790 找空闲端口 → 拉起 javaw（无黑窗）\n"
     "→ 轮询 /api/health → 就绪后 WebView2 加载页面 → 关窗时结束子进程")
para("后端地址由前端运行时判断：开发时（5173）指向 8787，打包后同源相对路径。")

h2("6. 关键实现细节")
bullet("密钥运行时可改：LlmSettings 读 data/llm.json，优先级高于环境变量，保存即生效。")
bullet("打包版禁用 .env.local 自动探测，避免把开发机上的 key 带进产品包。")
bullet("事件重建保留人工改过的事件（edited_by_user），其余重切。")
bullet("分页：记忆列表 30 条一页，按 offset/limit 在库层截取。")

h1("五、数据模型")
table(
    ["表", "作用", "关键字段"],
    [
        ["spaces", "记忆体", "name, persona/style, episode_auto_rewrite"],
        ["memories", "对话记忆（原文）", "question, answer, summary, embedding, episode_id"],
        ["episodes", "事件记忆", "title, diary, mood_*, relation_note, open_threads, started_at, last_at"],
        ["episode_transitions", "事件接缝", "from/to, kind, days_gap, transition_note"],
        ["import_jobs", "导入任务", "total, done, status, ids"],
    ],
    widths=[3.6, 4.6, 7.4],
)

h1("六、接口概览")
table(
    ["分组", "主要接口"],
    [
        ["状态", "GET /api/health、GET /api/stats?spaceId="],
        ["记忆体", "GET/POST /api/spaces、DELETE /api/spaces/{id}、POST /api/spaces/merge"],
        ["对话记忆", "GET /api/memories?spaceId=&limit=&offset=、GET/DELETE /api/memories/{id}"],
        ["导入", "POST /api/import、/api/web/import、/api/dsh/import、/api/deepseek/import"],
        ["检索", "POST /api/recall、POST /api/bundle、POST /api/chat"],
        ["事件", "GET /api/spaces/{id}/episodes、GET /api/episodes/{id}、POST .../partition、"
                 "POST .../pack、POST /api/episodes/{id}/merge"],
        ["人格", "GET/POST /api/spaces/{id}/style、POST .../style/picks、POST .../restyle"],
        ["模型", "GET /api/llm、POST /api/llm/key、/api/llm/test、/api/llm/clear"],
    ],
    widths=[2.6, 12.8],
)

h1("七、目录结构")
code("backend/          Spring Boot\n"
     "  src/main/java/com/dayufish/\n"
     "    service/      MemoryService / SummaryService / EpisodeService / StyleService ...\n"
     "    web/          ApiController\n"
     "    domain/       Space / Memory / Episode / EpisodeTransition\n"
     "  data/memory.mv.db\n"
     "frontend/         React + Vite\n"
     "  src/pages/      Library / ImportPage / BundlePage / Settings / ChatPage\n"
     "desktop-host/     .NET 8 WinForms + WebView2 外壳\n"
     "tools/            打包与文档脚本\n"
     "docs/             设计文档与手册\n")

h1("八、构建与打包")
code("cd frontend && npm run build\n"
     "xcopy /E /I /Y dist\\* ..\\backend\\src\\main\\resources\\static\\\n"
     "cd ..\\backend && mvn -DskipTests package\n"
     "jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files \\\n"
     "      --no-man-pages --compress=zip-6 --output ..\\build\\rt\n"
     "dotnet publish ../desktop-host -c Release -r win-x64 --self-contained true -o ../build/host\n"
     "→ 组装：exe + app.ico + server\\{runtime, app, data}")

h1("九、设计取舍")
table(
    ["取舍", "原因"],
    [
        ["摘要只做检索，回答用原文", "摘要会丢细节，模型需要原话才能准确回答"],
        ["事件是第一公民", "人回忆先想起事件，再想起具体的话"],
        ["单进程 + 本地 H2", "单用户、绿色免安装，复制文件夹即迁移"],
        ["网页版走 v0 接口", "没有公开 API，只能复用其前端接口，随版本可能变动"],
        ["向量缺 key 时退化", "保证离线可用，只是检索精度下降"],
    ],
    widths=[5.0, 10.4],
)

h1("十、已知限制")
bullet("事件粒度偏细（100 轮约 30 个事件），可手动合并。")
bullet("长事件自动拆分缺少真实样例验证。")
bullet("网页版接口随对方改版可能失效。")
bullet("单用户无鉴权，只监听 127.0.0.1，不可直接暴露公网。")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
