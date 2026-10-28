// 开发时前端在 5173、后端在 8787；打包后前后端同源，直接用相对路径。
const DEV = location.port === "5173" || location.port === "5174";
export const API = (import.meta as any).env?.VITE_API_BASE || (DEV ? "http://127.0.0.1:8787" : "");

export type Episode = {
  id: number;
  title: string;
  diary: string;
  moodFrom: string;
  moodTo: string;
  moodIntensity: number;
  moodBecause: string;
  relationNote: string;
  openThreads: string[];
  startedAt: string | null;
  lastAt: string | null;
  turnCount: number;
  importance: number;
  status: string;
  editedByUser: boolean;
};

export type Memory = {
  id: number;
  spaceId: number;
  source: string;
  occurredAt: string;
  question: string;
  answer: string;
  summary: string;
  keywords: string[];
  importance: number;
  status: "pending" | "ready" | "failed";
  error?: string;
  accessCount: number;
  lastAccessAt?: string;
  strength?: number;
  ageDays?: number;
  score?: number;
  parts?: { similarity: number; recency: number; strength: number; ageDays: number };
};

export type Space = {
  id: number;
  name: string;
  note: string;
  total: number;
  pending: number;
};

export type Health = {
  ok: boolean;
  llm: { enabled: boolean; model: string };
  embed: { online: boolean; model: string };
  pending: number;
};

export type DshSession = {
  id: string;
  title: string;
  cwd: string;
  createdAt: number;
  updatedAt: number;
  userTurns: number;
};

export type ChatMessage = { role: "user" | "assistant"; content: string };

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(API + path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error((data as any).error || `请求失败（${res.status}）`);
  return data as T;
}

export const api = {
  health: () => req<Health>("/api/health"),
  spaces: () => req<{ spaces: Space[] }>("/api/spaces"),
  createSpace: (name: string) =>
    req<{ id: number }>("/api/spaces", { method: "POST", body: JSON.stringify({ name }) }),
  deleteSpace: (id: number) => req(`/api/spaces/${id}`, { method: "DELETE" }),

  memories: (spaceId: number, q = "", limit = 30, offset = 0) =>
    req<{ items: Memory[]; total: number; offset: number; limit: number }>(
      `/api/memories?spaceId=${spaceId}&q=${encodeURIComponent(q)}&limit=${limit}&offset=${offset}`
    ),
  memory: (id: number) => req<{ item: Memory }>(`/api/memories/${id}`),
  updateMemory: (id: number, patch: Partial<Memory>) =>
    req<{ item: Memory }>(`/api/memories/${id}`, { method: "PATCH", body: JSON.stringify(patch) }),
  deleteMemory: (id: number) => req(`/api/memories/${id}`, { method: "DELETE" }),

  recall: (spaceId: number, query: string, deep = false, limit = 12) =>
    req<{ items: Memory[]; mode: string }>("/api/recall", {
      method: "POST",
      body: JSON.stringify({ spaceId, query, deep, limit }),
    }),

  importText: (spaceId: number, text: string, sourceName: string) =>
    req<{ importId: number; total: number }>("/api/import", {
      method: "POST",
      body: JSON.stringify({ spaceId, text, sourceName }),
    }),
  importProgress: (id: number) =>
    req<{ total: number; done: number; ready: number; failed: number; status: string }>(
      `/api/imports/${id}`
    ),

  webImport: (spaceId: number, url: string, text: string) =>
    req<{ importId: number; total: number }>("/api/web/import", {
      method: "POST",
      body: JSON.stringify({ spaceId, url, text }),
    }),

  dshSessions: () => req<{ sessions: DshSession[] }>("/api/dsh/sessions"),
  dshImport: (spaceId: number, sessionId: string) =>
    req<{ importId: number; total: number }>("/api/dsh/import", {
      method: "POST",
      body: JSON.stringify({ spaceId, sessionId }),
    }),

  deepseekConnect: (credential: string) =>
    req<{ ok: boolean; sessions: { id: string; title: string; updatedAt: number }[] }>(
      "/api/deepseek/connect",
      { method: "POST", body: JSON.stringify({ credential }) }
    ),
  deepseekImport: (spaceId: number, sessionId: string) =>
    req<{ importId: number; total: number }>("/api/deepseek/import", {
      method: "POST",
      body: JSON.stringify({ spaceId, sessionId }),
    }),
  deepseekDisconnect: () => req<{ ok: boolean }>("/api/deepseek/disconnect", { method: "POST" }),

  llmStatus: () =>
    req<{
      configured: boolean;
      masked: string;
      model: string;
      baseUrl: string;
      qwenConfigured: boolean;
      qwenMasked: string;
      qwenModel: string;
    }>("/api/llm"),
  llmSetKey: (key: string) =>
    req<{ configured: boolean; masked: string; model: string; baseUrl: string }>("/api/llm/key", {
      method: "POST",
      body: JSON.stringify({ key }),
    }),
  llmQwenKey: (key: string) =>
    req<{ qwenConfigured: boolean; qwenMasked: string; qwenModel: string }>("/api/llm/qwen", {
      method: "POST",
      body: JSON.stringify({ key }),
    }),
  llmQwenClear: () => req<{ qwenConfigured: boolean; qwenMasked: string }>("/api/llm/qwen/clear", { method: "POST" }),
  attachments: (memoryId: number) =>
    req<{ items: { id: number; kind: string; mime: string; ocrText: string; caption: string; status: string }[] }>(
      `/api/memories/${memoryId}/attachments`
    ),
  llmClear: () =>
    req<{ configured: boolean; masked: string; model: string; baseUrl: string }>("/api/llm/clear", {
      method: "POST",
    }),
  llmTest: () => req<{ ok: boolean; message: string }>("/api/llm/test", { method: "POST" }),

  exportSpace: (id: number) => req<any>(`/api/spaces/${id}/export`),
  importSpace: (payload: any) =>
    req<{ id: number }>("/api/spaces/import", { method: "POST", body: JSON.stringify(payload) }),
  getStyle: (spaceId: number) =>
    req<{ style: string; samples: number; updatedAt: string | null; picks: { id: number; question: string; answer: string }[] }>(
      `/api/spaces/${spaceId}/style`
    ),
  makeStyle: (spaceId: number) =>
    req<{ style: string; samples: number; updatedAt: string | null; picks: { id: number; question: string; answer: string }[] }>(
      `/api/spaces/${spaceId}/style`,
      { method: "POST" }
    ),
  history: (spaceId: number, kind: "self" | "style") =>
    req<{ items: { id: number; text: string; createdAt: string }[] }>(
      `/api/spaces/${spaceId}/history?kind=${kind}`
    ),
  styleCandidates: (spaceId: number, q: string, limit = 30) =>
    req<{ items: { id: number; question: string; answer: string }[] }>(
      `/api/spaces/${spaceId}/style/candidates?q=${encodeURIComponent(q)}&limit=${limit}`
    ),
  getContextMode: (spaceId: number) => req<{ mode: string }>(`/api/spaces/${spaceId}/context-mode`),
  setContextMode: (spaceId: number, mode: string) =>
    req<{ mode: string }>(`/api/spaces/${spaceId}/context-mode`, {
      method: "POST",
      body: JSON.stringify({ mode }),
    }),
  getSelf: (spaceId: number) => req<{ text: string }>(`/api/spaces/${spaceId}/persona/self`),
  generateSelf: (spaceId: number) =>
    req<{ text: string; basis: string[] }>(`/api/spaces/${spaceId}/persona/self`, { method: "POST" }),
  saveSelf: (spaceId: number, text: string) =>
    req<{ text: string }>(`/api/spaces/${spaceId}/persona/self/save`, {
      method: "POST",
      body: JSON.stringify({ text }),
    }),
  restyle: (spaceId: number) =>
    req<{ queued: number }>(`/api/spaces/${spaceId}/restyle`, { method: "POST" }),
  setStylePicks: (spaceId: number, ids: number[]) =>
    req<{ style: string; samples: number; updatedAt: string | null; picks: { id: number; question: string; answer: string }[] }>(
      `/api/spaces/${spaceId}/style/picks`,
      { method: "POST", body: JSON.stringify({ ids }) }
    ),
  mergeSpace: (fromId: number, toId: number) =>
    req<{ copied: number }>("/api/spaces/merge", {
      method: "POST",
      body: JSON.stringify({ fromId, toId }),
    }),

  treasures: (spaceId: number) =>
    req<{ items: { id: number; kind: string; title: string; text: string; why: string; createdAt: string }[] }>(
      `/api/spaces/${spaceId}/treasures`
    ),
  deleteTreasure: (id: number) => req<{ ok: boolean }>(`/api/treasures/${id}`, { method: "DELETE" }),
  episodes: (spaceId: number, limit = 20, offset = 0) =>
    req<{
      items: Episode[];
      total: number;
      ready: number;
      pending: number;
      failed: number;
      partitioning: boolean;
      offset: number;
      limit: number;
    }>(`/api/spaces/${spaceId}/episodes?limit=${limit}&offset=${offset}`),
  screenshot: (spaceId: number, filename: string, base64: string, mime: string) =>
    req<{ id: number }>(`/api/spaces/${spaceId}/screenshots`, {
      method: "POST",
      body: JSON.stringify({ filename, base64, mime }),
    }),
  episode: (id: number) =>
    req<{
      episode: Episode;
      turns: {
        id: number;
        occurredAt: string | null;
        question: string;
        answer: string;
        attachments?: { id: number; kind: string; caption: string; ocrText: string }[];
      }[];
    }>(`/api/episodes/${id}`),
  packEpisodes: (spaceId: number, max = 5) =>
    req<{ text: string; episodes: number[]; estimatedTokens: number }>(`/api/spaces/${spaceId}/episodes/pack`, {
      method: "POST",
      body: JSON.stringify({ max }),
    }),
  mergeEpisode: (fromId: number, into: number) =>
    req<{ episode: Episode }>(`/api/episodes/${fromId}/merge`, {
      method: "POST",
      body: JSON.stringify({ into }),
    }),

  bundle: (spaceId: number, query: string, maxChars: number, includeTime: boolean, scope = "auto") =>
    req<{ bundle: { text: string; count: number; chars: number; estimatedTokens: number; filtered: boolean } }>(
      "/api/bundle",
      {
        method: "POST",
        body: JSON.stringify({ spaceId, query, maxChars, includeTime, scope }),
      }
    ),

  chat: (spaceId: number, messages: ChatMessage[]) =>
    req<{ reply: string; model: string; memoryUsed: number; saved: boolean; searchQuery: string; styled: boolean }>("/api/chat", {
      method: "POST",
      body: JSON.stringify({ spaceId, messages }),
    }),

  stats: (spaceId: number) =>
    req<{ stats: { total: number; ready: number; pending: number; failed: number; firstAt?: string; lastAt?: string } }>(
      `/api/stats?spaceId=${spaceId}`
    ),
  reindex: (spaceId: number) =>
    req<{ updated: number }>("/api/reindex", { method: "POST", body: JSON.stringify({ spaceId }) }),
};
