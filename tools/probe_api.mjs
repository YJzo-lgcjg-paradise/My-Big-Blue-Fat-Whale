import { spawn } from "node:child_process";
import fs from "node:fs";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\probe3";
const PORT = 9604;
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
await sleep(3000);
await run(`document.querySelector('.intro-enter-btn')?.click(); true`);
await sleep(4000);

console.log("api:", JSON.stringify(await run(`fetch('/api/spaces').then(r=>r.json()).then(j=>({n:(j.spaces||[]).length, first:(j.spaces||[])[0]}))`)));
console.log("ls:", JSON.stringify(await run(`Object.fromEntries(Object.entries(localStorage))`)));
console.log("ss:", JSON.stringify(await run(`Object.fromEntries(Object.entries(sessionStorage))`)));

chrome.kill();
process.exit(0);
