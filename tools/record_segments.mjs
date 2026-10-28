// 分段录制 04–09：每段独立开一个 headless Chrome，执行动作并逐帧抓屏
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const BASE = "http://127.0.0.1:8790/";
const PLUGIN = "http://127.0.0.1:8793/";
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\video4";
const chapterId = process.argv[2];
const PORT = 9700 + Number(chapterId);

fs.mkdirSync(path.join(DIR, chapterId), { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function run() {
  const chrome = spawn(CHROME, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
    "--no-default-browser-check",
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${path.join(DIR, chapterId, "profile")}`,
    "--window-size=1440,900",
    "about:blank",
  ], { stdio: "ignore" });

  let wsUrl;
  for (let i = 0; i < 40 && !wsUrl; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
      wsUrl = list.find((t) => t.type === "page")?.webSocketDebuggerUrl;
    } catch {}
    if (!wsUrl) await sleep(300);
  }
  const ws = new WebSocket(wsUrl);
  let seq = 0;
  const pending = new Map();
  const frames = [];
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
    else if (m.method === "Page.screencastFrame") {
      frames.push({ data: m.params.data, ts: m.params.metadata.timestamp });
      send("Page.screencastFrameAck", { sessionId: m.params.sessionId }).catch(() => {});
    }
  };
  await new Promise((r) => (ws.onopen = r));
  const send = (method, params = {}) => new Promise((res) => {
    const id = ++seq;
    pending.set(id, res);
    ws.send(JSON.stringify({ id, method, params }));
  });
  const run = async (expr) => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;

  await send("Page.enable");
  await send("Runtime.enable");

  const url = chapterId === "09" ? PLUGIN : BASE;
  await send("Page.navigate", { url });
  await sleep(3200);

  if (chapterId !== "09") {
    // 跳过开场
    for (let i = 0; i < 10; i++) {
      const ok = await run(`(() => { const b = document.querySelector('.intro-enter-btn'); if (b) { b.click(); return true; } return false; })()`);
      if (ok) break;
      await sleep(600);
    }
    await sleep(3800);
  }

  await send("Page.startScreencast", { format: "jpeg", quality: 82, maxWidth: 1280, maxHeight: 800, everyNthFrame: 1 });
  await sleep(400);

  const step = async (ms, expr) => {
    if (expr) { try { await run(expr); } catch (e) { console.log("eval fail:", e.message); } }
    await sleep(ms);
  };

  const go = (hash) => `location.hash = '#${hash}'; true`;
  const clickText = (t) => `(() => { const el = [...document.querySelectorAll('button, .tab, .chip, a')].find(x => (x.textContent||'').trim() === ${JSON.stringify(t)}); if (el) { el.click(); return true; } return false; })()`;

  if (chapterId === "04") {
    await step(3200, go("import"));
    await step(4200);
    await step(3600, go("library"));
    await step(3600, clickText("对话记忆 · "));
    await step(2600, clickText("珍贵记忆"));
    await step(2200, clickText("回忆"));
    await step(3600, go("bundle"));
    await step(3800);
    await step(2600, go("library"));
    await step(2500);
  } else if (chapterId === "05") {
    await step(3000, go("library"));
    await step(2400);
    await step(3600, `[...document.querySelectorAll('button')].find(x => (x.textContent||'').trim() === 'API')?.click(); true`);
    await step(4600);
    await step(2200, `document.querySelector('.modal-mask')?.click(); true`);
    await step(2600);
    await step(2600, go("import"));
    await step(3200);
  } else if (chapterId === "06") {
    await step(3000, go("library"));
    await step(2600);
    await step(3800, `document.querySelectorAll('.ep-card')[1]?.click(); true`);
    await step(3400, `(() => { const b = document.querySelector('.ep-detail-body'); if (b) b.scrollTop = 320; return true; })()`);
    await step(2800);
    await step(1800, `document.querySelector('.modal-mask')?.click(); true`);
    await step(2400, clickText("对话记忆 · "));
    await step(2200, clickText("事件记忆 · "));
    await step(3000, go("bundle"));
    await step(2400, `[...document.querySelectorAll('button')].find(x => (x.textContent||'').includes('按事件组装'))?.click(); true`);
    await step(4600);
  } else if (chapterId === "07") {
    await step(3000, go("settings"));
    await step(2400);
    await step(3600, `(() => { const h = [...document.querySelectorAll('h3')].find(x => (x.textContent||'').includes('我是谁')); if (h) h.scrollIntoView({block:'center'}); return !!h; })()`);
    await step(5200);
    await step(3000, `(() => { const b = [...document.querySelectorAll('button')].find(x => (x.textContent||'').trim() === '查看过往'); if (b) b.click(); return true; })()`);
    await step(4200);
    await step(1600, `document.querySelector('.modal-mask')?.click(); true`);
    await step(2200);
  } else if (chapterId === "08") {
    await step(3000, go("settings"));
    await step(2400);
    await step(3200, `(() => { const h = [...document.querySelectorAll('h3')].find(x => (x.textContent||'').includes('上下文模式')); if (h) h.scrollIntoView({block:'center'}); return !!h; })()`);
    await step(4200);
    await step(2600, clickText("精准优先"));
    await step(3200);
    await step(2400, clickText("预算优先"));
    await step(2600);
    await step(2200, go("library"));
    await step(2200);
  } else if (chapterId === "09") {
    await step(4200);
    await step(3400, `window.scrollTo({top: 180, behavior: 'smooth'}); true`);
    await step(3600);
    await step(2600, `window.scrollTo({top: 420, behavior: 'smooth'}); true`);
    await step(3600);
  }

  await send("Page.stopScreencast");
  await sleep(300);

  const meta = [];
  const dir = path.join(DIR, chapterId);
  for (let i = 0; i < frames.length; i++) {
    const f = path.join(dir, `f-${String(i).padStart(5, "0")}.jpg`);
    fs.writeFileSync(f, Buffer.from(frames[i].data, "base64"));
    meta.push({ file: f, ts: frames[i].ts });
  }
  fs.writeFileSync(path.join(dir, "frames.json"), JSON.stringify(meta), "utf8");
  console.log(chapterId, "frames:", meta.length, "span:", ((meta.at(-1)?.ts ?? 0) - (meta[0]?.ts ?? 0)).toFixed(1));
  chrome.kill();
  process.exit(0);
}

run().catch((e) => { console.error(e); process.exit(1); });
