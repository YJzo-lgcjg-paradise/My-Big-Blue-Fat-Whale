// 探测：进入主界面后，找到「记忆库」入口并点开
import { spawn } from "node:child_process";
import fs from "node:fs";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\probe2";
const PORT = 9603;
fs.mkdirSync(DIR, { recursive: true });

const chrome = spawn(CHROME, [
  "--headless=new", "--disable-gpu", "--no-first-run",
  `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${DIR}\\profile`,
  "--window-size=1440,900", "about:blank",
], { stdio: "ignore" });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
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
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
};
await new Promise((r) => (ws.onopen = r));
const send = (method, params = {}) => new Promise((res) => {
  const id = ++seq;
  pending.set(id, res);
  ws.send(JSON.stringify({ id, method, params }));
});
const run = async (expr) => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;

await send("Page.enable");
await send("Page.navigate", { url: "http://127.0.0.1:8790/" });
await sleep(3500);
await run(`document.querySelector('.intro-enter-btn')?.click(); true`);
await sleep(4000);

console.log("nav texts:", await run(`[...document.querySelectorAll('a,button,li,div[role=button]')].map(x=>(x.textContent||'').trim()).filter(t=>t && t.length<12).slice(0,30)`));

// 找「记忆库」并点
const clicked = await run(`(() => {
  const el = [...document.querySelectorAll('a,button,li,div')].find(x => (x.textContent||'').trim() === '记忆库' && x.children.length === 0);
  if (el) { el.click(); return true; }
  const el2 = [...document.querySelectorAll('a,button,li')].find(x => (x.textContent||'').trim() === '记忆库');
  if (el2) { el2.click(); return true; }
  return false;
})()`);
console.log("clicked 记忆库:", clicked);
await sleep(3000);
console.log("state:", await run(`(() => ({
  chips: document.querySelectorAll('.chip').length,
  cards: document.querySelectorAll('.ep-card').length,
  hash: location.hash,
  title: (document.querySelector('h1,h2')?.textContent || '').slice(0, 20)
}))()`));

chrome.kill();
process.exit(0);
