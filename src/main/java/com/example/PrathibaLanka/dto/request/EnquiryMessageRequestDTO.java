package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A message a customer adds to their own enquiry, from the page their token opens. */
@Data
public class EnquiryMessageRequestDTO {

    @NotBlank(message = "Please write a message")
    @Size(max = 5000, message = "Please keep the message under 5000 characters")
    private String message;
}
