package com.example.PrathibaLanka.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewResponseDTO {
    private Long reviewId;
    private String customerName;
    private String packageTitle;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}