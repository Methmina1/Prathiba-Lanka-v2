package com.example.PrathibaLanka.entity;

import com.example.PrathibaLanka.enums.PackageStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_package")
@Data
public class TravelPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long packageId;

    @Column(nullable = false, length = 150)
    private String title;

    /** One or two sentences for the card and the journey header. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The full write-up, shown on the journey page and in the "read more" dialog: several paragraphs
     * separated by a blank line.
     */
    @Column(name = "long_description", columnDefinition = "TEXT")
    private String longDescription;

    @Column(length = 150)
    private String destination;

    private Integer durationDays;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer maxCapacity;

    @Column(columnDefinition = "TEXT")
    private String itinerary;

    /**
     * Cover image for the journey card and detail page. Uploaded through the media library, so this
     * holds a path like /media/<uuid>.jpg (or any hosted URL). Null means "use the drawn scene".
     */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private PackageStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}