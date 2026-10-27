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
 * AI 对话框：带着当前记忆体的检索结果回答。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM = """
            你是用户的长期助手，名下带着一份"记忆体"——过去对话沉淀下来的记忆。
            下面会给出检索到的记忆原文（摘要只用于检索，不会给你看）。规则：
            1. 回答必须基于这些原文：先想清楚原文说了什么，再作答；原文没写的不要编。
            2. 涉及具体参数、代码、结论但原文不完整时，直说"我记得不太准"，并指出缺哪一块。
            3. 原文与用户当前说法冲突时，以当前说法为准，并说明哪里对不上。
            4. 回答简洁，中文，不要整段复述原文，除非用户要求。
            """;

    private final ObjectMapper mapper;
    private final RestClient http;

    private final LlmSettings llm;

    public ChatService(ObjectMapper mapper, LlmSettings llm) {
        this.mapper = mapper;
        this.llm = llm;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(120_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public record Turn(String role, String content) {
    }

    public record Reply(String reply, List<Map<String, Object>> used, String model) {
    }

    /** 用一次最小请求验证 key 是否可用。 */
    public Map<String, Object> testKey() {
        if (!llm.enabled()) {
            return Map.of("ok", false, "message", "还没有填 key");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llm.model());
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            body.put("max_tokens", 1);
            http.post()
                    .uri(llm.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + llm.key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return Map.of("ok", true, "message", "连接正常");
        } catch (Exception e) {
            return Map.of("ok", false, "message", String.valueOf(e.getMessage()));
        }
    }

    public Reply chat(String memoryBlock, String styleBlock, List<Turn> history) {
        if (!llm.enabled()) {
            return new Reply("还没有配置 DeepSeek key，点右上角 API 填一个就能聊。", List.of(), "offline");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        String extra = (styleBlock == null || styleBlock.isBlank() ? "" : "\n\n" + styleBlock)
                + (memoryBlock.isBlank() ? "" : "\n\n" + memoryBlock);
        messages.add(Map.of("role", "system", "content", SYSTEM + extra));
        for (Turn t : history) {
            messages.add(Map.of("role", t.role(), "content", t.content()));
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llm.model());
            body.put("temperature", 0.6);
            body.put("messages", messages);

            String raw = http.post()
                    .uri(llm.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + llm.key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(raw);
            String reply = node.path("choices").path(0).path("message").path("content").asText("");
            return new Reply(reply, List.of(), llm.model());
        } catch (Exception e) {
            log.warn("对话失败：{}", e.getMessage());
            return new Reply("调用模型失败了：" + e.getMessage(), List.of(), llm.model());
        }
    }
}
