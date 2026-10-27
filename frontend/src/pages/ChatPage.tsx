import { useEffect, useRef, useState } from "react";
import { api, type ChatMessage, type Space } from "../api";

type Props = {
  spaces: Space[];
  spaceId: number;
  onPickSpace: (id: number) => void;
  toast: (msg: string) => void;
};

const storeKey = (spaceId: number) => `mbfw.chat.${spaceId}`;

function loadThread(spaceId: number): ChatMessage[] {
  try {
    const raw = localStorage.getItem(storeKey(spaceId));
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function saveThread(spaceId: number, list: ChatMessage[]) {
  try {
    localStorage.setItem(storeKey(spaceId), JSON.stringify(list.slice(-40)));
  } catch {
    /* 存不下就算了，不挡聊天 */
  }
}

const QUICK = [
  "我们之前聊过什么？",
  "上次那个问题最后怎么解决的？",
  "帮我把相关的记忆串一下",
];

/** 极简 markdown：只处理 **加粗**，够用了 */
function Rich({ text }: { text: string }) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return (
    <>
      {parts.map((p, i) =>
        p.startsWith("**") && p.endsWith("**") ? (
          <strong key={i} style={{ color: "var(--gold)", fontWeight: 500 }}>
            {p.slice(2, -2)}
          </strong>
        ) : (
          <span key={i}>{p}</span>
        )
      )}
    </>
  );
}

export function ChatPage({ spaces, spaceId, onPickSpace, toast }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>(() => loadThread(spaceId));
  const [threadSpace, setThreadSpace] = useState(spaceId);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [meta, setMeta] = useState<{ model: string; memoryUsed: number; saved?: boolean; searchQuery?: string; styled?: boolean } | null>(null);
  const boxRef = useRef<HTMLDivElement>(null);
  const lastUser = [...messages].reverse().find((m) => m.role === "user")?.content || "";

  // 切记忆体就换一条对话线，各自保留各自的上下文
  if (spaceId !== threadSpace) {
    setThreadSpace(spaceId);
    setMessages(loadThread(spaceId));
    setMeta(null);
  }

  useEffect(() => {
    saveThread(threadSpace, messages);
  }, [messages, threadSpace]);

  useEffect(() => {
    if (boxRef.current) boxRef.current.scrollTop = boxRef.current.scrollHeight;
  }, [messages, busy]);

  async function send(text?: string) {
    const content = (text ?? input).trim();
    if (!content || busy) return;
    const next: ChatMessage[] = [...messages, { role: "user", content }];
    setMessages(next);
    setInput("");
    setBusy(true);
    try {
      const res = await api.chat(spaceId, next.slice(-20));
      setMessages([...next, { role: "assistant", content: res.reply }]);
      setMeta({ model: res.model, memoryUsed: res.memoryUsed, saved: res.saved, searchQuery: res.searchQuery, styled: res.styled });
    } catch (e: any) {
      toast(e.message || "对话失败");
      setMessages(next);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="chat-page">
      <header className="chat-head">
        <div className="row" style={{ gap: 10 }}>
          <span className="chat-dot" />
          <div>
            <div className="chat-title">AI 对话</div>
            <div className="chat-sub">带着记忆回答</div>
          </div>
        </div>
        <div className="row" style={{ gap: 8 }}>
        <button
          className="btn ghost"
          style={{ height: 34, padding: "0 12px", fontSize: 12 }}
          onClick={() => {
            if (!messages.length) return;
            setMessages([]);
            setMeta(null);
            saveThread(spaceId, []);
            toast("已清空这个记忆体的对话");
          }}
          title="清空当前对话"
        >
          清空
        </button>
        <select
          className="input"
          style={{ height: 34, padding: "0 10px", width: 180 }}
          value={spaceId}
          onChange={(e) => onPickSpace(Number(e.target.value))}
        >
          {spaces.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        </div>
      </header>

      <div className="chat-body" ref={boxRef}>
        <div className="chat-inner">
          {messages.length === 0 && (
            <div className="chat-empty">
              <div className="chat-mark">
                <img src="/mascot.jpg" alt="" />
              </div>
              <p className="chat-hello">想聊点什么？我会先翻一遍这个记忆体。</p>
              <div className="row" style={{ justifyContent: "center", flexWrap: "wrap", marginTop: 16 }}>
                {QUICK.map((q) => (
                  <button key={q} className="chip" onClick={() => send(q)}>
                    {q}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((m, i) => (
            <div key={i} className={"msg " + m.role}>
              <div className="bubble">{m.role === "assistant" ? <Rich text={m.content} /> : m.content}</div>
            </div>
          ))}

          {busy && (
            <div className="msg assistant">
              <div className="bubble typing">
                <i /> <i /> <i />
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="chat-foot">
        <div className="chat-inner">
          {meta && (
            <div className="chat-meta">
              {meta.model} · 本轮带上了 {meta.memoryUsed} 条记忆{meta.saved ? " · 已记入当前记忆体" : ""}{meta.styled ? " · 按说话方式" : ""}
              {meta.searchQuery && meta.searchQuery !== lastUser && (
                <span className="chat-query"> · 检索「{meta.searchQuery}」</span>
              )}
            </div>
          )}
          <form
            className="chat-input"
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
          >
            <textarea
              className="textarea"
              style={{ minHeight: 46, maxHeight: 140, padding: "12px 14px" }}
              placeholder="问点什么，Enter 发送，Shift+Enter 换行"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  send();
                }
              }}
            />
            <button className="btn" disabled={busy || !input.trim()}>
              发送
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
