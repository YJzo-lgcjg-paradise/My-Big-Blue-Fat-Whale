import { useEffect, useState } from "react";
import { api, type Memory } from "../api";
import { absTime, bars, copyText } from "../format";

type Props = {
  memory: Memory | null;
  onClose: () => void;
  onChanged: (m: Memory) => void;
  onDeleted: (id: number) => void;
  toast: (msg: string) => void;
};

export function Drawer({ memory, onClose, onChanged, onDeleted, toast }: Props) {
  const [summary, setSummary] = useState("");
  const [importance, setImportance] = useState(3);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSummary(memory?.summary || "");
    setImportance(memory?.importance || 3);
  }, [memory?.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  if (!memory) return null;

  const value = bars(memory.strength ?? 0.6);

  async function save() {
    setSaving(true);
    try {
      const res = await api.updateMemory(memory!.id, { summary, importance });
      onChanged(res.item);
      toast("已保存，记忆已更新");
    } catch (e: any) {
      toast(e.message || "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!confirm("删除这条记忆？原文也会一起删掉。")) return;
    await api.deleteMemory(memory!.id);
    onDeleted(memory!.id);
    toast("已删除");
  }

  async function copyOne() {
    const text = `【${absTime(memory!.occurredAt)}】\n问：${memory!.question}\n记：${memory!.summary}\n`;
    await copyText(text);
    toast("已复制这一条");
  }

  return (
    <>
      <div className="backdrop" onClick={onClose} />
      <aside className="drawer">
        <div className="drawer-head">
          <div>
            <p className="eyebrow">MEMORY</p>
            <h2>{absTime(memory.occurredAt)}</h2>
            <div className="mem-time">
              <span>{memory.source}</span>
              <span className="abs">#{memory.id}</span>
            </div>
          </div>
          <button className="close" onClick={onClose} title="关闭">
            ×
          </button>
        </div>

        <div className="row" style={{ marginBottom: 14 }}>
          <div className="strength" title={`记忆强度 ${(memory.strength ?? 0.6).toFixed(2)}`}>
            {[1, 2, 3, 4, 5].map((i) => (
              <i key={i} className={i <= value ? "on" : ""} />
            ))}
          </div>
          <span className="hint">
            强度 {(memory.strength ?? 0).toFixed(2)} · 距今 {memory.ageDays ?? 0} 天 · 被想起 {memory.accessCount} 次
          </span>
        </div>

        <label className="field">
          <span>记忆总结</span>
          <textarea
            className="textarea"
            style={{ minHeight: 150, fontFamily: "var(--font-sans)", fontSize: 13.5 }}
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
          />
        </label>

        <label className="field">
          <span>重要度 {importance} / 5</span>
          <input
            type="range"
            min={1}
            max={5}
            value={importance}
            onChange={(e) => setImportance(Number(e.target.value))}
            style={{ width: "100%", accentColor: "#c6a76a" }}
          />
        </label>

        <div className="row" style={{ marginBottom: 6 }}>
          <button className="btn" onClick={save} disabled={saving}>
            {saving ? "保存中…" : "保存修改"}
          </button>
          <button className="btn ghost" onClick={copyOne}>
            复制这一条
          </button>
          <button className="btn danger" onClick={remove} style={{ marginLeft: "auto" }}>
            删除
          </button>
        </div>

        <div className="qa">
          <p className="label">原文 · 问</p>
          <p className="text user">{memory.question || "（无）"}</p>
        </div>
        <div className="qa">
          <p className="label">原文 · 答</p>
          <p className="text">{memory.answer || "（无）"}</p>
        </div>

        <div className="kv">
          <span>
            关键词 <b>{(memory.keywords || []).join(" / ") || "—"}</b>
          </span>
          <span>
            向量 <b>{memory.embedModel || "—"}</b>
          </span>
          <span>
            来源 <b>{memory.source}</b>
          </span>
        </div>
      </aside>
    </>
  );
}
