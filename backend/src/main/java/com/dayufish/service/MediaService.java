package com.dayufish.service;

import com.dayufish.domain.Attachment;
import com.dayufish.repo.AttachmentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 图片落盘：按内容哈希命名，同一张表情包只存一份。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final AttachmentRepository attachments;

    public MediaService(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    private static Path root() {
        return Path.of(System.getProperty("dayufish.data.dir", "data"), "media");
    }

    public Attachment save(Long spaceId, Long memoryId, byte[] bytes, String mime, String caption) {
        try {
            String sha = sha256(bytes);
            Attachment a = attachments.findFirstBySha256AndSpaceId(sha, spaceId).orElseGet(Attachment::new);
            if (a.getId() == null) {
                String ext = extOf(mime);
                LocalDate now = LocalDate.now();
                Path dir = root().resolve(String.valueOf(spaceId))
                        .resolve(String.format("%04d%02d", now.getYear(), now.getMonthValue()));
                Files.createDirectories(dir);
                Path file = dir.resolve(sha + "." + ext);
                if (!Files.exists(file)) {
                    Files.write(file, bytes);
                }
                a.setSpaceId(spaceId);
                a.setSha256(sha);
                a.setPath(file.toString());
                a.setMime(mime == null || mime.isBlank() ? "image/png" : mime);
                a.setStatus("pending");
                try (var in = new java.io.ByteArrayInputStream(bytes)) {
                    var img = javax.imageio.ImageIO.read(in);
                    if (img != null) {
                        a.setWidth(img.getWidth());
                        a.setHeight(img.getHeight());
                    }
                } catch (Exception ignored) {
                    // 读不出尺寸就算了
                }
                a.setCreatedAt(java.time.Instant.now());
            }
            a.setMemoryId(memoryId == null ? a.getMemoryId() : memoryId);
            if (caption != null && !caption.isBlank()) {
                a.setCaption(clip(caption, 480));
            }
            a.setKind("image");
            return attachments.save(a);
        } catch (Exception e) {
            log.warn("保存图片失败：{}", e.getMessage());
            throw new IllegalStateException("保存图片失败：" + e.getMessage());
        }
    }

    public void delete(Long id) {
        attachments.findById(id).ifPresent(a -> {
            attachments.delete(a);
            try {
                var p = Path.of(a.getPath());
                if (Files.exists(p)) {
                    Files.delete(p);
                }
            } catch (Exception ignored) {
                // 文件删不掉就算了
            }
        });
    }

    /** 从 ~/.dsh/attachments 里取文件，落进记忆库的 media 目录 */
    public Attachment saveFromDsh(Long spaceId, Long memoryId, ImportParser.Media m) {
        String id = m.attachmentId() == null ? "" : m.attachmentId();
        String hex = id.startsWith("sha256:") ? id.substring(7) : id;
        if (hex.length() < 3) {
            return null;
        }
        // DSH 的对象存放是 objects/<前两位>/<完整哈希>
        Path file = Path.of(System.getProperty("user.home"), ".dsh", "attachments", "v1", "objects",
                hex.substring(0, 2), hex);
        try {
            if (!Files.exists(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            return save(spaceId, memoryId, bytes, m.mime(), m.name());
        } catch (Exception e) {
            log.warn("读取 DSH 附件失败：{}", e.getMessage());
            return null;
        }
    }

    public byte[] read(Attachment a) {
        try {
            return Files.readAllBytes(Path.of(a.getPath()));
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public List<Attachment> ofMemory(Long memoryId) {
        return attachments.findByMemoryIdOrderByIdAsc(memoryId);
    }

    public Attachment get(Long id) {
        return attachments.findById(id).orElse(null);
    }

    /** 图片里读出来的字，拼进记忆文本用。 */
    public String textOf(Long memoryId) {
        StringBuilder sb = new StringBuilder();
        for (Attachment a : attachments.findByMemoryIdOrderByIdAsc(memoryId)) {
            if ("sticker".equals(a.getKind())) {
                continue;
            }
            if (a.getOcrText() != null && !a.getOcrText().isBlank()) {
                sb.append("[图片文字] ").append(a.getOcrText()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String guessKind(long size, String mime) {
        // 小图 + 常见表情尺寸，粗略当作表情包
        return size > 0 && size < 120 * 1024 && (mime == null || !mime.contains("jpeg")) ? "sticker" : "image";
    }

    private static String extOf(String mime) {
        if (mime == null) {
            return "png";
        }
        if (mime.contains("jpeg") || mime.contains("jpg")) {
            return "jpg";
        }
        if (mime.contains("gif")) {
            return "gif";
        }
        if (mime.contains("webp")) {
            return "webp";
        }
        return "png";
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }

    private static String clip(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
