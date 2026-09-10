package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.QueryStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QueryResponseDTO {
    private Long queryId;
    private String name;
    private String email;
    private String subject;
    private String message;
    private QueryStatus status;
    private String adminResponse;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
}