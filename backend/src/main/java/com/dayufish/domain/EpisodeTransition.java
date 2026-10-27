package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 事件之间的接缝：避免续命包读起来像跳帧。
 */
@Entity
@Table(name = "episode_transitions")
public class EpisodeTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "from_episode_id", nullable = false)
    private Long fromEpisodeId;

    @Column(name = "to_episode_id", nullable = false)
    private Long toEpisodeId;

    /** time_gap / topic_shift / mood_shift */
    @Column(length = 20)
    private String kind = "topic_shift";

    @Column(name = "days_gap")
    private Integer daysGap = 0;

    @Column(name = "transition_note", length = 300)
    private String transitionNote = "";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getFromEpisodeId() { return fromEpisodeId; }
    public void setFromEpisodeId(Long fromEpisodeId) { this.fromEpisodeId = fromEpisodeId; }
    public Long getToEpisodeId() { return toEpisodeId; }
    public void setToEpisodeId(Long toEpisodeId) { this.toEpisodeId = toEpisodeId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getDaysGap() { return daysGap; }
    public void setDaysGap(Integer daysGap) { this.daysGap = daysGap; }
    public String getTransitionNote() { return transitionNote; }
    public void setTransitionNote(String transitionNote) { this.transitionNote = transitionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
