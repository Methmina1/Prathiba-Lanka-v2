package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // TODO: add the queries if necessary
}