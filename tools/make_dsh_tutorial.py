# -*- coding: utf-8 -*-
"""生成《DSH 插件编写教程》docx。"""

import os

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

GOLD = RGBColor(0xB4, 0x8F, 0x4E)
DARK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x6B, 0x6B, 0x6B)
CODE = RGBColor(0x2B, 0x2B, 0x2B)
BODY = "宋体"
HEAD = "微软雅黑"

OUT = r"C:\Users\user\Desktop\DSH 插件编写教程.docx"


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
sec.top_margin, sec.bottom_margin = Cm(2.0), Cm(1.9)
sec.left_margin, sec.right_margin = Cm(2.1), Cm(2.1)

normal = doc.styles["Normal"]
normal.font.name = BODY
normal.font.size = Pt(10)
normal.font.color.rgb = DARK
east_asia(normal, BODY)
normal.paragraph_format.line_spacing = 1.5
normal.paragraph_format.space_after = Pt(5)

for name, size in (("Heading 1", 15), ("Heading 2", 11.5), ("Heading 3", 10.5)):
    st = doc.styles[name]
    st.font.name = HEAD
    st.font.size = Pt(size)
    st.font.color.rgb = DARK
    st.font.bold = True
    east_asia(st, HEAD)
    st.paragraph_format.space_before = Pt(14 if name == "Heading 1" else 8)
    st.paragraph_format.space_after = Pt(5)


def h1(t):
    doc.add_heading(t, level=1)


def h2(t):
    doc.add_heading(t, level=2)


def para(t):
    return doc.add_paragraph(t)


def bullet(t):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.4
    p.add_run(t)


def code(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.35)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(7)
    p.paragraph_format.line_spacing = 1.15
    r = p.add_run(t)
    r.font.name = "Consolas"
    r.font.size = Pt(8.2)
    r.font.color.rgb = CODE
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), "F4F2EE")
    pPr.append(shd)


def note(t):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(8)
    r = p.add_run("提示　" + t)
    r.font.size = Pt(9)
    r.font.color.rgb = RGBColor(0x8A, 0x70, 0x40)


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
            r.font.size = Pt(8.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# ---------------- 封面 ----------------
p = doc.add_paragraph()
r = p.add_run("DSH 插件编写教程")
r.font.name = "Georgia"
r.font.size = Pt(26)
r.bold = True

p = doc.add_paragraph()
r = p.add_run("从零写一个 DeepSeek Harness 插件：宿主侧、设置页、上下文注入")
r.font.name = HEAD
r.font.size = Pt(12)
r.font.color.rgb = GOLD
east_asia(r, HEAD)

p = doc.add_paragraph()
r = p.add_run("2026-10-27　·　以 dsh-memory-bridge 为实例")
r.font.size = Pt(9)
r.font.color.rgb = GREY

doc.add_paragraph()

h1("0. 先弄清楚三件事")
para("DSH 插件是一个普通的 npm 包，被安装到某个 profile 的 node_modules 里，"
     "启动时由 bundle patch 插进配置树。它有两个半边：")
bullet("宿主侧（lib/index.js）：跑在 Harness 主进程里，能注册 HTTP 路由、读配置、挂事件钩子。")
bullet("客户端侧（lib/client.js）：跑在 Harness 的界面里，能往「设置」里插一页。")
bullet("两边通过 HTTP 通信，插件自己定义路由，客户端用 fetch 调。")
note("没有构建步骤也行：写成 ES Module 直接跑，不需要 TypeScript、不需要打包器。")

h1("1. 五分钟：最小可跑插件")
h2("1.1 目录")
code("dsh-hello/\n"
     "├─ package.json\n"
     "├─ cordis.patch.yml\n"
     "├─ lib/index.js      宿主\n"
     "├─ lib/client.js     客户端（可选）\n"
     "└─ README.md")

h2("1.2 package.json")
code("""{
  "name": "dsh-hello",
  "version": "0.1.0",
  "type": "module",
  "main": "lib/index.js",
  "exports": {
    ".": "./lib/index.js",
    "./client": "./lib/client.js",
    "./package.json": "./package.json"
  },
  "dsh": {
    "bundle": { "patch": "./cordis.patch.yml" },
    "client": { "inject": [], "platform": "web" }
  },
  "files": ["lib", "cordis.patch.yml", "README.md"]
}""")
bullettxt = [
    "name 必须和安装后的包名一致，否则表现为「装上了但设置页 404」。",
    "dsh.bundle.patch 指向 patch 文件，DSH 启动时据此把插件插进配置树。",
    "dsh.client.platform = \"web\" 才会加载客户端半边；没有客户端就删掉这段。",
]
for b in bullettxt:
    bullet(b)

h2("1.3 cordis.patch.yml")
code("- insert:\n"
     "    - id: dsh-hello\n"
     "      name: dsh-hello")
para("id 和 name 都写包名。DSH 的 Loader 读这个文件，把插件挂到 profile 的配置树上；"
     "需要覆盖配置时在同一行加 config: {...}。")

h2("1.4 宿主：lib/index.js")
code("""export default {
  name: 'dsh-hello',
  inject: ['webServer'],
  apply(ctx) {
    const web = ctx.webServer ?? ctx.get('webServer')
    if (!web) return

    web.register({
      kind: 'exact',
      path: '/dsh-hello/api/ping',
      handler: async (req, res) => {
        res.setHeader('content-type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ ok: true, at: Date.now() }))
      },
    })
  },
}""")
para("这就是全部。inject 里列出你要用的服务，cordis 会等它们就绪再调用 apply；"
     "服务名写错会表现为 apply 永远不执行——不报错、也没日志，这是最常见的第一道坎。")

h2("1.5 安装与验证")
code("dsh plugin --profile desktop add \"link:C:\\\\path\\\\to\\\\dsh-hello\"\n"
     "dsh plugin --profile desktop list        # 能看到 dsh-hello@link:...")
para("装完重启 DSH，然后：")
code("curl http://127.0.0.1:<DSH端口>/dsh-hello/api/ping")
note("返回 401 说明路由已经注册、被 DSH 的信任栅栏挡住了（这是正常现象，见第 5 节）；"
     "返回 404 说明路由没挂上，通常是 inject 或 path 写错。")

h1("2. 宿主侧能做什么")
h2("2.1 注册路由")
para("webServer.register 接受 { kind, path, handler }。kind 用 'exact' 表示精确匹配。"
     "handler 是标准的 Node http 风格：(req, res)。")
code("web.register({\n"
     "  kind: 'exact',\n"
     "  path: '/dsh-hello/api/save',\n"
     "  handler: async (req, res) => {\n"
     "    const chunks = []\n"
     "    for await (const c of req) chunks.push(c)\n"
     "    const body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')\n"
     "    res.setHeader('content-type', 'application/json')\n"
     "    res.end(JSON.stringify({ got: body }))\n"
     "  },\n"
     "})")
note("同一个 path 只注册一次，在 handler 里按 req.method 分派。"
     "注册两次（GET 一个、POST 一个）时，宿主按首个匹配处理，第二个永远收不到请求——会表现为 405。")

h2("2.2 读写配置")
para("插件一般把配置放在 ~/.dsh/<插件名>.config.json。用 fs 直接读写最省事，"
     "注意读写都要容错（文件不存在、JSON 损坏）。")
code("import fs from 'node:fs'\n"
     "import os from 'node:os'\n"
     "import path from 'node:path'\n"
     "\n"
     "const FILE = path.join(process.env.DSH_HOME || path.join(os.homedir(), '.dsh'), 'dsh-hello.config.json')\n"
     "\n"
     "function readConfig() {\n"
     "  try { return { enabled: false, ...JSON.parse(fs.readFileSync(FILE, 'utf8')) } }\n"
     "  catch { return { enabled: false } }\n"
     "}\n"
     "\n"
     "function writeConfig(next) {\n"
     "  fs.mkdirSync(path.dirname(FILE), { recursive: true })\n"
     "  fs.writeFileSync(FILE, JSON.stringify(next, null, 2) + '\\n')\n"
     "}")
note("用 PowerShell 手改过这个文件的话，务必存成无 BOM 的 UTF-8；带 BOM 会让 JSON.parse 失败，"
     "而失败被 catch 吞掉后表现为「配置莫名其妙变回默认值」。")

h2("2.3 往系统提示词里加东西")
para("ctx.systemPrompt.context() 注册一段会进入 system prompt 的文本。text 可以是函数，"
     "每次组装时重新求值，所以能读到最新的外部状态。")
code("ctx.systemPrompt.context({\n"
     "  name: 'hello',\n"
     "  order: 900,\n"
     "  text: () => (readConfig().enabled ? '你记得昨天的事。' : ''),\n"
     "})")
para("这段内容不会出现在聊天记录里，模型却能看到。想按会话区分，就在 text 里读当前会话 id"
     "（见 2.5），别把不同窗口的记忆混在一起。")

h2("2.4 监听请求前（改写这一轮的消息）")
para("agent/pre-step 是 waterfall 事件：拿到 next() 的结果，可以返回一个改写过的 decision。"
     "这是唯一能改「这一轮到底发什么给模型」的地方。")
code("ctx.on('agent/pre-step', async (payload, next) => {\n"
     "  const decision = await next()\n"
     "  try {\n"
     "    if (!decision || decision.kind === 'reject') return decision\n"
     "    if (payload.signal?.aborted) return decision\n"
     "    if (!Array.isArray(decision.messages) || decision.messages.length === 0) return decision\n"
     "\n"
     "    // 只保留最后一条（当前输入），旧历史不再发送\n"
     "    return { ...decision, messages: [decision.messages.at(-1)] }\n"
     "  } catch (e) {\n"
     "    return decision            // 出错就原样放行，别弄坏会话\n"
     "  }\n"
     "}, { prepend: true })")
para("三条铁律：")
bullet("返回时一定展开 decision（{ ...decision, messages }），否则 startsRequestSeries 之类的字段会丢。")
bullet("任何异常都要吞掉并返回原 decision。插件出错不应该让用户聊不了天。")
bullet("payload.agent.session.id 是当前会话 id，用它做「只对某个窗口生效」的判断。")
note("agent/request 监听器不能改消息，只有 pre-step 能。")

h2("2.5 生命周期")
para("apply 期间注册的东西，用 ctx.effect 包一层返回清理函数，插件卸载/重载时才会被回收。")
code("ctx.effect(() => {\n"
     "  const timer = setInterval(tick, 2000)\n"
     "  return () => clearInterval(timer)\n"
     "})")

h1("3. 客户端侧：往设置里插一页")
para("客户端半边不是普通模块，要用 DSH 的模块加载器包装。文件必须叫 lib/client.js，"
     "并在 package.json 的 exports 里暴露 \"./client\"。")
code("""window.__ModuleLoader__.load({
  id: 'dsh-hello',
  factory: (require) => {
    const module = { exports: {} }
    const exports = module.exports
    Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' })

    const React = require('react')
    const h = React.createElement

    function Settings() {
      const [msg, setMsg] = React.useState('')
      return h('div', { style: { maxWidth: 720 } },
        h('p', null, '点一下按钮试试'),
        h('button', {
          onClick: async () => {
            const r = await fetch('/dsh-hello/api/ping').then((x) => x.json())
            setMsg('宿主说：' + JSON.stringify(r))
          },
        }, '调用宿主'),
        h('p', null, msg),
      )
    }

    exports.apply = (ctx) => {
      ctx.effect(() =>
        ctx.slots.inject('settings.section', () =>
          ctx.slots.register(
            { name: 'settings.section', id: 'dsh-hello', order: 7, label: () => '示例' },
            Settings,
          ),
        ),
      )
    }
    exports.inject = ['slots']
    return module.exports
  },
})""")
para("要点：")
bullet("id 用短横线小写，别和别的插件撞。")
bullet("order 决定在设置列表里的位置，数值小的靠前。")
bullet("label 是函数，返回显示名，方便以后做多语言。")
bullet("组件里只用 require('react')，不要引第三方库——客户端没有打包器。")

h1("4. 安装、调试、发布")
h2("4.1 安装方式")
table(["方式", "命令", "适用"], [
    ["本地链接", 'dsh plugin --profile desktop add "link:C:\\path\\to\\plugin"', "开发期，改完不用重装"],
    ["本地目录", 'dsh plugin --profile desktop add "file:C:\\path\\to\\plugin"', "需要固定副本"],
    ["git 仓库", "dsh plugin --profile desktop add github:user/repo", "给别人装"],
    ["npm 包", "dsh plugin --profile desktop add dsh-hello", "发布后"],
], [2.6, 8.6, 5.0])

h2("4.2 什么时候要重启")
table(["改动", "是否要重启 DSH"], [
    ["宿主 lib/index.js", "要（模块只在启动时加载一次）"],
    ["客户端 lib/client.js", "要（客户端 bundle 只在启动时构建）"],
    ["cordis.patch.yml / package.json", "要"],
    ["配置文件（json）", "不用，下次读取即生效"],
], [6.0, 9.4])

h2("4.3 看日志")
para("宿主侧的 console.log 会进 DSH 的进程输出。启动失败时，先去控制台看有没有 "
     "[你的插件名] 开头的行；没有这行，说明 apply 根本没被调用，先查 inject 和 patch 的 name。")

h1("5. 信任栅栏：为什么插件页面打不开")
para("DSH 的 Web 端口是回环地址，但浏览器里任何一个网页都能向回环发请求。"
     "所以宿主对所有请求做一道栅栏（connection.requestRejection）。")
para("结果是：插件自己注册的页面路径，在浏览器里直接打开通常是 401/403。"
     "这不是你写错了，是设计如此。可行做法有三种：")
table(["做法", "说明", "代价"], [
    ["用客户端 section", "设置里插一页，同源请求能过栅栏", "需要写 client.js，界面只能用 React 手搓"],
    ["插件自带小服务", "在 127.0.0.1 另起一个端口提供页面与 API", "多一个端口；记得只监听回环"],
    ["两者都做", "section 为主，自带服务兜底", "代码多一点，但最稳"],
], [3.4, 7.6, 5.2])

h1("6. 实战踩过的坑")
table(["现象", "真正的原因", "怎么改"], [
    ["装上了但设置页 404", "package.json 的 name 与 patch 里的 name 不一致", "两处都改成同一个包名"],
    ["apply 不执行、也不报错", "inject 里写了不存在的服务名", "核对服务名；拿不准就先不写 inject"],
    ["POST 一直 405", "同一 path 注册了两次 handler", "合并成一个，按 req.method 分派"],
    ["配置老是变回默认", "配置文件存成了带 BOM 的 UTF-8", "用无 BOM 的 UTF-8 重写"],
    ["改了代码没生效", "只重启了窗口，没重启 DSH", "宿主/客户端改动都要重启"],
    ["聊天里出现一大段注入文本", "把上下文塞进了 messages", "改用 systemPrompt.context()"],
    ["模型把注入内容当成提问", "注入的是一条独立 user 消息", "要么并进当前这条，要么走系统提示词"],
    ["会话被插件弄崩", "pre-step 里抛异常或没展开 decision", "try/catch 返回原 decision，返回时展开"],
], [4.2, 6.4, 5.6])

h1("7. 参考与借鉴")
para("DSH 自带一份插件开发文档，在安装目录里：")
code("resources/app.asar → dsh/node_modules/@deepseek-ai/dsh-agent-preset/skills/\n"
     "  cordis-plugin-development/\n"
     "    SKILL.md\n"
     "    references/host-plugin.md      宿主插件与 bundle\n"
     "    references/ui-plugin.md        客户端 section\n"
     "    references/practices.md        钩子、事件、坑\n"
     "    references/verification.md     装完怎么验")
para("app.asar 是打包文件，可以用 Node 直接读：前 16 字节是 pickle 头，"
     "第 12 字节起的 4 字节是 JSON 头长度，之后是文件表与内容。写个几十行的解包脚本就能把上面的文档取出来。")
para("另外，已安装的插件就是最好的例子。看别人怎么写：")
code("~/.dsh/profiles/desktop/node_modules/<插件名>/lib/index.js\n"
     "~/.dsh/profiles/desktop/node_modules/<插件名>/cordis.patch.yml\n"
     "~/.dsh/profiles/desktop/node_modules/<插件名>/package.json")

h1("8. 一份可抄的检查清单")
bullet("package.json 的 name == patch 里的 name == 客户端 section 的 id（可以不同，但别撞）")
bullet("inject 只列真正要用的服务，写错会静默失效")
bullet("所有路由 try/catch，出错返回 JSON 而不是挂起连接")
bullet("配置读写都容错，文件不存在要能跑")
bullet("pre-step 里任何异常都返回原 decision")
bullet("注入上下文优先用 systemPrompt.context()，别往聊天里塞消息")
bullet("改完宿主或客户端，重启 DSH 再验证")
bullet("装完先看 dsh plugin list，再看控制台有没有 [插件名] 日志")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("— 完 —")
r.font.color.rgb = GOLD
r.font.size = Pt(10)

doc.save(OUT)
print("saved", OUT)
