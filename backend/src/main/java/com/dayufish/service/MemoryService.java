package com.dayufish.service;

import com.dayufish.domain.ImportJob;
import com.dayufish.domain.Memory;
import com.dayufish.domain.Space;
import com.dayufish.repo.ImportJobRepository;
import com.dayufish.repo.MemoryRepository;
import com.dayufish.repo.SpaceRepository;
import com.dayufish.web.Dtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final double DAY = 86_400_000d;

    private final SpaceRepository spaces;
    private final MemoryRepository memories;
    private final ImportJobRepository imports;
    private final ImportParser parser;
    private final SummaryService summaries;
    private final StyleService styles;
    private final MediaService media;
    private final EmbeddingService embeddings;
    private final ObjectMapper mapper;
    private final AtomicBoolean draining = new AtomicBoolean(false);

    @Value("${dayufish.recall.half-life-days:30}")
    private double halfLifeDays;

    @Value("${dayufish.recall.weight-similarity:0.68}")
    private double wSimilarity;

    @Value("${dayufish.recall.weight-recency:0.16}")
    private double wRecency;

    @Value("${dayufish.recall.weight-strength:0.09}")
    private double wStrength;

    @Value("${dayufish.recall.weight-importance:0.07}")
    private double wImportance;

    public MemoryService(SpaceRepository spaces, MemoryRepository memories, ImportJobRepository imports,
            ImportParser parser, SummaryService summaries, EmbeddingService embeddings, ObjectMapper mapper,
            StyleService styles, MediaService media) {
        this.spaces = spaces;
        this.memories = memories;
        this.imports = imports;
        this.parser = parser;
        this.summaries = summaries;
        this.styles = styles;
        this.media = media;
        this.embeddings = embeddings;
        this.mapper = mapper;
    }

    /* ---------------- 空间 ---------------- */

    public List<Dtos.SpaceView> listSpaces() {
        return spaces.findAllByOrderByIdAsc().stream()
                .map(s -> new Dtos.SpaceView(
                        s.getId(), s.getName(), s.getNote(),
                        ISO.format(s.getCreatedAt()),
                        memories.countBySpaceId(s.getId()),
                        memories.countBySpaceIdAndStatus(s.getId(), "pending")))
                .toList();
    }

    public Long createSpace(String name, String note) {
        String n = (name == null || name.isBlank()) ? "新记忆体" : name.trim();
        return spaces.save(new Space(n.length() > 60 ? n.substring(0, 60) : n, note)).getId();
    }

    public void deleteSpace(Long id) {
        memories.findBySpaceIdOrderByOccurredAtDesc(id).forEach(memories::delete);
        spaces.deleteById(id);
    }

    /* ---------------- 单条 ---------------- */

    public Long add(Long spaceId, String question, String answer, String occurredAt, String source) {
        Memory m = new Memory();
        m.setSpaceId(spaceId);
        m.setSource(source == null ? "manual" : source);
        m.setQuestion(question == null ? "" : question);
        m.setAnswer(answer == null ? "" : answer);
        m.setOccurredAt(parseInstant(occurredAt));
        m.setStatus("pending");
        m.setCreatedAt(Instant.now());
        return memories.save(m).getId();
    }

    public Dtos.MemoryView get(Long id) {
        return memories.findById(id).map(m -> toView(m, null, null, strengthOf(m), (int) Math.round(ageOf(m)))).orElse(null);
    }

    public Dtos.MemoryView update(Long id, String summary, Integer importance, String keywords) {
        Memory m = memories.findById(id).orElse(null);
        if (m == null) {
            return null;
        }
        if (summary != null) {
            m.setSummary(summary);
        }
        if (importance != null) {
            m.setImportance(Math.max(1, Math.min(5, importance)));
        }
        if (keywords != null) {
            m.setKeywords(keywords);
        }
        return toView(memories.save(m), null, null, strengthOf(m), (int) Math.round(ageOf(m)));
    }

    public void delete(Long id) {
        memories.deleteById(id);
    }

    public List<Dtos.MemoryView> list(Long spaceId, String q, int limit, int offset) {
        int size = Math.max(1, limit);
        int skip = Math.max(0, offset);
        PageRequest pageable = PageRequest.of(0, skip + size);
        List<Memory> rows = (q == null || q.isBlank())
                ? memories.findBySpaceIdOrderByOccurredAtDesc(spaceId, pageable)
                : memories.search(spaceId, q.trim(), pageable);
        return rows.stream()
                .skip(skip)
                .limit(size)
                .map(m -> toView(m, null, null, strengthOf(m), (int) Math.round(ageOf(m))))
                .toList();
    }

    public long count(Long spaceId, String q) {
        return (q == null || q.isBlank())
                ? memories.countBySpaceId(spaceId)
                : memories.countSearch(spaceId, q.trim());
    }

    /* ---------------- 导入 ---------------- */

    public Dtos.ImportProgress importText(Long spaceId, String text, String sourceName) {
        ImportParser.Parsed parsed = parser.parse(text);
        if (parsed.turns().isEmpty()) {
            throw new IllegalArgumentException("没有解析出任何对话，请检查格式");
        }

        ImportJob job = new ImportJob();
        job.setSpaceId(spaceId);
        job.setSourceName(sourceName == null || sourceName.isBlank() ? "粘贴导入" : sourceName);
        job.setTotal(parsed.turns().size());
        job.setCreatedAt(Instant.now());
        job = imports.save(job);

        List<Long> ids = new ArrayList<>();
        for (ImportParser.Turn t : parsed.turns()) {
            ids.add(add(spaceId, t.question(), t.answer(), ISO.format(t.occurredAt()), "import:" + job.getId()));
        }
        try {
            job.setIds(mapper.writeValueAsString(ids));
        } catch (Exception e) {
            job.setIds("[]");
        }
        imports.save(job);
        return progress(job.getId());
    }

    public Dtos.ImportProgress progress(Long importId) {
        ImportJob job = imports.findById(importId).orElse(null);
        if (job == null) {
            return null;
        }
        List<Long> ids = readIds(job.getIds());
        long ready = ids.isEmpty() ? 0 : memories.countByIdInAndStatus(ids, "ready");
        long failed = ids.isEmpty() ? 0 : memories.countByIdInAndStatus(ids, "failed");
        int done = (int) (ready + failed);
        return new Dtos.ImportProgress(
                job.getId(), job.getSpaceId(), job.getSourceName(), job.getTotal(),
                (int) ready, (int) failed, done,
                done >= job.getTotal() ? "done" : "running",
                ISO.format(job.getCreatedAt()));
    }

    private List<Long> readIds(String json) {
        try {
            return mapper.readValue(json == null ? "[]" : json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /* ---------------- 后台整理 ---------------- */

    public int drain(int max) {
        if (!draining.compareAndSet(false, true)) {
            return 0;
        }
        int done = 0;
        try {
            for (int i = 0; i < max; i++) {
                List<Memory> batch = memories.findByStatusOrderByIdAsc("pending", PageRequest.of(0, 1));
                if (batch.isEmpty()) {
                    break;
                }
                Memory m = batch.get(0);
                try {
                    processOne(m);
                    done++;
                } catch (Exception e) {
                    m.setStatus("failed");
                    m.setError(trim(e.getMessage(), 400));
                    memories.save(m);
                    log.warn("整理失败 #{}：{}", m.getId(), e.getMessage());
                }
            }
        } finally {
            draining.set(false);
        }
        return done;
    }

    private void processOne(Memory m) {
        String prev = memories.findBySpaceIdOrderByOccurredAtDesc(m.getSpaceId()).stream()
                .filter(x -> x.getId() < m.getId() && "ready".equals(x.getStatus()))
                .findFirst()
                .map(Memory::getSummary)
                .orElse("");

        SummaryService.Result result = summaries.summarize(
                m.getQuestion(), m.getAnswer(), ImportParser.format(m.getOccurredAt()), prev,
                styles.profileText(m.getSpaceId()));

        EmbeddingService.Result emb = embeddings.embedAll(
                List.of(result.summary() + "\n" + (result.questionDigest() == null ? m.getQuestion() : result.questionDigest())));

        m.setSummary(result.summary());
        m.setKeywords(String.join(",", result.keywords()));
        m.setImportance(result.importance());
        m.setStatus("ready");
        m.setError("");
        m.setEmbedding(encode(emb.vectors().get(0)));
        m.setEmbedModel(emb.model());
        m.setEmbedDim(emb.vectors().get(0).length);
        memories.save(m);
    }

    /** 把整个记忆体的总结按当前说话方式重写：重置为 pending，交给后台队列慢慢跑。 */
    public int restyle(Long spaceId) {
        List<Memory> rows = memories.findBySpaceIdOrderByOccurredAtDesc(spaceId);
        int n = 0;
        for (Memory m : rows) {
            m.setStatus("pending");
            m.setSummary("");
            m.setKeywords("");
            memories.save(m);
            n++;
        }
        return n;
    }

    public Long spaceIdOf(Long memoryId) {
        return memories.findById(memoryId).map(Memory::getSpaceId).orElse(1L);
    }

    /** 上传一张截图：建一条对话记忆，并把图片挂上去，OCR 完成后会补进 answer。 */
    public Long addScreenshot(Long spaceId, String caption, byte[] bytes, String mime) {
        String q = caption == null || caption.isBlank() ? "（截图）" : clipText(caption, 200);
        Long id = add(spaceId, q, "", Instant.now().toString(), "shot");
        try {
            media.save(spaceId, id, bytes, mime, caption);
        } catch (Exception e) {
            // 图片存不下也让这条记忆留着
        }
        return id;
    }

    private static String clipText(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public void setEpisodeAutoRewrite(Long spaceId, boolean enabled) {
        spaces.findById(spaceId).ifPresent(s -> {
            s.setEpisodeAutoRewrite(enabled);
            spaces.save(s);
        });
    }

    public long pendingCount() {
        return memories.countByStatus("pending");
    }

    /* ---------------- 检索 ---------------- */

    public record RecallResult(List<Dtos.MemoryView> items, String mode) {
    }

    public RecallResult recall(Long spaceId, String query, boolean deep, int limit) {
        List<Memory> rows = memories.findBySpaceIdAndStatusNot(spaceId, "failed");
        if (rows.isEmpty()) {
            return new RecallResult(List.of(), "empty");
        }

        float[] qv = null;
        if (query != null && !query.isBlank()) {
            qv = embeddings.embedAll(List.of(query)).vectors().get(0);
        }

        List<Scored> scored = new ArrayList<>(rows.size());
        for (Memory m : rows) {
            double lex = lexical(query, m);
            double sim = 0;
            if (qv != null) {
                float[] v = decode(m.getEmbedding());
                sim = (v != null && v.length == qv.length) ? Math.max(0, EmbeddingService.cosine(qv, v)) : lex * 0.85;
            }
            double ageDays = ageOf(m);
            double recency = Math.exp(-ageDays / halfLifeDays);
            double strength = strengthOf(m);
            double importance = m.getImportance() / 5.0;
            double score = wSimilarity * sim + wRecency * recency + wStrength * strength
                    + wImportance * importance + 0.06 * lex;
            scored.add(new Scored(m, score, sim, recency, strength, (int) Math.round(ageDays)));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int k = Math.max(1, deep ? Math.max(limit, 30) : limit);
        List<Scored> picked = scored.stream()
                .limit(k)
                .filter(s -> deep || "ready".equals(s.memory.getStatus()))
                .toList();

        Instant now = Instant.now();
        List<Memory> touched = new ArrayList<>();
        for (Scored s : picked) {
            s.memory.setAccessCount(s.memory.getAccessCount() + 1);
            s.memory.setLastAccessAt(now);
            touched.add(s.memory);
        }
        if (!touched.isEmpty()) {
            memories.saveAll(touched);
        }

        List<Dtos.MemoryView> views = picked.stream()
                .map(s -> toView(s.memory, s.score, new Dtos.Parts(round(s.sim), round(s.recency), round(s.strength), s.ageDays),
                        s.strength, s.ageDays))
                .toList();
        return new RecallResult(views, deep ? "deep" : "normal");
    }

    private record Scored(Memory memory, double score, double sim, double recency, double strength, int ageDays) {
    }

    /* ---------------- 续命包 ---------------- */

    public Dtos.BundleView bundle(Long spaceId, String query, List<Long> ids, int maxChars, boolean includeTime, String scope) {
        List<Memory> items;
        boolean filtered = false;

        if (ids != null && !ids.isEmpty()) {
            items = memories.findByIdIn(ids).stream().filter(m -> "ready".equals(m.getStatus())).toList();
        } else {
            RecallResult r = recall(spaceId, query, false, 40);
            items = r.items().stream().map(v -> memories.findById(v.id()).orElse(null)).filter(x -> x != null).toList();
            if (query != null && !query.isBlank() && !"all".equals(scope) && !items.isEmpty()) {
                java.util.Map<Long, Double> scoreById = new java.util.HashMap<>();
                for (Dtos.MemoryView v : r.items()) {
                    scoreById.put(v.id(), v.score() == null ? 0d : v.score());
                }
                double top = scoreById.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double floor = Math.max(0.3, top * 0.62);
                List<Memory> kept = new ArrayList<>();
                for (Memory m : items) {
                    if (scoreById.getOrDefault(m.getId(), 0d) >= floor) {
                        kept.add(m);
                    }
                }
                filtered = kept.size() < items.size();
                items = kept.isEmpty() ? items.subList(0, Math.min(3, items.size())) : kept;
            }
        }

        items = items.stream()
                .filter(m -> "ready".equals(m.getStatus()) && m.getSummary() != null && !m.getSummary().isBlank())
                .sorted(Comparator.comparing(Memory::getOccurredAt))
                .toList();

        String header = "下面是我之前和你聊过的记忆，时间从早到晚。请先读完再回答；如果其中与我当前说法冲突，以我当前说法为准。\n";
        StringBuilder sb = new StringBuilder(header);
        List<Long> used = new ArrayList<>();

        for (Memory m : items) {
            String stamp = includeTime ? "【" + ImportParser.format(m.getOccurredAt()) + "】" : "【记忆】";
            String block = "\n" + stamp + "\n问：" + cut(m.getQuestion(), 300) + "\n记：" + m.getSummary() + "\n";
            if (sb.length() + block.length() > maxChars) {
                break;
            }
            sb.append(block);
            used.add(m.getId());
        }

        String text = used.isEmpty() ? "" : sb.toString();
        return new Dtos.BundleView(text, used.size(), text.length(), used,
                used.size() < items.size(), filtered, (int) Math.round(text.length() / 1.6));
    }

    /** 把一个记忆体的记忆复制进另一个（总结与向量原样带过去）。 */
    public int copySpace(Long fromId, Long toId) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            throw new IllegalArgumentException("源和目标是同一个记忆体");
        }
        if (spaces.findById(fromId).isEmpty() || spaces.findById(toId).isEmpty()) {
            throw new IllegalArgumentException("记忆体不存在");
        }
        List<Memory> source = memories.findBySpaceIdOrderByOccurredAtDesc(fromId);
        List<Memory> batch = new ArrayList<>();
        for (Memory m : source) {
            Memory copy = new Memory();
            copy.setSpaceId(toId);
            copy.setSource("merge:" + fromId);
            copy.setOccurredAt(m.getOccurredAt());
            copy.setQuestion(m.getQuestion());
            copy.setAnswer(m.getAnswer());
            copy.setSummary(m.getSummary());
            copy.setKeywords(m.getKeywords());
            copy.setImportance(m.getImportance());
            copy.setStatus(m.getStatus());
            copy.setEmbedding(m.getEmbedding());
            copy.setEmbedModel(m.getEmbedModel());
            copy.setEmbedDim(m.getEmbedDim());
            copy.setCreatedAt(Instant.now());
            batch.add(copy);
        }
        if (!batch.isEmpty()) {
            memories.saveAll(batch);
        }
        return batch.size();
    }

    /* ---------------- 导出 / 导入记忆体 ---------------- */

    public java.util.Map<String, Object> exportSpace(Long spaceId) {
        Space s = spaces.findById(spaceId).orElse(null);
        if (s == null) {
            throw new IllegalArgumentException("记忆体不存在");
        }
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        for (Memory m : memories.findBySpaceIdOrderByOccurredAtDesc(spaceId)) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("occurredAt", ISO.format(m.getOccurredAt()));
            row.put("question", m.getQuestion());
            row.put("answer", m.getAnswer());
            row.put("summary", m.getSummary());
            row.put("keywords", m.getKeywords());
            row.put("importance", m.getImportance());
            rows.add(row);
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("format", "mbfw-memory-body");
        out.put("version", 1);
        out.put("name", s.getName());
        out.put("note", s.getNote());
        out.put("exportedAt", ISO.format(Instant.now()));
        out.put("memories", rows);
        return out;
    }

    /** 导入一个记忆体：已有总结直接沿用，只补算向量。 */
    public Long importSpace(String fallbackName, com.fasterxml.jackson.databind.JsonNode payload) {
        String name = payload.path("name").asText(fallbackName);
        Long spaceId = createSpace(name, payload.path("note").asText("导入的记忆体"));
        com.fasterxml.jackson.databind.JsonNode rows = payload.path("memories");
        List<Memory> batch = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode r : rows) {
            Memory m = new Memory();
            m.setSpaceId(spaceId);
            m.setSource("import:bundle");
            m.setOccurredAt(parseInstant(r.path("occurredAt").asText(null)));
            m.setQuestion(r.path("question").asText(""));
            m.setAnswer(r.path("answer").asText(""));
            m.setSummary(r.path("summary").asText(""));
            m.setKeywords(r.path("keywords").asText(""));
            m.setImportance(Math.max(1, Math.min(5, r.path("importance").asInt(3))));
            m.setStatus("ready");
            m.setCreatedAt(Instant.now());
            batch.add(m);
            texts.add(m.getSummary() + "\n" + m.getQuestion());
        }
        if (!batch.isEmpty()) {
            EmbeddingService.Result emb = embeddings.embedAll(texts);
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setEmbedding(encode(emb.vectors().get(i)));
                batch.get(i).setEmbedModel(emb.model());
                batch.get(i).setEmbedDim(emb.vectors().get(i).length);
            }
            memories.saveAll(batch);
        }
        return spaceId;
    }

    /** 从 Harness 会话导入：把 turns 变成待整理的记忆。 */
    public Dtos.ImportProgress importTurns(Long spaceId, List<ImportParser.Turn> turns, String sourceName) {
        ImportJob job = new ImportJob();
        job.setSpaceId(spaceId);
        job.setSourceName(sourceName);
        job.setTotal(turns.size());
        job.setCreatedAt(Instant.now());
        job = imports.save(job);

        List<Long> ids = new ArrayList<>();
        for (ImportParser.Turn t : turns) {
            ids.add(add(spaceId, t.question(), t.answer(), ISO.format(t.occurredAt()), sourceName == null ? "harness" : sourceName));
        }
        try {
            job.setIds(mapper.writeValueAsString(ids));
        } catch (Exception e) {
            job.setIds("[]");
        }
        imports.save(job);
        return progress(job.getId());
    }

    /** 带图片的导入：turns 与 media 一一对应 */
    public Dtos.ImportProgress importTurnsWithMedia(Long spaceId, List<ImportParser.Turn> turns,
            String sourceName, List<List<ImportParser.Media>> mediaList) {
        ImportJob job = new ImportJob();
        job.setSpaceId(spaceId);
        job.setSourceName(sourceName);
        job.setTotal(turns.size());
        job.setCreatedAt(Instant.now());
        job = imports.save(job);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            ImportParser.Turn t = turns.get(i);
            Long id = add(spaceId, t.question(), t.answer(), ISO.format(t.occurredAt()),
                    sourceName == null ? "harness" : sourceName);
            ids.add(id);
            if (mediaList != null && i < mediaList.size() && mediaList.get(i) != null) {
                for (ImportParser.Media m : mediaList.get(i)) {
                    media.saveFromDsh(spaceId, id, m);
                }
            }
        }
        try {
            job.setIds(mapper.writeValueAsString(ids));
        } catch (Exception e) {
            job.setIds("[]");
        }
        imports.save(job);
        return progress(job.getId());
    }

    /** 给 AI 对话用的记忆上下文。 */
    public record Block(String text, List<Long> ids) {
    }

    public Block memoryBlock(Long spaceId, String query) {
        RecallResult r = recall(spaceId, query, false, 6);
        List<Long> ids = r.items().stream().map(Dtos.MemoryView::id).toList();
        if (r.items().isEmpty()) {
            return new Block("", ids);
        }
        // 摘要只负责「被检索到」，真正给模型看的是原文
        StringBuilder sb = new StringBuilder("【检索到的记忆原文】\n（按相关度排序，越靠前越相关）\n");
        int i = 1;
        for (Dtos.MemoryView v : r.items()) {
            sb.append("\n").append(i++).append(". 时间：")
                    .append(ImportParser.format(java.time.Instant.parse(v.occurredAt())))
                    .append("\n   问：").append(clip(v.question(), 300))
                    .append("\n   答：").append(clip(v.answer(), 900)).append("\n");
        }
        return new Block(sb.toString(), ids);
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    /* ---------------- 统计 / 重建 ---------------- */

    public java.util.Map<String, Object> stats(Long spaceId) {
        long total = memories.countBySpaceId(spaceId);
        Instant first = memories.firstAt(spaceId);
        Instant last = memories.lastAt(spaceId);
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("total", total);
        map.put("ready", memories.countBySpaceIdAndStatus(spaceId, "ready"));
        map.put("pending", memories.countBySpaceIdAndStatus(spaceId, "pending"));
        map.put("failed", memories.countBySpaceIdAndStatus(spaceId, "failed"));
        if (first != null) {
            map.put("firstAt", ISO.format(first));
        }
        if (last != null) {
            map.put("lastAt", ISO.format(last));
        }
        return map;
    }

    public int reindex(Long spaceId) {
        List<Memory> rows = spaceId == null
                ? memories.findAll()
                : memories.findBySpaceIdOrderByOccurredAtDesc(spaceId);
        int n = 0;
        for (Memory m : rows) {
            String text = (m.getSummary() == null ? "" : m.getSummary()) + "\n" + (m.getQuestion() == null ? "" : m.getQuestion());
            if (text.isBlank()) {
                continue;
            }
            EmbeddingService.Result emb = embeddings.embedAll(List.of(text));
            m.setEmbedding(encode(emb.vectors().get(0)));
            m.setEmbedModel(emb.model());
            m.setEmbedDim(emb.vectors().get(0).length);
            memories.save(m);
            n++;
        }
        return n;
    }

    /* ---------------- 工具 ---------------- */

    private Dtos.MemoryView toView(Memory m, Double score, Dtos.Parts parts, double strength, int ageDays) {
        List<String> keywords = new ArrayList<>();
        if (m.getKeywords() != null && !m.getKeywords().isBlank()) {
            for (String k : m.getKeywords().split(",")) {
                if (!k.isBlank()) {
                    keywords.add(k.trim());
                }
            }
        }
        return new Dtos.MemoryView(
                m.getId(), m.getSpaceId(), m.getSource(), ISO.format(m.getOccurredAt()),
                m.getQuestion(), m.getAnswer(), m.getSummary(), keywords, m.getImportance(),
                m.getStatus(), m.getError(), m.getAccessCount(),
                m.getLastAccessAt() == null ? null : ISO.format(m.getLastAccessAt()),
                score, parts, round(strength), ageDays, m.getEmbedModel());
    }

    private double strengthOf(Memory m) {
        Instant last = m.getLastAccessAt() != null ? m.getLastAccessAt() : m.getOccurredAt();
        return 0.55 + 0.45 * Math.exp(-ageOf(last) / 45);
    }

    private double ageOf(Memory m) {
        return ageOf(m.getOccurredAt());
    }

    private double ageOf(Instant instant) {
        if (instant == null) {
            return 0;
        }
        return Math.max(0, Duration.between(instant, Instant.now()).toMillis() / DAY);
    }

    private static double round(double v) {
        return Math.round(v * 1000d) / 1000d;
    }

    private double lexical(String query, Memory m) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        String q = query.toLowerCase();
        String answer = m.getAnswer() == null ? "" : m.getAnswer();
        String hay = (m.getSummary() + " " + m.getQuestion() + " " + m.getKeywords() + " " + answer).toLowerCase();
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 1 < q.length(); i++) {
            char a = q.charAt(i);
            char b = q.charAt(i + 1);
            if (isCjk(a) && isCjk(b)) {
                grams.add("" + a + b);
            }
        }
        for (String w : q.split("[^a-z0-9_]+")) {
            if (w.length() >= 2) {
                grams.add(w);
            }
        }
        if (grams.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String g : grams) {
            if (hay.contains(g)) {
                hit++;
            }
        }
        double ratio = (double) hit / grams.size();
        // 罕见长词原样命中（诗句、报错码、函数名这种）直接给高分
        for (int i = 0; i + 3 < q.length(); i++) {
            String quad = q.substring(i, i + 4);
            boolean cjk = true;
            for (char c : quad.toCharArray()) {
                if (!isCjk(c)) {
                    cjk = false;
                    break;
                }
            }
            if (cjk && hay.contains(quad)) {
                return Math.max(ratio, 0.95);
            }
        }
        return ratio;
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    static byte[] encode(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) {
            buf.putFloat(v);
        }
        return buf.array();
    }

    static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }
}
