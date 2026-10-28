package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 人格文本的历史版本：我是谁 / 说话方式，每次被覆盖前存一份。
 */
@Entity
@Table(name = "text_history")
public class TextHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    /** self / style */
    @Column(length = 10, nullable = false)
    private String kind = "self";

    @Column(length = 4000)
    private String text = "";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
