package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryRequestDTO {
    private Long customerId;

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid Email Format")
    @NotBlank(message = "Email is required")
    private String email;

    private String phone;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Message is required")
    private String message;
}