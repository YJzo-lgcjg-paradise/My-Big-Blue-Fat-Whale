package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 对话里的图片 / 表情包。文件按内容哈希落盘，行只记引用。
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "memory_id")
    private Long memoryId;

    /** image / sticker */
    @Column(length = 20)
    private String kind = "image";

    @Column(length = 64, nullable = false)
    private String sha256 = "";

    @Column(length = 300, nullable = false)
    private String path = "";

    @Column(length = 60)
    private String mime = "";

    @Column(name = "width")
    private Integer width = 0;

    @Column(name = "height")
    private Integer height = 0;

    @Column(name = "ocr_text", length = 2000)
    private String ocrText = "";

    @Column(length = 500)
    private String caption = "";

    /** pending / ready / failed / skipped */
    @Column(length = 20)
    private String status = "pending";

    @Column(length = 300)
    private String error = "";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
