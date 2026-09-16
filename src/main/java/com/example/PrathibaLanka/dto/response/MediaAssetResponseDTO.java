package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.MediaType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaAssetResponseDTO {

    private Long mediaId;
    private String originalName;
    private String contentType;
    private MediaType mediaType;
    private Long sizeBytes;
    private String url;
    private String title;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
}
