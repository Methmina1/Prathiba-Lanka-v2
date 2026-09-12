package com.example.PrathibaLanka.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewResponseDTO {
    private Long reviewId;
    private Long customerId;
    private String customerName;
    private Long packageId;
    private String packageTitle;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}