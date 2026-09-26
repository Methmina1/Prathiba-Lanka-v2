package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.ChangePasswordRequestDTO;
import com.example.PrathibaLanka.dto.response.ApiResponse;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.AdminPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin's own account, as opposed to the records they administer.
 *
 * <p>Under {@code /api/admin/**} so Spring Security's existing rule already requires an admin, and it
 * takes the id from the token rather than from the body: the one password an admin can change here is
 * their own, which is the only one they have proved anything about.
 */
@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminPasswordService adminPasswordService;

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {

        adminPasswordService.changePassword(
                principal.getUserId(), dto.getCurrentPassword(), dto.getNewPassword());

        return ResponseEntity.ok(ApiResponse.success(
                "Your password has been changed. Any other session you had open will ask for the new one.",
                null));
    }
}
