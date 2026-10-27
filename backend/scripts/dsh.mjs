/**
 * DeepSeek Harness 会话桥：读取 ~/.dsh/sessions 下的 .jsonl.zstd。
 * 用法：
 *   node dsh.mjs list
 *   node dsh.mjs export <sessionId>
 * 输出 JSON 到 stdout。
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import zlib from "node:zlib";

const ROOT = path.join(os.homedir(), ".dsh", "sessions");
const MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd]);

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name === "session.v4.jsonl.zstd") out.push(p);
  }
  return out;
}

/** DSH 是一个文件里追加多个 zstd 帧，需要按帧头切开逐个解。 */
function decompress(file) {
  const buf = fs.readFileSync(file);
  const starts = [];
  let i = 0;
  while ((i = buf.indexOf(MAGIC, i)) !== -1) {
    starts.push(i);
    i += 4;
  }
  let text = "";
  for (let k = 0; k < starts.length; k++) {
    const end = k + 1 < starts.length ? starts[k + 1] : buf.length;
    try {
      text += zlib.zstdDecompressSync(buf.subarray(starts[k], end)).toString("utf8");
    } catch {
      /* 跳过坏帧 */
    }
  }
  return text;
}

function records(file) {
  return decompress(file)
    .split("\n")
    .filter((l) => l.trim())
    .map((l) => {
      try {
        return JSON.parse(l);
      } catch {
        return null;
      }
    })
    .filter(Boolean);
}

const textOf = (content) =>
  (content || [])
    .filter((c) => c && c.type === "text" && typeof c.text === "string")
    .map((c) => c.text)
    .join("\n")
    .trim();

function summarize(file) {
  const recs = records(file);
  const head = recs.find((r) => r.type === "session") || {};
  const titleRec = [...recs].reverse().find((r) => r.type === "session/title");
  const users = recs.filter((r) => r.type === "user/message");
  const last = recs[recs.length - 1] || {};
  return {
    id: head.id || path.basename(path.dirname(file)),
    title: (titleRec && (titleRec.data?.title || titleRec.data)) || firstLine(users[0]) || "未命名会话",
    cwd: head.cwd || "",
    createdAt: head.createdAt || 0,
    updatedAt: last.time || head.createdAt || 0,
    userTurns: users.length,
  };
}

function firstLine(rec) {
  if (!rec) return "";
  const t = textOf(rec.data?.content);
  return t.split("\n")[0].slice(0, 60);
}

// 把消息归一成「一轮对话」，规则见 docs/对话切分规则：
// 多问一答 → 合并提问；一问多答 → 合并回答；无问有答 → 挂到上一轮或单独成条；
// 同一问题重复出现 → 视为重新生成，保留最后一次回答。
function imagesOf(rec) {
  const content = rec.type === "user/message" ? rec.data?.content : rec.data?.message?.content;
  const out = [];
  if (!Array.isArray(content)) return out;
  for (const part of content) {
    if (part && part.type === "image" && part.attachment) {
      out.push({
        attachmentId: part.attachment.attachmentId || "",
        mediaType: part.attachment.mediaType || "image/png",
        name: part.attachment.name || "",
      });
    }
  }
  return out;
}

function toTurns(recs) {
  const turns = [];
  let cur = null;
  const GAP = 10 * 60 * 1000;
  for (const r of recs) {
    if (r.type === "user/message") {
      const q = textOf(r.data?.content);
      if (!q) continue;
      const same = cur && cur.question.trim() === q.trim();
      if (same && cur.answer) {
        cur.answer = "";
        cur.occurredAt = r.time || cur.occurredAt;
        cur.alternates = (cur.alternates || 0) + 1;
      } else if (cur && !cur.answer) {
        cur.question = `${cur.question}\n${q}`.slice(0, 2000);
      } else {
        cur = { question: q, answer: "", occurredAt: r.time || null, seq: r.seq ?? 0, images: [] };
        turns.push(cur);
      }
      const qi = imagesOf(r);
      if (qi.length && cur) cur.images = [...(cur.images || []), ...qi];
    } else if (r.type === "assistant/message") {
      const a = textOf(r.data?.message?.content);
      if (!a) continue;
      if (!cur) {
        cur = { question: "（自主行动，承接上文）", answer: a, occurredAt: r.time || null, seq: r.seq ?? 0 };
        turns.push(cur);
      } else if (cur.answer) {
        const gap = (r.time || 0) - (cur.lastAt || cur.occurredAt || 0);
        if (gap > GAP) {
          cur = { question: "（自主行动，承接上文）", answer: a, occurredAt: r.time || null, seq: r.seq ?? 0 };
          turns.push(cur);
        } else {
          cur.answer = `${cur.answer}\n\n${a}`;
        }
      } else {
        cur.answer = a;
      }
      const ai = imagesOf(r);
      if (ai.length && cur) cur.images = [...(cur.images || []), ...ai];
      cur.lastAt = r.time || cur.lastAt || null;
    }
  }
  return turns.filter((t) => t.answer.trim().length > 0 || (t.question.trim().length > 0 && t.question !== "（自主行动，承接上文）"));
}

const cmd = process.argv[2] || "list";

if (cmd === "list") {
  const items = walk(ROOT)
    .map(summarize)
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  process.stdout.write(JSON.stringify({ ok: true, root: ROOT, sessions: items }));
} else if (cmd === "export") {
  const id = process.argv[3];
  const file = walk(ROOT).find((f) => f.includes(id));
  if (!file) {
    process.stdout.write(JSON.stringify({ ok: false, error: "会话不存在" }));
    process.exit(0);
  }
  const recs = records(file);
  const head = recs.find((r) => r.type === "session") || {};
  const titleRec = [...recs].reverse().find((r) => r.type === "session/title");
  process.stdout.write(JSON.stringify({
    ok: true,
    id: head.id,
    title: (titleRec && (titleRec.data?.title || titleRec.data)) || "DSH 会话",
    cwd: head.cwd || "",
    turns: toTurns(recs),
  }));
} else if (cmd === "dbg") {
  const id = process.argv[3];
  const file = walk(ROOT).find((f) => f.includes(id));
  if (!file) {
    process.stdout.write(JSON.stringify({ ok: false, error: "会话不存在" }));
    process.exit(0);
  }
  const recs = records(file);
  const partTypes = {};
  let imageSample = null;
  for (const r of recs) {
    if (r.type !== "user/message" && r.type !== "assistant/message") continue;
    const content = r.type === "user/message" ? r.data?.content : r.data?.message?.content;
    if (!Array.isArray(content)) continue;
    for (const part of content) {
      const k = part?.type || "unknown";
      partTypes[k] = (partTypes[k] || 0) + 1;
      if (!imageSample && /image|attachment|file/i.test(k)) {
        imageSample = JSON.stringify(part).slice(0, 600);
      }
    }
  }
  process.stdout.write(JSON.stringify({ ok: true, partTypes, imageSample }));
} else {
  process.stdout.write(JSON.stringify({ ok: false, error: "unknown command" }));
}
