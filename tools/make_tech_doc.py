# -*- coding: utf-8 -*-
"""生成《技术栈 / 架构 / 技术讲解》docx。"""

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

IMG = r"C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\docs\img"
OUT = r"C:\Users\user\Desktop\My Big Blue Fat Whale 4.0 技术架构与讲解.docx"


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
sec.top_margin, sec.bottom_margin = Cm(2.1), Cm(2.0)
sec.left_margin, sec.right_margin = Cm(2.2), Cm(2.2)

normal = doc.styles["Normal"]
normal.font.name = BODY
normal.font.size = Pt(10)
normal.font.color.rgb = DARK
east_asia(normal, BODY)
normal.paragraph_format.line_spacing = 1.55
normal.paragraph_format.space_after = Pt(6)

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
    p.paragraph_format.left_indent = Cm(0.4)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(7)
    p.paragraph_format.line_spacing = 1.2
    r = p.add_run(t)
    r.font.name = "Consolas"
    r.font.size = Pt(8.4)
    r.font.color.rgb = RGBColor(0x2B, 0x2B, 0x2B)
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), "F4F2EE")
    pPr.append(shd)


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
            r.font.size = Pt(8.6)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def figure(name, width_cm=16.4):
    path = os.path.join(IMG, name)
    if not os.path.exists(path):
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(3)
    p.add_run().add_picture(path, width=Cm(width_cm))


# ---------------- 封面 ----------------
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(80)
r = p.add_run("My Big Blue Fat Whale")
r.font.name = "Georgia"
r.font.size = Pt(26)
r.bold = True

p = doc.add_paragraph()
r = p.add_run("4.0 技术栈 · 架构 · 技术讲解")
r.font.name = HEAD
r.font.size = Pt(15)
r.font.color.rgb = GOLD
east_asia(r, HEAD)

p = doc.add_paragraph()
r = p.add_run("给 AI 对话装一套外置记忆：从消息到记忆，从检索到续命")
r.font.size = Pt(10)
r.font.color.rgb = GREY

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(160)
r = p.add_run("2026-10-28　·　对应版本 4.0")
r.font.size = Pt(9)
r.font.color.rgb = GREY

doc.add_page_break()

# ---------------- 一、总览 ----------------
h1("一、总览")
para("这个项目要解决的是一个具体问题：AI 的对话窗口有上限，聊得越久，前面的话就被挤出去；"
     "换个新窗口，模型连你是谁都不记得。它不是“忘了”，是那些话根本没有再被送进去。")
para("做法是把对话沉淀成外置记忆：一轮问答入库 → 逐轮写第一人称小记并算向量 → 读完全部原文切成事件、"
     "写事件日记 → 每次对话时，从记忆里挑一小部分原文装进上下文。")
para("三条不变量支撑了整个设计：")
bullet("摘要只负责被检索到，真正给模型看的是原文。")
bullet("记忆分两层：事件是“一件事”，对话是“当时那句话”。")
bullet("每轮的上下文是一个固定预算的池子，不是无限堆叠的历史。")

figure("arch-overview.png")

# ---------------- 二、技术栈 ----------------
h1("二、技术栈")
h2("2.1 后端")
table(["项", "选型", "说明"], [
    ["语言", "Java 21", "record、文本块、switch 表达式"],
    ["框架", "Spring Boot 3.3.5", "Web / Data JPA / 定时任务"],
    ["数据库", "H2（文件模式）", "单文件 memory.mv.db，免安装；留了 MySQL 配置开关"],
    ["ORM", "Hibernate 6", "ddl-auto=update，加表加列不动旧数据"],
    ["HTTP 客户端", "RestClient", "DeepSeek / 千问 OCR / DeepSeek 网页版"],
    ["并发", "单机线程池 + @Scheduled", "无消息队列，靠 Worker 轮询"],
    ["构建", "Maven（离线可用）", "spring-boot 插件打可执行 jar"],
], [2.6, 5.0, 8.0])

h2("2.2 前端")
table(["项", "选型", "说明"], [
    ["框架", "React 18.3 + TypeScript", "无 UI 库，手写 CSS 变量体系"],
    ["构建", "Vite 6", "产物直接打进后端 static，同源访问"],
    ["样式", "原生 CSS", "黑金配色，无 Tailwind"],
    ["状态", "useState / useEffect", "没上 Redux，跨页用 props 与 hash"],
], [2.6, 5.6, 7.4])

h2("2.3 桌面外壳")
table(["项", "选型", "说明"], [
    ["窗口", ".NET 8 WinForms + WebView2", "原生窗口，内嵌 Edge 内核"],
    ["启动流程", "找空闲端口 → 拉起 javaw → 轮询健康检查", "无黑窗，失败有提示"],
    ["图标", "多尺寸 .ico", "exe / 窗口 / 任务栏共用"],
    ["运行时", "jlink 裁剪 JRE 21", "约 87 MB，用户不用装 Java"],
    ["发布", "dotnet publish 单文件", "自包含，约 64 MB"],
], [2.6, 6.2, 6.8])

h2("2.4 插件与工具")
table(["项", "说明"], [
    ["DSH 插件", "dsh-memory-bridge：宿主侧 Node 插件 + 客户端 React section"],
    ["会话解析", "scripts/dsh.mjs：解 zstd 帧、归一化消息、抽取图片附件"],
    ["文档与视频", "python-docx 生成文档，LibreOffice Kit 转 PDF，ffmpeg 合成视频"],
], [3.6, 11.6])

h2("2.5 外部服务")
table(["用途", "服务", "降级策略"], [
    ["对话 / 总结 / 事件划分", "DeepSeek chat", "无 key：截断式兜底摘要，功能可用但质量下降"],
    ["向量", "智谱 embedding-3（2048 维）", "无 key：本地哈希向量 512 维，检索退化为词面"],
    ["看图识字", "千问 qwen-vl-ocr", "无 key：图片照样保存，只是不识别文字"],
    ["网页版导入", "DeepSeek 网页版 v0 接口", "失败时提示改用 cURL 或 Harness"],
], [4.4, 6.0, 6.8])

# ---------------- 三、系统架构 ----------------
h1("三、系统架构")
h2("3.1 分层")
para("从外到内是四层：桌面壳、前端、后端 API、服务与存储。桌面壳只负责“把服务拉起来并显示网页”，"
     "不含业务逻辑；前端只调同源 API；所有状态都在后端。")
bullet("桌面壳：读命令行参数与数据目录，选端口，拉起 javaw，等 /api/health 通了再加载 WebView2。")
bullet("前端：单页应用，顶栏 + 侧栏 + 四个主页面（记忆库 / 导入 / 续命包 / 设置），外加 AI 对话。")
bullet("API 层：一个 ApiController 汇总全部端点，按功能分组；参数用 Map 接收，返回值统一是 Map/JSON。")
bullet("服务层：MemoryService（检索与上下文）、EpisodeService（事件）、StyleService（人格）、"
        "MediaService / QwenOcrService（图片与 OCR）、SummaryService（总结）、ChatService（对话）。")

h2("3.2 一次请求的路径")
code("浏览器 → WebView2 → http://127.0.0.1:<port>/api/...\n"
     "  → ApiController\n"
     "    → MemoryService：检索 / 拼上下文\n"
     "    → ChatService：调 DeepSeek，拿回复\n"
     "    → 把关写回记忆体（pending）\n"
     "  → Worker（每 2 秒）：总结 → 向量 → 事件归类 → 日记\n"
     "  → H2 落盘")

h2("3.3 端口与进程")
para("开发时后端 8787、Vite 5173；打包后由外壳选 8790–8810 的端口，前端走同源相对路径，"
     "所以同一个界面既能跑在开发服务器上，也能跑在 jar 里。")
para("只有一个 Java 进程。所有后台任务都在这个进程里：没有独立队列服务，也没有定时容器。")

# ---------------- 四、核心数据流 ----------------
h1("四、核心数据流")

h2("4.1 导入：消息 → 轮次 → 记忆")
para("导入的第一步不是入库，而是把原始消息归一化成“轮”。不同来源的消息形状不一样，规则统一：")
table(["情况", "处理"], [
    ["多问一答（连续几条 user 才等到回答）", "合并成一条问题"],
    ["一问多答（几条 assistant 连发）", "合并成一条回答"],
    ["同一问题重复出现", "视作重新生成，只留最后一次"],
    ["无问有答（助手自主行动）", "10 分钟内的挂到上一轮；否则单独成条"],
    ["图片附件", "落到 data/media，按内容哈希去重，挂到这一轮"],
], [7.4, 8.0])
para("归一化之后，一轮问答就是 memories 表里的一行：question + answer + occurredAt + status=pending。")

h2("4.2 记忆流水线")
code("pending → Worker 取 10 条 → 调模型写第一人称小记 + 关键词 + 重要度\n"
     "        → 调向量接口 → 写 embedding → status=ready")
para("Worker 是 @Scheduled(fixedDelay=2000) 的轮询：每次取最多 10 条 pending，串行处理。"
     "导入接口立即返回，进度通过 /api/health 的 pending 字段暴露。")
para("小记是给检索用的，不是给模型看的。它的价值在于“自洽”：原文里有大量指代，摘要读起来是一句完整的话，"
     "向量和词面都更容易命中。")

h2("4.3 事件层")
para("事件的规则是死的：一条对话只属于一个事件；事件时间取它最早那条对话的时间；事件内按时间排序，"
     "事件之间也按时间排序。")
code("首次导入 → 全量划分：按 30 轮分块，LLM 切块内事件，落库后逐事件写日记\n"
     "之后新对话 → 攒 3 条归类：候选 = 最近 15 个事件，LLM 返回 event_id 或 new_title\n"
     "失败兜底 → 归到时间最近的既有事件（episodeStatus=fallback）")
para("日记由三部分组成：发生了什么、我怎么理解他、我做了什么取舍；同时产出情绪（从→到、强度、依据）、"
     "关系备注、未完成线头、事件级向量。")
para("长事件（超过 50 轮）会尝试拆成 2–3 段；拆不动就标记为长事件，日记改成只细写最近的部分。"
     "事件之间还有一张 episode_transitions 表，记录“隔了几天”“话题怎么转的”，续命包组装时用。")

h2("4.4 检索与上下文池")
figure("arch-context.png")
para("检索打分是加权求和：")
code("score = 0.68 × 向量相似度 + 0.16 × 新近度 + 0.09 × 记忆强度 + 0.07 × 重要度 + 0.06 × 词面")
para("词面这一项专门兜底：会扫问题、答案、小记、关键词，4 字以上的原样命中直接给高分——"
     "诗句、报错码、函数名这类罕见词，向量未必准，词面一定准。")
para("上下文装配不再按固定区块拼，而是一个池子：")
bullet("候选：最近 10/20 条原文 + 命中事件的整段对话 + 对话级检索命中的单轮原文。")
bullet("去重：按 turn id，同一句话不会出现两次。")
bullet("装填：最近上下文保底，其余按分数与预算装；单个事件最多占池子的 40%（精准模式 60%）。")
bullet("输出：按时间排序，带来源标签（〔那时的事〕〔原话〕〔最近〕）。")
para("两种模式的差别只在预算与门槛：预算优先 8k token、事件最多 1 个、门槛 0.45/0.30；"
     "精准优先不设总预算（安全上限 24k）、最近带到 20 条、事件最多 2 个、门槛放宽到 0.30/0.20。")

h2("4.5 人格层")
para("人格分三件东西，放在每轮上下文的固定层，比事件更靠前：")
table(["件", "内容", "来源"], [
    ["我是谁", "第一人称自述：我是他的谁、我怎么对他、我不做什么（120–220 字）", "读事件日记 + 风格 + 样例生成，带依据回查"],
    ["说话方式", "语气、称呼、格式、禁忌", "从对话里总结"],
    ["语气样例", "最多 8 条原话", "手挑，可按原文搜索"],
], [2.6, 7.4, 5.6])
para("「我是谁」每次被覆盖前，旧版本会存进 text_history，界面上可以「查看过往」。")

h2("4.6 回忆")
para("「回忆」不是又一个记忆库，而是从对话里挑出来的极少数：只有双方才懂的梗、暗号，特别开心的时刻，"
     "郑重的约定，突然的感悟。")
para("判定搭在逐轮总结的同一次调用里，不额外花钱：模型可以返回一个可选的 treasure 对象，"
     "满足判据才给，普通寒暄一律 null。入库时按“记忆体 + 标题”去重，同一件事只留一条。")
para("回忆用第一人称写，并附一句“为什么珍贵”。")

h2("4.7 图片与 OCR")
code("图片 → sha256 → data/media/<space>/<yyyyMM>/<hash>.<ext>\n"
     "     → attachments 行（pending）→ 队列 → 千问 OCR\n"
     "     → 识别文字补进记忆正文 → 重新排队总结")
para("按内容哈希命名，同一张图只存一份；事件详情里显示缩略图；续命包会带一句“他给你看过的东西”。")

h2("4.8 续命包")
para("续命包是一次性组装，不是增量：按事件组装时取最近 5 个事件，串上过渡、线头、情绪、看过的图；"
     "按原文组装时走检索。界面上显示字数与 token 估算，一键复制。")

# ---------------- 五、数据模型 ----------------
h1("五、数据模型")
table(["表", "作用", "关键字段"], [
    ["spaces", "记忆体", "name, persona_self, style_profile, context_mode, episode_auto_rewrite"],
    ["memories", "对话记忆", "question, answer, summary, keywords, importance, embedding, episode_id, status"],
    ["episodes", "事件记忆", "title, diary, mood_*, relation_note, open_threads, started_at, last_at, embedding"],
    ["episode_transitions", "事件接缝", "from_episode_id, to_episode_id, kind, days_gap, transition_note"],
    ["attachments", "图片", "sha256, path, mime, ocr_text, caption, memory_id, status"],
    ["treasures", "回忆", "kind, title, text, why, memory_id"],
    ["text_history", "人格历史", "kind(self/style), text, created_at"],
    ["import_jobs", "导入任务", "total, ids, created_at"],
], [3.4, 3.6, 9.4])
para("关系是线性的：space → memories（一对多）→ episodes（多对一）；attachments 挂 memory；"
     "treasures 同时记 space 与 memory，便于回跳原文。")

# ---------------- 六、接口 ----------------
h1("六、关键接口")
table(["分组", "接口"], [
    ["状态", "GET /api/health、GET /api/stats?spaceId="],
    ["记忆体", "GET/POST /api/spaces、DELETE /api/spaces/{id}、POST /api/spaces/merge"],
    ["对话记忆", "GET /api/memories?spaceId=&limit=&offset=、POST /api/recall"],
    ["导入", "POST /api/import、/api/web/import、/api/dsh/import、/api/deepseek/import"],
    ["事件", "GET /api/spaces/{id}/episodes?limit=&offset=、GET /api/episodes/{id}、"
             "POST .../episodes/pack、POST .../episodes/partition、POST /api/episodes/{id}/merge"],
    ["图片", "POST /api/spaces/{id}/screenshots、POST/GET /api/memories/{id}/attachments、GET /api/attachments/{id}/raw"],
    ["人格", "GET/POST /api/spaces/{id}/style、POST .../persona/self、GET .../history、GET/POST .../context-mode"],
    ["回忆", "GET/POST /api/spaces/{id}/treasures、DELETE /api/treasures/{id}"],
    ["模型", "GET/POST /api/llm、POST /api/llm/key、/api/llm/qwen、/api/llm/test"],
], [2.8, 12.6])

# ---------------- 七、取舍 ----------------
h1("七、关键设计决策")
table(["决策", "理由", "代价"], [
    ["摘要只做检索，回答用原文", "摘要会丢细节，模型需要原话才能答准", "存储翻倍：原文 + 摘要都要留"],
    ["事件是一等公民", "人回忆先想到事，再想到那句话", "多一层整理，导入后要等"],
    ["单进程 + 本地 H2", "单用户、绿色免安装，复制文件夹即迁移", "并发弱，只能开一个实例"],
    ["上下文用池子不用区块", "预算可控，命中错误时损失有限", "实现比拼字符串复杂"],
    ["防幻觉优先", "记忆会被反复引用，编造会自我强化", "有时宁可回答“记不清”"],
    ["插件走系统提示词", "聊天记录里不可见，不污染对话", "依赖宿主 API，版本变动要适配"],
], [5.0, 6.4, 5.2])

# ---------------- 八、可靠性 ----------------
h1("八、可靠性与降级")
bullet("模型没配：总结走截断兜底，向量走本地哈希，功能不断。")
bullet("总结失败：重试一次，仍失败则标记 failed 并记错误，不阻塞队列。")
bullet("事件归类失败：归到时间最近的既有事件，标 fallback，续命包里可识别。")
bullet("OCR 失败：附件标 failed，记忆照常保留，不重试风暴。")
bullet("插件改写出错：pre-step 里 try/catch，任何异常都返回原 decision，不弄坏会话。")
bullet("防幻觉：system 里明确“记忆里没有就说记不清”，实测比任何阈值都有效。")

# ---------------- 九、性能与成本 ----------------
h1("九、性能与成本")
table(["环节", "开销", "说明"], [
    ["导入", "每轮一次总结调用", "100 轮约 100 次，可并发限速"],
    ["事件划分", "每 30 轮一次调用", "100 轮约 4 次"],
    ["事件日记", "每个事件一次", "100 轮约 30 次"],
    ["每轮对话", "输入 4–8k token", "预算模式下稳定"],
    ["检索", "一次向量 + 本地打分", "毫秒级；无 key 时纯词面"],
], [3.6, 5.4, 7.4])
para("成本大头是“导入时的整理”，不是日常对话。整理是一次性的，之后每轮的成本是常数。")

# ---------------- 十、打包 ----------------
h1("十、打包与部署")
code("npm run build → 复制 dist 到 backend/src/main/resources/static\n"
     "mvn clean package → dayufish-backend-0.1.0.jar（内含前端）\n"
     "jlink --add-modules ALL-MODULE-PATH … → server/runtime\n"
     "dotnet publish -r win-x64 --self-contained → 单文件 exe\n"
     "组装：exe + app.ico + scripts + server/{runtime, app, data}")
para("发布包约 215 MB，其中 JRE 87 MB、jar 63 MB、exe 64 MB。用户双击即用，不需要装 Java，也不需要联网装依赖。")

# ---------------- 十一、插件 ----------------
h1("十一、DSH 插件技术细节")
para("插件是两层：宿主侧跑在 Harness 主进程，客户端侧跑在界面里。")
table(["层", "做什么"], [
    ["宿主 lib/index.js", "注册 /dsh-memory/api/*；把记忆写进 systemPrompt.context；监听 agent/pre-step 决定发哪些消息"],
    ["客户端 lib/client.js", "往 Harness 设置里插一页（slots.inject('settings.section')），选记忆体、启用/停用"],
], [4.0, 11.4])
para("两个关键点：")
bullet("systemPrompt.context 的 text 是函数，每次组装时求值，所以能读到最新状态，又不出现在聊天记录里。")
bullet("agent/pre-step 是 waterfall，可以改写这一轮的消息；接管模式下只发当前这一句，旧历史不再发送。"
       "改写时必须展开 decision（{ ...decision, messages }），否则会丢字段。")

# ---------------- 十二、限制 ----------------
h1("十二、已知限制与演进")
bullet("事件粒度偏细（100 轮约 30 个事件），可手动合并，但自动切分仍不够“像人”。")
bullet("长事件的自动拆分缺少足够多的真实样例验证。")
bullet("事件命中依赖向量质量；没有向量 key 时只能靠词面，换个说法就可能搜不到。")
bullet("单用户、无鉴权，只监听回环地址，不能直接暴露公网。")
bullet("插件与宿主 API 强耦合，Harness 升级后可能需要适配。")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
