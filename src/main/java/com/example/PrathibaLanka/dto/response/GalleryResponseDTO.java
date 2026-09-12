package com.example.PrathibaLanka.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GalleryResponseDTO {
    private Long imageId;
    private String imageUrl;
    private String caption;
    private Long packageId;
    private String packageTitle;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
}