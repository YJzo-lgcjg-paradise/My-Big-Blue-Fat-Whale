/**
 * 用 Chrome DevTools Protocol 点几下界面并截图。
 * 前置：Chrome 以 --remote-debugging-port=9222 打开 http://127.0.0.1:5173
 */
import fs from "node:fs";
import path from "node:path";

const OUT = path.resolve(".shots");
fs.mkdirSync(OUT, { recursive: true });

const targets = await fetch("http://127.0.0.1:9222/json").then((r) => r.json());
const page = targets.find((t) => t.type === "page");
if (!page) throw new Error("找不到页面");

const ws = new WebSocket(page.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg.result);
    pending.delete(msg.id);
  }
};
await new Promise((r) => (ws.onopen = r));

const send = (method, params = {}) =>
  new Promise((res) => {
    const id = ++seq;
    pending.set(id, res);
    ws.send(JSON.stringify({ id, method, params }));
  });

const run = async (expr) => {
  const r = await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true });
  return r?.result?.value;
};
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const shot = async (name) => {
  const r = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
  fs.writeFileSync(path.join(OUT, name), Buffer.from(r.data, "base64"));
  console.log("shot", name);
};

await send("Page.enable");
await send("Runtime.enable");

await run("location.hash='#library'");
await wait(1500);
await shot("01-library.png");

await run("document.querySelector('.mem')?.click()");
await wait(700);
await shot("02-drawer.png");
await run("document.querySelector('.close')?.click()");
await wait(400);

await run("location.hash='#import'");
await wait(900);
await run("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('填入示例'))?.click()");
await wait(400);
await shot("03-import.png");

await run("location.hash='#bundle'");
await wait(700);
await run("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('生成续命包'))?.click()");
await wait(2500);
await shot("04-bundle.png");

await run("location.hash='#settings'");
await wait(700);
await shot("05-settings.png");

// 检索演示：填问题、点回忆
await run("location.hash='#library'");
await wait(900);
await run(`(() => {
  const input = document.querySelector('.search input');
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(input, '上次 PDF 代码块分页怎么解决的');
  input.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()`);
await wait(200);
await run("document.querySelector('.search + .switch')?.click()");
await wait(200);
await run("[...document.querySelectorAll('button.btn')].find(b=>b.textContent.includes('回忆'))?.click()");
await wait(2000);
await shot("06-recall.png");

ws.close();
