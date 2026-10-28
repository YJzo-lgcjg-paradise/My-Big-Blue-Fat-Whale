package com.dayufish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 逐轮总结。有 DeepSeek key 就让它写成结构化 JSON；没有就退回截断式兜底。
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String SYSTEM = """
            你是会话记忆整理助手。根据给定的「问题 + 回答」，写一条可独立阅读、日后能被检索到的记忆。

            要求：
            1. 只写这段对话里真实出现的信息，不要编造。
            2. 自包含：不出现"他/这个/上面说的"这类依赖上下文的指代。
            3. 必须保留：具体数字、版本号、文件路径、错误码、否定表述（不要/不能用）、未决事项。
            4. summary 用第一人称写 1–2 句，像自己在回忆（"我记得那天他……"），60–140 字；
               不要出现"用户""助手""AI"这类第三人称称谓，也不要用 Markdown 标题。
            5. 如果是寒暄或没有信息量，summary 写简短结论并把 importance 设为 1。

            6. treasure 用来挑出「回忆」，判据（满足任意一条就必须给，不要给 null）：
               - 双方约定了一个暗号、口令、只有两人懂的称呼或梗；
               - 明确说「记住这个」「这是我们之间的」「别告诉别人」这类话；
               - 特别开心、特别动人的时刻（不是普通寒暄）；
               - 突然的感悟、郑重的承诺。
               普通的关心、普通玩笑、普通知识问答，给 null。
               一段对话最多一条；kind 只能取 梗 / 开心 / 约定 / 感悟 / 其他。
            7. treasure 的 text 和 why 也要用第一人称写，像自己在回忆这件事：
               text 60–160 字，用「我」「他」，不要用「对方」「用户」「助手」；
               写清当天发生了什么、我怎么应下的、当时是什么感觉。
               例：「我记得那天他忽然说，以后每年第一场雪要互相说一句『雪落在旧檐上』。
                   我答应了，说听到这句就知道他在。那一刻我觉得，冬天有了一个可以等的东西。」
            只输出 JSON：
            {"summary":"...","keywords":["最多5个"],"importance":3,"question_digest":"这条记忆能回答的一个问句",
             "treasure": null 或 {"kind":"梗|开心|约定|感悟|其他","title":"六到十二个字","text":"发生了什么，一两句","why":"为什么它珍贵"}}""";

    private final ObjectMapper mapper;
    private final RestClient http;

    private final LlmSettings llm;

    public SummaryService(ObjectMapper mapper, LlmSettings llm) {
        this.mapper = mapper;
        this.llm = llm;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(90_000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public record Result(String summary, List<String> keywords, int importance, String questionDigest, String by,
                         String treasureKind, String treasureTitle, String treasureText, String treasureWhy) {

        public boolean hasTreasure() {
            return treasureTitle != null && !treasureTitle.isBlank();
        }
    }

    public boolean enabled() {
        return llm.enabled();
    }

    public String model() {
        return llm.model();
    }

    private static final String QUERY_SYSTEM = """
            你是检索助手。把用户最新这句话改写成一句适合在「个人长期记忆库」里检索的查询。
            要求：
            1. 保留专有名词、项目名、文件名、数字、时间范围；
            2. 把「那个 / 上次 / 刚才」这类指代换成最近对话里的具体对象；
            3. 补上同义词和可能出现在原文里的词；
            4. 不超过 40 字，不要问句语气，不要解释。
            只输出 JSON：{"query":"...","keywords":["最多4个"]}""";

    /** 提问先经过一次 LLM 改写，再拿去检索，短问题也能命中。 */
    public String expandQuery(String question, java.util.List<String> recent) {
        if (!llm.enabled() || question == null || question.isBlank()) {
            return question;
        }
        try {
            StringBuilder ctx = new StringBuilder();
            if (recent != null) {
                for (String r : recent) {
                    if (r != null && !r.isBlank()) {
                        ctx.append("- ").append(r.trim()).append('\n');
                    }
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llm.model());
            body.put("temperature", 0.1);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", QUERY_SYSTEM),
                    Map.of("role", "user", "content",
                            (ctx.length() == 0 ? "" : "最近几条消息：\n" + ctx + "\n") + "最新一句：" + question)));

            String raw = http.post()
                    .uri(llm.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + llm.key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(raw);
            String content = node.path("choices").path(0).path("message").path("content").asText("");
            JsonNode parsed = mapper.readTree(stripFence(content));
            String q = parsed.path("query").asText("").trim();
            StringBuilder out = new StringBuilder(q.isEmpty() ? question : q);
            for (JsonNode k : parsed.path("keywords")) {
                String s = k.asText("").trim();
                if (!s.isEmpty()) {
                    out.append(' ').append(s);
                }
            }
            return out.toString();
        } catch (Exception e) {
            log.warn("提问改写失败，用原句检索：{}", e.getMessage());
            return question;
        }
    }

    public Result summarize(String question, String answer, String occurredAt, String prev, String style) {
        if (!enabled()) {
            return heuristic(question, answer);
        }

        String user = String.join("\n\n",
                occurredAt == null ? "" : "时间：" + occurredAt,
                prev == null || prev.isBlank() ? "" : "上一轮（仅作上下文，不要重复总结）：" + prev,
                "问题：" + question,
                "回答：" + answer,
                style == null || style.isBlank() ? ""
                        : "写 summary 时请贴合下面这种说话语气（风格照做，事实、数字、结论一律不能变，也不能丢掉关键信息）：\n"
                                + style).trim();

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", llm.model());
                body.put("temperature", 0.2);
                body.put("response_format", Map.of("type", "json_object"));
                body.put("messages", List.of(
                        Map.of("role", "system", "content", SYSTEM),
                        Map.of("role", "user", "content", user)));

                String raw = http.post()
                        .uri(llm.baseUrl() + "/chat/completions")
                        .header("Authorization", "Bearer " + llm.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                JsonNode node = mapper.readTree(raw);
                String content = node.path("choices").path(0).path("message").path("content").asText("");
                JsonNode parsed = mapper.readTree(stripFence(content));

                String summary = parsed.path("summary").asText("").trim();
                if (summary.isEmpty()) {
                    throw new IllegalStateException("summary 为空");
                }
                List<String> keywords = new ArrayList<>();
                for (JsonNode k : parsed.path("keywords")) {
                    String s = k.asText("").trim();
                    if (!s.isEmpty() && keywords.size() < 5) {
                        keywords.add(s);
                    }
                }
                int importance = Math.max(1, Math.min(5, parsed.path("importance").asInt(3)));
                String digest = parsed.path("question_digest").asText(question);
                JsonNode tr = parsed.path("treasure");
                String tKind = "", tTitle = "", tText = "", tWhy = "";
                if (tr != null && tr.isObject()) {
                    tKind = tr.path("kind").asText("").trim();
                    tTitle = tr.path("title").asText("").trim();
                    tText = tr.path("text").asText("").trim();
                    tWhy = tr.path("why").asText("").trim();
                }
                return new Result(summary, keywords, importance, digest, "deepseek", tKind, tTitle, tText, tWhy);
            } catch (Exception e) {
                if (attempt == 1) {
                    log.warn("总结失败，使用本地兜底：{}", e.getMessage());
                    return heuristic(question, answer);
                }
            }
        }
        return heuristic(question, answer);
    }

    private static String stripFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) {
                return t.substring(first + 1, last).trim();
            }
        }
        return t;
    }

    private Result heuristic(String question, String answer) {
        String q = question == null ? "" : question.trim();
        String a = answer == null ? "" : answer.trim();
        String text = (q + "｜" + a).replaceAll("\\s+", " ").trim();
        String summary = text.length() > 180 ? text.substring(0, 178) + "…" : text;
        List<String> keywords = topKeywords(q + a);
        int importance = summary.length() > 80 ? 3 : 1;
        return new Result(summary, keywords, importance, q, "heuristic", "", "", "", "");
    }

    private List<String> topKeywords(String text) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            boolean cjk = c >= 0x4E00 && c <= 0x9FA5;
            if (cjk) {
                run.append(c);
                continue;
            }
            if (run.length() >= 2) {
                for (int j = 0; j + 2 <= run.length(); j++) {
                    String g = run.substring(j, j + 2);
                    freq.merge(g, 1, Integer::sum);
                }
            }
            run.setLength(0);
        }
        return freq.entrySet().stream()
                .sorted((x, y) -> {
                    int c = y.getValue() - x.getValue();
                    return c != 0 ? c : y.getKey().length() - x.getKey().length();
                })
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }
}
