package com.dayufish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "spaces")
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 200)
    private String note = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** 从历史对话里总结出的说话方式 */
    @Column(name = "style_profile", length = 1200)
    private String styleProfile = "";

    @Column(name = "style_at")
    private Instant styleAt;

    @Column(name = "style_samples")
    private Integer styleSamples = 0;

    /** 用户挑出来当风格样例的记忆 id，逗号分隔 */
    @Column(name = "style_picks", length = 600)
    private String stylePicks = "";

    /** 事件新增对话后，是否自动重写日记 */
    @Column(name = "episode_auto_rewrite")
    private Boolean episodeAutoRewrite = true;

    public Space() {
    }

    public Space(String name, String note) {
        this.name = name;
        this.note = note == null ? "" : note;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStyleProfile() {
        return styleProfile;
    }

    public void setStyleProfile(String styleProfile) {
        this.styleProfile = styleProfile;
    }

    public Instant getStyleAt() {
        return styleAt;
    }

    public void setStyleAt(Instant styleAt) {
        this.styleAt = styleAt;
    }

    public Integer getStyleSamples() {
        return styleSamples;
    }

    public void setStyleSamples(Integer styleSamples) {
        this.styleSamples = styleSamples;
    }

    public String getStylePicks() {
        return stylePicks;
    }

    public void setStylePicks(String stylePicks) {
        this.stylePicks = stylePicks;
    }

    public Boolean getEpisodeAutoRewrite() { return episodeAutoRewrite; }

    public void setEpisodeAutoRewrite(Boolean episodeAutoRewrite) { this.episodeAutoRewrite = episodeAutoRewrite; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
