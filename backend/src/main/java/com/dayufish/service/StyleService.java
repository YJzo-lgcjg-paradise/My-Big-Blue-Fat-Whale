package com.dayufish.service;

import com.dayufish.domain.Memory;
import com.dayufish.domain.Space;
import com.dayufish.repo.MemoryRepository;
import com.dayufish.repo.SpaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 从一个记忆体的历史对话里总结「说话方式」，之后聊天时把它塞进 system prompt。
 */
@Service
public class StyleService {

    private static final Logger log = LoggerFactory.getLogger(StyleService.class);

    private static final String SYSTEM = """
            你在整理一份「这段关系怎么说话」的档案。下面是同一个记忆体里用户和助手的真实对话片段。
            请从双方怎么来往里总结出可以直接交给大模型当风格指令的描述，覆盖：
            1. 语气：整体冷暖、克制还是外放、有没有玩笑和调侃
            2. 互相的称呼：助手怎么称呼用户、用户怎么叫助手，有没有固定叫法或昵称
            3. 态度：彼此的姿态（朋友、同行、服务者、师徒……），亲疏程度
            4. 说话风格：句子长短、节奏、结构（分点/小标题/表格/括号补充）、开场与收尾、标点习惯、口癖
            5. 要避开的腔调：这段对话里明显没有的（客服腔、AI 腔、过度抒情、空泛概括等）
            写成一小段连贯的话，200-300 字，不要逐条罗列。
            只输出 JSON：{"style":"...","traits":["最多5个短标签"]}""";

    private final SpaceRepository spaces;
    private final MemoryRepository memories;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final LlmSettings llm;
    private final java.util.Set<Long> running = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread th = new Thread(r, "style-worker");
                th.setDaemon(true);
                return th;
            });

    public StyleService(SpaceRepository spaces, MemoryRepository memories, ObjectMapper mapper, LlmSettings llm) {
        this.spaces = spaces;
        this.memories = memories;
        this.mapper = mapper;
        this.llm = llm;
        this.http = RestClient.builder().build();
    }

    public record Profile(String style, List<String> traits, int samples, Instant updatedAt) {
    }

    public Profile get(Long spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        List<String> traits = new ArrayList<>();
        return new Profile(
                s.getStyleProfile() == null ? "" : s.getStyleProfile(),
                traits,
                s.getStyleSamples() == null ? 0 : s.getStyleSamples(),
                s.getStyleAt());
    }

    /** 取最近若干条记忆里的「用户原话」来推断风格。 */
    public Profile generate(Long spaceId) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        if (!llm.enabled()) {
            throw new IllegalStateException("没有配置模型 key，先在右上角 API 里填一个");
        }
        List<Memory> rows = memories.findBySpaceIdOrderByOccurredAtDesc(spaceId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("这个记忆体还没有记忆");
        }

        List<String> samples = new ArrayList<>();
        int taken = 0;
        for (Memory m : rows) {
            String q = clip(m.getQuestion(), 260);
            String a = clip(m.getAnswer(), 420);
            if (q.isBlank() && a.isBlank()) {
                continue;
            }
            samples.add("【第 " + (taken + 1) + " 轮】\n用户：" + q + "\n助手：" + a);
            if (++taken >= 80) {
                break;
            }
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("没有可用于分析的对话");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llm.model());
            body.put("temperature", 0.3);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM),
                    Map.of("role", "user", "content", "回答片段：\n" + String.join("\n", samples))));

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
            String style = parsed.path("style").asText("").trim();
            if (style.isEmpty()) {
                throw new IllegalStateException("模型没有返回风格描述");
            }
            List<String> traits = new ArrayList<>();
            for (JsonNode t : parsed.path("traits")) {
                String s = t.asText("").trim();
                if (!s.isEmpty() && traits.size() < 5) {
                    traits.add(s);
                }
            }

            space.setStyleProfile(style);
            space.setStyleSamples(taken);
            space.setStyleAt(Instant.now());
            spaces.save(space);
            return new Profile(style, traits, taken, space.getStyleAt());
        } catch (Exception e) {
            log.warn("总结说话方式失败：{}", e.getMessage());
            throw new IllegalStateException("总结失败：" + e.getMessage());
        }
    }

    /** 聊天后顺手补一份：第一次用、或者又攒了 40 条新记忆时重新总结，异步跑，不挡回复。 */
    public void ensureAsync(Long spaceId, long total) {
        if (!llm.enabled() || spaceId == null || total < 8) {
            return;
        }
        Space s = spaces.findById(spaceId).orElse(null);
        if (s == null) {
            return;
        }
        boolean missing = s.getStyleProfile() == null || s.getStyleProfile().isBlank();
        int done = s.getStyleSamples() == null ? 0 : s.getStyleSamples();
        boolean stale = total - done >= 40;
        if (!missing && !stale) {
            return;
        }
        if (!running.add(spaceId)) {
            return;
        }
        pool.submit(() -> {
            try {
                generate(spaceId);
                log.info("说话方式已更新：space={}", spaceId);
            } catch (Exception e) {
                log.warn("后台总结说话方式失败：{}", e.getMessage());
            } finally {
                running.remove(spaceId);
            }
        });
    }

    public record Picked(Long id, String question, String answer) {
    }

    public List<Picked> picks(Long spaceId) {
        Space s = spaces.findById(spaceId).orElse(null);
        List<Picked> out = new ArrayList<>();
        if (s == null || s.getStylePicks() == null || s.getStylePicks().isBlank()) {
            return out;
        }
        for (String part : s.getStylePicks().split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                memories.findById(id).ifPresent(m -> out.add(new Picked(
                        m.getId(), clip(m.getQuestion(), 300), clip(m.getAnswer(), 500))));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过
            }
        }
        return out;
    }

    public List<Picked> setPicks(Long spaceId, List<Long> ids) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        List<String> keep = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && keep.size() < 8) {
                    keep.add(String.valueOf(id));
                }
            }
        }
        s.setStylePicks(String.join(",", keep));
        spaces.save(s);
        return picks(spaceId);
    }

    /** 只取档案正文，给总结用。 */
    public String profileText(Long spaceId) {
        return spaces.findById(spaceId).map(Space::getStyleProfile).orElse("");
    }

    /** 聊天时用：风格档案 + 用户挑的样例。 */
    public String block(Long spaceId) {
        Space s = spaces.findById(spaceId).orElse(null);
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (s.getStyleProfile() != null && !s.getStyleProfile().isBlank()) {
            sb.append("【这个记忆体的说话方式】\n").append(s.getStyleProfile())
                    .append("\n回答时贴着这种语气和节奏，别端着。");
        }
        List<Picked> picked = picks(spaceId);
        if (!picked.isEmpty()) {
            sb.append("\n\n【说话方式参考】以下是自己在同一个记忆体里说过的原话，模仿这种语感，但不要照抄内容：");
            int i = 1;
            for (Picked p : picked) {
                sb.append("\n").append(i++).append(". 用户：").append(p.question())
                        .append("\n   助手：").append(p.answer());
            }
        }
        return sb.toString();
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
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
}
