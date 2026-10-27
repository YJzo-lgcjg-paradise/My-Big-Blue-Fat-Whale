import { useEffect, useRef, useState } from "react";
import { api, type DshSession, type Space } from "../api";

type Source = "web" | "harness" | "bundle";

type Props = {
  spaceId: number;
  space: Space | undefined;
  spaces: Space[];
  onImported: () => void;
  onSpaceCreated: (id: number) => void;
  toast: (msg: string) => void;
};

const CARDS: { key: Source; title: string; sub: string; icon: string }[] = [
  { key: "web", title: "DeepSeek 网页版", sub: "填 Cookie 自动导入", icon: "◈" },
  { key: "harness", title: "DeepSeek Harness", sub: "直接从本机对话导入", icon: "◉" },
  { key: "bundle", title: "已有记忆体", sub: "拖入 .json 或复制现有", icon: "▣" },
];

export function ImportPage({ spaceId, space, spaces, onImported, onSpaceCreated, toast }: Props) {
  const [source, setSource] = useState<Source | null>(null);

  const [url, setUrl] = useState("");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<{ total: number; done: number; ready: number; failed: number } | null>(null);
  const [error, setError] = useState("");

  const [sessions, setSessions] = useState<DshSession[] | null>(null);
  const [selected, setSelected] = useState<DshSession | null>(null);
  const [loadingSessions, setLoadingSessions] = useState(false);

  const [cookie, setCookie] = useState("");
  const [dsSessions, setDsSessions] = useState<{ id: string; title: string; updatedAt: number }[] | null>(null);
  const [dsPicked, setDsPicked] = useState<string[]>([]);
  const [dsLoading, setDsLoading] = useState(false);
  const [dsQuery, setDsQuery] = useState("");
  const [dsShown, setDsShown] = useState(20);

  const [bundleName, setBundleName] = useState("");
  const [bundleCount, setBundleCount] = useState(0);
  const [bundlePayload, setBundlePayload] = useState<any>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (source === "harness" && sessions === null && !loadingSessions) {
      setLoadingSessions(true);
      api
        .dshSessions()
        .then((r) => setSessions(r.sessions))
        .catch((e) => {
          setError(e.message);
          setSessions([]);
        })
        .finally(() => setLoadingSessions(false));
    }
  }, [source, sessions, loadingSessions]);

  async function waitFor(importId: number) {
    for (let i = 0; i < 900; i++) {
      await new Promise((r) => setTimeout(r, 1200));
      const p = await api.importProgress(importId);
      setProgress({ total: p.total, done: p.done, ready: p.ready, failed: p.failed });
      if (p.status === "done") {
        toast(`整理完成：${p.ready} 条记忆`);
        onImported();
        return;
      }
    }
  }

  async function run(kind: "web" | "harness", fn: () => Promise<{ importId: number; total: number }>) {
    setError("");
    setBusy(true);
    setProgress(null);
    try {
      const res = await fn();
      toast(`已切分 ${res.total} 条，后台正在总结`);
      await waitFor(res.importId);
    } catch (e: any) {
      setError(e.message || "导入失败");
    } finally {
      setBusy(false);
    }
  }

  async function pickBundle(file?: File) {
    if (!file) return;
    try {
      const payload = JSON.parse(await file.text());
      if (!Array.isArray(payload.memories)) throw new Error("不是有效的记忆体文件");
      setBundlePayload(payload);
      setBundleName(payload.name || file.name.replace(/\.json$/i, ""));
      setBundleCount(payload.memories.length);
      setError("");
    } catch (e: any) {
      setError(e.message || "文件格式不对");
      setBundlePayload(null);
    }
  }

  async function importBundle() {
    if (!bundlePayload) return;
    setBusy(true);
    try {
      const res = await api.importSpace(bundlePayload);
      toast(`已导入 ${bundleCount} 条记忆`);
      onSpaceCreated(res.id);
      onImported();
      setBundlePayload(null);
      setBundleCount(0);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function exportCurrent() {
    if (!space) return;
    const data = await api.exportSpace(space.id);
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `${space.name || "memory-body"}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    toast("已导出当前记忆体");
  }

  async function mergeFrom(id: number) {
    setBusy(true);
    setError("");
    try {
      const res = await api.mergeSpace(id, spaceId);
      toast(`已复制 ${res.copied} 条记忆`);
      onImported();
    } catch (e: any) {
      setError(e.message || "复制失败");
    } finally {
      setBusy(false);
    }
  }

  const dsFiltered = (dsSessions || []).filter((s) =>
    (s.title || "").toLowerCase().includes(dsQuery.trim().toLowerCase())
  );

  const pct = progress && progress.total ? Math.round((progress.done / progress.total) * 100) : 0;

  async function connectDeepSeek() {
    if (!cookie.trim()) {
      setError("先粘贴 Cookie 或 userToken");
      return;
    }
    setDsLoading(true);
    setError("");
    setDsSessions(null);
    setDsPicked([]);
    try {
      const res = await api.deepseekConnect(cookie.trim());
      setDsSessions(res.sessions);
      setDsQuery("");
      setDsShown(20);
      toast(res.sessions.length ? `连上了，共 ${res.sessions.length} 个会话` : "连上了，但没有会话");
    } catch (e: any) {
      setError(e.message || "连接失败");
    } finally {
      setDsLoading(false);
    }
  }

  async function importDeepSeek() {
    if (dsPicked.length === 0) return;
    const total = dsPicked.length;
    let ok = 0;
    setBusy(true);
    setError("");
    try {
      for (const id of dsPicked) {
        try {
          await api.deepseekImport(spaceId, id);
          ok++;
        } catch (e: any) {
          setError(`第 ${ok + 1} 个失败：${e.message || ""}`);
          break;
        }
      }
      if (ok > 0) {
        toast(`已导入 ${ok} / ${total} 个会话`);
        setDsPicked([]);
        onImported();
      }
    } finally {
      setBusy(false);
    }
  }

  async function clearCookie() {
    await api.deepseekDisconnect().catch(() => {});
    setCookie("");
    setDsSessions(null);
    setDsSelected(null);
    toast("已清除 Cookie");
  }

  return (
    <section>
      <div className="panel cookie-panel">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h3>DeepSeek 凭证（userToken）</h3>
            <p className="hint">
              这里要填的是 localStorage 里的 <code>userToken</code>（不是 Cookie）：F12 → Application →
              Local Storage → https://chat.deepseek.com，复制 userToken 的值。
              也支持整段 cURL 或 localStorage 的 JSON，会自动提取。凭证只存后端内存，重启即失效。
            </p>
          </div>
          {dsSessions && <span className="tag gold">{dsSessions.length} 个会话</span>}
        </div>

        <div style={{ marginTop: 12 }}>
          <textarea
            className="textarea"
            style={{ minHeight: 76, fontSize: 12 }}
            placeholder="直接粘 userToken，或右键请求「以 cURL 格式复制」整段粘进来（bash / cmd 都行）"
            value={cookie}
            onChange={(e) => setCookie(e.target.value)}
          />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={connectDeepSeek} disabled={dsLoading || busy}>
              {dsLoading ? "连接中…" : "连接"}
            </button>
            {dsSessions && (
              <button className="btn ghost" onClick={clearCookie} disabled={dsLoading}>
                清除
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="source-cards">
        {CARDS.map((c) => (
          <button
            key={c.key}
            className={"source-card" + (source === c.key ? " active" : "")}
            onClick={() => {
              setSource(source === c.key ? null : c.key);
              setError("");
            }}
          >
            <span className="source-icon">{c.icon}</span>
            <span className="source-title">{c.title}</span>
            <span className="source-sub">{c.sub}</span>
          </button>
        ))}
      </div>

      {source === "web" && (
        <div className="panel" style={{ marginTop: 18 }}>
          <h3>从 DeepSeek 网页版导入</h3>

          <p className="hint" style={{ marginBottom: 12 }}>
            {dsSessions
              ? `已连接，共 ${dsSessions.length} 个会话。在下面选一个导入。`
              : "还没连接 —— 先在页面上方「DeepSeek Cookie」里填好凭证。"}
          </p>

          {dsSessions && dsSessions.length > 0 && (
            <>
              <div className="row" style={{ marginBottom: 10 }}>
                <input
                  className="input grow"
                  placeholder="搜索会话标题"
                  value={dsQuery}
                  onChange={(e) => {
                    setDsQuery(e.target.value);
                    setDsShown(20);
                  }}
                />
                <span className="hint">
                  {dsFiltered.length} / {dsSessions.length}
                </span>
              </div>

              <div className="session-list">
                {dsFiltered.slice(0, dsShown).map((s) => (
                  <button
                    key={s.id}
                    className={"session-row" + (dsPicked.includes(s.id) ? " active" : "")}
                    onClick={() =>
                      setDsPicked((prev) => (prev.includes(s.id) ? prev.filter((x) => x !== s.id) : [...prev, s.id]))
                    }
                  >
                    <div className="session-main">
                      <div className="session-title">{s.title || "未命名会话"}</div>
                      <div className="session-sub">
                        {s.updatedAt ? new Date(s.updatedAt).toLocaleString() : ""}
                      </div>
                    </div>
                    <span className="session-dot" />
                  </button>
                ))}
              </div>

              {dsFiltered.length > dsShown && (
                <div className="row" style={{ justifyContent: "center", marginTop: 10 }}>
                  <button className="btn ghost" onClick={() => setDsShown((n) => n + 20)}>
                    再显示 20 个（还剩 {dsFiltered.length - dsShown} 个）
                  </button>
                </div>
              )}
              <div className="row" style={{ marginTop: 12 }}>
                <button className="btn" disabled={dsPicked.length === 0 || busy} onClick={importDeepSeek}>
                  {busy ? "整理中…" : `导入选中会话（${dsPicked.length}）`}
                </button>
                {dsPicked.length > 0 && <span className="hint">已选 {dsPicked.length} 个（再点一下取消）</span>}
              </div>
            </>
          )}

          <details style={{ marginTop: 18 }}>
            <summary className="hint" style={{ cursor: "pointer", marginBottom: 10 }}>
              不想填 Cookie？手动粘贴正文
            </summary>
            <label className="field" style={{ marginTop: 12 }}>
              <span>对话网址（仅作来源标记）</span>
              <input
                className="input"
                placeholder="https://chat.deepseek.com/a/chat/s/..."
                value={url}
                onChange={(e) => setUrl(e.target.value)}
              />
            </label>
            <label className="field">
              <span>对话正文</span>
              <textarea
                className="textarea"
                style={{ minHeight: 200 }}
                placeholder={"用户：……\n助手：……\n\n支持 用户：/助手：、User:/Assistant:、问：/答："}
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            </label>
            <button
              className="btn"
              disabled={busy}
              onClick={() => run("web", () => api.webImport(spaceId, url.trim() || "https://chat.deepseek.com", text))}
            >
              {busy ? "整理中…" : "导入这段正文"}
            </button>
          </details>
        </div>
      )}

      {source === "harness" && (
        <div className="panel" style={{ marginTop: 18 }}>
          <h3>从本机 Harness 导入</h3>
          <p className="hint" style={{ marginBottom: 12 }}>
            读取 <code>~/.dsh/sessions</code> 下的会话（只读，不改动原文件）。
          </p>

          {loadingSessions && <p className="hint">正在读取本机会话…</p>}
          {sessions && sessions.length === 0 && <p className="hint">没找到 Harness 会话。</p>}

          <div className="session-list">
            {(sessions || []).map((s) => (
              <button
                key={s.id}
                className={"session-row" + (selected?.id === s.id ? " active" : "")}
                onClick={() => setSelected(s)}
              >
                <div className="session-main">
                  <div className="session-title">{s.title}</div>
                  <div className="session-sub">
                    {new Date(s.updatedAt).toLocaleString()} · {s.userTurns} 轮 · {s.cwd.split("\\").pop()}
                  </div>
                </div>
                <span className="session-dot" />
              </button>
            ))}
          </div>

          <div className="row" style={{ marginTop: 14 }}>
            <button
              className="btn"
              disabled={!selected || busy}
              onClick={() => selected && run("harness", () => api.dshImport(spaceId, selected.id))}
            >
              {busy ? "整理中…" : "导入选中的会话"}
            </button>
            {selected && <span className="hint">已选：{selected.title}</span>}
          </div>
        </div>
      )}

      {source === "bundle" && (
        <div className="panel" style={{ marginTop: 18 }}>
          <h3>已有记忆体</h3>
          <p className="hint" style={{ marginBottom: 12 }}>
            把 `.json` 记忆体拖到窗口任意位置就能导入（也可以点下面选文件）；或者直接把现有记忆体里的记忆复制过来。
          </p>

          <div className="row">
            <button className="btn ghost" onClick={() => fileRef.current?.click()} disabled={busy}>
              选择 .json 文件
            </button>
            <button className="btn ghost" onClick={exportCurrent} disabled={!space}>
              导出当前记忆体
            </button>
            <input
              ref={fileRef}
              type="file"
              accept=".json,application/json"
              style={{ display: "none" }}
              onChange={(e) => pickBundle(e.target.files?.[0])}
            />
          </div>

          {bundlePayload && (
            <div className="panel" style={{ marginTop: 14 }}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <div>
                  <div className="session-title">{bundleName || "未命名记忆体"}</div>
                  <div className="session-sub">{bundleCount} 条记忆</div>
                </div>
                <button className="btn" onClick={importBundle} disabled={busy}>
                  {busy ? "导入中…" : "导入为新记忆体"}
                </button>
              </div>
            </div>
          )}

          {spaces.filter((s) => s.id !== spaceId).length > 0 && (
            <>
              <p className="hint" style={{ marginTop: 18, marginBottom: 8 }}>
                或者，从现有记忆体复制到这个记忆体：
              </p>
              <div className="session-list">
                {spaces
                  .filter((s) => s.id !== spaceId)
                  .map((s) => (
                    <div key={s.id} className="session-row" style={{ cursor: "default" }}>
                      <div className="session-main">
                        <div className="session-title">{s.name}</div>
                        <div className="session-sub">{s.total} 条记忆</div>
                      </div>
                      <button className="btn sm" onClick={() => mergeFrom(s.id)} disabled={busy}>
                        复制过来
                      </button>
                    </div>
                  ))}
              </div>
            </>
          )}
        </div>
      )}

      {error && <p className="err" style={{ marginTop: 12 }}>{error}</p>}

      {progress && (
        <div className="panel" style={{ marginTop: 16 }}>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <span className="hint">
              整理进度 {progress.done} / {progress.total}
              {progress.failed ? ` · 失败 ${progress.failed}` : ""}
            </span>
            <span className="mem-score">{pct}%</span>
          </div>
          <div className="progress">
            <i style={{ width: `${pct}%` }} />
          </div>
          <p className="hint">一条大约 2–4 秒，先去做别的，回来就好了。</p>
        </div>
      )}
    </section>
  );
}
