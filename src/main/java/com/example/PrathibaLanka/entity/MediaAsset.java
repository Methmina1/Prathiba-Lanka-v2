package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.MediaType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One uploaded file. The bytes live on disk (see {@code app.media.dir}); this row is the record of
 * what was uploaded, by whom, and under which public URL.
 */
@Entity
@Table(name = "media_asset")
@Data
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mediaId;

    /** Name as sent by the client, kept for display only - never used as a path. */
    @Column(nullable = false, length = 255)
    private String originalName;

    /** Generated name on disk, always {@code <uuid>.<ext>} with an extension we chose. */
    @Column(nullable = false, unique = true, length = 120)
    private String storedName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType;

    @Column(nullable = false)
    private Long sizeBytes;

    /** Public path the front end renders, e.g. {@code /media/8f3c....mp4}. */
    @Column(nullable = false, length = 255)
    private String url;

    @Column(length = 255)
    private String title;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private Admin uploadedBy;

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}
