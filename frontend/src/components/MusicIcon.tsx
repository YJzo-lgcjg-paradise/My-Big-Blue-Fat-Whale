type Props = {
  playing: boolean;
};

/**
 * 音乐图标：自己画的音符 + 斜线，斜线中点正好压在符干上（居中），
 * 比 "♪⃠" 这种组合字符稳，不会因为字体不同跑偏。
 */
export function MusicIcon({ playing }: Props) {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true" focusable="false">
      <ellipse cx="7.1" cy="14.3" rx="2.7" ry="2.15" transform="rotate(-18 7.1 14.3)" fill="currentColor" />
      <rect x="9.2" y="5.1" width="1.55" height="9.3" rx="0.75" fill="currentColor" />
      <path
        d="M10.75 5.2c2.55.55 3.95 1.85 4.15 3.95"
        stroke="currentColor"
        strokeWidth="1.5"
        fill="none"
        strokeLinecap="round"
      />
      {!playing && (
        <line
          x1="4.4"
          y1="4.2"
          x2="15.5"
          y2="15.4"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
        />
      )}
    </svg>
  );
}
