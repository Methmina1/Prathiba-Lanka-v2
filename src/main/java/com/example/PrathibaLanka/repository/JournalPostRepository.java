package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.JournalPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalPostRepository extends JpaRepository<JournalPost, Long> {
    // TODO: Add custom queries if needed
}