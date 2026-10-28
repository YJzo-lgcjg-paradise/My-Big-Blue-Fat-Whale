package com.dayufish.service;

import com.dayufish.domain.Memory;
import com.dayufish.domain.Space;
import com.dayufish.repo.MemoryRepository;
import com.dayufish.repo.EpisodeRepository;
import com.dayufish.repo.TextHistoryRepository;
import com.dayufish.repo.TreasureRepository;
import com.dayufish.domain.Treasure;
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


    private static final String OTHER_SYSTEM = """
            你在整理一个长期记忆体里「他是谁」的资料卡。

            素材：这个记忆体已整理的事件日记（含关系备注、未完成线头）、对话原文抽样、已有的「我是谁」、以及双方独有的回忆。
            要求：
            1. 只填素材里明确出现过的事实；不确定的字段留空字符串，绝不推测、不补全。
               原文里出现的具体信息（姓名、年龄、职业、所在城市、家人、口味、习惯用的工具）优先采用。
            2. 不要写语气风格，不要描述两人怎么相处，只要事实性资料。
            3. 输出 JSON：
               {"profile":{"name":"","age":"","occupation":"","location":"","family":""},
                "likes":{"food":"","dislikes":"","tools":""},
                "pacts":["说好的称呼：…","约定：…","暗号：…"],
                "history":["他的过往经历，一条一句，最多 10 条"],
                "together":["他和我的经历，一条一句，最多 8 条"],
                "summary":"一句话概括他是谁，30 字以内"}
            4. profile 里：
               name 是他怎么被称呼的名字或昵称（真名、笔名、网名都算）；
               age 写年龄；原文只给了出生年月就写出生年月；
               occupation 写职业或身份，「在读 / 学生 / 求学」也算；
               location 写他所在的城市；
               family 写家人关系。都没有就留空。
            5. likes 里：food 写爱吃与讨厌吃（原文提过的食物、口味都算）；
               dislikes 写他讨厌的事；tools 写他习惯用的工具、软件或做法。
            6. pacts 每条形如「类别：内容」，最多 8 条；没有就给空数组。
            7. history 写他本人的过往（读书、搬家、做过的事、来处），只写素材里出现过的，每条不超过 30 字。
            8. together 写他和「我」一起经历过的事（一起改稿、约定、说过的难忘的话），每条不超过 30 字。
            """;

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
    private final TreasureRepository treasures;
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

    public StyleService(SpaceRepository spaces, MemoryRepository memories, EpisodeRepository episodes, TextHistoryRepository history, TreasureRepository treasures,
            ObjectMapper mapper, LlmSettings llm) {
        this.spaces = spaces;
        this.memories = memories;
        this.episodes = episodes;
        this.history = history;
        this.treasures = treasures;
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
        String old = "self".equals(kind) ? space.getPersonaSelf()
                : "other".equals(kind) ? space.getPersonaOther()
                : space.getStyleProfile();
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

    /** 结构化资料卡：{profile, likes, pacts, summary} */
    public String generateOther(Long spaceId) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        if (!llm.enabled()) {
            throw new IllegalStateException("没有配置模型 key，先在右上角 API 里填一个");
        }
        List<Episode> eps = episodes.findBySpaceIdOrderByStartedAtDesc(spaceId);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Episode e : eps) {
            if (!"ready".equals(e.getStatus()) || e.getDiary() == null || e.getDiary().isBlank()) {
                continue;
            }
            sb.append("【").append(e.getTitle()).append("】").append(clip(e.getDiary(), 300)).append('\n');
            if (e.getRelationNote() != null && !e.getRelationNote().isBlank()) {
                sb.append("关系：").append(clip(e.getRelationNote(), 120)).append('\n');
            }
            if (++n >= 40) {
                break;
            }
        }
        if (n == 0) {
            throw new IllegalStateException("这个记忆体还没有整理好的事件，先导入对话");
        }
        if (space.getPersonaSelf() != null && !space.getPersonaSelf().isBlank()) {
            sb.append("【我是谁】\n").append(clip(space.getPersonaSelf(), 500)).append('\n');
        }
        // 原文抽样：姓名、职业、口味这类事实往往只在原话里
        List<Memory> rows = memories.findBySpaceIdOrderByOccurredAtDesc(spaceId);
        StringBuilder raw2 = new StringBuilder();
        int taken = 0;
        for (Memory m : rows) {
            if (!"ready".equals(m.getStatus())) {
                continue;
            }
            String q = clip(m.getQuestion(), 160);
            String a = clip(m.getAnswer(), 260);
            if (q.isBlank() && a.isBlank()) {
                continue;
            }
            raw2.append("他：").append(q).append('\n').append("我：").append(a).append('\n');
            if (++taken >= 60) {
                break;
            }
        }
        if (raw2.length() > 0) {
            sb.append("【对话原文抽样（最近 ").append(taken).append(" 轮）】\n").append(raw2).append('\n');
        }
        List<Treasure> ts = treasures.findBySpaceIdOrderByIdDesc(spaceId);
        if (!ts.isEmpty()) {
            sb.append("【双方独有的回忆】\n");
            int c = 0;
            for (Treasure tr : ts) {
                sb.append("- ").append(tr.getTitle()).append("：").append(clip(tr.getText(), 120)).append('\n');
                if (++c >= 10) {
                    break;
                }
            }
        }

        String raw;
        try {
            raw = callJson(OTHER_SYSTEM, sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException("生成失败：" + e.getMessage());
        }
        JsonNode node;
        try {
            node = mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是 JSON");
        }
        if (!node.has("profile") && !node.has("summary")) {
            throw new IllegalStateException("模型没有给出资料卡");
        }
        remember(space, "other");
        space.setPersonaOther(node.toString());
        space.setPersonaOtherAt(Instant.now());
        spaces.save(space);
        return space.getPersonaOther();
    }

    private static final String OTHER_PROMPT_SYSTEM = """
            你在为长期记忆体写一段「他是谁」的提示词，这段会每轮发给模型。

            输入：他的资料卡（画像 / 喜好 / 约定 / 过往经历 / 和我的经历）、双方独有的回忆。
            要求：
            1. 写成 80–160 字的一段话，第一人称，像「我记住了这个人」。
            2. 不要罗列字段、不要分点、不要加标题。
            3. 必须带：怎么称呼他、他是什么样的人、我们之间约定过的暗号或规则。
            4. 只用输入里有的内容，不推测、不补全。
            5. 输出 JSON：{"prompt":"..."}
            """;

    /** 由资料卡生成「他是谁」的提示词片段（与资料卡分开生成） */
    public String generateOtherPrompt(Long spaceId) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        if (!llm.enabled()) {
            throw new IllegalStateException("没有配置模型 key");
        }
        String card = space.getPersonaOther() == null ? "" : space.getPersonaOther().trim();
        if (card.isBlank()) {
            throw new IllegalStateException("先生成资料卡");
        }
        StringBuilder sb = new StringBuilder("【资料卡】\n").append(clip(card, 1800)).append('\n');
        var ts = treasures.findBySpaceIdOrderByIdDesc(spaceId);
        if (!ts.isEmpty()) {
            sb.append("【回忆】\n");
            int c = 0;
            for (var tr : ts) {
                sb.append("- ").append(tr.getTitle()).append("：").append(clip(tr.getText(), 100)).append('\n');
                if (++c >= 8) {
                    break;
                }
            }
        }
        String raw;
        try {
            raw = callJson(OTHER_PROMPT_SYSTEM, sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException("生成失败：" + e.getMessage());
        }
        String text = "";
        try {
            text = mapper.readTree(raw).path("prompt").asText("").trim();
        } catch (Exception ignored) {
        }
        if (text.isBlank()) {
            throw new IllegalStateException("模型没有给出提示词");
        }
        space.setPersonaOtherPrompt(clip(text, 3900));
        space.setPersonaOtherPromptAt(Instant.now());
        spaces.save(space);
        return space.getPersonaOtherPrompt();
    }

    public String saveOtherPrompt(Long spaceId, String text) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        space.setPersonaOtherPrompt(text == null ? "" : clip(text, 3900));
        space.setPersonaOtherPromptAt(Instant.now());
        spaces.save(space);
        return space.getPersonaOtherPrompt();
    }

    public String otherPromptText(Long spaceId) {
        return spaces.findById(spaceId)
                .map(s -> s.getPersonaOtherPrompt() == null ? "" : s.getPersonaOtherPrompt()).orElse("");
    }

    public String saveOther(Long spaceId, String json) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new IllegalArgumentException("记忆体不存在"));
        remember(space, "other");
        space.setPersonaOther(json == null ? "" : clip(json, 3900));
        space.setPersonaOtherAt(Instant.now());
        spaces.save(space);
        return space.getPersonaOther();
    }

    public String otherText(Long spaceId) {
        return spaces.findById(spaceId).map(s -> s.getPersonaOther() == null ? "" : s.getPersonaOther()).orElse("");
    }

    /** 渲染成能直接塞进提示词的一段 */
    public String otherBlock(Long spaceId) {
        String prompt = otherPromptText(spaceId);
        if (prompt != null && !prompt.isBlank()) {
            StringBuilder sb = new StringBuilder(prompt.trim()).append('\n');
            var ts = treasures.findBySpaceIdOrderByIdDesc(spaceId);
            if (!ts.isEmpty()) {
                sb.append("〔可以提起的回忆〕只在与当前话题自然相关时提，不要硬带：\n");
                int c = 0;
                for (var tr : ts) {
                    sb.append("- ").append(tr.getTitle()).append("：").append(clip(tr.getText(), 90)).append('\n');
                    if (++c >= 6) {
                        break;
                    }
                }
            }
            return sb.toString().trim();
        }
        String raw = otherText(spaceId);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        JsonNode node;
        try {
            node = mapper.readTree(raw);
        } catch (Exception e) {
            return raw.trim(); // 老格式：纯文本
        }
        JsonNode profile = node.path("profile");
        JsonNode likes = node.path("likes");
        StringBuilder sb = new StringBuilder();
        List<String> facts = new ArrayList<>();
        addFact(facts, "称呼", profile.path("name").asText(""));
        addFact(facts, "年龄", profile.path("age").asText(""));
        addFact(facts, "职业", profile.path("occupation").asText(""));
        addFact(facts, "所在地", profile.path("location").asText(""));
        addFact(facts, "家人", profile.path("family").asText(""));
        if (!facts.isEmpty()) {
            sb.append("画像｜").append(String.join("；", facts)).append('\n');
        }
        List<String> likesList = new ArrayList<>();
        addFact(likesList, "爱吃", likes.path("food").asText(""));
        addFact(likesList, "讨厌", likes.path("dislikes").asText(""));
        addFact(likesList, "习惯", likes.path("tools").asText(""));
        if (!likesList.isEmpty()) {
            sb.append("喜好｜").append(String.join("；", likesList)).append('\n');
        }
        List<String> pacts = new ArrayList<>();
        for (JsonNode p : node.path("pacts")) {
            String s = p.asText("").trim();
            if (!s.isEmpty()) {
                pacts.add(s);
            }
        }
        if (!pacts.isEmpty()) {
            sb.append("约定｜").append(String.join("；", pacts)).append('\n');
        }
        String summary = node.path("summary").asText("").trim();
        if (!summary.isEmpty()) {
            sb.append("一句话：").append(summary).append('\n');
        }
        List<Treasure> ts = treasures.findBySpaceIdOrderByIdDesc(spaceId);
        if (!ts.isEmpty()) {
            sb.append("〔可以提起的回忆〕只在与当前话题自然相关时提，不要硬带：\n");
            int c = 0;
            for (Treasure tr : ts) {
                sb.append("- ").append(tr.getTitle()).append("：").append(clip(tr.getText(), 90)).append('\n');
                if (++c >= 6) {
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    private static void addFact(List<String> list, String label, String value) {
        if (value != null && !value.isBlank()) {
            list.add(label + "：" + value.trim());
        }
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
