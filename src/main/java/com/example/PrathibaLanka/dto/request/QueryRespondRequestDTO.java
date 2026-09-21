package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QueryRespondRequestDTO {

    @NotBlank(message = "Response is required")
    // The reply becomes outgoing mail, and the column behind it is TEXT: without a ceiling, a pasted
    // document is mailed to a customer and recorded as correspondence.
    @Size(max = 5000, message = "Please keep the reply under 5000 characters")
    private String adminResponse;
}
