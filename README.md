# My Big Blue Fat Whale 4.1 · 源码

给 AI 对话装一套外置记忆。本目录是可构建的源代码，不含编译产物、运行数据与密钥。

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
docs/               设计文档与说明
tools/              文档、视频、录屏脚本
```

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
├─ MyBigBlueFatWhale3.0.exe        build/host 里的 exe
├─ app.ico
├─ scripts/                        backend/scripts
└─ server/
   ├─ runtime/                     jlink 产物
   ├─ app/dayufish-backend-0.1.0.jar
   └─ data/                        首次启动自动生成（不要提交）
```

## 运行时说明

- 服务只监听 `127.0.0.1`，默认端口 8790（开发时 8787）
- 数据目录：`server\data`，包含 `memory.mv.db`、`llm.json`（密钥）、`media/`（图片）
- 同一个数据目录不要同时启动两个实例，H2 文件锁会冲突
- 首次启动需要配置 DeepSeek key；千问 OCR、智谱向量为可选

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

装完重启 DSH，设置里会出现「记忆桥」。

## 不含什么

- `node_modules/`、`target/`、`dist/`、`bin/`、`obj/`（构建产物）
- `server/data/`、`.env.local`（密钥与个人数据）
- 发布包里的 exe / jar / JRE

## 许可

个人项目，自用为主。

## 分支约定

每个版本一个分支，分支头就是该版本的代码：

- `main`：当前最新版本（4.1）
- `v4.1`、`v4.0`、`v3.0`……：对应各个版本，想回到哪一版，切到对应分支即可

标签 `v3.0` / `v4.0` / `v4.1` 同名保留，用于固定发布点。
