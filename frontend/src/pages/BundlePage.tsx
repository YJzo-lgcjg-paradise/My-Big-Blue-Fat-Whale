import { useState } from "react";
import { api } from "../api";
import { copyText } from "../format";

type Props = {
  spaceId: number;
  toast: (msg: string) => void;
};

export function BundlePage({ spaceId, toast }: Props) {
  const [query, setQuery] = useState("");
  const [maxChars, setMaxChars] = useState(4000);
  const [includeTime, setIncludeTime] = useState(true);
  const [scopeAll, setScopeAll] = useState(false);
  const [text, setText] = useState("");
  const [meta, setMeta] = useState<{ count: number; chars: number; tokens: number; filtered: boolean } | null>(null);
  const [busy, setBusy] = useState(false);

  async function generateFromEpisodes() {
    setBusy(true);
    try {
      const res = await api.packEpisodes(spaceId, 5);
      setText(res.text);
      setMeta({ count: res.episodes.length, chars: res.text.length, tokens: res.estimatedTokens, filtered: false });
      if (!res.text) toast("还没有整理好的事件，先去导入对话");
    } catch (e: any) {
      toast(e.message || "生成失败");
    } finally {
      setBusy(false);
    }
  }

  async function generate() {
    setBusy(true);
    try {
      const res = await api.bundle(spaceId, query, maxChars, includeTime, scopeAll ? "all" : "auto");
      setText(res.bundle.text);
      setMeta({
        count: res.bundle.count,
        chars: res.bundle.chars,
        tokens: res.bundle.estimatedTokens,
        filtered: res.bundle.filtered,
      });
      if (!res.bundle.text) toast("这个范围里没有可用的记忆");
    } catch (e: any) {
      toast(e.message || "生成失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <div className="row" style={{ alignItems: "flex-start", gap: 18 }}>
        <div className="grow" style={{ flex: "1 1 420px" }}>
          <label className="field">
            <span>你要在新对话里聊什么？（留空 = 整段搬家）</span>
            <input
              className="input"
              placeholder="例如：继续修 PDF 的代码块分页问题"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && generate()}
            />
          </label>

          <div className="row" style={{ marginBottom: 14 }}>
            <label className="field" style={{ marginBottom: 0, minWidth: 150 }}>
              <span>字数上限</span>
              <select
                className="input"
                value={maxChars}
                onChange={(e) => setMaxChars(Number(e.target.value))}
              >
                <option value={2000}>2000 字 · 轻量</option>
                <option value={4000}>4000 字 · 推荐</option>
                <option value={6000}>6000 字 · 完整</option>
                <option value={10000}>10000 字 · 尽量多</option>
              </select>
            </label>

            <label className="switch" style={{ marginTop: 22 }}>
              <input type="checkbox" checked={includeTime} onChange={(e) => setIncludeTime(e.target.checked)} />
              带上时间
            </label>

            <label className="switch" style={{ marginTop: 22 }}>
              <input type="checkbox" checked={scopeAll} onChange={(e) => setScopeAll(e.target.checked)} />
              不筛选，全部塞进去
            </label>
          </div>

          <div className="row" style={{ marginBottom: 16 }}>
            <button className="btn" onClick={generateFromEpisodes} disabled={busy}>
              {busy ? "组装中…" : "按事件组装（推荐）"}
            </button>
            <button className="btn ghost" onClick={generate} disabled={busy}>
              {busy ? "生成中…" : "生成续命包"}
            </button>
            <button
              className="btn ghost"
              onClick={async () => {
                if (!text) return;
                const ok = await copyText(text);
                toast(ok ? "已复制，去粘贴到新对话开头" : "复制失败，请手动选中");
              }}
              disabled={!text}
            >
              复制
            </button>
            {meta && (
              <span className="hint">
                {meta.count} 条 · {meta.chars} 字 · 约 {meta.tokens} token
                {meta.filtered ? " · 已按相关度筛掉无关记忆" : ""}
              </span>
            )}
          </div>

          <textarea
            className="preview"
            readOnly
            value={text}
            placeholder="生成后会出现在这里：一段可以直接粘进 DeepSeek 新对话的记忆包。"
          />
        </div>

        <aside className="panel" style={{ flex: "0 0 280px", maxWidth: 320 }}>
          <h3>怎么用</h3>
          <p className="hint" style={{ marginBottom: 10 }}>
            1. 在旧对话里把没聊完的问题写进上面的输入框。
          </p>
          <p className="hint" style={{ marginBottom: 10 }}>
            2. 生成后点「复制」，粘到新对话的第一句话里。
          </p>
          <p className="hint" style={{ marginBottom: 10 }}>
            3. 新对话读完这段就知道前情，等于把记忆搬了过去。
          </p>
          <p className="hint">
            留空并勾选「全部塞进去」，就是把整个记忆体打包搬家。
          </p>
        </aside>
      </div>
    </section>
  );
}
