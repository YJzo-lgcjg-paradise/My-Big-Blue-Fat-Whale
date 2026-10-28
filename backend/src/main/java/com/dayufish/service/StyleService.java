package com.dayufish.service;

import com.dayufish.domain.Memory;
import com.dayufish.domain.Space;
import com.dayufish.repo.MemoryRepository;
import com.dayufish.repo.EpisodeRepository;
import com.dayufish.repo.TextHistoryRepository;
import com.dayufish.domain.TextHistory;
import com.dayufish.domain.Episode;
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


    private static final String SELF_SYSTEM = """
            你在为一个长期记忆体写「我是谁」的第一人称自述。

            素材：这个记忆体里已经整理好的事件日记（含关系备注与未完成线头）、风格档案、以及用户手选的语气样本。
            要求：
            1. 只用素材里出现过的关系、承诺、称呼与事实；素材里没有的，一律不写。
            2. 第一人称，120-220 字，不要分点，不要标题，写成一段话。
            3. 写三件事：我是他的谁；我怎么对他；我不做什么。
            4. 不要用「用户」「助手」「AI」「模型」这些词。
            5. 不要抒情堆砌，不要承诺未来，只写已经发生和一直在做的事。
            6. 输出 JSON：{"self":"...","basis":["依据的事件标题", ...]}
               basis 里列出每一句能对应上的事件标题；对不上的句子就删掉，不要硬凑。
            """;

    public record Self(String text, List<String> basis, Instant updatedAt) {
    }

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
    private final EpisodeRepository episodes;
    private final TextHistoryRepository history;
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

    public StyleService(SpaceRepository spaces, MemoryRepository memories, EpisodeRepository episodes, TextHistoryRepository history, ObjectMapper mapper, LlmSettings llm) {
        this.spaces = spaces;
        this.memories = memories;
        this.episodes = episodes;
        this.history = history;
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


    private String callJson(String system, String user) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llm.model());
        body.put("temperature", 0.4);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
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
        return stripFence(content);
    }


    /** 覆盖前把旧版本存一份，供「查看过往」 */
    private void remember(Space space, String kind) {
        String old = "self".equals(kind) ? space.getPersonaSelf() : space.getStyleProfile();
        if (old != null && !old.isBlank()) {
            TextHistory h = new TextHistory();
            h.setSpaceId(space.getId());
            h.setKind(kind);
            h.setText(old.length() > 3900 ? old.substring(0, 3900) : old);
            history.save(h);
        }
    }

    public List<TextHistory> historyOf(Long spaceId, String kind) {
        return history.findBySpaceIdAndKindOrderByIdDesc(spaceId, kind);
    }

    /** 生成「我是谁」：只用已整理的事件与样本，避免读全文 */
    public Self generateSelf(Long spaceId) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        if (!llm.enabled()) {
            throw new IllegalStateException("没有配置模型 key，先在右上角 API 里填一个");
        }
        List<Episode> eps = episodes.findBySpaceIdOrderByStartedAtDesc(spaceId);
        if (eps.isEmpty()) {
            throw new IllegalStateException("这个记忆体还没有整理好的事件，先导入对话");
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Episode e : eps) {
            if (!"ready".equals(e.getStatus()) || e.getDiary() == null || e.getDiary().isBlank()) {
                continue;
            }
            sb.append("【").append(e.getTitle()).append("】").append(clip(e.getDiary(), 320)).append('\n');
            if (e.getRelationNote() != null && !e.getRelationNote().isBlank()) {
                sb.append("关系：").append(clip(e.getRelationNote(), 120)).append('\n');
            }
            if (e.getOpenThreads() != null && !e.getOpenThreads().isBlank()) {
                sb.append("没说完：").append(clip(e.getOpenThreads(), 120)).append('\n');
            }
            sb.append('\n');
            if (++n >= 40) {
                break;
            }
        }
        if (n == 0) {
            throw new IllegalStateException("还没有写好的事件日记");
        }
        if (space.getStyleProfile() != null && !space.getStyleProfile().isBlank()) {
            sb.append("【风格档案】\n").append(clip(space.getStyleProfile(), 600)).append('\n');
        }
        for (Picked p : picks(spaceId)) {
            sb.append("【样本】他：").append(clip(p.question(), 120))
              .append(" / 我：").append(clip(p.answer(), 180)).append('\n');
        }

        String raw;
        try {
            raw = callJson(SELF_SYSTEM, sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException("生成失败：" + e.getMessage());
        }
        JsonNode node;
        try {
            node = mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是 JSON");
        }
        String self = node.path("self").asText("").trim();
        if (self.isBlank()) {
            throw new IllegalStateException("模型没有给出自述");
        }
        List<String> basis = new ArrayList<>();
        for (JsonNode b : node.path("basis")) {
            String s = b.asText("").trim();
            if (!s.isBlank()) {
                basis.add(s);
            }
        }
        remember(space, "self");
        space.setPersonaSelf(clip(self, 3000));
        space.setPersonaSelfAt(Instant.now());
        spaces.save(space);
        return new Self(space.getPersonaSelf(), basis, space.getPersonaSelfAt());
    }

    public Self saveSelf(Long spaceId, String text) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        remember(space, "self");
        space.setPersonaSelf(text == null ? "" : clip(text, 3000));
        space.setPersonaSelfAt(Instant.now());
        spaces.save(space);
        return new Self(space.getPersonaSelf(), List.of(), space.getPersonaSelfAt());
    }

    public String selfText(Long spaceId) {
        return spaces.findById(spaceId).map(s -> s.getPersonaSelf() == null ? "" : s.getPersonaSelf()).orElse("");
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

            remember(space, "style");
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

    /** 挑语气样例的候选：不填 q 给最近的，填了就在全文里搜 */
    public List<Picked> candidates(Long spaceId, String q, int limit) {
        var page = org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(100, limit)));
        String kw = q == null ? "" : q.trim();
        List<Memory> rows = memories.search(spaceId, kw, page);
        List<Picked> out = new ArrayList<>();
        for (Memory m : rows) {
            out.add(new Picked(m.getId(), clip(m.getQuestion(), 120), clip(m.getAnswer(), 160)));
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
