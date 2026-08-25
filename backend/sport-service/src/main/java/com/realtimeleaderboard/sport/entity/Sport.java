package com.realtimeleaderboard.sport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sports",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_sports_code", columnNames = "code"),
        indexes = @jakarta.persistence.Index(name = "idx_sports_code", columnList = "code"))
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Constrained to the SportCode enum: the service only accepts the sports it
     * knows. The database stores a plain varchar so new enum values require no
     * schema migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private SportCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Sport() {
    }

    public Sport(SportCode code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SportCode getCode() {
        return code;
    }

    public void setCode(SportCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
