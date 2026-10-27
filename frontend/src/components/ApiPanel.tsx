import { useEffect, useState } from "react";
import { api } from "../api";

type Props = {
  onClose: () => void;
  onChanged: () => void;
  toast: (msg: string) => void;
};

export function ApiPanel({ onClose, onChanged, toast }: Props) {
  const [status, setStatus] = useState<{
    configured: boolean;
    masked: string;
    model: string;
    baseUrl: string;
    qwenConfigured: boolean;
    qwenMasked: string;
    qwenModel: string;
  } | null>(null);
  const [qwenKey, setQwenKey] = useState("");
  const [qwenMsg, setQwenMsg] = useState("");
  const [key, setKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  useEffect(() => {
    api
      .llmStatus()
      .then(setStatus)
      .catch((e) => setMsg(e.message));
  }, []);

  async function save() {
    if (!key.trim()) {
      setMsg("先粘贴一个 key");
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      const s = await api.llmSetKey(key.trim());
      setStatus(s);
      setKey("");
      setMsg("已保存");
      toast("API key 已保存");
      onChanged();
    } catch (e: any) {
      setMsg(e.message || "保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function saveQwen() {
    if (!qwenKey.trim()) {
      setQwenMsg("先粘贴一个 key");
      return;
    }
    try {
      const r = await api.llmQwenKey(qwenKey.trim());
      setStatus((s) => (s ? { ...s, ...r } : s));
      setQwenKey("");
      setQwenMsg("已保存");
      toast("千问 OCR key 已保存");
    } catch (e: any) {
      setQwenMsg(e.message || "保存失败");
    }
  }

  async function clearQwen() {
    try {
      const r = await api.llmQwenClear();
      setStatus((s) => (s ? { ...s, ...r } : s));
      setQwenMsg("已清除");
    } catch (e: any) {
      setQwenMsg(e.message || "清除失败");
    }
  }

  async function test() {
    setBusy(true);
    setMsg("测试中…");
    try {
      const r = await api.llmTest();
      setMsg(r.ok ? "连接正常" : "失败：" + r.message);
    } catch (e: any) {
      setMsg(e.message || "测试失败");
    } finally {
      setBusy(false);
    }
  }

  async function clear() {
    setBusy(true);
    try {
      const s = await api.llmClear();
      setStatus(s);
      setMsg("已清除");
      onChanged();
    } catch (e: any) {
      setMsg(e.message || "清除失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="row" style={{ justifyContent: "space-between", marginBottom: 6 }}>
          <h3>模型 API</h3>
          <button className="icon-btn" onClick={onClose} aria-label="关闭">
            ✕
          </button>
        </div>

        <p className="hint" style={{ marginBottom: 14 }}>
          填你自己的 DeepSeek key，总结和 AI 对话都会用它。key 只存在本机
          <code>backend/data/llm.json</code>，不上传。
        </p>

        <div className="api-row">
          <span className="api-label">当前</span>
          <span className={status?.configured ? "api-ok" : "api-off"}>
            {status?.configured ? `已配置 ${status.masked}` : "未配置"}
          </span>
          <span className="hint" style={{ marginLeft: "auto" }}>
            {status?.model}
          </span>
        </div>

        <label className="field" style={{ marginTop: 14 }}>
          <span>DeepSeek API key</span>
          <input
            className="input"
            type="password"
            placeholder="sk-..."
            value={key}
            onChange={(e) => setKey(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && save()}
          />
        </label>

        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn" onClick={save} disabled={busy}>
            保存
          </button>
          <button className="btn ghost" onClick={test} disabled={busy || !status?.configured}>
            测试连接
          </button>
          {status?.configured && (
            <button className="btn ghost" onClick={clear} disabled={busy}>
              清除
            </button>
          )}
        </div>

        {msg && <p className="hint" style={{ marginTop: 12 }}>{msg}</p>}
        <div style={{ marginTop: 22, paddingTop: 16, borderTop: "1px solid var(--line)" }}>
          <p className="hint" style={{ marginBottom: 8 }}>
            看图识字（千问 OCR）——图片里如果有字，会读出来一起存进记忆。
          </p>
          <div className="api-row" style={{ marginBottom: 10 }}>
            <span className="api-label">当前</span>
            <span className={status?.qwenConfigured ? "api-ok" : "api-off"}>
              {status?.qwenConfigured ? `已配置 ${status.qwenMasked}` : "未配置"}
            </span>
            <span className="hint" style={{ marginLeft: "auto" }}>{status?.qwenModel}</span>
          </div>
          <label className="field" style={{ marginBottom: 8 }}>
            <span>千问 OCR key（DashScope sk-…）</span>
            <input
              className="input"
              type="password"
              placeholder="sk-..."
              value={qwenKey}
              onChange={(e) => setQwenKey(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && saveQwen()}
            />
          </label>
          <div className="row">
            <button className="btn ghost" onClick={saveQwen}>保存</button>
            {status?.qwenConfigured && (
              <button className="btn ghost" onClick={clearQwen}>清除</button>
            )}
          </div>
          {qwenMsg && <p className="hint" style={{ marginTop: 8 }}>{qwenMsg}</p>}
          <p className="hint" style={{ marginTop: 8 }}>
            拿 key：bailian.console.aliyun.com → API-KEY。没填也不影响别的功能，只是图片里的字读不出来。
          </p>
        </div>

        <p className="hint" style={{ marginTop: 12 }}>
          拿 key：platform.deepseek.com → API keys → 创建。填完点「测试连接」确认能用。
        </p>
      </div>
    </div>
  );
}
