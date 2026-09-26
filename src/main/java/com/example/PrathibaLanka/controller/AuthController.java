package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.ForgotPasswordRequestDTO;
import com.example.PrathibaLanka.dto.request.LoginRequestDTO;
import com.example.PrathibaLanka.dto.request.RegisterRequestDTO;
import com.example.PrathibaLanka.dto.request.ResetPasswordRequestDTO;
import com.example.PrathibaLanka.dto.response.ApiResponse;
import com.example.PrathibaLanka.dto.response.AuthResponseDTO;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.security.JwtService;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.AdminPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminPasswordService adminPasswordService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(dto.getEmail()), dto.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtService.generateToken(principal);
        String role = principal.getAuthorities().iterator().next().getAuthority();

        return ResponseEntity.ok(new AuthResponseDTO(
                token, principal.getEmail(), role, principal.getUserId()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto) {
        String email = normalizeEmail(dto.getEmail());

        // Email must be unique across admins and customers, not just within one table.
        if (adminRepository.findByEmailIgnoreCase(email).isPresent() ||
                customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BadRequestException("Email is already registered.");
        }

        Customer customer = new Customer();
        customer.setFullName(dto.getFullName().trim());
        customer.setEmail(email);
        customer.setPhone(dto.getPhone());
        customer.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        Customer saved = customerRepository.save(customer);

        UserPrincipal principal = UserPrincipal.fromCustomer(saved);
        String token = jwtService.generateToken(principal);

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponseDTO(
                token, principal.getEmail(), "ROLE_CUSTOMER", principal.getUserId()
        ));
    }

    /**
     * "I have forgotten my password." Emails a one-time code, if that address has an admin account.
     *
     * <p>Always the same 200 and the same sentence. Whether an address has an account behind it is
     * something this endpoint is not willing to tell anyone who can type an address into it, so the
     * caller learns nothing from the response - only from the inbox, which is the point.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO dto) {
        adminPasswordService.requestReset(dto.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "If that address belongs to an admin account, a code is on its way. It is good for 10 minutes.",
                null));
    }

    /** Spends a code and sets the new password. */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO dto) {
        AdminPasswordService.Outcome outcome =
                adminPasswordService.resetPassword(dto.getEmail(), dto.getCode(), dto.getNewPassword());

        if (outcome == AdminPasswordService.Outcome.INVALID_CODE) {
            // One message for a code that is wrong, expired, already used, or belongs to no account:
            // the difference between those is exactly what an attacker would like to be told.
            throw new BadRequestException(
                    "That code is not valid, or it has expired. Ask for a new one and try again.");
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Your password has been changed. Sign in with the new one.", null));
    }

    /** Single canonical form, otherwise "User@x.com" and "user@x.com" become two accounts. */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}