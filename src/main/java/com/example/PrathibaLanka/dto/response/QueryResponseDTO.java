package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.QueryStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueryResponseDTO {
    private Long queryId;
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private Boolean autoResponseSent;
    private String adminResponse;
    private String respondedByName;
    private QueryStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
}