package com.dayufish.service;

import com.dayufish.domain.Attachment;
import com.dayufish.repo.AttachmentRepository;
import com.dayufish.repo.MemoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 千问（DashScope 兼容模式）看图识字：qwen-vl-ocr。
 */
@Service
public class QwenOcrService {

    private static final Logger log = LoggerFactory.getLogger(QwenOcrService.class);

    private final AttachmentRepository attachments;
    private final MemoryRepository memories;
    private final MediaService media;
    private final LlmSettings llm;
    private final ObjectMapper mapper;
    private final RestClient http;

    public QwenOcrService(AttachmentRepository attachments, MemoryRepository memories, MediaService media, LlmSettings llm, ObjectMapper mapper) {
        this.attachments = attachments;
        this.memories = memories;
        this.media = media;
        this.llm = llm;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(90_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public long pendingCount() {
        return attachments.countByStatus("pending");
    }

    public int drain(int limit) {
        if (!llm.qwenEnabled()) {
            return 0;
        }
        List<Attachment> list = attachments.findByStatusOrderByIdAsc("pending", PageRequest.of(0, limit));
        int done = 0;
        for (Attachment a : list) {
            try {
                String text = recognize(a);
                String ocr = text == null ? "" : text.trim();
                a.setOcrText(ocr);
                a.setStatus("ready");
                a.setError("");
                attachments.save(a);
                appendToMemory(a, ocr);
                done++;
            } catch (Exception e) {
                a.setStatus("failed");
                a.setError(clip(e.getMessage(), 250));
                attachments.save(a);
                log.warn("OCR 失败：{}", e.getMessage());
            }
        }
        return done;
    }

    public String recognize(Attachment a) throws Exception {
        byte[] bytes = media.read(a);
        if (bytes.length == 0) {
            throw new IllegalStateException("图片文件读不到");
        }
        String mime = a.getMime() == null || a.getMime().isBlank() ? "image/png" : a.getMime();
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llm.qwenModel());
        body.put("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", "识别这张图里的所有文字，原样输出，不要解释，不要加标点。如果没有任何文字，输出空字符串。")))));
        body.put("max_tokens", 500);

        String raw = http.post()
                .uri(llm.qwenBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + llm.qwenKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode node = mapper.readTree(raw);
        if (node.has("error")) {
            throw new IllegalStateException(node.path("error").path("message").asText("OCR 接口返回错误"));
        }
        return node.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 把识别出的字补进那条记忆，并重新排队总结 */
    private void appendToMemory(Attachment a, String ocr) {
        if (a.getMemoryId() == null || ocr.isBlank()) {
            return;
        }
        memories.findById(a.getMemoryId()).ifPresent(m -> {
            String extra = "[图片文字] " + ocr;
            String ans = m.getAnswer() == null ? "" : m.getAnswer();
            if (!ans.contains(ocr)) {
                m.setAnswer(ans.isBlank() ? extra : ans + "\n" + extra);
                m.setStatus("pending");
                m.setSummary("");
                memories.save(m);
            }
        });
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
