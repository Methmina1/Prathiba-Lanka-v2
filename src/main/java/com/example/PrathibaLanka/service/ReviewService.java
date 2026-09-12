package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.ReviewRequestDTO;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.entity.Review;
import com.example.PrathibaLanka.entity.TravelPackage;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.CustomerRepository;
import com.example.PrathibaLanka.repository.ReviewRepository;
import com.example.PrathibaLanka.repository.TravelPackageRepository;
import com.example.PrathibaLanka.security.OwnershipGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepo;
    private final CustomerRepository customerRepo;
    private final TravelPackageRepository packageRepo;

    /**
     * Creates a review written by the authenticated customer. Without a packageId the review is
     * treated as a general company review.
     */
    public Review submitReview(ReviewRequestDTO dto, Long authenticatedCustomerId) {
        Long customerId = OwnershipGuard.requireOwnCustomerId(dto.getCustomerId(), authenticatedCustomerId);

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with id: " + customerId));

        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("Rating must be between 1 and 5.");
        }

        Review review = new Review();
        review.setCustomer(customer);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        if (dto.getPackageId() != null) {
            TravelPackage pkg = packageRepo.findById(dto.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Package not found with id: " + dto.getPackageId()));
            review.setTravelPackage(pkg);
        }

        return reviewRepo.save(review);
    }

    @Transactional(readOnly = true)
    public List<Review> getAllReviews() {
        return reviewRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<Review> getReviewsByPackage(Long packageId) {
        return reviewRepo.findByTravelPackage_PackageId(packageId);
    }

    public void deleteReview(Long reviewId) {
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found with id: " + reviewId));
        reviewRepo.delete(review);
    }
}
