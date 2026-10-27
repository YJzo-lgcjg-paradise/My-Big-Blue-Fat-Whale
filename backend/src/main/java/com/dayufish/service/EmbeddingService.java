package com.dayufish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 向量化：优先智谱 embedding-3；没有 key 或调用失败时退回本地哈希向量。
 * 两者维度不同，检索时只跟同维度的比较。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int HASH_DIM = 512;
    public static final String FALLBACK_MODEL = "local-hash-512";
    private static final Pattern WORD = Pattern.compile("[a-z0-9_]{2,}");

    private final ObjectMapper mapper;
    private final RestClient http;

    @Value("${dayufish.zhipu.api-key:}")
    private String apiKey;

    @Value("${dayufish.zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${dayufish.zhipu.model:embedding-3}")
    private String model;

    public EmbeddingService(ObjectMapper mapper) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public record Result(List<float[]> vectors, String model, int dim) {
    }

    public record Status(boolean online, String model) {
    }

    public boolean online() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Status status() {
        return new Status(online(), online() ? model : FALLBACK_MODEL);
    }

    public Result embedAll(List<String> texts) {
        List<String> safe = texts.stream()
                .map(t -> t == null ? "" : (t.length() > 2000 ? t.substring(0, 2000) : t))
                .toList();
        if (online()) {
            try {
                return zhipu(safe);
            } catch (Exception e) {
                log.warn("智谱向量失败，退回本地向量：{}", e.getMessage());
            }
        }
        List<float[]> vectors = new ArrayList<>(safe.size());
        for (String t : safe) {
            vectors.add(hashEmbed(t));
        }
        return new Result(vectors, FALLBACK_MODEL, HASH_DIM);
    }

    private Result zhipu(List<String> texts) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);

        String raw = http.post()
                .uri(baseUrl + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode node = mapper.readTree(raw);
        JsonNode data = node.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("返回为空");
        }
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode emb = item.get("embedding");
            float[] v = new float[emb.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) emb.get(i).asDouble();
            }
            vectors.add(v);
        }
        return new Result(vectors, model, vectors.get(0).length);
    }

    /* ---------------- 本地兜底：中文单字 + 二元组 + 英文词，FNV-1a 哈希 ---------------- */

    public static float[] hashEmbed(String text) {
        String s = text == null ? "" : text.toLowerCase();
        float[] v = new float[HASH_DIM];
        int prev = -1;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FA5) {
                add(v, String.valueOf(c), 0.6f);
                if (prev >= 0) {
                    add(v, new String(Character.toChars(prev)) + c, 1.2f);
                }
                prev = c;
            } else {
                prev = -1;
            }
        }

        Matcher m = WORD.matcher(s);
        while (m.find()) {
            add(v, m.group(), 1.6f);
        }

        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }

    private static void add(float[] v, String token, float weight) {
        int h = fnv1a(token);
        int idx = Math.floorMod(h, HASH_DIM);
        float sign = ((h >>> 16) & 1) == 1 ? 1f : -1f;
        v[idx] += sign * weight;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x01000193;
        }
        return h;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
