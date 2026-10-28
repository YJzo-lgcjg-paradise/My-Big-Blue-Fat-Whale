// 09 段：用截图轮询代替 screencast，避免页面不重绘导致只有一帧
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const DIR = "C:\\Users\\user\\Desktop\\My Big Blue Fat Whale · Forever\\build\\video4\\09";
const PORT = 9809;
fs.mkdirSync(DIR, { recursive: true });

const chrome = spawn(CHROME, ["--headless=new","--disable-gpu","--hide-scrollbars",`--remote-debugging-port=${PORT}`,`--user-data-dir=${path.join(DIR,"profile")}`,"--window-size=1440,900","about:blank"], { stdio: "ignore" });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let ws;
for (let i=0;i<40 && !ws;i++){ try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json(); ws = l.find(t=>t.type==="page")?.webSocketDebuggerUrl; } catch {} if(!ws) await sleep(300); }
const sock = new WebSocket(ws); let seq=0; const pending=new Map();
sock.onmessage = e => { const m=JSON.parse(e.data); if(m.id&&pending.has(m.id)){pending.get(m.id)(m.result);pending.delete(m.id);} };
await new Promise(r=>sock.onopen=r);
const send=(method,params={})=>new Promise(res=>{const id=++seq;pending.set(id,res);sock.send(JSON.stringify({id,method,params}));});
const run = async (expr) => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;

await send("Page.enable");
await send("Page.navigate", { url: "http://127.0.0.1:8793/" });
await sleep(3800);
console.log("title:", await run("document.title"));

const meta = [];
let i = 0;
const totalMs = 15000;
const startTs = Date.now() / 1000;
while ((Date.now() / 1000 - startTs) * 1000 < totalMs) {
  const t = (Date.now() / 1000 - startTs) / (totalMs / 1000); // 0..1
  await run(`window.scrollTo({top: ${Math.round(t * 330)}, behavior: 'auto'}); true`);
  await sleep(240);
  const shot = await send("Page.captureScreenshot", { format: "jpeg", quality: 82 });
  const f = path.join(DIR, `f-${String(i).padStart(5, "0")}.jpg`);
  fs.writeFileSync(f, Buffer.from(shot.data, "base64"));
  meta.push({ file: f, ts: startTs + i * 0.25 });
  i++;
}
fs.writeFileSync(path.join(DIR, "frames.json"), JSON.stringify(meta), "utf8");
console.log("09 frames:", meta.length);
chrome.kill();
process.exit(0);
