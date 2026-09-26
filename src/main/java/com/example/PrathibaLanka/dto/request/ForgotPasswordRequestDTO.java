package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** "I have forgotten my password" - the address the code should go to, if it has an account. */
@Data
public class ForgotPasswordRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid Email Format")
    private String email;
}
