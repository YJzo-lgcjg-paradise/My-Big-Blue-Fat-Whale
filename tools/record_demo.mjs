// 通过 CDP 录屏：headless Chrome 打开记忆库，按脚本操作，逐帧保存
import fs from "node:fs";
import path from "node:path";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const OUT = path.join(process.env.USERPROFILE, "Desktop", "My Big Blue Fat Whale 3.0", "..", "mbfw-demo-frames");
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\demo";
const PORT = 9601;
const URL = "http://127.0.0.1:8790/";

fs.mkdirSync(DIR, { recursive: true });

const { spawn } = await import("node:child_process");
const chrome = spawn(CHROME, [
  "--headless=new",
  "--disable-gpu",
  "--hide-scrollbars",
  "--no-first-run",
  "--no-default-browser-check",
  `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${path.join(DIR, "profile")}`,
  "--window-size=1440,900",
  "about:blank",
], { stdio: "ignore" });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getWs() {
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
      const page = list.find((t) => t.type === "page");
      if (page) return page.webSocketDebuggerUrl;
    } catch {}
    await sleep(300);
  }
  throw new Error("chrome 没起来");
}

const ws = new WebSocket(await getWs());
let seq = 0;
const pending = new Map();
const frames = [];
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) {
    pending.get(m.id)(m.result);
    pending.delete(m.id);
  } else if (m.method === "Page.screencastFrame") {
    frames.push({ data: m.params.data, ts: m.params.metadata.timestamp });
    send("Page.screencastFrameAck", { sessionId: m.params.sessionId }).catch(() => {});
  }
};
await new Promise((r) => (ws.onopen = r));
const send = (method, params = {}) =>
  new Promise((res, rej) => {
    const id = ++seq;
    pending.set(id, res);
    ws.send(JSON.stringify({ id, method, params }));
  });
const run = async (expr) => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;

await send("Page.enable");
await send("Runtime.enable");

async function step(name, expr, waitMs) {
  console.log("·", name);
  if (expr) {
    try {
      await run(expr);
    } catch (e) {
      console.log("  (eval failed)", e.message);
    }
  }
  await sleep(waitMs);
}

await send("Page.navigate", { url: URL });
await sleep(3000);
// 开场：点掉「折尽梅花」
for (let i = 0; i < 12; i++) {
  const clicked = await run(`(() => {
    const b = document.querySelector('.intro-enter-btn');
    if (b) { b.click(); return true; }
    return false;
  })()`);
  if (clicked) break;
  await sleep(600);
}
await sleep(4200);
await sleep(2600);
// 进入「记忆库」标签
for (let i = 0; i < 6; i++) {
  const ok = await run(`(() => {
    const el = [...document.querySelectorAll('button, a, .tab, li')].find(
      (x) => (x.textContent || '').trim() === '记忆库'
    );
    if (el) { el.click(); return true; }
    return false;
  })()`);
  if (ok) break;
  await sleep(700);
}
await sleep(3000);

await send("Page.startScreencast", { format: "jpeg", quality: 80, maxWidth: 1280, maxHeight: 800, everyNthFrame: 1 });
await sleep(600);

// —— 脚本 ——
await step("记忆库 · 事件层", "document.querySelector('.chip') && true", 2200);
await step("滚动一屏", "window.scrollTo({top: 420, behavior: 'smooth'}); true", 2200);
await step("点开一个事件", "document.querySelectorAll('.ep-card')[1]?.click(); true", 3200);
await step("看下辖对话", "document.querySelector('.ep-detail-body') && (document.querySelector('.ep-detail-body').scrollTop = 260); true", 2600);
await step("关闭详情", "document.querySelector('.modal-mask')?.click(); true", 1500);
await step("切到对话记忆", "document.querySelectorAll('.chip')[1]?.click(); true", 2600);
await step("回到事件层", "document.querySelectorAll('.chip')[0]?.click(); true", 1800);
await step("去续命包", "location.hash = '#bundle'; true", 2600);
await step("按事件组装", `(() => {
  const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('按事件组装'));
  if (b) b.click();
  return !!b;
})()`, 4200);
await step("滚动看结果", "window.scrollTo({top: 520, behavior: 'smooth'}); true", 2600);
await step("去导入页", "location.hash = '#import'; true", 2600);
await step("回到记忆库收尾", "location.hash = '#library'; window.scrollTo(0,0); true", 2600);

await send("Page.stopScreencast");
await sleep(400);

// 写帧
const meta = [];
for (let i = 0; i < frames.length; i++) {
  const f = path.join(DIR, `f-${String(i).padStart(5, "0")}.jpg`);
  fs.writeFileSync(f, Buffer.from(frames[i].data, "base64"));
  meta.push({ file: f, ts: frames[i].ts });
}
fs.writeFileSync(path.join(DIR, "frames.json"), JSON.stringify(meta), "utf8");
console.log("frames:", meta.length, "duration≈", ((meta.at(-1).ts - meta[0].ts) / 1000).toFixed(1), "s");

chrome.kill();
process.exit(0);
