package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 珍贵记忆：只有双方才懂的梗、特别开心的时刻、重要的约定、突然的感悟。
 */
@Entity
@Table(name = "treasures")
public class Treasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "memory_id")
    private Long memoryId;

    @Column(name = "episode_id")
    private Long episodeId;

    /** 梗 / 开心 / 约定 / 感悟 / 其他 */
    @Column(length = 20)
    private String kind = "其他";

    @Column(length = 60)
    private String title = "";

    @Column(length = 600)
    private String text = "";

    /** 为什么觉得它珍贵 */
    @Column(length = 300)
    private String why = "";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
