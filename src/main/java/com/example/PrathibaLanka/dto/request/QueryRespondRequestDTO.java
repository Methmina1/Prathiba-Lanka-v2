package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryRespondRequestDTO {
    @NotBlank(message = "Response is required")
    private String adminResponse;
}