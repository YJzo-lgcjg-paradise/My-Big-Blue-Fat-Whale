import { useCallback, useEffect, useRef, useState } from "react";
import { api, type Health, type Memory, type Space } from "./api";
import { Drawer } from "./components/Drawer";
import { Intro } from "./components/Intro";
import { Library } from "./pages/Library";
import { ImportPage } from "./pages/ImportPage";
import { BundlePage } from "./pages/BundlePage";
import { Settings } from "./pages/Settings";
import { ChatPage } from "./pages/ChatPage";
import { ApiPanel } from "./components/ApiPanel";
import { MusicButton } from "./components/MusicButton";

type View = "chat" | "library" | "import" | "bundle" | "settings";

const META: Record<View, { tab: string; title: string; desc: string }> = {
  chat: {
    tab: "AI 对话",
    title: "AI 对话",
    desc: "带着这个记忆体的内容回答。",
  },
  library: {
    tab: "记忆库",
    title: "记忆库",
    desc: "按时间看全部，或者问一句，让鲸鱼想起相关的那些。",
  },
  import: {
    tab: "导入",
    title: "导入对话",
    desc: "把 DeepSeek 网页版或 Harness 里的对话粘进来，切成一条条问答，逐条记住。",
  },
  bundle: {
    tab: "续命包",
    title: "续命与搬家",
    desc: "对话快到上限，或者要换一个新聊天框：把该带的记忆打包，粘过去就行。",
  },
  settings: {
    tab: "设置",
    title: "设置",
    desc: "能力状态、记忆衰减、向量重建与数据位置。",
  },
};

export default function App() {
  const [spaces, setSpaces] = useState<Space[]>([]);
  const [spaceId, setSpaceId] = useState<number>(0);
  const [view, setView] = useState<View>(() => {
    const h = window.location.hash.replace("#", "") as View;
    return META[h] ? h : "library";
  });
  const [health, setHealth] = useState<Health | null>(null);
  const [stats, setStats] = useState<any>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selected, setSelected] = useState<Memory | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const [entered, setEntered] = useState(() => sessionStorage.getItem("mbfw.intro") === "1");
  const [dragging, setDragging] = useState(false);
  const [apiOpen, setApiOpen] = useState(false);
  const [toastMsg, setToastMsg] = useState("");
  const toastTimer = useRef<number>();

  const toast = useCallback((msg: string) => {
    setToastMsg(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToastMsg(""), 2400);
  }, []);

  const loadSpaces = useCallback(async (preferId?: number) => {
    const res = await api.spaces();
    setSpaces(res.spaces);
    const id = preferId || (res.spaces[0]?.id ?? 0);
    setSpaceId((cur) => (res.spaces.some((s) => s.id === cur) ? cur : id));
  }, []);

  const loadStats = useCallback(async (id: number) => {
    if (!id) return;
    try {
      const res = await api.stats(id);
      setStats(res.stats);
    } catch {
      /* 忽略 */
    }
  }, []);

  const loadHealth = useCallback(() => {
    api.health().then(setHealth).catch(() => setHealth(null));
  }, []);

  useEffect(() => {
    api.health().then(setHealth).catch(() => setHealth(null));
    loadSpaces().catch((e) => toast(e.message));
  }, [loadSpaces, toast]);

  useEffect(() => {
    loadStats(spaceId);
  }, [spaceId, refreshKey, loadStats]);

  useEffect(() => {
    if (window.location.hash !== `#${view}`) {
      history.replaceState(null, "", `#${view}`);
    }
  }, [view]);

  useEffect(() => {
    const onHash = () => {
      const h = window.location.hash.replace("#", "") as View;
      if (META[h]) setView(h);
    };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  useEffect(() => {
    const t = window.setInterval(async () => {
      try {
        const h = await api.health();
        setHealth(h);
        if (h.pending > 0) {
          await loadSpaces(spaceId);
          setRefreshKey((k) => k + 1);
        }
      } catch {
        /* 后端暂时不可用 */
      }
    }, 4000);
    return () => window.clearInterval(t);
  }, [spaceId, loadSpaces]);

  const space = spaces.find((s) => s.id === spaceId);
  const meta = META[view];

  // 全窗口拖入 .json 记忆体
  useEffect(() => {
    let depth = 0;
    const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types || []).includes("Files");

    const onEnter = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      depth++;
      setDragging(true);
    };
    const onOver = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
    };
    const onLeave = () => {
      depth = Math.max(0, depth - 1);
      if (depth === 0) setDragging(false);
    };
    const onDrop = async (e: DragEvent) => {
      e.preventDefault();
      depth = 0;
      setDragging(false);
      const files = Array.from(e.dataTransfer?.files || []);
      if (files.length === 0) return;
      const images = files.filter((f) => /\.(png|jpe?g|webp|gif)$/i.test(f.name) || f.type.startsWith("image/"));
      if (images.length > 0) {
        let ok = 0;
        for (const f of images) {
          if (f.size > 12 * 1024 * 1024) {
            toast(`${f.name} 太大（超过 12MB）`);
            continue;
          }
          try {
            const dataUrl = await new Promise<string>((resolve, reject) => {
              const r = new FileReader();
              r.onload = () => resolve(String(r.result));
              r.onerror = reject;
              r.readAsDataURL(f);
            });
            const base64 = dataUrl.split(",")[1] || "";
            await api.screenshot(spaceId, f.name, base64, f.type || "image/png");
            ok++;
          } catch (err: any) {
            toast(`${f.name} 上传失败：${err.message || ""}`);
          }
        }
        if (ok > 0) {
          setRefreshKey((k) => k + 1);
          setView("library");
          toast(`已上传 ${ok} 张图片，正在识别文字`);
        }
        return;
      }
      const file = files[0];
      if (!/\.json$/i.test(file.name)) {
        toast("只支持 .json 记忆体文件，或图片（png/jpg/webp/gif）");
        return;
      }
      try {
        const payload = JSON.parse(await file.text());
        if (!Array.isArray(payload.memories)) throw new Error("不是有效的记忆体文件");
        const res = await api.importSpace(payload);
        await loadSpaces(res.id);
        setSpaceId(res.id);
        setRefreshKey((k) => k + 1);
        setView("import");
        toast(`已导入 ${payload.memories.length} 条记忆`);
      } catch (err: any) {
        toast(err.message || "导入失败");
      }
    };

    window.addEventListener("dragenter", onEnter);
    window.addEventListener("dragover", onOver);
    window.addEventListener("dragleave", onLeave);
    window.addEventListener("drop", onDrop);
    return () => {
      window.removeEventListener("dragenter", onEnter);
      window.removeEventListener("dragover", onOver);
      window.removeEventListener("dragleave", onLeave);
      window.removeEventListener("drop", onDrop);
    };
  }, [loadSpaces, toast]);

  async function newSpace() {
    const name = prompt("给新记忆体起个名字", "新的记忆体");
    if (!name) return;
    const res = await api.createSpace(name);
    await loadSpaces(res.id);
    setSpaceId(res.id);
    toast("记忆体已创建");
  }

  return (
    <>
      {!entered && (
        <Intro
          onDone={() => {
            sessionStorage.setItem("mbfw.intro", "1");
            setEntered(true);
          }}
        />
      )}
      <MusicButton />
      <div className="app">
      <header className="topbar">
        <div className="brand">
          <img className="brand-mark" src="/mascot.jpg" alt="My Big Blue Fat Whale" />
          <div className="brand-text">
            <div className="name">My Big Blue Fat Whale</div>
            <div className="sub">FOREVER · MEMORY SERVICE</div>
          </div>
        </div>

        <div className="topbar-right">
          <button
            className="btn ghost sm topbar-ai"
            onClick={() => setView("chat")}
            disabled={!spaceId}
          >
            AI 对话
          </button>
          <span className={"pill" + (health?.llm.enabled ? "" : " off")}>
            <i />
            {health?.llm.enabled ? "总结在线" : "总结离线"}
          </span>
          <span className={"pill" + (health?.embed.online ? "" : " off")}>
            <i />
            {health?.embed.online ? "向量在线" : "向量兜底"}
          </span>
          {health?.pending ? <span className="pill off">整理中 {health.pending}</span> : null}
          <button className="api-open" onClick={() => setApiOpen(true)} title="模型 API">
            API
            <i className={health?.llm.enabled ? "on" : ""} />
          </button>
        </div>
      </header>

      <div className="body">
        <aside className="sidebar">
          <button
            className={"ai-nav" + (view === "chat" ? " active" : "")}
            onClick={() => setView("chat")}
            disabled={!spaceId}
          >
            <span className="ai-nav-dot" />
            <span className="ai-nav-text">
              AI 对话
              <em>带着记忆回答</em>
            </span>
          </button>

          <div className="sidebar-title">
            <span>记忆体</span>
            <span>{spaces.length}</span>
          </div>

          {spaces.map((s) => (
            <div
              key={s.id}
              className={"space" + (s.id === spaceId ? " active" : "")}
              onClick={() => {
                setSpaceId(s.id);
                setView("library");
              }}
            >
              <span className="nm">{s.name}</span>
              <span className="ct">{s.total}</span>
            </div>
          ))}

          <div className="sidebar-foot">
            <button className="btn ghost sm" onClick={newSpace} style={{ width: "100%" }}>
              + 新建记忆体
            </button>
            <p className="sidebar-note">
              一个记忆体可以装多个聊天框的内容。
              <br />
              搬家就是换个地方接着记，整合就是把它们并进同一个记忆体。
            </p>
          </div>
        </aside>

        <main className={"main" + (view === "chat" ? " chat-mode" : "")}>
          {spaces.length === 0 ? (
            <div className="welcome">
              <p className="eyebrow">MY BIG BLUE FAT WHALE</p>
              <h1>还没有记忆体</h1>
              <p className="desc">
                记忆体是一份独立的外部记忆。给它起个名字，再把聊过的内容导进来——
                可以从 DeepSeek 网页版、本机 Harness，或者另一个记忆体文件。
              </p>
              <button className="btn" onClick={newSpace}>
                新建记忆体
              </button>
            </div>
          ) : view === "chat" ? (
            <ChatPage spaces={spaces} spaceId={spaceId} onPickSpace={setSpaceId} toast={toast} />
          ) : (
            <>
              <div className="page-head">
                <div>
                  <p className="eyebrow">{space?.name || "记忆体"}</p>
                  <h1>{meta.title}</h1>
                  <p className="desc">{meta.desc}</p>
                </div>
                <div className="hint" style={{ textAlign: "right", minWidth: 120 }}>
                  共 {stats?.total ?? 0} 条
                  {stats?.firstAt ? (
                    <>
                      <br />
                      {String(stats.firstAt).slice(0, 10)} 起
                    </>
                  ) : null}
                </div>
              </div>

              <nav className="tabs">
                {(Object.keys(META) as View[])
                  .filter((k) => k !== "chat")
                  .map((k) => (
                  <button
                    key={k}
                    className={"tab" + (view === k ? " active" : "")}
                    onClick={() => setView(k)}
                  >
                    {META[k].tab}
                  </button>
                ))}
              </nav>

              {view === "library" && spaceId > 0 && (
                <Library spaceId={spaceId} refreshKey={refreshKey} onOpen={setSelected} />
              )}
              {view === "import" && (
                <ImportPage
                  spaceId={spaceId}
                  space={space}
                  spaces={spaces}
                  toast={toast}
                  onSpaceCreated={(id) => {
                    setSpaceId(id);
                    setView("import");
                  }}
                  onImported={() => {
                    loadSpaces(spaceId);
                    setRefreshKey((k) => k + 1);
                  }}
                />
              )}
              {view === "bundle" && spaceId > 0 && <BundlePage spaceId={spaceId} toast={toast} />}
              {view === "settings" && spaceId > 0 && (
                <Settings
                  health={health}
                  stats={stats}
                  spaceId={spaceId}
                  spaceName={space?.name || "记忆体"}
                  toast={toast}
                  onChanged={() => {
                    loadSpaces(spaceId);
                    setRefreshKey((k) => k + 1);
                  }}
                  onDeleted={async () => {
                    setRefreshKey((k) => k + 1);
                    await loadSpaces();
                  }}
                />
              )}
            </>
          )}
        </main>
      </div>

      <Drawer
        memory={selected}
        onClose={() => setSelected(null)}
        toast={toast}
        onChanged={(m) => {
          setSelected(m);
          setRefreshKey((k) => k + 1);
        }}
        onDeleted={() => {
          setSelected(null);
          loadSpaces(spaceId);
          setRefreshKey((k) => k + 1);
        }}
      />

      {dragging && (
        <div className="drop-zone">
          <div className="drop-card">
            <div className="drop-icon">▣</div>
            <p>松开即可导入</p>
            <span>记忆体 .json，或截图（可多张）</span>
          </div>
        </div>
      )}

      {apiOpen && (
        <ApiPanel
          onClose={() => setApiOpen(false)}
          onChanged={loadHealth}
          toast={toast}
        />
      )}

      {toastMsg && <div className="toast">{toastMsg}</div>}
      </div>
    </>
  );
}
