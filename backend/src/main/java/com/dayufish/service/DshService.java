package com.dayufish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 读取本机 DeepSeek Harness 的会话。
 * Harness 把会话按 zstd 帧追加写在 ~/.dsh/sessions，Java 没有内置 zstd，
 * 所以这里调用同仓库里的 node 脚本（scripts/dsh.mjs）来解压和归一化。
 */
@Service
public class DshService {

    private static final Logger log = LoggerFactory.getLogger(DshService.class);

    private final ObjectMapper mapper;

    @Value("${dayufish.dsh.node:node}")
    private String node;

    @Value("${dayufish.dsh.script:scripts/dsh.mjs}")
    private String script;

    public DshService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Session(String id, String title, String cwd, long createdAt, long updatedAt, int userTurns) {
    }

    public List<Session> list() throws Exception {
        JsonNode root = run("list");
        if (!root.path("ok").asBoolean(false)) {
            throw new IllegalStateException(root.path("error").asText("读取 Harness 会话失败"));
        }
        List<Session> out = new ArrayList<>();
        for (JsonNode n : root.path("sessions")) {
            out.add(new Session(
                    n.path("id").asText(),
                    n.path("title").asText("未命名会话"),
                    n.path("cwd").asText(""),
                    n.path("createdAt").asLong(0),
                    n.path("updatedAt").asLong(0),
                    n.path("userTurns").asInt(0)));
        }
        return out;
    }

    public JsonNode export(String sessionId) throws Exception {
        JsonNode root = run("export", sessionId);
        if (!root.path("ok").asBoolean(false)) {
            throw new IllegalArgumentException(root.path("error").asText("会话不存在"));
        }
        return root;
    }

    /** 打包环境里没有全局 node，退回 DSH 自带的 node */
    private String resolveNode() {
        String configured = node == null ? "node" : node.trim();
        if (!configured.isEmpty() && !configured.equals("node")) {
            return configured;
        }
        java.io.File f = new java.io.File(System.getProperty("user.home"),
                ".dsh/dsh-runtimes/dsh-primary-runtime/dependencies/node/bin/node.exe");
        if (f.isFile()) {
            return f.getAbsolutePath();
        }
        return configured.isEmpty() ? "node" : configured;
    }

    /** 脚本路径：打包后 CWD 可能是 server 目录，按多种基准找 scripts/ */
    private java.io.File resolveScript() {
        java.io.File configured = new java.io.File(script);
        if (configured.isAbsolute() && configured.isFile()) {
            return configured;
        }
        List<java.io.File> bases = new ArrayList<>();
        bases.add(new java.io.File(System.getProperty("user.dir")));
        bases.add(new java.io.File(System.getProperty("user.dir")).getParentFile());
        bases.add(new java.io.File(System.getProperty("user.dir")).getParentFile().getParentFile());
        String dataDir = System.getProperty("dayufish.data.dir");
        if (dataDir != null && !dataDir.isBlank()) {
            java.io.File d = new java.io.File(dataDir).getAbsoluteFile();
            bases.add(d.getParentFile());
            bases.add(d.getParentFile() == null ? null : d.getParentFile().getParentFile());
        }
        for (java.io.File b : bases) {
            if (b == null) {
                continue;
            }
            java.io.File f = new java.io.File(b, script);
            if (f.isFile()) {
                return f;
            }
        }
        return configured.getAbsoluteFile();
    }

    private JsonNode run(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        java.io.File scriptFile = resolveScript();
        cmd.add(resolveNode());
        cmd.add(scriptFile.getAbsolutePath());
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.directory(scriptFile.getParentFile());
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(90, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("读取 Harness 会话超时");
        }
        if (p.exitValue() != 0) {
            log.warn("dsh.mjs 退出码 {}：{}", p.exitValue(), output);
        }
        return mapper.readTree(output);
    }
}
