package com.dayufish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 用用户自己的登录凭证去读 DeepSeek 网页版的会话列表与历史。
 *
 * 说明：网页版没有公开接口，这里走的是它自己前端用的 v0 接口，形状可能变。
 * 凭证（Cookie 或 userToken）只放在进程内存里，重启即失效，不写日志、不入库。
 */
@Service
public class DeepSeekWebService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekWebService.class);

    private final ObjectMapper mapper;
    private final RestClient http;
    private volatile String cookie = "";
    private volatile String token = "";

    @Value("${dayufish.deepseek-web.base-url:https://chat.deepseek.com}")
    private String baseUrl;

    public DeepSeekWebService(ObjectMapper mapper) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(30_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public record Session(String id, String title, long updatedAt) {
    }

    public record Imported(String title, List<ImportParser.Turn> turns) {
    }

    public boolean connected() {
        return !cookie.isBlank() || !token.isBlank();
    }

    public void clear() {
        cookie = "";
        token = "";
    }

    /**
     * 粘进来的可能是：userToken、整段 Cookie、localStorage 的 JSON、甚至整条 cURL。
     * 这里尽量把 token 和 cookie 都捞出来——网页版接口认的是 Authorization Bearer。
     */
    public void setCredential(String raw) {
        String s = raw == null ? "" : raw.trim();
        cookie = "";
        token = "";

        if (s.toLowerCase().contains("curl ")) {
            String c = unescapeCmd(s);
            cookie = extractHeader(c, "cookie");
            if (cookie.isBlank()) {
                cookie = extractShortFlag(c, "-b");
            }
            if (cookie.isBlank()) {
                cookie = extractShortFlag(c, "--cookie");
            }
            String auth = extractHeader(c, "authorization");
            token = auth.replaceAll("(?i)^bearer\\s+", "").trim();
            if (token.isBlank()) {
                java.util.regex.Matcher b = java.util.regex.Pattern
                        .compile("(?i)bearer\\s+([A-Za-z0-9._\\-]{16,})").matcher(c);
                if (b.find()) {
                    token = b.group(1);
                }
            }
            return;
        }

        if (s.startsWith("{") && s.contains("\"")) {
            // localStorage 里直接复制的 JSON
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"(?:userToken|token)\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            if (m.find()) {
                token = m.group(1).trim();
                return;
            }
        }

        if (s.matches("(?i)^bearer\\s+.*")) {
            token = s.replaceAll("(?i)^bearer\\s+", "").trim();
            return;
        }

        if (s.toLowerCase().startsWith("cookie:")) {
            s = s.substring(7).trim();
        }

        String fromCookie = extractToken(s);
        if (!fromCookie.isBlank()) {
            cookie = s;
            token = fromCookie;
            return;
        }

        // 没有等号、够长，基本就是 token 本身
        if (!s.contains("=") && s.length() > 30) {
            token = s;
            return;
        }

        cookie = s;
    }

    /** 浏览器「以 cURL 格式复制」的 cmd 版会带 ^ 转义，先把它们还原。 */
    private String unescapeCmd(String s) {
        String out = s
                .replace("^\\^\"", "\"")
                .replace("^\"", "\"")
                .replace("^%", "%")
                .replace("^^", "^");
        out = out.replaceAll("(?m)\\^\\s*$", " ");
        return out.replaceAll("\\s+", " ");
    }

    private String extractShortFlag(String curl, String flag) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)" + java.util.regex.Pattern.quote(flag) + "\\s+['\"]([^'\"]+)['\"]")
                .matcher(curl);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractHeader(String curl, String name) {
        // bash 版是 -H 'x: y'，cmd 版是 -H "x: y"，也有用 --header 的
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:-H|--header)\\s+['\"]" + name + ":\\s*([^'\"]+)['\"]").matcher(curl);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 兜底：整段里找 Bearer xxx
        if ("authorization".equalsIgnoreCase(name)) {
            java.util.regex.Matcher b = java.util.regex.Pattern
                    .compile("(?i)bearer\\s+([A-Za-z0-9._\\-]{20,})").matcher(curl);
            if (b.find()) {
                return "Bearer " + b.group(1);
            }
        }
        return "";
    }

    private String extractToken(String cookieText) {
        for (String part : cookieText.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && (kv[0].equalsIgnoreCase("userToken") || kv[0].equalsIgnoreCase("token"))) {
                return kv[1];
            }
        }
        return "";
    }

    public List<Session> listSessions() {
        // 网页版不同版本对参数要求不一样，挨个试，谁先有会话就用谁
        String[] variants = {
                "/api/v0/chat_session/fetch_page?lte_cursor.pinned=false",
                "/api/v0/chat_session/fetch_page?count=100",
                "/api/v0/chat_session/fetch_page"
        };
        for (String path : variants) {
            try {
                String body = call(path);
                List<Session> out = new ArrayList<>();
                collectSessions(safeParse(body), out);
                Map<String, Session> uniq = new LinkedHashMap<>();
                for (Session s : out) {
                    uniq.putIfAbsent(s.id(), s);
                }
                if (!uniq.isEmpty()) {
                    return new ArrayList<>(uniq.values());
                }
            } catch (Exception e) {
                log.warn("会话列表 {} 失败：{}", path, e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    public Imported fetch(String sessionId) {
        String body = call("/api/v0/chat/history_messages?chat_session_id=" + sessionId);
        JsonNode root = safeParse(body);
        List<ImportParser.Turn> turns = new ArrayList<>();
        List<Map<String, String>> raw = new ArrayList<>();

        // 已知结构：data.biz_data.chat_messages[]，字段是 message_id / role / content / inserted_at
        JsonNode messages = root.path("data").path("biz_data").path("chat_messages");
        if (messages.isArray()) {
            for (JsonNode m : messages) {
                String role = m.path("role").asText("");
                String text = textOfMessage(m);
                if (text.isEmpty()) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("role", role);
                row.put("text", text);
                row.put("time", String.valueOf(toMillis(m)));
                raw.add(row);
            }
        }
        // 兜底：结构变了就全树找
        if (raw.isEmpty()) {
            collectMessages(root, raw);
        }

        // 与 Harness 同一套归一化规则：多问一答合并提问、一问多答合并回答、
        // 无问有答挂到上一轮或单独成条、同题重复视作重新生成。
        String pendingQ = null;
        long pendingAt = 0;
        StringBuilder answer = new StringBuilder();
        long lastAt = 0;
        final long gapLimit = 10 * 60 * 1000L;
        for (Map<String, String> m : raw) {
            String role = m.getOrDefault("role", "").toLowerCase();
            String text = m.getOrDefault("text", "").trim();
            if (text.isEmpty() || role.contains("system")) {
                continue;
            }
            long at = Long.parseLong(m.getOrDefault("time", "0"));
            if (role.contains("user")) {
                if (pendingQ != null && answer.length() == 0) {
                    pendingQ = pendingQ + "\n" + text;
                } else {
                    if (pendingQ != null) {
                        turns.add(new ImportParser.Turn(pendingQ, answer.toString(), Instant.ofEpochMilli(pendingAt)));
                    }
                    pendingQ = text;
                    pendingAt = at > 0 ? at : System.currentTimeMillis();
                    answer.setLength(0);
                }
            } else {
                if (pendingQ == null) {
                    pendingQ = "（自主行动，承接上文）";
                    pendingAt = at > 0 ? at : System.currentTimeMillis();
                } else if (answer.length() > 0 && at > 0 && lastAt > 0 && at - lastAt > gapLimit) {
                    turns.add(new ImportParser.Turn(pendingQ, answer.toString(), Instant.ofEpochMilli(pendingAt)));
                    pendingQ = "（自主行动，承接上文）";
                    pendingAt = at;
                    answer.setLength(0);
                }
                if (answer.length() > 0) {
                    answer.append("\n\n");
                }
                answer.append(text);
                if (at > 0) {
                    lastAt = at;
                }
            }
        }
        if (pendingQ != null) {
            turns.add(new ImportParser.Turn(pendingQ, answer.toString(), Instant.ofEpochMilli(pendingAt)));
        }

        // 同一个问题连续出现（重新生成 / 编辑重发）只留最后一次回答，别把一次问答拆成两条记忆
        List<ImportParser.Turn> merged = new ArrayList<>();
        for (ImportParser.Turn t : turns) {
            if (!merged.isEmpty()
                    && merged.get(merged.size() - 1).question().trim().equals(t.question().trim())) {
                merged.set(merged.size() - 1, t);
                continue;
            }
            merged.add(t);
        }
        turns = merged;

        if (turns.isEmpty()) {
            String diag = "chat_messages=" + messages.size();
            if (messages.isArray() && messages.size() > 0) {
                List<String> keys = new ArrayList<>();
                messages.get(0).fieldNames().forEachRemaining(keys::add);
                diag += "，首条字段=" + String.join(",", keys);
                JsonNode c0 = messages.get(0).path("content");
                diag += "，content=" + c0.getNodeType();
                if (c0.isObject()) {
                    List<String> ck = new ArrayList<>();
                    c0.fieldNames().forEachRemaining(ck::add);
                    diag += "(" + String.join(",", ck) + ")";
                }
                if (c0.isTextual()) {
                    diag += "，content 长度=" + c0.asText().length();
                }
                JsonNode frs = messages.get(0).path("fragments");
                diag += "，fragments=" + frs.size();
                if (frs.isArray() && frs.size() > 0) {
                    List<String> fk = new ArrayList<>();
                    frs.get(0).fieldNames().forEachRemaining(fk::add);
                    diag += "(" + String.join(",", fk) + ")";
                }
            }
            throw new IllegalStateException("没有解析出问答（" + diag + "）");
        }
        return new Imported("DeepSeek 网页版", turns);
    }

    /** 消息正文：老版本在 content，新版本拆在 fragments[] 里。 */
    private String textOfMessage(JsonNode m) {
        if (m.hasNonNull("content")) {
            String t = flatten(m.get("content")).trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        JsonNode fragments = m.path("fragments");
        if (!fragments.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode f : fragments) {
            String type = f.path("type").asText("").toUpperCase();
            if (type.contains("THINK") || type.contains("REASON")) {
                continue;
            }
            String t = flatten(f.has("content") ? f.get("content") : f.path("text")).trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t);
        }
        return sb.toString().trim();
    }

    /** inserted_at 是「秒 + 小数」，也可能已经是毫秒，统一成毫秒。 */
    private long toMillis(JsonNode m) {
        JsonNode t = m.has("inserted_at") ? m.get("inserted_at") : m.path("created_at");
        if (t.isMissingNode() || t.isNull()) {
            return 0;
        }
        double v = t.asDouble(0);
        if (v <= 0) {
            return 0;
        }
        return v < 1_000_000_000_000d ? (long) (v * 1000) : (long) v;
    }

    private String call(String path) {
        try {
            String raw = http.get()
                    .uri(baseUrl + path)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", baseUrl + "/")
                    .header("x-app-version", "20241129.1")
                    .header("x-client-platform", "web")
                    .header("x-client-version", "1.0.0-always")
                    .header("x-client-locale", "zh_CN")
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36")
                    .headers(h -> {
                        if (!cookie.isBlank()) {
                            h.set("Cookie", cookie);
                        }
                        if (!token.isBlank()) {
                            h.set("Authorization", "Bearer " + token);
                        }
                    })
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("接口返回为空");
            }
            JsonNode node = mapper.readTree(raw);
            if (node.has("code") && node.get("code").asInt() != 0) {
                int code = node.get("code").asInt();
                String msg = node.path("msg").asText("");
                if (code == 40003) {
                    throw new IllegalStateException(
                            "鉴权失败（40003 INVALID_TOKEN）：DeepSeek 网页版认的是 localStorage 里的 userToken，"
                                    + "不是 Cookie。F12 → Application → Local Storage → https://chat.deepseek.com → "
                                    + "复制 userToken 的值，粘到输入框里再连一次。");
                }
                throw new IllegalStateException("接口返回 code=" + code + " msg=" + msg);
            }
            return raw;
        } catch (Exception e) {
            log.warn("DeepSeek 网页版接口失败：{}", e.getMessage());
            throw new IllegalStateException("读取失败：" + e.getMessage());
        }
    }

    private JsonNode safeParse(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("返回不是 JSON：" + raw.substring(0, Math.min(160, raw.length())));
        }
    }

    /** 会话列表可能在 biz_data.chat_sessions 之类的位置，递归找带 id + title 的对象。 */
    private void collectSessions(JsonNode node, List<Session> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode id = node.has("chat_session_id") ? node.get("chat_session_id") : node.get("id");
            JsonNode title = node.has("title") ? node.get("title") : node.get("name");
            if (id != null && title != null && id.isTextual() && title.isTextual()) {
                out.add(new Session(id.asText(), title.asText(), node.path("updated_at").asLong(0)));
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                collectSessions(it.next().getValue(), out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectSessions(child, out);
            }
        }
    }

    /** 历史消息同理：递归找有 role + content 的对象。 */
    private void collectMessages(JsonNode node, List<Map<String, String>> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode role = node.get("role");
            JsonNode content = node.has("content") ? node.get("content") : node.get("text");
            if (role != null && content != null && role.isTextual()) {
                String text = flatten(content);
                if (!text.isBlank()) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("role", role.asText());
                    m.put("text", text);
                    long t = node.path("created_at").asLong(0);
                    if (t == 0) {
                        t = node.path("inserted_at").asLong(0);
                    }
                    m.put("time", String.valueOf(t));
                    out.add(m);
                }
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                collectMessages(it.next().getValue(), out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectMessages(child, out);
            }
        }
    }

    private String flatten(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    sb.append(part.asText());
                } else if (part.has("content") && part.get("content").isTextual()) {
                    sb.append(part.get("content").asText());
                } else if (part.has("text") && part.get("text").isTextual()) {
                    sb.append(part.get("text").asText());
                }
            }
        } else if (content.has("content")) {
            return flatten(content.get("content"));
        } else if (content.has("fragments")) {
            return flatten(content.get("fragments"));
        } else if (content.has("text") && content.get("text").isTextual()) {
            return content.get("text").asText();
        }
        return sb.toString();
    }
}
