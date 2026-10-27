# -*- coding: utf-8 -*-
"""生成 3.0 版《项目说明》与《使用手册》（docx）。"""

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

OUT_DIR = r"C:\Users\user\Desktop\My Big Blue Fat Whale 3.0"


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


class Doc:
    def __init__(self, title, subtitle, meta):
        self.d = Document()
        sec = self.d.sections[0]
        sec.page_width, sec.page_height = Cm(21), Cm(29.7)
        sec.top_margin, sec.bottom_margin = Cm(2.2), Cm(2.0)
        sec.left_margin, sec.right_margin = Cm(2.3), Cm(2.3)

        normal = self.d.styles["Normal"]
        normal.font.name = BODY
        normal.font.size = Pt(10)
        normal.font.color.rgb = DARK
        east_asia(normal, BODY)
        normal.paragraph_format.line_spacing = 1.55
        normal.paragraph_format.space_after = Pt(5)

        for name, size in (("Heading 1", 15), ("Heading 2", 11.5)):
            st = self.d.styles[name]
            st.font.name = HEAD
            st.font.size = Pt(size)
            st.font.color.rgb = DARK
            st.font.bold = True
            east_asia(st, HEAD)
            st.paragraph_format.space_before = Pt(15 if name == "Heading 1" else 9)
            st.paragraph_format.space_after = Pt(6)

        p = self.d.add_paragraph()
        r = p.add_run("My Big Blue Fat Whale")
        r.font.name = "Georgia"
        r.font.size = Pt(22)
        r.bold = True

        p = self.d.add_paragraph()
        r = p.add_run(subtitle)
        r.font.name = HEAD
        r.font.size = Pt(13)
        r.font.color.rgb = GOLD
        east_asia(r, HEAD)

        p = self.d.add_paragraph()
        r = p.add_run(meta)
        r.font.size = Pt(9)
        r.font.color.rgb = GREY

    def h1(self, t):
        self.d.add_heading(t, level=1)

    def h2(self, t):
        self.d.add_heading(t, level=2)

    def para(self, t):
        return self.d.add_paragraph(t)

    def bullet(self, t):
        p = self.d.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.42
        p.add_run(t)

    def code(self, t):
        p = self.d.add_paragraph()
        p.paragraph_format.left_indent = Cm(0.5)
        p.paragraph_format.space_before = Pt(3)
        p.paragraph_format.space_after = Pt(6)
        r = p.add_run(t)
        r.font.name = "Consolas"
        r.font.size = Pt(8.5)
        r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)

    def quote(self, t):
        p = self.d.add_paragraph()
        p.paragraph_format.left_indent = Cm(0.6)
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(8)
        r = p.add_run(t)
        r.font.family = HEAD
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0x4A, 0x4A, 0x4A)

    def table(self, headers, rows, widths=None):
        t = self.d.add_table(rows=1, cols=len(headers))
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
        self.d.add_paragraph().paragraph_format.space_after = Pt(2)

    def end(self):
        p = self.d.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run("— 完 —")
        r.font.color.rgb = GOLD
        r.font.size = Pt(10)

    def save(self, path):
        self.d.save(path)
        print("saved", path)


# ============================================================================
# 一、项目说明 3.0
# ============================================================================
def project_doc():
    d = Doc("My Big Blue Fat Whale",
            "3.0 项目说明 · 技术栈 / 功能 / 实现方式",
            "版本 3.0　·　2026-10-27　·　便携版")

    d.h1("一、这是什么")
    d.para("一个给 AI 对话装外置记忆的工具。它把长对话按「事件」归纳成第一人称的记忆，"
           "再把少量骨架带进新窗口，让对话能在上下文耗尽后继续下去。")
    d.para("三个直接用途：")
    d.bullet("续命：对话快撞上上下文上限时，把该记的带走，换个新窗口接着聊。")
    d.bullet("搬家：多个聊天框的内容并进同一个记忆体。")
    d.bullet("回想：一句话把很久以前聊过的事找回来，需要时还能翻到原话。")

    d.h1("二、技术栈")
    d.h2("后端")
    d.table(["项", "选型", "说明"], [
        ["语言", "Java 21", "record、switch 表达式"],
        ["框架", "Spring Boot 3.3.5", "Web / Data JPA / 定时任务"],
        ["数据库", "H2（文件模式）", "单文件 memory.mv.db，免安装"],
        ["ORM", "Hibernate 6", "ddl-auto=update，加表加列不动旧数据"],
        ["HTTP", "RestClient", "DeepSeek / 千问 OCR / 网页版接口"],
    ], [2.2, 4.2, 9.2])

    d.h2("前端")
    d.table(["项", "选型", "说明"], [
        ["框架", "React 18.3 + TypeScript", "无 UI 库，手写 CSS 变量体系"],
        ["构建", "Vite 6", "产物打进后端 static，同源访问"],
        ["样式", "原生 CSS", "黑金配色"],
    ], [2.2, 5.0, 8.4])

    d.h2("桌面外壳")
    d.table(["项", "选型", "说明"], [
        ["窗口", ".NET 8 WinForms + WebView2", "原生窗口，内嵌 Edge 内核"],
        ["图标", "多尺寸 .ico", "exe / 窗口 / 任务栏"],
        ["运行时", "jlink 裁剪 JRE 21", "约 87 MB，用户不用装 Java"],
        ["发布", "dotnet publish 单文件", "自包含，约 67 MB"],
    ], [2.2, 5.6, 7.8])

    d.h2("外部服务")
    d.table(["用途", "服务", "备注"], [
        ["对话 / 总结 / 事件划分", "DeepSeek chat", "填任意兼容 OpenAI 协议的 key"],
        ["向量", "智谱 embedding-3（2048 维）", "缺 key 时退化为本地哈希 512 维"],
        ["看图识字", "千问 qwen-vl-ocr（DashScope）", "识别截图与手写字"],
        ["网页版导入", "DeepSeek 网页版 v0 接口", "userToken / cURL，只读"],
    ], [4.2, 5.8, 5.8])

    d.h1("三、功能清单")
    d.h2("记忆体")
    d.bullet("多记忆体，各自独立的对话、事件、人格锚点与统计。")
    d.bullet("新建、重命名、删除；导出为 .json，或从 .json 导入（可拖进窗口）。")

    d.h2("导入")
    d.table(["来源", "做法"], [
        ["DeepSeek Harness", "读本机 ~/.dsh/sessions，多选会话批量导入，自动带图"],
        ["DeepSeek 网页版", "填 userToken 或整段 cURL，多选会话批量导入"],
        ["已有记忆体", "拖 .json 到窗口任意位置"],
        ["截图 / 图片", "拖进窗口，可多张，自动 OCR"],
        ["手动粘贴", "用户/助手、User/Assistant 等格式"],
    ], [3.8, 11.6])

    d.h2("记忆库（两层）")
    d.bullet("事件记忆：一件件事的卡片，第一人称日记 + 情绪 + 关系备注 + 未完成线头；分页加载。")
    d.bullet("对话记忆：一问一答原文，分页、关键词搜索、深度回忆。")
    d.bullet("事件可点开看下辖对话与图片缩略图；可勾选两条合并。")

    d.h2("图片与 OCR")
    d.bullet("图片按内容哈希落盘（同一张只存一份），原图本地保存，不上传。")
    d.bullet("千问 OCR 读出图里的字，补进对应那条记忆，并重新总结。")
    d.bullet("事件详情显示缩略图；续命包会带一句「他给你看过的东西」。")

    d.h2("AI 对话")
    d.bullet("每轮先检索当前记忆体，再把命中内容拼进 system prompt。")
    d.bullet("问题先经 LLM 改写（补指代、同义词）再检索。")
    d.bullet("每个记忆体一条独立对话线；聊完的问答写回记忆体。")

    d.h2("人格锚点 / 说话方式")
    d.bullet("从双方对话里总结语气、称呼、态度、格式习惯、禁忌。")
    d.bullet("可手选最多 8 条对话作为语气样例。")

    d.h2("续命包")
    d.bullet("两种组装：按原文，或按事件（推荐）。")
    d.bullet("按事件 = 事件日记 + 事件间过渡 + 线头 + 情绪 + 他给你看过的东西。")
    d.bullet("显示字数与 token 估算，一键复制。")

    d.h2("DSH 插件 dsh-memory-bridge")
    d.bullet("在 DeepSeek Harness 设置里新增「记忆桥」一页：选记忆体、绑定窗口、启用/停用。")
    d.bullet("启用后每轮把记忆写进系统提示词（聊天里不可见），接管时不再发送旧历史。")
    d.bullet("注入顺序：骨架 → 按当前问题检索原文 → 最近十条。")

    d.h1("四、实现方式")
    d.h2("1. 记忆流水线")
    d.code("导入/对话 → 切分（一轮问答=一条 memories）→ pending\n"
           "→ Worker 每 2 秒取 10 条 → 第一人称小记 + 关键词 + 重要度\n"
           "→ 向量 → ready")
    d.para("Worker 是定时任务，导入接口立即返回，进度由 /api/health 的 pending 暴露。")

    d.h2("2. 检索打分")
    d.code("query → LLM 改写 → 向量相似度 + 词面匹配 + 新近度 + 强度 + 重要度")
    d.para("权重：相似度 0.68、新近度 0.16、强度 0.09、重要度 0.07、词面 0.06；半衰期 30 天。"
           "词面会扫正文，4 字以上原样命中直接加分——诗句、报错码靠它兜住。")

    d.h2("3. 事件层")
    d.para("规则：一条对话只属于一个事件；事件时间取该事件最早对话的时间；"
           "事件内按时间排序，事件之间也按时间排序。")
    d.code("首次导入 → 全量划分：30 轮一块，LLM 切块内事件，逐事件写日记\n"
           "之后新对话 → 攒 3 条归类：候选=最近 15 个事件，LLM 返回 event_id 或 new_title\n"
           "失败兜底 → 归到时间最近的既有事件")
    d.para("日记含：发生了什么、我怎么理解他、我做了什么取舍；同时产出情绪、关系备注、"
           "未完成线头、事件级向量。长事件（>50 轮）自动尝试拆分，拆不动标记为长事件。")

    d.h2("4. 上下文：20 条原文 + 字数上限")
    d.para("事件归类和日记生成都会带上该记忆体最近 20 条对话的**原文**（不截句），"
           "总字数上限 20000（dayufish.context.recent-chars），从最新往前收。")

    d.h2("5. 图片")
    d.code("图片 → sha256 → data/media/<space>/<yyyymm>/<hash>.<ext>\n"
           "     → attachments 行 → 队列 → 千问 OCR → 补进记忆 → 重新总结")
    d.para("DSH 的附件落在 ~/.dsh/attachments/v1/objects/<前两位>/<完整哈希>，导入时按引用取回。")

    d.h2("6. DSH 插件")
    d.code("宿主：webServer 路由 + systemPrompt.context + agent/pre-step\n"
           "客户端：slots.inject('settings.section') → 记忆桥一页\n"
           "兜底：127.0.0.1:8791 小接口（DSH 路由不可用时客户端自动退过去）")
    d.para("pre-step 是 waterfall，可改写这一轮要发的消息；系统提示词段不可见，聊天里不显示记忆。")

    d.h1("五、数据模型")
    d.table(["表", "作用", "关键字段"], [
        ["spaces", "记忆体", "name, persona, episode_auto_rewrite"],
        ["memories", "对话记忆", "question, answer, summary, embedding, episode_id"],
        ["episodes", "事件记忆", "title, diary, mood_*, open_threads, started_at, last_at"],
        ["episode_transitions", "事件接缝", "from/to, kind, days_gap, note"],
        ["attachments", "图片", "sha256, path, ocr_text, caption, memory_id"],
        ["import_jobs", "导入任务", "total, done, status, ids"],
    ], [3.8, 4.0, 7.8])

    d.h1("六、接口概览")
    d.table(["分组", "主要接口"], [
        ["状态", "GET /api/health、GET /api/stats?spaceId="],
        ["记忆体", "GET/POST /api/spaces、DELETE /api/spaces/{id}、POST /api/spaces/merge"],
        ["对话记忆", "GET /api/memories?spaceId=&limit=&offset="],
        ["导入", "POST /api/import、/api/web/import、/api/dsh/import、/api/deepseek/import"],
        ["图片", "POST /api/spaces/{id}/screenshots、POST/GET /api/memories/{id}/attachments、GET /api/attachments/{id}/raw"],
        ["检索", "POST /api/recall、POST /api/bundle、POST /api/chat"],
        ["事件", "GET /api/spaces/{id}/episodes?limit=&offset=、GET /api/episodes/{id}、"
                 "POST .../episodes/pack、POST /api/episodes/{id}/merge"],
        ["人格", "GET/POST /api/spaces/{id}/style、POST .../style/picks"],
        ["模型", "GET/POST /api/llm、POST /api/llm/key、/api/llm/qwen、/api/llm/test"],
    ], [2.6, 12.8])

    d.h1("七、目录结构")
    d.code("backend/          Spring Boot（service / web / domain / repo）\n"
           "frontend/         React + Vite\n"
           "desktop-host/     .NET 8 WinForms + WebView2 外壳\n"
           "dsh-plugin-memory-bridge/  DSH 插件（宿主 + 客户端）\n"
           "scripts/dsh.mjs   读 Harness 会话（zstd 解压 + 归一化）\n"
           "tools/            打包与文档脚本")

    d.h1("八、构建与打包")
    d.code("cd frontend && npm run build\n"
           "复制 dist → backend/src/main/resources/static\n"
           "cd backend && mvn -DskipTests clean package\n"
           "dotnet publish desktop-host -c Release -r win-x64 --self-contained true\n"
           "组装：exe + app.ico + scripts + server\\{runtime, app, data}")

    d.h1("九、已知限制")
    d.bullet("事件粒度偏细（100 轮约 30 个事件），可手动合并。")
    d.bullet("长事件自动拆分缺少真实样例验证。")
    d.bullet("插件与 DSH 的接口随版本可能变动，需要适配。")
    d.bullet("单用户无鉴权，只监听本机回环地址。")

    d.h1("十、版本演进")
    d.table(["版本", "变化"], [
        ["1.0", "一问一答入库、向量检索、续命包（原文拼装）"],
        ["2.0", "事件层：划分 / 日记 / 增量归类 / 接缝；记忆库两层；第一人称"],
        ["3.0", "图片与千问 OCR；拖拽截图；导入自动抓图；事件分页；"
                "上下文 20 条原文；DSH 插件（系统提示词注入 + 接管）"],
    ], [2.0, 13.4])
    d.end()

    return os.path.join(OUT_DIR, "3.0 项目说明.docx")


# ============================================================================
# 二、使用手册 3.0
# ============================================================================
def manual_doc():
    d = Doc("My Big Blue Fat Whale",
            "3.0 使用手册",
            "版本 3.0　·　2026-10-27")

    d.h1("1. 这是什么")
    d.para("一个给 AI 对话装外置记忆的工具。它把长对话按「事件」记成第一人称的记忆，"
           "再把少量骨架带进新窗口，让对话能在上下文耗尽后继续下去。")
    d.bullet("续命：快撞上上下文上限时，把该记的带走，换个窗口接着聊。")
    d.bullet("搬家：多个聊天框的内容并进同一个记忆体。")
    d.bullet("回想：一句话把很久以前聊过的事找回来，还能翻到原话。")

    d.h1("2. 三分钟上手")
    d.bullet("双击 MyBigBlueFatWhale3.0.exe，窗口会自动打开（内置服务，无需装 Java）。")
    d.bullet("右上角「API」填 DeepSeek key，点保存。")
    d.bullet("左侧「+ 新建记忆体」建一个。")
    d.bullet("「导入」页挑一个来源把对话搬进来。")
    d.bullet("等右上角「总结在线」变绿、进度条走完，记忆就整理好了。")

    d.h1("3. 配置 API")
    d.h2("DeepSeek key（必填）")
    d.para("platform.deepseek.com → API keys → 创建，粘贴到「API」面板，点保存，再点「测试连接」。")
    d.h2("千问 OCR key（可选）")
    d.para("bailian.console.aliyun.com → API-KEY。填了才能读出图片里的字；"
           "不填也能用，只是截图里的文字不会被识别。")
    d.h2("智谱向量 key（可选）")
    d.para("没填时用本地哈希向量，检索精度略低，功能不受影响。")

    d.h1("4. 把对话搬进来")
    d.table(["来源", "操作"], [
        ["DeepSeek Harness", "「导入」→ Harness → 会话列表里点选（可多选）→ 导入选中会话"],
        ["DeepSeek 网页版", "「导入」→ 网页版 → 填 token 或粘 cURL → 列出会话 → 多选导入"],
        ["已有记忆体", "把 .json 文件拖进窗口任意位置"],
        ["截图 / 图片", "把图片拖进窗口（可多张），自动识别文字"],
        ["手动粘贴", "「导入」→ 粘贴，按「用户/助手」分段"],
    ], [3.6, 11.8])
    d.para("导入是后台跑的：右上角显示待处理条数，整理完会变成 0。")

    d.h1("5. 记忆库")
    d.h2("两层")
    d.bullet("事件记忆：一件件事的卡片。标题、日期、轮数、情绪流转、第一人称日记、未完成线头。")
    d.bullet("对话记忆：一问一答原文。可按关键词搜、可深度回忆、分页加载。")
    d.h2("操作")
    d.bullet("点卡片：看这件事的日记、关系备注、没说完的事，以及下辖的每条对话与图片。")
    d.bullet("勾选两条 → 合并：把后一条并进前一条。")
    d.bullet("底部「再加载 20 个事件」：事件多了分页显示。")
    d.h2("进度")
    d.para("还有事件在整理时，列表上方会出现进度条：「正在写事件日记… 12 / 31」，每 3 秒刷新。")

    d.h1("6. 图片与截图")
    d.bullet("把截图拖进窗口即可，支持 png / jpg / webp / gif，单张 ≤ 12MB，可一次多张。")
    d.bullet("每张图会建一条对话记忆，图片挂在它下面。")
    d.bullet("填了千问 key 的话，后台会读出图里的字，补进那条记忆并重新总结。")
    d.bullet("事件详情里能看到缩略图；续命包会带一句「他给你看过的东西」。")

    d.h1("7. AI 对话")
    d.para("「AI 对话」页直接聊。每轮会先检索当前记忆体，再回答；聊完的问答自动写回记忆体。")
    d.para("问法越具体越好：「上次那个 PDF 代码块是怎么修的」比「你还记得那个吗」更容易命中。")

    d.h1("8. 人格锚点（说话方式）")
    d.bullet("「设置」里可以总结一套语气：称呼、态度、格式习惯、禁忌。")
    d.bullet("可手选最多 8 条对话作为语气样例。")
    d.bullet("「按现在语气重写」会让所有总结改腔调，事实不变。")

    d.h1("9. 续命包")
    d.bullet("「续命包」页有两种组装：按原文，或按事件（推荐）。")
    d.bullet("按事件 = 最近事件的日记 + 事件之间的过渡 + 没说完的线头 + 情绪 + 看过的图。")
    d.bullet("右下方显示字数与 token 估算，点复制，粘到新窗口第一句。")
    d.quote("示例：\n【你们最近在做什么】\n1. 约定好的五个字：那天他忽然说要试一个约定……\n"
            "【没说完的】\n- 他还没发出“当时明月在”\n【他现在的状态】\n期待")

    d.h1("10. DSH 插件（记忆桥）")
    d.para("装好插件后，DeepSeek Harness 的「设置」里会多一页「记忆桥」。")
    d.code('dsh plugin --profile desktop add "link:C:\\Users\\user\\Desktop\\dsh-plugin-memory-bridge"')
    d.h2("用法")
    d.bullet("刷新记忆体 → 选一个 → （可选）绑定某个对话窗口 → 保存并启用。")
    d.bullet("启用后，每轮把记忆写进系统提示词，聊天记录里看不到。")
    d.bullet("勾着「接管上下文」时，旧历史不再发送，只发当前这一句。")
    d.bullet("点「停用」立刻失效。")
    d.h2("注意")
    d.bullet("插件改动后需要重启 DSH 才生效。")
    d.bullet("没选记忆体时保存会被拦下（spaceId=0 视为未选）。")

    d.h1("11. 设置与维护")
    d.bullet("重新索引：模型或向量换了以后，让旧记忆重新算一遍。")
    d.bullet("重建事件：对很久以前导入的记忆体重新划分（会保留人工改过的事件）。")
    d.bullet("导入/导出：记忆体可导出 .json 备份或搬到另一台机器。")

    d.h1("12. 数据在哪、怎么备份")
    d.code("server\\data\\memory.mv.db     记忆库\n"
           "server\\data\\llm.json        密钥\n"
           "server\\data\\media\\          图片")
    d.para("备份：关掉窗口，整个 server\\data 目录复制走即可。恢复：覆盖回去再打开。")

    d.h1("13. 常见问题")
    d.table(["现象", "处理"], [
        ["窗口打不开 / 一闪而过", "可能端口被占：关掉其它实例再开"],
        ["API 保存没反应", "确认是在窗口里点的保存；重启窗口再试"],
        ["导入后一直不整理", "检查 key 是否可用；右上角「测试连接」"],
        ["图片里的字没识别", "没填千问 OCR key，或识别的图是纯照片没有文字"],
        ["插件设置页没出现", "重启 DSH；确认 dsh plugin list 里有该插件"],
        ["插件显示未启用", "在记忆桥里选记忆体后点「保存并启用」"],
    ], [4.6, 10.8])

    d.h1("14. 隐私与安全")
    d.bullet("对话与图片只存在本机 server\\data，不上传任何服务器。")
    d.bullet("只有总结、向量、OCR 会把对应文本或图片发给你配置的服务商。")
    d.bullet("服务只监听本机 127.0.0.1，不要直接暴露到公网。")
    d.end()

    return os.path.join(OUT_DIR, "3.0 使用手册.docx")


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    project_doc()
    manual_doc()
