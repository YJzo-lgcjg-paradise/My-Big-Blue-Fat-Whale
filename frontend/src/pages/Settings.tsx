import { useEffect, useState } from "react";
import { api, type Health } from "../api";

type Props = {
  health: Health | null;
  stats: { total: number; ready: number; pending: number; failed: number; firstAt?: string; lastAt?: string } | null;
  spaceId: number;
  spaceName: string;
  onChanged: () => void;
  onDeleted: () => void;
  toast: (msg: string) => void;
};

export function Settings({ health, stats, spaceId, spaceName, onChanged, onDeleted, toast }: Props) {
  const [busy, setBusy] = useState(false);
  const [style, setStyle] = useState<{
    style: string;
    samples: number;
    updatedAt: string | null;
    picks: { id: number; question: string; answer: string }[];
  } | null>(null);
  const [styleBusy, setStyleBusy] = useState(false);
  const [selfText, setSelfText] = useState("");
  const [ctxMode, setCtxMode] = useState<"budget" | "precision">("budget");
  const [historyKind, setHistoryKind] = useState<"self" | "style" | null>(null);
  const [historyItems, setHistoryItems] = useState<{ id: number; text: string; createdAt: string }[]>([]);
  const [selfBasis, setSelfBasis] = useState<string[]>([]);
  const [selfBusy, setSelfBusy] = useState(false);
  const [candidates, setCandidates] = useState<{ id: number; question: string; summary: string }[]>([]);
  const [picked, setPicked] = useState<number[]>([]);
  const [picksBusy, setPicksBusy] = useState(false);
  const [pickQuery, setPickQuery] = useState("");
  const [pickSearching, setPickSearching] = useState(false);

  useEffect(() => {
    setStyle(null);
    setCandidates([]);
    setPicked([]);
    Promise.all([
      api.getStyle(spaceId).catch(() => null),
      api.memories(spaceId, "", 30, 0).catch(() => ({ items: [] as any[] })),
    ]).then(([s, r]) => {
      const rows = (r.items || []).map((m: any) => ({ id: m.id, question: m.question, summary: m.summary }));
      if (s) {
        setStyle(s);
        api.getSelf(spaceId).then((r) => setSelfText(r.text || "")).catch(() => {});
        api.getContextMode(spaceId).then((r) => setCtxMode(r.mode === "precision" ? "precision" : "budget")).catch(() => {});
        const ids = (s.picks || []).map((p) => p.id);
        setPicked(ids);
        // 已选但不在最近 30 条里的，补到列表最前面，保证勾选可见
        const missing = (s.picks || [])
          .filter((p) => !rows.some((x) => x.id === p.id))
          .map((p) => ({ id: p.id, question: p.question, summary: "" }));
        setCandidates([...missing, ...rows]);
      } else {
        setCandidates(rows);
      }
    });
  }, [spaceId]);

  async function openHistory(kind: "self" | "style") {
    setHistoryKind(kind);
    setHistoryItems([]);
    try {
      const r = await api.history(spaceId, kind);
      setHistoryItems(r.items);
    } catch (e: any) {
      toast(e.message || "读取历史失败");
    }
  }

  async function genSelf() {
    setSelfBusy(true);
    try {
      const res = await api.generateSelf(spaceId);
      setSelfText(res.text || "");
      setSelfBasis(res.basis || []);
      toast("已经写完「我是谁」");
    } catch (e: any) {
      toast(e.message || "生成失败");
    } finally {
      setSelfBusy(false);
    }
  }

  async function saveSelfText() {
    setSelfBusy(true);
    try {
      await api.saveSelf(spaceId, selfText);
      toast("已保存");
    } catch (e: any) {
      toast(e.message || "保存失败");
    } finally {
      setSelfBusy(false);
    }
  }

  async function restyleAll() {
    if (!confirm("按当前说话方式重写这个记忆体的全部总结？会逐条重新调用模型，条数多时要跑一阵。")) return;
    setStyleBusy(true);
    try {
      const res = await api.restyle(spaceId);
      toast(`已排队 ${res.queued} 条，后台按新语气重写中`);
      onChanged();
    } catch (e: any) {
      toast(e.message || "重写失败");
    } finally {
      setStyleBusy(false);
    }
  }

  async function searchPicks(q: string) {
    setPickSearching(true);
    try {
      const r = await api.styleCandidates(spaceId, q, 60);
      // 已选中的哪怕这次没搜到，也保留在列表里，避免勾选丢失
      const rows = r.items;
      const missing = candidates.filter((c) => picked.includes(c.id) && !rows.some((x) => x.id === c.id));
      setCandidates([...missing, ...rows.map((m) => ({ id: m.id, question: m.question, summary: m.answer }))]);
    } catch (e: any) {
      toast(e.message || "搜索失败");
    } finally {
      setPickSearching(false);
    }
  }

  async function savePicks() {
    setPicksBusy(true);
    try {
      const res = await api.setStylePicks(spaceId, picked);
      setStyle(res);
      toast(`已选 ${res.picks.length} 条对话作为风格样例`);
    } catch (e: any) {
      toast(e.message || "保存失败");
    } finally {
      setPicksBusy(false);
    }
  }

  async function summarizeStyle() {
    setStyleBusy(true);
    try {
      const res = await api.makeStyle(spaceId);
      setStyle(res);
      toast(`已从 ${res.samples} 条回答里总结出说话方式`);
    } catch (e: any) {
      toast(e.message || "总结失败");
    } finally {
      setStyleBusy(false);
    }
  }

  async function rebuild() {
    if (!confirm("重新生成这个记忆体所有记忆的向量？记忆很多时会花一会儿。")) return;
    setBusy(true);
    try {
      const res = await api.reindex(spaceId);
      toast(`已重建 ${res.updated} 条向量`);
      onChanged();
    } catch (e: any) {
      toast(e.message || "重建失败");
    } finally {
      setBusy(false);
    }
  }

  async function removeSpace() {
    if (!confirm(`删除记忆体「${spaceName}」以及里面全部记忆？不可恢复。`)) return;
    await api.deleteSpace(spaceId);
    toast("记忆体已删除");
    onDeleted();
  }

  return (
    <section>
      <div className="kpis">
        <div className="kpi">
          <div className="n">{stats?.total ?? 0}</div>
          <div className="l">记忆总数</div>
        </div>
        <div className="kpi">
          <div className="n">{stats?.ready ?? 0}</div>
          <div className="l">已就绪</div>
        </div>
        <div className="kpi">
          <div className="n">{stats?.pending ?? 0}</div>
          <div className="l">整理中</div>
        </div>
        <div className="kpi">
          <div className="n">{stats?.failed ?? 0}</div>
          <div className="l">失败</div>
        </div>
      </div>

      <div className="panel">
        <h3>能力状态</h3>
        <div className="kv" style={{ marginTop: 0 }}>
          <span>
            总结模型 <b>{health?.llm.enabled ? `DeepSeek · ${health.llm.model}` : "离线兜底"}</b>
          </span>
          <span>
            向量模型 <b>{health?.embed.online ? `智谱 · ${health.embed.model}` : "本地哈希向量"}</b>
          </span>
          <span>
            后台队列 <b>{health?.pending ?? 0} 条待整理</b>
          </span>
        </div>
        <p className="hint" style={{ marginTop: 12 }}>
          两个 key 写在 <code>backend/.env.local</code>，首次启动时从本机已有项目自动带过来。留空也能跑，只是总结和向量会退化为本地算法。
        </p>
      </div>

      <div className="panel">
        <h3>上下文模式</h3>
        <p className="hint" style={{ marginBottom: 10 }}>
          决定每轮给模型看多少记忆。预算优先把上下文压在 8k token 内；精准优先不设总预算（安全上限 24k），
          最近上下文带到 20 条，命中门槛也放得更宽。
        </p>
        <div className="row">
          <button
            className={"chip" + (ctxMode === "budget" ? " on" : "")}
            onClick={async () => {
              await api.setContextMode(spaceId, "budget");
              setCtxMode("budget");
              toast("已切到预算优先");
            }}
          >
            预算优先
          </button>
          <button
            className={"chip" + (ctxMode === "precision" ? " on" : "")}
            onClick={async () => {
              await api.setContextMode(spaceId, "precision");
              setCtxMode("precision");
              toast("已切到精准优先");
            }}
          >
            精准优先
          </button>
        </div>
        <p className="hint" style={{ marginTop: 8 }}>
          {ctxMode === "budget"
            ? "当前：8k 预算 · 最近 10 条 · 事件最多 1 个 · 事件门槛 0.45 / 对话 0.30"
            : "当前：不设预算（上限 24k）· 最近 20 条 · 事件最多 2 个 · 事件门槛 0.30 / 对话 0.20"}
        </p>
      </div>

      <div className="panel">
        <h3>我是谁？</h3>
        <p className="hint" style={{ marginBottom: 10 }}>
          从这个记忆体已整理的事件里提炼一段第一人称自述：我是他的谁、我怎么对他、我不做什么。
          续命包会把它放在最前面，比说话方式更靠前。
        </p>
        <div className="prose-box">
        <textarea
          className="prose-input"
          value={selfText}
          placeholder="还没有写。点下面的「生成」会让它读一遍事件日记，写出一段；也可以自己改。"
          onChange={(e) => setSelfText(e.target.value)}
        />
        </div>
        {selfBasis.length > 0 && (
          <p className="hint" style={{ marginTop: 8 }}>
            依据：{selfBasis.slice(0, 6).join(" · ")}
          </p>
        )}
        <div className="row" style={{ marginTop: 12 }}>
          <button className="btn ghost" onClick={genSelf} disabled={selfBusy}>
            {selfBusy ? "生成中…" : selfText ? "重新生成" : "生成"}
          </button>
          <button className="btn ghost" onClick={saveSelfText} disabled={selfBusy}>
            保存修改
          </button>
          <button className="btn ghost" onClick={() => openHistory("self")}>
            查看过往
          </button>
        </div>
      </div>

      <div className="panel">
        <h3>说话方式</h3>
        {style?.style ? (
          <>
            <p className="hint" style={{ marginBottom: 10 }}>
              从 {style.samples} 条回答里总结，之后 AI 对话按这个语气回答
              {style.updatedAt ? ` · 更新于 ${new Date(style.updatedAt).toLocaleString()}` : ""}
            </p>
            <div className="prose-box">
              <p className="prose-text">{style.style}</p>
            </div>
          </>
        ) : (
          <p className="hint">还没有总结过。它会翻这个记忆体里助手的回答，看出它说话的语气、结构和口癖，之后照着回。</p>
        )}
        <div className="row" style={{ marginTop: 12 }}>
          <button className="btn ghost" onClick={summarizeStyle} disabled={styleBusy}>
            {styleBusy ? "总结中…" : style?.style ? "重新总结" : "总结说话方式"}
          </button>
          {style?.style && (
            <button className="btn ghost" onClick={restyleAll} disabled={styleBusy}>
              按这个语气重写全部总结
            </button>
          )}
          <button className="btn ghost" onClick={() => openHistory("style")}>
            查看过往
          </button>
        </div>

        <div style={{ marginTop: 20, borderTop: "1px solid var(--line)", paddingTop: 16 }}>
          <p className="hint" style={{ marginBottom: 8 }}>
            还可以手挑几条对话塞进固定提示词当语气样例（最多 8 条，已选 {picked.length} 条）。
            选中的会和「我是谁」「说话方式」一起，每轮都发；
          </p>
          <div className="row" style={{ marginBottom: 8 }}>
            <input
              className="input grow"
              placeholder="搜原文，比如「当时明月在」或某句你记得的话"
              value={pickQuery}
              onChange={(e) => setPickQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && searchPicks(pickQuery)}
            />
            <button className="btn ghost" onClick={() => searchPicks(pickQuery)} disabled={pickSearching}>
              {pickSearching ? "搜索中…" : "搜索"}
            </button>
            {pickQuery && (
              <button
                className="btn ghost"
                onClick={() => {
                  setPickQuery("");
                  searchPicks("");
                }}
              >
                清空
              </button>
            )}
          </div>
          <div className="pick-list">
            {candidates.map((m) => {
              const on = picked.includes(m.id);
              return (
                <label key={m.id} className={"pick-row" + (on ? " on" : "")}>
                  <input
                    type="checkbox"
                    checked={on}
                    onChange={() => {
                      setPicked((prev) => {
                        if (prev.includes(m.id)) return prev.filter((x) => x !== m.id);
                        if (prev.length >= 8) {
                          toast("最多选 8 条");
                          return prev;
                        }
                        return [...prev, m.id];
                      });
                    }}
                  />
                  <span className="pick-q">{(m.question || m.summary || "未命名").slice(0, 46)}</span>
                </label>
              );
            })}
            {candidates.length === 0 && <p className="hint">还没有记忆可选。</p>}
          </div>
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn ghost" onClick={savePicks} disabled={picksBusy}>
              {picksBusy ? "保存中…" : "保存样例"}
            </button>
          </div>
        </div>
      </div>

      <div className="panel">
        <h3>记忆衰减</h3>
        <p className="hint">
          检索分数 = 相似度 0.68 + 新近度 0.16 + 记忆强度 0.09 + 重要度 0.07。半衰期 30 天：越久没被想起，权重越低，
          这就是「些许遗忘」；但原文一直在库里，打开「深度回忆」就能全库扫回来。
        </p>
      </div>

      <div className="panel">
        <h3>维护</h3>
        <div className="row">
          <button className="btn ghost" onClick={rebuild} disabled={busy}>
            {busy ? "重建中…" : "重建本记忆体向量"}
          </button>
          <button className="btn ghost" onClick={onChanged}>
            刷新状态
          </button>
          <button className="btn danger" onClick={removeSpace} style={{ marginLeft: "auto" }}>
            删除当前记忆体
          </button>
        </div>
        <p className="hint" style={{ marginTop: 12 }}>
          换了大模型或向量模型之后，重建一次向量，检索才会对齐。
        </p>
      </div>

      <div className="panel">
        <h3>数据放在哪</h3>
        <p className="hint">
          全部记忆在 <code>backend/data/memory.mv.db</code> 这一个文件里（H2）。想整体搬到别的机器，复制这个文件即可；
          想看原始对话，每条记忆点开都有原文。
        </p>
      </div>
      {historyKind && (
        <div className="modal-mask" onClick={() => setHistoryKind(null)}>
          <div className="modal-card" style={{ width: "min(720px, 92vw)" }} onClick={(e) => e.stopPropagation()}>
            <div className="row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
              <h3>{historyKind === "self" ? "「我是谁」的过往版本" : "说话方式的过往版本"}</h3>
              <button className="icon-btn" onClick={() => setHistoryKind(null)}>✕</button>
            </div>
            {historyItems.length === 0 ? (
              <p className="hint">还没有历史版本。以后再生成或修改，旧的那份会自动存到这里。</p>
            ) : (
              <div style={{ maxHeight: "60vh", overflowY: "auto" }}>
                {historyItems.map((h) => (
                  <div key={h.id} className="hist-item">
                    <p className="hint" style={{ marginBottom: 6 }}>{new Date(h.createdAt).toLocaleString()}</p>
                    <p className="prose-text" style={{ margin: 0 }}>{h.text}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
