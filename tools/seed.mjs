/**
 * 演示数据：导入一段带时间戳的历史对话，然后跑一次检索与续命包。
 * 用法：node tools/seed.mjs
 */
const BASE = process.env.BASE || "http://127.0.0.1:8787";

const transcript = `[2026-09-18 10:12]
用户：本机 MySQL 的端口为什么不是 3306？
助手：因为 3306 已经被一个旧实例占用了，paradise 这套服务改到了 3308。连接串必须同步写成 jdbc:mysql://localhost:3308/paradise，application.yml 里的 serverTimezone 也不能省，否则时间会偏 8 小时。

[2026-10-02 15:40]
用户：给文档做封面，想要那种很克制的风格。
助手：用宋体正文、金色竖条标题、米色表头就够了。金色选 #B08D4F，标题左侧用 3pt 竖线，页眉放小字，页脚写"第 X 页 / 共 N 页"。不要大色块，也不要用无衬线标题。

[2026-10-10 09:05]
用户：DeepSeek 网页版聊久了会变慢吗？
助手：会。上下文越长，prefill 越久，首字延迟和费用一起上涨。单会话还能靠缓存顶一部分，跨会话就只能靠外部记忆了。五十轮之后一次请求大概两万五千 token。

[2026-10-20 21:30]
用户：我想让 AI 记住以前聊过的东西，但不要每次都把全文塞进去。
助手：把历史切成一条条总结，总结进向量库，回答前先检索相关总结，只有需要精确细节时才回捞原文。总结是索引不是答案，原文必须能查回来。

[2026-10-24 14:20]
用户：导出的 PDF 里代码块总是从中间断开。
助手：给代码块设置整块不跨页，或者把长 SQL 拆成两段。LibreOffice 导出时按段落分页，keep_together 打开之后就不会被截断。

[2026-10-26 11:00]
用户：记忆要不要模拟人的遗忘？
助手：要，但要能找回来。用时间衰减降低旧记忆的权重，用户提醒或者深度回忆时再全库扫一遍，这样既像人，又不会真的丢东西。`;

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(`${path} ${res.status}: ${JSON.stringify(data)}`);
  return data;
}

const imp = await post("/api/import", { text: transcript, sourceName: "演示 · 历史对话" });
console.log("导入：", imp.total, "条，importId =", imp.importId);

for (let i = 0; i < 60; i++) {
  await new Promise((r) => setTimeout(r, 1000));
  const p = await fetch(`${BASE}/api/imports/${imp.importId}`).then((r) => r.json());
  process.stdout.write(`\r进度 ${p.done}/${p.total}  `);
  if (p.status === "done") break;
}
console.log("");

const rec = await post("/api/recall", { query: "PDF 里的代码块为什么会被截断", deep: false });
console.log("\n检索「PDF 里的代码块为什么会被截断」：");
for (const m of rec.items.slice(0, 4)) {
  console.log(` ${m.score}  ${m.occurredAt.slice(0, 10)}  ${m.summary.slice(0, 40)}…`);
}

const b = await post("/api/bundle", { query: "我要在新对话里继续问排版的事", maxChars: 3000 });
console.log(`\n续命包：${b.bundle.count} 条 / ${b.bundle.chars} 字\n`);
console.log(b.bundle.text);
