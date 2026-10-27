import type { Memory } from "../api";
import { absTime, bars, relTime } from "../format";

type Props = {
  memory: Memory;
  onClick?: () => void;
  selected?: boolean;
};

export function MemoryCard({ memory, onClick }: Props) {
  const pending = memory.status === "pending";
  const failed = memory.status === "failed";
  const value = bars(memory.strength ?? memory.parts?.strength ?? 0.6);
  const score = memory.score;

  return (
    <article className="mem" onClick={onClick}>
      <div className="mem-head">
        <div className="mem-time">
          <span>{relTime(memory.occurredAt)}</span>
          <span className="abs">{absTime(memory.occurredAt)}</span>
          {pending && <span className="tag">整理中</span>}
          {failed && <span className="tag" style={{ color: "#d66a6a", borderColor: "#d66a6a55" }}>失败</span>}
        </div>
        {typeof score === "number" && <span className="mem-score">{score.toFixed(3)}</span>}
      </div>

      <p className={"mem-summary" + (pending ? " dim" : "")}>
        {memory.summary || (pending ? "正在生成总结…" : "（无总结）")}
      </p>

      <div className="mem-foot">
        <div className="tags">
          {(memory.keywords || []).slice(0, 4).map((k) => (
            <span className="tag gold" key={k}>
              {k}
            </span>
          ))}
          {memory.importance >= 4 && <span className="tag">重要</span>}
        </div>
        <div className="strength" title={`记忆强度 ${(memory.strength ?? 0.6).toFixed(2)}`}>
          {[1, 2, 3, 4, 5].map((i) => (
            <i key={i} className={i <= value ? "on" : ""} />
          ))}
        </div>
      </div>
    </article>
  );
}
