package com.example.PrathibaLanka.dto.request;

import com.example.PrathibaLanka.enums.PackageStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PackageRequestDTO {
    @NotBlank(message = "Title is required")
    private String title;

    /** One or two sentences: what the card and the journey header show. */
    private String description;

    /** The full write-up for the journey page and the "read more" dialog. */
    private String longDescription;

    @NotBlank(message = "Destination is required")
    private String destination;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal price;

    @Min(value = 1, message = "Max capacity must be at least 1")
    private Integer maxCapacity;

    private String itinerary;

    /** Media library path (/media/...) or a hosted URL. Empty clears it; null leaves it unchanged. */
    @Size(max = 255, message = "Image URL must be at most 255 characters")
    private String imageUrl;

    private PackageStatus status;
}