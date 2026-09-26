package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** A signed-in admin changing their own password, having proved they know the current one. */
@Data
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
