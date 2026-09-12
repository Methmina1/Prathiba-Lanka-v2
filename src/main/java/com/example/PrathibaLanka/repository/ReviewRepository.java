package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByTravelPackage_PackageId(Long packageId);
}