package com.dayufish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 backend/.env.local，把 key 塞进系统属性，这样 application.yml 里的
 * ${DEEPSEEK_API_KEY:} 能直接取到，不必再配 IDEA 的运行环境变量。
 * 首次启动若文件不存在，会尝试从本机已有项目里借 key，并生成一份。
 */
final class EnvBootstrap {

    private static final Path[] CANDIDATES = {
            Paths.get(".env.local"),
            Paths.get("backend", ".env.local"),
            Paths.get("..", "backend", ".env.local")
    };

    private EnvBootstrap() {
    }

    static void load() {
        if (Boolean.getBoolean("dayufish.no-env-scan")) {
            return;
        }
        try {
            Path envFile = null;
            for (Path p : CANDIDATES) {
                if (Files.exists(p)) {
                    envFile = p;
                    break;
                }
            }
            Map<String, String> env = envFile != null
                    ? parse(Files.readString(envFile))
                    : createDefault();
            env.forEach((k, v) -> {
                if (v != null && !v.isBlank()
                        && System.getProperty(k) == null
                        && System.getenv(k) == null) {
                    System.setProperty(k, v);
                }
            });
        } catch (Exception e) {
            System.err.println("[env] 读取 .env.local 失败：" + e.getMessage());
        }
    }

    private static Map<String, String> createDefault() throws IOException {
        Map<String, String> detected = detect();
        List<String> lines = new ArrayList<>();
        lines.add("# My Big Blue Fat Whale · 本地配置（首次启动自动生成，可自行修改）");
        lines.add("# 留空的 key 会让对应能力退回离线模式");
        lines.add("DEEPSEEK_API_KEY=" + detected.getOrDefault("DEEPSEEK_API_KEY", ""));
        lines.add("DEEPSEEK_BASE_URL=" + detected.getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com"));
        lines.add("DEEPSEEK_MODEL=" + detected.getOrDefault("DEEPSEEK_MODEL", "deepseek-chat"));
        lines.add("ZHIPU_API_KEY=" + detected.getOrDefault("ZHIPU_API_KEY", ""));
        lines.add("ZHIPU_EMBED_MODEL=embedding-3");
        lines.add("");
        Path target = Paths.get(".env.local");
        Files.writeString(target, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
        System.out.println("[env] 已生成 " + target.toAbsolutePath());
        return parse(String.join("\n", lines));
    }

    private static Map<String, String> detect() {
        Map<String, String> found = new LinkedHashMap<>();
        borrow(found, "C:/Users/user/Desktop/paradise-video-official-20260804-1346/paradise-ai-deploy/.env",
                Map.of("PARADISE_DEEPSEEK_API_KEY", "DEEPSEEK_API_KEY",
                        "PARADISE_DEEPSEEK_BASE_URL", "DEEPSEEK_BASE_URL"));
        borrow(found, "C:/Users/user/Desktop/Gager-backup-20260728/.env",
                Map.of("ZHIPU_API_KEY", "ZHIPU_API_KEY"));
        return found;
    }

    private static void borrow(Map<String, String> found, String file, Map<String, String> mapping) {
        try {
            Path p = Paths.get(file);
            if (!Files.exists(p)) {
                return;
            }
            Map<String, String> env = parse(Files.readString(p));
            mapping.forEach((from, to) -> {
                String v = env.get(from);
                if (v != null && !v.isBlank() && !found.containsKey(to)) {
                    found.put(to, v);
                }
            });
        } catch (Exception ignored) {
            // 单个文件失败不影响启动
        }
    }

    private static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int i = line.indexOf('=');
            if (i < 0) {
                continue;
            }
            String k = line.substring(0, i).trim();
            String v = line.substring(i + 1).trim();
            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                v = v.substring(1, v.length() - 1);
            }
            out.put(k, v);
        }
        return out;
    }
}
