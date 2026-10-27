import { useEffect, useRef, useState } from "react";

type Props = {
  onDone: () => void;
};

export function Intro({ onDone }: Props) {
  const [ready, setReady] = useState(false);
  const [black, setBlack] = useState(false);
  const [phase, setPhase] = useState<"idle" | "a" | "b">("idle");
  const iframeRef = useRef<HTMLIFrameElement>(null);

  // 鼠标位置转发给 iframe 里的场景，镜头会跟着轻微偏移
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const win = iframeRef.current?.contentWindow;
      if (!win) return;
      const x = (e.clientX / window.innerWidth) * 2 - 1;
      const y = -(e.clientY / window.innerHeight) * 2 + 1;
      win.postMessage({ type: "autumn-mouse", x, y }, "*");
    };
    window.addEventListener("mousemove", onMove);
    return () => window.removeEventListener("mousemove", onMove);
  }, []);

  // 场景没加载完也放行，避免卡死
  useEffect(() => {
    const t = window.setTimeout(() => setReady(true), 12000);
    return () => window.clearTimeout(t);
  }, []);

  function sceneLoaded() {
    window.setTimeout(() => setReady(true), 700);
  }

  // 折尽梅花：渐暗成黑幕 → 难寄相思 → …… → 进项目
  function beginEnter() {
    if (phase !== "idle") return;
    setBlack(true);
    // 用链式定时器，保证前一句真的渲染过再切下一句（慢机器上也不会被合并）
    window.setTimeout(() => {
      setPhase("a");
      window.setTimeout(() => {
        setPhase("b");
        window.setTimeout(() => onDone(), 800);
      }, 600);
    }, 600);
  }

  return (
    <div className={"intro scene" + (ready ? " brand" : "")}>
      <iframe
        ref={iframeRef}
        className="intro-scene"
        src="/autumn/scene/index.html"
        title="秋 · 落叶与阳光"
        onLoad={sceneLoaded}
      />

      <div className="intro-top">MY BIG BLUE FAT WHALE · 秋</div>

      <div className="intro-brand">
        <div className="intro-scrim" />
        <div className="intro-stage">
          <img className="intro-mark" src="/paradise_logo.png" alt="PARADISE" />
          <p className="eyebrow intro-brand-line">PARADISE · 帕拉蒂斯高级</p>
          <h1 className="intro-title">
            <span className="intro-line l">当时明月在</span>
            <span className="intro-line r">
              <i>曾照彩云归</i>
            </span>
          </h1>
          <div className="intro-rule" aria-hidden="true" />
          <div className="intro-actions">
            <button className="intro-enter-btn" onClick={beginEnter}>
              折尽梅花
            </button>
          </div>
        </div>
        <p className="intro-credit">MY BIG BLUE FAT WHALE · 记忆</p>
      </div>

      <div className={"intro-blackout" + (black ? " on" : "")}>
        <div className="curtain-top">MY BIG BLUE FAT WHALE · 秋</div>
        {phase !== "idle" && (
          <div className="curtain-text" key={phase}>
            {phase === "a" ? "「难寄相思」" : "「……」"}
          </div>
        )}
      </div>
    </div>
  );
}
