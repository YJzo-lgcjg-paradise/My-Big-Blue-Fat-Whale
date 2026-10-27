package com.dayufish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 运行时可改的 DeepSeek 配置。
 * 优先级：界面上填的（data/llm.json）> 启动时读到的属性 / .env.local。
 */
@Service
public class LlmSettings {

    private static final Logger log = LoggerFactory.getLogger(LlmSettings.class);
    private static final Path FILE = Path.of(System.getProperty("dayufish.data.dir", "data"), "llm.json");

    private final ObjectMapper mapper;

    @Value("${dayufish.deepseek.api-key:}")
    private String envKey;

    @Value("${dayufish.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${dayufish.deepseek.model:deepseek-chat}")
    private String model;

    private volatile String key = "";

    /** 千问（DashScope）的 key，用来看图识字 */
    private volatile String qwenKey = "";

    @Value("${dayufish.qwen.model:qwen-vl-ocr}")
    private String qwenModel;

    @Value("${dayufish.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenBaseUrl;

    public LlmSettings(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void load() {
        key = envKey == null ? "" : envKey.trim();
        try {
            if (Files.exists(FILE)) {
                JsonNode n = mapper.readTree(FILE.toFile());
                String saved = n.path("key").asText("").trim();
                if (!saved.isBlank()) {
                    key = saved;
                }
                String m = n.path("model").asText("").trim();
                if (!m.isBlank()) {
                    model = m;
                }
                String qk = n.path("qwenKey").asText("").trim();
                if (!qk.isBlank()) {
                    qwenKey = qk;
                }
            }
        } catch (Exception e) {
            log.warn("读取 data/llm.json 失败：{}", e.getMessage());
        }
    }

    public boolean enabled() {
        return !key.isBlank();
    }

    public String key() {
        return key;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public String masked() {
        if (key.isBlank()) {
            return "";
        }
        if (key.length() <= 12) {
            return "****";
        }
        return key.substring(0, 6) + "…" + key.substring(key.length() - 4);
    }

    public void set(String newKey) {
        String k = newKey == null ? "" : newKey.trim();
        if (k.isEmpty()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (k.startsWith("Bearer ")) {
            k = k.substring(7).trim();
        }
        key = k;
        save();
    }

    public void clear() {
        key = "";
        save();
    }

    public boolean qwenEnabled() {
        return !qwenKey.isBlank();
    }

    public String qwenKey() {
        return qwenKey;
    }

    public String qwenModel() {
        return qwenModel;
    }

    public String qwenBaseUrl() {
        return qwenBaseUrl;
    }

    public String maskedQwen() {
        if (qwenKey.isBlank()) {
            return "";
        }
        return qwenKey.length() <= 12 ? "****" : qwenKey.substring(0, 6) + "…" + qwenKey.substring(qwenKey.length() - 4);
    }

    public void setQwenKey(String newKey) {
        String k = newKey == null ? "" : newKey.trim();
        if (k.isEmpty()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (k.startsWith("Bearer ")) {
            k = k.substring(7).trim();
        }
        qwenKey = k;
        save();
    }

    public void clearQwen() {
        qwenKey = "";
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            var node = mapper.createObjectNode();
            node.put("key", key);
            node.put("model", model);
            node.put("qwenKey", qwenKey);
            node.put("qwenModel", qwenModel);
            mapper.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), node);
        } catch (Exception e) {
            log.warn("写入 data/llm.json 失败：{}", e.getMessage());
        }
    }
}
