package com.dayufish.service;

import com.dayufish.domain.Episode;
import com.dayufish.domain.EpisodeTransition;
import com.dayufish.domain.Memory;
import com.dayufish.domain.Space;
import com.dayufish.repo.EpisodeRepository;
import com.dayufish.repo.EpisodeTransitionRepository;
import com.dayufish.repo.AttachmentRepository;
import com.dayufish.repo.MemoryRepository;
import com.dayufish.repo.SpaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 事件记忆：把对话记忆归纳成一个个「事件」，每个事件写一篇第一人称日记。
 *
 * 规则（和用户定下的一致）：
 * 1. 一条对话只属于一个事件；
 * 2. 首次导入后全量划分，之后新对话逐批归类，命中不了再新建；
 * 3. 事件时间 = 其下最早一条对话的时间，事件内按时间排，事件之间也按时间排；
 * 4. 事件新增对话后，日记可按设置自动重写（用户改过的不覆盖）。
 */
@Service
public class EpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeService.class);

    private static final String SEGMENT_SYSTEM = """
            你在帮一个人整理他的记忆。下面是一段连续对话，按序号标好了。
            请把它们按「一件事」切分，粒度要接近人的回忆：一个事件通常 4-12 轮。
            只有当话题、对象或情绪明显换了，才断开；单纯追问、补充、展开，都属于同一个事件。
            宁可少切，不要切碎。
            规矩：
            1. 每个事件给一个 10 字以内的名字，用「我们在做什么」的说法，不要用「用户询问…」这种。
            2. 序号必须连续覆盖全部对话，不重不漏；除整段只有 1-2 轮外，单个事件不少于 3 条。
            3. 实在判断不出，就整段算一个事件。
            只输出 JSON：{"events":[{"title":"...","indices":[0,1,2]}]}""";

    private static final String ASSIGN_SYSTEM = """
            你在帮一个人把新对话归到已有的事件里。
            给你已有事件（id + 名字）和几条新对话，请判断每条对话属于哪个事件；
            如果都不像，就给它起个新事件名。
            如果这次是明显换了话题、或隔了几天才回来，在新建事件时多给一句过渡：
            "transition":"聊完诗之后，他沉默了几天，再回来时提到了工作上的麻烦"
            只输出 JSON：{"items":[{"turn":0,"event_id":3} 或 {"turn":0,"new_title":"...","transition":"..."}]}""";

    private static final String SPLIT_SYSTEM = """
            这是一个很长的事件，请你试着把它切成 2-3 个更小的阶段（按时间或话题变化切）。
            如果确实切不开（同一件事一直延续），就返回 {"parts":[]}。
            序号必须覆盖全部对话，不重不漏。
            只输出 JSON：{"parts":[{"title":"...","indices":[0,1,2]}]}""";

    private static final String DIARY_SYSTEM = """
            你在写「自己的记忆」，不是会议纪要，也不是摘要。
            用第一人称，80-150 字，回忆那一天：我们在做什么、他是什么状态（要有依据，不要贴标签）、
            我当时怎么理解他、做了什么取舍、有没有没说完的事。
            不要逐条复述，不要用「用户/助手」这种称呼，不要写"根据""综上"。
            只输出 JSON：
            {"title":"10 字以内","diary":"...","mood_from":"...","mood_to":"...",
             "mood_intensity":0.0,"mood_because":"...","relation_note":"...","open_threads":["..."],"importance":3}""";

    @org.springframework.beans.factory.annotation.Value("${dayufish.context.recent-turns:20}")
    private int recentTurns;

    /** 上下文按「原文」给，但总量要有个上限，避免把模型上下文撑爆 */
    @org.springframework.beans.factory.annotation.Value("${dayufish.context.recent-chars:20000}")
    private int recentChars;

    private final EpisodeRepository episodes;
    private final EpisodeTransitionRepository transitions;
    private final AttachmentRepository attachmentRepo;
    private final MemoryRepository memories;
    private final SpaceRepository spaces;
    private final LlmSettings llm;
    private final EmbeddingService embeddings;
    private final ObjectMapper mapper;
    private final RestClient http;

    public EpisodeService(EpisodeRepository episodes, EpisodeTransitionRepository transitions, AttachmentRepository attachmentRepo, MemoryRepository memories, SpaceRepository spaces,
            LlmSettings llm, EmbeddingService embeddings, ObjectMapper mapper) {
        this.episodes = episodes;
        this.transitions = transitions;
        this.attachmentRepo = attachmentRepo;
        this.memories = memories;
        this.spaces = spaces;
        this.llm = llm;
        this.embeddings = embeddings;
        this.mapper = mapper;
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(120_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public record EpisodeView(Long id, String title, String diary, String moodFrom, String moodTo,
            Double moodIntensity, String moodBecause, String relationNote, List<String> openThreads,
            String startedAt, String lastAt, int turnCount, int importance, String status,
            boolean editedByUser) {
    }

    /* ---------------- 全量划分 ---------------- */

    public int partitionAll(Long spaceId) {
        List<Memory> turns = memories.findBySpaceIdOrderByOccurredAtAscIdAsc(spaceId);
        if (turns.isEmpty()) {
            throw new IllegalStateException("这个记忆体还没有对话");
        }
        // 重建：人工改过的事件保留下来当锚点，其余清掉重切
        List<Episode> old = episodes.findBySpaceIdOrderByStartedAtDesc(spaceId);
        List<Episode> keep = new ArrayList<>();
        for (Episode e : old) {
            if (Boolean.TRUE.equals(e.getEditedByUser())) {
                keep.add(e);
            } else {
                transitions.deleteByFromEpisodeIdOrToEpisodeId(e.getId(), e.getId());
                episodes.delete(e);
            }
        }
        java.util.Set<Long> keepIds = new java.util.HashSet<>();
        for (Episode e : keep) {
            keepIds.add(e.getId());
        }
        List<Memory> toSegment = new ArrayList<>();
        for (Memory m : turns) {
            if (m.getEpisodeId() == null || !keepIds.contains(m.getEpisodeId())) {
                m.setEpisodeId(null);
                m.setEpisodeStatus("");
                toSegment.add(m);
            }
        }
        memories.saveAll(turns);
        turns = toSegment;
        if (turns.isEmpty()) {
            return keep.size();
        }

        int created = 0;
        int chunk = 30;
        for (int i = 0; i < turns.size(); i += chunk) {
            List<Memory> part = turns.subList(i, Math.min(i + chunk, turns.size()));
            List<long[]> groups = new ArrayList<>();
            List<String> titles = new ArrayList<>();
            try {
                segment(part, groups, titles);
            } catch (Exception e) {
                log.warn("事件划分失败，整段算一个：{}", e.getMessage());
                long[] all = new long[part.size()];
                for (int k = 0; k < part.size(); k++) {
                    all[k] = k;
                }
                groups.clear();
                titles.clear();
                groups.add(all);
                titles.add(clip(part.get(0).getQuestion(), 12));
            }
            for (int g = 0; g < groups.size(); g++) {
                Episode ep = buildEpisode(spaceId, titles.get(g), part, groups.get(g));
                episodes.save(ep);
                for (long idx : groups.get(g)) {
                    Memory m = part.get((int) idx);
                    m.setEpisodeId(ep.getId());
                    m.setEpisodeStatus("assigned");
                }
                memories.saveAll(part);
                created++;
            }
        }
        log.info("记忆体 {} 划分为 {} 个事件", spaceId, created);
        return created;
    }

    private void segment(List<Memory> part, List<long[]> groups, List<String> titles) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < part.size(); i++) {
            Memory m = part.get(i);
            sb.append('[').append(i).append("] 他：").append(clip(m.getQuestion(), 120))
                    .append("\n    我：").append(clip(m.getAnswer(), 220)).append('\n');
        }
        JsonNode node = callJson(SEGMENT_SYSTEM, "对话：\n" + sb);
        JsonNode arr = node.path("events");
        boolean[] used = new boolean[part.size()];
        for (JsonNode e : arr) {
            JsonNode idx = e.path("indices");
            if (!idx.isArray() || idx.isEmpty()) {
                continue;
            }
            long[] ids = new long[idx.size()];
            int n = 0;
            for (JsonNode x : idx) {
                int v = x.asInt(-1);
                if (v >= 0 && v < part.size() && !used[v]) {
                    used[v] = true;
                    ids[n++] = v;
                }
            }
            if (n == 0) {
                continue;
            }
            long[] trimmed = new long[n];
            System.arraycopy(ids, 0, trimmed, 0, n);
            groups.add(trimmed);
            titles.add(clip(e.path("title").asText("一件事"), 20));
        }
        // 兜底：漏掉的对话补成最后一个事件
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                missing.add((long) i);
            }
        }
        if (!missing.isEmpty()) {
            if (groups.isEmpty()) {
                groups.add(new long[0]);
                titles.add("零散对话");
            }
            long[] last = groups.get(groups.size() - 1);
            long[] merged = new long[last.length + missing.size()];
            System.arraycopy(last, 0, merged, 0, last.length);
            for (int i = 0; i < missing.size(); i++) {
                merged[last.length + i] = missing.get(i);
            }
            groups.set(groups.size() - 1, merged);
        }
    }

    private Episode buildEpisode(Long spaceId, String title, List<Memory> part, long[] idx) {
        Episode ep = new Episode();
        ep.setSpaceId(spaceId);
        ep.setTitle(title);
        ep.setStatus("pending");
        Instant start = null;
        Instant end = null;
        for (long i : idx) {
            Memory m = part.get((int) i);
            if (start == null || m.getOccurredAt().isBefore(start)) {
                start = m.getOccurredAt();
            }
            if (end == null || m.getOccurredAt().isAfter(end)) {
                end = m.getOccurredAt();
            }
        }
        ep.setStartedAt(start == null ? Instant.now() : start);
        ep.setLastAt(end == null ? ep.getStartedAt() : end);
        ep.setTurnCount(idx.length);
        ep.setCreatedAt(Instant.now());
        ep.setUpdatedAt(Instant.now());
        return ep;
    }

    /* ---------------- 增量归类 ---------------- */

    private final java.util.Set<Long> partitioning = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean isPartitioning(Long spaceId) {
        return partitioning.contains(spaceId);
    }

    /** 后台定时调用：没有事件的记忆体先全量划分，其余按 3 条攒批归类。 */
    public void tickAll() {
        if (!llm.enabled()) {
            return;
        }
        try {
            splitLongEvents();
        } catch (Exception ignored) {
            // 拆分失败不影响别的
        }
        for (Space s : spaces.findAll()) {
            Long id = s.getId();
            long ready = memories.countBySpaceIdAndEpisodeIdIsNullAndStatus(id, "ready");
            if (ready <= 0) {
                continue;
            }
            if (episodes.countBySpaceId(id) == 0) {
                if (partitioning.add(id)) {
                    Thread th = new Thread(() -> {
                        try {
                            partitionAll(id);
                        } catch (Exception e) {
                            log.warn("全量划分失败：{}", e.getMessage());
                        } finally {
                            partitioning.remove(id);
                        }
                    }, "episode-partition");
                    th.setDaemon(true);
                    th.start();
                }
            } else if (ready >= 3) {
                try {
                    assignPending(id);
                } catch (Exception e) {
                    log.warn("增量归类失败：{}", e.getMessage());
                }
            }
        }
    }

    /** 新对话入库后：攒够 3 条就归类一批。返回处理条数。 */
    public int assignPending(Long spaceId) {
        List<Memory> pending = memories.findBySpaceIdAndEpisodeIdIsNullAndStatusOrderByOccurredAtAscIdAsc(
                spaceId, "ready", PageRequest.of(0, 6));
        if (pending.isEmpty()) {
            return 0;
        }
        if (episodes.countBySpaceId(spaceId) == 0) {
            partitionAll(spaceId);
            return pending.size();
        }
        List<Episode> candidates = episodes.findBySpaceIdOrderByLastAtDesc(spaceId, PageRequest.of(0, 15));

        String ctx = recentContext(spaceId, null);
        StringBuilder sb = new StringBuilder(ctx.isEmpty() ? "" : ctx + "\n");
        sb.append("已有事件：\n");
        Map<Long, Episode> byId = new LinkedHashMap<>();
        for (Episode e : candidates) {
            byId.put(e.getId(), e);
            sb.append("- id=").append(e.getId()).append("  ").append(e.getTitle()).append('\n');
        }
        sb.append("\n新对话：\n");
        for (int i = 0; i < pending.size(); i++) {
            Memory m = pending.get(i);
            sb.append('[').append(i).append("] 他：").append(clip(m.getQuestion(), 120))
                    .append("\n    我：").append(clip(m.getAnswer(), 220)).append('\n');
        }

        List<Long> touched = new ArrayList<>();
        try {
            JsonNode node = callJson(ASSIGN_SYSTEM, sb.toString());
            for (JsonNode item : node.path("items")) {
                int idx = item.path("turn").asInt(-1);
                if (idx < 0 || idx >= pending.size()) {
                    continue;
                }
                Memory m = pending.get(idx);
                long eventId = item.path("event_id").asLong(0);
                String newTitle = item.path("new_title").asText("").trim();
                if (eventId > 0 && byId.containsKey(eventId)) {
                    Episode ep = byId.get(eventId);
                    m.setEpisodeId(ep.getId());
                    m.setEpisodeStatus("assigned");
                    touch(ep, m);
                    touched.add(ep.getId());
                } else if (!newTitle.isEmpty()) {
                    Episode prev = previousOf(spaceId, m);
                    Episode ep = new Episode();
                    ep.setSpaceId(spaceId);
                    ep.setTitle(clip(newTitle, 20));
                    ep.setStartedAt(m.getOccurredAt());
                    ep.setLastAt(m.getOccurredAt());
                    ep.setTurnCount(1);
                    ep.setStatus("pending");
                    ep.setCreatedAt(Instant.now());
                    ep.setUpdatedAt(Instant.now());
                    episodes.save(ep);
                    m.setEpisodeId(ep.getId());
                    m.setEpisodeStatus("assigned");
                    touched.add(ep.getId());
                    String note = item.path("transition").asText("").trim();
                    if (prev != null && !note.isEmpty()) {
                        saveTransition(spaceId, prev, ep, note);
                    }
                } else {
                    m.setEpisodeStatus("fallback");
                    m.setEpisodeId(nearest(spaceId, m));
                }
                memories.save(m);
            }
        } catch (Exception e) {
            log.warn("事件归类失败，走时间兜底：{}", e.getMessage());
            for (Memory m : pending) {
                m.setEpisodeId(nearest(spaceId, m));
                m.setEpisodeStatus("fallback");
                memories.save(m);
            }
        }
        // 受影响的事件：按设置决定是否重写日记
        Space space = spaces.findById(spaceId).orElse(null);
        boolean auto = space == null || !Boolean.FALSE.equals(space.getEpisodeAutoRewrite());
        if (auto) {
            for (Long id : touched) {
                episodes.findById(id).ifPresent(ep -> {
                    if (!Boolean.TRUE.equals(ep.getEditedByUser())) {
                        ep.setStatus("pending");
                        episodes.save(ep);
                    }
                });
            }
        }
        return pending.size();
    }

    /** 兜底：归到时间上最近的那个事件；没有事件就新建。 */
    private Long nearest(Long spaceId, Memory m) {
        List<Episode> list = episodes.findBySpaceIdOrderByLastAtDesc(spaceId, PageRequest.of(0, 1));
        if (list.isEmpty()) {
            Episode ep = new Episode();
            ep.setSpaceId(spaceId);
            ep.setTitle(clip(m.getQuestion(), 12));
            ep.setStartedAt(m.getOccurredAt());
            ep.setLastAt(m.getOccurredAt());
            ep.setTurnCount(1);
            ep.setStatus("pending");
            episodes.save(ep);
            return ep.getId();
        }
        Episode ep = list.get(0);
        touch(ep, m);
        return ep.getId();
    }

    private void touch(Episode ep, Memory m) {
        ep.setTurnCount((ep.getTurnCount() == null ? 0 : ep.getTurnCount()) + 1);
        if (ep.getLastAt() == null || m.getOccurredAt().isAfter(ep.getLastAt())) {
            ep.setLastAt(m.getOccurredAt());
        }
        if (ep.getStartedAt() == null || m.getOccurredAt().isBefore(ep.getStartedAt())) {
            ep.setStartedAt(m.getOccurredAt());
        }
        ep.setUpdatedAt(Instant.now());
        episodes.save(ep);
    }

    public boolean hasPendingDiary() {
        return episodes.countByStatus("pending") > 0;
    }

    public int drainDiary(Long spaceId, int limit) {
        List<Episode> list = spaceId == null
                ? episodes.findByStatusOrderByIdAsc("pending", PageRequest.of(0, limit))
                : episodes.findBySpaceIdAndStatusOrderByIdAsc(spaceId, "pending", PageRequest.of(0, limit));
        int done = 0;
        for (Episode ep : list) {
            try {
                generateDiary(ep);
                done++;
            } catch (Exception e) {
                ep.setStatus("failed");
                ep.setError(clip(e.getMessage(), 250));
                episodes.save(ep);
            }
        }
        return done;
    }

    /* ---------------- 日记 ---------------- */

    public void generateDiary(Episode ep) throws Exception {
        List<Memory> turns = memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(ep.getId());
        if (turns.isEmpty()) {
            ep.setStatus("ready");
            episodes.save(ep);
            return;
        }
        String ctx = recentContext(ep.getSpaceId(), ep.getStartedAt());
        StringBuilder sb = new StringBuilder(ctx.isEmpty() ? "" : ctx + "\n【这件事本身】\n");
        for (Memory m : turns) {
            sb.append("他：").append(clip(m.getQuestion(), 200)).append('\n')
                    .append("我：").append(clip(m.getAnswer(), 400)).append("\n\n");
        }
        JsonNode node = callJson(DIARY_SYSTEM, sb.toString());
        ep.setTitle(clip(node.path("title").asText(ep.getTitle()), 20));
        ep.setDiary(clip(node.path("diary").asText(""), 1100));
        ep.setMoodFrom(clip(node.path("mood_from").asText(""), 20));
        ep.setMoodTo(clip(node.path("mood_to").asText(""), 20));
        ep.setMoodIntensity(Math.max(0, Math.min(1, node.path("mood_intensity").asDouble(0.3))));
        ep.setMoodBecause(clip(node.path("mood_because").asText(""), 280));
        ep.setRelationNote(clip(node.path("relation_note").asText(""), 500));
        StringBuilder threads = new StringBuilder();
        for (JsonNode t : node.path("open_threads")) {
            String s = t.asText("").trim();
            if (!s.isEmpty()) {
                if (threads.length() > 0) {
                    threads.append('\n');
                }
                threads.append("- ").append(s);
            }
        }
        ep.setOpenThreads(clip(threads.toString(), 1100));
        ep.setImportance(Math.max(1, Math.min(5, node.path("importance").asInt(3))));
        ep.setStatus("ready");
        ep.setError("");
        ep.setUpdatedAt(Instant.now());
        try {
            EmbeddingService.Result emb = embeddings.embedAll(
                    List.of(ep.getTitle() + "\n" + ep.getDiary()));
            ep.setEmbedding(encode(emb.vectors().get(0)));
            ep.setEmbedModel(emb.model());
            ep.setEmbedDim(emb.vectors().get(0).length);
        } catch (Exception e) {
            log.warn("事件向量化失败：{}", e.getMessage());
        }
        episodes.save(ep);
    }

    /* ---------------- 查询 ---------------- */

    public List<EpisodeView> list(Long spaceId) {
        return episodes.findBySpaceIdOrderByStartedAtDesc(spaceId).stream().map(this::toView).toList();
    }

    /** 分页版：事件多了以后前端不用一次全拿 */
    public List<EpisodeView> page(Long spaceId, int limit, int offset) {
        int size = Math.max(1, limit);
        int skip = Math.max(0, offset);
        List<Episode> all = episodes.findBySpaceIdOrderByStartedAtDesc(spaceId);
        return all.stream().skip(skip).limit(size).map(this::toView).toList();
    }

    public EpisodeView get(Long id) {
        return episodes.findById(id).map(this::toView).orElse(null);
    }

    public List<Map<String, Object>> turnsOf(Long episodeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Memory m : memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(episodeId)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", m.getId());
            one.put("occurredAt", m.getOccurredAt() == null ? null : m.getOccurredAt().toString());
            one.put("question", m.getQuestion());
            one.put("answer", m.getAnswer());
            one.put("summary", m.getSummary());
            List<Map<String, Object>> atts = new ArrayList<>();
            for (var a : attachmentRepo.findByMemoryIdOrderByIdAsc(m.getId())) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("id", a.getId());
                am.put("kind", a.getKind());
                am.put("caption", a.getCaption());
                am.put("ocrText", a.getOcrText());
                atts.add(am);
            }
            one.put("attachments", atts);
            out.add(one);
        }
        return out;
    }

    private EpisodeView toView(Episode e) {
        List<String> threads = new ArrayList<>();
        if (e.getOpenThreads() != null && !e.getOpenThreads().isBlank()) {
            for (String line : e.getOpenThreads().split("\n")) {
                String s = line.replaceFirst("^-\\s*", "").trim();
                if (!s.isEmpty()) {
                    threads.add(s);
                }
            }
        }
        return new EpisodeView(e.getId(), e.getTitle(), e.getDiary(), e.getMoodFrom(), e.getMoodTo(),
                e.getMoodIntensity(), e.getMoodBecause(), e.getRelationNote(), threads,
                e.getStartedAt() == null ? null : e.getStartedAt().toString(),
                e.getLastAt() == null ? null : e.getLastAt().toString(),
                e.getTurnCount() == null ? 0 : e.getTurnCount(),
                e.getImportance() == null ? 3 : e.getImportance(),
                e.getStatus(), Boolean.TRUE.equals(e.getEditedByUser()));
    }


    /* ---------------- 事件接缝 ---------------- */

    private Episode previousOf(Long spaceId, Memory m) {
        Episode best = null;
        for (Episode e : episodes.findBySpaceIdOrderByStartedAtDesc(spaceId)) {
            if (e.getLastAt() == null || m.getOccurredAt() == null) {
                continue;
            }
            if (e.getLastAt().isBefore(m.getOccurredAt())) {
                if (best == null || e.getLastAt().isAfter(best.getLastAt())) {
                    best = e;
                }
            }
        }
        return best;
    }

    private void saveTransition(Long spaceId, Episode from, Episode to, String note) {
        EpisodeTransition tr = new EpisodeTransition();
        tr.setSpaceId(spaceId);
        tr.setFromEpisodeId(from.getId());
        tr.setToEpisodeId(to.getId());
        tr.setTransitionNote(clip(note, 280));
        long days = 0;
        if (from.getLastAt() != null && to.getStartedAt() != null) {
            days = Math.max(0, java.time.Duration.between(from.getLastAt(), to.getStartedAt()).toDays());
        }
        tr.setDaysGap((int) days);
        tr.setKind(days >= 1 ? "time_gap" : "topic_shift");
        transitions.save(tr);
    }

    /* ---------------- 续命包：事件 + 接缝 ---------------- */

    public Map<String, Object> packForRestart(Long spaceId, int max) {
        List<Episode> all = episodes.findBySpaceIdOrderByStartedAtDesc(spaceId);
        List<Episode> ready = new ArrayList<>();
        for (Episode e : all) {
            if ("ready".equals(e.getStatus()) && e.getDiary() != null && !e.getDiary().isBlank()) {
                ready.add(e);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (ready.isEmpty()) {
            out.put("text", "");
            out.put("episodes", List.of());
            return out;
        }
        Instant now = Instant.now();
        ready.sort((a, b) -> Double.compare(score(b, now), score(a, now)));
        List<Episode> picked = new ArrayList<>(ready.subList(0, Math.min(max, ready.size())));
        picked.sort((a, b) -> {
            Instant x = a.getStartedAt() == null ? Instant.EPOCH : a.getStartedAt();
            Instant y = b.getStartedAt() == null ? Instant.EPOCH : b.getStartedAt();
            return x.compareTo(y);
        });

        Map<String, EpisodeTransition> byPair = new LinkedHashMap<>();
        for (EpisodeTransition tr : transitions.findBySpaceIdOrderByIdAsc(spaceId)) {
            byPair.put(tr.getFromEpisodeId() + "-" + tr.getToEpisodeId(), tr);
        }

        StringBuilder sb = new StringBuilder();
        String selfText = spaces.findById(spaceId).map(s -> s.getPersonaSelf() == null ? "" : s.getPersonaSelf()).orElse("");
        if (!selfText.isBlank()) {
            sb.append("【我是谁】\n").append(selfText.trim()).append("\n\n");
        }
        String otherText = otherBlockOf(spaceId);
        if (!otherText.isBlank()) {
            sb.append("【他是谁】\n").append(otherText.trim()).append("\n\n");
        }
        sb.append("【你们最近在做什么】\n");
        List<String> threads = new ArrayList<>();
        Episode last = null;
        int i = 1;
        for (Episode e : picked) {
            if (last != null) {
                EpisodeTransition tr = byPair.get(last.getId() + "-" + e.getId());
                if (tr != null && tr.getTransitionNote() != null && !tr.getTransitionNote().isBlank()) {
                    sb.append("（").append(tr.getTransitionNote()).append("）\n");
                } else if (last.getLastAt() != null && e.getStartedAt() != null) {
                    long d = Math.max(0, java.time.Duration.between(last.getLastAt(), e.getStartedAt()).toDays());
                    if (d >= 1) {
                        sb.append("（隔了 ").append(d).append(" 天）\n");
                    }
                }
            }
            sb.append(i++).append(". ").append(e.getTitle()).append("：").append(e.getDiary()).append('\n');
            if (e.getOpenThreads() != null && !e.getOpenThreads().isBlank()) {
                for (String line : e.getOpenThreads().split("\n")) {
                    String s = line.replaceFirst("^-\\s*", "").trim();
                    if (!s.isEmpty()) {
                        threads.add(s);
                    }
                }
            }
            last = e;
        }
        List<String> seen = new ArrayList<>();
        for (Episode e : picked) {
            for (Memory m : memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(e.getId())) {
                for (var a : attachmentRepo.findByMemoryIdOrderByIdAsc(m.getId())) {
                    String desc = a.getCaption() == null ? "" : a.getCaption().trim();
                    if (desc.isEmpty() && a.getOcrText() != null) {
                        desc = a.getOcrText().trim();
                    }
                    if (desc.isEmpty()) {
                        desc = "一张图";
                    }
                    seen.add(desc);
                    if (seen.size() >= 5) {
                        break;
                    }
                }
            }
        }
        if (!seen.isEmpty()) {
            sb.append("\n【他给你看过的东西】\n");
            seen.stream().distinct().limit(5).forEach(s -> sb.append("- ").append(clip(s, 120)).append('\n'));
        }
        if (!threads.isEmpty()) {
            sb.append("\n【没说完的】\n");
            threads.stream().distinct().limit(8).forEach(s -> sb.append("- ").append(s).append('\n'));
        }
        if (last != null) {
            String mood = (last.getMoodTo() == null ? "" : last.getMoodTo());
            if (!mood.isBlank()) {
                sb.append("\n【他现在的状态】\n").append(mood);
                if (last.getMoodBecause() != null && !last.getMoodBecause().isBlank()) {
                    sb.append("（因为：").append(last.getMoodBecause()).append("）");
                }
                sb.append('\n');
            }
        }
        List<Long> ids = new ArrayList<>();
        for (Episode e : picked) {
            ids.add(e.getId());
        }
        out.put("text", sb.toString());
        out.put("episodes", ids);
        out.put("estimatedTokens", Math.max(1, sb.length() / 2));
        return out;
    }

    /** 选取分数：新近（按 last_at，7 天衰减）+ 重要度 + 有未完成线头 */
    private double score(Episode e, Instant now) {
        double recency = 0;
        if (e.getLastAt() != null) {
            double days = Math.max(0, java.time.Duration.between(e.getLastAt(), now).toHours() / 24.0);
            recency = Math.exp(-days / 7.0);
        }
        double importance = (e.getImportance() == null ? 3 : e.getImportance()) / 5.0;
        double threads = (e.getOpenThreads() != null && !e.getOpenThreads().isBlank()) ? 1 : 0;
        return 0.5 * recency + 0.3 * importance + 0.2 * threads;
    }

    /* ---------------- 合并 ---------------- */

    public EpisodeView merge(Long fromId, Long intoId) {
        Episode from = episodes.findById(fromId).orElseThrow(() -> new IllegalArgumentException("事件不存在"));
        Episode into = episodes.findById(intoId).orElseThrow(() -> new IllegalArgumentException("事件不存在"));
        List<Memory> turns = memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(fromId);
        for (Memory m : turns) {
            m.setEpisodeId(intoId);
            memories.save(m);
        }
        if (from.getStartedAt() != null && (into.getStartedAt() == null || from.getStartedAt().isBefore(into.getStartedAt()))) {
            into.setStartedAt(from.getStartedAt());
        }
        if (from.getLastAt() != null && (into.getLastAt() == null || from.getLastAt().isAfter(into.getLastAt()))) {
            into.setLastAt(from.getLastAt());
        }
        into.setTurnCount(memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(intoId).size());
        into.setStatus("pending");
        into.setEditedByUser(false);
        into.setUpdatedAt(Instant.now());
        episodes.save(into);
        transitions.deleteByFromEpisodeIdOrToEpisodeId(fromId, fromId);
        episodes.delete(from);
        return toView(into);
    }

    /* ---------------- 长事件拆分 ---------------- */

    public int splitLongEvents() {
        int n = 0;
        for (Episode e : episodes.findByStatusOrderByIdAsc("ready", PageRequest.of(0, 5))) {
            int count = e.getTurnCount() == null ? 0 : e.getTurnCount();
            if (count >= 50 && !Boolean.TRUE.equals(e.getIsLongRunning()) && e.getSplitOf() == null) {
                try {
                    if (splitOne(e)) {
                        n++;
                    }
                } catch (Exception ex) {
                    log.warn("长事件拆分失败：{}", ex.getMessage());
                    e.setIsLongRunning(true);
                    episodes.save(e);
                }
            }
        }
        return n;
    }

    private boolean splitOne(Episode e) throws Exception {
        List<Memory> turns = memories.findByEpisodeIdOrderByOccurredAtAscIdAsc(e.getId());
        if (turns.size() < 12) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < turns.size(); i++) {
            sb.append('[').append(i).append("] 他：").append(clip(turns.get(i).getQuestion(), 100))
                    .append("\n    我：").append(clip(turns.get(i).getAnswer(), 160)).append('\n');
        }
        JsonNode node = callJson(SPLIT_SYSTEM, sb.toString());
        JsonNode parts = node.path("parts");
        if (!parts.isArray() || parts.size() < 2) {
            e.setIsLongRunning(true);
            e.setStatus("pending");
            episodes.save(e);
            return false;
        }
        int no = 0;
        for (JsonNode part : parts) {
            JsonNode idx = part.path("indices");
            if (!idx.isArray() || idx.isEmpty()) {
                continue;
            }
            List<Memory> sub = new ArrayList<>();
            for (JsonNode x : idx) {
                int v = x.asInt(-1);
                if (v >= 0 && v < turns.size()) {
                    sub.add(turns.get(v));
                }
            }
            if (sub.isEmpty()) {
                continue;
            }
            Episode child = new Episode();
            child.setSpaceId(e.getSpaceId());
            child.setTitle(clip(part.path("title").asText(e.getTitle()), 20));
            child.setSplitOf(e.getId());
            child.setPartNo(no++);
            child.setStatus("pending");
            child.setImportance(e.getImportance());
            child.setStartedAt(sub.get(0).getOccurredAt());
            child.setLastAt(sub.get(sub.size() - 1).getOccurredAt());
            child.setTurnCount(sub.size());
            episodes.save(child);
            for (Memory m : sub) {
                m.setEpisodeId(child.getId());
            }
            memories.saveAll(sub);
        }
        if (no == 0) {
            e.setIsLongRunning(true);
            episodes.save(e);
            return false;
        }
        transitions.deleteByFromEpisodeIdOrToEpisodeId(e.getId(), e.getId());
        episodes.delete(e);
        return true;
    }

    /* ---------------- 工具 ---------------- */

    /** 取该记忆体「最近 N 条已完成对话」作为上下文，避免每次从零理解。 */
    /** 「他/她是谁」：结构化 JSON 渲染成提示词片段；老格式直接返回原文 */
    private String otherBlockOf(Long spaceId) {
        try {
            var sp = spaces.findById(spaceId).orElse(null);
            if (sp == null) {
                return "";
            }
            // 优先用单独生成/编辑过的提示词
            String prompt = sp.getPersonaOtherPrompt();
            if (prompt != null && !prompt.isBlank()) {
                return prompt.trim();
            }
            if (sp.getPersonaOther() == null || sp.getPersonaOther().isBlank()) {
                return "";
            }
            String raw = sp.getPersonaOther().trim();
            if (!raw.startsWith("{")) {
                return raw;
            }
            var node = mapper.readTree(raw);
            StringBuilder sb = new StringBuilder();
            var pr = node.path("profile");
            List<String> facts = new ArrayList<>();
            addFact(facts, "称呼", pr.path("name").asText(""));
            addFact(facts, "年龄", pr.path("age").asText(""));
            addFact(facts, "职业", pr.path("occupation").asText(""));
            addFact(facts, "所在地", pr.path("location").asText(""));
            addFact(facts, "家人", pr.path("family").asText(""));
            if (!facts.isEmpty()) {
                sb.append("画像｜").append(String.join("；", facts)).append('\n');
            }
            var lk = node.path("likes");
            List<String> likes = new ArrayList<>();
            addFact(likes, "爱吃", lk.path("food").asText(""));
            addFact(likes, "讨厌", lk.path("dislikes").asText(""));
            addFact(likes, "习惯", lk.path("tools").asText(""));
            if (!likes.isEmpty()) {
                sb.append("喜好｜").append(String.join("；", likes)).append('\n');
            }
            List<String> pacts = new ArrayList<>();
            for (var p : node.path("pacts")) {
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
            return sb.toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private static void addFact(List<String> list, String label, String value) {
        if (value != null && !value.isBlank()) {
            list.add(label + "：" + value.trim());
        }
    }

    private String recentContext(Long spaceId, java.time.Instant before) {
        if (recentTurns <= 0) {
            return "";
        }
        List<Memory> all = memories.findBySpaceIdOrderByOccurredAtAscIdAsc(spaceId);
        List<Memory> picked = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && picked.size() < recentTurns; i--) {
            Memory m = all.get(i);
            if (!"ready".equals(m.getStatus())) {
                continue;
            }
            if (before != null && m.getOccurredAt() != null && !m.getOccurredAt().isBefore(before)) {
                continue;
            }
            picked.add(0, m);
        }
        if (picked.isEmpty()) {
            return "";
        }
        // 原文给，不截断句子；只按总字数从最新往前收，保证「最近的」优先
        List<String> blocks = new ArrayList<>();
        int used = 0;
        for (int i = picked.size() - 1; i >= 0; i--) {
            Memory m = picked.get(i);
            String block = "他：" + (m.getQuestion() == null ? "" : m.getQuestion()) + "\n"
                    + "我：" + (m.getAnswer() == null ? "" : m.getAnswer()) + "\n";
            if (used + block.length() > recentChars && !blocks.isEmpty()) {
                break;
            }
            blocks.add(0, block);
            used += block.length();
        }
        if (blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【这个记忆体之前的对话原文，仅供理解上下文，不要重复、不要引用】\n");
        for (String b : blocks) {
            sb.append(b).append('\n');
        }
        return sb.toString();
    }

    private JsonNode callJson(String system, String user) throws Exception {
        if (!llm.enabled()) {
            throw new IllegalStateException("没有配置模型 key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llm.model());
        body.put("temperature", 0.3);
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
        String content = mapper.readTree(raw).path("choices").path(0).path("message").path("content").asText("");
        String t = content.trim();
        if (t.startsWith("```")) {
            int a = t.indexOf('\n');
            int b = t.lastIndexOf("```");
            if (a > 0 && b > a) {
                t = t.substring(a + 1, b).trim();
            }
        }
        return mapper.readTree(t);
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    private static byte[] encode(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4);
        for (float f : v) {
            bb.putFloat(f);
        }
        return bb.array();
    }
}
