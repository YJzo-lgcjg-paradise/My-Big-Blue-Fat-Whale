import { useEffect, useRef, useState } from "react";
import { MusicIcon } from "./MusicIcon";

/**
 * 全局背景音乐：挂一次，开场、黑幕、应用内都在。
 * 浏览器会拦自动播放，所以先试一次，被拦了就在第一次点击/按键时补上。
 */
export function MusicButton() {
  const audioRef = useRef<HTMLAudioElement>(null);
  const startedRef = useRef(false);
  const [playing, setPlaying] = useState(false);

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
      <audio ref={audioRef} src="/autumn/intro-theme.mp3" loop preload="auto" />
    </>
  );
}
