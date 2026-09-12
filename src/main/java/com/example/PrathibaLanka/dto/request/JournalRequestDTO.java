package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JournalRequestDTO {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Content is required")
    private String content;

    private String coverImageUrl;

    private String status;
}