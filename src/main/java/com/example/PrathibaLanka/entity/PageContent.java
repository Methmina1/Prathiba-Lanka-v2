package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.ContentSection;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * The editable copy of one public page section, stored as JSON so the shape can grow without a
 * schema change. The service validates it against the section before saving.
 */
@Entity
@Table(name = "page_content", uniqueConstraints = @UniqueConstraint(columnNames = "section"))
@Data
public class PageContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentSection section;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private Admin updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
