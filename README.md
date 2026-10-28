# My Big Blue Fat Whale 4.1 · 源码

给 AI 对话装一套外置记忆。本目录是可构建的源代码，不含编译产物、运行数据与密钥。

一句话讲原理：对话按「事件」归纳成第一人称日记，真正发给模型的仍是原文；每轮只带一个固定预算的上下文池。

## 4.1 有什么新的

- **「他/她是谁」资料卡**：`我是谁` 下面新增一栏，写的是对话对面那个人
  - 五块：画像（名字/年龄/职业/所在地/家人）、喜好、约定、过往经历、和我的经历
  - 生成时读事件日记 + 最近 60 轮原文抽样 + 回忆，只填有依据的，不确定留空
- **提示词独立生成**：由资料卡写出一段第一人称的话，每轮真正发给模型的是这段
- **卡片与提示词分开重生成**：改卡片不影响提示词，改提示词不影响卡片
- **回忆只按标题列出**，并注明「只在与当前话题自然相关时提，不要硬带」
- **单实例保护**：全局互斥锁，避免两个进程共用一个数据目录把 H2 锁坏
- **任务栏图标修复**：去掉显式 AppUserModelID，任务栏改用窗口图标

## 目录

```
backend/            Spring Boot 3.3.5 / Java 21
  src/              源码（service / web / domain / repo）
  scripts/dsh.mjs   读取 DeepSeek Harness 本机会话
  pom.xml
frontend/           React 18 + Vite 6 + TypeScript
  src/              页面与组件
  index.html
  package.json
desktop-host/       .NET 8 WinForms + WebView2 外壳
  MbfwHost.csproj
  HostForm.cs
  Program.cs
  app.ico
dsh-plugin-memory-bridge/   DeepSeek Harness 插件（记忆桥）
  lib/index.js      宿主侧
  lib/client.js     客户端设置页
  cordis.patch.yml
docs/               设计文档、说明书、架构图
tools/              文档、视频、录屏脚本
```

## 关键代码在哪

| 想知道 | 看这里 |
|---|---|
| 每轮上下文怎么装 | `backend/.../service/MemoryService.java` → `chatContext()` |
| 事件怎么划分、日记怎么写 | `EpisodeService.java` |
| 我是谁 / 他她是谁 / 提示词 | `StyleService.java` → `generateSelf` `generateOther` `generateOtherPrompt` |
| 逐轮总结、回忆判定 | `SummaryService.java` |
| 对话调用 | `ChatService.java` |
| 接口清单 | `web/ApiController.java` |
| 设置页 | `frontend/src/pages/Settings.tsx` |
| 任务栏与窗口 | `desktop-host/Program.cs`、`HostForm.cs` |

## 构建

### 1. 前端

```bash
cd frontend
npm install
npm run build
```

产物在 `frontend/dist`。

### 2. 后端（把前端打进静态资源）

```bash
cd backend
# 先清空旧的静态资源，避免旧 bundle 残留
rm -rf src/main/resources/static
mkdir -p src/main/resources/static
cp -r ../frontend/dist/* src/main/resources/static/
mvn -DskipTests clean package
```

产物：`backend/target/dayufish-backend-0.1.0.jar`

> Windows 下 `mvn spring-boot:run` 在含中文/空格的路径里可能失败，直接 `java -jar` 更稳。

### 3. Java 运行时（jlink 裁剪）

```bash
jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files \
      --no-man-pages --compress=zip-6 --output build/runtime
```

### 4. 桌面外壳

```bash
dotnet publish desktop-host -c Release -r win-x64 --self-contained true \
       -p:ApplicationIcon=desktop-host/app.ico -o build/host
```

### 5. 组装发布包

```
发布包/
├─ MyBigBlueFatWhale4.1.exe       build/host 里的 exe
├─ app.ico
├─ 4.1 说明书.pdf
├─ scripts/                       backend/scripts
└─ server/
   ├─ runtime/                    jlink 产物
   ├─ app/dayufish-backend-0.1.0.jar
   └─ data/                       首次启动自动生成（不要提交）
```

## 运行时说明

- 服务只监听 `127.0.0.1`，默认端口 8790（开发时 8787）
- 数据目录：`server\data`，包含 `memory.mv.db`、`llm.json`（密钥）、`media/`（图片）
- **同一个数据目录只能开一个实例**：程序里有全局互斥锁，第二次启动只会弹提示
- 首次启动需要配置 DeepSeek key；千问 OCR、智谱向量为可选
- 智谱向量 key 写在 `data/llm.json` 的 `zhipuKey`，不在面板里；旧记忆要重算向量需调 `POST /api/reindex`

## 开发模式

```bash
# 后端（不要用 mvn spring-boot:run，中文路径会炸）
cd backend && java -jar target/dayufish-backend-0.1.0.jar     # 8787

# 前端（Vite 代理到 8787）
cd frontend && npm run dev                                    # 5173
```

前端通过 `location.port` 判断开发环境，打包后走同源相对路径。

## 插件

```bash
dsh plugin --profile desktop add "link:<本目录>\dsh-plugin-memory-bridge"
```

装完重启 DSH，设置里会出现「记忆桥」。插件有两种接管方式：

- **注入记忆**：记忆写进 `systemPrompt.context`，聊天记录里看不到
- **旧历史不发送**：`agent/pre-step` 里只发当前这一句

## 文档

- `docs/4.1 说明书.pdf`：面向使用者（发布包里同名）
- `docs/设计-人一样的记忆.md`：设计取舍
- `docs/项目说明.pdf`：3.0 时期的项目说明，保留作参考
- `docs/img/`：架构图、上下文装配图、前言插画
- `tools/`：说明书、技术文档、讲解词、视频与录屏脚本
  - `make_manual_4_0.py` 生成 4.1 说明书
  - `make_tech_doc.py` 生成技术架构文档
  - `make_intro_video2.py` / `make_seg3.py` 生成开场与第三段
  - `record_segments.mjs` 录 04–09 的实机演示

## 不含什么

- `node_modules/`、`target/`、`dist/`、`bin/`、`obj/`（构建产物）
- `server/data/`、`.env.local`（密钥与个人数据）
- 发布包里的 exe / jar / JRE

## 许可

个人项目，自用为主。

## 分支约定

每个版本一个分支，分支头就是该版本的代码：

- `main`：当前最新版本（4.1）
- `v4.1`、`v4.0`、`v3.0`：对应各个版本，想回到哪一版，切到对应分支即可

```bash
git switch v4.0     # 看 4.0 的代码
git switch main     # 回到最新
```
