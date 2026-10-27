# dsh-memory-bridge

把 DeepSeek Harness 的对话窗口接到「大肥鱼」记忆库。

## 它做什么

- 在 DSH 的插件设置里出现一页「大肥鱼 · 记忆桥」
- 选一个记忆体，绑定到这个窗口
- 一键生成「记忆骨架」：人格锚点 + 最近事件的日记 + 没说完的线头 + 他给你看过的东西
- 另提供接口，按需取该记忆体最近 10 条对话原文

## 安装

先把大肥鱼桌面版启动（默认 `http://127.0.0.1:8790`）。

```bat
dsh plugin --profile desktop add "link:C:\Users\user\Desktop\My Big Blue Fat Whale · Forever\dsh-plugin-memory-bridge"
```

装完在 DSH 里打开：

```
http://127.0.0.1:<DSH 端口>/dsh-memory/
```

## 设置项

| 项 | 说明 |
|---|---|
| 地址 | 记忆库地址，默认 8790，会自动退到 8787 |
| 记忆体 | 这个窗口使用的记忆体 |
| 最近对话条数 | 默认 10 |

配置存在 `~/.dsh/memory-bridge.config.json`。

## 接口

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/dsh-memory/api/spaces` | 列出记忆体 |
| GET | `/dsh-memory/api/pack` | 按事件组装记忆骨架 |
| GET | `/dsh-memory/api/recent?n=10` | 最近 N 条对话原文 |
| GET/POST | `/dsh-memory/api/config` | 读 / 写绑定 |

## 现在的边界

插件的「生成骨架 + 复制」已经可用，但**还没有做到每轮自动注入**——
那需要宿主提供「请求前改写上下文」的钩子。DSH 的插件 API 里，设置页
（`webServer.register`）和客户端插槽（`slots.inject`）是确认可用的，
但对话请求链路上的挂点还需要确认。

在确认之前，这个插件的用法是：进设置页 → 选记忆体 → 生成骨架 → 复制 →
粘到新窗口的第一句。效果和手动贴续命包一样，只是不用离开 DSH。

## 下一步

1. 找到 DSH 的请求前钩子（`ctx.on('agent/...')` 之类），把骨架自动塞进 system prompt
2. 绑定做成「按会话」而不是「按插件」：不同窗口绑不同记忆体
3. 窗口里说「你上次说的那句原话」时，调 `/api/recall` 做一次深度回忆
