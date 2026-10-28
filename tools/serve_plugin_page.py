# -*- coding: utf-8 -*-
"""录屏用的插件设置页小服务（自带数据）：/ 出 html，/api/* 由本进程直接应答。"""
import http.server
import json
import urllib.request

PAGE = r"C:\Users\user\Desktop\dsh-plugin-memory-bridge\lib\settings.html"
BACKEND = "http://127.0.0.1:8790"
SPACE = 11


def backend(path):
    with urllib.request.urlopen(BACKEND + path, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _json(self, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", "application/json; charset=utf-8")
        self.send_header("content-length", str(len(body)))
        self.send_header("cache-control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/api/spaces"):
            try:
                data = backend("/api/spaces")
                data["baseUrl"] = BACKEND
                self._json(data)
            except Exception as e:
                self._json({"spaces": [], "error": str(e)})
            return
        if self.path.startswith("/api/config"):
            self._json({"baseUrl": BACKEND, "spaceId": SPACE, "recentTurns": 10,
                        "sessionId": "", "takeover": True, "enabled": True})
            return
        if self.path.startswith("/api/pack"):
            try:
                req = urllib.request.Request(f"{BACKEND}/api/spaces/{SPACE}/episodes/pack",
                                             data=b'{"max":5}',
                                             headers={"content-type": "application/json"})
                with urllib.request.urlopen(req, timeout=60) as r:
                    self._json(json.loads(r.read().decode("utf-8")))
            except Exception as e:
                self._json({"text": "", "error": str(e)})
            return
        with open(PAGE, "rb") as f:
            body = f.read()
        self.send_response(200)
        self.send_header("content-type", "text/html; charset=utf-8")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("content-length") or 0)
        if length:
            self.rfile.read(length)
        self._json({"baseUrl": BACKEND, "spaceId": SPACE, "recentTurns": 10,
                    "sessionId": "", "takeover": True, "enabled": True})


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("127.0.0.1", 8793), Handler).serve_forever()
