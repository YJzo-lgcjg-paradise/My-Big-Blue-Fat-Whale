package com.dayufish;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类：IDE 里直接 Run，或 mvn spring-boot:run。
 * 打开 http://127.0.0.1:8787/api/health 能看到状态就说明起来了。
 */
@SpringBootApplication
@EnableScheduling
public class DayufishApplication {

    public static void main(String[] args) {
        locateDataDir();
        EnvBootstrap.load();
        boolean desktop = Boolean.getBoolean("dayufish.desktop");
        if (desktop) {
            int port = pickFreePort();
            System.setProperty("server.port", String.valueOf(port));
            System.out.println("[desktop] 使用端口 " + port);
        } else {
            checkPort();
        }
        SpringApplication.run(DayufishApplication.class, args);
        if (desktop) {
            openBrowserWhenReady(Integer.parseInt(System.getProperty("server.port")));
        }
    }

    /** 等端口真的能连上再打开浏览器，避免白屏。 */
    private static void openBrowserWhenReady(int port) {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 60; i++) {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
                    break;
                } catch (Exception e) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }
            openBrowser("http://127.0.0.1:" + port);
        }, "open-browser");
        t.setDaemon(true);
        t.start();
    }

    private static void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Throwable ignored) {
            // 没图形环境就算了
        }
    }

    /** 桌面版：从 8790 开始找一个空闲端口，最多试到 8810。 */
    private static int pickFreePort() {
        int start = Integer.parseInt(System.getProperty("dayufish.port.start", "8790"));
        for (int p = start; p < start + 20; p++) {
            try (java.net.ServerSocket s = new java.net.ServerSocket()) {
                s.bind(new java.net.InetSocketAddress("127.0.0.1", p));
                return p;
            } catch (Exception ignored) {
                // 被占用就试下一个
            }
        }
        return start;
    }


    /** 8787 被占用时给个能看懂的提示，而不是一句 Failed to launch JVM。 */
    private static void checkPort() {
        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "8787")));
        try (java.net.ServerSocket probe = new java.net.ServerSocket()) {
            probe.bind(new java.net.InetSocketAddress("127.0.0.1", port));
        } catch (Exception e) {
            String msg = "端口 " + port + " 已经被占用。\n\n"
                    + "很可能已经有一个 My Big Blue Fat Whale 在运行，\n"
                    + "或者你还在跑开发版的后端（java -jar …）。\n\n"
                    + "先关掉它，再重新打开本程序。";
            System.err.println(msg);
            try {
                javax.swing.JOptionPane.showMessageDialog(null, msg, "My Big Blue Fat Whale", javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
                // 没有图形环境就只打印
            }
            System.exit(2);
        }
    }

    /**
     * 打包成 exe 之后，数据要落在程序目录旁边，而不是「启动时的当前目录」——
     * 从桌面快捷方式启动时，当前目录可能是 C:\Windows\System32。
     */
    private static void locateDataDir() {
        if (System.getProperty("dayufish.data.dir") != null) {
            return;
        }
        try {
            Path self = Path.of(DayufishApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(self) ? self : self.getParent();
            // jpackage 的结构是 <app>/app/<jar>，data 放在 <app>/data
            if (dir != null && "app".equalsIgnoreCase(String.valueOf(dir.getFileName()))
                    && dir.getParent() != null) {
                System.setProperty("dayufish.data.dir", dir.getParent().resolve("data").toString());
                System.setProperty("dayufish.no-env-scan", "true");
            }
        } catch (Exception ignored) {
            // 取不到就用默认 ./data
        }
    }
}
