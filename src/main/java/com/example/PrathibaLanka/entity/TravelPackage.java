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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 150)
    private String destination;

    private Integer durationDays;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer maxCapacity;

    @Column(columnDefinition = "TEXT")
    private String itinerary;

    @Enumerated(EnumType.STRING)
    private PackageStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}