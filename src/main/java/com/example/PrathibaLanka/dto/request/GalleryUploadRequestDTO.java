package com.example.PrathibaLanka.dto.request;

import com.example.PrathibaLanka.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GalleryUploadRequestDTO {
    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String caption;

    private Long packageId;

    /** IMAGE or VIDEO; anything missing or unknown is treated as an image. */
    private MediaType mediaType;
}