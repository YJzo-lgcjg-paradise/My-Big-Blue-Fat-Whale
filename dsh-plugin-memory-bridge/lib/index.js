/**
 * dsh-memory-bridge —— 把 Harness 的对话窗口接到「大肥鱼」记忆库。
 *
 * 能力：
 *   1. 设置页 GET /dsh-memory/           选一个记忆体，存到 ~/.dsh/memory-bridge.config.json
 *   2. GET  /dsh-memory/api/spaces       列出记忆库里的记忆体（代理本机 8790/8787）
 *   3. GET  /dsh-memory/api/pack         按事件组装一段「可粘进新窗口」的记忆骨架
 *   4. GET  /dsh-memory/api/recent       取该记忆体最近 N 条对话原文（默认 10）
 *   5. POST /dsh-memory/api/config       保存绑定
 *
 * 说明：自动注入到每一轮对话，需要宿主提供「请求前改写」的钩子；
 * 目前插件先把上下文准备好并暴露成 API，设置页里一键复制。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const PAGE = path.join(HERE, 'settings.html')
const DSH_HOME = process.env.DSH_HOME || path.join(os.homedir(), '.dsh')
const CFG_FILE = path.join(DSH_HOME, 'memory-bridge.config.json')

const DEFAULTS = {
  baseUrl: 'http://127.0.0.1:8790',
  spaceId: 0,
  spaceName: '',
  recentTurns: 10,
}

function readConfig() {
  let user = {}
  try {
    user = JSON.parse(fs.readFileSync(CFG_FILE, 'utf8'))
  } catch {
    /* 没配就用默认 */
  }
  return { ...DEFAULTS, ...user }
}

function writeConfig(next) {
  fs.mkdirSync(path.dirname(CFG_FILE), { recursive: true })
  fs.writeFileSync(CFG_FILE, JSON.stringify(next, null, 2) + '\n')
}

async function getJson(url, timeoutMs = 20000) {
  const ctl = new AbortController()
  const t = setTimeout(() => ctl.abort(), timeoutMs)
  try {
    const r = await fetch(url, { signal: ctl.signal, headers: { accept: 'application/json' } })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return await r.json()
  } finally {
    clearTimeout(t)
  }
}

async function postJson(url, body, timeoutMs = 60000) {
  const ctl = new AbortController()
  const t = setTimeout(() => ctl.abort(), timeoutMs)
  try {
    const r = await fetch(url, {
      method: 'POST',
      signal: ctl.signal,
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify(body || {}),
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return await r.json()
  } finally {
    clearTimeout(t)
  }
}

/** 候选地址：先设置里的，再退到 8790 / 8787 */
async function resolveBase() {
  const cfg = readConfig()
  const cands = [cfg.baseUrl, 'http://127.0.0.1:8790', 'http://127.0.0.1:8787'].filter(Boolean)
  for (const b of cands) {
    try {
      await getJson(`${b}/api/health`, 2000)
      return b
    } catch {
      /* 下一个 */
    }
  }
  throw new Error('记忆库没启动（8790/8787 都没响应）')
}

export default {
  name: 'dsh-memory-bridge',
  apply(ctx) {
    const route = (ctx.webServer || ctx.get?.('webServer'))
    if (!route || typeof route.register !== 'function') {
      return
    }

    const send = (res, code, data, type = 'application/json; charset=utf-8') => {
      res.statusCode = code
      res.setHeader('content-type', type)
      res.setHeader('cache-control', 'no-store')
      res.end(typeof data === 'string' ? data : JSON.stringify(data))
    }

    route.register({
      kind: 'prefix',
      path: '/dsh-memory',
      handler: async (req, res) => {
        const url = new URL(req.url || '/', 'http://127.0.0.1')
        const p = url.pathname.replace(/^\/dsh-memory/, '') || '/'

        try {
          if (p === '/' || p === '/index.html') {
            return send(res, 200, fs.readFileSync(PAGE, 'utf8'), 'text/html; charset=utf-8')
          }

          if (p === '/api/config' && req.method === 'GET') {
            return send(res, 200, readConfig())
          }

          if (p === '/api/config' && req.method === 'POST') {
            const chunks = []
            for await (const c of req) chunks.push(c)
            const body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
            const next = { ...readConfig(), ...body }
            writeConfig(next)
            return send(res, 200, next)
          }

          if (p === '/api/spaces') {
            const base = await resolveBase()
            const data = await getJson(`${base}/api/spaces`)
            return send(res, 200, { ...data, baseUrl: base })
          }

          if (p === '/api/pack') {
            const cfg = readConfig()
            const base = await resolveBase()
            const sid = Number(url.searchParams.get('spaceId') || cfg.spaceId || 0)
            if (!sid) throw new Error('还没选记忆体')
            const data = await postJson(`${base}/api/spaces/${sid}/episodes/pack`, { max: 5 })
            return send(res, 200, data)
          }

          if (p === '/api/recent') {
            const cfg = readConfig()
            const base = await resolveBase()
            const sid = Number(url.searchParams.get('spaceId') || cfg.spaceId || 0)
            const n = Number(url.searchParams.get('n') || cfg.recentTurns || 10)
            if (!sid) throw new Error('还没选记忆体')
            const data = await getJson(`${base}/api/memories?spaceId=${sid}&limit=${n}&offset=0`)
            return send(res, 200, data)
          }

          return send(res, 404, { error: 'not found' })
        } catch (e) {
          return send(res, 500, { error: String(e && e.message ? e.message : e) })
        }
      },
    })
  },
}
