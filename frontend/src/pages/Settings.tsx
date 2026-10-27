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
  const [candidates, setCandidates] = useState<{ id: number; question: string; summary: string }[]>([]);
  const [picked, setPicked] = useState<number[]>([]);
  const [picksBusy, setPicksBusy] = useState(false);

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
        <h3>说话方式</h3>
        {style?.style ? (
          <>
            <p className="hint" style={{ marginBottom: 10 }}>
              从 {style.samples} 条回答里总结，之后 AI 对话按这个语气回答
              {style.updatedAt ? ` · 更新于 ${new Date(style.updatedAt).toLocaleString()}` : ""}
            </p>
            <p style={{ color: "var(--text)", fontSize: 13.5, lineHeight: 1.8 }}>{style.style}</p>
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
        </div>

        <div style={{ marginTop: 20, borderTop: "1px solid var(--line)", paddingTop: 16 }}>
          <p className="hint" style={{ marginBottom: 8 }}>
            还可以手挑几条对话塞进提示词当语气样例（最多 8 条，已选 {picked.length} 条）。
          </p>
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
    </section>
  );
}
