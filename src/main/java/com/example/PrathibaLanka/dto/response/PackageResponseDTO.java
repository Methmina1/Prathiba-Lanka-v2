package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.PackageStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PackageResponseDTO {
    private Long packageId;
    private String title;
    private String description;
    private String destination;
    private Integer durationDays;
    private BigDecimal price;
    private Integer maxCapacity;
    private String itinerary;
    private PackageStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}