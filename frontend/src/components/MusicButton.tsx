import { useEffect, useRef, useState } from "react";
import { MusicIcon } from "./MusicIcon";

type Props = {
  /** 正在播的曲子；开场用 intro-theme，进入应用后换成 app-theme */
  src?: string;
};

/**
 * 全局背景音乐：挂一次，开场用开场曲，进入应用后换曲子（保持播放状态）。
 * 浏览器会拦自动播放，所以先试一次，被拦了就在第一次点击/按键时补上。
 */
export function MusicButton({ src = "/autumn/intro-theme.mp3" }: Props) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const startedRef = useRef(false);
  const [playing, setPlaying] = useState(false);
  const srcRef = useRef(src);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = 0.4;

    const tryPlay = () => {
      if (startedRef.current) return;
      audio
        .play()
        .then(() => {
          startedRef.current = true;
          setPlaying(true);
        })
        .catch(() => {
          /* 等用户第一次交互 */
        });
    };

    tryPlay();

    const kick = () => tryPlay();
    window.addEventListener("pointerdown", kick, { once: true });
    window.addEventListener("keydown", kick, { once: true });
    window.addEventListener("touchstart", kick, { once: true });
    return () => {
      window.removeEventListener("pointerdown", kick);
      window.removeEventListener("keydown", kick);
      window.removeEventListener("touchstart", kick);
    };
  }, []);

  // 换曲子：开场 → 应用内
  useEffect(() => {
    if (srcRef.current === src) return;
    srcRef.current = src;
    const audio = audioRef.current;
    if (!audio) return;
    const wasPlaying = !audio.paused || startedRef.current;
    audio.src = src;
    audio.load();
    if (wasPlaying) {
      audio
        .play()
        .then(() => {
          startedRef.current = true;
          setPlaying(true);
        })
        .catch(() => {});
    }
  }, [src]);

  function toggle() {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      audio
        .play()
        .then(() => {
          startedRef.current = true;
          setPlaying(true);
        })
        .catch(() => {});
    } else {
      audio.pause();
      setPlaying(false);
    }
  }

  return (
    <>
      <button
        className={"music-fab" + (playing ? "" : " off")}
        onClick={toggle}
        title={playing ? "静音" : "播放音乐"}
        aria-label={playing ? "静音" : "播放音乐"}
      >
        <MusicIcon playing={playing} />
      </button>
      <audio ref={audioRef} src={src} loop preload="auto" />
    </>
  );
}
