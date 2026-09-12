package com.example.PrathibaLanka.controller;

import com.example.PrathibaLanka.dto.request.ReviewRequestDTO;
import com.example.PrathibaLanka.dto.response.ReviewResponseDTO;
import com.example.PrathibaLanka.entity.Review;
import com.example.PrathibaLanka.security.UserPrincipal;
import com.example.PrathibaLanka.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ---------------- PUBLIC ----------------

    @GetMapping("/api/reviews")
    public ResponseEntity<List<ReviewResponseDTO>> all() {
        return ResponseEntity.ok(reviewService.getAllReviews().stream().map(this::toDTO).toList());
    }

    @GetMapping("/api/reviews/package/{packageId}")
    public ResponseEntity<List<ReviewResponseDTO>> byPackage(@PathVariable Long packageId) {
        return ResponseEntity.ok(reviewService.getReviewsByPackage(packageId).stream().map(this::toDTO).toList());
    }

    // ---------------- CUSTOMER ----------------

    /** Customers submit a review. Requires a customer token; the review is attributed to it. */
    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponseDTO> submit(@Valid @RequestBody ReviewRequestDTO dto,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        Review r = reviewService.submitReview(dto, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(r));
    }

    // ---------------- ADMIN ----------------

    @DeleteMapping("/api/admin/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- MAPPER ----------------

    private ReviewResponseDTO toDTO(Review r) {
        ReviewResponseDTO dto = new ReviewResponseDTO();
        dto.setReviewId(r.getReviewId());
        dto.setCustomerId(r.getCustomer().getCustomerId());
        dto.setCustomerName(r.getCustomer().getFullName());
        if (r.getTravelPackage() != null) {
            dto.setPackageId(r.getTravelPackage().getPackageId());
            dto.setPackageTitle(r.getTravelPackage().getTitle());
        }
        dto.setRating(r.getRating());
        dto.setComment(r.getComment());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}