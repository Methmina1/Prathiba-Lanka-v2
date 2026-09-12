package com.example.PrathibaLanka.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JournalResponseDTO {
    private Long journalId;
    private String title;
    private String description;
    private String content;
    private String coverImageUrl;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}