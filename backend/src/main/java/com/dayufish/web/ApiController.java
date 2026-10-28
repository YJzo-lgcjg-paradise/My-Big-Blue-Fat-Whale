package com.dayufish.web;

import com.dayufish.service.ChatService;
import com.dayufish.service.DeepSeekWebService;
import com.dayufish.service.DshService;
import com.dayufish.service.LlmSettings;
import com.dayufish.service.StyleService;
import com.dayufish.service.EpisodeService;
import com.dayufish.service.MediaService;
import com.dayufish.service.QwenOcrService;
import com.dayufish.service.EmbeddingService;
import com.dayufish.service.ImportParser;
import com.dayufish.service.MemoryService;
import com.dayufish.service.SummaryService;
import com.dayufish.repo.TreasureRepository;
import com.dayufish.domain.Treasure;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final MemoryService memory;
    private final SummaryService summaries;
    private final EmbeddingService embeddings;
    private final DshService dsh;
    private final ChatService chat;
    private final DeepSeekWebService deepseek;
    private final LlmSettings llm;
    private final StyleService styles;
    private final EpisodeService episodes;
    private final MediaService media;
    private final TreasureRepository treasures;
    private final QwenOcrService ocr;

    public ApiController(MemoryService memory, SummaryService summaries, EmbeddingService embeddings,
            DshService dsh, ChatService chat, DeepSeekWebService deepseek, LlmSettings llm,
            StyleService styles, EpisodeService episodes, MediaService media, QwenOcrService ocr,
            TreasureRepository treasures) {
        this.memory = memory;
        this.summaries = summaries;
        this.embeddings = embeddings;
        this.dsh = dsh;
        this.chat = chat;
        this.deepseek = deepseek;
        this.llm = llm;
        this.styles = styles;
        this.episodes = episodes;
        this.media = media;
        this.treasures = treasures;
        this.ocr = ocr;
    }

    /* ---------------- 健康 ---------------- */

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("time", Instant.now().toString());
        m.put("llm", Map.of("enabled", summaries.enabled(), "model", summaries.model()));
        m.put("embed", Map.of("online", embeddings.online(), "model", embeddings.status().model()));
        m.put("pending", memory.pendingCount());
        m.put("db", "H2 · backend/data/memory.mv.db");
        return m;
    }

    /* ---------------- 空间 ---------------- */

    @GetMapping("/spaces")
    public Map<String, Object> spaces() {
        return Map.of("spaces", memory.listSpaces());
    }

    @PostMapping("/spaces")
    public Map<String, Object> createSpace(@RequestBody Map<String, Object> body) {
        Long id = memory.createSpace(str(body.get("name")), str(body.get("note")));
        return Map.of("id", id);
    }

    @DeleteMapping("/spaces/{id}")
    public Map<String, Object> deleteSpace(@PathVariable Long id) {
        memory.deleteSpace(id);
        return Map.of("ok", true);
    }

    /* ---------------- 记忆 ---------------- */

    @GetMapping("/memories")
    public Map<String, Object> memories(
            @RequestParam(defaultValue = "1") Long spaceId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", memory.list(spaceId, q, limit, offset));
        out.put("total", memory.count(spaceId, q));
        out.put("offset", offset);
        out.put("limit", limit);
        return out;
    }

    @PostMapping("/memories")
    public Map<String, Object> addMemory(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 1L : Long.parseLong(String.valueOf(body.get("spaceId")));
        Long id = memory.add(spaceId, str(body.get("question")), str(body.get("answer")),
                str(body.get("occurredAt")), str(body.getOrDefault("source", "manual")));
        return Map.of("id", id);
    }

    @GetMapping("/memories/{id}")
    public Map<String, Object> memory(@PathVariable Long id) {
        Dtos.MemoryView view = memory.get(id);
        if (view == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "记忆不存在");
        }
        return Map.of("item", view);
    }

    @PatchMapping("/memories/{id}")
    public Map<String, Object> updateMemory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer importance = body.get("importance") == null ? null : Integer.valueOf(String.valueOf(body.get("importance")));
        Object keywords = body.get("keywords");
        String kw = keywords instanceof List<?> list
                ? String.join(",", list.stream().map(String::valueOf).toList())
                : (keywords == null ? null : String.valueOf(keywords));
        Dtos.MemoryView view = memory.update(id, body.get("summary") == null ? null : String.valueOf(body.get("summary")),
                importance, kw);
        if (view == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "记忆不存在");
        }
        return Map.of("item", view);
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, Object> deleteMemory(@PathVariable Long id) {
        memory.delete(id);
        return Map.of("ok", true);
    }

    /* ---------------- 导入 ---------------- */

    @PostMapping("/import")
    public Map<String, Object> importText(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 1L : Long.parseLong(String.valueOf(body.get("spaceId")));
        Dtos.ImportProgress p = memory.importText(spaceId, str(body.get("text")), str(body.get("sourceName")));
        return Map.of("importId", p.id(), "total", p.total());
    }

    @GetMapping("/imports/{id}")
    public Dtos.ImportProgress progress(@PathVariable Long id) {
        Dtos.ImportProgress p = memory.progress(id);
        if (p == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入记录不存在");
        }
        return p;
    }

    /* ---------------- 检索与打包 ---------------- */

    @PostMapping("/recall")
    public Map<String, Object> recall(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 1L : Long.parseLong(String.valueOf(body.get("spaceId")));
        boolean deep = Boolean.TRUE.equals(body.get("deep"));
        int limit = body.get("limit") == null ? 12 : Integer.parseInt(String.valueOf(body.get("limit")));
        MemoryService.RecallResult r = memory.recall(spaceId, str(body.get("query")), deep, limit);
        return Map.of("items", r.items(), "mode", r.mode());
    }

    @PostMapping("/bundle")
    public Map<String, Object> bundle(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 1L : Long.parseLong(String.valueOf(body.get("spaceId")));
        int maxChars = body.get("maxChars") == null ? 6000 : Integer.parseInt(String.valueOf(body.get("maxChars")));
        boolean includeTime = !Boolean.FALSE.equals(body.get("includeTime"));
        List<Long> ids = body.get("ids") instanceof List<?> list
                ? list.stream().map(x -> Long.valueOf(String.valueOf(x))).toList()
                : List.of();
        Dtos.BundleView view = memory.bundle(spaceId, str(body.get("query")), ids, maxChars, includeTime,
                str(body.getOrDefault("scope", "auto")));
        return Map.of("bundle", view);
    }

    /* ---------------- 统计与维护 ---------------- */

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) Long spaceId) {
        long id = spaceId == null ? 1L : spaceId;
        return Map.of("stats", memory.stats(id), "pending", memory.pendingCount());
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex(@RequestBody(required = false) Map<String, Object> body) {
        Long spaceId = (body == null || body.get("spaceId") == null)
                ? null
                : Long.valueOf(String.valueOf(body.get("spaceId")));
        return Map.of("updated", memory.reindex(spaceId));
    }

    /* ---------------- Harness 导入 ---------------- */

    @GetMapping("/dsh/sessions")
    public Map<String, Object> dshSessions() throws Exception {
        return Map.of("sessions", dsh.list());
    }

    @PostMapping("/dsh/import")
    public Map<String, Object> dshImport(@RequestBody Map<String, Object> body) throws Exception {
        long spaceId = body.get("spaceId") == null ? 0L : Long.parseLong(String.valueOf(body.get("spaceId")));
        String sessionId = str(body.get("sessionId"));
        JsonNode data = dsh.export(sessionId);
        List<ImportParser.Turn> turns = new ArrayList<>();
        List<List<ImportParser.Media>> media = new ArrayList<>();
        for (JsonNode t : data.path("turns")) {
            long time = t.path("occurredAt").asLong(0);
            Instant at = time > 0 ? Instant.ofEpochMilli(time) : Instant.now();
            turns.add(new ImportParser.Turn(
                    t.path("question").asText(""),
                    t.path("answer").asText(""),
                    at));
            List<ImportParser.Media> one = new ArrayList<>();
            for (JsonNode img : t.path("images")) {
                String aid = img.path("attachmentId").asText("");
                if (!aid.isBlank()) {
                    one.add(new ImportParser.Media(aid, img.path("mediaType").asText("image/png"),
                            img.path("name").asText("")));
                }
            }
            media.add(one);
        }
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("这个会话里没有可导入的问答");
        }
        Dtos.ImportProgress p = memory.importTurnsWithMedia(spaceId, turns,
                "Harness · " + data.path("title").asText("会话"), media);
        return Map.of("importId", p.id(), "total", p.total());
    }

    /* ---------------- 网页版导入 ---------------- */

    @PostMapping("/web/import")
    public Map<String, Object> webImport(@RequestBody Map<String, Object> body) {
        String url = str(body.get("url")).trim();
        String text = str(body.get("text")).trim();
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("请粘贴 chat.deepseek.com 的对话网址");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("网页版需要登录，服务端读不到页面内容；请把对话正文一起粘贴进来");
        }
        long spaceId = body.get("spaceId") == null ? 0L : Long.parseLong(String.valueOf(body.get("spaceId")));
        Dtos.ImportProgress p = memory.importText(spaceId, text, "DeepSeek 网页版 · " + url);
        return Map.of("importId", p.id(), "total", p.total());
    }

    /* ---------------- 截图上传 ---------------- */

    @PostMapping("/spaces/{id}/screenshots")
    public Map<String, Object> uploadScreenshot(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String b64 = str(body.get("base64"));
        if (b64.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少 base64");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(b64.replaceAll("^data:[^,]+,", ""));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "base64 解析失败");
        }
        String caption = str(body.get("caption"));
        if (caption.isBlank()) {
            caption = str(body.get("filename"));
        }
        Long memoryId = memory.addScreenshot(id, caption, bytes, str(body.get("mime")));
        return Map.of("id", memoryId);
    }

    /* ---------------- 图片附件 ---------------- */

    @GetMapping("/memories/{id}/attachments")
    public Map<String, Object> attachments(@PathVariable Long id) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : media.ofMemory(id)) {
            out.add(attachmentView(a));
        }
        return Map.of("items", out);
    }

    @PostMapping("/memories/{id}/attachments")
    public Map<String, Object> addAttachment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String b64 = str(body.get("base64"));
        if (b64.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少 base64");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(b64.replaceAll("^data:[^,]+,", ""));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "base64 解析失败");
        }
        Long spaceId = memory.spaceIdOf(id);
        var a = media.save(spaceId, id, bytes, str(body.get("mime")), str(body.get("caption")));
        return attachmentView(a);
    }

    @DeleteMapping("/attachments/{id}")
    public Map<String, Object> deleteAttachment(@PathVariable Long id) {
        media.delete(id);
        return Map.of("ok", true);
    }

    @GetMapping("/attachments/{id}/raw")
    public org.springframework.http.ResponseEntity<byte[]> attachmentRaw(@PathVariable Long id) {
        var a = media.get(id);
        if (a == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        byte[] bytes = media.read(a);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        a.getMime() == null || a.getMime().isBlank() ? "image/png" : a.getMime()))
                .body(bytes);
    }

    private Map<String, Object> attachmentView(com.dayufish.domain.Attachment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("kind", a.getKind());
        m.put("mime", a.getMime());
        m.put("width", a.getWidth());
        m.put("height", a.getHeight());
        m.put("ocrText", a.getOcrText());
        m.put("caption", a.getCaption());
        m.put("status", a.getStatus());
        m.put("memoryId", a.getMemoryId());
        return m;
    }

    @GetMapping("/spaces/{id}/context-mode")
    public Map<String, Object> getContextMode(@PathVariable Long id) {
        var s = memory.spaceOf(id);
        return Map.of("mode", s.getContextMode());
    }

    @PostMapping("/spaces/{id}/context-mode")
    public Map<String, Object> setContextMode(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String mode = str(body.get("mode")).isBlank() ? "budget" : str(body.get("mode"));
        memory.setContextMode(id, mode);
        return Map.of("mode", mode);
    }

    /* ---------------- 回忆 ---------------- */

    @GetMapping("/spaces/{id}/treasures")
    public Map<String, Object> treasures(@PathVariable Long id) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Treasure t : treasures.findBySpaceIdOrderByIdDesc(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("kind", t.getKind());
            m.put("title", t.getTitle());
            m.put("text", t.getText());
            m.put("why", t.getWhy());
            m.put("memoryId", t.getMemoryId());
            m.put("createdAt", String.valueOf(t.getCreatedAt()));
            out.add(m);
        }
        return Map.of("items", out);
    }

    @DeleteMapping("/treasures/{id}")
    public Map<String, Object> deleteTreasure(@PathVariable Long id) {
        treasures.deleteById(id);
        return Map.of("ok", true);
    }

    @PostMapping("/spaces/{id}/treasures")
    public Map<String, Object> addTreasure(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Treasure t = new Treasure();
        t.setSpaceId(id);
        t.setKind(str(body.get("kind")).isBlank() ? "其他" : str(body.get("kind")));
        t.setTitle(str(body.get("title")));
        t.setText(str(body.get("text")));
        t.setWhy(str(body.get("why")));
        t = treasures.save(t);
        return Map.of("id", t.getId());
    }

    /* ---------------- 事件记忆 ---------------- */

    @GetMapping("/spaces/{id}/episodes")
    public Map<String, Object> episodes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var list = limit > 0 ? episodes.page(id, limit, offset) : episodes.list(id);
        long ready = list.stream().filter(e -> "ready".equals(e.status())).count();
        long pending = list.stream().filter(e -> "pending".equals(e.status())).count();
        long failed = list.stream().filter(e -> "failed".equals(e.status())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", list);
        long all = episodes.list(id).size();
        out.put("total", all);
        out.put("offset", offset);
        out.put("limit", limit);
        out.put("ready", ready);
        out.put("pending", pending);
        out.put("failed", failed);
        out.put("partitioning", episodes.isPartitioning(id));
        return out;
    }

    @GetMapping("/episodes/{id}")
    public Map<String, Object> episode(@PathVariable Long id) {
        var view = episodes.get(id);
        if (view == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "事件不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode", view);
        out.put("turns", episodes.turnsOf(id));
        return out;
    }

    @PostMapping("/spaces/{id}/episodes/pack")
    public Map<String, Object> packEpisodes(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        int max = body == null || body.get("max") == null ? 5 : Integer.parseInt(String.valueOf(body.get("max")));
        return episodes.packForRestart(id, Math.max(1, Math.min(10, max)));
    }

    @PostMapping("/episodes/{id}/merge")
    public Map<String, Object> mergeEpisode(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        long into = Long.parseLong(String.valueOf(body.get("into")));
        var view = episodes.merge(id, into);
        return Map.of("episode", view);
    }

    @PostMapping("/spaces/{id}/episodes/partition")
    public Map<String, Object> partition(@PathVariable Long id) {
        return Map.of("created", episodes.partitionAll(id));
    }

    @PostMapping("/spaces/{id}/episodes/drain")
    public Map<String, Object> drainEpisodes(@PathVariable Long id) {
        int done = episodes.drainDiary(id, 8);
        var list = episodes.list(id);
        long pending = list.stream().filter(e -> "pending".equals(e.status())).count();
        long failed = list.stream().filter(e -> "failed".equals(e.status())).count();
        return Map.of("done", done, "pending", pending, "failed", failed);
    }

    @PostMapping("/spaces/{id}/episodes/auto-rewrite")
    public Map<String, Object> autoRewrite(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean on = !Boolean.FALSE.equals(body.get("enabled"));
        memory.setEpisodeAutoRewrite(id, on);
        return Map.of("enabled", on);
    }

    /* ---------------- 说话方式 ---------------- */

    @GetMapping("/spaces/{id}/style")
    public Map<String, Object> getStyle(@PathVariable Long id) {
        return styleBody(id, styles.get(id));
    }

    @PostMapping("/spaces/{id}/style")
    public Map<String, Object> makeStyle(@PathVariable Long id) {
        return styleBody(id, styles.generate(id));
    }

    @PostMapping("/spaces/{id}/restyle")
    public Map<String, Object> restyle(@PathVariable Long id) {
        return Map.of("queued", memory.restyle(id));
    }

    @GetMapping("/spaces/{id}/history")
    public Map<String, Object> history(@PathVariable Long id,
            @RequestParam(defaultValue = "self") String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var h : styles.historyOf(id, kind)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("text", h.getText());
            m.put("createdAt", String.valueOf(h.getCreatedAt()));
            out.add(m);
        }
        return Map.of("items", out);
    }

    @GetMapping("/spaces/{id}/persona/self")
    public Map<String, Object> getSelf(@PathVariable Long id) {
        var s = styles.get(id);
        return Map.of("text", styles.selfText(id));
    }

    @PostMapping("/spaces/{id}/persona/self")
    public Map<String, Object> generateSelf(@PathVariable Long id) {
        var s = styles.generateSelf(id);
        return Map.of("text", s.text(), "basis", s.basis(), "at", String.valueOf(s.updatedAt()));
    }

    @PostMapping("/spaces/{id}/persona/self/save")
    public Map<String, Object> saveSelf(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var s = styles.saveSelf(id, str(body.get("text")));
        return Map.of("text", s.text());
    }

    @GetMapping("/spaces/{id}/style/candidates")
    public Map<String, Object> styleCandidates(@PathVariable Long id,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "30") int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var p : styles.candidates(id, q, limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("question", p.question());
            m.put("answer", p.answer());
            out.add(m);
        }
        return Map.of("items", out, "total", out.size());
    }

    @PostMapping("/spaces/{id}/style/picks")
    public Map<String, Object> setStylePicks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    ids.add(Long.parseLong(String.valueOf(o)));
                }
            }
        }
        styles.setPicks(id, ids);
        return styleBody(id, styles.get(id));
    }

    private Map<String, Object> styleBody(Long spaceId, StyleService.Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("style", p.style());
        m.put("samples", p.samples());
        m.put("updatedAt", p.updatedAt() == null ? null : p.updatedAt().toString());
        List<Map<String, Object>> picks = new ArrayList<>();
        for (StyleService.Picked pk : styles.picks(spaceId)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", pk.id());
            one.put("question", pk.question());
            one.put("answer", pk.answer());
            picks.add(one);
        }
        m.put("picks", picks);
        return m;
    }

    /* ---------------- 模型 API ---------------- */

    @GetMapping("/llm")
    public Map<String, Object> llmStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", llm.enabled());
        m.put("masked", llm.masked());
        m.put("model", llm.model());
        m.put("baseUrl", llm.baseUrl());
        m.put("qwenConfigured", llm.qwenEnabled());
        m.put("qwenMasked", llm.maskedQwen());
        m.put("qwenModel", llm.qwenModel());
        return m;
    }

    @PostMapping("/llm/key")
    public Map<String, Object> llmKey(@RequestBody Map<String, Object> body) {
        llm.set(str(body.get("key")));
        return llmStatus();
    }

    @PostMapping("/llm/qwen")
    public Map<String, Object> qwenKey(@RequestBody Map<String, Object> body) {
        llm.setQwenKey(str(body.get("key")));
        return llmStatus();
    }

    @PostMapping("/llm/qwen/clear")
    public Map<String, Object> qwenClear() {
        llm.clearQwen();
        return llmStatus();
    }

    @PostMapping("/llm/clear")
    public Map<String, Object> llmClear() {
        llm.clear();
        return llmStatus();
    }

    @PostMapping("/llm/test")
    public Map<String, Object> llmTest() {
        return chat.testKey();
    }

    /* ---------------- DeepSeek 网页版（用 Cookie） ---------------- */

    @PostMapping("/deepseek/connect")
    public Map<String, Object> deepseekConnect(@RequestBody Map<String, Object> body) {
        deepseek.setCredential(str(body.get("credential")));
        List<DeepSeekWebService.Session> sessions = deepseek.listSessions();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DeepSeekWebService.Session s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("title", s.title());
            m.put("updatedAt", s.updatedAt());
            out.add(m);
        }
        return Map.of("ok", true, "sessions", out);
    }

    @PostMapping("/deepseek/disconnect")
    public Map<String, Object> deepseekDisconnect() {
        deepseek.clear();
        return Map.of("ok", true);
    }

    @PostMapping("/deepseek/import")
    public Map<String, Object> deepseekImport(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 0L : Long.parseLong(String.valueOf(body.get("spaceId")));
        String sessionId = str(body.get("sessionId"));
        DeepSeekWebService.Imported data = deepseek.fetch(sessionId);
        if (data.turns().isEmpty()) {
            throw new IllegalArgumentException("这个会话里没有解析出问答");
        }
        Dtos.ImportProgress p = memory.importTurns(spaceId, data.turns(), "DeepSeek 网页版");
        return Map.of("importId", p.id(), "total", p.total());
    }

    /* ---------------- 记忆体导入 / 导出 ---------------- */

    @GetMapping("/spaces/{id}/export")
    public Map<String, Object> exportSpace(@PathVariable Long id) {
        return memory.exportSpace(id);
    }

    @PostMapping("/spaces/merge")
    public Map<String, Object> mergeSpace(@RequestBody Map<String, Object> body) {
        long from = Long.parseLong(String.valueOf(body.get("fromId")));
        long to = Long.parseLong(String.valueOf(body.get("toId")));
        return Map.of("copied", memory.copySpace(from, to));
    }

    @PostMapping("/spaces/import")
    public Map<String, Object> importSpace(@RequestBody JsonNode payload) {
        String name = payload.path("name").asText("导入的记忆体");
        Long id = memory.importSpace(name, payload);
        return Map.of("id", id);
    }

    /* ---------------- AI 对话 ---------------- */

    @PostMapping("/chat")
    public Map<String, Object> chatApi(@RequestBody Map<String, Object> body) {
        long spaceId = body.get("spaceId") == null ? 0L : Long.parseLong(String.valueOf(body.get("spaceId")));
        List<ChatService.Turn> history = new ArrayList<>();
        String lastUser = "";
        if (body.get("messages") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String role = str(m.get("role"));
                    String content = str(m.get("content"));
                    history.add(new ChatService.Turn(role, content));
                    if ("user".equals(role)) {
                        lastUser = content;
                    }
                }
            }
        }
        // 先用 LLM 把这句话改写成检索查询（指代、同义词、专有名词都补上），再进记忆库找
        String searchQuery = lastUser;
        if (spaceId > 0 && !lastUser.isBlank()) {
            List<String> recent = new ArrayList<>();
            for (ChatService.Turn t : history) {
                if ("user".equals(t.role())) {
                    recent.add(t.content());
                }
            }
            if (recent.size() > 3) {
                recent = recent.subList(recent.size() - 3, recent.size());
            }
            searchQuery = summaries.expandQuery(lastUser, recent);
        }
        MemoryService.Block memBlock = spaceId > 0 ? memory.chatContext(spaceId, searchQuery) : new MemoryService.Block("", List.of());
        String block = memBlock.text();
        String styleBlock = spaceId > 0 ? styles.block(spaceId) : "";
        ChatService.Reply reply = chat.chat(block, styleBlock, history);

        int used = 0;
        if (!block.isBlank()) {
            used = (int) block.lines().filter(l -> l.matches("^\\d+\\..*")).count();
        }
        // 对话本身也进记忆体：走和导入一样的切分 → 总结 → 向量化
        boolean saved = false;
        String text = reply.reply() == null ? "" : reply.reply();
        boolean failed = text.isBlank()
                || text.startsWith("调用模型失败")
                || text.startsWith("还没有配置");
        if (spaceId > 0 && !lastUser.isBlank() && !failed) {
            memory.add(spaceId, lastUser, text, java.time.Instant.now().toString(), "chat");
            saved = true;
            styles.ensureAsync(spaceId, memory.count(spaceId, ""));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", reply.reply());
        out.put("model", reply.model());
        out.put("memoryUsed", used);
        out.put("memoryIds", memBlock.ids());
        out.put("saved", saved);
        out.put("searchQuery", searchQuery);
        out.put("styled", !styleBlock.isBlank());
        return out;
    }

    /* ---------------- 错误 ---------------- */

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static class ApiException extends RuntimeException {
        private final HttpStatus status;

        public ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    @ExceptionHandler(ApiException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBad(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleAny(Exception e, HttpServletRequest req) {
        return Map.of("error", e.getMessage() == null ? "服务器错误" : e.getMessage());
    }
}
