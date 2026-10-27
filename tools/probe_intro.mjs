// 探测：点「折尽梅花」后，界面多久进入主界面
import { spawn } from "node:child_process";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\probe";
const PORT = 9602;

import fs from "node:fs";
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

console.log("btn exists:", await run(`!!document.querySelector('.intro-enter-btn')`));
await run(`document.querySelector('.intro-enter-btn')?.click(); true`);

for (const t of [2, 4, 6, 8, 10, 12, 15, 18]) {
  await sleep(t * 1000 - (t === 2 ? 0 : 0));
  const state = await run(`(() => ({
    intro: !!document.querySelector('.intro'),
    chips: document.querySelectorAll('.chip').length,
    text: (document.body.innerText || '').slice(0, 40).replace(/\\n/g, ' ')
  }))()`);
  console.log(t + "s", JSON.stringify(state));
  if (!state.intro && state.chips > 0) break;
}
chrome.kill();
process.exit(0);
