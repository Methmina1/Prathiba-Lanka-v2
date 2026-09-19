package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.JournalPost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JournalPostRepository extends JpaRepository<JournalPost, Long> {
    List<JournalPost> findByStatus(String status);

    boolean existsByCoverImageUrl(String coverImageUrl);
}