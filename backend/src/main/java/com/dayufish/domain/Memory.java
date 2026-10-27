package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "memories", indexes = {
        @Index(name = "idx_memory_space", columnList = "space_id"),
        @Index(name = "idx_memory_status", columnList = "status")
})
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(length = 80, nullable = false)
    private String source = "manual";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(length = 100_000)
    private String question = "";

    @Column(length = 100_000)
    private String answer = "";

    @Column(length = 4_000)
    private String summary = "";

    @Column(length = 500)
    private String keywords = "";

    @Column(nullable = false)
    private int importance = 3;

    @Column(length = 16, nullable = false)
    private String status = "pending";

    @Column(length = 500)
    private String error = "";

    @Lob
    private byte[] embedding;

    @Column(name = "embed_model", length = 64)
    private String embedModel = "";

    @Column(name = "embed_dim")
    private int embedDim;

    @Column(name = "access_count", nullable = false)
    private int accessCount;

    @Column(name = "last_access_at")
    private Instant lastAccessAt;

    /** 归属的事件 */
    @Column(name = "episode_id")
    private Long episodeId;

    /** 对话级第一人称小记：我当时记住的这一句 */
    @Column(name = "diary_snippet", length = 400)
    private String diarySnippet = "";

    /** 归类状态：空=未归类，assigned=已归类，fallback=兜底归类 */
    @Column(name = "episode_status", length = 20)
    private String episodeStatus = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public String getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(String embedModel) {
        this.embedModel = embedModel;
    }

    public int getEmbedDim() {
        return embedDim;
    }

    public void setEmbedDim(int embedDim) {
        this.embedDim = embedDim;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public Long getEpisodeId() { return episodeId; }

    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }

    public String getDiarySnippet() { return diarySnippet; }

    public void setDiarySnippet(String diarySnippet) { this.diarySnippet = diarySnippet; }

    public String getEpisodeStatus() { return episodeStatus; }

    public void setEpisodeStatus(String episodeStatus) { this.episodeStatus = episodeStatus; }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Instant lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
