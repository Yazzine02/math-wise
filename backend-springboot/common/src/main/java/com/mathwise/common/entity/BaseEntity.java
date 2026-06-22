package com.mathwise.common.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
Why UUIDs ?
Do not use auto-incrementing integers (1, 2, 3) for your primary keys. Use UUIDs (v4 or v7).
It makes database merging easier, prevents bad actors from guessing URLs (e.g., /api/users/45),
and allows mobile clients to generate IDs offline if you ever add offline support.
 */

/*
Why LAZY loadig ?
Every relationship (@OneToMany, @ManyToOne) must be explicitly set to fetch lazily.
If you load a Student, you do not want Hibernate automatically pulling all 5,000 of their
past interactions into RAM unless you explicitly ask for them (preventing the dreaded
N+1 query problem).
 */

/*
Telemetry and Auditing:
No row is ever actually deleted. We add created_at, updated_at, and is_active flags to every table.
If an admin deletes a buggy exercise, it just toggles the flag.
 */
@MappedSuperclass
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // This is JPA entity lifecycle event management, see: https://www.baeldung.com/jpa-entity-lifecycle-events
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // getters and setters section

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
