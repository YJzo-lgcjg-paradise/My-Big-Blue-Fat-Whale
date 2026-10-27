import { useCallback, useEffect, useState } from "react";
import { api, type Episode, type Memory } from "../api";
import { MemoryCard } from "../components/MemoryCard";

type Props = {
  spaceId: number;
  refreshKey: number;
  onOpen: (m: Memory) => void;
};

const PAGE = 30;

export function Library({ spaceId, refreshKey, onOpen }: Props) {
  const [layer, setLayer] = useState<"episodes" | "turns">("episodes");
  const [q, setQ] = useState("");
  const [deep, setDeep] = useState(false);
  const [items, setItems] = useState<Memory[]>([]);
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [epTotal, setEpTotal] = useState(0);
  const [epLoadingMore, setEpLoadingMore] = useState(false);
  const [epProgress, setEpProgress] = useState<{ total: number; ready: number; pending: number; failed: number; partitioning: boolean }>({
    total: 0,
    ready: 0,
    pending: 0,
    failed: 0,
    partitioning: false,
  });
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [mode, setMode] = useState<"list" | "recall">("list");
  const [error, setError] = useState("");

  const [detail, setDetail] = useState<{
    episode: Episode;
    turns: {
      id: number;
      occurredAt: string | null;
      question: string;
      answer: string;
      attachments?: { id: number; kind: string; caption: string; ocrText: string }[];
    }[];
  } | null>(null);
  const [merging, setMerging] = useState<number[]>([]);

  const loadEpisodes = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await api.episodes(spaceId, PAGE, 0);
      setEpisodes(res.items);
      setEpTotal(res.total);
      setEpProgress({
        total: res.total,
        ready: res.ready,
        pending: res.pending,
        failed: res.failed,
        partitioning: res.partitioning,
      });
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoading(false);
    }
  }, [spaceId]);

  const load = useCallback(
    async (keyword = "", useDeep = false) => {
      setLoading(true);
      setError("");
      try {
        if (keyword.trim()) {
          const res = await api.recall(spaceId, keyword.trim(), useDeep, useDeep ? 30 : 14);
          setItems(res.items);
          setTotal(res.items.length);
          setMode("recall");
        } else {
          const res = await api.memories(spaceId, "", PAGE, 0);
          setItems(res.items);
          setTotal(res.total);
          setMode("list");
        }
      } catch (e: any) {
        setError(e.message || "加载失败");
      } finally {
        setLoading(false);
      }
    },
    [spaceId]
  );

  const loadMore = useCallback(async () => {
    if (mode !== "list" || loadingMore || loading || items.length >= total) return;
    setLoadingMore(true);
    try {
      const res = await api.memories(spaceId, "", PAGE, items.length);
      setItems((prev) => [...prev, ...res.items]);
      setTotal(res.total);
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoadingMore(false);
    }
  }, [mode, loadingMore, loading, items.length, total, spaceId]);

  useEffect(() => {
    setQ("");
    setMerging([]);
    loadEpisodes();
    load("", false);
  }, [spaceId, refreshKey, load, loadEpisodes]);

  useEffect(() => {
    if (layer !== "episodes") return;
    if (!epProgress.partitioning && epProgress.pending === 0) return;
    const t = window.setInterval(() => loadEpisodes(), 3000);
    return () => window.clearInterval(t);
  }, [layer, epProgress.partitioning, epProgress.pending, loadEpisodes]);

  async function loadMoreEpisodes() {
    if (epLoadingMore || episodes.length >= epTotal) return;
    setEpLoadingMore(true);
    try {
      const res = await api.episodes(spaceId, PAGE, episodes.length);
      setEpisodes((prev) => [...prev, ...res.items]);
      setEpTotal(res.total);
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setEpLoadingMore(false);
    }
  }

  async function openEpisode(ep: Episode) {
    try {
      const res = await api.episode(ep.id);
      setDetail(res);
    } catch (e: any) {
      setError(e.message || "打开失败");
    }
  }

  async function doMerge() {
    if (merging.length !== 2) return;
    const [a, b] = merging;
    if (!confirm("把第二条合并进第一条？第二条的对话会移到第一条，第二条被删除。")) return;
    try {
      await api.mergeEpisode(b, a);
      setMerging([]);
      await loadEpisodes();
    } catch (e: any) {
      setError(e.message || "合并失败");
    }
  }

  return (
    <section>
      <div className="row" style={{ marginBottom: 14 }}>
        <button className={"chip" + (layer === "episodes" ? " on" : "")} onClick={() => setLayer("episodes")}>
          事件记忆 · {epTotal}
        </button>
        <button className={"chip" + (layer === "turns" ? " on" : "")} onClick={() => setLayer("turns")}>
          对话记忆 · {total}
        </button>
        {layer === "episodes" && (
          <span className="hint" style={{ marginLeft: "auto" }}>
            先想到事件，再想到具体的事
          </span>
        )}
      </div>

      {layer === "episodes" && (
        <>
          {merging.length > 0 && (
            <div className="row" style={{ marginBottom: 12 }}>
              <span className="hint">已选 {merging.length} 条，选两条后合并</span>
              <button className="btn ghost" onClick={doMerge} disabled={merging.length !== 2}>
                合并
              </button>
              <button className="btn ghost" onClick={() => setMerging([])}>
                取消
              </button>
            </div>
          )}

          {error && <p className="err" style={{ marginBottom: 12 }}>{error}</p>}

          {(epProgress.partitioning || epProgress.pending > 0) && (
            <div className="ep-progress">
              <div className="row" style={{ justifyContent: "space-between", marginBottom: 6 }}>
                <span className="hint">
                  {epProgress.partitioning ? "正在划分事件…" : "正在写事件日记…"}
                  {epProgress.total > 0 ? ` ${epProgress.ready} / ${epProgress.total}` : ""}
                </span>
                {epProgress.failed > 0 && <span className="hint">{epProgress.failed} 条失败</span>}
              </div>
              <div className="progress">
                <i
                  style={{
                    width:
                      epProgress.total > 0
                        ? `${Math.round((epProgress.ready / epProgress.total) * 100)}%`
                        : "8%",
                  }}
                />
              </div>
            </div>
          )}

          {episodes.length === 0 && !loading && (
            <div className="empty">
              <b>还没有事件</b>
              <p>导入对话后，它会读完全部原文，按「一件事」切开，再写成第一人称的日记。</p>
            </div>
          )}

          <div className="ep-list">
            {episodes.map((ep) => (
              <div key={ep.id} className="ep-card" onClick={() => openEpisode(ep)}>
                <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <div className="ep-title">{ep.title || "未命名事件"}</div>
                    <div className="ep-sub">
                      {ep.startedAt ? new Date(ep.startedAt).toLocaleDateString() : ""}
                      {" · "}
                      {ep.turnCount} 轮
                      {ep.moodTo ? ` · ${ep.moodFrom ? ep.moodFrom + "→" : ""}${ep.moodTo}` : ""}
                      {ep.status !== "ready" ? " · 整理中" : ""}
                    </div>
                  </div>
                  <label className="pick-box" onClick={(e) => e.stopPropagation()} title="选两条合并">
                    <input
                      type="checkbox"
                      checked={merging.includes(ep.id)}
                      onChange={() =>
                        setMerging((prev) =>
                          prev.includes(ep.id)
                            ? prev.filter((x) => x !== ep.id)
                            : prev.length >= 2
                            ? prev
                            : [...prev, ep.id]
                        )
                      }
                    />
                  </label>
                </div>

                <p className="ep-diary">{ep.diary || "…"}</p>

                {ep.openThreads?.length > 0 && (
                  <div className="ep-threads">
                    {ep.openThreads.slice(0, 3).map((t, i) => (
                      <span key={i} className="chip small">
                        {t}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>

          {episodes.length < epTotal && (
            <div style={{ display: "flex", justifyContent: "center", padding: "16px 0 4px" }}>
              <button className="btn ghost" onClick={loadMoreEpisodes} disabled={epLoadingMore}>
                {epLoadingMore ? "加载中…" : `再加载 ${PAGE} 个事件（还剩 ${epTotal - episodes.length} 个）`}
              </button>
            </div>
          )}
        </>
      )}

      {layer === "turns" && (
        <>
          <div className="row" style={{ marginBottom: 16 }}>
            <div className="search grow">
              <span style={{ color: "#6f6a62", fontSize: 13 }}>⌕</span>
              <input
                value={q}
                placeholder="问点什么，比如「上次那个 PDF 代码块怎么修的」"
                onChange={(e) => setQ(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && load(q, deep)}
              />
              <span className="k">Enter</span>
            </div>
            <label className="switch">
              <input type="checkbox" checked={deep} onChange={(e) => setDeep(e.target.checked)} />
              深度回忆
            </label>
            <button className="btn" onClick={() => load(q, deep)} disabled={loading}>
              {loading ? "正在回忆…" : "回忆"}
            </button>
            {(q || mode === "recall") && (
              <button
                className="btn ghost"
                onClick={() => {
                  setQ("");
                  load("", false);
                }}
              >
                返回全部
              </button>
            )}
          </div>

          <div className="row" style={{ marginBottom: 14, justifyContent: "space-between" }}>
            <span className="hint">
              {mode === "recall"
                ? `按相关度排序 · ${items.length} 条${deep ? " · 已扫描全部记忆" : ""}`
                : `按时间倒序 · 共 ${total} 条${items.length < total ? `，已显示 ${items.length}` : ""}`}
            </span>
            {mode === "recall" && <span className="hint">分数 = 相似度 + 新近度 + 强度 + 重要度</span>}
          </div>

          {error && <p className="err" style={{ marginBottom: 12 }}>{error}</p>}

          {items.length === 0 && !loading ? (
            <div className="empty">
              <b>这里还没有记忆</b>
              <p>去「导入」粘贴一段对话，鲸鱼会把它切成一条条记住。</p>
            </div>
          ) : (
            <div className="list">
              {items.map((m) => (
                <MemoryCard key={m.id} memory={m} onClick={() => onOpen(m)} />
              ))}
            </div>
          )}

          {mode === "list" && items.length < total && (
            <div style={{ display: "flex", justifyContent: "center", padding: "18px 0 4px" }}>
              <button className="btn ghost" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? "加载中…" : `再加载 ${PAGE} 条（还剩 ${total - items.length} 条）`}
              </button>
            </div>
          )}
        </>
      )}

      {detail && (
        <div className="modal-mask" onClick={() => setDetail(null)}>
          <div className="modal-card" style={{ width: "min(760px, 92vw)" }} onClick={(e) => e.stopPropagation()}>
            <div className="row" style={{ justifyContent: "space-between", marginBottom: 8 }}>
              <h3>{detail.episode.title}</h3>
              <button className="icon-btn" onClick={() => setDetail(null)}>
                ✕
              </button>
            </div>
            <p className="hint" style={{ marginBottom: 12 }}>
              {detail.episode.startedAt ? new Date(detail.episode.startedAt).toLocaleString() : ""}
              {" · "}
              {detail.episode.turnCount} 轮
              {detail.episode.moodTo
                ? ` · 情绪 ${detail.episode.moodFrom ? detail.episode.moodFrom + "→" : ""}${detail.episode.moodTo}`
                : ""}
            </p>
            <div className="ep-detail-body">
              <p style={{ color: "var(--text)", lineHeight: 1.8, fontSize: 13.5 }}>{detail.episode.diary}</p>
              {detail.episode.relationNote && (
                <p className="hint" style={{ marginTop: 10 }}>关系：{detail.episode.relationNote}</p>
              )}
              {detail.episode.openThreads?.length > 0 && (
                <div style={{ marginTop: 10 }}>
                  <p className="hint" style={{ marginBottom: 4 }}>没说完的</p>
                  {detail.episode.openThreads.map((t, i) => (
                    <p key={i} className="hint">· {t}</p>
                  ))}
                </div>
              )}
              <p className="hint" style={{ marginTop: 16, marginBottom: 6 }}>
                下辖的对话（{detail.turns.length}）
              </p>
              {detail.turns.map((t) => (
                <div key={t.id} className="ep-turn">
                  <div className="ep-turn-q">他：{t.question}</div>
                  <div className="ep-turn-a">
                    我：{t.answer.slice(0, 400)}
                    {t.answer.length > 400 ? "…" : ""}
                  </div>
                  {t.attachments && t.attachments.length > 0 && (
                    <div className="ep-atts">
                      {t.attachments.map((a) => (
                        <div key={a.id} className="ep-att">
                          <img src={`/api/attachments/${a.id}/raw`} alt={a.caption || "图片"} />
                          {(a.caption || a.ocrText) && (
                            <span className="hint">{a.caption || a.ocrText}</span>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
