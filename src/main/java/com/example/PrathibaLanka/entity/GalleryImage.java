package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.MediaType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_image")
@Data
public class GalleryImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    @Column(nullable = false, length = 255)
    private String imageUrl;

    @Column(length = 255)
    private String caption;

    /** IMAGE or VIDEO. Nullable so rows created before videos existed stay valid, and read as IMAGE. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MediaType mediaType;

    @ManyToOne
    @JoinColumn(name = "package_id")
    private TravelPackage travelPackage;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private Admin uploadedBy;

    @CreationTimestamp
    private LocalDateTime uploadedAt;

    @PrePersist
    void defaultMediaType() {
        if (mediaType == null) {
            mediaType = MediaType.IMAGE;
        }
    }
}