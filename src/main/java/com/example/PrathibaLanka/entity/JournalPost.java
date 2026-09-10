package com.example.PrathibaLanka.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_post")
@Data
public class JournalPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long journalId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(length = 20)
    private String status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void publish() {
        this.status = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
    }

    public void unPublish() {
        this.status = "DRAFT";
        this.publishedAt = null;
    }
}