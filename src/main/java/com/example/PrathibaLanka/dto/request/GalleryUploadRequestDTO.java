package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GalleryUploadRequestDTO {
    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String caption;

    private Long packageId;
}