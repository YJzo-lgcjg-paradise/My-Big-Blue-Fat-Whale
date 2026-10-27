package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 事件记忆：由若干条对话归纳出来的「一件事」，带第一人称日记。
 */
@Entity
@Table(name = "episodes")
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(length = 80)
    private String title = "";

    @Column(length = 1200)
    private String diary = "";

    @Column(name = "mood_from", length = 20)
    private String moodFrom = "";

    @Column(name = "mood_to", length = 20)
    private String moodTo = "";

    @Column(name = "mood_intensity")
    private Double moodIntensity = 0.0;

    @Column(name = "mood_because", length = 300)
    private String moodBecause = "";

    @Column(name = "relation_note", length = 600)
    private String relationNote = "";

    @Column(name = "open_threads", length = 1200)
    private String openThreads = "";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_at")
    private Instant lastAt;

    @Column(name = "turn_count")
    private Integer turnCount = 0;

    private Integer importance = 3;

    @Lob
    private byte[] embedding;

    @Column(name = "embed_model", length = 60)
    private String embedModel = "";

    @Column(name = "embed_dim")
    private Integer embedDim = 0;

    @Column(length = 20)
    private String status = "pending";

    /** 拆不动的长事件：日记改滚动式，续命包取最近 10 轮原文 */
    @Column(name = "is_long_running")
    private Boolean isLongRunning = false;

    /** 子事件指回父事件；父事件为空表示顶层 */
    @Column(name = "split_of")
    private Long splitOf;

    @Column(name = "part_no")
    private Integer partNo = 0;

    @Column(name = "edited_by_user")
    private Boolean editedByUser = false;

    @Column(length = 300)
    private String error = "";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDiary() { return diary; }
    public void setDiary(String diary) { this.diary = diary; }
    public String getMoodFrom() { return moodFrom; }
    public void setMoodFrom(String moodFrom) { this.moodFrom = moodFrom; }
    public String getMoodTo() { return moodTo; }
    public void setMoodTo(String moodTo) { this.moodTo = moodTo; }
    public Double getMoodIntensity() { return moodIntensity; }
    public void setMoodIntensity(Double moodIntensity) { this.moodIntensity = moodIntensity; }
    public String getMoodBecause() { return moodBecause; }
    public void setMoodBecause(String moodBecause) { this.moodBecause = moodBecause; }
    public String getRelationNote() { return relationNote; }
    public void setRelationNote(String relationNote) { this.relationNote = relationNote; }
    public String getOpenThreads() { return openThreads; }
    public void setOpenThreads(String openThreads) { this.openThreads = openThreads; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getLastAt() { return lastAt; }
    public void setLastAt(Instant lastAt) { this.lastAt = lastAt; }
    public Integer getTurnCount() { return turnCount; }
    public void setTurnCount(Integer turnCount) { this.turnCount = turnCount; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public byte[] getEmbedding() { return embedding; }
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }
    public String getEmbedModel() { return embedModel; }
    public void setEmbedModel(String embedModel) { this.embedModel = embedModel; }
    public Integer getEmbedDim() { return embedDim; }
    public void setEmbedDim(Integer embedDim) { this.embedDim = embedDim; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getEditedByUser() { return editedByUser; }
    public void setEditedByUser(Boolean editedByUser) { this.editedByUser = editedByUser; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Boolean getIsLongRunning() { return isLongRunning; }

    public void setIsLongRunning(Boolean isLongRunning) { this.isLongRunning = isLongRunning; }

    public Long getSplitOf() { return splitOf; }

    public void setSplitOf(Long splitOf) { this.splitOf = splitOf; }

    public Integer getPartNo() { return partNo; }

    public void setPartNo(Integer partNo) { this.partNo = partNo; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
