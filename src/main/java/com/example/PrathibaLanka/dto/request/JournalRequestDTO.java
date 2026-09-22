package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JournalRequestDTO {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Content is required")
    private String content;

    private String coverImageUrl;

    private String status;

    /**
     * When the story was published, when the caller knows better than "now".
     *
     * <p>Optional, and only honoured when the post ends up published: the console leaves it empty and
     * the API stamps the moment it went up, while an import filling a second instance - or an editor
     * backdating a story written before the site existed - sends the date it should carry. Without
     * this, seeding a production database dates every story the day of the deployment.
     */
    private LocalDateTime publishedAt;
}
